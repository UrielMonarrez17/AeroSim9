import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;

// Clase base para crear ventanas con tablas
public abstract class Tabla extends JFrame implements Runnable {
    private final DefaultTableModel model;
    private final JTable table;
    private final long refreshMillis;
    private volatile boolean running = false;
    private Thread workerThread;

    // Configuracion de la ventana y columnas de la tabla
    public Tabla(String windowTitle, String[] headers, int x, int y, int width, int height, long refreshMillis) {
        super(windowTitle);
        this.refreshMillis = refreshMillis;
        this.model = new DefaultTableModel(headers, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        this.table = new JTable(model);
        this.table.setRowHeight(21);
        this.table.setFont(new Font("SansSerif", Font.PLAIN, 12));
        this.table.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 11));
        this.table.setFillsViewportHeight(true);

        JPanel container = new JPanel(new BorderLayout());
        TitledBorder border = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(148, 163, 184), 1), windowTitle);
        border.setTitleFont(new Font("SansSerif", Font.BOLD, 12));
        border.setTitleColor(new Color(15, 23, 42));
        container.setBorder(border);
        container.add(new JScrollPane(table), BorderLayout.CENTER);

        setContentPane(container);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setBounds(x, y, width, height);
    }

    protected abstract List<Object[]> buildRows();

    // Actualizacion de filas de la tabla
    public void setContent(List<Object[]> rows) {
        SwingUtilities.invokeLater(() -> {
            model.setRowCount(0);
            for (Object[] row : rows) {
                model.addRow(row);
            }
        });
    }

    // Inicio del hilo de la ventana
    public void startWindowThread(String threadName) {
        if (running) return;
        running = true;
        setVisible(true);
        workerThread = new Thread(this, threadName);
        workerThread.setDaemon(true);
        workerThread.start();
    }

    public void stopWindowThread() {
        running = false;
        if (workerThread != null) {
            workerThread.interrupt();
        }
        SwingUtilities.invokeLater(this::dispose);
    }

    @Override
    public void run() {
        try {
            while (running && !Thread.currentThread().isInterrupted()) {
                List<Object[]> rows = buildRows();
                setContent(rows);
                Thread.sleep(refreshMillis);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public JTable getTable() {
        return table;
    }
}
