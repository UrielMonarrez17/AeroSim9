
import java.awt.Canvas;
import java.util.ArrayList;
import java.awt.Color;
import java.awt.Graphics;

public class SimulatorInterface extends Canvas {
    Graphics gall;
    private int counter = 0;
    ArrayList<Agent> agentes = new ArrayList<>();

    public SimulatorInterface() {
        this.setBackground(Color.GRAY);
    }

    @Override
    public void paint(Graphics g) {
        gall = g.create();

    }
}
