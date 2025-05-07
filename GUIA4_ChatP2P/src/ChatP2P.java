import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

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

    public ChatP2P () {
        setContentPane(mainPanel);
        setTitle("Chat P2P");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(600, 500);
        setLocationRelativeTo(null);
        setResizable(true);
        setVisible(true);

        puertoButton1.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

            }
        });
        llamarButton1.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

            }
        });
        envioButton1.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

            }
        });
    }

    public static void main(String[] args) {
        new ChatP2P();
    }
}
