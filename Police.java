import java.util.Objects;
import java.util.Random;

public class Police extends Thread {
    public enum Status { PATROLLING, INVESTIGATING, RESTING, STOPPED }

    private final SecurityArea security;
    private final Random random;
    private final double reviewProbability;
    private final long patrolMillis, investigationMillis, restMillis;
    private final int roundsBeforeRest;
    private volatile Status status = Status.PATROLLING;

    public Police(int id, SecurityArea security) {
        this(id, security, new Random(), 0.30, 100, 150, 300, 5);
    }

    public Police(int id, SecurityArea security, Random random, double reviewProbability,
                  long patrolMillis, long investigationMillis, long restMillis, int roundsBeforeRest) {
        super("Policia-" + id);
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
    }

    public Status getStatus() { return status; }
    void changeStatus(Status next) { status = next; }

    @Override
    public void run() {
        int rounds = 0;
        try {
            while (!isInterrupted()) {
                status = Status.PATROLLING;
                Thread.sleep(patrolMillis);
                security.investigate(this, random, reviewProbability, investigationMillis);
                if (++rounds == roundsBeforeRest) {
                    status = Status.RESTING;
                    Thread.sleep(restMillis);
                    rounds = 0;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally { status = Status.STOPPED; }
    }
}
