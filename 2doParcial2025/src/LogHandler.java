
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Singleton para manejo centralizado de logs con bloqueo de archivo.
 */
public class LogHandler {
    private static LogHandler instance;
    private final Path logPath;
    private final SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

    private LogHandler(String fileName) {
        this.logPath = Paths.get(fileName);
    }

    public static synchronized LogHandler getInstance() {
        if (instance == null) {
            instance = new LogHandler("token.log");
        }
        return instance;
    }

    public void log(String ip, int puerto, String mensaje) {
        String timestamp = sdf.format(new Date());
        String entry = String.format("%s %s:%d %s%n", timestamp, ip, puerto, mensaje);
        byte[] bytes = entry.getBytes();
        try (FileChannel channel = FileChannel.open(
                logPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.APPEND);
             FileLock lock = channel.lock()) {
            channel.write(ByteBuffer.wrap(bytes));
        } catch (IOException e) {
            System.err.println("LogHandler error: " + e.getMessage());
        }
    }
}