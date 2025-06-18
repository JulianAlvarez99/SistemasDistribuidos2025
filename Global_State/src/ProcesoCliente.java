import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;

public class ProcesoCliente {
    private static final String COORD_HOST = "localhost";
    private static final int    COORD_PORT = 5000;
    private static final String ARCHIVO    = "registro.txt";

    private enum Estado { IDLE, REQUESTING, IN_CRITICAL }
    private Estado estado = Estado.IDLE;
    private final int id;
    private final Random rand = new Random();

    public ProcesoCliente(int id) {
        this.id = id;
    }

    public void start() {
        try {
            // 1) Generar propuesta local y obtener L_max
            int propuesta = 5 + rand.nextInt(6);
            int L_max = ejecutarConsenso(propuesta);

            // 2) Programar envío de estado global en tiempo aleatorio (5–30s)
            int delay = 5 + rand.nextInt(26);
            new Thread(() -> {
                try {
                    Thread.sleep(delay * 1000);
                    reportarEstado();
                } catch (Exception ignored) {}
            }).start();

            // 3) Espera antes de solicitar sección crítica
            int espera = 5 + rand.nextInt(26);
            Thread.sleep(espera * 1000);

            // 4) Solicitud de exclusión mutua
            estado = Estado.REQUESTING;
            try (Socket socket = new Socket(COORD_HOST, COORD_PORT);
                 DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                 DataInputStream  in  = new DataInputStream(socket.getInputStream()))
            {
                out.writeUTF("EXCLUSION");
                out.writeInt(id);
                String permiso = in.readUTF();
                if ("GRANT".equals(permiso)) {
                    estado = Estado.IN_CRITICAL;
                    for (int i = 1; i <= L_max; i++) {
                        escribirLinea(i, L_max);
                        Thread.sleep(1000);
                    }
                    out.writeUTF("DONE");
                    estado = Estado.IDLE;
                }
            }
        } catch (Exception e) {
            System.err.printf("Proceso %d: error — %s%n", id, e.getMessage());
        }
    }

    private int ejecutarConsenso(int propuesta) throws IOException {
        try (Socket socket = new Socket(COORD_HOST, COORD_PORT);
             DataOutputStream out = new DataOutputStream(socket.getOutputStream());
             DataInputStream  in  = new DataInputStream(socket.getInputStream()))
        {
            out.writeUTF("CONSENSUS");
            out.writeInt(id);
            out.writeInt(propuesta);
            int L_max = in.readInt();
            System.out.printf("Proceso %d: consenso decidió L_max = %d%n", id, L_max);
            return L_max;
        }
    }

    private void reportarEstado() {
        String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
        try (Socket socket = new Socket(COORD_HOST, COORD_PORT);
             DataOutputStream out = new DataOutputStream(socket.getOutputStream()))
        {
            out.writeUTF("STATE");
            out.writeInt(id);
            out.writeUTF(estado.name());
            out.writeUTF(timestamp);
            System.out.printf("Proceso %d: estado %s reportado a las %s.%n", id, estado, timestamp);
        } catch (IOException e) {
            System.err.println("Error reportando estado: " + e.getMessage());
        }
    }

    private void escribirLinea(int actual, int total) {
        String hora = new SimpleDateFormat("HH:mm:ss").format(new Date());
        String linea = String.format("Proceso %d|%s|%d/%d", id, hora, actual, total);
        System.out.println(linea);
        try (FileWriter fw = new FileWriter(ARCHIVO, true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out   = new PrintWriter(bw))
        {
            out.println(linea);
        } catch (IOException e) {
            System.err.println("Error escribiendo archivo: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        Random rand = new Random();
        int id = 1 + rand.nextInt(100); // ID aleatorio entre 1 y 100
        new ProcesoCliente(id).start();
    }
}
