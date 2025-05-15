import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.*;
import java.net.*;
import java.util.UUID;
import java.util.logging.*;

public class Suscriptor extends JFrame {
    private JPanel ipPanel;
    private JTextField ipField;
    private JPanel portPanel;
    private JTextField portField;
    private JPanel topicPanel;
    private JTextField topicField;
    private JButton startBtn;
    private JButton stopBtn;
    private JPanel mainPanel;
    private JTextArea logArea;

    private static final Logger logger = Logger.getLogger(Suscriptor.class.getName());
    private static JTextArea logAreaStatic;
    private Socket socket;
    private PrintWriter writer;
    private BufferedReader reader;
    private Thread listenerThread;
    private volatile boolean running;
    private final String subscriberId;

    public Suscriptor() {
        // Configurar la interfaz Swing
        setContentPane(mainPanel);
        setTitle("Suscriptor");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(400, 600);
        setLocationRelativeTo(null);
        logAreaStatic = logArea;

        // Generar ID único
        subscriberId = "SUB-" + UUID.randomUUID().toString().substring(0, 8);

        // Configurar el logger
        setupLogger();

        // Configurar acciones de los botones
        startBtn.addActionListener(e -> startListening());
        stopBtn.addActionListener(e -> stopListening());

        setVisible(true);
    }

    private void setupLogger() {
        try {
            FileHandler fileHandler = new FileHandler("suscriptor-" + subscriberId + ".log", true);
            fileHandler.setFormatter(new SimpleFormatter());
            logger.addHandler(fileHandler);
            logger.setLevel(Level.INFO);
        } catch (IOException e) {
            log("Error configurando logger: " + e.getMessage());
        }
    }

    private void log(String message) {
        String logMessage = "[" + subscriberId + "] " + message;
        logger.info(logMessage);
        if (logAreaStatic != null) {
            SwingUtilities.invokeLater(() -> logAreaStatic.append(logMessage + "\n"));
        }
    }

    private void startListening() {
        if (running) {
            log("El suscriptor ya está en ejecución");
            return;
        }
        try {
            String host = ipField.getText().trim();
            int port = Integer.parseInt(portField.getText().trim());
            String topic = topicField.getText().trim();

            if (host.isEmpty() || topic.isEmpty()) {
                log("Error: Complete todos los campos con valores válidos");
                return;
            }

            // Conectar al Broker
            socket = new Socket(host, port);
            writer = new PrintWriter(socket.getOutputStream(), true);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            log("Conectado al Broker en " + host + ":" + port);

            // Subscribirse al tema
            writer.println("SUBSCRIBE " + topic);
            String response = reader.readLine();
            if (!"OK".equals(response)) {
                log("Error al subscribirse: " + response);
                socket.close();
                return;
            }
            log("Subscrito al tema " + topic);

            // Escuchar mensajes
            running = true;
            listenerThread = new Thread(() -> {
                try {
                    String message;
                    while ((message = reader.readLine()) != null) {
                        log("Recibido en " + topic + ": " + message);
                    }
                } catch (IOException e) {
                    if (running) {
                        log("Conexión cerrada: " + e.getMessage());
                    }
                } finally {
                    stopListening();
                }
            });
            listenerThread.start();

        } catch (IOException | NumberFormatException e) {
            log("Error iniciando suscriptor: " + e.getMessage());
        }
    }

    private void stopListening() {
        if (!running) {
            log("El suscriptor ya está detenido");
            return;
        }
        running = false;
        if (listenerThread != null) {
            listenerThread.interrupt();
        }
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
                log("Error cerrando conexión: " + e.getMessage());
            }
        }
        log("Suscriptor detenido");
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Suscriptor::new);
    }
}