import java.io.*;
import java.net.*;

// Client TCP minimal (usato da Listener)
public class Client {

    private Socket socket;
    private final String ip;
    private final int port;
    private final String nome;
    private volatile boolean connesso = false;
    private BufferedReader in;
    private PrintWriter out;
    private Thread readerThread;

    public interface MessageListener {
        void onMessageReceived(String message);
    }

    private MessageListener messageListener;

    public Client(String ip, int port, String nome) {
        this.ip = ip;
        this.port = port;
        this.nome = nome;
    }

    public void setMessageListener(MessageListener listener) {
        this.messageListener = listener;
    }

    public void connectToServer() {
        try {
            socket = new Socket(ip, port);
            connesso = true;
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            System.out.println("[Client] Connesso a " + ip + ":" + port);
        } catch (IOException e) {
            System.err.println("[Client] Impossibile connettersi: " + e.getMessage());
            connesso = false;
            return;
        }

        out.println("REGISTER:" + nome);

        readerThread = new Thread(this::leggiMsg);
        readerThread.setDaemon(false);
        readerThread.start();
    }

    public void waitUntilDisconnected() {
        if (readerThread != null) {
            try { readerThread.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }

    public boolean isConnected() {
        return connesso;
    }

    private void leggiMsg() {
        try {
            String msg;
            while ((msg = in.readLine()) != null) {
                if (messageListener != null) {
                    messageListener.onMessageReceived(msg);
                }
            }
        } catch (IOException e) { }
        connesso = false;
    }
}