import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Agente Torre de Control. Cada recurso compartido tiene su propio cerrojo:
 *   1) Pista (zona critica, m de 10)        -> runwayLock
 *   2) Fila de abordaje (zona critica)      -> gateLock
 *   3) Pasarela (seccion critica, 1)        -> bridgeLock
 * Ademas crea los hilos de red: TorreServidor (recibe vuelos) y TorreCliente (envia vuelos).
 */
public class ControlTower extends Thread {
    public static final int MAX_RUNWAY = 10;

    public enum Status { MONITORING, HOLDING, ASSIGNING_RUNWAY, AUTHORIZING_TAKEOFF, STOPPED }

    private final String code;
    private final int runwayCapacity;
    private final long coordinationMillis, bridgeMillis, retryMillis;
    private volatile Status status = Status.MONITORING;

    // ===== 1. PISTA: zona critica de m aviones (m <= 10) =====
    private final ReentrantLock runwayLock = new ReentrantLock(true);
    private final Condition runwayChanged = runwayLock.newCondition();
    private final Set<Airplane> onRunway = new HashSet<>();
    private final List<Airplane> dockRequests = new ArrayList<>();
    private final List<Airplane> departureRequests = new ArrayList<>();
    private final Set<Airplane> granted = new HashSet<>();

    // ===== 2. FILA DE ABORDAJE y 3. PASARELA =====
    private final ReentrantLock gateLock = new ReentrantLock(true);
    private final Condition gateChanged = gateLock.newCondition();
    private final Deque<Client> readyQueue = new ArrayDeque<>();
    private final Set<Client> called = new HashSet<>();
    private final ReentrantLock bridgeLock = new ReentrantLock(true);

    // ===== 4. RED =====
    private volatile ServerSocket serverSocket;
    private final AtomicInteger flightsSent = new AtomicInteger();
    private final AtomicInteger flightsReceived = new AtomicInteger();
    private final AtomicInteger flightsHeld = new AtomicInteger();

    public ControlTower(String code, int runwayCapacity, long coordinationMillis,
                        long bridgeMillis, long retryMillis) {
        super("Torre-" + code);
        if (runwayCapacity < 1 || runwayCapacity > MAX_RUNWAY || coordinationMillis < 0
                || bridgeMillis < 0 || retryMillis < 1) {
            throw new IllegalArgumentException("Configuracion de torre invalida");
        }
        this.code = code;
        this.runwayCapacity = runwayCapacity;
        this.coordinationMillis = coordinationMillis;
        this.bridgeMillis = bridgeMillis;
        this.retryMillis = retryMillis;
    }

