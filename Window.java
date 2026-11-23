
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.Timer;
import javax.swing.border.TitledBorder;

// Controlador de ventanas y simulacion
public class Window extends JFrame {

    private SecurityArea securityArea;
    private ControlTower localTower, remoteTower;
    private int remotePort = 5099;

    private final List<Client> allClients = new CopyOnWriteArrayList<>();
    private final List<Police> allPolice = new CopyOnWriteArrayList<>();
    private final List<Police> activeShiftPolice = new CopyOnWriteArrayList<>();
    private final List<Airplane> allAirplanes = new CopyOnWriteArrayList<>();
    private final Random random = new Random();

    private int nextClientId = 1, nextPoliceId = 1, nextAirplaneId = 1;
    private volatile int currentHour, hoursInCurrentShift = 0, currentShiftNumber = 1;

    private SimulatorInterface simulatorCanvas;
    private ThreadStateTable threadStateWindow;
    private TablaGeneral generalTotalsWindow;
    private TablaEstados bpmnStateWindow;
    private Timer clockHourTimer;

    public Window() {
        super("AeroSim9 - Controlador de Simulación");
        this.currentHour = SimConfig.START_HOUR.getValue();
        startSimulation();
    }

    // Ventana de Configuracion de Entradas
    public static void showConfigurationWindow() {
        JFrame cfgFrame = new JFrame("AeroSim9 - Configuración de Entradas del Simulador");
        cfgFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        cfgFrame.setLayout(new BorderLayout(10, 10));

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(15, 23, 42));
        header.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
        JLabel title = new JLabel("Configuración de Entradas, Búfers y Timers");
        title.setForeground(Color.WHITE);
        title.setFont(new Font("SansSerif", Font.BOLD, 15));
        header.add(title, BorderLayout.CENTER);
        cfgFrame.add(header, BorderLayout.NORTH);

        JPanel center = new JPanel(new GridLayout(1, 2, 12, 0));
        center.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        JPanel left = new JPanel(new GridBagLayout());
        left.setBorder(createSectionBorder("1. Agentes, Turnos (8h) y Búfers"));
        JTextField tfClients = createField(SimConfig.NUM_CLIENTS, 5);
        JTextField tfPolice = createField(SimConfig.NUM_POLICE, 5);
        JTextField tfPlanes = createField(SimConfig.NUM_AIRPLANES, 5);
        JTextField tfHour = createField(SimConfig.START_HOUR, 5);
        JTextField tfWaitM = createField(SimConfig.WAITING_ROOM, 4);
        JTextField tfInterM = createField(SimConfig.INTERROGATION_ROOM, 4);
        JTextField tfSeatsM = createField(SimConfig.AIRPLANE_SEATS, 4);
        JTextField tfRunwayM = createField(SimConfig.TOWER_RUNWAY, 4);
        JTextField tfBridgeM = createField(SimConfig.BOARDING_BRIDGE, 4);

        Object[][] leftRows = {
            {"Número de Clientes:", tfClients, "agentes"},
            {"Número de Policías por turno:", tfPolice, "policías"},
            {"Número de Aviones iniciales:", tfPlanes, "aviones"},
            {"Hora de entrada (0 - 23 hrs):", tfHour, "hrs (Turno 8h)"},
            {"Búfer Sala de Espera:", createCapPanel(tfWaitM, SimConfig.WAITING_ROOM.getMax()), "máx " + SimConfig.WAITING_ROOM.getMax()},
            {"Búfer Sala Interrogatorio:", createCapPanel(tfInterM, SimConfig.INTERROGATION_ROOM.getMax()), "máx 2 espacios"},
            {"Búfer Capacidad de Avión:", createCapPanel(tfSeatsM, SimConfig.AIRPLANE_SEATS.getMax()), "máx " + SimConfig.AIRPLANE_SEATS.getMax()},
            {"Búfer Torre Control (Pista):", createCapPanel(tfRunwayM, SimConfig.TOWER_RUNWAY.getMax()), "máx " + SimConfig.TOWER_RUNWAY.getMax()},
            {"Sección Crítica Pasarela:", createCapPanel(tfBridgeM, SimConfig.BOARDING_BRIDGE.getMax()), "máx 1"}
        };
        for (int i = 0; i < leftRows.length; i++) {
            addConfigRow(left, i, (String) leftRows[i][0], (Component) leftRows[i][1], (String) leftRows[i][2]);
        }

