// CoordinatorServer.java
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class CoordinatorServer {
    private static final int CRIT_PORT = 5000;
    private final Queue<Socket> queue = new ArrayDeque<>();
    private boolean busy = false;

    public void startServing() {
        System.out.println("CoordinatorServer iniciado en puerto " + CRIT_PORT);
        try (ServerSocket ss = new ServerSocket(CRIT_PORT)) {
            while (true) {
                Socket client = ss.accept();
                synchronized (queue) {
                    queue.add(client);
                    if (!busy) serveNext();
                }
            }
        } catch (IOException e) {
            System.err.println("CoordinatorServer error: " + e.getMessage());
        }
    }

    private void serveNext() {
        Socket s = queue.poll();
        if (s == null) { busy = false; return; }
        busy = true;
        new Thread(() -> {
            try (DataInputStream in = new DataInputStream(s.getInputStream());
                 DataOutputStream out = new DataOutputStream(s.getOutputStream()))
            {
                int pid = in.readInt();
                System.out.println("Permitido CS a " + pid);
                out.writeUTF("GRANT");
                String done = in.readUTF();
                if ("DONE".equals(done)) {
                    System.out.println("Proceso " + pid + " terminó CS.");
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try { s.close(); } catch (IOException ignored) {}
                synchronized (queue) {
                    busy = false;
                    serveNext();
                }
            }
        }).start();
    }
}
