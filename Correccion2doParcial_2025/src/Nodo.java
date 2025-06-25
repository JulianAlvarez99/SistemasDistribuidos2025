import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.*;
import java.util.Random;

public class Nodo {
    private String nombre;
    private String ip;
    private int puerto;
    private String siguienteNickname;
    private String siguienteIp;
    private int siguientePuerto;
    private volatile boolean tieneToken = false;
    private boolean conectado = false;
    private final int PUERTO_DIRECTORIO = 5000;
    private final String IP_DIRECTORIO = "localhost";
    private Color blockColor = Color.LIGHT_GRAY;
    private final Random random = new Random();// Componentes de la GUI

    private JFrame frame;
    private JPanel mainPanel;
    private JPanel configPanel;
    private JLabel ipLabel;
    private JTextField ipField;
    private JLabel portLabel;
    private JTextField portField;
    private JLabel nickLabel;
    private JTextField nickField;
    private JLabel nextipLabel;
    private JTextField nextipField;
    private JLabel nextportLabel;
    private JTextField nextportField;
    private JLabel nextnickLabel;
    private JTextField nextnickField;
    private JButton btnToken;
    private JTextArea logArea;
    private JScrollPane scrollPane;
    private JButton btnReg;

    public Nodo() {
        iniciarGUI();
    }

    private void iniciarGUI() {
        nombre = "noname";
        frame = new JFrame("Nodo: " + nombre);
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE); // Controlar cierre manualmente
        frame.setSize(600, 400);
        frame.setLocationRelativeTo(null);
        ipField.setText(IP_DIRECTORIO);
        logArea.setEditable(false);
        frame.setContentPane(mainPanel);
        nextipField.setEditable(false);
        nextipField.setBackground(blockColor);
        nextportField.setEditable(false);
        nextportField.setBackground(blockColor);
        nextnickField.setEditable(false);
        nextnickField.setBackground(blockColor);
        btnToken.setEnabled(false);

