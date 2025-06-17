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
//        while (true) {
            try {
                // Espera aleatoria 5–30 s antes de pedir acceso
                int espera = 5 + rand.nextInt(26);
                Thread.sleep(espera * 1000);

                try (Socket socket = new Socket(COORD_HOST, COORD_PORT);
                     DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                     DataInputStream  in  = new DataInputStream(socket.getInputStream()))
                {
                    // 1) Enviar ID
                    out.writeInt(id);

                    // 2) Esperar permiso
                    String permiso = in.readUTF();
                    if ("GRANT".equals(permiso)) {
                        // 3) Escribir 5–10 líneas a 1 s por línea
                        int lineas = 5 + rand.nextInt(6);
                        for (int i = 1; i <= lineas; i++) {
                            escribirLinea(i, lineas);
                            Thread.sleep(1000);
                        }
                        // 4) Avisar DONE
                        out.writeUTF("DONE");
                    }
                }
            } catch (Exception e) {
                System.err.printf("Proceso %d: error — %s%n", id, e.getMessage());
            }
//        }
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
        Random random_id = new Random();
        int id = random_id.nextInt(1000);
//        int id = Integer.parseInt(args[0]);
        new ProcesoCliente(id).start();
    }
}
