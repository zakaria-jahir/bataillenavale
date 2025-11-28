package client;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ClientGUI - version "pro" (no external libs)
 *
 * Compatible with server text-protocol:
 *  - Server -> Client:
 *     MSG|text
 *     ASKMODE
 *     TURN|YOU  or TURN|OPP
 *     RESULT|HIT|x|y
 *     RESULT|MISS|x|y
 *     RESULT|SUNK|x|y
 *     OPPONENT_FIRE|HIT|x|y
 *     OPPONENT_FIRE|MISS|x|y
 *     END|WIN | END|LOSE | END|ABANDON
 *     ERROR|message
 *     OPPONENT_LEFT|message
 *     CHAT|from|text
 *
 *  - Client -> Server (plain text lines)
 *     pseudo is sent as first line after connection
 *     mode selection: "1" or "2" in response to ASKMODE
 *     SHOT syntax expected by server: "SHOT|x|y" (this GUI sends that)
 *     QUIT by sending "QUIT"
 *     CHAT by sending "CHAT|text"
 *
 * How to compile:
 *   javac -d out src/client/ClientGUI.java
 * Run:
 *   java -cp out client.ClientGUI
 *
 * Adjust default SERVER_HOST and SERVER_PORT in UI if needed.
 */
public class ClientGUI extends JFrame {

    // ======== Default connection values (editable in UI) ========
    private String defaultHost = "172.20.10.3";
    private int defaultPort = 1234;

    // ======== Grid config (must match server SIZE) ========
    private static final int GRID_SIZE = 4; // keep 4 for TP

    // ======== UI components ========
    private final JTextField hostField = new JTextField(defaultHost, 12);
    private final JTextField portField = new JTextField(String.valueOf(defaultPort), 6);
    private final JTextField pseudoField = new JTextField("player", 10);
    private final JComboBox<String> themeBox = new JComboBox<>(new String[]{"Light", "Dark"});
    private final JButton connectBtn = new JButton("Connect");
    private final JButton disconnectBtn = new JButton("Disconnect");
    private final JButton quitBtn = new JButton("Quit Game");

    private final JButton[][] myGridButtons = new JButton[GRID_SIZE][GRID_SIZE];
    private final JButton[][] enemyGridButtons = new JButton[GRID_SIZE][GRID_SIZE];

    private final JLabel statusLabel = new JLabel("Disconnected");
    private final JLabel turnLabel = new JLabel("Turn: -");
    private final JLabel shipsLabel = new JLabel("Ships left: -");
    private final JLabel timerLabel = new JLabel("Timer: -");

    private final JTextArea logArea = new JTextArea();
    private final DefaultListModel<String> chatModel = new DefaultListModel<>();
    private final JList<String> chatList = new JList<>(chatModel);
    private final JTextField chatInput = new JTextField();

    // settings
    private final JSpinner timerSpinner = new JSpinner(new SpinnerNumberModel(20, 5, 120, 1));
    private final JComboBox<String> modeCombo = new JComboBox<>(new String[]{"JvJ (1)", "IA (2)"});

    // ======== Networking ========
    private volatile Socket socket;
    private volatile BufferedReader in;
    private volatile PrintWriter out;
    private Thread listenerThread;

    // state
    private volatile boolean myTurn = false;
    private volatile boolean connected = false;
    private volatile boolean inGame = false;
    private volatile AtomicInteger shipsLeft = new AtomicInteger(2); // server uses 2 ships (length 2 each)
    private final AtomicBoolean waitingModeAsk = new AtomicBoolean(false);

    // timer
    private java.util.Timer turnTimer;
    private volatile int turnSecondsLeft = 0;

    // utility
    private final Color COLOR_BG = Color.decode("#f4f7fb");
    private final Color COLOR_PANEL = Color.decode("#ffffff");
    private final Color COLOR_PRIMARY = Color.decode("#2b6df6");
    private final Color COLOR_HIT = new Color(0xE53935);
    private final Color COLOR_MISS = new Color(0x29B6F6);
    private final Color COLOR_SUNK = new Color(0x212121);
    private final Color COLOR_MYSHIP = new Color(0x9E9E9E);

