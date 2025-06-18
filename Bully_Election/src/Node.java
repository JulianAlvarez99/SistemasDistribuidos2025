// Node.java
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public class Node {
    // --- Configuración fija ---
    private static final int CRIT_PORT       = 5000;          // puerto de solicitudes CS
    private static final int ELECTION_PORT_BASE = 6000;       // Base port para election servers

    private final int id;
    private volatile int coordId;                            // ID del coordinador actual
    private final Map<Integer, InetSocketAddress> peers;     // ID -> (host, electionPort)
    private final Random rand = new Random();

    public Node(int id, Map<Integer, InetSocketAddress> peers) {
        this.id    = id;
        this.peers = peers;
        this.coordId = Collections.max(peers.keySet());      // al arrancar, suponemos el mayor
    }

    public void start() throws IOException {
        // 1) Arranca el servidor de elección
        new Thread(this::electionServer).start();

        // 2) Hilo de monitor para detección de caída del coordinador
        new Thread(this::monitorCoordinator).start();

        // 3) Lógica principal: solicitar CS periódicamente
        requestCriticalSectionLoop();
    }

    private void requestCriticalSectionLoop() {
        while (true) {
            try {
                Thread.sleep((5 + rand.nextInt(26)) * 1000L);
                if (coordId == id) {
                    // Si soy yo el coordinador, sirvo mi propia petición
                    enterCriticalSection();
                } else {
                    if (!askCoordinatorPermission()) {
                        System.out.printf("[%d] No responde coord %d: arranco elección.%n", id, coordId);
                        startElection();
                    }
                }
            } catch (InterruptedException | IOException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private boolean askCoordinatorPermission() {
        try (Socket s = new Socket("localhost", CRIT_PORT);
             DataOutputStream out = new DataOutputStream(s.getOutputStream());
             DataInputStream  in  = new DataInputStream(s.getInputStream()))
        {
            out.writeInt(id);
            String permiso = in.readUTF();
            if ("GRANT".equals(permiso)) {
                enterCriticalSection();
                out.writeUTF("DONE");
                return true;
            }
        } catch (IOException e) {
            // caído o no disponible
        }
        return false;
    }

    private void enterCriticalSection() throws IOException {
        int lines = 5 + rand.nextInt(6);
        for (int i = 1; i <= lines; i++) {
            String ts = new SimpleDateFormat("HH:mm:ss").format(new Date());
            String msg = String.format("Proceso %d|%s|%d/%d", id, ts, i, lines);
            System.out.println(msg);
            // Aquí podrías escribir a archivo si quieres
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    // ——————————————
    // BULY ELECTION
    // ——————————————
    private void startElection() {
        Set<Integer> higher = new HashSet<>();
        for (int peerId : peers.keySet()) {
            if (peerId > id) higher.add(peerId);
        }
        final CountDownLatch latch = new CountDownLatch(higher.size());
        // 1) Enviar ELECTION a todos más fuertes
        higher.forEach(pid -> {
            new Thread(() -> {
                try (Socket s = new Socket(peers.get(pid).getHostName(), peers.get(pid).getPort());
                     DataOutputStream out = new DataOutputStream(s.getOutputStream());
                     DataInputStream  in  = new DataInputStream(s.getInputStream()))
                {
                    out.writeUTF("ELECTION");
                    String resp = in.readUTF();
                    if ("OK".equals(resp)) {
                        latch.countDown();
                    }
                } catch (IOException ignore) {}
            }).start();
        });

        try {
            // 2) Espera un intervalo razonable
            if (!latch.await(5, TimeUnit.SECONDS)) {
                // Nadie respondió → soy el nuevo coord
                announceCoordinator();
            }
            // Si recibí OK, espero anuncio de coord
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void announceCoordinator() {
        coordId = id;
        System.out.printf("[%d] Me proclamo Coordinador.%n", id);
        // Arranco mi servidor de exclusión mutua
        new Thread(() -> new CoordinatorServer().startServing()).start();
        // Anuncio a todos los demás
        peers.keySet().forEach(pid -> {
            if (pid == id) return;
            try (Socket s = new Socket(peers.get(pid).getHostName(), peers.get(pid).getPort());
                 DataOutputStream out = new DataOutputStream(s.getOutputStream()))
            {
                out.writeUTF("COORDINATOR:" + id);
            } catch (IOException ignore) {}
        });
    }

    private void monitorCoordinator() {
        while (true) {
            try {
                Thread.sleep(10_000);
                if (coordId != id && !pingElectionServer(coordId)) {
                    System.out.printf("[%d] Detectado fallo de coord %d.%n", id, coordId);
                    startElection();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private boolean pingElectionServer(int peerId) {
        try (Socket s = new Socket(peers.get(peerId).getHostName(), peers.get(peerId).getPort());
             DataOutputStream out = new DataOutputStream(s.getOutputStream());
             DataInputStream  in  = new DataInputStream(s.getInputStream()))
        {
            out.writeUTF("PING");
            return "PONG".equals(in.readUTF());
        } catch (IOException e) {
            return false;
        }
    }

    private void electionServer() {
        int port = ELECTION_PORT_BASE + id;
        try (ServerSocket ss = new ServerSocket(port)) {
            System.out.printf("[%d] Election server en %d%n", id, port);
            while (true) {
                try (Socket s = ss.accept();
                     DataInputStream in  = new DataInputStream(s.getInputStream());
                     DataOutputStream out = new DataOutputStream(s.getOutputStream()))
                {
                    String msg = in.readUTF();
                    if (msg.equals("ELECTION")) {
                        out.writeUTF("OK");                  // contesto OK
                        startElection();                    // y arranco mi propia elección
                    } else if (msg.startsWith("COORDINATOR:")) {
                        coordId = Integer.parseInt(msg.split(":")[1]);
                        System.out.printf("[%d] Nuevo coordinador %d%n", id, coordId);
                        out.writeUTF("ACK");
                    } else if (msg.equals("PING")) {
                        out.writeUTF("PONG");
                    }
                } catch (IOException ignored) {}
            }
        } catch (IOException e) {
            System.err.printf("[%d] electionServer error: %s%n", id, e.getMessage());
        }
    }

    // --- main de arranque sin argumentos ---
    public static void main(String[] args) throws Exception {
        int myId = 1; // <<< CAMBIÁ ESTE VALOR PARA CADA NODO (1, 2 o 3)

        Map<Integer, InetSocketAddress> peers = new HashMap<>();
        peers.put(1, new InetSocketAddress("localhost", 6001));
        peers.put(2, new InetSocketAddress("localhost", 6002));
        peers.put(3, new InetSocketAddress("localhost", 6003));
        peers.put(4, new InetSocketAddress("localhost", 6004));
        peers.put(5, new InetSocketAddress("localhost", 6005));

        new Node(myId, peers).start();
    }
}
