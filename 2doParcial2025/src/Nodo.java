import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.*;

public class Nodo {
    private final String nombre;
    private String ip;
    private int puerto;
    private String siguienteNickname;
    private boolean tieneToken = false;
    private boolean conectado = false;
    private final Random random = new Random();
    private JFrame frame;
    private JPanel mainPanel;
    private JLabel nextipLabel;
    private JPanel configPanel;
    private JTextField nextipField;
    private JLabel portLabel;
    private JTextField portField;
    private JLabel nextportLabel;
    private JTextField nextportField;
    private JButton btnToken;
    private JTextArea logArea;
    private JScrollPane scrollPane;
    private JButton btnConnect;
    private JLabel ipLabel;
    private JLabel nextnickLabel;
    private JLabel nickLabel;


    public Nodo(String nombre) {
        this.nombre = nombre;
        iniciarGUI();
    }

    private void iniciarGUI() {
        frame = new JFrame("Nodo: " + nombre);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(600, 300);
        frame.setContentPane(mainPanel);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        btnConnect.addActionListener(e -> conectar());
        btnToken.addActionListener(e -> generarToken());

        // Color inicial
        actualizarEstadoToken();
    }

    private void conectar() {
        if (conectado) return;
        ip = nextipField.getText().trim();
        puerto = Integer.parseInt(portField.getText().trim());
        try {
            ServerSocket ss = new ServerSocket(puerto);
            new Thread(() -> escuchar(ss), "Nodo-Listener").start();
            registrarEnDirectorio();
            enviarReady();
            conectado = true;
            btnConnect.setEnabled(false);
            btnToken.setEnabled(true);
            nextipField.setEditable(false);
            portField.setEditable(false);
            logArea.append("Conectado como " + ip + ":" + puerto + "\n");
        } catch (IOException e) {
            logArea.append("Error al conectar: " + e.getMessage() + "\n");
        }
    }

    private void registrarEnDirectorio() throws IOException {
        try (Socket socket = new Socket("localhost", 5000);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            out.println("REGISTRAR " + nombre + " " + ip + " " + puerto);
            in.readLine(); // OK
        }
    }

    private void enviarReady() throws IOException {
        try (Socket socket = new Socket("localhost", 5000);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            out.println("READY " + nombre);
            in.readLine(); // OK
        }
    }

    private void escuchar(ServerSocket ss) {
        logArea.append("Escuchando en " + ip + ":" + puerto + "\n");
        while (true) {
            try (Socket s = ss.accept();
                 BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
                 PrintWriter out = new PrintWriter(s.getOutputStream(), true)) {
                String msg = in.readLine();
                if (msg == null) continue;
                String[] parts = msg.split(" ");
                switch (parts[0]) {
                    case "TOKEN":
                        manejarToken();
                        out.println("OK");
                        break;
                    case "SIGUIENTE":
                        String nuevo = parts[1];
                        if (!nuevo.equals(siguienteNickname)) {
                            siguienteNickname = nuevo;
                            logArea.append("Actualizado siguiente nodo: " + siguienteNickname + "\n");
                            if (tieneToken) {
                                LogHandler.getInstance().log(ip, puerto, "TOKEN_DELETE");
                                tieneToken = false;
                                actualizarEstadoToken();
                            }
                        }
                        out.println("OK");
                        break;
                    case "GENERAR_TOKEN":
                        generarToken();
                        out.println("OK");
                        break;
                }
            } catch (IOException | InterruptedException e) {
                logArea.append("Error listener: " + e.getMessage() + "\n");
            }
        }
    }

    private void generarToken() {
        if (!conectado) {
            logArea.append("Error: Debe conectar primero\n");
            return;
        }
        if (tieneToken) {
            logArea.append("Ya posee el token \n");
            return;
        }
        new Thread(() -> {
            try {
                manejarToken();
            } catch (IOException | InterruptedException e) {
                logArea.append("Error al manejar token: " + e.getMessage() + "\n");
            }
        }, "Nodo-Token").start();
    }

    private void manejarToken() throws IOException, InterruptedException {
        tieneToken = true;
        actualizarEstadoToken();
        LogHandler.getInstance().log(ip, puerto, nombre);
        Thread.sleep(1000 + random.nextInt(4000));
        passarYManejarFallo();
        tieneToken = false;
        actualizarEstadoToken();
    }

    private void passarYManejarFallo() throws IOException {
        if (siguienteNickname == null || siguienteNickname.isEmpty()) {
            logArea.append("No hay nodo siguiente configurado\n");
            return;
        }
        String direccion = resolver(siguienteNickname);
        if (direccion != null) {
            String[] d = direccion.split(":");
            try (Socket socket = new Socket(d[0], Integer.parseInt(d[1]));
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
                out.println("TOKEN");
                logArea.append("Token enviado a " + direccion + " (" + siguienteNickname + ")\n");
            } catch (IOException e) {
                logArea.append("Error al pasar token a " + siguienteNickname + ": " + e.getMessage() + "\n");
                        // Notifica baja al Directorio para reconfigurar anillo
                        notificarDesregistrar(siguienteNickname);
            }
        } else {
            logArea.append("No se pudo resolver la dirección de " + siguienteNickname + "\n");
        }
    }

    private void notificarDesregistrar(String nombre) {
        try (Socket socket = new Socket("localhost", 5000);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            out.println("DESREGISTRAR " + nombre);
            in.readLine(); // OK
        } catch (IOException e) {
            logArea.append("Error notificar desregistrar: " + e.getMessage() + "\n");
        }
    }

    private String resolver(String nickname) throws IOException {
        try (Socket socket = new Socket("localhost", 5000);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            out.println("RESOLVER " + nickname);
            String resp = in.readLine();
            return "DESCONOCIDO".equals(resp) ? null : resp;
        }
    }

    private void actualizarEstadoToken() {
        frame.getContentPane().setBackground(tieneToken ? Color.GREEN : Color.LIGHT_GRAY);
        logArea.append(tieneToken ? "Posee el token\n" : "No posee el token\n");
    }

    public static void main(String[] args) {
        String nombre = JOptionPane.showInputDialog("Ingrese el nickname del nodo:");
        SwingUtilities.invokeLater(() -> new Nodo(nombre));
    }
}
