import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.Date;

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
        try (FileChannel channel = FileChannel.open(logPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
             FileLock lock = channel.lock()) {
            channel.write(java.nio.ByteBuffer.wrap(entry.getBytes()));
        } catch (IOException e) {
            System.err.println("Error al escribir log: " + e.getMessage());
        }
    }
}