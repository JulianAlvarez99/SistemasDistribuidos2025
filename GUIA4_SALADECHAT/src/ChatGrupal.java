import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class ChatGrupal extends JFrame {
    private JPanel configPanel;
    private JLabel ipLabel;
    private JTextField ipField;
    private JLabel puertoLabel;
    private JTextField puertoField;
    private JButton conectarBtn;
    private JTextArea chatArea;
    private JScrollPane textScrollPane;
    private JPanel bottomPanel;
    private JTextField envioField;
    private JButton envioBtn;
    private JPanel mainPanel;

    private InetAddress group;
    private MulticastSocket multicastSocket;
    private int port;
    private Thread listenerThread;
    private final String nickname = "User-" + UUID.randomUUID().toString().substring(0, 5);

    public ChatGrupal() {
        setContentPane(mainPanel);
        setTitle("Sala de chat");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(600, 300);
        setLocationRelativeTo(null);
        setResizable(true);

        conectarBtn.addActionListener(e -> startMulticast());
        envioBtn.addActionListener(e -> {
            String text = envioField.getText().trim();
            if (!text.isEmpty()) {
                sendMulticastMessage(text);
                envioField.setText("");
            }
        });

        setVisible(true);
    }

    private void startMulticast() {
        try {
            // Leer configuración
            port = Integer.parseInt(puertoField.getText().trim());
            group = InetAddress.getByName(ipField.getText().trim());

            // Crear socket y unirse al grupo
            multicastSocket = new MulticastSocket(port);
            multicastSocket.joinGroup(group);

            SwingUtilities.invokeLater(() ->
                    chatArea.append("Joined chat: " + group.getHostAddress() + ":" + port + " as " + nickname + "\n")
            );

            // Listener de mensajes
            listenerThread = new Thread(() -> {
                byte[] buf = new byte[1024];
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        DatagramPacket packet = new DatagramPacket(buf, buf.length);
                        multicastSocket.receive(packet);
                        String msg = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);

                        // Evitar eco basado en nickname
                        if (!msg.startsWith(nickname + ":")) {
                            SwingUtilities.invokeLater(() ->
                                    chatArea.append(msg + "\n")
                            );
                        }
                    } catch (IOException e) {
                        SwingUtilities.invokeLater(() ->
                                chatArea.append("Receive error: " + e.getMessage() + "\n")
                        );
                        break;
                    }
                }
            });
            listenerThread.start();

        } catch (IOException ex) {
            SwingUtilities.invokeLater(() ->
                    chatArea.append("Join error: " + ex.getMessage() + "\n")
            );
        }
    }

    private void sendMulticastMessage(String message) {
        if (multicastSocket == null || group == null) return;
        String fullMsg = nickname + ": " + message;
        byte[] data = fullMsg.getBytes(StandardCharsets.UTF_8);
        DatagramPacket packet = new DatagramPacket(data, data.length, group, port);
        try {
            multicastSocket.send(packet);
            SwingUtilities.invokeLater(() ->
                    chatArea.append(fullMsg + "\n")
            );
        } catch (IOException e) {
            SwingUtilities.invokeLater(() ->
                    chatArea.append("Send error: " + e.getMessage() + "\n")
            );
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ChatGrupal::new);
    }
}
