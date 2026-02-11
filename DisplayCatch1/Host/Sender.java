import java.net.*;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.io.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

// Sender: avvia il server TCP e fornisce GUI per inviare messaggi
public class Sender {

    private static final int TCP_PORT = 5555;
    private static final int UDP_PORT = 4445;

    // Colori tema
    private static final Color PRIMARY    = new Color(41, 98, 255);     // Blue
    private static final Color PRIMARY_DARK = new Color(25, 70, 200);
    private static final Color BG_MAIN    = new Color(245, 247, 250);   // Light gray
    private static final Color BG_WHITE   = Color.WHITE;
    private static final Color TEXT_DARK  = new Color(33, 37, 41);
    private static final Color TEXT_LIGHT = new Color(108, 117, 125);
    private static final Color SUCCESS    = new Color(40, 167, 69);
    private static final Color BORDER_COLOR = new Color(222, 226, 230);

    private static Server server;
    private static JTextArea logArea;
    private static DefaultListModel<String> deviceListModel;
    private static JList<String> deviceList;
    private static JLabel statusLabel;
    private static JLabel countLabel;
    private static JTextField messageField;
    private static JButton sendButton;
    private static JButton stopButton;
    // New UI structures: container with groups and checkboxes per screen
    private static java.util.List<String> devices = new ArrayList<>();
    private static java.util.List<String> folders = new ArrayList<>();
    private static java.util.Map<String, java.util.Set<String>> deviceFolders = new HashMap<>(); // baseId -> set of folder names
    private static JComboBox<String> folderFilterCombo;

    public static void main(String[] args) {
        // Look & Feel di sistema
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
        catch (Exception ignored) {}

        // 1. Avvia server TCP
        server = new Server(TCP_PORT);
        server.setConnectionListener(new Server.ConnectionListener() {
            @Override
            public void onClientConnected(String clientId) {
                SwingUtilities.invokeLater(() -> {
                    addDeviceEntry(clientId);
                    updateCount();
                    appendLog("✓ Connesso: " + clientId);
                });
            }
            @Override
            public void onClientDisconnected(String clientId) {
                SwingUtilities.invokeLater(() -> {
                    removeDeviceEntry(clientId);
                    updateCount();
                    appendLog("✗ Disconnesso: " + clientId);
                });
            }
        });
        server.startServer();

        // 2. Broadcast periodico
        Thread broadcastThread = new Thread(() -> {
            String payload = "PORT:" + TCP_PORT;
            while (true) {
                sendBroadcast(payload, UDP_PORT);
                try { Thread.sleep(60000); } catch (InterruptedException e) { break; }
            }
        });
        broadcastThread.setDaemon(true);
        broadcastThread.start();

        // 3. Crea GUI
        // Load saved folders/devices before showing UI
        loadFoldersFromDisk();
        SwingUtilities.invokeLater(() -> createGUI());
    }

