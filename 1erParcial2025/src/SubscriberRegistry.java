import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

// Registro de suscriptores por tema
public class SubscriberRegistry {
    // Mapa: tema -> lista de [dirección, puerto]
    private final Map<String, List<SubscriberInfo>> subscribers;

    public SubscriberRegistry() {
        subscribers = new ConcurrentHashMap<>();
    }

    // Agrega un suscriptor a un tema
    public void addSubscriber(String topic, String address, int port) {
        subscribers.computeIfAbsent(topic, k -> new ArrayList<>())
                .add(new SubscriberInfo(address, port));
    }

    // Elimina un suscriptor de un tema
    public void removeSubscriber(String topic, String address, int port) {
        List<SubscriberInfo> list = subscribers.get(topic);
        if (list != null) {
            list.removeIf(info -> info.address.equals(address) && info.port == port);
            if (list.isEmpty()) {
                subscribers.remove(topic);
            }
        }
    }

    // Obtiene la lista de suscriptores para un tema
    public List<SubscriberInfo> getSubscribers(String topic) {
        return subscribers.getOrDefault(topic, Collections.emptyList());
    }

    // Clase interna para almacenar información de un suscriptor
    public static class SubscriberInfo {
        String address;
        int port;

        SubscriberInfo(String address, int port) {
            this.address = address;
            this.port = port;
        }
    }
}