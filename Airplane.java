import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Agente Avion. Consume pasajeros de la fila de abordaje y ocupa un lugar en la pista. */
public class Airplane extends Thread {
    public static final int MAX_SEATS = 20;

    public enum Status {
        WAITING_RUNWAY, LOADING, TAKING_OFF, FLYING_LOCAL,
        SERIALIZING, IN_TRANSIT, LANDING, DISEMBARKING, FINISHED
    }

    /** Lo unico que viaja por la red: un Thread no se puede serializar. */
    public static class FlightData implements Serializable {
        private static final long serialVersionUID = 1L;
        final int flightId;
        final String origin;
        final String[] passengers;
        final long groundMillis;

        FlightData(int flightId, String origin, String[] passengers, long groundMillis) {
            this.flightId = flightId;
            this.origin = origin;
            this.passengers = passengers;
            this.groundMillis = groundMillis;
        }
    }

    private final int id;
    private final ControlTower tower;
    private final int seats;
    private final long loadTimeoutMillis, flightMillis;
    private final String destHost; // null = vuelo local
    private final int destPort;
    private final FlightData arriving; // != null = vuelo que llego por la red
    private final List<Client> passengers = new ArrayList<>();
    private volatile int passengerCount;
    private volatile Status status;

    /** Avion que sale de este aeropuerto. */
    public Airplane(int id, ControlTower tower, int seats, long loadTimeoutMillis,
                    long flightMillis, String destHost, int destPort) {
        super("Avion-" + id);
        if (seats < 1 || seats > MAX_SEATS || loadTimeoutMillis < 0 || flightMillis < 0) {
            throw new IllegalArgumentException("Configuracion de avion invalida");
        }
        this.id = id;
        this.tower = Objects.requireNonNull(tower);
        this.seats = seats;
        this.loadTimeoutMillis = loadTimeoutMillis;
        this.flightMillis = flightMillis;
        this.destHost = destHost;
        this.destPort = destPort;
        this.arriving = null;
        this.status = Status.WAITING_RUNWAY;
    }

    /** Avion que llega de otro nodo; la torre ya le aparto lugar en la pista. */
    Airplane(FlightData data, ControlTower tower) {
        super("Avion-" + data.origin + "-" + data.flightId);
        this.id = data.flightId;
        this.tower = tower;
        this.seats = data.passengers.length;
        this.loadTimeoutMillis = 0;
        this.flightMillis = data.groundMillis;
        this.destHost = null;
        this.destPort = 0;
        this.arriving = data;
        this.passengerCount = data.passengers.length;
        this.status = Status.LANDING;
    }

    @Override
    public void run() {
        try {
            if (arriving != null) land();
            else departFromHere();
        } catch (InterruptedException e) {
            tower.updatePassengers(passengers, Client.Status.CANCELLED);
            Thread.currentThread().interrupt();
        } finally {
            tower.leaveRunway(this); // nunca deja ocupado un lugar de pista
            status = Status.FINISHED;
        }
    }

    private void departFromHere() throws InterruptedException {
        status = Status.WAITING_RUNWAY;
        tower.requestDock(this);

        status = Status.LOADING;
        load();

        status = Status.TAKING_OFF;
        tower.requestDeparture(this);
        tower.updatePassengers(passengers, Client.Status.FLYING);

        if (destHost == null) {
            status = Status.FLYING_LOCAL;
            Thread.sleep(flightMillis);
            tower.updatePassengers(passengers, Client.Status.ARRIVED);
        } else {
            status = Status.SERIALIZING;
            FlightData data = toFlightData();
            status = Status.IN_TRANSIT;
            Thread sender = tower.sendFlight(data, destHost, destPort);
            try {
                sender.join();
            } catch (InterruptedException e) {
                sender.interrupt();
                throw e;
            }
            tower.updatePassengers(passengers, Client.Status.TRANSFERRED);
        }
    }

    /** Productor-consumidor: saca pasajeros hasta llenar asientos o vencer el tiempo. */
    private void load() throws InterruptedException {
        long deadline = System.nanoTime() + loadTimeoutMillis * 1_000_000L;
        while (passengers.size() < seats) {
            Client next = tower.callPassenger(deadline - System.nanoTime());
            if (next == null) break; // se acabo el tiempo
            passengers.add(next);
        }
        tower.awaitOnBoard(passengers);
        passengerCount = passengers.size();
    }

    private FlightData toFlightData() {
        String[] names = new String[passengers.size()];
        for (int i = 0; i < names.length; i++) names[i] = passengers.get(i).getName();
        return new FlightData(id, tower.getCode(), names, flightMillis);
    }

    private void land() throws InterruptedException {
        status = Status.LANDING;
        Thread.sleep(flightMillis);
        status = Status.DISEMBARKING;
        Thread.sleep(flightMillis);
    }

    public Status getStatus() { return status; }
    public int getPassengerCount() { return passengerCount; }
    public int getSeats() { return seats; }
    public boolean isRemote() { return destHost != null; }
}
