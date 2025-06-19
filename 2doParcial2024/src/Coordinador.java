import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class Coordinador {
    private List<String> recursosLibres = new ArrayList<>();
    private Map<String, String> recursosAsignados = new HashMap<>();
    private PrintWriter log;

    public Coordinador(int puerto) throws IOException {
        log = new PrintWriter(new FileWriter("coordinador.log", true));
        ServerSocket serverSocket = new ServerSocket(puerto);
        System.out.println("Coordinador en puerto " + puerto);
        while (true) {
            try (Socket socket = serverSocket.accept();
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
                String[] partes = in.readLine().split(" ");
                String comando = partes[0];
                if (comando.equals("REGISTRAR")) registrarRecurso(partes[1], out);
                else if (comando.equals("ASIGNAR")) asignarRecurso(partes[1], partes[2], out);
                else if (comando.equals("LIBERAR")) liberarRecurso(partes[1], partes[2], out);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public synchronized void registrarRecurso(String nombre, PrintWriter out) {
        recursosLibres.add(nombre);
        out.println("OK");
        System.out.println("RECURSO " + nombre +" REGISTRADO");
    }

    public synchronized void asignarRecurso(String cliente, String recurso, PrintWriter out) {
        // Verificar si el recurso existe y está libre
        if (recursosLibres.contains(recurso) && !recursosAsignados.containsKey(recurso)) {
            recursosLibres.remove(recurso);
            recursosAsignados.put(recurso, cliente);
            registrarLog(cliente, recurso, "ASIGNACION");
            out.println("OK");
        } else {
            out.println("NO_DISPONIBLE");
        }
    }

    public synchronized void liberarRecurso(String cliente, String recurso, PrintWriter out) {
        if (recursosAsignados.containsKey(recurso) && recursosAsignados.get(recurso).equals(cliente)) {
            recursosAsignados.remove(recurso);
            recursosLibres.add(recurso);
            registrarLog(cliente, recurso, "DEVOLUCION");
            out.println("OK");
        } else {
            out.println("ERROR");
        }
    }

    private void registrarLog(String cliente, String recurso, String accion) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        log.println(timestamp + " CLIENTE " + cliente + " RECURSO " + recurso + " " + accion);
        log.flush();
    }

    public static void main(String[] args) throws IOException {
        new Coordinador(5000);
    }
}