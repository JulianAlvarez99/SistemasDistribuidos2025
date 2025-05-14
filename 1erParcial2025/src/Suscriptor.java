import javax.swing.*;
import java.io.*;
import java.net.*;
import java.util.Objects;
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
    private JComboBox topicBox;

    private static final Logger logger = Logger.getLogger(Suscriptor.class.getName());
    private static JTextArea logAreaStatic; // Referencia estática para el logArea
    private ServerSocket serverSocket;
    private volatile boolean running;
    private Thread listenThread;

    public Suscriptor() {
        // Configurar la interfaz Swing
        setContentPane(mainPanel);
        setTitle("Suscriptor");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(400, 600);
        setLocationRelativeTo(null);
        setVisible(true);
        logAreaStatic = logArea;

        // Configurar el logger
        setupLogger();

        // Configurar acciones de los botones
        startBtn.addActionListener(e -> startListening());
        stopBtn.addActionListener(e -> stopListening());

        // Deshabilitar ActionListener de los campos (no necesarios)
        ipField.setActionCommand(null);
        portField.setActionCommand(null);
        topicField.setActionCommand(null);
    }

    // Configura el logger para escribir en suscriptor.log
    private void setupLogger() {
        try {
            FileHandler fileHandler = new FileHandler("suscriptor.log", true);
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

    // Inicia el suscriptor: registra en el Broker y escucha mensajes
    private void startListening() {
        if (running) {
            log("El suscriptor ya está en ejecución");
            return;
        }
        try {
            String host = ipField.getText().trim();
            int port = Integer.parseInt(portField.getText().trim());
//            String topic = topicField.getText().trim();
            String topic = Objects.requireNonNull(topicBox.getSelectedItem()).toString().trim();
            int controlPort = 4999; // Puerto de control del Broker

            if (host.isEmpty() || topic.isEmpty()) {
                log("Error: Complete todos los campos con valores válidos");
                return;
            }

            // Registrar en el Broker
            try (Socket socket = new Socket(host, controlPort);
                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                 BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                writer.write("SUBSCRIBE " + topic + " " + port + "\n");
                writer.flush();
                String response = reader.readLine();
                log("Respuesta del Broker: " + response);
                if (!response.equals("OK")) {
                    log("Error: No se pudo registrar en el Broker");
                    return;
                }
            }

            // Iniciar escucha de mensajes
            running = true;
            serverSocket = new ServerSocket(port);
            listenThread = new Thread(() -> {
                while (running) {
                    try (Socket client = serverSocket.accept();
                         BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()))) {
                        String message = reader.readLine();
                        if (message != null) {
                            log("Recibido en " + topic + ": " + message);
                        }
                    } catch (IOException e) {
                        if (running) {
                            log("Error recibiendo mensaje para " + topic + ": " + e.getMessage());
                        }
                    }
                }
            });
            listenThread.start();
            log("Suscriptor inscrito para " + topic + " en puerto " + port);
        } catch (IOException | NumberFormatException e) {
            log("Error iniciando suscriptor: " + e.getMessage());
        }
    }

    // Detiene el suscriptor
    private void stopListening() {
        if (!running) {
            log("El suscriptor ya está detenido");
            return;
        }
        running = false;
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                log("Error cerrando servidor: " + e.getMessage());
            }
        }
        if (listenThread != null) {
            listenThread.interrupt();
        }
        log("Suscriptor detenido");
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Suscriptor::new);
    }
}