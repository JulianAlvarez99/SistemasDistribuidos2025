import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.net.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

public class RelojCoordinador {
    private JPanel panelMain;
    private JLabel labelHora;
    private JButton btnSync;
    private DefaultListModel<String> logModel;
    private JList<String> logList;

    private long tiempoCoordinador;
    private Timer timer;
    private List<Socket> clientesConectados = new ArrayList<>();

    public RelojCoordinador() {
        tiempoCoordinador = System.currentTimeMillis();
        labelHora.setFont(new Font("Arial", Font.BOLD, 24));

        timer = new Timer(1000, e -> {
            tiempoCoordinador += 1000;
            labelHora.setText(formatearHora(tiempoCoordinador));
        });
        timer.start();

        btnSync.addActionListener(e -> ejecutarBerkeley());
        new Thread(this::esperarClientes).start();
    }

    private void esperarClientes() {
        try (ServerSocket serverSocket = new ServerSocket(6000)) {
            log("Esperando clientes...");
            while (true) {
                Socket socket = serverSocket.accept();
                clientesConectados.add(socket);
                log("Cliente conectado: " + socket.getInetAddress());
            }
        } catch (IOException ex) {
            log("Error en el servidor: " + ex.getMessage());
        }
    }

    private void ejecutarBerkeley() {
        try {
            log("Iniciando sincronización...");
            List<Long> tiempos = new ArrayList<>();
            Map<Socket, Long> respuestas = new HashMap<>();

            for (Socket socket : clientesConectados) {
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                out.writeUTF("SYNC");

                DataInputStream in = new DataInputStream(socket.getInputStream());
                long tiempoCliente = in.readLong();
                respuestas.put(socket, tiempoCliente);
                tiempos.add(tiempoCliente);
            }

            tiempos.add(tiempoCoordinador);
            long promedio = tiempos.stream().mapToLong(Long::longValue).sum() / tiempos.size();
            long ajusteCoordinador = promedio - tiempoCoordinador;
            tiempoCoordinador += ajusteCoordinador;

            labelHora.setText(formatearHora(tiempoCoordinador));
            log("Promedio: " + promedio + " | Ajuste coord.: " + ajusteCoordinador);

            for (Map.Entry<Socket, Long> entry : respuestas.entrySet()) {
                long ajuste = promedio - entry.getValue();
                DataOutputStream out = new DataOutputStream(entry.getKey().getOutputStream());
                out.writeUTF("ADJUST");
                out.writeLong(ajuste);
            }

        } catch (IOException ex) {
            log("Error en sincronización: " + ex.getMessage());
        }
    }

    private String formatearHora(long millis) {
        return new SimpleDateFormat("HH:mm:ss").format(new Date(millis));
    }

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> logModel.addElement("[" + new Date() + "] " + msg));
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Coordinador Berkeley");
            RelojCoordinador app = new RelojCoordinador();
            app.logModel = new DefaultListModel<>();
            app.logList.setModel(app.logModel);
            frame.setContentPane(app.panelMain);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(500, 400);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}

