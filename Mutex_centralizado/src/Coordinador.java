import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class Coordinador {
    private static final int PUERTO = 5000;

    // Cola de espera para procesos que solicitan escribir
    private final Queue<ClienteHandler> cola = new ArrayDeque<>();
    private boolean ocupado = false;

    public void iniciar() {
        System.out.println("Coordinador iniciado en puerto " + PUERTO);
        try (ServerSocket serverSocket = new ServerSocket(PUERTO)) {
            while (true) {
                Socket cliente = serverSocket.accept();
                ClienteHandler handler = new ClienteHandler(cliente);
                synchronized (cola) {
                    cola.offer(handler);
                    if (ocupado) {
                        System.out.println("Nuevo proceso conectado. Encolado en espera.");
                    }
                    else{
                        atenderSiguiente();
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error en Coordinador: " + e.getMessage());
        }
    }

    private void atenderSiguiente() {
        if (!cola.isEmpty()) {
            ClienteHandler siguiente = cola.poll();
            ocupado = true;
            new Thread(() -> siguiente.atender(this)).start();
        } else {
            ocupado = false;
        }
    }

    private class ClienteHandler {
        private final Socket socket;
        private int id;

        ClienteHandler(Socket socket) {
            this.socket = socket;
        }

        void atender(Coordinador coord) {
            try (DataInputStream in = new DataInputStream(socket.getInputStream());
                 DataOutputStream out = new DataOutputStream(socket.getOutputStream()))
            {
                id = in.readInt();
                System.out.println("Proceso " + id + " obtuvo permiso para escribir.");

                // Enviar permiso
                out.writeUTF("GRANT");

                // Esperar notificación de fin
                String mensaje = in.readUTF();
                if ("DONE".equals(mensaje)) {
                    System.out.println("Proceso " + id + " terminó de escribir.");
                }

            } catch (IOException e) {
                System.err.println("Error con el proceso " + id + ": " + e.getMessage());
            } finally {
                try {
                    socket.close();
                } catch (IOException ignored) {}
                synchronized (cola) {
                    ocupado = false;
                    atenderSiguiente(); // Pasar al siguiente
                }
            }
        }
    }

    public static void main(String[] args) {
        new Coordinador().iniciar();
    }
}
