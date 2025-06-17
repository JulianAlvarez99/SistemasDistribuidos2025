import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;

public class ProcesoToken {

    private final int id;
    private final int port;
    private final String nextHost;
    private final int nextPort;
    private boolean tengoToken;
    private ServerSocket serverSocket;
    private volatile boolean quiereEscribir = false;

    public ProcesoToken(int id, int port, String nextHost, int nextPort, boolean inicioConToken) throws IOException {
        this.id = id;
        this.port = port;
        this.nextHost = nextHost;
        this.nextPort = nextPort;
        this.tengoToken = inicioConToken;
        serverSocket = new ServerSocket(port);
    }

    public void iniciar() {
        new Thread(this::esperarToken).start();
        new Thread(this::intentarEscribir).start();
    }

    private void esperarToken() {
        while (true) {
            try (Socket socket = serverSocket.accept();
                 DataInputStream in = new DataInputStream(socket.getInputStream())) {
                String mensaje = in.readUTF();
                if ("TOKEN".equals(mensaje)) {
                    tengoToken = true;
                    System.out.println("Proceso " + id + " recibió el token.");
                    manejarTokenRecibido();  // gestionar si escribe o no
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void manejarTokenRecibido() {
        new Thread(() -> {
            try {
                // Espera hasta 1 segundo para ver si va a escribir
                Thread.sleep(1000);

                // Si el proceso no está escribiendo ni va a escribir, pasa el token
                if (!deseaEscribirAhora()) {
                    tengoToken = false;
                    enviarToken();
                }

            } catch (InterruptedException | IOException e) {
                e.printStackTrace();
            }
        }).start();
    }


    private void intentarEscribir() {
        Random rand = new Random();
        while (true) {
            try {
                Thread.sleep((10 + rand.nextInt(56)) * 1000); // Espera entre 10-60s
                quiereEscribir = true;
                System.out.println("Proceso " + id + " desea escribir...");

                while (!tengoToken) {
                    Thread.sleep(100); // Esperar el token
                }

                int lineas = 1 + rand.nextInt(6);
                for (int i = 1; i <= lineas; i++) {
                    escribirLinea(i, lineas);
                    Thread.sleep(1000);
                }

                quiereEscribir = false;
                tengoToken = false;
                enviarToken();
            } catch (InterruptedException | IOException e) {
                e.printStackTrace();
            }
        }
    }

    private boolean deseaEscribirAhora() {
        return quiereEscribir;
    }

    private void escribirLinea(int actual, int total) {
        String linea = "Proceso " + id + "|" + new SimpleDateFormat("HH:mm:ss").format(new Date()) + "|" + actual + "/" + total;
        System.out.println(linea);
        try (FileWriter fw = new FileWriter("archivo.txt", true);
             BufferedWriter bw = new BufferedWriter(fw)) {
            bw.write(linea);
            bw.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void enviarToken() throws IOException {
        try (Socket socket = new Socket(nextHost, nextPort);
             DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
            out.writeUTF("TOKEN");
            System.out.println("Proceso " + id + " envió el token al proceso siguiente.");
        }
    }

    public static void main(String[] args) throws IOException {
//        if (args.length != 5) {
//            System.out.println("Uso: java ProcesoToken <id> <miPuerto> <siguienteHost> <siguientePuerto> <tengoToken:true|false>");
//            return;
//        }
//
//        int id = Integer.parseInt(args[0]);
//        int miPuerto = Integer.parseInt(args[1]);
//        String siguienteHost = args[2];
//        int siguientePuerto = Integer.parseInt(args[3]);
//        boolean tengoToken = Boolean.parseBoolean(args[4]);

//        ProcesoToken proceso = new ProcesoToken(id, miPuerto, siguienteHost, siguientePuerto, tengoToken);
        Random random_id = new Random();
        int id = random_id.nextInt(1000);
        ProcesoToken proceso = new ProcesoToken(id, 5004, "localhost", 5000, true);
        proceso.iniciar();
    }
}
