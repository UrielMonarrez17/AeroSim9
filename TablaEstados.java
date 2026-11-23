import java.util.ArrayList;
import java.util.List;

// Tabla de Estados de los Agentes
public class TablaEstados extends Tabla {
    private static final String TITLE = "Tablero de Estados de los Agentes";
    private static final String[] HEADERS = {
            "Agente", "Estado", "Cantidad", "Región / Búfer Asociado"
    };

    private final List<Client> clients;
    private final List<Police> police;
    private final List<Airplane> localAirplanes;
    private final ControlTower localTower;
    private final ControlTower remoteTower;
    private final int runwayM;
    private final int remotePort;

    // Conexion con los agentes
    public TablaEstados(List<Client> clients, List<Police> police, List<Airplane> localAirplanes,
                        ControlTower localTower, ControlTower remoteTower,
                        int runwayM, int remotePort,
                        int x, int y, int width, int height) {
        super(TITLE, HEADERS, x, y, width, height, 150);
        this.clients = clients;
        this.police = police;
        this.localAirplanes = localAirplanes;
        this.localTower = localTower;
        this.remoteTower = remoteTower;
        this.runwayM = runwayM;
        this.remotePort = remotePort;
    }

    // Conteo de agentes por estado
    @Override
    protected List<Object[]> buildRows() {
        List<Airplane> airplanes = new ArrayList<>(localAirplanes);
        airplanes.addAll(remoteTower.getReceivedAirplanes());
        int distCount = 1 + localTower.getNetworkThreads().size() + remoteTower.getNetworkThreads().size();

        List<Object[]> rows = new ArrayList<>();
        for (Client.Status st : Client.Status.values()) {
            long count = clients.stream().filter(c -> c.getStatus() == st).count();
            rows.add(new Object[]{"Cliente", st.name(), count, clientRegion(st)});
        }
        for (Police.Status st : Police.Status.values()) {
            long count = police.stream().filter(p -> p.getStatus() == st).count();
            rows.add(new Object[]{"Policía", st.name(), count, policeRegion(st)});
        }
        for (Airplane.Status st : Airplane.Status.values()) {
            long count = airplanes.stream().filter(a -> a.getStatus() == st).count();
            rows.add(new Object[]{"Avión", st.name(), count, airplaneRegion(st)});
        }
        rows.add(new Object[]{"Torre de Control", localTower.getStatus().name(), 1,
                "Pista (" + runwayM + " de 10)"});
        rows.add(new Object[]{"Agente Distribuido", remoteTower.getStatus().name(),
                distCount, "Socket TCP Puerto " + remotePort});
        return rows;
    }

    private String clientRegion(Client.Status st) {
        switch (st) {
            case ARRIVING: return "Cola de Entrada";
            case WAITING: return "Sala de Espera";
            case BEING_QUESTIONED:
            case ARRESTED: return "Sala Interrogatorio (2 espacios)";
            case PROCESSED: return "Detenido / Procesado";
            case READY_TO_BOARD: return "Fila hacia Pasarela";
            case BOARDING: return "Pasarela";
            case ON_BOARD: return "Asientos de Avión";
            case FLYING: return "En Vuelo";
            case ARRIVED: return "Finalizado (Local)";
            case TRANSFERRED: return "Transferido a Nodo Remoto";
            default: return "Cancelado";
        }
    }

    private String policeRegion(Police.Status st) {
        switch (st) {
            case PATROLLING: return "Sala de Espera";
            case SIGNALING_SUSPECT: return "Señal de Sospechoso";
            case INVESTIGATING: return "Sala Interrogatorio (2 espacios)";
            case RESTING: return "Área de Descanso";
            case SHIFT_ENDED: return "Fin de Turno";
            default: return "Detenido";
        }
    }

    private String airplaneRegion(Airplane.Status st) {
        switch (st) {
            case WAITING_RUNWAY: return "Esperando Pista";
            case LOADING: return "Cargando Asientos";
            case TAKING_OFF: return "Solicitando Despegue";
            case FLYING_LOCAL: return "Vuelo Local";
            case SERIALIZING: return "Serializando Estado";
            case IN_TRANSIT: return "Transmitiendo por Red";
            case LANDING:
            case DISEMBARKING: return "Pista Nodo Remoto";
            default: return "Vuelo Finalizado";
        }
    }
}
