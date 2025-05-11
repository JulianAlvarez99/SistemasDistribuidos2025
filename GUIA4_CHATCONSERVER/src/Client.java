import javax.swing.*;
import java.io.*;
import java.net.Socket;
import java.util.UUID;

// ChatGrupal.java (Cliente)
// Este cliente se conecta a un servidor de chat y permite enviar y recibir mensajes

public class Client extends JFrame {
    private JPanel mainPanel;
    private JPanel configPanel;
    private JLabel ipLabel;
    private JTextField ipField;
    private JLabel portLabel;
    private JTextField puertoField;
    private JButton conectarBtn;
    private JTextArea chatArea;
    private JTextField envioField;
    private JButton envioBtn;

    private String serverHost;
    private int serverPort;
    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private Thread listenerThread;
    private final String nickname = "User-" + UUID.randomUUID().toString().substring(0, 5);

    public Client() {
        setContentPane(mainPanel);
        setTitle("Sala de chat");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(600, 300);
        setLocationRelativeTo(null);
        setResizable(true);

        conectarBtn.addActionListener(e -> connectToServer());
        envioBtn.addActionListener(e -> {
            String text = envioField.getText().trim();
            if (!text.isEmpty()) {
                sendMessage(text);
                envioField.setText("");
            }
        });
        setVisible(true);
    }

    private void connectToServer() {
        try {
            serverHost = ipField.getText().trim();
            serverPort = Integer.parseInt(puertoField.getText().trim());
            socket = new Socket(serverHost, serverPort);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintWriter(socket.getOutputStream(), true);

            chatArea.append("Connected to chat server.\n");

            // Escuchar mensajes desde el servidor
            listenerThread = new Thread(() -> {
                try {
                    String msg;
                    while ((msg = reader.readLine()) != null) {
                        chatArea.append(msg + "\n");
                    }
                } catch (IOException e) {
                    chatArea.append("Connection closed.\n");
                }
            });
            listenerThread.start();
        } catch (IOException ex) {
            chatArea.append("Connection error: " + ex.getMessage() + "\n");
        }
    }

    private void sendMessage(String message) {
        if (writer != null) {
            writer.println(nickname + ": " + message);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Client::new);
    }
}
