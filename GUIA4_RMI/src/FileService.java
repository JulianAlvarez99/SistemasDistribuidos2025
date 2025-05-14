import java.io.IOException;
import java.rmi.Remote;
import java.rmi.RemoteException;

// Interfaz remota para el servicio de transferencia de archivos
public interface FileService extends Remote {
    // Descarga un archivo del servidor, devolviendo su contenido como byte[]
    byte[] get(String filename) throws RemoteException, IOException, IOException;

    // Sube un archivo al servidor con el nombre y contenido especificados
    void put(String filename, byte[] data) throws RemoteException, IOException;

    // Elimina un archivo en el servidor
    void delete(String filename) throws RemoteException, IOException;

    // Lista los archivos y directorios en el directorio base del servidor
    String[] dir() throws RemoteException, IOException;

    // Crea un directorio en el servidor
    void mkdir(String dirname) throws RemoteException, IOException;

    // Elimina un directorio vacío en el servidor
    void rmdir(String dirname) throws RemoteException, IOException;
}