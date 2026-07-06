import java.awt.BorderLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

public class Main {
    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());
    private static final int PORTA = 50123;

    public static void main(String[] args) {
        configurarLogger();

        String endereco = "127.0.0.1";
        System.out.println("Digite seu apelido:");
        Scanner scanner = new Scanner(System.in);
        String apelido = scanner.nextLine().trim();
        scanner.close();

        try {
            new ClienteChat(endereco, PORTA, apelido).iniciar();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Falha ao conectar ao servidor", e);
        }
    }

    private static void configurarLogger() {
        LOGGER.setUseParentHandlers(false);
        LOGGER.setLevel(Level.INFO);
        ConsoleHandler handler = new ConsoleHandler();
        handler.setLevel(Level.INFO);
        handler.setFormatter(new SimpleFormatter());
        LOGGER.addHandler(handler);
    }

    static class ClienteChat {
        private final String endereco;
        private final int porta;
        private final String apelido;
        private Socket socket;
        private BufferedReader entrada;
        private PrintWriter saida;
        private JTextArea areaMensagens;
        private JTextField campoMensagem;
        private JFrame janela;

        ClienteChat(String endereco, int porta, String apelido) {
            this.endereco = endereco;
            this.porta = porta;
            this.apelido = apelido;
        }

        void iniciar() throws IOException {
            socket = new Socket(endereco, porta);
            entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            saida = new PrintWriter(socket.getOutputStream(), true);

            LOGGER.info("Conectado ao servidor " + endereco + ":" + porta);
            saida.println(apelido);

            iniciarGUI();
        }


        private void iniciarGUI() {
            SwingUtilities.invokeLater(() -> {
                janela = new JFrame("Chat - " + apelido);
                janela.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
                janela.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosing(WindowEvent e) {
                        enviarMensagem("##sair##");
                        fecharConexao();
                        janela.dispose();
                    }
                });

                areaMensagens = new JTextArea();
                areaMensagens.setEditable(false);
                areaMensagens.setLineWrap(true);
                areaMensagens.setWrapStyleWord(true);

                campoMensagem = new JTextField();
                JButton botaoEnviar = new JButton("Enviar");
                botaoEnviar.addActionListener(e -> enviarMensagemDaInterface());
                campoMensagem.addActionListener(e -> enviarMensagemDaInterface());

                JPanel painelInferior = new JPanel(new BorderLayout());
                painelInferior.add(campoMensagem, BorderLayout.CENTER);
                painelInferior.add(botaoEnviar, BorderLayout.EAST);

                janela.add(new JScrollPane(areaMensagens), BorderLayout.CENTER);
                janela.add(painelInferior, BorderLayout.SOUTH);
                janela.setSize(500, 400);
                janela.setLocationRelativeTo(null);
                janela.setVisible(true);

                Thread leitor = new Thread(() -> {
                    try {
                        String mensagem;
                        while ((mensagem = entrada.readLine()) != null) {
                            String texto = mensagem;
                            SwingUtilities.invokeLater(() -> areaMensagens.append(texto + System.lineSeparator()));
                        }
                    } catch (IOException e) {
                        SwingUtilities
                                .invokeLater(() -> areaMensagens.append("Conexão encerrada." + System.lineSeparator()));
                    }
                });
                leitor.setDaemon(true);
                leitor.start();
            });
        }

        private void enviarMensagemDaInterface() {
            String texto = campoMensagem.getText().trim();
            if (!texto.isEmpty()) {
                if ("sair".equalsIgnoreCase(texto)) {
                    enviarMensagem("##sair##");
                    fecharConexao();
                    if (janela != null) {
                        janela.dispose();
                    }
                } else {
                    enviarMensagem(texto);
                    campoMensagem.setText("");
                }
            }
        }

        private void enviarMensagem(String texto) {
            if (saida != null) {
                saida.println(texto);
                saida.flush();
            }
        }

        private void fecharConexao() {
            try {
                if (saida != null) {
                    saida.close();
                }
                if (entrada != null) {
                    entrada.close();
                }
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException e) {
                LOGGER.log(Level.FINE, "Erro ao encerrar conexão", e);
            }
        }
    }
}