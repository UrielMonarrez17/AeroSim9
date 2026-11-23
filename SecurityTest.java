import java.util.Random;
import java.util.function.BooleanSupplier;

/** Pruebas sin dependencias: java -cp build SecurityTest. */
public class SecurityTest {
    private static Random fixed(double value) {
        return new Random(1) {
            @Override public double nextDouble() { return value; }
        };
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + 3_000_000_000L;
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) throw new AssertionError("Tiempo de espera agotado");
            Thread.sleep(1);
        }
    }

    public static void main(String[] args) throws Exception {
        SecurityArea area = new SecurityArea(2);
        check(new Client(1, area, 0, fixed(0.049999)).isTerrorist(), "Debajo de 5%");
        check(!new Client(2, area, 0, fixed(0.05)).isTerrorist(), "Limite de 5%");

        Client innocent = new Client(3, area, 150, fixed(0.8));
        Client suspect = new Client(4, area, 150, fixed(0.01));
        Police first = new Police(1, area, fixed(0), 1, 1, 50, 1, 2);
        Police second = new Police(2, area, fixed(0), 1, 1, 50, 1, 2);
        try {
            innocent.start(); suspect.start();
            await(() -> area.getOccupancy() == 2);
            first.start(); second.start();
            long deadline = System.nanoTime() + 3_000_000_000L;
            while (innocent.isAlive() || suspect.isAlive()) {
                check(System.nanoTime() < deadline, "Clientes bloqueados");
                // Lectura bajo el mismo cerrojo evita snapshots mezclados entre turnos.
                check(area.getInvestigationOccupancy() <= 1, "Dos clientes en investigacion");
                check(area.getOccupancy() <= 2, "Capacidad excedida");
                Thread.sleep(1);
            }
            check(innocent.getStatus() == Client.Status.READY_TO_BOARD, "Liberar inocente");
            check(suspect.getStatus() == Client.Status.PROCESSED, "Procesar terrorista");
            check(area.getOccupancy() == 0, "Liberar cupos");
        } finally {
            innocent.interrupt(); suspect.interrupt(); first.interrupt(); second.interrupt();
            innocent.join(1000); suspect.join(1000); first.join(1000); second.join(1000);
        }

        final SecurityArea single = new SecurityArea(1);
        Client held = new Client(5, single, 10000, fixed(0.01));
        Client blocked = new Client(6, single, 0, fixed(0.5));
        Police interrupted = new Police(3, single, fixed(0), 1, 1, 10000, 1, 2);
        try {
            held.start();
            await(() -> held.getStatus() == Client.Status.WAITING);
            blocked.start();
            await(() -> blocked.getState() == Thread.State.WAITING);
            check(blocked.getStatus() == Client.Status.ARRIVING, "Entrada debe esperar cupo");
            interrupted.start();
            await(() -> held.getStatus() == Client.Status.BEING_QUESTIONED);
            interrupted.interrupt(); interrupted.join(1000);
            check(!interrupted.isAlive(), "Policia debe detenerse");
            check(held.getStatus() == Client.Status.WAITING, "No dejar interrogatorio bloqueado");
            held.interrupt(); held.join(1000); blocked.join(1000);
            check(blocked.getStatus() == Client.Status.READY_TO_BOARD, "Desbloquear entrada");
            check(single.getOccupancy() == 0, "No perder cupos al cancelar");
        } finally {
            held.interrupt(); blocked.interrupt(); interrupted.interrupt();
            held.join(1000); blocked.join(1000); interrupted.join(1000);
        }

        Client undetected = new Client(7, new SecurityArea(1), 0, fixed(0.01));
        undetected.start(); undetected.join(1000);
        check(undetected.getStatus() == Client.Status.READY_TO_BOARD,
                "Terrorista no revisado sigue el flujo del cliente");
        System.out.println("OK: probabilidad, revision, exclusividad, capacidad y cancelacion");
    }
}
