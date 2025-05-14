import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

// Servidor RMI que registra el servicio de transferencia de archivos
public class FileServer {
    private static final String BASE_DIR = "C:/Users/julia/Desktop/SistDistribuidos/FileServerStorage";
    private static final int RMI_PORT = 1099; // Puerto por defecto de RMI
    private static final String SERVICE_NAME = "FileService";

    public static void main(String[] args) {
        try {
            // Crear el servicio
            FileServiceImpl service = new FileServiceImpl(BASE_DIR);
            // Crear o obtener el registro RMI
            Registry registry = LocateRegistry.createRegistry(RMI_PORT);
            // Registrar el servicio
            registry.rebind(SERVICE_NAME, service);
            System.out.println("Servidor RMI iniciado en el puerto " + RMI_PORT);
            System.out.println("Servicio '" + SERVICE_NAME + "' registrado");
        } catch (Exception e) {
            System.err.println("Error al iniciar el servidor: " + e.getMessage());
            e.printStackTrace();
        }
    }
}