    public ClientGUI() {
        super("Bataille Navale - Client GUI (Pro)");
        initUI();
        attachHandlers();
        applyTheme("Light");
        pack();
        setMinimumSize(new Dimension(900, 650));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
    }

    // ----------------- UI setup -----------------
    private void initUI() {
        setLayout(new BorderLayout(8, 8));

        // Top: connection panel and settings
        JPanel top = new JPanel(new BorderLayout(8, 8));
        top.setBorder(new EmptyBorder(8, 8, 0, 8));
        add(top, BorderLayout.NORTH);

        JPanel connPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        connPanel.setBorder(new TitledBorder("Connection"));

        connPanel.add(new JLabel("Host:"));
        connPanel.add(hostField);
        connPanel.add(new JLabel("Port:"));
        connPanel.add(portField);

        connPanel.add(new JLabel("Pseudo:"));
        connPanel.add(pseudoField);

        connPanel.add(new JLabel("Mode:"));
        connPanel.add(modeCombo);

        connPanel.add(new JLabel("Turn timer(s):"));
        connPanel.add(timerSpinner);

        connPanel.add(new JLabel("Theme:"));
        connPanel.add(themeBox);

        connPanel.add(connectBtn);
        connPanel.add(disconnectBtn);
        disconnectBtn.setEnabled(false);

        top.add(connPanel, BorderLayout.CENTER);

        // Info panel
        JPanel info = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        info.setBorder(new TitledBorder("Status"));
        statusLabel.setPreferredSize(new Dimension(220, 20));
        turnLabel.setPreferredSize(new Dimension(120, 20));
        shipsLabel.setPreferredSize(new Dimension(120, 20));
        timerLabel.setPreferredSize(new Dimension(120, 20));

        info.add(statusLabel);
        info.add(turnLabel);
        info.add(shipsLabel);
        info.add(timerLabel);
        top.add(info, BorderLayout.EAST);

        // Center: grids and chat/log
        JPanel center = new JPanel(new GridBagLayout());
        add(center, BorderLayout.CENTER);

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.gridx = 0; c.gridy = 0; c.weightx = 0.7; c.weighty = 1.0; c.fill = GridBagConstraints.BOTH;
        center.add(buildGamePanel(), c);

        c.gridx = 1; c.gridy = 0; c.weightx = 0.3;
        center.add(buildRightPanel(), c);

        // Bottom: quit button
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.setBorder(new EmptyBorder(0, 8, 8, 8));
        quitBtn.setEnabled(false);
        bottom.add(quitBtn);
        add(bottom, BorderLayout.SOUTH);
    }

    private JPanel buildGamePanel() {
        JPanel p = new JPanel(new BorderLayout(10, 10));
        p.setBorder(new TitledBorder("Plateaux"));

        JPanel grids = new JPanel(new GridLayout(1,2, 12, 12));
        grids.add(buildMyGridPanel());
        grids.add(buildEnemyGridPanel());

        p.add(grids, BorderLayout.CENTER);
        return p;
    }

