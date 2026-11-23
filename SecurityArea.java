import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

// Control de la sala de espera y sala de interrogatorio
public class SecurityArea {
    public static final int MAX_WAITING_CAPACITY = 50;
    public static final int MAX_INVESTIGATION_CAPACITY = 2; // 1 policia + 1 cliente

    private final int capacity;
    private final int investigationCapacity;
    private final List<Client> waiting = new ArrayList<>();
    private final ReentrantLock lock = new ReentrantLock(true);
    private final Condition changed = lock.newCondition();
    // Un policia conserva este cerrojo durante todo el interrogatorio.
    private final ReentrantLock investigationRoom = new ReentrantLock(true);

    private int waitingToEnter = 0;
    private int waitingForInvestigation = 0;
    private volatile Client currentQuestionedClient = null;
    private volatile Police currentInvestigatingPolice = null;
    private volatile String lastSuspectSignal = "Sin alertas recientes";
    private volatile long lastSignalTimestamp = 0L;
    private int totalSignalsEmitted = 0;

    public SecurityArea(int capacity) {
        this(capacity, MAX_INVESTIGATION_CAPACITY);
    }

    public SecurityArea(int capacity, int investigationCapacity) {
        if (capacity < 1) throw new IllegalArgumentException("Capacidad debe ser positiva");
        if (investigationCapacity < 1 || investigationCapacity > MAX_INVESTIGATION_CAPACITY) {
            throw new IllegalArgumentException("Capacidad de sala de interrogatorio invalida (1..2)");
        }
        this.capacity = capacity;
        this.investigationCapacity = investigationCapacity;
    }

    void enter(Client client) throws InterruptedException {
        lock.lockInterruptibly();
        try {
            waitingToEnter++;
            try {
                while (waiting.size() >= capacity) changed.await();
                waiting.add(client);
                client.changeStatus(Client.Status.WAITING);
                changed.signalAll();
            } finally {
                waitingToEnter--;
            }
        } finally { lock.unlock(); }
    }

    void finishWaiting(Client client) throws InterruptedException {
        lock.lockInterruptibly();
        try {
            while (client.getStatus() == Client.Status.BEING_QUESTIONED
                    || client.getStatus() == Client.Status.ARRESTED) changed.await();
            if (client.getStatus() == Client.Status.WAITING) {
                waiting.remove(client);
                client.changeStatus(Client.Status.READY_TO_BOARD);
                changed.signalAll();
            }
        } finally { lock.unlock(); }
    }

