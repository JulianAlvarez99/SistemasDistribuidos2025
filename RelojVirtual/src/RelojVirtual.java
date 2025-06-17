import org.apache.commons.net.ntp.NTPUDPClient;
import org.apache.commons.net.ntp.TimeInfo;

import javax.swing.*;
import java.awt.*;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.Date;

public class RelojVirtual {

    private JPanel mainPanel;
    private JLabel timeLabel;
    private JLabel freqLabel;
    private JSpinner freqSpinner;
    private JLabel timeRefresh;
    private JLabel serverLabel;
    private JTextField serverField;
    private JLabel updateTimeLabel;
    private JSpinner updateSpinner;

    private Timer relojTimer;
    private Timer syncTimer;

    private long virtualTimeMillis;
    private int frecuencia;     // en milisegundos
    private int syncInterval;   // en milisegundos
    private String ntpServer;   // dirección del servidor NTP

    public RelojVirtual() {
        // Inicializar valores por defecto
        frecuencia = 1000;
        syncInterval = 1000;
        virtualTimeMillis = System.currentTimeMillis();
        ntpServer = "time.windows.com";

        // Configurar componentes visuales
        freqSpinner.setModel(new SpinnerNumberModel(frecuencia, 100, 5000, 100));
        updateSpinner.setModel(new SpinnerNumberModel(syncInterval, 100, 5000, 100));
        serverField.setText(ntpServer);
        //timeLabel.setFont(new Font("Monospaced", Font.BOLD, 28));
        timeRefresh.setFont(new Font("Arial", Font.PLAIN, 14));

        // Listeners
        freqSpinner.addChangeListener(e -> {
            frecuencia = (int) freqSpinner.getValue();
            reiniciarReloj();
        });

        updateSpinner.addChangeListener(e -> {
            syncInterval = (int) updateSpinner.getValue();
            reiniciarSincronizacion();
        });

        serverField.addActionListener(e -> {
            ntpServer = serverField.getText();
            sincronizarConNTP();  // sincronizar inmediatamente si cambia
        });

        // Iniciar los timers
        iniciarReloj();
        iniciarSincronizacion();
    }

    private void iniciarReloj() {
        relojTimer = new Timer(frecuencia, e -> {
            virtualTimeMillis += 1000;
            timeRefresh.setText(formatearTiempo(virtualTimeMillis));
        });
        relojTimer.start();
    }

    private void reiniciarReloj() {
        if (relojTimer != null && relojTimer.isRunning()) {
            relojTimer.stop();
        }
        iniciarReloj();
    }

    private void iniciarSincronizacion() {
        syncTimer = new Timer(syncInterval, e -> sincronizarConNTP());
        syncTimer.start();
    }

    private void reiniciarSincronizacion() {
        if (syncTimer != null && syncTimer.isRunning()) {
            syncTimer.stop();
        }
        iniciarSincronizacion();
    }

    private void sincronizarConNTP() {
        try {
            NTPUDPClient client = new NTPUDPClient();
            client.setDefaultTimeout(1000);
            client.open();
            InetAddress hostAddr = InetAddress.getByName(ntpServer);
            TimeInfo info = client.getTime(hostAddr);
            info.computeDetails();

            Long offset = info.getOffset();
            if (offset != null) {
                virtualTimeMillis = System.currentTimeMillis() + offset;
                System.out.println("Sincronizado con NTP. Offset (ms): " + offset);
            } else {
                System.err.println("No se pudo calcular el offset con el servidor NTP.");
            }
            client.close();
        } catch (Exception ex) {
            System.err.println("Error al sincronizar con NTP: " + ex.getMessage());
        }
    }

    private String formatearTiempo(long millis) {
        return new SimpleDateFormat("HH:mm:ss").format(new Date(millis));
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Reloj Virtual con NTP");
            RelojVirtual rv = new RelojVirtual();
            frame.setContentPane(rv.mainPanel);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(400, 250);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}
