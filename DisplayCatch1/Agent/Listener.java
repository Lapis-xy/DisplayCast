import java.net.*;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import javax.swing.*;
import java.awt.*;
import java.util.*;

// Listener: riceve e mostra messaggi dai Sender
public class Listener {

    private static final int UDP_PORT = 4445;
    private static final String DEFAULT_TEXT = "In attesa di connessione...";
    // Panel background alpha (0-255)
    private static final int PANEL_BG_ALPHA = 200;
    // Font size limits
    private static final int MIN_FONT_SIZE = 12;
    private static final int MAX_FONT_SIZE = 200;
    // Safety margin (pixels)
    private static final int TEXT_SAFE_MARGIN = 120;

    private static java.util.List<JFrame> frames = new ArrayList<>();
    private static java.util.List<javax.swing.JTextPane> messageLabels = new ArrayList<>();
    private static String lastMessage = DEFAULT_TEXT;

    public static void main(String[] args) {
        // Genera un ID per questo dispositivo: prova a usare il nome host, altrimenti fallback
        String clientId;
        try {
            clientId = InetAddress.getLocalHost().getHostName();
            if (clientId == null || clientId.isBlank()) throw new Exception("empty hostname");
        } catch (Exception e) {
            clientId = "TV-" + (System.currentTimeMillis() % 10000);
        }

        // Il Listener resta in background; la finestra verrà mostrata solo alla connessione
        // L'avvio automatico è gestito da Start-Listener.bat (chiave di registro Windows)

        // --- Loop principale: ascolta broadcast -> connetti -> mostra messaggi ---
        while (true) {
            updateDisplay(DEFAULT_TEXT);
            System.out.println("[Listener " + clientId + "] In ascolto broadcast UDP...");

            String senderIp = null;
            int tcpPort = 5555;

            // FASE 1: Aspetta broadcast UDP
            try (DatagramSocket udpSocket = new DatagramSocket(UDP_PORT)) {
                udpSocket.setBroadcast(true);
                byte[] buf = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                udpSocket.receive(packet);

                String msg = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8).trim();
                senderIp = packet.getAddress().getHostAddress();

                if (msg.startsWith("PORT:")) {
                    try { tcpPort = Integer.parseInt(msg.substring(5).trim()); } catch (NumberFormatException ignored) {}
                }

                System.out.println("[Listener] Sender trovato: " + senderIp + ":" + tcpPort);
            } catch (IOException e) {
                System.err.println("[Listener] Errore UDP: " + e.getMessage());
                sleep(2000);
                continue;
            }

            // FASE 2: Connetti via TCP
            lastMessage = "Connessione in corso...";

            Client client = new Client(senderIp, tcpPort, clientId);
            client.setMessageListener(message -> onMessageReceived(message));
            client.connectToServer();

            if (!client.isConnected()) {
                lastMessage = "Connessione fallita. Riprovo...";
                sleep(3000);
                continue;
            }

            // Non aprire la finestra alla connessione: verrà mostrata
            // solo dopo il primo messaggio ricevuto dal Sender.

            // FASE 3: Resta connesso
            client.waitUntilDisconnected();

            // Chiudi/nascondi la finestra e torna ad ascoltare
            hideDisplay();
            lastMessage = "Disconnesso. Riconnessione...";
            sleep(2000);
        }
    }

    /** Crea una finestra fullscreen bianca per ogni monitor disponibile. */
    private static void createDisplay() {
        frames.clear();
        messageLabels.clear();

        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice[] gds = ge.getScreenDevices();

        for (int i = 0; i < gds.length; i++) {
            GraphicsDevice gd = gds[i];
            Rectangle bounds = gd.getDefaultConfiguration().getBounds();

            JFrame f = new JFrame("Listener Display " + (i + 1));
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setUndecorated(true);
        
            f.setBackground(new Color(255, 255, 255, PANEL_BG_ALPHA));
            f.setLayout(new BorderLayout());
            f.setBounds(bounds);

            int availWidth = Math.max(200, bounds.width - 240);
            int availHeight = Math.max(100, bounds.height - 240);
            JTextPane lbl = new JTextPane();
            lbl.setEditable(false);
            lbl.setOpaque(false);
            lbl.setFocusable(false);
            lbl.setForeground(new Color(50, 50, 50));
            lbl.setFont(new Font("Arial", Font.BOLD, 64));
            lbl.setBorder(BorderFactory.createEmptyBorder(10, 24, 10, 24));
            // center paragraph alignment
            javax.swing.text.SimpleAttributeSet centerAttr = new javax.swing.text.SimpleAttributeSet();
            javax.swing.text.StyleConstants.setAlignment(centerAttr, javax.swing.text.StyleConstants.ALIGN_CENTER);
            lbl.setParagraphAttributes(centerAttr, false);

            JPanel panel = new JPanel(new BorderLayout());
            // Keep the panel transparent so the frame's semi-transparent
            // background (showing the desktop) is visible through it.
            panel.setOpaque(false);
            panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(40, 40, 40, 40),
                BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(200, 200, 200), 3, true),
                    BorderFactory.createEmptyBorder(60, 60, 60, 60)
                )
            ));
            panel.add(lbl, BorderLayout.CENTER);

            f.add(panel, BorderLayout.CENTER);
            f.setAlwaysOnTop(true);

            frames.add(f);
            messageLabels.add(lbl);
            fitTextToPane(lbl, lastMessage, availWidth, availHeight);
        }
    }

 
    /** Nasconde e distrugge la finestra display. */
    private static void hideDisplay() {
        SwingUtilities.invokeLater(() -> {
            for (JFrame f : frames) {
                try { if (f != null) { f.setVisible(false); f.dispose(); } } catch (Exception ignored) {}
            }
            frames.clear();
            messageLabels.clear();
        });
    }

    /** Aggiorna il testo sullo schermo (thread-safe). */
    private static void updateDisplay(String text) {
        lastMessage = text;
        SwingUtilities.invokeLater(() -> {
            for (int i = 0; i < messageLabels.size(); i++) {
                    JTextPane lbl = messageLabels.get(i);
                JFrame f = (i < frames.size() ? frames.get(i) : null);
                int availW = Math.max(200, (f != null ? f.getWidth() : 800) - 240);
                int availH = Math.max(100, (f != null ? f.getHeight() : 600) - 240);
                    if (lbl != null) fitTextToPane(lbl, text, availW, availH);
            }
        });
    }

    /** Chiamato quando arriva un messaggio dal Sender. Mostra la finestra se necessario. */
    private static void onMessageReceived(String text) {
        if (text != null) {
            String up = text.toUpperCase();
            if (up.startsWith("CMD:")) {
                String cmd = text.substring(4).trim();
                if (cmd.equalsIgnoreCase("HIDE")) {
                    hideDisplay();
                    return;
                }
                // altri comandi possono essere gestiti qui senza terminare il processo
            }
        }

        lastMessage = text;
        SwingUtilities.invokeLater(() -> {
            if (frames.isEmpty()) {
                createDisplay();
            }
            for (int i = 0; i < messageLabels.size(); i++) {
                JTextPane lbl = messageLabels.get(i);
                JFrame f = frames.get(i);
                int availW = Math.max(200, (f != null ? f.getWidth() : 800) - 240);
                int availH = Math.max(100, (f != null ? f.getHeight() : 600) - 240);
                try {
                    Container parent = lbl != null ? lbl.getParent() : null;
                    if (parent != null && parent.getWidth() > 100) {
                        Insets pin = parent.getInsets();
                        Insets lin = lbl.getInsets();
                        availW = Math.max(50, parent.getWidth() - pin.left - pin.right - lin.left - lin.right - 20);
                        availH = Math.max(20, parent.getHeight() - pin.top - pin.bottom - lin.top - lin.bottom - 20);
                    }
                } catch (Exception ignored) {}
                if (lbl != null) fitTextToPane(lbl, text, availW, availH);
                if (f != null) { f.setVisible(true); f.toFront(); f.requestFocus(); }
            }
        });
    }

    /** Resize and wrap text into a JTextPane so it fits within maxWidth/maxHeight.
     *  Uses Swing's layout to compute wrapped height and reduces font size until it fits.
     */
    private static void fitTextToPane(JTextPane pane, String text, int maxWidth, int maxHeight) {
        if (pane == null) return;
        String raw = text == null ? "" : text;

        int effectiveMaxWidth = Math.max(50, maxWidth);
        int effectiveMaxHeight = Math.max(20, maxHeight);
        try {
            Container parent = pane.getParent();
            if (parent != null && parent.getWidth() > 0 && parent.getHeight() > 0) {
                Insets pin = parent.getInsets();
                Insets lin = pane.getInsets();
                effectiveMaxWidth = Math.max(50, parent.getWidth() - pin.left - pin.right - lin.left - lin.right - TEXT_SAFE_MARGIN);
                effectiveMaxHeight = Math.max(20, parent.getHeight() - pin.top - pin.bottom - lin.top - lin.bottom - TEXT_SAFE_MARGIN);
            } else {
                Insets lin = pane.getInsets();
                effectiveMaxWidth = Math.max(50, maxWidth - lin.left - lin.right - TEXT_SAFE_MARGIN);
                effectiveMaxHeight = Math.max(20, maxHeight - lin.top - lin.bottom - TEXT_SAFE_MARGIN);
            }
        } catch (Exception ignored) {}

        String fontFamily = "Arial";
        int fontStyle = Font.BOLD;

        // Try sizes from large to small
        int startSize = Math.min(MAX_FONT_SIZE, Math.max(MIN_FONT_SIZE, effectiveMaxHeight));
        int chosen = MIN_FONT_SIZE;
        for (int size = startSize; size >= MIN_FONT_SIZE; size--) {
            Font f = new Font(fontFamily, fontStyle, size);
            pane.setFont(f);
            pane.setText(raw);
            // center paragraph
            javax.swing.text.SimpleAttributeSet attr = new javax.swing.text.SimpleAttributeSet();
            javax.swing.text.StyleConstants.setAlignment(attr, javax.swing.text.StyleConstants.ALIGN_CENTER);
            pane.setParagraphAttributes(attr, false);

            // Let Swing compute preferred size for this width
            pane.setSize(effectiveMaxWidth, Integer.MAX_VALUE);
            Dimension pref = pane.getPreferredSize();
            if (pref.height <= effectiveMaxHeight) { chosen = size; break; }
        }

        // Apply chosen size and set final text
        pane.setFont(new Font(fontFamily, fontStyle, chosen));
        pane.setText(raw);
        javax.swing.text.SimpleAttributeSet attr = new javax.swing.text.SimpleAttributeSet();
        javax.swing.text.StyleConstants.setAlignment(attr, javax.swing.text.StyleConstants.ALIGN_CENTER);
        pane.setParagraphAttributes(attr, false);
        pane.setSize(effectiveMaxWidth, effectiveMaxHeight);
        pane.revalidate();
        pane.repaint();
    }
    
    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }

}