    // ===================================================================
    // HILO DE LA TORRE: atiende solicitudes de pista una por una
    // ===================================================================
    @Override
    public void run() {
        try {
            while (!isInterrupted()) {
                Airplane plane;
                boolean takeoff;
                runwayLock.lockInterruptibly();
                try {
                    // Espera hasta tener algo que pueda atender.
                    while (departureRequests.isEmpty()
                            && (dockRequests.isEmpty() || onRunway.size() >= runwayCapacity)) {
                        status = dockRequests.isEmpty() ? Status.MONITORING : Status.HOLDING;
                        runwayChanged.await();
                    }
                    // Los despegues van primero porque liberan pista.
                    takeoff = !departureRequests.isEmpty();
                    plane = (takeoff ? departureRequests : dockRequests).remove(0);
                    if (!takeoff) onRunway.add(plane); // reserva el lugar desde ya
                    status = takeoff ? Status.AUTHORIZING_TAKEOFF : Status.ASSIGNING_RUNWAY;
                } finally {
                    runwayLock.unlock();
                }

                Thread.sleep(coordinationMillis); // tiempo que tarda la torre en autorizar

                runwayLock.lockInterruptibly();
                try {
                    if (takeoff) onRunway.remove(plane);
                    if (plane.isAlive()) granted.add(plane);
                    runwayChanged.signalAll();
                } finally {
                    runwayLock.unlock();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            status = Status.STOPPED;
        }
    }

    // ===================================================================
    // 1. PISTA (los llama el hilo del AVION)
    // ===================================================================
    void requestDock(Airplane plane) throws InterruptedException {
        request(dockRequests, plane);
    }

    void requestDeparture(Airplane plane) throws InterruptedException {
        request(departureRequests, plane);
    }

    /** Deja la solicitud y espera (WAITING) hasta que el hilo de la torre la autorice. */
    private void request(List<Airplane> requests, Airplane plane) throws InterruptedException {
        runwayLock.lockInterruptibly();
        try {
            requests.add(plane);
            runwayChanged.signalAll();
            while (!granted.remove(plane)) runwayChanged.await();
        } finally {
            requests.remove(plane); // por si lo interrumpieron antes de ser atendido
            runwayLock.unlock();
        }
    }

    /** Libera el lugar sin pasar por la torre: vuelos que llegaron o aviones interrumpidos. */
    void leaveRunway(Airplane plane) {
        runwayLock.lock();
        try {
            dockRequests.remove(plane);
            departureRequests.remove(plane);
            granted.remove(plane);
            if (onRunway.remove(plane)) runwayChanged.signalAll();
        } finally {
            runwayLock.unlock();
        }
    }

    // ===================================================================
    // 2. FILA DE ABORDAJE y 3. PASARELA
    // ===================================================================

    /** Hilo del CLIENTE: se forma, cruza la pasarela y espera a bordo hasta llegar. */
    void board(Client client) throws InterruptedException {
        gateLock.lockInterruptibly();
        try {
            readyQueue.add(client);
            gateChanged.signalAll();
            while (!called.contains(client)) gateChanged.await();
        } finally {
            gateLock.unlock();
        }

        bridgeLock.lockInterruptibly(); // SECCION CRITICA: un solo pasajero en la pasarela
        try {
            client.changeStatus(Client.Status.BOARDING);
            Thread.sleep(bridgeMillis);
            // Se marca a bordo ANTES de soltar la pasarela: nunca hay dos en BOARDING.
            gateLock.lock();
            try {
                client.changeStatus(Client.Status.ON_BOARD);
                called.remove(client);
                gateChanged.signalAll();
            } finally {
                gateLock.unlock();
            }
        } finally {
            bridgeLock.unlock();
        }

        gateLock.lockInterruptibly();
        try {
            while (client.getStatus() == Client.Status.ON_BOARD
                    || client.getStatus() == Client.Status.FLYING) {
                gateChanged.await();
            }
        } finally {
            gateLock.unlock();
        }
    }

    /** Hilo del AVION (consumidor): toma al siguiente pasajero o null si se acabo el tiempo. */
    Client callPassenger(long timeoutNanos) throws InterruptedException {
        gateLock.lockInterruptibly();
        try {
            while (readyQueue.isEmpty()) {
                if (timeoutNanos <= 0) return null;
                timeoutNanos = gateChanged.awaitNanos(timeoutNanos);
            }
            Client client = readyQueue.poll();
            called.add(client);
            gateChanged.signalAll();
            return client;
        } finally {
            gateLock.unlock();
        }
    }

    /** Hilo del AVION: espera a que todos los llamados terminen de cruzar la pasarela. */
    void awaitOnBoard(List<Client> passengers) throws InterruptedException {
        gateLock.lockInterruptibly();
        try {
            while (passengers.stream().anyMatch(c -> c.getStatus() == Client.Status.READY_TO_BOARD
                    || c.getStatus() == Client.Status.BOARDING)) {
                gateChanged.await();
            }
            passengers.removeIf(c -> c.getStatus() != Client.Status.ON_BOARD);
        } finally {
            gateLock.unlock();
        }
    }

    /** Cambia a los pasajeros a bordo (despegue, aterrizaje, traslado) y los despierta. */
    void updatePassengers(List<Client> passengers, Client.Status next) {
        gateLock.lock();
        try {
            for (Client c : passengers) {
                if (c.getStatus() == Client.Status.ON_BOARD || c.getStatus() == Client.Status.FLYING) {
                    c.changeStatus(next);
                }
            }
            gateChanged.signalAll();
        } finally {
            gateLock.unlock();
        }
    }

    /** Cliente interrumpido: sale de la fila sin dejar basura. */
    void cancel(Client client) {
        gateLock.lock();
        try {
            readyQueue.remove(client);
            called.remove(client);
            gateChanged.signalAll();
        } finally {
            gateLock.unlock();
        }
    }

    // ===================================================================
    // 4. RED - agente servidor: recibe vuelos de otro nodo
    // ===================================================================
    public void startServer(int port) throws IOException {
        serverSocket = new ServerSocket(port); // falla aqui si el puerto esta ocupado
        Thread server = new Thread(this::listen, "TorreServidor-" + code);
        server.setDaemon(true);
        server.start();
    }

    private void listen() {
        while (!serverSocket.isClosed()) {
            try (Socket socket = serverSocket.accept()) {
                socket.setSoTimeout(5000);
                ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
                // Solo acepta FlightData y Strings: no deserializa clases arbitrarias de la red.
                in.setObjectInputFilter(ObjectInputFilter.Config.createFilter(
                        "Airplane$FlightData;java.lang.String;maxdepth=3;!*"));
                Airplane.FlightData data = (Airplane.FlightData) in.readObject();
                Airplane arriving = reserveForArrival(data);
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                out.writeUTF(arriving != null ? "OK" : "WAIT");
                out.flush();
                if (arriving != null) arriving.start();
            } catch (IOException | ClassNotFoundException | ClassCastException e) {
                if (serverSocket.isClosed()) return;
            }
        }
    }

    /** Si hay cupo en pista, lo aparta para el vuelo que llega; si no, devuelve null. */
    private Airplane reserveForArrival(Airplane.FlightData data) {
        runwayLock.lock();
        try {
            if (onRunway.size() >= runwayCapacity) {
                flightsHeld.incrementAndGet();
                return null;
            }
            Airplane arriving = new Airplane(data, this);
            onRunway.add(arriving);
            flightsReceived.incrementAndGet();
            runwayChanged.signalAll();
            return arriving;
        } finally {
            runwayLock.unlock();
        }
    }

    // ===================================================================
    // 5. RED - agente cliente: envia un vuelo y reintenta si le dicen WAIT
    // ===================================================================
    Thread sendFlight(Airplane.FlightData data, String host, int port) {
        Thread client = new Thread(() -> transmit(data, host, port),
                "TorreCliente-" + code + "-" + data.flightId);
        client.start();
        return client;
    }

    private void transmit(Airplane.FlightData data, String host, int port) {
        while (!Thread.currentThread().isInterrupted()) {
            try (Socket socket = new Socket(host, port)) {
                socket.setSoTimeout(5000);
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                out.writeObject(data);
                out.flush();
                if ("OK".equals(new DataInputStream(socket.getInputStream()).readUTF())) {
                    flightsSent.incrementAndGet();
                    return;
                }
            } catch (IOException e) {
                // El otro nodo no responde: se trata igual que un WAIT.
            }
            try {
                Thread.sleep(retryMillis);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    public void shutdown() {
        interrupt();
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {
        }
    }

    // ===================================================================
    // 6. CONTEOS PARA LOS TABLEROS
    // ===================================================================
    public String getCode() { return code; }
    public Status getStatus() { return status; }
    public int getRunwayCapacity() { return runwayCapacity; }

    public int getPlanesOnRunway() {
        runwayLock.lock();
        try { return onRunway.size(); } finally { runwayLock.unlock(); }
    }

    public int getPlanesWaitingRunway() {
        runwayLock.lock();
        try { return dockRequests.size(); } finally { runwayLock.unlock(); }
    }

    public int getPassengersWaitingToBoard() {
        gateLock.lock();
        try { return readyQueue.size(); } finally { gateLock.unlock(); }
    }

    public int getPassengersWaitingBridge() { return bridgeLock.getQueueLength(); }
    public boolean isBridgeBusy() { return bridgeLock.isLocked(); }
    public int getFlightsSent() { return flightsSent.get(); }
    public int getFlightsReceived() { return flightsReceived.get(); }
    public int getFlightsHeld() { return flightsHeld.get(); }

    // ===================================================================
    // DEMO: dos aeropuertos en la misma computadora (CDMX envia a GDL)
    // ===================================================================
    public static void main(String[] args) throws Exception {
        ControlTower cdmx = new ControlTower("CDMX", 3, 100, 40, 300);
        ControlTower gdl = new ControlTower("GDL", 1, 100, 40, 300);
        gdl.startServer(5002);
        cdmx.start();
        gdl.start();

        SecurityArea security = new SecurityArea(15);
        Police police = new Police(1, security);
        police.start();
        Random random = new Random(9);
        List<Client> clients = new ArrayList<>();
        for (int i = 1; i <= 40; i++) {
            Client c = new Client(i, security, cdmx, 300, random);
            clients.add(c);
            c.start();
        }
        List<Airplane> planes = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            String destination = (i % 2 == 0) ? "localhost" : null; // pares van a GDL
            Airplane p = new Airplane(i, cdmx, 10, 2500, 800, destination, 5002);
            planes.add(p);
            p.start();
        }

        while (planes.stream().anyMatch(Thread::isAlive) || gdl.getPlanesOnRunway() > 0) {
            StringBuilder line = new StringBuilder();
            line.append(String.format("CDMX %-19s pista %d/%d espera %d | fila %2d | ",
                    cdmx.getStatus(), cdmx.getPlanesOnRunway(), cdmx.getRunwayCapacity(),
                    cdmx.getPlanesWaitingRunway(), cdmx.getPassengersWaitingToBoard()));
            for (Airplane p : planes) {
                line.append(String.format("%s:%s(%d) ", p.getName(), p.getStatus(), p.getPassengerCount()));
            }
            line.append(String.format("| GDL pista %d/%d", gdl.getPlanesOnRunway(), gdl.getRunwayCapacity()));
            System.out.println(line);
            Thread.sleep(400);
        }

        for (Client c : clients) c.interrupt();
        police.interrupt();
        cdmx.shutdown();
        gdl.shutdown();
        for (Client c : clients) c.join(1000);

        System.out.printf("%nGDL recibio %d vuelos (le pidio esperar a %d intentos)%n",
                gdl.getFlightsReceived(), gdl.getFlightsHeld());
        for (Client.Status s : Client.Status.values()) {
            long n = clients.stream().filter(c -> c.getStatus() == s).count();
            if (n > 0) System.out.println("  " + s + ": " + n);
        }
    }
}