    private static void createGUI() {
        JFrame frame = new JFrame("Sender — Trasmetti Messaggi");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(900, 600);
        frame.setMinimumSize(new Dimension(700, 450));
        frame.setLocationRelativeTo(null);
        frame.getContentPane().setBackground(BG_MAIN);
        frame.setLayout(new BorderLayout(0, 0));

        // ===== TOP BAR =====
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(PRIMARY);
        topBar.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));

        JLabel title = new JLabel("📡  Sender — Trasmetti Messaggi");
        title.setFont(new Font("Segoe UI", Font.BOLD, 20));
        title.setForeground(Color.WHITE);

        statusLabel = new JLabel("● Server attivo sulla porta " + TCP_PORT);
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        statusLabel.setForeground(new Color(180, 210, 255));

        JLabel byline = new JLabel("<html><a style=\"color: white; text-decoration: none;\" href=\"https://lapis-xy.github.io/\">made by Lapis-xy</a></html>");
        byline.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        byline.setForeground(Color.WHITE);
        byline.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        byline.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                try { java.awt.Desktop.getDesktop().browse(new java.net.URI("https://lapis-xy.github.io/")); } catch (Exception ex) {}
            }
        });

        JPanel titlePanel = new JPanel();
        titlePanel.setLayout(new BoxLayout(titlePanel, BoxLayout.Y_AXIS));
        titlePanel.setOpaque(false);
        titlePanel.add(title);
        titlePanel.add(Box.createVerticalStrut(4));
        titlePanel.add(statusLabel);

        topBar.add(titlePanel, BorderLayout.WEST);
        topBar.add(byline, BorderLayout.EAST);
        frame.add(topBar, BorderLayout.NORTH);

        // ===== PANNELLO CENTRALE (split: log + dispositivi) =====
        JPanel centerPanel = new JPanel(new BorderLayout(12, 0));
        centerPanel.setBackground(BG_MAIN);
        centerPanel.setBorder(BorderFactory.createEmptyBorder(12, 15, 0, 15));

        // --- Log area ---
        JPanel logPanel = createCard("Registro Messaggi");
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 13));
        logArea.setForeground(TEXT_DARK);
        logArea.setBackground(BG_WHITE);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        logArea.setMargin(new Insets(8, 10, 8, 10));

        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
        logScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        logPanel.add(logScroll, BorderLayout.CENTER);

        // --- Dispositivi connessi ---
        JPanel devicePanel = createCard("Dispositivi Connessi");
        devicePanel.setPreferredSize(new Dimension(220, 0));

        countLabel = new JLabel("  0 schermi connessi");
        countLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        countLabel.setForeground(TEXT_LIGHT);
        countLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        devicePanel.add(countLabel, BorderLayout.NORTH);

        // Device list (stylish JList)
        deviceListModel = new DefaultListModel<>();
        deviceList = new JList<>(deviceListModel);
        deviceList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        deviceList.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        deviceList.setFixedCellHeight(36);
        deviceList.setBackground(BG_WHITE);
        deviceList.setSelectionBackground(new Color(232, 240, 254));
        deviceList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(
                        list, value, index, isSelected, cellHasFocus);
                label.setText("  🖥  " + value);
                label.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COLOR));
                return label;
            }
        });

        JScrollPane deviceScroll = new JScrollPane(deviceList);
        deviceScroll.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
        deviceScroll.getVerticalScrollBar().setUnitIncrement(16);
        devicePanel.add(deviceScroll, BorderLayout.CENTER);

        // Folder controls (create/filter) and selection helpers
        JPanel folderBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        folderBar.setOpaque(false);
        // Ensure default folder exists only once
        if (!folders.contains("Tutti")) folders.add("Tutti");
        folderFilterCombo = new JComboBox<>(folders.toArray(new String[0]));
        folderFilterCombo.setPreferredSize(new Dimension(120, 26));
        folderFilterCombo.addActionListener(e -> updateDeviceListModel());
        JButton newFolderBtn = new JButton("+");
        newFolderBtn.setToolTipText("Crea nuova cartella");
        newFolderBtn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        newFolderBtn.setPreferredSize(new Dimension(28, 24));
        // Style: white background with green + and light hover
        newFolderBtn.setBackground(BG_WHITE);
        newFolderBtn.setForeground(SUCCESS);
        newFolderBtn.setFocusPainted(false);
        newFolderBtn.setBorder(BorderFactory.createLineBorder(BORDER_COLOR, 1, true));
        newFolderBtn.setOpaque(true);
        newFolderBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        newFolderBtn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { newFolderBtn.setBackground(new Color(250, 250, 250)); }
            @Override public void mouseExited(MouseEvent e)  { newFolderBtn.setBackground(BG_WHITE); }
        });
        newFolderBtn.addActionListener(e -> createNewFolder());
        JButton selectAllBtn = new JButton("Seleziona tutti");
        selectAllBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        selectAllBtn.addActionListener(e -> {
            if (deviceListModel.getSize() > 0) deviceList.setSelectionInterval(0, deviceListModel.getSize() - 1);
        });
        JButton deselectAllBtn = new JButton("Deseleziona tutti");
        deselectAllBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        deselectAllBtn.addActionListener(e -> deviceList.clearSelection());
        folderBar.add(folderFilterCombo);
        folderBar.add(newFolderBtn);
        folderBar.add(selectAllBtn);
        folderBar.add(deselectAllBtn);
        devicePanel.add(folderBar, BorderLayout.NORTH);

        // When host selected, update action buttons
        deviceList.addListSelectionListener(e -> SwingUtilities.invokeLater(() -> updateActionButtons()));

        // Mouse listener: double-click to send to that device; right-click to show folder menu
        deviceList.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent evt) {
                if (evt.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(evt)) {
                    int idx = deviceList.locationToIndex(evt.getPoint());
                    if (idx >= 0) {
                        String base = deviceListModel.get(idx);
                        String text = messageField.getText().trim();
                        if (text.isEmpty() || text.equals("Scrivi un messaggio...")) {
                            appendLog("Nessun messaggio da inviare a " + base);
                        } else {
                            server.inviaMsgA(base, text);
                            appendLog("➤ [A " + base + "] " + text);
                            messageField.setText("");
                        }
                    }
                }
            }

            @Override public void mousePressed(MouseEvent e) { maybeShowPopup(e); }
            @Override public void mouseReleased(MouseEvent e) { maybeShowPopup(e); }
            private void maybeShowPopup(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    int idx = deviceList.locationToIndex(e.getPoint());
                    if (idx >= 0) {
                        String base = deviceListModel.get(idx);
                        JPopupMenu m = new JPopupMenu();
                        JMenu move = new JMenu("Sposta in cartella");
                            for (String f : folders) {
                                JMenuItem it = new JMenuItem(f);
                                it.addActionListener(ae -> {
                                    java.util.Set<String> set = deviceFolders.get(base);
                                    if (set == null) set = new LinkedHashSet<>();
                                    if (set.contains(f)) set.remove(f); else set.add(f);
                                    if (set.isEmpty()) set.add("Tutti");
                                    deviceFolders.put(base, set);
                                    updateDeviceListModel();
                                    saveFoldersToDisk();
                                });
                                move.add(it);
                            }
                        m.add(move);
                        m.show(deviceList, e.getX(), e.getY());
                    }
                }
            }
        });

        // Interaction handling moved to checkbox UI inside device groups.
        // The device groups provide per-screen checkboxes and right-click menus.

        centerPanel.add(logPanel, BorderLayout.CENTER);
        centerPanel.add(devicePanel, BorderLayout.EAST);
        frame.add(centerPanel, BorderLayout.CENTER);

        // ===== BOTTOM: INPUT BAR =====
        JPanel bottomPanel = new JPanel(new BorderLayout(10, 0));
        bottomPanel.setBackground(BG_WHITE);
        bottomPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_COLOR),
            BorderFactory.createEmptyBorder(12, 15, 12, 15)
        ));

        messageField = new JTextField();
        messageField.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        messageField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COLOR, 1, true),
            BorderFactory.createEmptyBorder(10, 14, 10, 14)
        ));
        messageField.putClientProperty("JTextField.placeholderText", "Scrivi un messaggio...");

        // Placeholder manuale (compatibile con tutti i L&F)
        messageField.setForeground(TEXT_LIGHT);
        messageField.setText("Scrivi un messaggio...");
        messageField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                if (messageField.getText().equals("Scrivi un messaggio...")) {
                    messageField.setText("");
                    messageField.setForeground(TEXT_DARK);
                }
            }
            @Override
            public void focusLost(FocusEvent e) {
                if (messageField.getText().isEmpty()) {
                    messageField.setForeground(TEXT_LIGHT);
                    messageField.setText("Scrivi un messaggio...");
                }
            }
        });

        sendButton = new JButton("  Invia a Tutti  ");
        sendButton.setFont(new Font("Segoe UI", Font.BOLD, 14));
        sendButton.setBackground(PRIMARY);
        sendButton.setForeground(Color.WHITE);
        sendButton.setFocusPainted(false);
        sendButton.setBorderPainted(false);
        sendButton.setOpaque(true);
        sendButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        sendButton.setPreferredSize(new Dimension(160, 44));

        // Hover effect
        sendButton.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { sendButton.setBackground(PRIMARY_DARK); }
            @Override public void mouseExited(MouseEvent e)  { sendButton.setBackground(PRIMARY); }
        });

        // Azione invio
        ActionListener sendAction = e -> sendMessage();
        sendButton.addActionListener(sendAction);
        messageField.addActionListener(sendAction);
        // Update action buttons initially and when selection changes
        deviceList.addListSelectionListener(e -> SwingUtilities.invokeLater(() -> updateActionButtons()));

        // Bottone per terminare la trasmissione (nascondere il messaggio sui client)
        stopButton = new JButton("Termina Trasmissione");
        stopButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
        // Make fully red with white text for readability
        stopButton.setBackground(new Color(200, 35, 51));
        stopButton.setForeground(Color.WHITE);
        stopButton.setFocusPainted(false);
        stopButton.setBorderPainted(false);
        stopButton.setOpaque(true);
        stopButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        stopButton.setEnabled(false);
        stopButton.setPreferredSize(new Dimension(160, 36));
        stopButton.addActionListener(e -> stopTransmission());

        JPanel rightControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightControls.setOpaque(false);
        rightControls.add(stopButton);
        rightControls.add(sendButton);

        bottomPanel.add(messageField, BorderLayout.CENTER);
        bottomPanel.add(rightControls, BorderLayout.EAST);
        frame.add(bottomPanel, BorderLayout.SOUTH);

        // Messaggio di benvenuto nel log
        appendLog("Server avviato sulla porta " + TCP_PORT);
        appendLog("Broadcast UDP attivo sulla porta " + UDP_PORT);
        appendLog("In attesa di dispositivi...");

        frame.setVisible(true);
        messageField.requestFocusInWindow();
    }

    /** Crea un pannello "card" con titolo e bordo arrotondato. */
    private static JPanel createCard(String title) {
        JPanel card = new JPanel(new BorderLayout(0, 6));
        card.setBackground(BG_WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COLOR, 1, true),
            BorderFactory.createEmptyBorder(12, 12, 12, 12)
        ));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        titleLabel.setForeground(TEXT_DARK);
        titleLabel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COLOR));

        // Il titolo è già nella card, il contenuto va aggiunto dal chiamante
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(titleLabel, BorderLayout.WEST);
        header.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.add(header, BorderLayout.NORTH);
        card.add(wrapper, BorderLayout.NORTH);

        return card;
    }

    /** Invia il messaggio a tutti gli schermi. */
    private static void sendMessage() {
        String text = messageField.getText().trim();
        if (text.isEmpty() || text.equals("Scrivi un messaggio...")) return;
        // If hosts are selected in the list, send to them; otherwise broadcast to all
        java.util.List<String> sel = deviceList.getSelectedValuesList();
        if (sel != null && !sel.isEmpty()) {
            for (String id : sel) server.inviaMsgA(id, text);
            appendLog("➤ [A selezionati (" + sel.size() + ")] " + text);
        } else {
            int count = server.getClientCount();
            server.inviaMsg(text);
            appendLog("➤ [A tutti (" + count + " dispositivi)] " + text);
        }

        messageField.setText("");
        messageField.setForeground(TEXT_DARK);
        messageField.requestFocusInWindow();
    }

    /** Aggiunge una riga al log con timestamp. */
    private static void appendLog(String text) {
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        SwingUtilities.invokeLater(() -> {
            logArea.append("[" + time + "] " + text + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    /** Invia il comando di nascondere il messaggio ai selezionati o a tutti. */
    private static void stopTransmission() {
        java.util.List<String> sel = deviceList.getSelectedValuesList();
        if (sel != null && !sel.isEmpty()) {
            for (String id : sel) server.inviaMsgA(id, "CMD:HIDE");
            appendLog("✖ [Termina trasmissione su " + sel.size() + " dispositivi]");
        } else {
            server.inviaMsg("CMD:HIDE");
            appendLog("✖ [Termina trasmissione su tutti i dispositivi]");
        }
    }

    /** Aggiorna il contatore dei dispositivi. */
    private static void updateCount() {
        int devicesCount = deviceListModel == null ? 0 : deviceListModel.getSize();
        countLabel.setText("  " + devicesCount + " dispositivi connessi");
        countLabel.setForeground(devicesCount > 0 ? SUCCESS : TEXT_LIGHT);
        // Aggiorna stato bottone invio: disabilita se non ci sono dispositivi
        if (sendButton != null) {
            sendButton.setEnabled(devicesCount > 0);
            if (devicesCount == 0) sendButton.setText("  Invia a Tutti  ");
        }
    }

    // Adds a device entry; clientId can be "host" or "host#screenIndex"
    private static void addDeviceEntry(String clientId) {
        if (clientId == null || clientId.isBlank()) return;
        String base = clientId;
        String screen = null;
        if (clientId.contains("#")) {
            int i = clientId.indexOf('#');
            base = clientId.substring(0, i);
            screen = clientId.substring(i + 1);
        }
        // only track base device
        if (deviceListModel != null && !deviceListModel.contains(base)) deviceListModel.addElement(base);
        if (!devices.contains(base)) devices.add(base);
        // Default folder
        deviceFolders.putIfAbsent(base, new LinkedHashSet<>(Arrays.asList("Tutti")));
        updateDeviceListModel();
        updateCount();
    }

    private static void removeDeviceEntry(String clientId) {
        if (clientId == null || clientId.isBlank()) return;
        String base = clientId;
        String screen = null;
        if (clientId.contains("#")) {
            int i = clientId.indexOf('#');
            base = clientId.substring(0, i);
            screen = clientId.substring(i + 1);
        }
        // Remove the base device (ignore per-screen)
        if (deviceListModel != null && deviceListModel.contains(base)) deviceListModel.removeElement(base);
        deviceFolders.remove(base);
        devices.remove(base);
        updateDeviceListModel();
        saveFoldersToDisk();
        updateCount();
    }

    private static void createNewFolder() {
        String name = JOptionPane.showInputDialog(null, "Nome cartella:", "Crea cartella", JOptionPane.PLAIN_MESSAGE);
        if (name == null) return;
        name = name.trim();
        if (name.isEmpty()) return;
        if (!folders.contains(name)) {
            folders.add(name);
            folderFilterCombo.addItem(name);
            saveFoldersToDisk();
        }
    }

    private static void refreshFolderFilter() {
        updateDeviceListModel();
    }

    private static void updateDeviceListModel() {
        if (deviceListModel == null) return;
        String sel = (String) (folderFilterCombo != null ? folderFilterCombo.getSelectedItem() : "Tutti");
        if (sel == null) sel = "Tutti";
        // preserve selection
        String selVal = deviceList.getSelectedValue();
        deviceListModel.clear();
        for (String base : devices) {
            java.util.Set<String> set = deviceFolders.getOrDefault(base, new LinkedHashSet<>(Arrays.asList("Tutti")));
            if (sel.equals("Tutti") || set.contains(sel)) deviceListModel.addElement(base);
        }
        if (selVal != null && deviceListModel.contains(selVal)) deviceList.setSelectedValue(selVal, true);
        deviceList.revalidate(); deviceList.repaint();
    }

    // Persistence: save/load folders and device assignments as JSON in %APPDATA%/DisplayCatch/sender/config.json
    private static File getConfigFile() {
        String appdata = System.getenv("APPDATA");
        File dir;
        if (appdata != null && !appdata.isBlank()) dir = new File(appdata, "DisplayCatch\\sender");
        else dir = new File(System.getProperty("user.home"), ".DisplayCatch/sender");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, "config.json");
    }

    private static void saveFoldersToDisk() {
        try {
            File f = getConfigFile();
            try (FileWriter w = new FileWriter(f)) {
                StringBuilder sb = new StringBuilder();
                sb.append("{\n");
                // folders
                sb.append("  \"folders\": [");
                for (int i = 0; i < folders.size(); i++) {
                    sb.append('"').append(escapeJson(folders.get(i))).append('"');
                    if (i + 1 < folders.size()) sb.append(',');
                }
                sb.append("],\n");
                // deviceFolder map (device -> array of folders)
                sb.append("  \"deviceFolder\": {");
                int count = 0;
                for (Map.Entry<String, java.util.Set<String>> e : deviceFolders.entrySet()) {
                    if (count++ > 0) sb.append(',');
                    sb.append('\n').append("    \"").append(escapeJson(e.getKey())).append("\": [");
                    int j = 0;
                    for (String fv : e.getValue()) {
                        if (j++ > 0) sb.append(',');
                        sb.append('"').append(escapeJson(fv)).append('"');
                    }
                    sb.append(']');
                }
                sb.append('\n').append("  }\n");
                sb.append("}\n");
                w.write(sb.toString());
            }
        } catch (IOException ex) {
            appendLog("Errore salvataggio config: " + ex.getMessage());
        }
    }

    private static void loadFoldersFromDisk() {
        try {
            File f = getConfigFile();
            if (!f.exists()) return;
            StringBuilder content = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new FileReader(f))) {
                String line;
                while ((line = r.readLine()) != null) { content.append(line).append('\n'); }
            }
            String s = content.toString();
            // crude parse: extract folders array and deviceFolder object
            int fi = s.indexOf("\"folders\"");
            if (fi >= 0) {
                int b = s.indexOf('[', fi);
                int e = s.indexOf(']', b);
                if (b >= 0 && e >= 0) {
                    String arr = s.substring(b + 1, e).trim();
                    folders.clear();
                    if (!arr.isEmpty()) {
                        String[] items = arr.split(",");
                        for (String it : items) {
                            String t = it.trim();
                            if (t.startsWith("\"") && t.endsWith("\"")) t = t.substring(1, t.length() - 1);
                            folders.add(unescapeJson(t));
                        }
                    }
                }
            }
            int di = s.indexOf("\"deviceFolder\"");
            if (di >= 0) {
                // Use regex to find "deviceId": ["f1","f2"] entries
                java.util.regex.Pattern p = java.util.regex.Pattern.compile("\\\"([^\\\"]+)\\\"\\s*:\\s*\\[(.*?)\\]", java.util.regex.Pattern.DOTALL);
                java.util.regex.Matcher m = p.matcher(s);
                deviceFolders.clear();
                while (m.find()) {
                    String key = unescapeJson(m.group(1));
                    String inside = m.group(2);
                    java.util.Set<String> set = new LinkedHashSet<>();
                    String[] parts = inside.split(",");
                    for (String part : parts) {
                        String t = part.trim();
                        if (t.startsWith("\"") && t.endsWith("\"")) t = t.substring(1, t.length() - 1);
                        if (!t.isEmpty()) set.add(unescapeJson(t));
                    }
                    if (set.isEmpty()) set.add("Tutti");
                    deviceFolders.put(key, set);
                }
            }
        } catch (IOException ex) {
            appendLog("Errore caricamento config: " + ex.getMessage());
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private static String unescapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static void updateActionButtons() {
        java.util.List<String> selectedHosts = deviceList.getSelectedValuesList();
        int hostCount = selectedHosts == null ? 0 : selectedHosts.size();

        if (hostCount == 0) {
            sendButton.setText("  Invia a Tutti  ");
            boolean hasClients = server != null && server.getClientCount() > 0;
            sendButton.setEnabled(hasClients);
            stopButton.setEnabled(hasClients);
        } else {
            sendButton.setText(hostCount == 1 ? "  Invia al selezionato  " : "  Invia ai selezionati  ");
            sendButton.setEnabled(true);
            stopButton.setEnabled(true);
        }
    }

    // DeviceGroup stores UI for a host and its screen checkboxes
    // DeviceGroup and per-screen UI removed: selection is by host only now.

    /** Invia broadcast UDP. */
    public static void sendBroadcast(String message, int udpPort) {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            byte[] buf = message.getBytes(StandardCharsets.UTF_8);
            InetAddress addr = InetAddress.getByName("255.255.255.255");
            DatagramPacket packet = new DatagramPacket(buf, buf.length, addr, udpPort);
            socket.send(packet);
        } catch (IOException e) {
            System.err.println("[Sender] Errore broadcast: " + e.getMessage());
        }
    }
}
