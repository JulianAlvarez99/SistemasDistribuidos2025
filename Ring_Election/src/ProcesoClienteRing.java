import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Algoritmo de elección en anillo siguiendo especificación clásica.
 * Cada proceso conoce su sucesor fijo. Lista estática de nodos.
 */
public class ProcesoClienteRing {
    private static final int COORD_PORT = 5000;
    private static final int BASE_RING_PORT = 6000;
    private final int id;
    private final int myPort;
    private final int successorPort;
    private final List<Integer> ringIds;
    private volatile int coordId;
    private volatile boolean soyCoordinador;
    private volatile boolean electionInProgress = false;

    public ProcesoClienteRing(int id, List<Integer> orderedIds) {
        this.id = id;
        this.ringIds = new ArrayList<>(orderedIds);
        this.myPort = BASE_RING_PORT + id;
        int idx = ringIds.indexOf(id);
        int succId = ringIds.get((idx + 1) % ringIds.size());
        this.successorPort = BASE_RING_PORT + succId;
        this.coordId = Collections.max(ringIds);
        this.soyCoordinador = (this.id == this.coordId);
    }

    public void start() {
        // 1) Levantar servidor de anillo para recibir mensajes
        new Thread(this::ringServer, "RingServer").start();
        // 2) Si soy coordinador inicial, iniciar servidor CS
        if (soyCoordinador) startCoordinatorServer();
        // 3) Monitor de coordinador caído
        new Thread(this::monitorCoordinator, "CoordMonitor").start();
        // 4) Loop principal: solicitar CS si no soy coordinador
        while (true) {
            try {
                Thread.sleep(5000);
                if (!soyCoordinador) {
                    if (!requestCriticalSection()) {
                        System.out.printf("[%d] Coordinador %d no responde. Inicio elección.%n", id, coordId);
                        startElection();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    // --- Elección en anillo ---
    private synchronized void startElection() {
        if (electionInProgress) return;
        electionInProgress = true;
        List<Integer> electionList = new ArrayList<>();
        electionList.add(id);
        sendRingMessage(new RingMessage(RingMessage.Type.ELECTION, electionList));
    }

    private void sendRingMessage(RingMessage msg) {
        try (Socket s = new Socket("localhost", successorPort);
             ObjectOutputStream oos = new ObjectOutputStream(s.getOutputStream())) {
            oos.writeObject(msg);
        } catch (IOException e) {
            System.err.printf("[%d] Error enviando a sucesor en puerto %d: %s%n", id, successorPort, e.getMessage());
        }
    }

    private void ringServer() {
        try (ServerSocket ss = new ServerSocket(myPort)) {
            System.out.printf("[%d] RingServer en puerto %d%n", id, myPort);
            while (true) {
                try (Socket s = ss.accept();
                     ObjectInputStream ois = new ObjectInputStream(s.getInputStream())) {
                    RingMessage msg = (RingMessage) ois.readObject();
                    handleRingMessage(msg);
                } catch (Exception ignored) {}
            }
        } catch (IOException e) {
            System.err.printf("[%d] RingServer error: %s%n", id, e.getMessage());
        }
    }

    private void handleRingMessage(RingMessage msg) {
        switch (msg.type) {
            case ELECTION:
                msg.ids.add(id);
                if (msg.ids.get(0).equals(id) && msg.ids.size() > 1) {
                    // Vino completo
                    int newCoord = Collections.max(msg.ids);
                    coordId = newCoord;
                    soyCoordinador = (newCoord == id);
                    System.out.printf("[%d] Elección finalizada. Nuevo coordinador: %d%n", id, newCoord);
                    // Iniciar anuncio
                    msg = new RingMessage(RingMessage.Type.COORDINATOR, msg.ids);
                }
                sendRingMessage(msg);
                if (msg.type == RingMessage.Type.COORDINATOR && soyCoordinador) {
                    startCoordinatorServer();
                    electionInProgress = false;
                }
                if (msg.type == RingMessage.Type.COORDINATOR && msg.ids.get(0).equals(id)) {
                    // Anuncio completo
                    electionInProgress = false;
                }
                break;
            case COORDINATOR:
                coordId = Collections.max(msg.ids);
                soyCoordinador = (coordId == id);
                System.out.printf("[%d] Recibido anuncio coordinador: %d%n", id, coordId);
                sendRingMessage(msg);
                if (msg.ids.get(0).equals(id)) {
                    electionInProgress = false;
                }
                break;
        }
    }

    // --- Exclusión mutua centralizada ---
    private boolean requestCriticalSection() {
        try (Socket s = new Socket("localhost", COORD_PORT);
             DataOutputStream out = new DataOutputStream(s.getOutputStream());
             DataInputStream in = new DataInputStream(s.getInputStream())) {
            out.writeInt(id);
            if ("GRANT".equals(in.readUTF())) {
                performCriticalSection();
                out.writeUTF("DONE");
                return true;
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }

    private void performCriticalSection() {
        try {
            int lines = 5 + new Random().nextInt(6);
            for (int i = 1; i <= lines; i++) {
                System.out.printf("Proceso %d|línea %d/%d%n", id, i, lines);
                Thread.sleep(1000);
            }
        } catch (InterruptedException ignored) {}
    }

    private void monitorCoordinator() {
        while (true) {
            try { Thread.sleep(5000); } catch (InterruptedException e) { break; }
            if (!soyCoordinador) {
                try (Socket s = new Socket("localhost", COORD_PORT)) {
                    // coordinador vivo
                } catch (IOException e) {
                    System.err.printf("[%d] Coordinador %d no responde.%n", id, coordId);
                    startElection();
                }
            }
        }
    }

    private void startCoordinatorServer() {
        new Thread(() -> {
            System.out.printf("[%d] Iniciando Coordinador en puerto %d%n", id, COORD_PORT);
            try (ServerSocket ss = new ServerSocket(COORD_PORT)) {
                while (soyCoordinador) {
                    Socket c = ss.accept();
                    new Thread(() -> {
                        try (DataInputStream in = new DataInputStream(c.getInputStream());
                             DataOutputStream out = new DataOutputStream(c.getOutputStream())) {
                            int pid = in.readInt();
                            out.writeUTF("GRANT");
                            if ("DONE".equals(in.readUTF())) {
                                System.out.printf("[Coordinador %d] %d terminó.%n", id, pid);
                            }
                        } catch (IOException ignored) {}
                    }).start();
                }
            } catch (IOException e) {
                System.err.printf("[%d] Coordinador error: %s%n", id, e.getMessage());
            }
        }, "CoordServer").start();
    }

    public static void main(String[] args) {
        // IDs ordenados del anillo
        List<Integer> ids = Arrays.asList(1, 2, 4, 5);
        int myId = 5;
        new ProcesoClienteRing(myId, ids).start();
    }
}

// Mensaje para anillo
class RingMessage implements Serializable {
    enum Type { ELECTION, COORDINATOR }
    Type type;
    List<Integer> ids;
    RingMessage(Type type, List<Integer> ids) {
        this.type = type;
        this.ids = new ArrayList<>(ids);
    }
}
