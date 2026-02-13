import java.net.*;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import javax.swing.*;
import java.awt.*;
import java.util.*;

// Listener: riceve e mostra messaggi dai Sender
public class Listener {

    private static final int UDP_PORT = 4445;
    private static final String DEFAULT_TEXT = "Pronto — in attesa";
    // Panel background alpha (0-255)
    private static final int PANEL_BG_ALPHA = 220;
    // Font size limits
    private static final int MIN_FONT_SIZE = 14;
    private static final int MAX_FONT_SIZE = 200;
    // Safety margin (pixels) used when fitting text
    private static final int TEXT_SAFE_MARGIN = 80;
    // Panel padding inside the window
    private static final int PANEL_PADDING = 12;
    // Small window size and margin for bottom-left placement
    private static final int WINDOW_SIDE_WIDTH = 360;
    private static final int WINDOW_HEIGHT = 180;
    private static final int WINDOW_MARGIN = 20;
    private enum Position { BOTTOM, LEFT, RIGHT }
    private static Position currentPosition = Position.BOTTOM;
    private static volatile boolean recreateDisplay = false;

    private static java.util.List<JFrame> frames = new ArrayList<>();
    private static java.util.List<javax.swing.JTextPane> messageLabels = new ArrayList<>();
    private static String lastMessage = DEFAULT_TEXT;
    // Debug flag: if true, ignore hide calls so UI stays visible for inspection
    private static final boolean DEBUG_KEEP_VISIBLE = false;

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
            System.out.println("[Listener " + clientId + "] In ascolto (broadcast UDP)...");

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

                System.out.println("[Listener] UDP packet from " + senderIp + " payload='" + msg + "'");

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
            lastMessage = "Connessione...";

            System.out.println("[Listener] connecting to " + senderIp + ":" + tcpPort + " as " + clientId);

            Client client = new Client(senderIp, tcpPort, clientId);
            client.setMessageListener(message -> onMessageReceived(message));
            client.connectToServer();

            if (!client.isConnected()) {
                lastMessage = "Connessione fallita — riprovo...";
                System.out.println("[Listener] connect failed to " + senderIp + ":" + tcpPort);
                sleep(3000);
                continue;
            }

            System.out.println("[Listener] client connected, waiting for messages...");

            // Non aprire la finestra alla connessione: verrà mostrata
            // solo dopo il primo messaggio ricevuto dal Sender.

            // FASE 3: Resta connesso
            client.waitUntilDisconnected();

