import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.logging.*;

public class Receptor {
    private static final File outputDir = new File("C:/Users/julia/Desktop/JulianFacu/SistemasDistribuidosl/outText");
    private static final String SENTINEL = "__SENTINEL__";
    private static final String MULTICAST_ADDRESS = "230.0.0.1";
    private static final int MULTICAST_PORT = 4446;
    private final int id;
    private MulticastSocket multicastSocket;
    private Thread listenerThread;
//    private Logger logger;

    public Receptor(int id) {
        this.id = id;
//        setupLogger();
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
    }

//    private void setupLogger() {
//        try {
//            logger = Logger.getLogger(Receptor.class.getName() + "_" + id);
//            FileHandler fileHandler = new FileHandler("receptor_" + id + ".log", true);
//            fileHandler.setFormatter(new SimpleFormatter());
//            logger.addHandler(fileHandler);
//            logger.setLevel(Level.INFO);
//        } catch (IOException e) {
//            System.err.println("Error configurando logger para Receptor " + id + ": " + e.getMessage());
//        }
//    }
//
//    private void log(String message) {
//        logger.info(message);
//    }

    public void startMulticast() {
        try {
            multicastSocket = new MulticastSocket(MULTICAST_PORT);
            InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
            multicastSocket.joinGroup(group);

            listenerThread = new Thread(() -> {
                File outputFile = new File(outputDir, "output_receptor_" + id + ".txt");
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile, true))) {
                    byte[] buf = new byte[1024];
                    while (!Thread.currentThread().isInterrupted()) {
                        DatagramPacket packet = new DatagramPacket(buf, buf.length);
                        multicastSocket.receive(packet);
                        String msg = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
//                        log("Received: " + msg);
                        if (msg.equals(SENTINEL)) {
//                            log("Received SENTINEL, terminating");
                            break;
                        }
                        writer.write(msg + " ");
                        writer.flush();
                    }
                    writer.write("\n");
                    writer.flush();
                } catch (IOException e) {
//                    log("Error en el hilo de recepción: " + e.getMessage());
                    System.out.println(e.getMessage());
                }
            });
            listenerThread.start();

        } catch (IOException e) {
//            log("Error iniciando multicast: " + e.getMessage());
            System.out.println(e.getMessage());
        }
    }

    public void stop() {
        if (listenerThread != null) {
            listenerThread.interrupt();
        }
        if (multicastSocket != null) {
            try {
                multicastSocket.leaveGroup(InetAddress.getByName(MULTICAST_ADDRESS));
            } catch (IOException e) {
//                log("Error abandonando grupo multicast: " + e.getMessage());
                System.out.println(e.getMessage());
            }
            multicastSocket.close();
        }
    }

    public static void main(String[] args) {
//        if (args.length < 1) {
//            System.err.println("Uso: java Receptor <id>");
//            return;
//        }
        try {
            Scanner scanner = new Scanner(System.in);
            System.out.print("Enter receptor id: ");
            int id_in = scanner.nextInt();
//            int id = Integer.parseInt(args[0]);
            Receptor receptor = new Receptor(id_in);
            receptor.startMulticast();
            // Mantener el proceso vivo hasta que el receptor termine
            receptor.listenerThread.join();
            receptor.stop();
        } catch (NumberFormatException e) {
            System.err.println("El ID debe ser un número: " + e.getMessage());
        } catch (InterruptedException e) {
            System.err.println("Interrupción en el receptor: " + e.getMessage());
        }
    }
}