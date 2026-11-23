// Enum con las variables de configuracion, limites y timers del simulador

public enum SimConfig {
    NUM_CLIENTS(30, 1, 500),
    NUM_POLICE(3, 1, 100),
    NUM_AIRPLANES(4, 1, 50),
    START_HOUR(6, 0, 23),
    WAITING_ROOM(15, 1, SecurityArea.MAX_WAITING_CAPACITY),
    INTERROGATION_ROOM(2, 1, SecurityArea.MAX_INVESTIGATION_CAPACITY),
    AIRPLANE_SEATS(10, 1, Airplane.MAX_SEATS),
    TOWER_RUNWAY(3, 1, ControlTower.MAX_RUNWAY),
    BOARDING_BRIDGE(1, 1, 1),
    CLIENT_WAITING_MS(3200, 50, 60000),
    POLICE_PATROL_MS(700, 50, 60000),
    POLICE_INVESTIGATION_MS(1200, 50, 60000),
    POLICE_REST_MS(1500, 50, 60000),
    POLICE_ROUNDS_REST(4, 1, 100),
    AIRPLANE_LOAD_MS(4000, 100, 60000),
    AIRPLANE_FLIGHT_MS(2500, 100, 60000),
    TOWER_COORD_MS(350, 50, 60000),
    BRIDGE_CROSSING_MS(250, 20, 60000),
    SIMULATED_HOUR_MS(2000, 200, 60000);

    private int value;
    private final int min;
    private final int max;

    SimConfig(int defaultValue, int min, int max) {
        this.value = defaultValue;
        this.min = min;
        this.max = max;
    }

    public int getValue() {
        return value;
    }

    public int getMax() {
        return max;
    }

    // Validacion y asignacion del valor capturado
    public void setFromText(String text, String name) {
        try {
            int parsed = Integer.parseInt(text.trim());
            if (parsed < min || parsed > max) {
                throw new IllegalArgumentException(
                        String.format("%s debe estar entre %d y %d (recibido: %d).", name, min, max, parsed));
            }
            this.value = parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Valor inválido en: " + name);
        }
    }
}
