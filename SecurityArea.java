import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/** Estado compartido. Todas las decisiones sobre un pasajero son atomicas. */
public class SecurityArea {
    private final int capacity;
    private final List<Client> waiting = new ArrayList<>();
    private final ReentrantLock lock = new ReentrantLock(true);
    private final Condition changed = lock.newCondition();
    // Un policia conserva este cerrojo durante todo el interrogatorio.
    private final ReentrantLock investigationRoom = new ReentrantLock(true);

    public SecurityArea(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("Capacidad debe ser positiva");
        this.capacity = capacity;
    }

    void enter(Client client) throws InterruptedException {
        lock.lockInterruptibly();
        try {
            while (waiting.size() == capacity) changed.await();
            waiting.add(client);
            client.changeStatus(Client.Status.WAITING);
            changed.signalAll();
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

    /** La seleccion no consulta isTerrorist: tambien se revisan inocentes. */
    boolean investigate(Police police, Random random, double reviewProbability,
                        long durationMillis) throws InterruptedException {
        investigationRoom.lockInterruptibly();
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
                selected.changeStatus(Client.Status.BEING_QUESTIONED);
                police.changeStatus(Police.Status.INVESTIGATING);
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
                police.changeStatus(Police.Status.PATROLLING);
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

    public int getOccupancy() {
        lock.lock();
        try { return waiting.size(); }
        finally { lock.unlock(); }
    }

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
}
