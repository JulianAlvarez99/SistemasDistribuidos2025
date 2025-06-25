import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

public class Directorio {
    private final List<NodoInfo> nodos = new ArrayList<>();
    private final Set<String> notificados = new HashSet<>();
    private JTextArea logArea;
    private JScrollPane scrollPanel;
    private JPanel mainPanel;


    private static class NodoInfo {
        final String nombre;
        final String ip;
        final int puerto;
        NodoInfo(String nombre, String ip, int puerto) {
            this.nombre = nombre;
            this.ip = ip;
            this.puerto = puerto;
        }
        @Override public String toString() { return nombre + " (" + ip + ":" + puerto + ")"; }
    }

    public Directorio(int puertoDirectorio) throws IOException {
        JFrame frame = new JFrame("Directorio");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(400, 300);
        logArea = new JTextArea(); logArea.setEditable(false);
        frame.add(new JScrollPane(logArea));
        frame.setVisible(true);

        ServerSocket serverSocket = new ServerSocket(puertoDirectorio);
        logArea.append("Directorio en puerto " + puertoDirectorio + "\n");

        new Thread(() -> {
            while (true) {
                try {
                    Socket socket = serverSocket.accept();
                    handleConnection(socket);
                } catch (IOException e) {
                    SwingUtilities.invokeLater(() -> logArea.append("Error Directorio: " + e.getMessage() + "\n"));
                }
            }
        }, "Director-Acceptor").start();
    }

    private void handleConnection(Socket socket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            String[] partes = in.readLine().split(" ");
            String cmd = partes[0];
            switch (cmd) {
                case "REGISTRAR":
                    registrarNodo(partes[1], partes[2], Integer.parseInt(partes[3]));
                    out.println("OK");
                    break;
                case "READY":
                    reconfigurarAnillo();
                    out.println("OK");
                    break;
                case "DESREGISTRAR":
                    desregistrarNodo(partes[1]);
                    out.println("OK");
                    break;
                case "RESOLVER":
                    resolver(partes[1], out);
                    break;
                case "RESOLVER_POR_PUERTO":
                    resolverPorPuerto(partes[1], out);
                    break;
                default:
                    out.println("COMANDO_DESCONOCIDO");
            }
        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> logArea.append("Error al procesar conexión: " + e.getMessage() + "\n"));
        }
    }

    private synchronized void registrarNodo(String nombre, String ip, int puerto) {
        nodos.add(new NodoInfo(nombre, ip, puerto));
        SwingUtilities.invokeLater(this::actualizarGUI);
    }

    private synchronized void desregistrarNodo(String nombre) {
        nodos.removeIf(n -> n.nombre.equals(nombre));
        SwingUtilities.invokeLater(this::actualizarGUI);
        reconfigurarAnillo();
    }

    private synchronized void reconfigurarAnillo() {
        SwingUtilities.invokeLater(this::actualizarGUI);
        notificados.clear();
        for (int i = 0; i < nodos.size(); i++) {
            NodoInfo actual = nodos.get(i);
            NodoInfo siguiente = nodos.get((i + 1) % nodos.size());
            notificarSiguiente(actual, siguiente.nombre);
        }
    }

    private void notificarSiguiente(NodoInfo nodo, String sigNombre) {
        for (int i = 0; i < 5; i++) {
            try (Socket s = new Socket(nodo.ip, nodo.puerto);
                 PrintWriter out = new PrintWriter(s.getOutputStream(), true);
                 BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()))) {
                out.println("SIGUIENTE " + sigNombre);
                in.readLine();
                return;
            } catch (IOException e) {
                logArea.append("Intento fallido SIGUIENTE a " + nodo.nombre + ": " + e.getMessage() + "\n");
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            }
        }
        logArea.append("Error notificar SIGUIENTE a " + nodo.nombre + "\n");
    }

    private void resolver(String nombre, PrintWriter out) {
        for (NodoInfo n : nodos) if (n.nombre.equals(nombre)) { out.println(n.ip + ":" + n.puerto); return; }
        out.println("DESCONOCIDO");
    }

    private void resolverPorPuerto(String puertoStr, PrintWriter out) {
        try {
            int p = Integer.parseInt(puertoStr);
            for (NodoInfo n : nodos) if (n.puerto == p) { out.println(n.nombre); return; }
        } catch (NumberFormatException ignored) {}
        out.println("DESCONOCIDO");
    }

    private void actualizarGUI() {
        StringBuilder sb = new StringBuilder("Nodos en anillo:\n");
        nodos.forEach(n -> sb.append(n.toString()).append("\n"));
        logArea.setText(sb.toString());
    }

    public static void main(String[] args) throws IOException {
        new Directorio(5000);
    }
}