        // Manejador para el cierre de la ventana
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (conectado) {
                    notificarDesregistrar(nombre);
                }
                System.exit(0);
            }
        });

        frame.setVisible(true);

        btnReg.addActionListener(e -> {
            try {
                registrarEnDirectorio();
                conectar();
            } catch (IOException ex) {
                logArea.append("Error: " + ex.getMessage() + "\n");
            }
        });

        btnToken.addActionListener(e -> generarToken());

        actualizarEstadoToken();
    }

    private void conectar() throws IOException {
        if (conectado) return;
        ServerSocket ss = new ServerSocket(puerto);
        conectado = true;
        btnReg.setEnabled(false);
        btnToken.setEnabled(true);
        ipField.setEditable(false);
        portField.setEditable(false);
        nickField.setEditable(false);
        ipField.setBackground(blockColor);
        portField.setBackground(blockColor);
        nickField.setBackground(blockColor);
        logArea.append("Conectado como " + ip + ":" + puerto + " (" + nombre + ")\n");
        new Thread(() -> escuchar(ss), "Nodo-Listener").start();
        try (Socket socket = new Socket(IP_DIRECTORIO, PUERTO_DIRECTORIO);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            out.println("LISTO " + nombre);
            in.readLine(); // Esperar respuesta OK
        }
    }

    private void registrarEnDirectorio() throws IOException {
        ip = ipField.getText().trim();
        puerto = Integer.parseInt(portField.getText().trim());
        nombre = nickField.getText().trim();
        frame.setTitle("Nodo: " + nombre);

        try (Socket socket = new Socket(IP_DIRECTORIO, PUERTO_DIRECTORIO);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            out.println("REGISTRAR " + nombre + " " + ip + " " + puerto);
            String response = in.readLine();
            if (!"OK".equals(response)) {
                throw new IOException("Registro fallido: " + response);
            }
            logArea.append("Registrado en directorio\n");
        }
    }

    private void escuchar(ServerSocket ss) {
        logArea.append("Escuchando en " + ip + ":" + puerto + "\n");
        while (conectado) {
            try (Socket s = ss.accept();
                 BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
                 PrintWriter out = new PrintWriter(s.getOutputStream(), true)) {
                String msg = in.readLine();
                if (msg == null) continue;
                logArea.append("Mensaje recibido: " + msg + "\n");
                String[] parts = msg.split(" ");
                switch (parts[0]) {
                    case "DISCARD_TOKEN":
                        if (tieneToken) {
                            LogHandler.getInstance().log(this.ip, this.puerto, nombre + " TOKEN_DELETE");
                            logArea.append("Token descartado\n");
                            tieneToken = false;
                            actualizarEstadoToken();
                        }
                        out.println("OK");
                        break;
                    case "SIGUIENTE":
                        actualizarSiguiente(parts[1], parts[2], Integer.parseInt(parts[3]));
                        out.println("OK");
                        break;
                    case "GENERAR_TOKEN":
                        generarToken();
                        out.println("OK");
                        break;
                    case "TOKEN":
                        if (!tieneToken) {
                            new Thread(() -> {
                                try {
                                    manejarToken();
                                } catch (Exception ex) {
                                    logArea.append("Error token: " + ex.getMessage() + "\n");
                                }
                            }).start();
                        }
                        out.println("OK");
                        break;
                    default:
                        out.println("COMANDO_DESCONOCIDO");
                }
            } catch (IOException e) {
                logArea.append("Error en listener: " + e.getMessage() + "\n");
            }
        }
    }

    private void generarToken() {
        if (!conectado) {
            logArea.append("Error: Debe conectar primero\n");
            return;
        }
        if (tieneToken) {
            logArea.append("Ya posee el token, no se genera nuevo\n");
            return;
        }
        if (siguienteNickname == null || siguienteNickname.isEmpty()) {
            logArea.append("No se puede generar token: nodo siguiente no configurado\n");
            return;
        }
        logArea.append("Generando token para " + nombre + "\n");
        new Thread(() -> {
            try {
                manejarToken();
            } catch (IOException | InterruptedException e) {
                logArea.append("Error al manejar token: " + e.getMessage() + "\n");
            }
        }, "Nodo-Token").start();
    }

    private synchronized void manejarToken() throws IOException, InterruptedException {
        if (tieneToken) {
            logArea.append("Ignorado: ya posee el token\n");
            return;
        }

        tieneToken = true;
        actualizarEstadoToken();

        LogHandler.getInstance().log(ip, puerto, nombre);
        Thread.sleep(1000 + random.nextInt(4000)); // Retención entre 1 y 5 segundos

        pasarYManejarFallo();

        tieneToken = false;
        actualizarEstadoToken();
    }

    private void pasarYManejarFallo() throws IOException {
        if (siguienteNickname == null || siguienteNickname.isEmpty()) {
            logArea.append("No hay nodo siguiente configurado\n");
            return;
        }
        try (Socket socket = new Socket(siguienteIp, siguientePuerto);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            out.println("TOKEN");
            logArea.append("Token enviado a " + siguienteNickname + " (" + siguienteIp + ":" + siguientePuerto + ")\n");
        } catch (IOException e) {
            logArea.append("Error al pasar token a " + siguienteNickname + ": " + e.getMessage() + "\n");
            notificarDesregistrar(siguienteNickname);
            try {
                Thread.sleep(500);
            } catch (InterruptedException ignored) {}
        }
    }

    private void notificarDesregistrar(String nombre) {
        try (Socket socket = new Socket(IP_DIRECTORIO, PUERTO_DIRECTORIO);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            out.println("DESREGISTRAR " + nombre);
            in.readLine();
            logArea.append("Nodo " + nombre + " desregistrado\n");
        } catch (IOException e) {
            logArea.append("Error notificar desregistrar: " + e.getMessage() + "\n");
        }
    }

    private void actualizarSiguiente(String nickname, String ip, int puerto) {
        siguienteNickname = nickname;
        siguienteIp = ip;
        siguientePuerto = puerto;
        SwingUtilities.invokeLater(() -> {
            nextnickField.setText(nickname);
            nextipField.setText(ip);
            nextportField.setText(String.valueOf(puerto));
            logArea.append("Actualizado siguiente nodo: " + nickname + " (" + ip + ":" + puerto + ")\n");
        });
    }

    private synchronized void actualizarEstadoToken() {
        SwingUtilities.invokeLater(() -> {
            frame.getContentPane().setBackground(tieneToken ? Color.GREEN : Color.LIGHT_GRAY);
            logArea.append(tieneToken ? "Posee el token\n" : "No posee el token\n");
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Nodo::new);
    }
}

