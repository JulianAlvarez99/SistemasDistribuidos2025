import javax.swing.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;

public class Nodo {
    private String nombre;
    private String ip;
    private int puerto;
    private String siguienteNickname;
    private boolean tieneToken;
    private boolean conectado;
    private final Random random = new Random();
    private JPanel mainPanel;
    private JLabel ipLabel;
    private JPanel configPanel;
    private JTextField ipField;
    private JLabel portLabel;
    private JTextField portField;
    private JLabel nextportLabel;
    private JTextField nextportField;
    private JButton btnToken;
    private JTextArea logArea;
    private JScrollPane scrollPane;
    private JButton btnConnect;
    public Nodo(String nombre) {
        this.nombre = nombre;
        this.tieneToken = false;
        iniciarGUI();
    }

    private void iniciarGUI() {
        JFrame frame = new JFrame("Nodo: " + nombre);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(600, 300);
        frame.setContentPane(mainPanel);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        btnConnect.addActionListener(e -> conectar());
        btnToken.addActionListener(e -> generarToken());
    }

    private void conectar() {
        if (conectado) { logArea.append("Ya está conectado\n"); return; }
        ip = ipField.getText().trim();
        String puertoStr = portField.getText().trim();
        if (ip.isEmpty() || puertoStr.isEmpty()) { logArea.append("Error: IP y puerto son requeridos\n"); return; }
        try { puerto = Integer.parseInt(puertoStr); if (puerto < 1024 || puerto > 65535) throw new NumberFormatException(); }
        catch (NumberFormatException e) { logArea.append("Error: Puerto inválido\n"); return; }
        try {
            ServerSocket serverSocket = new ServerSocket(puerto);
            escuchar(serverSocket);
            registrarEnDirectorio();
            enviarReady();
            conectado = true;
            btnConnect.setEnabled(false);
            btnToken.setEnabled(true);
            ipField.setEditable(false);
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

    private void escuchar(ServerSocket serverSocket) {
        new Thread(() -> {
            logArea.append("Escuchando en " + ip + ":" + puerto + "\n");
            while (true) {
                try (Socket socket = serverSocket.accept();
                     BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                     PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
                    String mensaje = in.readLine(); if (mensaje == null) continue;
                    switch (mensaje.split(" ")[0]) {
                        case "TOKEN":
                            manejarToken(); out.println("OK");
                            break;
                        case "SIGUIENTE":
                            String nuevo = mensaje.split(" ")[1];
                            if (!nuevo.equals(siguienteNickname)) {
                                siguienteNickname = nuevo;
                                logArea.append("Actualizado siguiente nodo: " + siguienteNickname + "\n");
                                if (tieneToken) {
                                    LogHandler.getInstance().log(ip, puerto, "TOKEN_DELETE");
                                    tieneToken = false; actualizarEstadoToken();
                                }
                            }
                            out.println("OK");
                            break;
                        case "GENERAR_TOKEN":
                            generarToken(); out.println("OK");
                            break;
                    }
                } catch (IOException | InterruptedException e) {
                    logArea.append("Error: " + e.getMessage() + "\n");
                }
            }
        }, "Nodo-Listener").start();
    }

    private void generarToken() {
        if (!conectado) { logArea.append("Error: Debe conectar primero\n"); return; }
        if (tieneToken) { logArea.append("Ya posee el token\n"); return; }
        new Thread(() -> {
            try {
                manejarToken();
            } catch (IOException | InterruptedException e) {
                logArea.append("Error al manejar token: " + e.getMessage() + "\n");
            }
        }, "Nodo-Token").start();
    }

    private void manejarToken() throws IOException, InterruptedException {
        tieneToken = true; actualizarEstadoToken();
        LogHandler.getInstance().log(ip, puerto, nombre);
        Thread.sleep(1000 + random.nextInt(4000));
        pasarToken();
        tieneToken = false; actualizarEstadoToken();
    }

    private void pasarToken() throws IOException {
        String nickname = siguienteNickname;
        String puertoSiguiente = nextportField.getText().trim();
        if (!puertoSiguiente.isEmpty()) {
            nickname = resolverPorPuerto(puertoSiguiente);
            if (nickname == null) { logArea.append("No se encontró nodo en el puerto " + puertoSiguiente + "\n"); return; }
        }
        if (nickname == null || nickname.isEmpty()) { logArea.append("No hay nodo siguiente configurado\n"); return; }
        String direccion = resolver(nickname);
        if (direccion != null) {
            String[] d = direccion.split(":");
            try (Socket socket = new Socket(d[0], Integer.parseInt(d[1]));
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
                out.println("TOKEN");
                logArea.append("Token enviado a " + direccion + " (" + nickname + ")\n");
            }
        } else {
            logArea.append("No se pudo resolver la dirección de " + nickname + "\n");
        }
    }

    private String resolver(String nickname) throws IOException {
        try (Socket socket = new Socket("localhost", 5000);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            out.println("RESOLVER " + nickname);
            String resp = in.readLine();
            return resp.equals("DESCONOCIDO") ? null : resp;
        }
    }

    private String resolverPorPuerto(String puerto) throws IOException {
        try (Socket socket = new Socket("localhost", 5000);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            out.println("RESOLVER_POR_PUERTO " + puerto);
            String resp = in.readLine();
            return resp.equals("DESCONOCIDO") ? null : resp;
        }
    }

    private void actualizarEstadoToken() {
        logArea.append(tieneToken ? "Posee el token\n" : "No posee el token\n");
    }

    public static void main(String[] args) {
        String nombre = JOptionPane.showInputDialog("Ingrese el nickname del nodo:");
        SwingUtilities.invokeLater(() -> new Nodo(nombre));
    }
}