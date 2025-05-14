import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Arrays;
import java.util.Scanner;

// Cliente que interactúa con el servicio remoto de transferencia de archivos
public class FileClient {
    private static final String CLIENT_DIR = "C:/Users/julia/Desktop/SistDistribuidos/FileClientStorage";
    private static final int RMI_PORT = 1099;
    private static final String SERVICE_NAME = "FileService";
    private final FileService service;
    private final Path clientDir;

    // Constructor: conecta al servicio remoto
    public FileClient(String host) throws Exception {
        // Obtener el registro RMI
        Registry registry = LocateRegistry.getRegistry(host, RMI_PORT);
        // Buscar el servicio
        service = (FileService) registry.lookup(SERVICE_NAME);
        // Inicializar directorio del cliente
        clientDir = Paths.get(CLIENT_DIR);
        Files.createDirectories(clientDir);
    }

    // Ejecuta la interfaz de consola
    public void start() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Cliente de transferencia de archivos iniciado.");
        System.out.println("Comandos: get <file>, put <file>, delete <file>, dir, mkdir <dir>, rmdir <dir>, exit");

        while (true) {
            System.out.print("> ");
            String[] input = scanner.nextLine().trim().split("\\s+", 2);
            String command = input[0].toLowerCase();

            try {
                switch (command) {
                    case "get":
                        if (input.length < 2) throw new IllegalArgumentException("Uso: get <file>");
                        getFile(input[1]);
                        break;
                    case "put":
                        if (input.length < 2) throw new IllegalArgumentException("Uso: put <file>");
                        putFile(input[1]);
                        break;
                    case "delete":
                        if (input.length < 2) throw new IllegalArgumentException("Uso: delete <file>");
                        deleteFile(input[1]);
                        break;
                    case "dir":
                        listDir();
                        break;
                    case "mkdir":
                        if (input.length < 2) throw new IllegalArgumentException("Uso: mkdir <dir>");
                        makeDir(input[1]);
                        break;
                    case "rmdir":
                        if (input.length < 2) throw new IllegalArgumentException("Uso: rmdir <dir>");
                        removeDir(input[1]);
                        break;
                    case "exit":
                        System.out.println("Cliente terminado.");
                        return;
                    default:
                        System.out.println("Comando desconocido: " + command);
                }
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
    }

    private void getFile(String filename) throws IOException, RemoteException {
        // Descargar el archivo del servidor
        byte[] data = service.get(filename);
        // Guardar en el directorio del cliente
        Path filePath = clientDir.resolve(filename);
        Files.write(filePath, data);
        System.out.println("Archivo descargado: " + filename);
    }

    private void putFile(String filename) throws IOException, RemoteException {
        // Leer el archivo local
        Path filePath = clientDir.resolve(filename);
        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            throw new IOException("El archivo no existe o no es un archivo regular: " + filename);
        }
        byte[] data = Files.readAllBytes(filePath);
        // Subir al servidor
        service.put(filename, data);
        System.out.println("Archivo subido: " + filename);
    }

    private void deleteFile(String filename) throws IOException, RemoteException {
        // Eliminar el archivo en el servidor
        service.delete(filename);
        System.out.println("Archivo eliminado: " + filename);
    }

    private void listDir() throws IOException, RemoteException {
        // Listar archivos y directorios
        String[] files = service.dir();
        System.out.println("Contenido del directorio:");
        Arrays.stream(files).forEach(System.out::println);
    }

    private void makeDir(String dirname) throws IOException, RemoteException {
        // Crear directorio en el servidor
        service.mkdir(dirname);
        System.out.println("Directorio creado: " + dirname);
    }

    private void removeDir(String dirname) throws IOException, RemoteException {
        // Eliminar directorio en el servidor
        service.rmdir(dirname);
        System.out.println("Directorio eliminado: " + dirname);
    }

    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "localhost";
        try {
            FileClient client = new FileClient(host);
            client.start();
        } catch (Exception e) {
            System.err.println("Error al iniciar el cliente: " + e.getMessage());
            e.printStackTrace();
        }
    }
}