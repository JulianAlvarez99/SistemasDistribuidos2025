import java.io.*;
import java.net.*;
import java.util.Random;
import java.util.Scanner;

public class Cliente {
    private String nombre;
    private Random random = new Random();
    private static final int TIMEOUT_MS = 30000; // Timeout de 30 segundos
    private static final int RETRY_INTERVAL_MS = 5000; // Intervalo de reintentos

    public Cliente(String nombre) {
        this.nombre = nombre;
    }

    public void iniciar() throws IOException, InterruptedException {
        Scanner scanner = new Scanner(System.in);
        boolean salir = false;

        while (!salir) {
            // Leer el nombre del recurso desde la consola
            System.out.print("CLIENTE " + this.nombre + "=> Ingrese el nombre (ID) del recurso a utilizar: ");
            String recurso = scanner.nextLine().trim();

            // Verificar si el usuario quiere salir
            if (recurso.equalsIgnoreCase("close")) {
                System.out.println("Cerrando cliente " + nombre);
                salir = true;
                continue;
            }

            // Intentar asignar el recurso con espera activa
            boolean asignado = false;
            while (!asignado && !salir) {
                asignado = solicitarRecurso(recurso);
                if (asignado) {
                    String direccion = resolverRecurso(recurso);
                    if (direccion != null) {
                        usarRecurso(direccion);
                        liberarRecurso(recurso);
//                        salir = true; // Salir después de usar el recurso
                    } else {
                        System.out.println("No se pudo resolver la dirección del recurso: " + recurso);
                        break; // Salir del bucle de espera para pedir otro recurso
                    }
                } else {
                    System.out.println("El recurso " + recurso + " no está disponible o no existe.");
                    System.out.print("¿Desea esperar (E) o elegir otro recurso (C)? ");
                    String opcion = scanner.nextLine().trim().toUpperCase();
                    if (opcion.equals("C")) {
                        break; // Salir del bucle de espera para pedir otro recurso
                    } else {
                        System.out.println("Esperando " + (RETRY_INTERVAL_MS / 1000) + " segundos antes de reintentar...");
                        Thread.sleep(RETRY_INTERVAL_MS);
                    }
                }
            }
        }
    }

    private boolean solicitarRecurso(String recurso) throws IOException {
        try (Socket socket = new Socket("localhost", 5000);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            socket.setSoTimeout(TIMEOUT_MS);
            // Enviar solicitud específica para el recurso
            out.println("ASIGNAR " + nombre + " " + recurso);
            String respuesta = in.readLine();
            return respuesta.equals("OK");
        }
    }

    private String resolverRecurso(String recurso) throws IOException {
        try (Socket socket = new Socket("localhost", 5001);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            socket.setSoTimeout(TIMEOUT_MS);
            out.println("RESOLVER " + recurso);
            String respuesta = in.readLine();
            return respuesta.equals("DESCONOCIDO") ? null : respuesta;
        }
    }

    private void usarRecurso(String direccion) throws IOException, InterruptedException {
        String[] partes = direccion.split(":");
        Socket socket = new Socket(partes[0], Integer.parseInt(partes[1]));
        try {
            socket.setSoTimeout(TIMEOUT_MS);
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Enviar INICIO y verificar respuesta
            System.out.println("Enviando INICIO a " + direccion);
            out.println("INICIO " + nombre);
            String respuestaInicio = in.readLine();
            if (respuestaInicio == null) {
                throw new IOException("El recurso no respondió a INICIO");
            }
            System.out.println("Respuesta a INICIO: " + respuestaInicio);

            // Simular uso del recurso con un tiempo aleatorio
            Thread.sleep(5000 + random.nextInt(10000));

            // Enviar FIN y verificar respuesta
            System.out.println("Enviando FIN a " + direccion);
            out.println("FIN " + nombre);
            String respuestaFin = in.readLine();
            if (respuestaFin == null) {
                throw new IOException("El recurso no respondió a FIN");
            }
            System.out.println("Respuesta a FIN: " + respuestaInicio);
            out.flush();

            // No leer respuesta después de FIN, ya que el servidor cierra la conexión
        } catch (SocketTimeoutException e) {
            System.err.println("Timeout en la comunicación con el recurso: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("Error en la comunicación con el recurso: " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                System.err.println("Error al cerrar socket: " + e.getMessage());
            }
        }
    }

    private void liberarRecurso(String recurso) throws IOException {
        try (Socket socket = new Socket("localhost", 5000);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            socket.setSoTimeout(TIMEOUT_MS);
            out.println("LIBERAR " + nombre + " " + recurso);
            in.readLine(); // Leer respuesta para completar el protocolo
        }
    }

    // Metodo main para ejecutar el cliente
    public static void main(String[] args) throws IOException, InterruptedException {
        Random rand_id = new Random();
        String id = String.valueOf(rand_id.nextInt(100));
        Cliente cliente = new Cliente(id);
        cliente.iniciar();
    }
}