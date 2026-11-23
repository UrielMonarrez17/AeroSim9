
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

// Ventana y Canvas de animacion de los agentes
public class SimulatorInterface extends Canvas implements Runnable {

    private static final String ASSETS_DIR = "assets";

    private SecurityArea securityArea;
    private ControlTower localTower;
    private List<Client> clients = new ArrayList<>();
    private List<Police> policeList = new ArrayList<>();

    private BufferedImage imgClient;
    private BufferedImage imgTerrorist;
    private BufferedImage imgPolice;
    private BufferedImage imgWaitingArea;
    private BufferedImage imgInterrogationRoom;
    private BufferedImage imgRestArea;
    private BufferedImage imgBoardingGate;
    private BufferedImage backBuffer;

    private final Map<String, float[]> agentPositions = new HashMap<>();
    private int animTick = 0;
    private volatile String clockText = "06:00 hrs | Turno #1";

    private JFrame canvasFrame;
    private final JLabel lblClock = new JLabel();
    private final JLabel lblSignalBanner = new JLabel("Señal Policial: Patrullando sala de espera...");
    private volatile boolean running = false;
    private Thread canvasThread;

    public SimulatorInterface() {
        this.setBackground(new Color(238, 242, 247));
        this.setPreferredSize(new Dimension(740, 560));
        ensureAndLoadPngAssets();
    }

    // Conexion con los agentes
    public void bindSimulation(SecurityArea securityArea, ControlTower localTower,
            List<Client> clients, List<Police> policeList) {
        this.securityArea = securityArea;
        this.localTower = localTower;
        this.clients = clients;
        this.policeList = policeList;
        this.agentPositions.clear();
        repaint();
    }

