import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

public class Directorio {
    private List<NodoInfo> nodos = new ArrayList<>();
    private JTextArea logArea;
    private JScrollPane scrollPanel;
    private JPanel mainPanel;
    private Set<String> notificados = new HashSet<>();

    private static class NodoInfo {
        String nombre;
        String ip;
        int puerto;

        NodoInfo(String nombre, String ip, int puerto) {
            this.nombre = nombre;
            this.ip = ip;
            this.puerto = puerto;
        }

        @Override
        public String toString() {
            return nombre + " (" + ip + ":" + puerto + ")";
        }
    }

    public Directorio(int puerto) throws IOException {
        iniciarGUI();

        ServerSocket serverSocket = new ServerSocket(puerto);
        logArea.append("Directorio en puerto " + puerto + "\n");

        new Thread(() -> {
            while (true) {
                try {
                    Socket socket = serverSocket.accept();
                    handleConnection(socket);
                } catch (IOException e) {
                    SwingUtilities.invokeLater(() ->
                            logArea.append("Error Directorio: " + e.getMessage() + "\n"));
                }
            }
        }, "Director-Acceptor").start();
    }

    private void handleConnection(Socket socket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            String[] partes = in.readLine().split(" ");
            String comando = partes[0];

            switch (comando) {
                case "REGISTRAR":
                    registrarNodo(partes[1], partes[2], Integer.parseInt(partes[3]), out);
                    break;
                case "READY":
                    marcaYNotificaReady(partes[1], out);
                    break;
                case "RESOLVER":
                    resolver(partes[1], out);
                    break;
                case "RESOLVER_POR_PUERTO":
                    resolverPorPuerto(partes[1], out);
                    break;
                case "DESREGISTRAR":
                    desregistrarNodo(partes[1], out);
                    break;
                default:
                    out.println("COMANDO_DESCONOCIDO");
            }
        } catch (IOException e) {
            SwingUtilities.invokeLater(() ->
                    logArea.append("Error al procesar conexión: " + e.getMessage() + "\n"));
        }
    }

    private void registrarNodo(String nombre, String ip, int puerto, PrintWriter out) {
        nodos.add(new NodoInfo(nombre, ip, puerto));
        SwingUtilities.invokeLater(this::actualizarGUI);
        // No notificar aún: esperamos READY
        out.println("OK");
    }

    private void marcaYNotificaReady(String nombre, PrintWriter out) {
        // Un nodo confirma estar listo: ya está en nodos, lanzamos notificación
        SwingUtilities.invokeLater(this::actualizarGUI);
        notificarNodos();
        if (nodos.size() == 1) {
            indicarGenerarToken(nodos.get(0));
        }
        out.println("OK");
    }

    private void desregistrarNodo(String nombre, PrintWriter out) {
        nodos.removeIf(n -> n.nombre.equals(nombre));
        SwingUtilities.invokeLater(this::actualizarGUI);
        notificarNodos();
        if (!nodos.isEmpty()) {
            indicarGenerarToken(nodos.get(0));
        }
        out.println("OK");
    }

    private void resolver(String nombre, PrintWriter out) {
        for (NodoInfo nodo : nodos) {
            if (nodo.nombre.equals(nombre)) {
                out.println(nodo.ip + ":" + nodo.puerto);
                return;
            }
        }
        out.println("DESCONOCIDO");
    }

    private void resolverPorPuerto(String puertoStr, PrintWriter out) {
        try {
            int puerto = Integer.parseInt(puertoStr);
            for (NodoInfo nodo : nodos) {
                if (nodo.puerto == puerto) {
                    out.println(nodo.nombre);
                    return;
                }
            }
            out.println("DESCONOCIDO");
        } catch (NumberFormatException e) {
            out.println("DESCONOCIDO");
        }
    }

    private void notificarNodos() {
        notificados.clear();
        for (int i = 0; i < nodos.size(); i++) {
            NodoInfo nodo = nodos.get(i);
            String siguiente = (i + 1 < nodos.size()) ? nodos.get(i + 1).nombre : nodos.get(0).nombre;
            String clave = nodo.nombre + ":" + siguiente;
            if (notificados.contains(clave)) continue;
            notificados.add(clave);
            int intentos = 5;
            boolean notificado = false;
            while (intentos > 0 && !notificado) {
                try (Socket socket = new Socket(nodo.ip, nodo.puerto);
                     PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                     BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                    out.println("SIGUIENTE " + siguiente);
                    in.readLine();
                    notificado = true;
                } catch (IOException e) {
                    logArea.append("Intento fallido al notificar a " + nodo.nombre + ": " + e.getMessage() + "\n");
                    intentos--;
                    if (intentos > 0) {
                        try { Thread.sleep(1000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                    }
                }
            }
            if (!notificado) {
                logArea.append("Error al notificar a " + nodo.nombre + ": No se pudo conectar\n");
            }
        }
    }

    private void indicarGenerarToken(NodoInfo nodo) {
        int intentos = 5;
        boolean notificado = false;
        while (intentos > 0 && !notificado) {
            try (Socket socket = new Socket(nodo.ip, nodo.puerto);
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                out.println("GENERAR_TOKEN");
                in.readLine();
                notificado = true;
            } catch (IOException e) {
                logArea.append("Intento fallido al indicar generar token a " + nodo.nombre + ": " + e.getMessage() + "\n");
                intentos--;
                if (intentos > 0) { try { Thread.sleep(1000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); } }
            }
        }
        if (!notificado) {
            logArea.append("Error al indicar generar token a " + nodo.nombre + ": No se pudo conectar\n");
        }
    }

    private void iniciarGUI() {
        JFrame frame = new JFrame("Directorio");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(400, 300);
        logArea = new JTextArea();
        logArea.setEditable(false);
        frame.add(new JScrollPane(logArea));
        frame.setVisible(true);
        actualizarGUI();
    }

    private void actualizarGUI() {
        StringBuilder sb = new StringBuilder("Nodos conectados:\n");
        for (NodoInfo nodo : nodos) {
            sb.append(nodo.toString()).append("\n");
        }
        logArea.setText(sb.toString());
    }

    public static void main(String[] args) throws IOException {
        new Directorio(5000);
    }
}
