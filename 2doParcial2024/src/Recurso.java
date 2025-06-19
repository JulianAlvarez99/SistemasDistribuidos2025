import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;

public class Recurso {
    private String nombre;
    private PrintWriter log;

    public Recurso(String nombre, int puerto) throws IOException {
        this.nombre = nombre;
        log = new PrintWriter(new FileWriter(nombre + ".log", true));
        ServerSocket serverSocket = new ServerSocket(puerto);
        registrar("localhost", 5000, "REGISTRAR " + nombre);
        registrar("localhost", 5001, "REGISTRAR " + nombre + " localhost " + puerto);
        System.out.println("Recurso " + nombre + " en puerto " + puerto);

        while (true) {
            Socket socket = serverSocket.accept();
            // Manejar cada cliente en un hilo separado
            new Thread(() -> manejarCliente(socket)).start();
        }
    }

    private void manejarCliente(Socket socket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            // Procesar mensajes INICIO y FIN en la misma conexión
            while (true) {
                String mensaje = in.readLine();
                if (mensaje == null) break; // Cliente cerró la conexión
                String[] partes = mensaje.split(" ");
                String comando = partes[0];
                String cliente = partes[1];

                registrarLog(cliente, comando);
                out.println("OK");

                // Si se recibe FIN, salir del bucle para cerrar la conexión
                if (comando.equals("FIN")) {
                    break;
                }
            }
        } catch (IOException e) {
            System.err.println("Error al manejar cliente en " + nombre + ": " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                System.err.println("Error al cerrar socket: " + e.getMessage());
            }
        }
    }

    private void registrar(String host, int puerto, String mensaje) throws IOException {
        try (Socket socket = new Socket(host, puerto);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            out.println(mensaje);
            if (!in.readLine().equals("OK")) throw new IOException("Registro fallido");
        }
    }

    private void registrarLog(String cliente, String accion) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        log.println(timestamp + " CLIENTE " + cliente + " " + accion);
        log.flush();
    }

    public static void main(String[] args) throws IOException {
        Random rand_id = new Random();
        String id = String.valueOf(rand_id.nextInt(100));
        new Recurso(id, 5102);
    }
}