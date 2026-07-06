import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class Main {
    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());
    private static final int PORTA = 50123;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public static void main(String[] args) {
        configurarLogger();
        LOGGER.info("Iniciando o servidor de chat...");

        ExecutorService fofoqueiro = Executors.newFixedThreadPool(4);
        CopyOnWriteArrayList<Participante> participantes = new CopyOnWriteArrayList<>();

        try (ServerSocket serverSocket = new ServerSocket(PORTA)) {
            LOGGER.info("Servidor aguardando conexões na porta " + PORTA);
            while (true) {
                Socket socket = serverSocket.accept();
                LOGGER.info("Nova conexão recebida de " + socket.getRemoteSocketAddress());

                Participante participante = new Participante(socket, participantes, fofoqueiro);
                participantes.add(participante);
                Thread thread = new Thread(participante, "Participante-" + participantes.size());
                thread.start();
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Erro no servidor", e);
        } finally {
            fofoqueiro.shutdownNow();
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

    static String formatarHorario() {
        return LocalDateTime.now().format(FORMATTER);
    }

    static class Participante implements Runnable {
        private final Socket socket;
        private final CopyOnWriteArrayList<Participante> participantes;
        private final ExecutorService fofoqueiro;
        private final BufferedReader entrada;
        private final PrintWriter saida;
        private String apelido;
        private boolean ativo = true;

        Participante(Socket socket, CopyOnWriteArrayList<Participante> participantes, ExecutorService fofoqueiro)
                throws IOException {
            this.socket = socket;
            this.participantes = participantes;
            this.fofoqueiro = fofoqueiro;
            this.entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.saida = new PrintWriter(socket.getOutputStream(), true);
        }

        @Override
        public void run() {
            try {
                String mensagem;
                while ((mensagem = entrada.readLine()) != null) {
                    String texto = mensagem.trim();
                    if (texto.isEmpty()) {
                        continue;
                    }

                    if ("##sair##".equalsIgnoreCase(texto)) {
                        LOGGER.info("Participante " + apelido + " solicitou encerramento");
                        break;
                    }

                    if (apelido == null) {
                        apelido = texto;
                        LOGGER.info("Participante conectado com apelido " + apelido);
                        fofoqueiro.execute(new ServicoMensagem(apelido, "entrou no chat", participantes));
                        continue;
                    }

                    LOGGER.log(Level.INFO, "Mensagem recebida de {0}: {1}", new Object[] { apelido, texto });
                    fofoqueiro.execute(new ServicoMensagem(apelido, texto, participantes));
                }
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Erro na comunicação com " + apelido, e);
            } finally {
                encerrar();
            }
        }

        synchronized void enviarMensagem(String mensagem) {
            if (ativo) {
                saida.println(mensagem);
            }
        }

        boolean isAtivo() {
            return ativo;
        }

        void encerrar() {
            if (!ativo) {
                return;
            }
            ativo = false;
            participantes.remove(this);

            try {
                socket.close();
            } catch (IOException e) {
                LOGGER.log(Level.FINE, "Erro ao fechar socket", e);
            }

            LOGGER.info("Participante " + (apelido == null ? "desconectado" : apelido) + " saiu do chat");
        }
    }

    static class ServicoMensagem implements Runnable {
        private final String apelido;
        private final String texto;
        private final CopyOnWriteArrayList<Participante> participantes;

        ServicoMensagem(String apelido, String texto, CopyOnWriteArrayList<Participante> participantes) {
            this.apelido = apelido;
            this.texto = texto;
            this.participantes = participantes;
        }

        @Override
        public void run() {
            String horario = formatarHorario();
            LOGGER.log(Level.INFO, "{0} FINE ({1}) - {2}", new Object[] { horario, apelido, texto });

            for (Participante participante : participantes) {
                if (participante.isAtivo()) {
                    participante.enviarMensagem(horario + " (" + apelido + ") - " + texto);
                }
            }
        }
    }
}
