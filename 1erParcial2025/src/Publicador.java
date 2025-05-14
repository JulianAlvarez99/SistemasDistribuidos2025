import javax.swing.*;
import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.*;

// Publicador que envía mensajes a un tema en intervalos aleatorios
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
    private JTextArea logArea; // Área para mostrar logs
    private JPanel mainPanel;
    private JComboBox topicBox;

    private static final Logger logger = Logger.getLogger(Publicador.class.getName());
    private static JTextArea logAreaStatic; // Referencia estática para el logArea
    private final String publisherId; // ID único del publicador
    private ScheduledExecutorService scheduler; // Programador de eventos
    private volatile boolean running; // Estado del publicador
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    public Publicador() {
        // Configurar la interfaz Swing
        setContentPane(mainPanel);
        setTitle("Publicador");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(400, 600);
        setLocationRelativeTo(null);
        setVisible(true);
        logAreaStatic = logArea;

        // Generar ID único para el publicador
        publisherId = "PUB-" + UUID.randomUUID().toString().substring(0, 8);

        // Configurar el logger
        setupLogger();

        // Configurar acciones de los botones
        startBtn.addActionListener(e -> startPublishing());
        stopBtn.addActionListener(e -> stopPublishing());

        // Deshabilitar ActionListener de los campos (no necesarios)
        ipField.setActionCommand(null);
        portField.setActionCommand(null);
        topicField.setActionCommand(null);
        segField.setActionCommand(null);
    }

    // Configura el logger para escribir en publicador.log
    private void setupLogger() {
        try {
            FileHandler fileHandler = new FileHandler("publicador.log", true);
            fileHandler.setFormatter(new SimpleFormatter());
            logger.addHandler(fileHandler);
            logger.setLevel(Level.INFO);
        } catch (IOException e) {
            log("Error configurando logger: " + e.getMessage());
        }
    }

    // Registra un mensaje en el log y la interfaz
    private static void log(String message) {
        logger.info(message);
        if (logAreaStatic != null) {
            SwingUtilities.invokeLater(() -> logAreaStatic.append(message + "\n"));
        }
    }

    // Inicia la publicación de eventos
    private void startPublishing() {
        if (running) {
            log("El publicador ya está en ejecución");
            return;
        }
        try {
            String host = ipField.getText().trim();
            int port = Integer.parseInt(portField.getText().trim());
//            String topic = topicField.getText().trim();
            String topic = Objects.requireNonNull(topicBox.getSelectedItem()).toString().trim();
            int maxInterval = Integer.parseInt(segField.getText().trim());

            if (host.isEmpty() || topic.isEmpty() || maxInterval <= 0) {
                log("Error: Complete todos los campos con valores válidos");
                return;
            }

            running = true;
            scheduler = Executors.newScheduledThreadPool(1);
            Random random = new Random();

            // Programar eventos a intervalos aleatorios
            scheduler.scheduleWithFixedDelay(() -> {
                if (!running) return;
                // Generar mensaje con el formato requerido
                String timestamp = LocalDateTime.now().format(formatter);
                String message = String.format("Evento %s generado por el publicador %s el %s", topic, publisherId, timestamp);
                try (Socket socket = new Socket(host, port);
                     BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()))) {
                    writer.write(message + "\n");
                    writer.flush();
                    log("Publicado en " + topic + ": " + message);
                } catch (IOException e) {
                    log("Error publicando en " + topic + ": " + e.getMessage());
                }
                // Programar el próximo evento con un intervalo aleatorio
            }, 0, random.nextInt(maxInterval * 1000), TimeUnit.MILLISECONDS);

            log("Publicador iniciado para " + topic + " en " + host + ":" + port);
        } catch (NumberFormatException e) {
            log("Error: Puerto y intervalo deben ser números válidos");
        }
    }

    // Detiene la publicación
    private void stopPublishing() {
        if (!running) {
            log("El publicador ya está detenido");
            return;
        }
        running = false;
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        log("Publicador detenido");
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Publicador::new);
    }
}