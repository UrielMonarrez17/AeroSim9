import java.util.Objects;
import java.util.Random;

/** Un solo tipo de pasajero: la condicion de terrorista no es otro agente. */
public class Client extends Thread {
    public static final double TERRORIST_PROBABILITY = 0.05;

    public enum Status {
        ARRIVING, WAITING, BEING_QUESTIONED, READY_TO_BOARD,
        ARRESTED, PROCESSED, CANCELLED
    }

    private final SecurityArea security;
    private final boolean terrorist;
    private final long waitingMillis;
    private volatile Status status = Status.ARRIVING;
    // Protegido por el cerrojo de SecurityArea.
    boolean reviewed;

    public Client(int id, SecurityArea security, long waitingMillis) {
        this(id, security, waitingMillis, new Random());
    }

    /** Random inyectable para reproducir la simulacion y probar el limite del 5%. */
    public Client(int id, SecurityArea security, long waitingMillis, Random random) {
        super("Cliente-" + id);
        if (waitingMillis < 0) throw new IllegalArgumentException("Tiempo negativo");
        this.security = Objects.requireNonNull(security);
        this.waitingMillis = waitingMillis;
        this.terrorist = Objects.requireNonNull(random).nextDouble() < TERRORIST_PROBABILITY;
    }

    public boolean isTerrorist() { return terrorist; }
    public Status getStatus() { return status; }

    void changeStatus(Status next) { status = next; }

    @Override
    public void run() {
        try {
            security.enter(this);
            Thread.sleep(waitingMillis);
            security.finishWaiting(this);
        } catch (InterruptedException e) {
            security.cancel(this);
            Thread.currentThread().interrupt();
        }
    }
}
