import java.util.Objects;
import java.util.Random;

public class Police extends Thread {
    public enum Status { PATROLLING, SIGNALING_SUSPECT, INVESTIGATING, RESTING, SHIFT_ENDED, STOPPED }

    private final SecurityArea security;
    private final Random random;
    private final double reviewProbability;
    private final long patrolMillis, investigationMillis, restMillis;
    private final int roundsBeforeRest;
    private final int shiftNumber;
    private volatile Status status = Status.PATROLLING;
    private volatile boolean shiftOver = false;
    private volatile String lastSignal = "";

    public Police(int id, SecurityArea security) {
        this(id, security, new Random(), 0.30, 100, 150, 300, 5, 1);
    }

    public Police(int id, SecurityArea security, Random random, double reviewProbability,
                  long patrolMillis, long investigationMillis, long restMillis, int roundsBeforeRest) {
        this(id, security, random, reviewProbability, patrolMillis, investigationMillis, restMillis, roundsBeforeRest, 1);
    }

    public Police(int id, SecurityArea security, Random random, double reviewProbability,
                  long patrolMillis, long investigationMillis, long restMillis, int roundsBeforeRest, int shiftNumber) {
        super("Policia-" + id + (shiftNumber > 1 ? "-T" + shiftNumber : ""));
        if (!Double.isFinite(reviewProbability) || reviewProbability < 0 || reviewProbability > 1
                || patrolMillis < 1 || investigationMillis < 0 || restMillis < 0 || roundsBeforeRest < 1) {
            throw new IllegalArgumentException("Configuracion de policia invalida");
        }
        this.security = Objects.requireNonNull(security);
        this.random = Objects.requireNonNull(random);
        this.reviewProbability = reviewProbability;
        this.patrolMillis = patrolMillis;
        this.investigationMillis = investigationMillis;
        this.restMillis = restMillis;
        this.roundsBeforeRest = roundsBeforeRest;
        this.shiftNumber = shiftNumber;
    }

    public Status getStatus() { return status; }
    void changeStatus(Status next) { status = next; }
    public int getShiftNumber() { return shiftNumber; }
    public String getLastSignal() { return lastSignal; }
    void setLastSignal(String signal) { this.lastSignal = signal; }

    // Cambio de turno del policia
    public void endShift() {
        shiftOver = true;
        this.interrupt();
    }

    public boolean isShiftOver() { return shiftOver; }

    @Override
    public void run() {
        int rounds = 0;
        try {
            while (!isInterrupted() && !shiftOver) {
                status = Status.PATROLLING;
                Thread.sleep(patrolMillis);
                if (shiftOver) break;
                security.investigate(this, random, reviewProbability, investigationMillis);
                if (++rounds == roundsBeforeRest && !shiftOver) {
                    status = Status.RESTING;
                    Thread.sleep(restMillis);
                    rounds = 0;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            status = shiftOver ? Status.SHIFT_ENDED : Status.STOPPED;
        }
    }
}