    // Ventana del Canvas y su hilo de ejecucion
    public void startCanvasWindowThread(int x, int y, int width, int height,
            Runnable onWindowClose) {
        canvasFrame = new JFrame("Simulación Gráfica - Interacción Clientes y Policías");
        canvasFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        canvasFrame.setLayout(new BorderLayout(6, 6));

        JPanel topBar = new JPanel(new BorderLayout(8, 4));
        topBar.setBackground(new Color(15, 23, 42));
        topBar.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

        lblClock.setForeground(new Color(56, 189, 248));
        lblClock.setFont(new Font("SansSerif", Font.BOLD, 13));
        lblClock.setText(clockText);

        lblSignalBanner.setForeground(new Color(250, 204, 21));
        lblSignalBanner.setFont(new Font("SansSerif", Font.BOLD, 11));

        topBar.add(lblClock, BorderLayout.NORTH);
        topBar.add(lblSignalBanner, BorderLayout.CENTER);

        canvasFrame.add(topBar, BorderLayout.NORTH);
        canvasFrame.add(this, BorderLayout.CENTER);
        canvasFrame.setBounds(x, y, width, height);

        canvasFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (onWindowClose != null) {
                    onWindowClose.run();
                }
            }
        });

        running = true;
        canvasFrame.setVisible(true);
        canvasThread = new Thread(this, "Thread-Canvas-SimulatorInterface");
        canvasThread.setDaemon(true);
        canvasThread.start();
    }

    public void stopCanvasWindowThread() {
        running = false;
        if (canvasThread != null) {
            canvasThread.interrupt();
        }
        if (canvasFrame != null) {
            SwingUtilities.invokeLater(() -> canvasFrame.dispose());
        }
    }

    @Override
    public void run() {
        try {
            while (running && !Thread.currentThread().isInterrupted()) {
                stepAnimation();
                if (securityArea != null) {
                    String sig = securityArea.getLastSuspectSignal();
                    SwingUtilities.invokeLater(() -> lblSignalBanner.setText(sig));
                }
                Thread.sleep(55);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void setClockText(String clockText) {
        this.clockText = clockText;
        SwingUtilities.invokeLater(() -> lblClock.setText(clockText));
    }

    public void stepAnimation() {
        animTick++;
        repaint();
    }

    // Carga de imagenes PNG
    public final void ensureAndLoadPngAssets() {
        File dir = new File(ASSETS_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        imgClient = loadOrCreatePng(new File(dir, "client.png"), "CLIENT");
        imgTerrorist = loadOrCreatePng(new File(dir, "terrorist.png"), "TERRORIST");
        imgPolice = loadOrCreatePng(new File(dir, "police.png"), "POLICE");
        imgWaitingArea = loadOrCreatePng(new File(dir, "waiting_area.png"), "WAITING_AREA");
        imgInterrogationRoom = loadOrCreatePng(new File(dir, "interrogation_room.png"), "INTERROGATION");
        imgRestArea = loadOrCreatePng(new File(dir, "rest_area.png"), "REST_AREA");
        imgBoardingGate = loadOrCreatePng(new File(dir, "boarding_gate.png"), "BOARDING_GATE");
    }

    private BufferedImage loadOrCreatePng(File file, String type) {
        if (file.exists()) {
            try {
                BufferedImage loaded = ImageIO.read(file);
                if (loaded != null) {
                    return loaded;
                }
            } catch (IOException ignored) {
            }
        }
        BufferedImage generated = generateSpritePng(type);
        try {
            ImageIO.write(generated, "png", file);
        } catch (IOException ignored) {
        }
        return generated;
    }

    private BufferedImage generateSpritePng(String type) {
        int size = 64;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if ("CLIENT".equals(type)) {
            g2.setColor(new Color(37, 99, 235));
            g2.fillRoundRect(16, 26, 24, 28, 10, 10);
            g2.setColor(new Color(253, 224, 171));
            g2.fillOval(18, 6, 20, 20);
            g2.setColor(new Color(30, 58, 138));
            g2.fillArc(18, 5, 20, 12, 0, 180);
            g2.setColor(new Color(180, 83, 9));
            g2.fillRoundRect(42, 34, 14, 18, 4, 4);
            g2.setColor(Color.DARK_GRAY);
            g2.drawRect(46, 28, 6, 6);
        } else if ("TERRORIST".equals(type)) {
            g2.setColor(new Color(185, 28, 28));
            g2.fillRoundRect(16, 26, 24, 28, 10, 10);
            g2.setColor(new Color(245, 208, 169));
            g2.fillOval(18, 6, 20, 20);
            g2.setColor(new Color(24, 24, 27));
            g2.fillArc(17, 4, 22, 14, 0, 180);
            g2.fillRect(20, 13, 16, 5);
            g2.setColor(new Color(127, 29, 29));
            g2.fillRoundRect(41, 33, 16, 19, 4, 4);
            g2.setColor(Color.YELLOW);
            g2.setFont(new Font("SansSerif", Font.BOLD, 14));
            g2.drawString("!", 47, 48);
        } else if ("POLICE".equals(type)) {
            g2.setColor(new Color(15, 23, 42));
            g2.fillRoundRect(16, 26, 28, 30, 10, 10);
            g2.setColor(new Color(253, 224, 171));
            g2.fillOval(20, 8, 20, 20);
            g2.setColor(new Color(30, 64, 175));
            g2.fillRoundRect(16, 3, 28, 8, 4, 4);
            g2.setColor(Color.BLACK);
            g2.fillRect(18, 9, 24, 3);
            g2.setColor(new Color(250, 204, 21));
            g2.fillOval(33, 32, 8, 9);
            g2.fillOval(28, 5, 5, 5);
        } else if ("WAITING_AREA".equals(type)) {
            g2.setColor(new Color(59, 130, 246));
            g2.fillRoundRect(6, 6, 52, 52, 12, 12);
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(3f));
            g2.drawRoundRect(16, 18, 12, 26, 4, 4);
            g2.drawRoundRect(34, 18, 12, 26, 4, 4);
        } else if ("INTERROGATION".equals(type)) {
            g2.setColor(new Color(220, 38, 38));
            g2.fillRoundRect(6, 6, 52, 52, 12, 12);
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("SansSerif", Font.BOLD, 24));
            g2.drawString("2", 25, 41);
        } else if ("REST_AREA".equals(type)) {
            g2.setColor(new Color(16, 185, 129));
            g2.fillRoundRect(6, 6, 52, 52, 12, 12);
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("SansSerif", Font.BOLD, 20));
            g2.drawString("8h", 19, 40);
        } else {
            g2.setColor(new Color(124, 58, 237));
            g2.fillRoundRect(6, 6, 52, 52, 12, 12);
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("SansSerif", Font.BOLD, 18));
            g2.drawString("1", 25, 39);
        }
        g2.dispose();
        return img;
    }

    @Override
    public void update(Graphics g) {
        paint(g);
    }

    // Dibujo del Canvas
    @Override
    public void paint(Graphics g) {
        int w = Math.max(getWidth(), 680);
        int h = Math.max(getHeight(), 440);
        if (backBuffer == null || backBuffer.getWidth() != w || backBuffer.getHeight() != h) {
            backBuffer = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        }
        Graphics2D g2 = backBuffer.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g2.setPaint(new GradientPaint(0, 0, new Color(241, 245, 249), 0, h, new Color(226, 232, 240)));
        g2.fillRect(0, 0, w, h);

        drawZones(g2, w, h);
        drawAgentsAndInteractions(g2, w, h);
        drawLegendBar(g2, w, h);

        g2.dispose();
        g.drawImage(backBuffer, 0, 0, null);
    }

    // Dibujo de zonas y bufers
    private void drawZones(Graphics2D g2, int w, int h) {
        int waitCap = securityArea != null ? securityArea.getCapacity() : 15;
        int rawWaitOcc = securityArea != null ? securityArea.getOccupancy() : 0;
        int waitOcc = Math.min(rawWaitOcc, waitCap);
        long arrivingCount = clients.stream().filter(c -> c.getStatus() == Client.Status.ARRIVING).count();
        int waitQueue = securityArea != null ? Math.max(securityArea.getWaitingToEnterCount(), (int) arrivingCount) : 0;

        int invCap = securityArea != null ? securityArea.getInvestigationCapacity() : 2;
        int invOcc = securityArea != null ? Math.min(securityArea.getInvestigationBufferAgents(), invCap) : 0;
        int invQueue = securityArea != null ? securityArea.getWaitingForInvestigationCount() : 0;

        int gateWait = localTower != null ? localTower.getPassengersWaitingToBoard() : 0;
        boolean bridgeBusy = localTower != null && localTower.isBridgeBusy();

        drawZoneBox(g2, 12, 12, 140, h - 75,
                "1. Entrada / Cola",
                "En fila para entrar: " + waitQueue,
                Color.WHITE, new Color(148, 163, 184), imgWaitingArea);

        int waitX = 160;
        int waitW = w - 430;
        drawZoneBox(g2, waitX, 12, waitW, h - 75,
                String.format("2. Sala de Espera (Búfer %d / %d)", waitOcc, waitCap),
                String.format("Capacidad configurada: %d (Máx: %d)", waitCap, SecurityArea.MAX_WAITING_CAPACITY),
                new Color(239, 246, 255), new Color(59, 130, 246), imgWaitingArea);

        int rightX = waitX + waitW + 10;
        int rightW = w - rightX - 12;
        int boxH = (h - 99) / 3;

        boolean signalActive = securityArea != null
                && (System.currentTimeMillis() - securityArea.getLastSignalTimestamp() < 2200);
        Color invBorder = signalActive ? new Color(220, 38, 38) : new Color(239, 68, 68);
        Color invBg = signalActive ? new Color(254, 226, 226) : new Color(254, 242, 242);

        drawZoneBox(g2, rightX, 12, rightW, boxH,
                String.format("3. Sala Interrogatorio (%d / %d)", invOcc, invCap),
                "Búfer 2 espacios (1 Policía + 1 Cliente) | Cola: " + invQueue,
                invBg, invBorder, imgInterrogationRoom);

        int slotY = 56;
        int slotW = (rightW - 26) / 2;
        int slotH = Math.max(46, boxH - 66);
        g2.setColor(new Color(255, 255, 255, 210));
        g2.fillRoundRect(rightX + 8, slotY, slotW, slotH, 10, 10);
        g2.fillRoundRect(rightX + 16 + slotW, slotY, slotW, slotH, 10, 10);
        g2.setColor(new Color(248, 113, 113));
        g2.drawRoundRect(rightX + 8, slotY, slotW, slotH, 10, 10);
        g2.drawRoundRect(rightX + 16 + slotW, slotY, slotW, slotH, 10, 10);
        g2.setFont(new Font("SansSerif", Font.BOLD, 9));
        g2.setColor(new Color(153, 27, 27));
        g2.drawString("Espacio 1: POLICIA", rightX + 12, slotY + 13);
        g2.drawString("Espacio 2: CLIENTE", rightX + 20 + slotW, slotY + 13);

        int restY = 12 + boxH + 10;
        drawZoneBox(g2, rightX, restY, rightW, boxH,
                "4. Descanso / Turno (8h)",
                clockText,
                new Color(236, 253, 245), new Color(16, 185, 129), imgRestArea);

        int gateY = restY + boxH + 10;
        long departedCount = clients.stream().filter(c -> c.getStatus() == Client.Status.ON_BOARD
                || c.getStatus() == Client.Status.FLYING
                || c.getStatus() == Client.Status.ARRIVED
                || c.getStatus() == Client.Status.TRANSFERRED).count();
        drawZoneBox(g2, rightX, gateY, rightW, boxH,
                String.format("5. Pasarela (%d / 1) y Salida", bridgeBusy ? 1 : 0),
                String.format("Fila: %d | Abordo/Vuelo: %d", gateWait, departedCount),
                new Color(245, 243, 255), new Color(139, 92, 246), imgBoardingGate);
    }

    private void drawZoneBox(Graphics2D g2, int x, int y, int w, int h,
            String title, String subtitle, Color bg, Color border, BufferedImage icon) {
        g2.setColor(bg);
        g2.fill(new RoundRectangle2D.Float(x, y, w, h, 14, 14));
        g2.setColor(border);
        g2.setStroke(new BasicStroke(2f));
        g2.draw(new RoundRectangle2D.Float(x, y, w, h, 14, 14));

        if (icon != null) {
            g2.drawImage(icon, x + 8, y + 6, 24, 24, null);
        }
        g2.setColor(new Color(15, 23, 42));
        g2.setFont(new Font("SansSerif", Font.BOLD, 11));
        g2.drawString(title, x + 36, y + 17);

        g2.setColor(new Color(71, 85, 105));
        g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
        g2.drawString(subtitle, x + 36, y + 31);
    }

    // Dibujo de los agentes y sus posiciones respetando estrictamente la capacidad de cada zona
    private void drawAgentsAndInteractions(Graphics2D g2, int w, int h) {
        int waitX = 160;
        int waitW = w - 430;
        int rightX = waitX + waitW + 10;
        int rightW = w - rightX - 12;
        int boxH = (h - 99) / 3;
        int restY = 12 + boxH + 10;
        int gateY = restY + boxH + 10;
        int slotW = (rightW - 26) / 2;
        int waitCap = securityArea != null ? securityArea.getCapacity() : 15;

        // 1. Clientes en la Sala de Espera: solo los que estan en el bufer de SecurityArea (maximo waitCap)
        List<Client> inWaitingRoom = securityArea != null ? securityArea.getWaitingSnapshot() : new ArrayList<>();
        int waitCols = Math.max(3, (waitW - 28) / 48);
        int maxWaitRows = Math.max(2, (h - 150) / 44);
        int waitingDrawn = 0;
        for (Client c : inWaitingRoom) {
            Client.Status st = c.getStatus();
            if (st == Client.Status.BEING_QUESTIONED || st == Client.Status.ARRESTED) {
                float targetX = rightX + 20 + slotW + (slotW - 32) / 2f;
                float targetY = 72;
                float[] pos = interpolatePosition(c.getName(), targetX, targetY);
                drawClientNode(g2, c, st, (int) pos[0], (int) pos[1]);
            } else if (st == Client.Status.WAITING && waitingDrawn < waitCap) {
                int col = waitingDrawn % waitCols;
                int row = (waitingDrawn / waitCols) % maxWaitRows;
                float targetX = waitX + 16 + col * 46;
                float targetY = 58 + row * 42;
                waitingDrawn++;
                float[] pos = interpolatePosition(c.getName(), targetX, targetY);
                // Mantener la posicion dentro de los limites de la Sala de Espera
                int clampedX = Math.max(waitX + 8, Math.min((int) pos[0], waitX + waitW - 38));
                drawClientNode(g2, c, st, clampedX, (int) pos[1]);
            }
        }

        // 2. Clientes en Cola de Entrada (ARRIVING) y en Pasarela / Salida
        int arrivingIdx = 0, boardingIdx = 0, processedIdx = 0;
        int maxArrivingRows = Math.max(3, (h - 150) / 38);
        List<Client> snapshotClients = new ArrayList<>(clients);
        for (Client c : snapshotClients) {
            Client.Status st = c.getStatus();
            if (st == Client.Status.ARRIVING) {
                int col = arrivingIdx % 2;
                int row = (arrivingIdx / 2) % maxArrivingRows;
                float targetX = 20 + col * 56;
                float targetY = 56 + row * 38;
                arrivingIdx++;
                float[] pos = interpolatePosition(c.getName(), targetX, targetY);
                int clampedX = Math.min((int) pos[0], 115);
                drawClientNode(g2, c, st, clampedX, (int) pos[1]);
            } else if (st == Client.Status.READY_TO_BOARD || st == Client.Status.BOARDING) {
                float targetX = rightX + 14 + (boardingIdx % 5) * 40;
                float targetY = gateY + 44 + ((boardingIdx / 5) % 2) * 34;
                boardingIdx++;
                // Saltar directo a la zona de pasarela para no cruzar encima de la Sala de Espera
                float[] pos = snapToZonePosition(c.getName(), targetX, targetY, rightX);
                drawClientNode(g2, c, st, (int) pos[0], (int) pos[1]);
            } else if (st == Client.Status.PROCESSED) {
                float targetX = rightX + 14 + (processedIdx % 5) * 40;
                float targetY = gateY + boxH - 42;
                processedIdx++;
                float[] pos = snapToZonePosition(c.getName(), targetX, targetY, rightX);
                drawClientNode(g2, c, st, (int) pos[0], (int) pos[1]);
            } else if (st == Client.Status.ON_BOARD || st == Client.Status.FLYING
                    || st == Client.Status.ARRIVED || st == Client.Status.TRANSFERRED
                    || st == Client.Status.CANCELLED) {
                // Ya abordaron el avion o finalizaron: se retiran del piso de la terminal
                agentPositions.remove(c.getName());
            }
        }

        List<Police> snapshotPolice = new ArrayList<>(policeList);
        int patrolIdx = 0, restIdx = 0;
        for (Police p : snapshotPolice) {
            Police.Status pst = p.getStatus();
            float targetX, targetY;

            if (pst == Police.Status.INVESTIGATING || pst == Police.Status.SIGNALING_SUSPECT) {
                targetX = rightX + 12 + (slotW - 32) / 2f;
                targetY = 72;
            } else if (pst == Police.Status.RESTING || pst == Police.Status.SHIFT_ENDED || pst == Police.Status.STOPPED) {
                targetX = rightX + 16 + (restIdx % 4) * 50;
                targetY = restY + 46 + ((restIdx / 4) % 2) * 36;
                restIdx++;
            } else {
                double angle = (animTick * 0.08) + patrolIdx * 1.7;
                int centerX = waitX + waitW / 2;
                int centerY = (h - 75) / 2 + 25;
                int radiusX = Math.max(36, (waitW - 85) / 2);
                int radiusY = Math.max(32, (h - 210) / 3);
                targetX = (float) (centerX + Math.cos(angle) * radiusX - 16);
                targetY = (float) (centerY + Math.sin(angle * 1.3) * radiusY);
                patrolIdx++;
            }

            float[] pos = interpolatePosition(p.getName(), targetX, targetY);
            Color badgeColor = (pst == Police.Status.SHIFT_ENDED || pst == Police.Status.STOPPED)
                    ? new Color(100, 116, 139) : new Color(15, 23, 42);
            drawAgentSprite(g2, imgPolice, (int) pos[0], (int) pos[1],
                    p.getName().replace("Policia-", "P"),
                    shortPoliceStatus(pst),
                    badgeColor);
        }

        if (securityArea != null) {
            Police invP = securityArea.getCurrentInvestigatingPolice();
            Client invC = securityArea.getCurrentQuestionedClient();
            if (invP != null && invC != null) {
                float[] pPos = agentPositions.get(invP.getName());
                float[] cPos = agentPositions.get(invC.getName());
                if (pPos != null && cPos != null) {
                    g2.setColor(new Color(220, 38, 38));
                    g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                            0, new float[]{6f, 4f}, animTick % 10));
                    g2.drawLine((int) pPos[0] + 16, (int) pPos[1] + 16, (int) cPos[0] + 16, (int) cPos[1] + 16);

                    g2.setFont(new Font("SansSerif", Font.BOLD, 10));
                    String alertTag = invC.isTerrorist()
                            ? "¡SEÑAL SOSPECHOSO (TERRORISTA)!"
                            : "¡SEÑAL DE REVISIÓN EN SALA (2/2)!";
                    int tagW = g2.getFontMetrics().stringWidth(alertTag) + 10;
                    int tagX = rightX + (rightW - tagW) / 2;
                    int tagY = 12 + boxH - 20;
                    g2.setColor(new Color(220, 38, 38));
                    g2.fillRoundRect(tagX, tagY, tagW, 16, 8, 8);
                    g2.setColor(Color.WHITE);
                    g2.drawString(alertTag, tagX + 5, tagY + 12);
                }
            }
        }
    }

    private void drawClientNode(Graphics2D g2, Client c, Client.Status st, int x, int y) {
        BufferedImage sprite = c.isTerrorist() ? imgTerrorist : imgClient;
        drawAgentSprite(g2, sprite, x, y,
                c.getName().replace("Cliente-", "C"),
                shortClientStatus(st),
                c.isTerrorist() ? new Color(220, 38, 38) : new Color(37, 99, 235));
    }

    private float[] snapToZonePosition(String key, float targetX, float targetY, int minZoneX) {
        float[] pos = agentPositions.get(key);
        if (pos == null || pos[0] < minZoneX) {
            pos = new float[]{targetX, targetY};
            agentPositions.put(key, pos);
            return pos;
        }
        return interpolatePosition(key, targetX, targetY);
    }

    private float[] interpolatePosition(String key, float targetX, float targetY) {
        float[] pos = agentPositions.get(key);
        if (pos == null) {
            pos = new float[]{targetX, targetY};
            agentPositions.put(key, pos);
        } else {
            pos[0] += (targetX - pos[0]) * 0.28f;
            pos[1] += (targetY - pos[1]) * 0.28f;
        }
        return pos;
    }

    private void drawAgentSprite(Graphics2D g2, BufferedImage sprite, int x, int y,
            String label, String stateText, Color tagColor) {
        if (sprite != null) {
            g2.drawImage(sprite, x, y, 30, 30, null);
        }
        g2.setFont(new Font("SansSerif", Font.BOLD, 9));
        FontMetrics fm = g2.getFontMetrics();
        String fullTag = label + ":" + stateText;
        int tw = fm.stringWidth(fullTag) + 6;
        g2.setColor(tagColor);
        g2.fillRoundRect(x + 15 - tw / 2, y + 29, tw, 12, 6, 6);
        g2.setColor(Color.WHITE);
        g2.drawString(fullTag, x + 18 - tw / 2, y + 38);
    }

    private String shortClientStatus(Client.Status s) {
        switch (s) {
            case ARRIVING:
                return "COLA";
            case WAITING:
                return "ESPERA";
            case BEING_QUESTIONED:
                return "INTERROG";
            case READY_TO_BOARD:
                return "LISTO";
            case ARRESTED:
                return "ARRESTO";
            case PROCESSED:
                return "PROCESADO";
            case BOARDING:
                return "PASARELA";
            case ON_BOARD:
                return "ABORDO";
            case FLYING:
                return "VOLANDO";
            case ARRIVED:
                return "DESTINO";
            case TRANSFERRED:
                return "REMOTO";
            default:
                return "CANCEL";
        }
    }

    private String shortPoliceStatus(Police.Status s) {
        switch (s) {
            case PATROLLING:
                return "PATRULLA";
            case SIGNALING_SUSPECT:
                return "SENAL!";
            case INVESTIGATING:
                return "INTERROG";
            case RESTING:
                return "DESCANSO";
            case SHIFT_ENDED:
                return "FIN_TURNO";
            default:
                return "STOP";
        }
    }

    private void drawLegendBar(Graphics2D g2, int w, int h) {
        int barY = h - 55;
        g2.setColor(new Color(15, 23, 42));
        g2.fillRoundRect(12, barY, w - 24, 44, 12, 12);

        g2.drawImage(imgClient, 18, barY + 8, 26, 26, null);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, 11));
        g2.drawString("Cliente (95%)", 48, barY + 25);

        g2.drawImage(imgTerrorist, 140, barY + 8, 26, 26, null);
        g2.setColor(new Color(252, 165, 165));
        g2.drawString("Terrorista (5%)", 170, barY + 25);

        g2.drawImage(imgPolice, 280, barY + 8, 26, 26, null);
        g2.setColor(new Color(147, 197, 253));
        g2.drawString("Policía (Turno 8h)", 310, barY + 25);

        String signal = securityArea != null ? securityArea.getLastSuspectSignal() : "Sin alertas";
        g2.setColor(new Color(250, 204, 21));
        g2.setFont(new Font("SansSerif", Font.BOLD, 10));
        g2.drawString(signal, 430, barY + 25);
    }
}
