import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.*;

// Publicador: Cliente que envía mensajes a un tema
public class Publicador extends JFrame {
    private JPanel ipPanel;
    private JTextField ipField;
    private JPanel portPanel;
    private JTextField portField;
    private JPanel topicPanel;
    private JTextField topicField;
    private JPanel segPanel;
    private JTextField segField;
    private JButton startBtn;
    private JButton stopBtn;
    private JTextArea logArea;
    private JPanel mainPanel;

    private static final Logger logger = Logger.getLogger(Publicador.class.getName());
    private static JTextArea logAreaStatic;
    private final String publisherId;
    private Socket socket;
    private PrintWriter writer;
    private BufferedReader reader;
    private ScheduledExecutorService scheduler;
    private volatile boolean running;
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    public Publicador() {
        // Configurar la interfaz Swing
        setContentPane(mainPanel);
        setTitle("Publicador");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(400, 600);
        setLocationRelativeTo(null);
        logAreaStatic = logArea;

        // Generar ID único
        publisherId = "PUB-" + UUID.randomUUID().toString().substring(0, 8);

        // Configurar el logger
        setupLogger();

        // Configurar acciones de los botones
        startBtn.addActionListener(e -> startPublishing());
        stopBtn.addActionListener(e -> stopPublishing());

        setVisible(true);
    }

    private void setupLogger() {
        try {
            FileHandler fileHandler = new FileHandler("publicador-" + publisherId + ".log", true);
            fileHandler.setFormatter(new SimpleFormatter());
            logger.addHandler(fileHandler);
            logger.setLevel(Level.INFO);
        } catch (IOException e) {
            log("Error configurando logger: " + e.getMessage());
        }
    }

    private void log(String message) {
        String logMessage = "[" + publisherId + "] " + message;
        logger.info(logMessage);
        if (logAreaStatic != null) {
            SwingUtilities.invokeLater(() -> logAreaStatic.append(logMessage + "\n"));
        }
    }

    private void startPublishing() {
        if (running) {
            log("El publicador ya está en ejecución");
            return;
        }
        try {
            String host = ipField.getText().trim();
            int port = Integer.parseInt(portField.getText().trim());
            String topic = topicField.getText().trim();
            int maxInterval = Integer.parseInt(segField.getText().trim());

            if (host.isEmpty() || topic.isEmpty() || maxInterval <= 0) {
                log("Error: Complete todos los campos con valores válidos");
                return;
            }

            // Conectar al Broker
            socket = new Socket(host, port);
            writer = new PrintWriter(socket.getOutputStream(), true);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            log("Conectado al Broker en " + host + ":" + port);

            running = true;
            scheduler = Executors.newScheduledThreadPool(1);
            Random random = new Random();

            // Programar eventos
            scheduler.scheduleWithFixedDelay(() -> {
                if (!running) return;
                String timestamp = LocalDateTime.now().format(formatter);
                String message = String.format("Evento %s generado por el publicador %s el %s", topic, publisherId, timestamp);
                writer.println("PUBLISH " + topic + " " + message);
                log("Publicado en " + topic + ": " + message);
                try {
                    String response = reader.readLine();
                    if (!"OK".equals(response)) {
                        log("Error del Broker: " + response);
                    }
                } catch (IOException e) {
                    log("Error recibiendo respuesta: " + e.getMessage());
                    stopPublishing();
                }
            }, 0, random.nextInt(maxInterval * 1000) + 100, TimeUnit.MILLISECONDS);

        } catch (IOException | NumberFormatException e) {
            log("Error iniciando publicador: " + e.getMessage());
        }
    }

    private void stopPublishing() {
        if (!running) {
            log("El publicador ya está detenido");
            return;
        }
        running = false;
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
                log("Error cerrando conexión: " + e.getMessage());
            }
        }
        log("Publicador detenido");
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Publicador::new);
    }
}