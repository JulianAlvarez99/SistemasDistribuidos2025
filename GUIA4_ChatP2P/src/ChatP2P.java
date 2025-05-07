import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Set;

public class ChatP2P extends JFrame {

    private JLabel puertoLabel;
    private JTextField puertoField1;
    private JButton puertoButton1;
    private JPanel llamarPanel;
    private JLabel ipField;
    private JTextField ipField1;
    private JLabel puertoLabel2;
    private JTextField puertoField2;
    private JButton llamarButton1;
    private JTextArea textArea1;
    private JTextField envioField1;
    private JButton envioButton1;
    private JPanel mainPanel;
    private Socket socket;
    private ServerSocket serverSocket;
    private Thread listeningThread;
    private BufferedReader reader;
    private BufferedWriter writer;
    private static final Set<String> usedIPs = new HashSet<>();
    private static final String BASE_IP = "127.0.0.";

    private String findAvailableIP() {
        for (int i = 1; i < 255; i++) {
            String ip = BASE_IP + i;
            if (!usedIPs.contains(ip)) {
                usedIPs.add(ip);
                return ip;
            }
        }
        throw new RuntimeException("No available IP addresses in the loopback range");
    }

    private int findAvaiblePort(){
        try (ServerSocket tempSocket = new ServerSocket(0)){
            return tempSocket.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException("No available ports found", e);
        }
    }
    private void startListening() {
        // Implement the logic to start listening for incoming connections
        int myPort = Integer.parseInt(puertoField1.getText());
        listeningThread = new Thread(() -> {
            try{
                serverSocket = new ServerSocket(myPort);
                textArea1.append("Server listening on port " + myPort + "...\n");
                while(true){
                    Socket incoming = serverSocket.accept();
                    BufferedReader incomingReader = new BufferedReader(new InputStreamReader(incoming.getInputStream()));
                    BufferedWriter inomingWriter = new BufferedWriter(new OutputStreamWriter(incoming.getOutputStream()));
                    String line = incomingReader.readLine();
                    if("Chat request".equals(line)){
                        int result = JOptionPane.showConfirmDialog(null, "Accept chat request from " + incoming.getInetAddress().getHostAddress() + "?", "New Chat", JOptionPane.YES_NO_OPTION);
                        if(result == JOptionPane.YES_OPTION){
                            inomingWriter.write("Accepted\n");
                            inomingWriter.flush();
                            socket = incoming;
                            reader = incomingReader;
                            writer = inomingWriter;
                            textArea1.append("Connected to " + socket.getInetAddress().getHostAddress() + "\n");
                            listenForMessages();
                        } else {
                            inomingWriter.write("Rejected\n");
                            inomingWriter.flush();
                            incoming.close();
                        }
                    }
                }
            } catch (IOException ex){
                textArea1.append("Error listening: " + ex.getMessage() + "\n");
            }
        });
        listeningThread.start();
    }

    private void listenForMessages(){
        Thread messageThread = new Thread(() -> {
            try{
                String line;
                while((line = reader.readLine()) != null){
                    textArea1.append("Other: " + line + "\n");
                }
            } catch (IOException ex){
                textArea1.append("Connection closed with " + socket.getInetAddress().getHostAddress() + "\n");
            }
        });
        messageThread.start();
    }

    public ChatP2P () {
        setContentPane(mainPanel);

        int assignedPort = findAvaiblePort();
        puertoField1.setText(String.valueOf(assignedPort));

        String assignedIP = findAvailableIP();
        ipField1.setText(assignedIP);

        setTitle("Chat P2P " + "localHost");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(600, 500);
        setLocationRelativeTo(null);
        setResizable(true);

        puertoButton1.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                startListening();
            }
        });
        llamarButton1.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String ip = ipField1.getText();
                int port = Integer.parseInt(puertoField2.getText());
                try {
                    socket = new Socket(ip, port);
                    writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                    reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    writer.write("Chat request\n");
                    writer.flush();

                    String response = reader.readLine();
                    if ("Accepted".equals(response)) {
                        textArea1.append("Connected with " + ip + "\n");
                        listenForMessages();
                    } else {
                        JOptionPane.showMessageDialog(ChatP2P.this, ip + " Request denied", "New chat", JOptionPane.WARNING_MESSAGE);
                        socket.close();
                    }
                } catch (IOException ex) {
                    textArea1.append("Error connecting: " + ex.getMessage() + "\n");
                }
            }
        });

        envioButton1.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String msg = envioField1.getText();
                try{
                    if(writer != null){
                        writer.write(msg + "\n");
                        writer.flush();
                        textArea1.append("Me: " + msg + "\n");
                        envioField1.setText("");
                    } else {
                        JOptionPane.showMessageDialog(ChatP2P.this, "No connection established", "Error", JOptionPane.ERROR_MESSAGE);
                    }
                } catch(IOException ex){
                    textArea1.append("Error sending message: " + ex.getMessage() + "\n");
                }
            }
        });
        setVisible(true);
    }

    public static void main(String[] args) {
        new ChatP2P();
    }
}
