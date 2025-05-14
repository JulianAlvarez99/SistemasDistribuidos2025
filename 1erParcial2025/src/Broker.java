import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Broker que actúa como middleware en la arquitectura publicador-suscriptor
public class Broker extends JFrame {
    private JTextArea logArea;
    private JButton startBtn;
    private JButton stopBtn;
    private JPanel mainPanel;

    private static final Logger logger = Logger.getLogger(Broker.class.getName());
    private static JTextArea logAreaStatic; // Referencia estática para el logArea
    private static final int CONTROL_PORT = 4999; // Puerto para registro de suscriptores
    private static final Map<String, Integer> TOPIC_INPUT_PORTS = new HashMap<>(); // Puertos de entrada (publicadores)
    private static final Map<String, Integer> TOPIC_OUTPUT_PORTS = new HashMap<>(); // Puertos de salida (suscriptores)
    private SubscriberRegistry subscriberRegistry; // Registro de suscriptores
    private ExecutorService executor; // Pool de hilos para manejar temas
    private ServerSocket controlServer; // Servidor para registro de suscriptores
    private volatile boolean running; // Estado del Broker
    private Thread controlThread; // Hilo para manejar registros

    // Constructor: inicializa la interfaz y el logging
    public Broker() {
        // Configurar la interfaz Swing
        setContentPane(mainPanel);
        setTitle("Broker");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(400, 600);
        setLocationRelativeTo(null);
        setVisible(true);
        logAreaStatic = logArea;

        // Configurar el logger
        setupLogger();

        // Inicializar puertos para los temas
        TOPIC_INPUT_PORTS.put("Noticia", 5000);
        TOPIC_INPUT_PORTS.put("Alerta", 5001);
        TOPIC_INPUT_PORTS.put("Actualizacion", 5002);
        TOPIC_INPUT_PORTS.put("Registro", 5003);
        TOPIC_OUTPUT_PORTS.put("Noticia", 6000);
        TOPIC_OUTPUT_PORTS.put("Alerta", 6001);
        TOPIC_OUTPUT_PORTS.put("Actualizacion", 6002);
        TOPIC_OUTPUT_PORTS.put("Registro", 6003);

        // Configurar acciones de los botones
        startBtn.addActionListener(e -> startBroker());
        stopBtn.addActionListener(e -> stopBroker());
    }

    // Configura el logger para escribir en broker.log
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

    // Registra un mensaje en el log y la interfaz
    public static void log(String message) {
        logger.info(message);
        if (logAreaStatic != null) {
            SwingUtilities.invokeLater(() -> logAreaStatic.append(message + "\n"));
        }
    }

    // Inicia el Broker: abre servidores para temas y registro de suscriptores
    private void startBroker() {
        if (running) {
            log("El Broker ya está en ejecución");
            return;
        }
        running = true;
        subscriberRegistry = new SubscriberRegistry();
        executor = Executors.newFixedThreadPool(TOPIC_INPUT_PORTS.size() + 1); // Hilos para temas + control

        // Iniciar un hilo por tema
        for (Map.Entry<String, Integer> entry : TOPIC_INPUT_PORTS.entrySet()) {
            String topic = entry.getKey();
            int inputPort = entry.getValue();
            int outputPort = TOPIC_OUTPUT_PORTS.get(topic);
            executor.submit(new TopicHandler(topic, inputPort, outputPort, subscriberRegistry));
        }

        // Iniciar el servidor de control para registro de suscriptores
        controlThread = new Thread(() -> {
            try {
                controlServer = new ServerSocket(CONTROL_PORT);
                log("Servidor de control iniciado en el puerto " + CONTROL_PORT);
                while (running) {
                    Socket client = controlServer.accept();
                    handleSubscriberRegistration(client);
                }
            } catch (IOException e) {
                if (running) {
                    log("Error en el servidor de control: " + e.getMessage());
                }
            }
        });
        controlThread.start();
        log("Broker iniciado");
    }

    // Detiene el Broker: cierra servidores y libera recursos
    private void stopBroker() {
        if (!running) {
            log("El Broker ya está detenido");
            return;
        }
        running = false;
        try {
            if (controlServer != null) {
                controlServer.close();
            }
            if (executor != null) {
                executor.shutdownNow();
            }
            log("Broker detenido");
        } catch (IOException e) {
            log("Error deteniendo el Broker: " + e.getMessage());
        }
    }

    // Maneja el registro/desregistro de suscriptores
    private void handleSubscriberRegistration(Socket client) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(client.getOutputStream()))) {
            String message = reader.readLine();
            if (message != null) {
                String[] parts = message.split(" ");
                if (parts.length == 3 && parts[0].equals("SUBSCRIBE")) {
                    String topic = parts[1];
                    int port = Integer.parseInt(parts[2]);
                    String address = client.getInetAddress().getHostAddress();
                    subscriberRegistry.addSubscriber(topic, address, port);
                    log("Suscriptor registrado: " + address + ":" + port + " para " + topic);
                    writer.write("OK\n");
                    writer.flush();
                } else if (parts.length == 3 && parts[0].equals("UNSUBSCRIBE")) {
                    String topic = parts[1];
                    int port = Integer.parseInt(parts[2]);
                    String address = client.getInetAddress().getHostAddress();
                    subscriberRegistry.removeSubscriber(topic, address, port);
                    log("Suscriptor desregistrado: " + address + ":" + port + " para " + topic);
                    writer.write("OK\n");
                    writer.flush();
                } else {
                    writer.write("ERROR: Formato inválido\n");
                    writer.flush();
                }
            }
        } catch (IOException | NumberFormatException e) {
            log("Error manejando registro de suscriptor: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Broker::new);
    }
}