import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public class Coordinador {
    private static final int PUERTO = 5000;
    private static final int TOTAL_PROCESOS = 3;

    // Consenso
    private final Map<Integer,Integer> propuestas = new ConcurrentHashMap<>();
    private final CountDownLatch latch = new CountDownLatch(TOTAL_PROCESOS);

    // Exclusión mutua
    private final Queue<ClienteHandler> cola = new ArrayDeque<>();
    private boolean ocupado = false;

    // Estados globales: ID -> "ESTADO@HH:mm:ss"
    private final Map<Integer,String> estados = new ConcurrentHashMap<>();

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

            switch (tipo) {
                case "CONSENSUS":
                    int idC = in.readInt();
                    int prop = in.readInt();
                    propuestas.put(idC, prop);
                    latch.countDown();
                    latch.await();
                    int L_max = Collections.min(propuestas.values());
                    out.writeInt(L_max);
                    socket.close();
                    break;

                case "EXCLUSION":
                    ClienteHandler handler = new ClienteHandler(socket, in, out);
                    synchronized (cola) {
                        cola.offer(handler);
                        if (!ocupado) atenderSiguiente();
                    }
                    break;

                case "STATE":
                    int pid = in.readInt();
                    String estado = in.readUTF();
                    String hora = in.readUTF();
                    estados.put(pid, estado + "@" + hora);
                    if (estados.size() == TOTAL_PROCESOS) guardarLog();
                    socket.close();
                    break;

                default:
                    socket.close();
            }
        } catch (Exception e) {
            System.err.println("Error en Coordinador: " + e.getMessage());
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    private void guardarLog() {
        String fechaHora = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss").format(new Date());
        String nombre = new SimpleDateFormat("dd-MM-yyyy").format(new Date()) + ".log";
        try (FileWriter fw = new FileWriter(nombre, true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw))
        {
            out.println("Estado global en " + fechaHora + ":");
            for (Map.Entry<Integer,String> e : estados.entrySet()) {
                String[] parts = e.getValue().split("@");
                out.printf("Proceso %d -> %s a las %s%n", e.getKey(), parts[0], parts[1]);
            }
            out.println("-------------------------");
            System.out.println("Log guardado en " + nombre);
            estados.clear();
        } catch (IOException e) {
            System.err.println("Error guardando log: " + e.getMessage());
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
                out.writeUTF("GRANT");
                if ("DONE".equals(in.readUTF())) {
                    // no-op
                }
            } catch (IOException e) {
                System.err.println("Error con proceso " + id + ": " + e.getMessage());
            } finally {
                try { socket.close(); } catch (IOException ignored) {}
            }
        }
    }

    public static void main(String[] args) {
        new Coordinador().iniciar();
    }
}