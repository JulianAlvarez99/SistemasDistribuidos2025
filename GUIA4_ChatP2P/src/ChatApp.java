import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.*;

public class ChatApp extends JFrame {
    private JTextField txtIp, txtPort, txtMyPort, txtMessage;
    private JTextArea chatArea;
    private JButton btnCall, btnSend, btnConnect;
    private Socket socket;
    private ServerSocket serverSocket;
    private BufferedReader reader;
    private BufferedWriter writer;
    private Thread listeningThread;

    public ChatApp() {
        setTitle("Chat " + getLocalAddress());
        setSize(500, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        JPanel topPanel = new JPanel(new GridLayout(2, 5));
        txtMyPort = new JTextField("5000");
        btnConnect = new JButton("Conectar");
        txtIp = new JTextField("127.0.0.1");
        txtPort = new JTextField("5001");
        btnCall = new JButton("Llamar");

        topPanel.add(new JLabel("Puerto:"));
        topPanel.add(txtMyPort);
        topPanel.add(btnConnect);
        topPanel.add(new JLabel());
        topPanel.add(new JLabel("IP:"));
        topPanel.add(txtIp);
        topPanel.add(new JLabel("Puerto:"));
        topPanel.add(txtPort);
        topPanel.add(btnCall);

        add(topPanel, BorderLayout.NORTH);

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        add(new JScrollPane(chatArea), BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        txtMessage = new JTextField();
        btnSend = new JButton("Enviar");
        bottomPanel.add(txtMessage, BorderLayout.CENTER);
        bottomPanel.add(btnSend, BorderLayout.EAST);
        add(bottomPanel, BorderLayout.SOUTH);

        btnConnect.addActionListener(e -> startListening());

        btnCall.addActionListener(e -> {
            String ip = txtIp.getText();
            int port = Integer.parseInt(txtPort.getText());
            try {
                socket = new Socket(ip, port);
                writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                writer.write("Solicitud de chat\n");
                writer.flush();

                String response = reader.readLine();
                if ("Aceptado".equals(response)) {
                    chatArea.append("Conectado con " + ip + "\n");
                    listenForMessages();
                } else {
                    JOptionPane.showMessageDialog(this, ip + " no aceptó la solicitud.", "Nuevo Chat", JOptionPane.WARNING_MESSAGE);
                    socket.close();
                }
            } catch (IOException ex) {
                chatArea.append("No se pudo conectar: " + ex.getMessage() + "\n");
            }
        });

        btnSend.addActionListener(e -> {
            String msg = txtMessage.getText();
            try {
                if (writer != null) {
                    writer.write(msg + "\n");
                    writer.flush();
                    chatArea.append("Yo: " + msg + "\n");
                    txtMessage.setText("");
                }
            } catch (IOException ex) {
                chatArea.append("Error al enviar mensaje: " + ex.getMessage() + "\n");
            }
        });

        setVisible(true);
    }

    private void startListening() {
        int myPort = Integer.parseInt(txtMyPort.getText());
        listeningThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(myPort);
                chatArea.append("Esperando conexiones en puerto " + myPort + "...\n");
                while (true) {
                    Socket incoming = serverSocket.accept();
                    BufferedReader incomingReader = new BufferedReader(new InputStreamReader(incoming.getInputStream()));
                    BufferedWriter incomingWriter = new BufferedWriter(new OutputStreamWriter(incoming.getOutputStream()));
                    String line = incomingReader.readLine();
                    if ("Solicitud de chat".equals(line)) {
                        int result = JOptionPane.showConfirmDialog(null, "¿Acepta solicitud de " + incoming.getInetAddress().getHostAddress() + "?", "Nuevo Chat", JOptionPane.YES_NO_OPTION);
                        if (result == JOptionPane.YES_OPTION) {
                            incomingWriter.write("Aceptado\n");
                            incomingWriter.flush();
                            socket = incoming;
                            reader = incomingReader;
                            writer = incomingWriter;
                            chatArea.append("Conectado con " + socket.getInetAddress().getHostAddress() + "\n");
                            listenForMessages();
                        } else {
                            incomingWriter.write("Rechazado\n");
                            incomingWriter.flush();
                            incoming.close();
                        }
                    }
                }
            } catch (IOException ex) {
                chatArea.append("Error al escuchar: " + ex.getMessage() + "\n");
            }
        });
        listeningThread.start();
    }

    private void listenForMessages() {
        Thread messageThread = new Thread(() -> {
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    chatArea.append("Otro: " + line + "\n");
                }
            } catch (IOException ex) {
                chatArea.append("Conexión cerrada\n");
            }
        });
        messageThread.start();
    }

    private String getLocalAddress() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            return "localhost";
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ChatApp::new);
    }
}
