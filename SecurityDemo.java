import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Demo aislada: no implementa aviones, torre ni interfaz grafica. */
public class SecurityDemo {
    public static void main(String[] args) throws InterruptedException {
        SecurityArea security = new SecurityArea(10);
        Police[] officers = {new Police(1, security), new Police(2, security)};
        List<Client> clients = new ArrayList<>();
        Random random = new Random(9);
        try {
            for (Police officer : officers) officer.start();
            for (int i = 1; i <= 40; i++) {
                Client client = new Client(i, security, 1200, random);
                clients.add(client);
                client.start();
            }
            for (Client client : clients) {
                client.join();
                System.out.printf("%s | terrorista=%s | %s%n",
                        client.getName(), client.isTerrorist(), client.getStatus());
            }
        } finally {
            for (Client client : clients) client.interrupt();
            for (Police officer : officers) officer.interrupt();
            for (Client client : clients) client.join();
            for (Police officer : officers) officer.join();
        }
    }
}
