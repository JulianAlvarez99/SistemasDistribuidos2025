import java.io.PrintWriter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

// Registro de suscriptores por tema
public class SubscriberRegistry {
    private final Map<String, Map<String, PrintWriter>> subscribers; // tema -> (clientId -> writer)

    public SubscriberRegistry() {
        subscribers = new ConcurrentHashMap<>();
    }

    // Agrega un suscriptor a un tema
    public void addSubscriber(String topic, String clientId, PrintWriter writer) {
        subscribers.computeIfAbsent(topic, k -> new ConcurrentHashMap<>())
                .put(clientId, writer);
    }

    // Elimina un suscriptor de un tema
    public void removeSubscriber(String topic, String clientId) {
        Map<String, PrintWriter> topicSubscribers = subscribers.get(topic);
        if (topicSubscribers != null) {
            topicSubscribers.remove(clientId);
            if (topicSubscribers.isEmpty()) {
                subscribers.remove(topic);
            }
        }
    }

    // Elimina un cliente de todos los temas
    public void removeClient(String clientId) {
        for (Map<String, PrintWriter> topicSubscribers : subscribers.values()) {
            topicSubscribers.remove(clientId);
        }
        subscribers.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    // Envía un mensaje a todos los suscriptores de un tema
    public void forwardMessage(String topic, String message) {
        Map<String, PrintWriter> topicSubscribers = subscribers.getOrDefault(topic, Collections.emptyMap());
        for (PrintWriter writer : topicSubscribers.values()) {
            writer.println(message);
        }
    }
}