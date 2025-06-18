import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class Coordinador {
    private static final int PUERTO = 5000;
    private static final int TOTAL_PROCESOS = 3;

    private final Map<Integer,Integer> propuestas = new ConcurrentHashMap<>();
    private final CountDownLatch latch = new CountDownLatch(TOTAL_PROCESOS);

    private final Queue<ClienteHandler> cola = new ArrayDeque<>();
    private boolean ocupado = false;

    public void iniciar() {
        System.out.println("Coordinador iniciado en puerto " + PUERTO);
        try (ServerSocket serverSocket = new ServerSocket(PUERTO)) {
            while (true) {
                Socket cliente = serverSocket.accept();
                new Thread(() -> manejarConexion(cliente)).start();
            }
        } catch (IOException e) {
            System.err.println("Error en Coordinador: " + e.getMessage());
        }
    }

    private void manejarConexion(Socket socket) {
        try {
            DataInputStream in  = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());

            String tipo = in.readUTF();
            if ("CONSENSUS".equals(tipo)) {
                int id   = in.readInt();
                int prop = in.readInt();
                propuestas.put(id, prop);
                System.out.printf("Coordinador: recibió propuesta %d de proceso %d%n", prop, id);
                latch.countDown();

                latch.await();
                int L_max = Collections.min(propuestas.values());
                out.writeInt(L_max);
                socket.close();

            } else if ("EXCLUSION".equals(tipo)) {
                ClienteHandler handler = new ClienteHandler(socket, in, out);
                synchronized (cola) {
                    cola.offer(handler);
                    if (!ocupado) atenderSiguiente();
                }
            } else {
                socket.close(); // petición desconocida
            }
        } catch (Exception e) {
            System.err.println("Error en Coordinador: " + e.getMessage());
            try { socket.close(); } catch (IOException ignore) {}
        }
    }

    private void atenderSiguiente() {
        if (!cola.isEmpty()) {
            ClienteHandler siguiente = cola.poll();
            ocupado = true;
            new Thread(() -> {
                siguiente.atender();
                synchronized (cola) {
                    ocupado = false;
                    atenderSiguiente();
                }
            }).start();
        }
    }

    private class ClienteHandler {
        private final Socket socket;
        private final DataInputStream in;
        private final DataOutputStream out;
        private int id;

        ClienteHandler(Socket socket, DataInputStream in, DataOutputStream out) {
            this.socket = socket;
            this.in     = in;
            this.out    = out;
        }

        void atender() {
            try {
                id = in.readInt();
                System.out.println("Proceso " + id + " obtuvo permiso para escribir.");
                out.writeUTF("GRANT");
                String msg = in.readUTF();
                if ("DONE".equals(msg)) {
                    System.out.println("Proceso " + id + " terminó de escribir.");
                }
            } catch (IOException e) {
                System.err.println("Error con el proceso " + id + ": " + e.getMessage());
            } finally {
                try { socket.close(); } catch (IOException ignored) {}
            }
        }
    }

    public static void main(String[] args) {
        new Coordinador().iniciar();
    }
}