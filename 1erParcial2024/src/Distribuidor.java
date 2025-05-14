import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.*;
import java.util.stream.Collectors;

public class Distribuidor extends JFrame {
    private JPanel configPanel;
    private JPanel mainPanel;
    private JLabel prodThreadsLabel;
    private JTextField prodThreadsField;
    private JTextField consThreadsField;
    private JLabel consThreadsLabel;
    private JLabel bufferLabel;
    private JTextField bufferField;
    private JLabel prodTimeLabel;
    private JTextField prodTimeField;
    private JLabel consTimeLabel;
    private JTextField consTimeField;
    private JTextArea logArea;
    private JButton runBtn;

    private static BlockingQueue<String> buffer; // Buffer para palabras
    private static int consThreads;
    private static int prodThreads;
    private static int prodTimePerChar; // ms por carácter para productores
    private static int consTimePerChar; // ms por carácter para consumidores
    private static final File inputDir = new File("C:/Users/julia/Desktop/SistDistribuidos/inText");
    private static final String SENTINEL = "__SENTINEL__";
    private static final Logger logger = Logger.getLogger(Distribuidor.class.getName());
    private static JTextArea logAreaStatic; // Referencia estática para el logArea
    private static MulticastSocket multicastSocket; // Socket multicast compartido
    private static final String MULTICAST_ADDRESS = "230.0.0.1";
    private static final int MULTICAST_PORT = 4446;
    private static final Object bufferLock = new Object();

    public Distribuidor() {
        setupLogger();
        setContentPane(mainPanel);
        setTitle("Distribuidor");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(300, 600);
        setLocationRelativeTo(null);
        setResizable(true);
        setVisible(true);
        logAreaStatic = logArea;

        // Inicializar MulticastSocket
        try {
            multicastSocket = new MulticastSocket();
            multicastSocket.setTimeToLive(32); // TTL para permitir multicast en la red
        } catch (IOException e) {
            log("Error inicializando MulticastSocket: " + e.getMessage());
        }

        runBtn.addActionListener(e -> {
            try {
                prodThreads = Integer.parseInt(prodThreadsField.getText());
                consThreads = Integer.parseInt(consThreadsField.getText());
                int bufferSize = Integer.parseInt(bufferField.getText());
                prodTimePerChar = Integer.parseInt(prodTimeField.getText());
                consTimePerChar = Integer.parseInt(consTimeField.getText());

                buffer = new ArrayBlockingQueue<>(bufferSize);
                log("Configuración: Productores=" + prodThreads + ", Consumidores=" + consThreads +
                        ", Tamaño Buffer=" + bufferSize + ", Tiempo Prod/char=" + prodTimePerChar +
                        "ms, Tiempo Cons/char=" + consTimePerChar + "ms");

                distributionProcess();
            } catch (NumberFormatException ex) {
                log("Error: Ingrese valores numéricos válidos");
            }
        });
    }