    // Interrogatorio y deteccion de sospechosos
    boolean investigate(Police police, Random random, double reviewProbability,
                        long durationMillis) throws InterruptedException {
        lock.lockInterruptibly();
        try {
            waitingForInvestigation++;
        } finally {
            lock.unlock();
        }
        investigationRoom.lockInterruptibly();
        lock.lock();
        try {
            waitingForInvestigation--;
        } finally {
            lock.unlock();
        }
        Client selected = null;
        boolean completed = false;
        try {
            lock.lockInterruptibly();
            try {
                List<Client> candidates = new ArrayList<>();
                for (Client c : waiting) {
                    if (c.getStatus() == Client.Status.WAITING && !c.reviewed) candidates.add(c);
                }
                if (candidates.isEmpty() || random.nextDouble() >= reviewProbability) return false;
                selected = candidates.get(random.nextInt(candidates.size()));
                // Policia emite senal de cliente sospechoso y ambos entran al bufer de 2 espacios
                police.changeStatus(Police.Status.SIGNALING_SUSPECT);
                String sig = String.format("SENAL: %s detecto sospechoso a %s%s -> Sala Interrogatorio (%d/%d)",
                        police.getName(), selected.getName(),
                        selected.isTerrorist() ? " [5% TERRORISTA]" : " [Revision]",
                        investigationCapacity, MAX_INVESTIGATION_CAPACITY);
                police.setLastSignal(sig);
                lastSuspectSignal = sig;
                lastSignalTimestamp = System.currentTimeMillis();
                totalSignalsEmitted++;

                selected.changeStatus(Client.Status.BEING_QUESTIONED);
                police.changeStatus(Police.Status.INVESTIGATING);
                currentQuestionedClient = selected;
                currentInvestigatingPolice = police;
                changed.signalAll();
            } finally { lock.unlock(); }

            Thread.sleep(durationMillis);
            lock.lockInterruptibly();
            try {
                if (selected.getStatus() == Client.Status.BEING_QUESTIONED) {
                    selected.reviewed = true;
                    selected.changeStatus(selected.isTerrorist()
                            ? Client.Status.ARRESTED : Client.Status.WAITING);
                }
                changed.signalAll();
            } finally { lock.unlock(); }
            // Traslado/procesamiento simulado; mantiene la pareja reservada.
            if (selected.getStatus() == Client.Status.ARRESTED) Thread.sleep(durationMillis);
            lock.lockInterruptibly();
            try {
                if (selected.getStatus() == Client.Status.ARRESTED) {
                    waiting.remove(selected);
                    selected.changeStatus(Client.Status.PROCESSED);
                }
                completed = true;
                changed.signalAll();
            } finally { lock.unlock(); }
            return true;
        } finally {
            lock.lock();
            try {
                if (!completed && selected != null) {
                    if (selected.getStatus() == Client.Status.BEING_QUESTIONED) {
                        selected.changeStatus(Client.Status.WAITING);
                    } else if (selected.getStatus() == Client.Status.ARRESTED) {
                        waiting.remove(selected);
                        selected.changeStatus(Client.Status.PROCESSED);
                    }
                    changed.signalAll();
                }
                currentQuestionedClient = null;
                currentInvestigatingPolice = null;
                if (!police.isShiftOver()) {
                    police.changeStatus(Police.Status.PATROLLING);
                }
            } finally {
                lock.unlock();
                investigationRoom.unlock();
            }
        }
    }

    void cancel(Client client) {
        lock.lock();
        try {
            waiting.remove(client);
            if (client.getStatus() != Client.Status.PROCESSED) client.changeStatus(Client.Status.CANCELLED);
            changed.signalAll();
        } finally { lock.unlock(); }
    }

    public int getCapacity() { return capacity; }
    public int getInvestigationCapacity() { return investigationCapacity; }

    public int getOccupancy() {
        lock.lock();
        try { return waiting.size(); }
        finally { lock.unlock(); }
    }

    public int getWaitingToEnterCount() {
        lock.lock();
        try { return waitingToEnter; }
        finally { lock.unlock(); }
    }

    public int getWaitingForInvestigationCount() {
        lock.lock();
        try { return Math.max(waitingForInvestigation, investigationRoom.getQueueLength()); }
        finally { lock.unlock(); }
    }

    // Conteo de clientes en interrogatorio
    public int getInvestigationOccupancy() {
        lock.lock();
        try {
            int count = 0;
            for (Client client : waiting) {
                if (client.getStatus() == Client.Status.BEING_QUESTIONED
                        || client.getStatus() == Client.Status.ARRESTED) count++;
            }
            return count;
        } finally { lock.unlock(); }
    }

    // Conteo de agentes en la sala de interrogatorio
    public int getInvestigationBufferAgents() {
        lock.lock();
        try {
            int count = 0;
            if (currentQuestionedClient != null) count++;
            if (currentInvestigatingPolice != null) count++;
            return Math.min(count, investigationCapacity);
        } finally { lock.unlock(); }
    }

    public Client getCurrentQuestionedClient() { return currentQuestionedClient; }
    public Police getCurrentInvestigatingPolice() { return currentInvestigatingPolice; }
    public String getLastSuspectSignal() { return lastSuspectSignal; }
    public long getLastSignalTimestamp() { return lastSignalTimestamp; }

    public int getTotalSignalsEmitted() {
        lock.lock();
        try { return totalSignalsEmitted; }
        finally { lock.unlock(); }
    }

    public List<Client> getWaitingSnapshot() {
        lock.lock();
        try { return new ArrayList<>(waiting); }
        finally { lock.unlock(); }
    }
}