        JPanel right = new JPanel(new GridBagLayout());
        right.setBorder(createSectionBorder("2. Variables de los Timers (ms)"));
        JTextField tfClientMs = createField(SimConfig.CLIENT_WAITING_MS, 6);
        JTextField tfPatrolMs = createField(SimConfig.POLICE_PATROL_MS, 6);
        JTextField tfInvMs = createField(SimConfig.POLICE_INVESTIGATION_MS, 6);
        JTextField tfRestMs = createField(SimConfig.POLICE_REST_MS, 6);
        JTextField tfRounds = createField(SimConfig.POLICE_ROUNDS_REST, 6);
        JTextField tfLoadMs = createField(SimConfig.AIRPLANE_LOAD_MS, 6);
        JTextField tfFlightMs = createField(SimConfig.AIRPLANE_FLIGHT_MS, 6);
        JTextField tfTowerMs = createField(SimConfig.TOWER_COORD_MS, 6);
        JTextField tfBridgeMs = createField(SimConfig.BRIDGE_CROSSING_MS, 6);
        JTextField tfHourMs = createField(SimConfig.SIMULATED_HOUR_MS, 6);

        Object[][] rightRows = {
            {"Timer estancia Cliente en Sala:", tfClientMs, "ms"},
            {"Timer patrullaje de Policía:", tfPatrolMs, "ms"},
            {"Timer interrogatorio:", tfInvMs, "ms"},
            {"Timer descanso de Policía:", tfRestMs, "ms"},
            {"Rondas antes de descanso:", tfRounds, "rondas"},
            {"Timer espera Avión cargando:", tfLoadMs, "ms"},
            {"Timer duración vuelo Avión:", tfFlightMs, "ms"},
            {"Timer coordinación Torre:", tfTowerMs, "ms"},
            {"Timer cruce Pasarela:", tfBridgeMs, "ms"},
            {"Timer 1 hora simulada:", tfHourMs, "ms/hora"}
        };
        for (int i = 0; i < rightRows.length; i++) {
            addConfigRow(right, i, (String) rightRows[i][0], (Component) rightRows[i][1], (String) rightRows[i][2]);
        }

        center.add(left);
        center.add(right);
        cfgFrame.add(center, BorderLayout.CENTER);