    private void setupLogger() {
        try {
            FileHandler fileHandler = new FileHandler("distribuidor.log", true);
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

    private void distributionProcess() {
        ExecutorService prodPool = Executors.newFixedThreadPool(prodThreads);
        ExecutorService consPool = Executors.newFixedThreadPool(consThreads);

        try {
            File[] files = inputDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".txt"));
            if (files == null || files.length == 0) {
                log("No se encontraron archivos .txt en " + inputDir);
                return;
            }
            ///*****************************************************************************************************////
            /// Este bloque de codigo es para dividir productores por cada archivo de texto
            /// Ver que la clase productor corresponde a este bloque
//            int filesPerProducer = files.length / prodThreads + 1;
//            for (int i = 0; i < prodThreads; i++) {
//                int start = i * filesPerProducer;
//                int end = Math.min(start + filesPerProducer, files.length);
//                File[] producerFiles = Arrays.copyOfRange(files, start, end);
//                prodPool.submit(new Productor(producerFiles, i));
//            }
            ///
            ///*****************************************************************************************************////
            /// Este bloque de codigo es para dividir la cantidad de palabras del archivo entre productores
            /// Ver que la clase productor corresponde a este bloque
            /// Tomar el primer archivo (puedes ajustar para seleccionar uno específico)
            File inputFile = files[0];
            log("Procesando archivo: " + inputFile.getName());
            String content = Files.readString(inputFile.toPath());
            List<String> words = Arrays.stream(content.split("\\s+"))
                    .filter(word -> !word.isEmpty())
                    .collect(Collectors.toList());

            // Contador compartido para el índice de palabras
            AtomicInteger wordIndex = new AtomicInteger(0);

            //  Iniciar productores
            for (int i = 0; i < prodThreads; i++) {
                prodPool.submit(new Productor(words, wordIndex, i));
            }
            ///*****************************************************************************************************////

            for (int i = 0; i < consThreads; i++) {
                consPool.submit(new Consumidor(i));
            }
            prodPool.shutdown();
            if (!prodPool.awaitTermination(10, TimeUnit.MINUTES)) {
                log("Timeout esperando a productores, forzando terminación");
                prodPool.shutdownNow();
            }
        } catch (Exception e) {
            log("Error en el proceso: " + e.getMessage());
        } finally {
            try {
                log("Enviando " + consThreads + " sentinelas a los consumidores");
                for (int i = 0; i < consThreads; i++) {
                    buffer.put(SENTINEL);
                }
            } catch (InterruptedException e) {
                log("Error enviando sentinelas: " + e.getMessage());
            }

            try {
                consPool.shutdown();
                if (!consPool.awaitTermination(10, TimeUnit.MINUTES)) {
                    log("Timeout esperando a consumidores, forzando terminación");
                    consPool.shutdownNow();
                }
            } catch (Exception e) {
                log("Error terminando consumidores: " + e.getMessage());
            }

            // Cerrar MulticastSocket
            if (multicastSocket != null) {
                multicastSocket.close();
                multicastSocket = null;
            }
        }
        log("Distribución completada");
    }
    ///*****************************************************************************************************////
    /// Esta clase de productor es para la division de archivos enter productores
//    static class Productor implements Runnable {
//        private final File[] files;
//        private final int id;
//
//        Productor(File[] files, int id) {
//            this.files = files;
//            this.id = id;
//        }
//
//        @Override
//        public void run() {
//            try {
//                for (File file : files) {
//                    log("Productor " + id + " procesando archivo: " + file.getName());
//                    String content = Files.readString(file.toPath());
//                    String[] words = content.split("\\s+");
//                    for (String word : words) {
//                        if (!word.isEmpty()) {
//                            if (buffer.remainingCapacity() == 0) {
//                                log("WAIT PRODUCTOR_" + id);
//                            }
//                            buffer.put(word);
//                            log("ADD " + word + "\nBUFFER SIZE: " + buffer.size());
//                            Thread.sleep(word.length() * prodTimePerChar);
//                        }
//                    }
//                }
//                log("Productor " + id + " terminó de procesar archivos");
//            } catch (IOException | InterruptedException e) {
//                log("Error en Productor " + id + ": " + e.getMessage());
//                Thread.currentThread().interrupt();
//            }
//        }
//    }
    ///*****************************************************************************************************////
    ///
    ///*****************************************************************************************************////
    /// Esta clase es para la division de palabras de un unico archivo entre varios productores
    /// OJO hay que usar sincronized porque sino los productores no se turnan en orden (medio que no funca eso)
    /// Despues igual se ordenan pero el problema son las primeras interacciones con el buffer
    /// Conviene usar un solo productor por archivo

    static class Productor implements Runnable {
        private final List<String> words;
        private final AtomicInteger wordIndex;
        private final int id;

        Productor(List<String> words, AtomicInteger wordIndex, int id) {
            this.words = words;
            this.wordIndex = wordIndex;
            this.id = id;
        }

        @Override
        public void run() {
            try {
                while (true) {
                    // Obtener el índice de la próxima palabra
                    int index = wordIndex.getAndIncrement();
                    if (index >= words.size()) {
                        break; // No hay más palabras
                    }
                    String word = words.get(index);
                    synchronized (bufferLock) {
                        if (buffer.remainingCapacity() == 0) {
                            log("WAIT PRODUCTOR_" + id);
                        }
                        buffer.put(word);
                        log("ADD " + word + "\nBUFFER SIZE: " + buffer.size());
                    }
                    Thread.sleep(word.length() * prodTimePerChar);
                }
                log("Productor " + id + " terminó de procesar palabras");
            } catch (InterruptedException e) {
                log("Error en Productor " + id + ": " + e.getMessage());
                Thread.currentThread().interrupt();
            }
        }
    }

    static class Consumidor implements Runnable {
        private final int id;

        Consumidor(int id) {
            this.id = id;
        }

        @Override
        public void run() {
            try {
                while (true) {
                    if (buffer.isEmpty()) {
                        log("WAIT CONSUMIDOR_" + id);
                    }
                    String word = buffer.take();
                    log("REMOVE (" + word + ")\nBUFFER SIZE: " + buffer.size());
                    sendMulticastMessage(word);
                    if (word.equals(SENTINEL)) {
                        log("Consumidor " + id + " recibió SENTINEL, terminando");
                        break;
                    }
                    Thread.sleep(word.length() * consTimePerChar);
                }
                log("CONSUMIDOR_" + id + " FINISHED");
            } catch (InterruptedException e) {
                log("Error en Consumidor " + id + ": " + e.getMessage());
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void sendMulticastMessage(String message) {
        if (multicastSocket == null) {
            log("Error: MulticastSocket no inicializado");
            return;
        }
        try {
            byte[] data = message.getBytes(StandardCharsets.UTF_8);
            DatagramPacket packet = new DatagramPacket(data, data.length,
                    InetAddress.getByName(MULTICAST_ADDRESS), MULTICAST_PORT);
            multicastSocket.send(packet);
            log("SENT " + message);
        } catch (IOException e) {
            log("Error enviando mensaje multicast: " + e.getMessage());
        }
    }

    public static void main(String[] args) {SwingUtilities.invokeLater(Distribuidor::new);}
}