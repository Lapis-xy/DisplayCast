import java.net.*;
import java.util.*;
import java.io.*;

// Server TCP usato da Sender
public class Server {

    /** Callback per eventi di connessione/disconnessione. */
    public interface ConnectionListener {
        void onClientConnected(String clientId);
        void onClientDisconnected(String clientId);
    }

    private ServerSocket serverSocket;
    private final int port;
    private final List<Socket> clients = Collections.synchronizedList(new ArrayList<>());
    private final Map<Socket, PrintWriter> writers = Collections.synchronizedMap(new HashMap<>());
    private final Map<Socket, String> clientIds = Collections.synchronizedMap(new HashMap<>());
    private ConnectionListener connectionListener;

    public Server(int port) {
        this.port = port;
    }

    public void setConnectionListener(ConnectionListener listener) {
        this.connectionListener = listener;
    }

    public void startServer() {
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("[Server] Avviato sulla porta " + port);
            Thread t = new Thread(this::acceptClients);
            t.setDaemon(true);
            t.start();
        } catch (IOException e) {
            System.err.println("[Server] Errore avvio: " + e.getMessage());
        }
    }

    private void acceptClients() {
        while (true) {
            try {
                Socket client = serverSocket.accept();
                clients.add(client);
                PrintWriter writer = new PrintWriter(client.getOutputStream(), true);
                writers.put(client, writer);
                System.out.println("[Server] Nuovo client: " + client.getRemoteSocketAddress());
                Thread t = new Thread(() -> readFromClient(client));
                t.setDaemon(true);
                t.start();
            } catch (IOException e) {
                System.err.println("[Server] Errore accept: " + e.getMessage());
            }
        }
    }

    private void readFromClient(Socket client) {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
            String line;
            while ((line = in.readLine()) != null) {
                if (line.startsWith("REGISTER:")) {
                    String id = line.substring("REGISTER:".length()).trim();
                    clientIds.put(client, id);
                    System.out.println("[Server] Registrato: " + id);
                    if (connectionListener != null) connectionListener.onClientConnected(id);
                }
            }
        } catch (IOException e) { }
        removeClient(client);
    }

    private void removeClient(Socket s) {
        clients.remove(s);
        writers.remove(s);
        String id = clientIds.remove(s);
        if (id != null) {
            System.out.println("[Server] Disconnesso: " + id);
            if (connectionListener != null) connectionListener.onClientDisconnected(id);
        }
        try { s.close(); } catch (IOException ignored) {}
    }

    /** Invia messaggio a TUTTI i client connessi. */
    public void inviaMsg(String msg) {
        synchronized (clients) {
            for (Socket s : new ArrayList<>(clients)) {
                PrintWriter w = writers.get(s);
                if (w != null) w.println(msg);
            }
        }
    }

    /** Invia messaggio a un singolo client per ID. */
    public void inviaMsgA(String clientId, String msg) {
        synchronized (clientIds) {
            for (Map.Entry<Socket, String> e : clientIds.entrySet()) {
                if (clientId.equals(e.getValue())) {
                    PrintWriter w = writers.get(e.getKey());
                    if (w != null) w.println(msg);
                    return;
                }
            }
        }
        System.out.println("[Server] Client non trovato: " + clientId);
    }

    public Set<String> getConnectedIds() {
        return new HashSet<>(clientIds.values());
    }

    public int getClientCount() {
        return clients.size();
    }
}
