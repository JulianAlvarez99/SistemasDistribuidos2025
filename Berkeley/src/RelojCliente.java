import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;

public class RelojCliente {
    private JPanel mainP;
    private JSpinner freqSpinner;
    private JTextField serverField;
    private JLabel timeLabel;
    private JLabel freqLabel;
    private JLabel serverLabel;
    private JLabel timeRefresh;
    private JLabel portLabel;
    private JTextField portField;
    private JButton btnConnect;

    private long tiempoCliente;
    private int frecuencia;     // en milisegundos
    private Timer relojTimer;
    private volatile boolean conectado = false;

    public RelojCliente() {
        tiempoCliente = System.currentTimeMillis();
        frecuencia = 1000;

        freqSpinner.setModel(new SpinnerNumberModel(frecuencia, 100, 5000, 100));
        freqSpinner.addChangeListener(e -> {
            frecuencia = (int) freqSpinner.getValue();
            reiniciarTimer();
        });

        btnConnect.addActionListener(e -> conectarAlCoordinador());

        iniciarReloj();
    }

    private void iniciarReloj() {
        relojTimer = new Timer(frecuencia, e -> {
            tiempoCliente += 1000;
            timeRefresh.setText(formatearHora(tiempoCliente));
        });
        relojTimer.start();
    }

    private void reiniciarTimer() {
        if (relojTimer != null && relojTimer.isRunning()) {
            relojTimer.stop();
        }
        iniciarReloj();
    }

    private void conectarAlCoordinador() {
        if (conectado) return;

        String host = serverField.getText();
        int port = Integer.parseInt(portField.getText().trim());

        new Thread(() -> {
            try (Socket socket = new Socket(host, port)) {
                conectado = true;
                DataInputStream in = new DataInputStream(socket.getInputStream());
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());

                while (true) {
                    String comando = in.readUTF();
                    if ("SYNC".equals(comando)) {
                        out.writeLong(tiempoCliente);
                    } else if ("ADJUST".equals(comando)) {
                        long ajuste = in.readLong();
                        tiempoCliente += ajuste;
                        System.out.println("Ajuste aplicado: " + ajuste + " ms");
                    }
                }
            } catch (IOException e) {
                JOptionPane.showMessageDialog(null, "Error al conectar: " + e.getMessage());
                conectado = false;
            }
        }).start();
    }

    private String formatearHora(long millis) {
        return new SimpleDateFormat("HH:mm:ss").format(new Date(millis));
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Reloj Cliente");
            RelojCliente app = new RelojCliente();
            frame.setContentPane(app.mainP);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(500, 250);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}