            System.out.println("[Listener] client disconnected; keeping display visible until CMD:HIDE. Returning to UDP listen");
            // Do NOT hide the display here; keep the last shown message visible
            lastMessage = "(disconnesso) " + lastMessage;
            sleep(2000);
        }
    }

    /** Crea una finestra fullscreen bianca per ogni monitor disponibile. */
    private static void createDisplay() {
        frames.clear();
        messageLabels.clear();
        System.out.println("[Listener] createDisplay() position=" + currentPosition);

        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice[] gds = ge.getScreenDevices();

        for (int i = 0; i < gds.length; i++) {
            GraphicsDevice gd = gds[i];
            Rectangle bounds = gd.getDefaultConfiguration().getBounds();

            JFrame f = new JFrame("Display");
            f.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
            // use undecorated to allow consistent background handling
            f.setUndecorated(true);

            f.setBackground(new Color(255, 255, 255, PANEL_BG_ALPHA));
            f.setLayout(new BorderLayout());

            int width, height, x, y;
            if (currentPosition == Position.BOTTOM) {
                width = Math.max(200, bounds.width - WINDOW_MARGIN * 2);
                height = Math.min(WINDOW_HEIGHT, Math.max(80, bounds.height / 6));
                x = bounds.x + WINDOW_MARGIN;
                y = bounds.y + bounds.height - height - WINDOW_MARGIN;
            } else if (currentPosition == Position.LEFT) {
                width = Math.min(WINDOW_SIDE_WIDTH, Math.max(180, bounds.width / 4));
                height = Math.max(200, bounds.height - WINDOW_MARGIN * 2);
                x = bounds.x + WINDOW_MARGIN;
                y = bounds.y + WINDOW_MARGIN;
            } else { // RIGHT
                width = Math.min(WINDOW_SIDE_WIDTH, Math.max(180, bounds.width / 4));
                height = Math.max(200, bounds.height - WINDOW_MARGIN * 2);
                x = bounds.x + bounds.width - width - WINDOW_MARGIN;
                y = bounds.y + WINDOW_MARGIN;
            }
            f.setBounds(x, y, width, height);

                int availWidth = Math.max(200, width - PANEL_PADDING * 2 - 40);
                int availHeight = Math.max(80, height - PANEL_PADDING * 2 - 40);
            JTextPane lbl = new JTextPane();
            lbl.setEditable(false);
            // make the text pane opaque so the text is always visible
            lbl.setOpaque(true);
            lbl.setBackground(new Color(255, 255, 255, 220));
            lbl.setFocusable(false);
            lbl.setForeground(new Color(30, 30, 30));
            lbl.setFont(new Font("Arial", Font.BOLD, 20));
            lbl.setBorder(BorderFactory.createEmptyBorder(12, 20, 12, 20));
            // center paragraph alignment
            javax.swing.text.SimpleAttributeSet centerAttr = new javax.swing.text.SimpleAttributeSet();
            javax.swing.text.StyleConstants.setAlignment(centerAttr, javax.swing.text.StyleConstants.ALIGN_CENTER);
            lbl.setParagraphAttributes(centerAttr, false);

            JPanel panel = new JPanel(new BorderLayout());
            // make panel visible with light translucent background and no decorative border
            panel.setOpaque(true);
            panel.setBackground(new Color(255, 255, 255, PANEL_BG_ALPHA));
            panel.setBorder(BorderFactory.createEmptyBorder(PANEL_PADDING, PANEL_PADDING, PANEL_PADDING, PANEL_PADDING));
            panel.add(lbl, BorderLayout.CENTER);

            f.add(panel, BorderLayout.CENTER);
            f.setAlwaysOnTop(true);

            System.out.println("[Listener] created frame for monitor " + i + " at " + x + "," + y + " size=" + width + "x" + height);

            frames.add(f);
            messageLabels.add(lbl);
            fitTextToPane(lbl, lastMessage, availWidth, availHeight);
        }
    }

 
    /** Nasconde e distrugge la finestra display. */
    private static void hideDisplay() {
        // Log caller stack at the call site so we can trace who invoked hideDisplay()
        System.out.println("[Listener] hideDisplay() called on thread " + Thread.currentThread());
        Exception callerTrace = new Exception("hideDisplay() caller trace");
        callerTrace.printStackTrace(System.out);

        Runnable doHide = () -> {
            if (DEBUG_KEEP_VISIBLE) {
                System.out.println("[Listener] hideDisplay() invoked but ignored because DEBUG_KEEP_VISIBLE=true");
                return;
            }
            System.out.println("[Listener] hideDisplay() executing on " + Thread.currentThread());
            for (JFrame f : frames) {
                try {
                    if (f != null) {
                        System.out.println("[Listener] hiding/disposing frame: " + f.getTitle());
                        f.setVisible(false);
                        f.dispose();
                    }
                } catch (Exception ignored) {}
            }
            frames.clear();
            messageLabels.clear();
        };

        if (SwingUtilities.isEventDispatchThread()) {
            // If already on EDT, perform hide immediately to avoid it being queued
            doHide.run();
        } else {
            SwingUtilities.invokeLater(doHide);
        }
    }

    /** Aggiorna il testo sullo schermo (thread-safe). */
    private static void updateDisplay(String text) {
        lastMessage = text;
        SwingUtilities.invokeLater(() -> {
            for (int i = 0; i < messageLabels.size(); i++) {
                JTextPane lbl = messageLabels.get(i);
                JFrame f = (i < frames.size() ? frames.get(i) : null);
                int availW = Math.max(50, (f != null ? f.getWidth() : 800) - 40);
                int availH = Math.max(20, (f != null ? f.getHeight() : 600) - 40);
                if (lbl != null) fitTextToPane(lbl, text, availW, availH);
            }
        });
    }

    /** Chiamato quando arriva un messaggio dal Sender. Mostra la finestra se necessario. */
    private static void onMessageReceived(String text) {
        System.out.println("[Listener] Messaggio ricevuto: " + text);
        if (text == null) return;

        // Parse possible position commands embedded in the message.
        // Supported prefixes (case-insensitive): "POS:", "POSITION:", "CMD:"
        String raw = text;
        String up = text.toUpperCase();

        // handle commands and position prefixes packed in the same line
        if (up.startsWith("CMD:") || up.startsWith("POS:") || up.startsWith("POSITION:")) {
            String body;
            if (up.startsWith("CMD:")) body = text.substring(4).trim();
            else if (up.startsWith("POS:")) body = text.substring(4).trim();
            else body = text.substring(9).trim(); // POSITION:

            // If sender used delimiter '|' (POS:RIGHT|Hello) split into position and message
            String posPart = body;
            String msgPart = "";
            int delim = body.indexOf('|');
            if (delim >= 0) {
                posPart = body.substring(0, delim).trim();
                msgPart = body.substring(delim + 1).trim();
            }

            String upBody = posPart.toUpperCase();
            if (upBody.startsWith("HIDE") || msgPart.equalsIgnoreCase("HIDE")) {
                System.out.println("[Listener] HIDE command received; scheduling hide");
                // schedule hide after a short delay to avoid race with createDisplay
                javax.swing.Timer t = new javax.swing.Timer(500, ev -> hideDisplay());
                t.setRepeats(false);
                t.start();
                return;
            }

            // position keywords
            if (upBody.startsWith("LEFT")) {
                if (currentPosition != Position.LEFT) recreateDisplay = true;
                currentPosition = Position.LEFT;
                raw = msgPart.isEmpty() ? "" : msgPart;
            } else if (upBody.startsWith("RIGHT")) {
                if (currentPosition != Position.RIGHT) recreateDisplay = true;
                currentPosition = Position.RIGHT;
                raw = msgPart.isEmpty() ? "" : msgPart;
            } else if (upBody.startsWith("BOTTOM") || upBody.startsWith("DOWN")) {
                if (currentPosition != Position.BOTTOM) recreateDisplay = true;
                currentPosition = Position.BOTTOM;
                raw = msgPart.isEmpty() ? "" : msgPart;
            } else if (!msgPart.isEmpty()) {
                // not a recognized position but contains a message after delimiter
                raw = msgPart;
            }
        }

        lastMessage = raw;
        SwingUtilities.invokeLater(() -> {
            if (frames.isEmpty() || recreateDisplay) {
                hideDisplay();
                createDisplay();
                recreateDisplay = false;
            }
            for (int i = 0; i < messageLabels.size(); i++) {
                JTextPane lbl = messageLabels.get(i);
                JFrame f = frames.get(i);
                int availW = Math.max(50, (f != null ? f.getWidth() : 800) - PANEL_PADDING * 2 - 40);
                int availH = Math.max(20, (f != null ? f.getHeight() : 600) - PANEL_PADDING * 2 - 40);
                try {
                    Container parent = lbl != null ? lbl.getParent() : null;
                    if (parent != null && parent.getWidth() > 100) {
                        Insets pin = parent.getInsets();
                        Insets lin = lbl.getInsets();
                        availW = Math.max(50, parent.getWidth() - pin.left - pin.right - lin.left - lin.right - 20);
                        availH = Math.max(20, parent.getHeight() - pin.top - pin.bottom - lin.top - lin.bottom - 20);
                    }
                } catch (Exception ignored) {}
                if (lbl != null) fitTextToPane(lbl, lastMessage, availW, availH);
                if (f != null) {
                    System.out.println("[Listener] showing frame: " + f.getTitle() + " (pos=" + currentPosition + ") message='" + lastMessage + "'");
                    f.setVisible(true);
                    f.toFront();
                    f.requestFocus();
                }
            }
        });
    }

        private static String removePrefix(String original, String bodyAfterPrefix) {
            // If original contains the bodyAfterPrefix as a substring, strip the left part up to it
            int idx = original.toUpperCase().indexOf(bodyAfterPrefix.toUpperCase());
            if (idx >= 0) {
                String remaining = original.substring(idx + bodyAfterPrefix.length()).trim();
                if (!remaining.isEmpty()) return remaining;
                // if nothing after, return a short indicator
                return "";
            }
            // fallback: try to remove first line
            int nl = original.indexOf('\n');
            if (nl >= 0 && nl + 1 < original.length()) return original.substring(nl + 1).trim();
            return original;
        }

    /** Resize and wrap text into a JTextPane so it fits within maxWidth/maxHeight.
     *  Uses Swing's layout to compute wrapped height and reduces font size until it fits.
     */
    private static void fitTextToPane(JTextPane pane, String text, int maxWidth, int maxHeight) {
        if (pane == null) return;
        String raw = text == null ? "" : text;

        // Insert zero-width break characters into very long words so they can wrap
        // correctly when measuring. This prevents single long tokens from forcing
        // an oversized font because height checks pass despite horizontal overflow.
        String measureText = insertZeroWidthBreaks(raw, 10);

        int effectiveMaxWidth = Math.max(50, maxWidth);
        int effectiveMaxHeight = Math.max(20, maxHeight);
        try {
            // Use a smaller safety margin for the bottom bar so it can use more vertical
            // space; side panels keep a larger margin to avoid clipping with OS/taskbars.
            int safeMargin = TEXT_SAFE_MARGIN;
            try { if (currentPosition == Position.BOTTOM) safeMargin = 24; } catch (Exception ignored) {}

            Container parent = pane.getParent();
            Insets lin = pane.getInsets();
            if (parent != null && parent.getWidth() > 0 && parent.getHeight() > 0) {
                Insets pin = parent.getInsets();
                effectiveMaxWidth = Math.max(50, parent.getWidth() - pin.left - pin.right - lin.left - lin.right - safeMargin);
                effectiveMaxHeight = Math.max(20, parent.getHeight() - pin.top - pin.bottom - lin.top - lin.bottom - safeMargin);
            } else {
                effectiveMaxWidth = Math.max(50, maxWidth - lin.left - lin.right - safeMargin);
                effectiveMaxHeight = Math.max(20, maxHeight - lin.top - lin.bottom - safeMargin);
            }
        } catch (Exception ignored) {}

        String fontFamily = "Arial";
        int fontStyle = Font.BOLD;

        System.out.println("[Listener] fitTextToPane() text='" + raw + "' maxWidth=" + maxWidth + " maxHeight=" + maxHeight);

        // Try sizes from large to small. Use a position-adjusted allowed height so
        // BOTTOM bars (short height) are allowed more effective height and therefore
        // produce larger fonts, while LEFT/RIGHT use slightly less.
        double allowedScale = 1.0;
        try {
            if (currentPosition == Position.BOTTOM) allowedScale = 1.6; // allow more height for bottom bars
            else allowedScale = 0.9; // slightly reduce for side panels
        } catch (Exception ignored) {}
        int allowedMaxHeight = Math.max(20, (int) (effectiveMaxHeight * allowedScale));

        int startSize = Math.min(MAX_FONT_SIZE, Math.max(MIN_FONT_SIZE, allowedMaxHeight));
        // For side panels, limit font by width to avoid enormous single-line text
        if (currentPosition == Position.LEFT || currentPosition == Position.RIGHT) {
            try {
                // compute longest token length (without zero-width inserts)
                String[] tokens = raw.split("\\s+");
                int longest = 0;
                for (String t : tokens) if (t.length() > longest) longest = t.length();
                if (longest <= 0) longest = 1;
                // estimate average character width relative to font size (~0.55-0.65); use 0.6
                double estCharFactor = 0.6;
                int maxFromWidth = (int) (effectiveMaxWidth / Math.max(1.0, longest * estCharFactor));
                // be conservative
                maxFromWidth = Math.max(MIN_FONT_SIZE, Math.min(maxFromWidth, MAX_FONT_SIZE));
                startSize = Math.min(startSize, maxFromWidth);
            } catch (Exception ignored) {}
        }
        int chosen = MIN_FONT_SIZE;
        for (int size = startSize; size >= MIN_FONT_SIZE; size--) {
            Font f = new Font(fontFamily, fontStyle, size);
            pane.setFont(f);
            pane.setText(measureText);
            // center paragraph
            javax.swing.text.SimpleAttributeSet attr = new javax.swing.text.SimpleAttributeSet();
            javax.swing.text.StyleConstants.setAlignment(attr, javax.swing.text.StyleConstants.ALIGN_CENTER);
            pane.setParagraphAttributes(attr, false);

            // Let Swing compute preferred size for this width
            pane.setSize(effectiveMaxWidth, Integer.MAX_VALUE);
            Dimension pref = pane.getPreferredSize();
            if (pref.height <= allowedMaxHeight) { chosen = size; break; }
        }

        // Apply chosen size and set final text
        pane.setFont(new Font(fontFamily, fontStyle, chosen));
        pane.setText(measureText);
        System.out.println("[Listener] fitTextToPane chosenSize=" + chosen + " effectiveW=" + effectiveMaxWidth + " effectiveH=" + effectiveMaxHeight);
        javax.swing.text.SimpleAttributeSet attr = new javax.swing.text.SimpleAttributeSet();
        javax.swing.text.StyleConstants.setAlignment(attr, javax.swing.text.StyleConstants.ALIGN_CENTER);
        pane.setParagraphAttributes(attr, false);
        pane.setSize(effectiveMaxWidth, allowedMaxHeight);
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

    // Insert zero-width space (U+200B) every `chunk` characters into long runs
    // of non-whitespace so JTextPane can wrap them.
    private static String insertZeroWidthBreaks(String s, int chunk) {
        if (s == null || s.length() <= chunk) return s == null ? "" : s;
        StringBuilder out = new StringBuilder(s.length() + s.length()/chunk + 4);
        int run = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            out.append(c);
            if (Character.isWhitespace(c)) {
                run = 0;
            } else {
                run++;
                if (run >= chunk) {
                    out.append('\u200B');
                    run = 0;
                }
            }
        }
        return out.toString();
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }

}
