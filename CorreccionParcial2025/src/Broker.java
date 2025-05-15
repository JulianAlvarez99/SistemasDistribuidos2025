import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.*;
import java.net.*;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.*;

// Broker: Servidor que gestiona publicadores y suscriptores
public class Broker extends JFrame {
    private JTextArea logArea;
    private JButton startBtn;
    private JButton stopBtn;
    private JPanel mainPanel;

    private static final Logger logger = Logger.getLogger(Broker.class.getName());
    private static JTextArea logAreaStatic;
    private static final int PORT = 5000; // Puerto único para todas las conexiones
    private SubscriberRegistry subscriberRegistry;
    private ServerSocket serverSocket;
    private ExecutorService executor;
    private volatile boolean running;

    public Broker() {
        // Configurar la interfaz Swing
        setContentPane(mainPanel);
        setTitle("Broker");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(400, 600);
        setLocationRelativeTo(null);
        logAreaStatic = logArea;

        // Configurar el logger
        setupLogger();

        // Configurar acciones de los botones
        startBtn.addActionListener(e -> startServer());
        stopBtn.addActionListener(e -> stopServer());

        setVisible(true);
    }

    private void setupLogger() {
        try {
            FileHandler fileHandler = new FileHandler("broker.log", true);
            fileHandler.setFormatter(new SimpleFormatter());
            logger.addHandler(fileHandler);
            logger.setLevel(Level.INFO);
        } catch (IOException e) {
            log("Error configurando logger: " + e.getMessage());
        }
    }

    private static void log(String message) {
        logger.info(message);
        if (logAreaStatic != null) {
            SwingUtilities.invokeLater(() -> logAreaStatic.append(message + "\n"));
        }
    }

    private void startServer() {
        if (running) {
            log("El Broker ya está en ejecución");
            return;
        }
        running = true;
        subscriberRegistry = new SubscriberRegistry();
        executor = Executors.newCachedThreadPool();

        try {
            serverSocket = new ServerSocket(PORT);
            log("Broker iniciado en el puerto " + PORT);

            // Aceptar conexiones de clientes
            executor.submit(() -> {
                while (running) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        executor.submit(new ClientHandler(clientSocket, subscriberRegistry));
                    } catch (IOException e) {
                        if (running) {
                            log("Error aceptando conexión: " + e.getMessage());
                        }
                    }
                }
            });
        } catch (IOException e) {
            log("Error iniciando el Broker: " + e.getMessage());
            running = false;
        }
    }

    private void stopServer() {
        if (!running) {
            log("El Broker ya está detenido");
            return;
        }
        running = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
            if (executor != null) {
                executor.shutdownNow();
            }
            log("Broker detenido");
        } catch (IOException e) {
            log("Error deteniendo el Broker: " + e.getMessage());
        }
    }

    // Manejador de clientes (publicadores y suscriptores)
    private static class ClientHandler implements Runnable {
        private final Socket socket;
        private final SubscriberRegistry registry;
        private PrintWriter writer;
        private String clientId;

        public ClientHandler(Socket socket, SubscriberRegistry registry) {
            this.socket = socket;
            this.registry = registry;
            this.clientId = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
        }

        @Override
        public void run() {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                writer = new PrintWriter(socket.getOutputStream(), true);
                log("Cliente conectado: " + clientId);

                String message;
                while ((message = reader.readLine()) != null) {
                    processMessage(message);
                }
            } catch (IOException e) {
                log("Cliente desconectado: " + clientId + " (" + e.getMessage() + ")");
            } finally {
                registry.removeClient(clientId);
                try {
                    socket.close();
                } catch (IOException e) {
                    log("Error cerrando socket: " + e.getMessage());
                }
            }
        }

        private void processMessage(String message) {
            String[] parts = message.split(" ", 3);
            if (parts.length < 2) {
                writer.println("ERROR: Formato inválido");
                return;
            }

            String command = parts[0];
            String topic = parts[1];

            switch (command) {
                case "SUBSCRIBE":
                    registry.addSubscriber(topic, clientId, writer);
                    log("Suscriptor " + clientId + " registrado para " + topic);
                    writer.println("OK");
                    break;
                case "UNSUBSCRIBE":
                    registry.removeSubscriber(topic, clientId);
                    log("Suscriptor " + clientId + " desregistrado de " + topic);
                    writer.println("OK");
                    break;
                case "PUBLISH":
                    if (parts.length < 3) {
                        writer.println("ERROR: Mensaje requerido");
                        return;
                    }
                    String content = parts[2];
                    log("Recibido mensaje para " + topic + " de " + clientId + ": " + content);
                    registry.forwardMessage(topic, content);
                    writer.println("OK");
                    break;
                default:
                    writer.println("ERROR: Comando desconocido");
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Broker::new);
    }
}