        JButton btnStart = new JButton("Iniciar Simulación");
        btnStart.setFont(new Font("SansSerif", Font.BOLD, 13));
        btnStart.setBackground(new Color(37, 99, 235));
        btnStart.setForeground(Color.WHITE);
        btnStart.addActionListener(e -> {
            try {
                SimConfig.NUM_CLIENTS.setFromText(tfClients.getText(), "Número de Clientes");
                SimConfig.NUM_POLICE.setFromText(tfPolice.getText(), "Número de Policías");
                SimConfig.NUM_AIRPLANES.setFromText(tfPlanes.getText(), "Número de Aviones");
                SimConfig.START_HOUR.setFromText(tfHour.getText(), "Hora de entrada");
                SimConfig.WAITING_ROOM.setFromText(tfWaitM.getText(), "Búfer Sala Espera");
                SimConfig.INTERROGATION_ROOM.setFromText(tfInterM.getText(), "Búfer Interrogatorio");
                SimConfig.AIRPLANE_SEATS.setFromText(tfSeatsM.getText(), "Búfer Asientos Avión");
                SimConfig.TOWER_RUNWAY.setFromText(tfRunwayM.getText(), "Búfer Torre Control");
                SimConfig.BOARDING_BRIDGE.setFromText(tfBridgeM.getText(), "Pasarela");
                SimConfig.CLIENT_WAITING_MS.setFromText(tfClientMs.getText(), "Timer estancia Cliente");
                SimConfig.POLICE_PATROL_MS.setFromText(tfPatrolMs.getText(), "Timer patrullaje");
                SimConfig.POLICE_INVESTIGATION_MS.setFromText(tfInvMs.getText(), "Timer interrogatorio");
                SimConfig.POLICE_REST_MS.setFromText(tfRestMs.getText(), "Timer descanso");
                SimConfig.POLICE_ROUNDS_REST.setFromText(tfRounds.getText(), "Rondas descanso");
                SimConfig.AIRPLANE_LOAD_MS.setFromText(tfLoadMs.getText(), "Timer espera Avión");
                SimConfig.AIRPLANE_FLIGHT_MS.setFromText(tfFlightMs.getText(), "Timer vuelo Avión");
                SimConfig.TOWER_COORD_MS.setFromText(tfTowerMs.getText(), "Timer Torre");
                SimConfig.BRIDGE_CROSSING_MS.setFromText(tfBridgeMs.getText(), "Timer Pasarela");
                SimConfig.SIMULATED_HOUR_MS.setFromText(tfHourMs.getText(), "Timer 1 hora simulada");

                cfgFrame.dispose();
                new Window();
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(cfgFrame, ex.getMessage(),
                        "Validación de Datos", JOptionPane.WARNING_MESSAGE);
            }
        });

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 8));
        footer.add(btnStart);
        cfgFrame.add(footer, BorderLayout.SOUTH);
        cfgFrame.pack();
        cfgFrame.setLocationRelativeTo(null);
        cfgFrame.setVisible(true);
    }

    // Conexion con los agentes y apertura de ventanas
    private void startSimulation() {
        securityArea = new SecurityArea(SimConfig.WAITING_ROOM.getValue(), SimConfig.INTERROGATION_ROOM.getValue());
        localTower = new ControlTower("CDMX", SimConfig.TOWER_RUNWAY.getValue(),
                SimConfig.TOWER_COORD_MS.getValue(), SimConfig.BRIDGE_CROSSING_MS.getValue(), 400);
        remoteTower = new ControlTower("NODO-REMOTO", Math.max(1, SimConfig.TOWER_RUNWAY.getValue()),
                SimConfig.TOWER_COORD_MS.getValue(), SimConfig.BRIDGE_CROSSING_MS.getValue(), 400);

        remotePort = findAvailablePort(5099);
        try {
            remoteTower.startServer(remotePort);
        } catch (IOException ignored) {
        }
        localTower.start();
        remoteTower.start();

        spawnPoliceShift(SimConfig.NUM_POLICE.getValue(), currentShiftNumber);
        spawnClients(SimConfig.NUM_CLIENTS.getValue());
        for (int i = 0; i < SimConfig.NUM_AIRPLANES.getValue(); i++) {
            spawnAirplane();
        }

        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int scrW = Math.max(1280, screen.width);
        int scrH = Math.max(760, screen.height - 50);
        int leftW = (int) (scrW * 0.53);
        int rightW = scrW - leftW - 15;
        int tableH = scrH / 3;

        // Ventana de Simulacion Grafica 
        simulatorCanvas = new SimulatorInterface();
        simulatorCanvas.bindSimulation(securityArea, localTower, allClients, allPolice);
        updateClockLabel();
        simulatorCanvas.startCanvasWindowThread(
                5, 5, leftW, scrH,
                () -> {
                    stopAllWindowsAndSimulation();
                    System.exit(0);
                }
        );

        // Tabla de Estados de los Threads
        threadStateWindow = new ThreadStateTable(
                allClients, allPolice, allAirplanes, localTower, remoteTower,
                leftW + 10, 5, rightW, tableH - 4
        );
        threadStateWindow.startWindowThread("Thread-Tabla-Threads");

        // Tabla General de Agentes y Bufers
        generalTotalsWindow = new TablaGeneral(
                securityArea, localTower, remoteTower,
                allClients, allPolice, activeShiftPolice, allAirplanes,
                () -> currentShiftNumber,
                leftW + 10, 5 + tableH, rightW, tableH - 4
        );
        generalTotalsWindow.startWindowThread("Thread-Tabla-General");

        // Tabla de Estados de los Agentes
        bpmnStateWindow = new TablaEstados(
                allClients, allPolice, allAirplanes, localTower, remoteTower,
                SimConfig.TOWER_RUNWAY.getValue(), remotePort,
                leftW + 10, 5 + tableH * 2, rightW, tableH - 4
        );
        bpmnStateWindow.startWindowThread("Thread-Tabla-Estados");

        clockHourTimer = new Timer(SimConfig.SIMULATED_HOUR_MS.getValue(), e -> {
            currentHour = (currentHour + 1) % 24;
            if (++hoursInCurrentShift >= 8) {
                triggerPoliceShiftChange();
            } else {
                updateClockLabel();
            }
        });
        clockHourTimer.start();
    }

    private void spawnClients(int count) {
        for (int i = 0; i < count; i++) {
            Client client = new Client(nextClientId++, securityArea, localTower,
                    SimConfig.CLIENT_WAITING_MS.getValue(), random);
            allClients.add(client);
            client.start();
        }
    }

    private void spawnPoliceShift(int count, int shiftNum) {
        activeShiftPolice.clear();
        for (int i = 0; i < count; i++) {
            Police police = new Police(nextPoliceId++, securityArea, new Random(), 0.45,
                    SimConfig.POLICE_PATROL_MS.getValue(), SimConfig.POLICE_INVESTIGATION_MS.getValue(),
                    SimConfig.POLICE_REST_MS.getValue(), SimConfig.POLICE_ROUNDS_REST.getValue(), shiftNum);
            allPolice.add(police);
            activeShiftPolice.add(police);
            police.start();
        }
    }

    private void spawnAirplane() {
        int id = nextAirplaneId++;
        Airplane plane = new Airplane(id, localTower, SimConfig.AIRPLANE_SEATS.getValue(),
                SimConfig.AIRPLANE_LOAD_MS.getValue(), SimConfig.AIRPLANE_FLIGHT_MS.getValue(),
                (id % 2 == 0) ? "localhost" : null, remotePort);
        allAirplanes.add(plane);
        plane.start();
    }

    private void triggerPoliceShiftChange() {
        hoursInCurrentShift = 0;
        currentShiftNumber++;
        for (Police oldOfficer : activeShiftPolice) {
            oldOfficer.endShift();
        }
        spawnPoliceShift(SimConfig.NUM_POLICE.getValue(), currentShiftNumber);
        updateClockLabel();
    }

    private void updateClockLabel() {
        String text = String.format("Hora Aeropuerto: %02d:00 hrs | Turno #%d (Faltan %dh)",
                currentHour, currentShiftNumber, 8 - hoursInCurrentShift);
        if (simulatorCanvas != null) {
            simulatorCanvas.setClockText(text);
        }
    }

    private void stopAllWindowsAndSimulation() {
        if (clockHourTimer != null) {
            clockHourTimer.stop();
        }
        if (simulatorCanvas != null) {
            simulatorCanvas.stopCanvasWindowThread();
        }
        if (threadStateWindow != null) {
            threadStateWindow.stopWindowThread();
        }
        if (generalTotalsWindow != null) {
            generalTotalsWindow.stopWindowThread();
        }
        if (bpmnStateWindow != null) {
            bpmnStateWindow.stopWindowThread();
        }
        for (Client client : allClients) {
            client.interrupt();
        }
        for (Police police : allPolice) {
            police.interrupt();
        }
        for (Airplane plane : allAirplanes) {
            plane.interrupt();
        }
        if (localTower != null) {
            localTower.shutdown();
        }
        if (remoteTower != null) {
            remoteTower.shutdown();
        }
        dispose();
    }

    private static int findAvailablePort(int preferred) {
        try (ServerSocket ss = new ServerSocket(preferred)) {
            return ss.getLocalPort();
        } catch (IOException e) {
            try (ServerSocket ss = new ServerSocket(0)) {
                return ss.getLocalPort();
            } catch (IOException ex) {
                return preferred;
            }
        }
    }

    private static JTextField createField(SimConfig cfg, int cols) {
        return new JTextField(String.valueOf(cfg.getValue()), cols);
    }

    private static JPanel createCapPanel(JTextField tfCap, int maxCap) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
        panel.add(tfCap);
        panel.add(new JLabel(" de " + maxCap));
        return panel;
    }

    private static void addConfigRow(JPanel panel, int row, String label, Component field, String unit) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridy = row;
        gbc.gridx = 0;
        gbc.weightx = 0.45;
        panel.add(new JLabel(label), gbc);
        gbc.gridx = 1;
        gbc.weightx = 0.30;
        panel.add(field, gbc);
        gbc.gridx = 2;
        gbc.weightx = 0.25;
        panel.add(new JLabel(unit), gbc);
    }

    private static TitledBorder createSectionBorder(String title) {
        TitledBorder tb = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(148, 163, 184), 1), title);
        tb.setTitleFont(new Font("SansSerif", Font.BOLD, 12));
        tb.setTitleColor(new Color(15, 23, 42));
        return tb;
    }
}
