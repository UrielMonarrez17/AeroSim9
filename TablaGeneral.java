import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;

// Tabla General de Agentes, Bufers y Secciones Criticas
public class TablaGeneral extends Tabla {
    private static final String TITLE = "Tablero General - Totales de Agentes, Búfers y Secciones Críticas";
    private static final String[] HEADERS = {
            "Categoría", "Agente / Búfer / Sección Crítica", "Estado / Cantidad en Tiempo Real"
    };

    private final SecurityArea security;
    private final ControlTower localTower;
    private final ControlTower remoteTower;
    private final List<Client> clients;
    private final List<Police> allPolice;
    private final List<Police> activePolice;
    private final List<Airplane> localAirplanes;
    private final IntSupplier currentShiftSupplier;

    // Conexion con los agentes y recursos compartidos
    public TablaGeneral(SecurityArea security,
                        ControlTower localTower, ControlTower remoteTower,
                        List<Client> clients, List<Police> allPolice,
                        List<Police> activePolice, List<Airplane> localAirplanes,
                        IntSupplier currentShiftSupplier,
                        int x, int y, int width, int height) {
        super(TITLE, HEADERS, x, y, width, height, 150);
        this.security = security;
        this.localTower = localTower;
        this.remoteTower = remoteTower;
        this.clients = clients;
        this.allPolice = allPolice;
        this.activePolice = activePolice;
        this.localAirplanes = localAirplanes;
        this.currentShiftSupplier = currentShiftSupplier;
        getTable().getColumnModel().getColumn(0).setMaxWidth(115);
    }

    // Llenado de datos de agentes y bufers
    @Override
    protected List<Object[]> buildRows() {
        List<Airplane> planes = new ArrayList<>(localAirplanes);
        planes.addAll(remoteTower.getReceivedAirplanes());

        List<Thread> distributedThreads = new ArrayList<>();
        distributedThreads.add(remoteTower);
        distributedThreads.addAll(localTower.getNetworkThreads());
        distributedThreads.addAll(remoteTower.getNetworkThreads());

        long terrorists = clients.stream().filter(Client::isTerrorist).count();
        List<Airplane> activePlanes = planes.stream()
                .filter(p -> p.getStatus() != Airplane.Status.WAITING_RUNWAY
                        && p.getStatus() != Airplane.Status.FINISHED)
                .toList();
        int seatsOccupied = activePlanes.stream().mapToInt(Airplane::getPassengerCount).sum();
        int activeSeatCapacity = Math.max(SimConfig.AIRPLANE_SEATS.getValue(),
                activePlanes.size() * SimConfig.AIRPLANE_SEATS.getValue());
        int bridgeInside = localTower.isBridgeBusy() ? 1 : 0;
        int currentShift = currentShiftSupplier.getAsInt();

        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{"Agente", "Agente <Cliente>",
                String.format("Hay %d agentes (%d estándar, %d terroristas)",
                        clients.size(), clients.size() - terrorists, terrorists)});
        rows.add(new Object[]{"Agente", "Agente <Policía>",
                String.format("Hay %d agentes (%d activos en Turno #%d, %d fin de turno)",
                        allPolice.size(), activePolice.size(), currentShift, allPolice.size() - activePolice.size())});
        rows.add(new Object[]{"Agente", "Agente <Avión>",
                String.format("Hay %d agentes (%d recibidos en nodo remoto)",
                        planes.size(), remoteTower.getReceivedAirplanes().size())});
        rows.add(new Object[]{"Agente", "Agente <Torre de Control>",
                String.format("Hay 1 agente local (%s)", localTower.getCode())});
        rows.add(new Object[]{"Agente", "Agente <Distribuido>",
                String.format("Hay %d hilos (vuelos recibidos: %d, en espera TCP: %d)",
                        distributedThreads.size(), remoteTower.getFlightsReceived(), remoteTower.getFlightsHeld())});

        rows.add(new Object[]{"Espera Búfer", "Búfer <Sala de Espera>",
                String.format("Hay %d agentes esperando por entrar", security.getWaitingToEnterCount())});
        rows.add(new Object[]{"En Búfer", "Búfer <Sala de Espera>",
                String.format("Hay %d / %d (máx %d) agentes en el búfer",
                        security.getOccupancy(), SimConfig.WAITING_ROOM.getValue(), SimConfig.WAITING_ROOM.getMax())});

        rows.add(new Object[]{"Espera Búfer", "Búfer <Sala Interrogatorio>",
                String.format("Hay %d agentes esperando por entrar", security.getWaitingForInvestigationCount())});
        rows.add(new Object[]{"En Búfer", "Búfer <Sala Interrogatorio>",
                String.format("Hay %d / %d (máx %d) agentes en el búfer",
                        security.getInvestigationBufferAgents(), SimConfig.INTERROGATION_ROOM.getValue(), SimConfig.INTERROGATION_ROOM.getMax())});

        rows.add(new Object[]{"Espera Búfer", "Búfer <Asientos de Avión>",
                String.format("Hay %d agentes esperando por entrar",
                        localTower.getPassengersWaitingToBoard())});
        rows.add(new Object[]{"En Búfer", "Búfer <Asientos de Avión>",
                String.format("Hay %d / %d agentes a bordo (%d por avión, máx %d)",
                        seatsOccupied, activeSeatCapacity,
                        SimConfig.AIRPLANE_SEATS.getValue(), SimConfig.AIRPLANE_SEATS.getMax())});

        rows.add(new Object[]{"Espera Búfer", "Búfer <Pista Torre de Control>",
                String.format("Hay %d aviones esperando por entrar a pista",
                        localTower.getPlanesWaitingRunway())});
        rows.add(new Object[]{"En Búfer", "Búfer <Pista Torre de Control>",
                String.format("Hay %d / %d (máx %d) aviones en pista",
                        localTower.getPlanesOnRunway(), SimConfig.TOWER_RUNWAY.getValue(), SimConfig.TOWER_RUNWAY.getMax())});

        rows.add(new Object[]{"Espera Zona Crítica", "Zona Crítica <Pasarela Abordaje>",
                String.format("Hay %d agentes esperando por entrar a zona crítica",
                        localTower.getPassengersWaitingBridge())});
        rows.add(new Object[]{"En Zona Crítica", "Zona Crítica <Pasarela Abordaje>",
                String.format("Hay %d / %d agentes en sección crítica",
                        bridgeInside, SimConfig.BOARDING_BRIDGE.getMax())});

        return rows;
    }
}
