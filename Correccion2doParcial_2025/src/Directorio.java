import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Directorio {
    private final List<NodoInfo> nodos = new ArrayList<>();
    private final Set<String> nodosListos = new HashSet<>();
    private final String IP_DIRECTORIO = "localhost";
    private final int PUERTO_DIRECTORIO = 5000;
    private JTextArea logArea;
    private JPanel mainPanel;
    private JScrollPane scrollPanel;
    private boolean nodoAgregado = false;
    private boolean tokenActivo = false;

    private static class NodoInfo {
        final String nombre;
        final String ip;
        final int puerto;

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

    public Directorio() throws IOException {
        JFrame frame = new JFrame("Directorio");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(400, 300);
        logArea = new JTextArea();
        logArea.setEditable(false);
        frame.add(new JScrollPane(logArea));
        frame.setVisible(true);

        ServerSocket serverSocket = new ServerSocket(PUERTO_DIRECTORIO);
        logArea.append("Directorio en puerto " + PUERTO_DIRECTORIO + "\n");

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
                case "LISTO":
                    String nombre = partes[1];
                    synchronized (nodosListos) {
                        nodosListos.add(nombre);
                    }
                    logArea.append("Nodo listo: " + nombre + "\n");
                    out.println("OK");
                    reconfigurarAnillo();
                    break;
                case "REGISTRAR":
                    registrarNodo(partes[1], partes[2], Integer.parseInt(partes[3]));
                    out.println("OK");
                    break;
                case "DESREGISTRAR":
                    desregistrarNodo(partes[1]);
                    out.println("OK");
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
        nodoAgregado = true;
        actualizarGUI();
        // Esperar a que el nodo envíe LISTO antes de reconfigurar
    }

    private synchronized void desregistrarNodo(String nombre) {
        NodoInfo nodoEliminado = null;
        for (NodoInfo nodo : nodos) {
            if (nodo.nombre.equals(nombre)) {
                nodoEliminado = nodo;
                break;
            }
        }
        if (nodoEliminado != null) {
            nodos.remove(nodoEliminado);
            synchronized (nodosListos) {
                nodosListos.remove(nombre);
            }
            nodoAgregado = true;
            actualizarGUI();
            reconfigurarAnillo();
        }
    }

    private void reconfigurarAnillo() {
        synchronized (nodosListos) {
            if (nodos.isEmpty()) {
                tokenActivo = false;
                logArea.append("Anillo vacío, token desactivado\n");
                return;
            }

            // Verificar si todos los nodos están listos
            boolean todosListos = true;
            for (NodoInfo nodo : nodos) {
                if (!nodosListos.contains(nodo.nombre)) {
                    todosListos = false;
                    logArea.append("Nodo " + nodo.nombre + " no está listo, esperando...\n");
                    break;
                }
            }

            if (!todosListos) {
                // Reintentar después de un retraso
                new Thread(() -> {
                    try {
                        Thread.sleep(1000);
                        reconfigurarAnillo();
                    } catch (InterruptedException e) {
                        logArea.append("Error al reintentar reconfiguración: " + e.getMessage() + "\n");
                    }
                }).start();
                return;
            }

            logArea.append("Reconfigurando anillo con " + nodos.size() + " nodos\n");

            // Fase 1: Enviar DISCARD_TOKEN a todos los nodos
            for (NodoInfo nodo : nodos) {
                enviarMensajeDescartarToken(nodo);
            }

            // Fase 2: Configurar el anillo
            boolean configuracionExitosa = true;
            for (int i = 0; i < nodos.size(); i++) {
                NodoInfo actual = nodos.get(i);
                NodoInfo siguiente = (nodos.size() == 1) ? actual : nodos.get((i + 1) % nodos.size());
                String mensaje = "SIGUIENTE " + siguiente.nombre + " " + siguiente.ip + " " + siguiente.puerto;
                if (!enviarMensajeSiguiente(actual, mensaje)) {
                    configuracionExitosa = false;
                    break;
                }
            }

            // Fase 3: Generar nuevo token si la configuración fue exitosa
            if (configuracionExitosa) {
                NodoInfo primerNodo = nodos.getFirst();
                enviarMensajeGenerarToken(primerNodo);
                tokenActivo = true;
                logArea.append("Indicando a " + primerNodo.nombre + " que genere un nuevo token\n");
            }
            nodoAgregado = false;
        }
    }

    private boolean enviarMensajeDescartarToken(NodoInfo nodo) {
        for (int i = 0; i < 3; i++) {
            try (Socket s = new Socket(nodo.ip, nodo.puerto);
                 PrintWriter out = new PrintWriter(s.getOutputStream(), true);
                 BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()))) {
                out.println("DISCARD_TOKEN");
                String respuesta = in.readLine();
                logArea.append("Respuesta de " + nodo.nombre + " a DISCARD_TOKEN: " + respuesta + "\n");
                return true;
            } catch (IOException e) {
                logArea.append("Intento " + (i + 1) + " fallido al enviar DISCARD_TOKEN a " + nodo.nombre + ": " + e.getMessage() + "\n");
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ignored) {}
            }
        }
        logArea.append("Error: No se pudo contactar a " + nodo.nombre + ", desregistrando\n");
        desregistrarNodo(nodo.nombre);
        return false;
    }

    private boolean enviarMensajeSiguiente(NodoInfo nodo, String mensaje) {
        for (int i = 0; i < 3; i++) {
            try (Socket s = new Socket(nodo.ip, nodo.puerto);
                 PrintWriter out = new PrintWriter(s.getOutputStream(), true);
                 BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()))) {
                out.println(mensaje);
                String respuesta = in.readLine();
                logArea.append("Respuesta de " + nodo.nombre + " a " + mensaje + ": " + respuesta + "\n");
                return true;
            } catch (IOException e) {
                logArea.append("Intento " + (i + 1) + " fallido al enviar mensaje a " + nodo.nombre + ": " + e.getMessage() + "\n");
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ignored) {}
            }
        }
        logArea.append("Error: No se pudo contactar a " + nodo.nombre + ", desregistrando\n");
        desregistrarNodo(nodo.nombre);
        return false;
    }

    private boolean enviarMensajeGenerarToken(NodoInfo nodo) {
        for (int i = 0; i < 3; i++) {
            try (Socket s = new Socket(nodo.ip, nodo.puerto);
                 PrintWriter out = new PrintWriter(s.getOutputStream(), true);
                 BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()))) {
                out.println("GENERAR_TOKEN");
                String respuesta = in.readLine();
                logArea.append("Respuesta de " + nodo.nombre + " a GENERAR_TOKEN: " + respuesta + "\n");
                return true;
            } catch (IOException e) {
                logArea.append("Intento " + (i + 1) + " fallido al enviar GENERAR_TOKEN a " + nodo.nombre + ": " + e.getMessage() + "\n");
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ignored) {}
            }
        }
        logArea.append("Error: No se pudo contactar a " + nodo.nombre + ", desregistrando\n");
        desregistrarNodo(nodo.nombre);
        return false;
    }

    private void actualizarGUI() {
        StringBuilder sb = new StringBuilder("Nodos en anillo:\n");
        nodos.forEach(n -> sb.append(n.toString()).append("\n"));
        logArea.setText(sb.toString());
    }

    public static void main(String[] args) throws IOException {
        new Directorio();
    }
}

