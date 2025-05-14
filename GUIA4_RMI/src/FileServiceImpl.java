import java.io.*;
import java.nio.file.*;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.stream.Stream;

// Implementación del servicio de transferencia de archivos
public class FileServiceImpl extends UnicastRemoteObject implements FileService {
    private final Path baseDir; // Directorio base para almacenar archivos

    // Constructor: inicializa el directorio base
    protected FileServiceImpl(String baseDirPath) throws RemoteException {
        this.baseDir = Paths.get(baseDirPath);
        // Crear el directorio base si no existe
        try {
            Files.createDirectories(baseDir);
        } catch (IOException e) {
            throw new RemoteException("Error al crear directorio base", e);
        }
    }

    @Override
    public byte[] get(String filename) throws RemoteException, IOException {
        // Construir la ruta completa del archivo
        Path filePath = baseDir.resolve(filename).normalize();
        // Verificar que el archivo esté dentro del directorio base (seguridad)
        if (!filePath.startsWith(baseDir)) {
            throw new IOException("Acceso denegado: ruta fuera del directorio base");
        }
        // Verificar que el archivo exista y sea un archivo regular
        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            throw new IOException("El archivo no existe o no es un archivo regular: " + filename);
        }
        // Leer y devolver el contenido del archivo
        return Files.readAllBytes(filePath);
    }

    @Override
    public void put(String filename, byte[] data) throws RemoteException, IOException {
        // Construir la ruta completa del archivo
        Path filePath = baseDir.resolve(filename).normalize();
        // Verificar que la ruta esté dentro del directorio base
        if (!filePath.startsWith(baseDir)) {
            throw new IOException("Acceso denegado: ruta fuera del directorio base");
        }
        // Escribir los datos en el archivo (crea o sobrescribe)
        Files.write(filePath, data);
    }

    @Override
    public void delete(String filename) throws RemoteException, IOException {
        // Construir la ruta completa del archivo
        Path filePath = baseDir.resolve(filename).normalize();
        // Verificar que la ruta esté dentro del directorio base
        if (!filePath.startsWith(baseDir)) {
            throw new IOException("Acceso denegado: ruta fuera del directorio base");
        }
        // Verificar que el archivo exista y sea un archivo regular
        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            throw new IOException("El archivo no existe o no es un archivo regular: " + filename);
        }
        // Eliminar el archivo
        Files.delete(filePath);
    }

    @Override
    public String[] dir() throws RemoteException, IOException {
        // Listar archivos y directorios en el directorio base usando Files.list
        try (Stream<Path> stream = Files.list(baseDir)) {
            return stream
                    .map(path -> baseDir.relativize(path).toString())
                    .toArray(String[]::new);
        }
    }

    @Override
    public void mkdir(String dirname) throws RemoteException, IOException {
        // Construir la ruta completa del directorio
        Path dirPath = baseDir.resolve(dirname).normalize();
        // Verificar que la ruta esté dentro del directorio base
        if (!dirPath.startsWith(baseDir)) {
            throw new IOException("Acceso denegado: ruta fuera del directorio base");
        }
        // Crear el directorio
        Files.createDirectories(dirPath);
    }

    @Override
    public void rmdir(String dirname) throws RemoteException, IOException {
        // Construir la ruta completa del directorio
        Path dirPath = baseDir.resolve(dirname).normalize();
        // Verificar que la ruta esté dentro del directorio base
        if (!dirPath.startsWith(baseDir)) {
            throw new IOException("Acceso denegado: ruta fuera del directorio base");
        }
        // Verificar que el directorio exista y sea un directorio
        if (!Files.exists(dirPath) || !Files.isDirectory(dirPath)) {
            throw new IOException("El directorio no existe o no es un directorio: " + dirname);
        }
        // Verificar que el directorio esté vacío
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dirPath)) {
            if (stream.iterator().hasNext()) {
                throw new IOException("El directorio no está vacío: " + dirname);
            }
        }
        // Eliminar el directorio
        Files.delete(dirPath);
    }
}