import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.*;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.concurrent.*;
import java.util.logging.*;

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
    private static final File outputDir = new File("C:/Users/julia/Desktop/SistDistribuidos/outText");
    private static final String SENTINEL = "__SENTINEL__";
    private static final Logger logger = Logger.getLogger(Distribuidor.class.getName());
    private static JTextArea logAreaStatic; // Referencia estática para el logArea

    //TODO: Hacer parte del recepcion con los sockets multicast
    public Distribuidor() {
        // Configurar logger
        setupLogger();
        setContentPane(mainPanel);
        setTitle("Distribuidor");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(300, 600);
        setLocationRelativeTo(null);
        setResizable(true);
        setVisible(true);
        // Inicializar logAreaStatic
        logAreaStatic = logArea;

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
            e.printStackTrace();
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
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }

            File[] files = inputDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".txt"));
            if (files == null || files.length == 0) {
                log("No se encontraron archivos .txt en " + inputDir);
                return;
            }

            // Dividir archivos entre productores
            //TODO: Revisar que se pueda hacer dos versiones, productores por archivo o productores para palabras
            int filesPerProducer = files.length / prodThreads + 1;
            for (int i = 0; i < prodThreads; i++) {
                int start = i * filesPerProducer;
                int end = Math.min(start + filesPerProducer, files.length);
                File[] producerFiles = Arrays.copyOfRange(files, start, end);
                prodPool.submit(new Productor(producerFiles, i));
            }

            // Iniciar consumidores
            for (int i = 0; i < consThreads; i++) {
                consPool.submit(new Consumidor(i));
            }

            // Esperar a que los productores terminen
            prodPool.shutdown();
            if (!prodPool.awaitTermination(10, TimeUnit.MINUTES)) {
                log("Timeout esperando a productores, forzando terminación");
                prodPool.shutdownNow();
            }

        } catch (Exception e) {
            log("Error en el proceso: " + e.getMessage());
        } finally {
            // Enviar sentinelas en un bloque finally para garantizar que los consumidores terminen
            try {
                log("Enviando " + consThreads + " sentinelas a los consumidores");
                for (int i = 0; i < consThreads; i++) {
                    buffer.put(SENTINEL);
                }
            } catch (InterruptedException e) {
                log("Error enviando sentinelas: " + e.getMessage());
            }

            // Esperar a que los consumidores terminen
            try {
                consPool.shutdown();
                if (!consPool.awaitTermination(10, TimeUnit.MINUTES)) {
                    log("Timeout esperando a consumidores, forzando terminación");
                    consPool.shutdownNow();
                }
            } catch (Exception e) {
                log("Error terminando consumidores: " + e.getMessage());
            }
        }
        log("Distribución completada");
    }

    static class Productor implements Runnable {
        private final File[] files;
        private final int id;

        Productor(File[] files, int id) {
            this.files = files;
            this.id = id;
        }

        @Override
        public void run() {
            try {
                for (File file : files) {
                    log("Productor " + id + " procesando archivo: " + file.getName());
                    String content = Files.readString(file.toPath());
                    String[] words = content.split("\\s+");
                    for (String word : words) {
                        if (!word.isEmpty()) {
                            // Verificar si el buffer está lleno antes de put
                            if (buffer.remainingCapacity() == 0) {
                                log("WAIT PRODUCTOR_" + id);
                            }
                            buffer.put(word);
                            log("ADD " + word + "\nBUFFER SIZE: " + buffer.size());
                            Thread.sleep(word.length() * prodTimePerChar); // Simular tiempo de procesamiento
                        }
                    }
                }
                log("Productor " + id + " terminó de procesar archivos");
            } catch (IOException | InterruptedException e) {
                log("Error en Productor " + id + ": " + e.getMessage());
                Thread.currentThread().interrupt(); // Restaurar el estado de interrupción
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
                File outputFile = new File(outputDir, "output_consumidor_" + id + ".txt");
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile, true))) {
                    while (true) {
                        // Verificar si el buffer está vacío antes de take
                        if (buffer.isEmpty()) {
                            log("WAIT CONSUMIDOR_" + id);
                        }
                        String word = buffer.take();
                        log("REMOVE (" + word + ")\nBUFFER SIZE: " + buffer.size());
                        if (word.equals(SENTINEL)) {
                            log("Consumidor " + id + " recibió SENTINEL, terminando");
                            break;
                        }
                        writer.write(word + "\n");
                        writer.flush();
                        Thread.sleep(word.length() * consTimePerChar); // Simular tiempo de procesamiento
                    }
                }
                log("CONSUMIDOR_" + id + " FINISHED");
            } catch (IOException | InterruptedException e) {
                log("Error en Consumidor " + id + ": " + e.getMessage());
                Thread.currentThread().interrupt(); // Restaurar el estado de interrupción
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Distribuidor::new);
    }
}