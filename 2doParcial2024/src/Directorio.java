import javax.swing.*;
import java.io.*;
import java.net.*;
import java.util.*;

public class Directorio {
    private Map<String, String> registro = new HashMap<>();
    private JPanel mainPanel;
    private JScrollPane scrollPanel;

    public Directorio(int puerto) throws IOException {
        ServerSocket serverSocket = new ServerSocket(puerto);
        System.out.println("Directorio en puerto " + puerto);
        while (true) {
            try (Socket socket = serverSocket.accept();
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
                String[] partes = in.readLine().split(" ");
                String comando = partes[0];
                if (comando.equals("REGISTRAR")) registrarRecurso(partes[1], partes[2], Integer.parseInt(partes[3]), out);
                else if (comando.equals("RESOLVER")) resolver(partes[1], out);
            } catch (IOException e) { e.printStackTrace(); }
        }
    }

    public void registrarRecurso(String nombre, String ip, int puerto, PrintWriter out) {
        registro.put(nombre, ip + ":" + puerto);
        out.println("OK");
        System.out.println("RECURSO " + nombre +" REGISTRADO EN ==>" + ip + ":" + puerto);
    }

    public void resolver(String nombre, PrintWriter out) {
        String direccion = registro.get(nombre);
        out.println(direccion != null ? direccion : "DESCONOCIDO");
    }

    public static void main(String[] args) throws IOException {
        new Directorio(5001);
    }
}