    private JPanel buildMyGridPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder("Votre plateau"));

        JPanel grid = new JPanel(new GridLayout(GRID_SIZE, GRID_SIZE, 3, 3));
        for (int i = 0; i < GRID_SIZE; i++) {
            for (int j = 0; j < GRID_SIZE; j++) {
                JButton b = new JButton();
                b.setEnabled(false); // player's own grid is not clickable in this UI (we show ships)
                b.setBackground(Color.LIGHT_GRAY);
                myGridButtons[i][j] = b;
                grid.add(b);
            }
        }
        panel.add(grid, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildEnemyGridPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder("Grille ennemie (cliquez pour tirer)"));

        JPanel grid = new JPanel(new GridLayout(GRID_SIZE, GRID_SIZE, 3, 3));
        for (int i = 0; i < GRID_SIZE; i++) {
            for (int j = 0; j < GRID_SIZE; j++) {
                final int x = i, y = j;
                JButton b = new JButton();
                b.setBackground(Color.WHITE);
                b.addActionListener(e -> onEnemyCellClicked(x, y));
                enemyGridButtons[i][j] = b;
                grid.add(b);
            }
        }
        panel.add(grid, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildRightPanel() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(new TitledBorder("Communication"));

        // Chat & log split
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        split.setResizeWeight(0.6);

        // chat panel
        JPanel chatPanel = new JPanel(new BorderLayout(4, 4));
        chatPanel.setBorder(new TitledBorder("Chat"));

        chatList.setVisibleRowCount(8);
        chatPanel.add(new JScrollPane(chatList), BorderLayout.CENTER);

        JPanel chatInputPanel = new JPanel(new BorderLayout(4,4));
        chatInputPanel.add(chatInput, BorderLayout.CENTER);
        JButton sendChat = new JButton("Send");
        chatInputPanel.add(sendChat, BorderLayout.EAST);
        chatPanel.add(chatInputPanel, BorderLayout.SOUTH);

        sendChat.addActionListener(e -> sendChatMessage());
        chatInput.addActionListener(e -> sendChatMessage());

        // log panel
        JPanel logPanel = new JPanel(new BorderLayout());
        logPanel.setBorder(new TitledBorder("Journal / Debug"));

        logArea.setEditable(false);
        JScrollPane logScroll = new JScrollPane(logArea);
        logPanel.add(logScroll, BorderLayout.CENTER);

        split.setTopComponent(chatPanel);
        split.setBottomComponent(logPanel);

        panel.add(split, BorderLayout.CENTER);

        // controls: quick actions
        JPanel controls = new JPanel(new GridLayout(4,1,6,6));
        JButton clearLog = new JButton("Clear Log");
        controls.add(clearLog);
        clearLog.addActionListener(e -> logArea.setText(""));

        JButton showHelp = new JButton("Aide protocole");
        controls.add(showHelp);
        showHelp.addActionListener(e -> showProtocolHelp());

        JButton enableShips = new JButton("Afficher mes bateaux");
        controls.add(enableShips);
        enableShips.addActionListener(e -> revealMyShips());

        controls.add(new JLabel(" "));

        panel.add(controls, BorderLayout.SOUTH);
        return panel;
    }

    // ----------------- Handlers & network -----------------
    private void attachHandlers() {
        connectBtn.addActionListener(e -> connectToServer());
        disconnectBtn.addActionListener(e -> disconnectFromServer());
        quitBtn.addActionListener(e -> {
            if (out != null) {
                out.println("QUIT");
            }
            setInGame(false);
        });

        themeBox.addActionListener(e -> applyTheme((String) themeBox.getSelectedItem()));
    }

    private void connectToServer() {
        if (connected) return;
        String host = hostField.getText().trim();
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Port invalide", "Erreur", JOptionPane.ERROR_MESSAGE);
            return;
        }
        final String pseudo = pseudoField.getText().trim();
        if (pseudo.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Entrez un pseudo", "Erreur", JOptionPane.ERROR_MESSAGE);
            return;
        }

        connectBtn.setEnabled(false);
        new Thread(() -> {
            try {
                socket = new Socket(host, port);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);
                connected = true;
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Connected to " + host + ":" + port);
                    connectBtn.setEnabled(false);
                    disconnectBtn.setEnabled(true);
                });

                // send pseudo as first line (server expects)
                out.println(pseudo);
                log("Sent pseudo: " + pseudo);

                // start listener
                listenerThread = new Thread(this::listenLoop, "ListenerThread");
                listenerThread.setDaemon(true);
                listenerThread.start();

            } catch (IOException ex) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this, "Connection failed: " + ex.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
                    connectBtn.setEnabled(true);
                    statusLabel.setText("Disconnected");
                });
                log("Connection error: " + ex.getMessage());
            }
        }).start();
    }

    private void disconnectFromServer() {
        if (!connected) return;
        try {
            if (out != null) out.println("QUIT");
            if (socket != null) socket.close();
        } catch (IOException ignored) {}
        connected = false;
        setInGame(false);
        connectBtn.setEnabled(true);
        disconnectBtn.setEnabled(false);
        statusLabel.setText("Disconnected");
        log("Disconnected.");
    }

    private void listenLoop() {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                final String l = line;
                SwingUtilities.invokeLater(() -> handleServerLine(l));
            }
        } catch (IOException e) {
            log("Connection lost: " + e.getMessage());
            SwingUtilities.invokeLater(() -> {
                statusLabel.setText("Disconnected");
                connectBtn.setEnabled(true);
                disconnectBtn.setEnabled(false);
                setInGame(false);
            });
        } finally {
            try { if (socket != null && !socket.isClosed()) socket.close(); } catch (IOException ignored) {}
            connected = false;
        }
    }

    // ----------------- Protocol handling -----------------
    private void handleServerLine(String line) {
        log("[SERVER] " + line);
        // Messages are KEY|... ; split into parts
        String[] parts = line.split("\\|", 4);
        String key = parts[0];

        switch (key) {
            case "MSG":
                if (parts.length >= 2) {
                    appendChat("SERVER: " + parts[1]);
                }
                // if server asks mode with ASKMODE token separately, some server sends "ASKMODE" line
                break;
            case "ASKMODE":
                // server requests mode selection; send our chosen mode
                String sel = modeCombo.getSelectedIndex() == 0 ? "1" : "2";
                out.println(sel);
                log("Sent MODE: " + sel);
                waitingModeAsk.set(false);
                break;
            case "TURN":
                if (parts.length >= 2) {
                    boolean you = parts[1].equalsIgnoreCase("YOU");
                    setMyTurn(you);
                }
                break;
            case "RESULT":
                // RESULT|HIT|x|y  OR RESULT|MISS|x|y OR RESULT|SUNK|x|y
                if (parts.length >= 4) {
                    String res = parts[1];
                    int x = safeParse(parts[2]), y = safeParse(parts[3]);
                    handleShotResult(res, x, y);
                }
                break;
            case "OPPONENT_FIRE":
                if (parts.length >= 4) {
                    String res = parts[1];
                    int x = safeParse(parts[2]), y = safeParse(parts[3]);
                    handleOpponentFire(res, x, y);
                }
                break;
            case "END":
                if (parts.length >= 2) {
                    String res = parts[1];
                    handleGameEnd(res);
                }
                break;
            case "ERROR":
                if (parts.length >= 2) {
                    JOptionPane.showMessageDialog(this, "Server error: " + parts[1], "Erreur", JOptionPane.ERROR_MESSAGE);
                }
                break;
            case "OPPONENT_LEFT":
                appendChat("[SYSTEM] Adversaire déconnecté.");
                setInGame(false);
                break;
            case "CHAT":
                // CHAT|from|text
                if (parts.length >= 3) {
                    String from = parts[1];
                    String text = parts.length >= 4 ? parts[2] : "";
                    appendChat(from + ": " + text);
                }
                break;
            default:
                // unknown raw message
                appendChat("RAW: " + line);
        }
    }

    // --------------- Game-state helpers ----------------
    private void setInGame(boolean v) {
        inGame = v;
        quitBtn.setEnabled(v);
        if (!v) {
            setMyTurn(false);
            shipsLeft.set(2);
            updateShipsLabel();
            resetGrids();
        }
    }

    private void setMyTurn(boolean t) {
        myTurn = t;
        turnLabel.setText("Turn: " + (t ? "YOU" : "OPP"));
        setEnemyGridEnabled(t);
        if (t) startTurnTimer((Integer) timerSpinner.getValue());
        else stopTurnTimer();
        if (t) appendChat("[SYSTEM] C'est votre tour !");
    }

    private void updateShipsLabel() {
        shipsLabel.setText("Ships left: " + shipsLeft.get());
    }

    private void resetGrids() {
        for (int i = 0; i < GRID_SIZE; i++)
            for (int j = 0; j < GRID_SIZE; j++) {
                myGridButtons[i][j].setBackground(Color.LIGHT_GRAY);
                myGridButtons[i][j].setText("");
                enemyGridButtons[i][j].setBackground(Color.WHITE);
                enemyGridButtons[i][j].setText("");
                enemyGridButtons[i][j].setEnabled(false);
            }
    }

    private void revealMyShips() {
        // request not available on server side — we simulate local random placement display
        // for usability we reveal random positions similar to server initial placement result
        // NOTE: This is purely a visual helper — server actually controls game state
        for (int i = 0; i < GRID_SIZE; i++)
            for (int j = 0; j < GRID_SIZE; j++) {
                if (Math.random() < 0.12) { // random small hint
                    myGridButtons[i][j].setBackground(COLOR_MYSHIP);
                }
            }
    }

    private void setEnemyGridEnabled(boolean en) {
        for (int i=0;i<GRID_SIZE;i++) for (int j=0;j<GRID_SIZE;j++) enemyGridButtons[i][j].setEnabled(en);
    }

    private void handleShotResult(String res, int x, int y) {
        setInGame(true); // when we get results, we are in a game
        switch (res.toUpperCase()) {
            case "MISS":
                flashButton(enemyGridButtons[x][y], COLOR_MISS);
                enemyGridButtons[x][y].setText("o");
                break;
            case "HIT":
                flashButton(enemyGridButtons[x][y], COLOR_HIT);
                enemyGridButtons[x][y].setText("X");
                break;
            case "SUNK":
                flashButton(enemyGridButtons[x][y], COLOR_SUNK);
                enemyGridButtons[x][y].setText("S");
                // reduce ships count heuristic: show sunk -> -1
                shipsLeft.getAndUpdate(prev -> Math.max(0, prev-1));
                updateShipsLabel();
                break;
            case "ALREADY":
                JOptionPane.showMessageDialog(this, "Case déjà jouée", "Info", JOptionPane.INFORMATION_MESSAGE);
                break;
            default:
                appendChat("[RESULT] " + res + " (" + x + "," + y + ")");
        }
    }

    private void handleOpponentFire(String res, int x, int y) {
        setInGame(true);
        switch (res.toUpperCase()) {
            case "MISS":
                flashButton(myGridButtons[x][y], COLOR_MISS);
                myGridButtons[x][y].setText("o");
                break;
            case "HIT":
                flashButton(myGridButtons[x][y], COLOR_HIT);
                myGridButtons[x][y].setText("X");
                break;
            case "SUNK":
                flashButton(myGridButtons[x][y], COLOR_SUNK);
                myGridButtons[x][y].setText("S");
                // client lost a ship -> decrement
                shipsLeft.getAndUpdate(prev -> Math.max(0, prev-1));
                updateShipsLabel();
                break;
            default:
                appendChat("[OPP_FIRE] " + res + " (" + x + "," + y + ")");
        }
    }

    private void handleGameEnd(String code) {
        setInGame(false);
        stopTurnTimer();
        switch (code.toUpperCase()) {
            case "WIN":
                JOptionPane.showMessageDialog(this, "🏆 Vous avez gagné !", "Game Over", JOptionPane.INFORMATION_MESSAGE);
                appendChat("[SYSTEM] Vous avez gagné !");
                break;
            case "LOSE":
                JOptionPane.showMessageDialog(this, "💀 Vous avez perdu.", "Game Over", JOptionPane.INFORMATION_MESSAGE);
                appendChat("[SYSTEM] Vous avez perdu.");
                break;
            case "ABANDON":
            default:
                appendChat("[SYSTEM] Partie terminée: " + code);
                break;
        }
    }

    // --------------- user actions ---------------
    private void onEnemyCellClicked(int x, int y) {
        if (!connected || !myTurn) {
            appendChat("[SYSTEM] Ce n'est pas votre tour ou pas connecté.");
            return;
        }
        // protect against double-click visually
        Color bg = enemyGridButtons[x][y].getBackground();
        if (bg.equals(COLOR_HIT) || bg.equals(COLOR_MISS) || bg.equals(COLOR_SUNK) || !enemyGridButtons[x][y].isEnabled()) {
            appendChat("[SYSTEM] Case déjà ciblée.");
            return;
        }
        // send shot
        if (out != null) {
            out.println("SHOT|" + x + "|" + y);
            appendChat("[YOU] Tir en (" + x + "," + y + ")");
            // disable further clicks while awaiting result
            setEnemyGridEnabled(false);
        }
    }

    private void sendChatMessage() {
        if (!connected) {
            appendChat("[SYSTEM] Non connecté.");
            return;
        }
        String text = chatInput.getText().trim();
        if (text.isEmpty()) return;
        // we send "CHAT|text" for server
        out.println("CHAT|" + text);
        appendChat("[YOU] " + text);
        chatInput.setText("");
    }

    // --------------- visuals & utils ---------------
    private void flashButton(JButton b, Color color) {
        Color before = b.getBackground();
        b.setBackground(color);
        Timer t = new Timer(350, e -> b.setBackground(before));
        t.setRepeats(false);
        t.start();
    }

    private void appendChat(String s) {
        chatModel.addElement(s);
        // auto scroll
        SwingUtilities.invokeLater(() -> {
            int size = chatModel.size();
            if (size > 0) chatList.ensureIndexIsVisible(size - 1);
        });
        log(s);
    }

    private void log(String s) {
        logArea.append(s + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private int safeParse(String s) {
        try { return Integer.parseInt(s.trim()); }
        catch (Exception ex) { return -1; }
    }

    private void showProtocolHelp() {
        String help = "Protocole pris en charge (exemples) :\n"
                + "  ASKMODE\n"
                + "  MSG|Bienvenue\n"
                + "  TURN|YOU\n"
                + "  RESULT|HIT|x|y\n"
                + "  OPPONENT_FIRE|MISS|x|y\n"
                + "  END|WIN\n"
                + "Client -> Server (GUI envoie) :\n"
                + "  pseudo (première ligne après connexion)\n"
                + "  1 (pour JvJ) ou 2 (pour IA) en réponse à ASKMODE\n"
                + "  SHOT|x|y pour tirer\n"
                + "  CHAT|message pour chat\n"
                + "  QUIT pour quitter\n";
        JOptionPane.showMessageDialog(this, help, "Protocole", JOptionPane.INFORMATION_MESSAGE);
    }

    // ----------------- timer management -----------------
    private void startTurnTimer(int seconds) {
        stopTurnTimer();
        turnSecondsLeft = seconds;
        timerLabel.setText("Timer: " + turnSecondsLeft + "s");
        turnTimer = new java.util.Timer("TurnTimer", true);
        turnTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                turnSecondsLeft--;
                SwingUtilities.invokeLater(() -> timerLabel.setText("Timer: " + turnSecondsLeft + "s"));
                if (turnSecondsLeft <= 0) {
                    // time over: auto-pass (we send "PASS" or just disable turn)
                    SwingUtilities.invokeLater(() -> {
                        appendChat("[SYSTEM] Temps écoulé !");
                        setMyTurn(false);
                    });
                    if (out != null) out.println("TIMEOUT");
                    stopTurnTimer();
                }
            }
        }, 1000L, 1000L);
    }

    private void stopTurnTimer() {
        if (turnTimer != null) {
            turnTimer.cancel();
            turnTimer = null;
        }
        timerLabel.setText("Timer: -");
    }

    // ----------------- theme -----------------
    private void applyTheme(String theme) {
        boolean dark = "Dark".equalsIgnoreCase(theme);
        SwingUtilities.invokeLater(() -> {
            if (dark) {
                getContentPane().setBackground(Color.decode("#2b2b2b"));
                logArea.setBackground(Color.decode("#1e1e1e"));
                logArea.setForeground(Color.white);
                chatList.setBackground(Color.decode("#1e1e1e"));
                chatList.setForeground(Color.white);
            } else {
                getContentPane().setBackground(COLOR_BG);
                logArea.setBackground(Color.white);
                logArea.setForeground(Color.black);
                chatList.setBackground(Color.white);
                chatList.setForeground(Color.black);
            }
            // force repaint
            repaint();
        });
    }

    // ----------------- main -----------------
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ClientGUI gui = new ClientGUI();
            gui.setVisible(true);
        });
    }
}
