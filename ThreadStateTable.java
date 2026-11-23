
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// Tabla de Estados de los Threads
public class ThreadStateTable extends Tabla {

    private static final String TITLE = "Tablero General - Tipos de Agentes y Estados de Threads";
    private static final String[] HEADERS = {
        "Tipo de Agente", "Total", "RUNNABLE", "TIMED_WAITING", "WAITING", "BLOCKED", "TERMINATED"
    };

    private final List<Client> clients;
    private final List<Police> police;
    private final List<Airplane> localAirplanes;
    private final ControlTower localTower;
    private final ControlTower remoteTower;

    // Conexion con los agentes
    public ThreadStateTable(List<Client> clients, List<Police> police, List<Airplane> localAirplanes,
            ControlTower localTower, ControlTower remoteTower,
            int x, int y, int width, int height) {
        super(TITLE, HEADERS, x, y, width, height, 150);
        this.clients = clients;
        this.police = police;
        this.localAirplanes = localAirplanes;
        this.localTower = localTower;
        this.remoteTower = remoteTower;
    }

    // Conteo de estados por tipo de agente
    @Override
    protected List<Object[]> buildRows() {
        List<Airplane> allPlanes = new ArrayList<>(localAirplanes);
        allPlanes.addAll(remoteTower.getReceivedAirplanes());

        List<Thread> distributedThreads = new ArrayList<>();
        distributedThreads.add(remoteTower);
        distributedThreads.addAll(localTower.getNetworkThreads());
        distributedThreads.addAll(remoteTower.getNetworkThreads());

        List<Object[]> rows = new ArrayList<>();
        rows.add(buildRow("Agente Cliente", clients));
        rows.add(buildRow("Agente Policía", police));
        rows.add(buildRow("Agente Avión", allPlanes));
        rows.add(buildRow("Agente Torre de Control", Collections.singletonList(localTower)));
        rows.add(buildRow("Agente Distribuido", distributedThreads));
        return rows;
    }

    private Object[] buildRow(String agentName, List<? extends Thread> threads) {
        int runnable = 0, timedWaiting = 0, waiting = 0, blocked = 0, terminated = 0;
        for (Thread t : threads) {
            switch (t.getState()) {
                case RUNNABLE:
                    runnable++;
                    break;
                case TIMED_WAITING:
                    timedWaiting++;
                    break;
                case WAITING:
                    waiting++;
                    break;
                case BLOCKED:
                    blocked++;
                    break;
                case TERMINATED:
                    terminated++;
                    break;
                default:
                    break;
            }
        }
        return new Object[]{agentName, threads.size(), runnable, timedWaiting, waiting, blocked, terminated};
    }
}
