import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.*;

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
    private JButton descBtn;

    private static final String MULTICAST_ADDRESS = "230.0.0.1";
    private static final int MULTICAST_PORT = 4446;
    private MulticastSocket multicastSocket;
    private InetAddress group;
    private final String localAddress;
    private static final String BASE_LOCAL = "127.0.0.";
    private Thread listenerThread;

    private String assignRandomLocalAddress() {
        int random = (int)(Math.random() * 254) + 1;
        return BASE_LOCAL + random;
    }

    private void startMulticast() {
        try {
            group = InetAddress.getByName(ipField.getText());
            multicastSocket = new MulticastSocket(Integer.parseInt(puertoField.getText()));
            multicastSocket.joinGroup(group);

            chatArea.append("Joined chat room at " + MULTICAST_ADDRESS + ":" + MULTICAST_PORT + " as " + localAddress + "\n");

            Thread listenerThread = new Thread(() -> {
                byte[] buffer = new byte[1024];
                while (true) {
                    try {
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        multicastSocket.receive(packet);

                        String senderAddress = packet.getAddress().getHostAddress();
                        if (!senderAddress.equals(InetAddress.getLocalHost().getHostAddress())) {
                            String message = new String(packet.getData(), 0, packet.getLength());
                            chatArea.append(message + "\n");
                        }
                    } catch (IOException e) {
                        chatArea.append("Error receiving message: " + e.getMessage() + "\n");
                    }
                }
            });
            listenerThread.start();
        } catch (IOException e) {
            chatArea.append("Error joining chat room: " + e.getMessage() + "\n");
        }
    }


    private void sendMulticastMessage(String message) {
        try {
            String userMessage = localAddress + ": " + message;
            DatagramPacket packet = new DatagramPacket(userMessage.getBytes(), userMessage.length(), group, MULTICAST_PORT);
            multicastSocket.send(packet);
        } catch (IOException e) {
            chatArea.append("Error sending message: " + e.getMessage() + "\n");
        }
    }

    private void stopMulticast() {
        try {
            if (listenerThread != null && listenerThread.isAlive()) {
                listenerThread.interrupt();
            }
            if (multicastSocket != null) {
                multicastSocket.leaveGroup(group);
                multicastSocket.close();
                chatArea.append("Disconnected from chat room.\n");
            }
        } catch (IOException e) {
            chatArea.append("Error disconnecting: " + e.getMessage() + "\n");
        }
    }

    public ChatGrupal() {
        setContentPane(mainPanel);
        setTitle("Sala de chat");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(600, 300);
        setLocationRelativeTo(null);
        setResizable(true);

        localAddress = assignRandomLocalAddress();

        conectarBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                startMulticast();
            }
        });

        envioBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String msg = envioField.getText().trim();
                if (!msg.isEmpty()) {
                    sendMulticastMessage(msg);
                    envioField.setText("");
                }
            }
        });

        setVisible(true);
        descBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                stopMulticast();
            }
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ChatGrupal::new);
    }
}
