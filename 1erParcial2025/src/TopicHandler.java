import java.io.*;
import java.net.*;
import java.util.List;

// Maneja la recepción y distribución de mensajes para un tema
public class TopicHandler implements Runnable {
    private final String topic;
    private final int inputPort;
    private final int outputPort;
    private final SubscriberRegistry registry;
    private volatile boolean running;
    private ServerSocket serverSocket;

    public TopicHandler(String topic, int inputPort, int outputPort, SubscriberRegistry registry) {
        this.topic = topic;
        this.inputPort = inputPort;
        this.outputPort = outputPort;
        this.registry = registry;
        this.running = true;
    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(inputPort);
            Broker.log("Servidor para tema " + topic + " iniciado en puerto " + inputPort);
            while (running) {
                try (Socket client = serverSocket.accept();
                     BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()))) {
                    String message = reader.readLine();
                    if (message != null) {
                        Broker.log("Recibido mensaje para " + topic + ": " + message);
                        forwardMessage(message);
                    }
                } catch (IOException e) {
                    if (running) {
                        Broker.log("Error recibiendo mensaje para " + topic + ": " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            Broker.log("Error iniciando servidor para " + topic + ": " + e.getMessage());
        } finally {
            stop();
        }
    }

    // Envía el mensaje a los suscriptores del tema (unicast, secuencial)
    private void forwardMessage(String message) {
        List<SubscriberRegistry.SubscriberInfo> subscribers = registry.getSubscribers(topic);
        for (SubscriberRegistry.SubscriberInfo sub : subscribers) {
            try (Socket socket = new Socket(sub.address, sub.port);
                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()))) {
                writer.write(message + "\n");
                writer.flush();
                Broker.log("Enviado mensaje para " + topic + " a " + sub.address + ":" + sub.port + ": " + message);
            } catch (IOException e) {
                Broker.log("Error enviando mensaje a " + sub.address + ":" + sub.port + " para " + topic + ": " + e.getMessage());
            }
        }
    }

    // Detiene el servidor del tema
    public void stop() {
        running = false;
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                Broker.log("Error cerrando servidor para " + topic + ": " + e.getMessage());
            }
        }
    }
}