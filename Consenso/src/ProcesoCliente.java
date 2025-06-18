import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;

public class ProcesoCliente {
    private static final String COORD_HOST = "localhost";
    private static final int    COORD_PORT = 5000;
    private static final String ARCHIVO    = "registro.txt";

    private final int id;
    private final Random rand = new Random();

    public ProcesoCliente(int id) {
        this.id = id;
    }

    public void start() {
        while (true) {
            try {
                // 1) Generar propuesta local (5–10 líneas)
                int propuesta = 5 + rand.nextInt(6);

                // 2) Ejecutar consenso para obtener L_max
                int L_max = ejecutarConsenso(propuesta);

                // 3) Espera aleatoria antes de solicitar sección crítica
                int espera = 5 + rand.nextInt(26);
                Thread.sleep(espera * 1000);

                // 4) Solicitar acceso a sección crítica
                try (Socket socket = new Socket(COORD_HOST, COORD_PORT);
                     DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                     DataInputStream in = new DataInputStream(socket.getInputStream())) {
                    out.writeUTF("EXCLUSION");  // etiqueta de exclusión
                    out.writeInt(id);

                    String permiso = in.readUTF();
                    if ("GRANT".equals(permiso)) {
                        for (int i = 1; i <= L_max; i++) {
                            escribirLinea(i, L_max);
                            Thread.sleep(1000);
                        }
                        out.writeUTF("DONE");
                    }
                }
            } catch (Exception e) {
                System.err.printf("Proceso %d: error — %s%n", id, e.getMessage());
            }
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
        int id = rand.nextInt(100) + 1; // ID aleatorio entre 1 y 1000
        new ProcesoCliente(id).start();
    }
}