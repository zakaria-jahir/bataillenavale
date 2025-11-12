package clientawt;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.Socket;
import javax.swing.*;

/**
 * Client graphique Swing/AWT amélioré.F
 * - Peut être instancié manuellement (UI) ou via MainMenu pour auto-connexion.
 * - Si autoConnect = true : se connecte immédiatement avec le pseudo et le mode fournis.
 *
 * Utilise le même protocole texte que le serveur (pseudo, puis mode, puis commandes "tir x y").
 */
public class BattleshipClientAWT extends JFrame {

    private static final int SIZE = 5;
    private final JButton[][] enemyButtons = new JButton[SIZE][SIZE];
    private final JButton[][] myButtons = new JButton[SIZE][SIZE];

    private JTextArea logArea;
    private JTextField pseudoField;
    private JButton connectBtn, disconnectBtn;
    private JRadioButton modeJvJ, modeJvIA;

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    private Thread listenerThread;
    private volatile boolean myTurn = false;
    private int[] lastShot = null;

    public BattleshipClientAWT() {
        this("", 1, false);
    }

    /**
     * Constructeur principal.
     * @param pseudo pseudo à utiliser (si autoConnect true, ne pas laisser vide)
     * @param mode 1 = JvJ, 2 = JvIA
     * @param autoConnect si true connecte automatiquement (utile lorsque lancé via MainMenu)
     */
    public BattleshipClientAWT(String pseudo, int mode, boolean autoConnect) {
        super("Bataille Navale - Client (" + pseudo + ")");
        setSize(800, 700);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        setLocationByPlatform(true);

        JPanel topPanel = new JPanel();
        topPanel.add(new JLabel("Pseudo:"));
        pseudoField = new JTextField(10);
        pseudoField.setText(pseudo);
        topPanel.add(pseudoField);

        modeJvJ = new JRadioButton("JvJ", mode == 1);
        modeJvIA = new JRadioButton("JvIA", mode == 2);
        ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(modeJvJ);
        modeGroup.add(modeJvIA);
        topPanel.add(modeJvJ);
        topPanel.add(modeJvIA);

        connectBtn = new JButton("Se connecter");
        disconnectBtn = new JButton("Déconnecter");
        disconnectBtn.setEnabled(false);
        topPanel.add(connectBtn);
        topPanel.add(disconnectBtn);

        add(topPanel, BorderLayout.NORTH);

        JPanel gridsPanel = new JPanel(new GridLayout(1, 2, 20, 0));
        gridsPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        gridsPanel.add(createMyGrid());
        gridsPanel.add(createEnemyGrid());
        add(gridsPanel, BorderLayout.CENTER);

        logArea = new JTextArea(8, 60);
        logArea.setEditable(false);
        add(new JScrollPane(logArea), BorderLayout.SOUTH);

        connectBtn.addActionListener(e -> connectAction());
        disconnectBtn.addActionListener(e -> disconnect());

        setVisible(true);

        if (autoConnect) {
            // Délai court pour laisser la fenêtre s'initialiser
            new Thread(() -> {
                try { Thread.sleep(120); } catch (InterruptedException ignored) {}
                SwingUtilities.invokeLater(() -> {
                    connectBtn.setEnabled(false);
                    disconnectBtn.setEnabled(true);
                });
                connect(pseudo, mode);
            }, "AutoConnect-" + pseudo).start();
        }
    }

    private JPanel createEnemyGrid() {
        JPanel grid = new JPanel(new GridLayout(SIZE, SIZE, 5, 5));
        grid.setBorder(BorderFactory.createTitledBorder("Plateau Ennemi (cliquez pour tirer)"));
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                JButton btn = new JButton();
                btn.setBackground(Color.LIGHT_GRAY);
                final int x = i;
                final int y = j;
                btn.addActionListener(e -> shoot(x, y));
                enemyButtons[i][j] = btn;
                grid.add(btn);
            }
        }
        return grid;
    }

    private JPanel createMyGrid() {
        JPanel grid = new JPanel(new GridLayout(SIZE, SIZE, 5, 5));
        grid.setBorder(BorderFactory.createTitledBorder("Votre Plateau"));
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                JButton btn = new JButton();
                btn.setBackground(Color.LIGHT_GRAY);
                btn.setEnabled(false);
                myButtons[i][j] = btn;
                grid.add(btn);
            }
        }
        return grid;
    }

    private void connectAction() {
        String pseudo = pseudoField.getText().trim();
        if (pseudo.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Entrez un pseudo.");
            return;
        }
        int mode = modeJvIA.isSelected() ? 2 : 1;
        connect(pseudo, mode);
        connectBtn.setEnabled(false);
        disconnectBtn.setEnabled(true);
    }

    private void connect(String pseudo, int mode) {
        try {
            // start server locally if not already started
            ServerLauncher.startServerIfNeeded();

            socket = new Socket("localhost", 1234);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            log("Connecté au serveur.");
            // protocole : pseudo + mode
            out.println(pseudo);
            out.println(String.valueOf(mode));

            startListener();

        } catch (IOException e) {
            log("Erreur connexion : " + e.getMessage());
            connectBtn.setEnabled(true);
            disconnectBtn.setEnabled(false);
        }
    }

    private void startListener() {
        listenerThread = new Thread(() -> {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    final String msg = line;
                    SwingUtilities.invokeLater(() -> processServerMessage(msg));
                }
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> log("Connexion perdue : " + e.getMessage()));
            } finally {
                closeResources();
            }
        }, "Listener-" + System.identityHashCode(this));
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    private void shoot(int x, int y) {
        if (out == null) {
            log("Non connecté.");
            return;
        }
        if (!myTurn) {
            log("Ce n'est pas votre tour.");
            return;
        }

        JButton btn = enemyButtons[x][y];
        Color c = btn.getBackground();
        if (c.equals(Color.RED) || c.equals(Color.BLUE) || c.equals(Color.BLACK)) {
            log("Déjà ciblé ici.");
            return;
        }

        out.println("tir " + x + " " + y);
        lastShot = new int[]{x, y};
        log("→ Tir en (" + x + "," + y + ")");
        myTurn = false;
    }

    private void disconnect() {
        try {
            if (out != null) out.println("quit");
            closeResources();
            log("Déconnecté.");
            connectBtn.setEnabled(true);
            disconnectBtn.setEnabled(false);
        } catch (Exception ignored) {}
    }

    private void closeResources() {
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
        socket = null; out = null; in = null;
    }

    private void processServerMessage(String msg) {
        log("[Serveur] " + msg);

        // Tours
        String low = msg.toLowerCase();
        if (low.contains("vous commencez") || low.contains("c'est votre tour") || low.contains("c’est votre tour")) {
            myTurn = true;
        }
        if (low.contains("votre adversaire commence") || low.contains("attente d'un adversaire") || low.contains("en attente")) {
            myTurn = false;
        }
        if (low.contains("ce n'est pas votre tour")) {
            myTurn = false;
        }

        // Réponse à notre tir (heuristique selon texte serveur)
        if (lastShot != null) {
            if (low.contains("coul") || low.contains("gagn")) {
                markEnemyCellSunk(lastShot[0], lastShot[1]);
                lastShot = null;
                return;
            } else if (low.contains("touch") || low.contains("💥") || low.contains("touch")) {
                markEnemyCellHit(lastShot[0], lastShot[1]);
                lastShot = null;
                return;
            } else if (low.contains("manqu") || low.contains("rat") || low.contains("miss")) {
                markEnemyCellMiss(lastShot[0], lastShot[1]);
                lastShot = null;
                return;
            }
        }


        int p1 = msg.indexOf('(');
        int p2 = msg.indexOf(')');
        if (p1 >= 0 && p2 > p1) {
            String inside = msg.substring(p1 + 1, p2).replaceAll("\\s", "");
            String[] parts = inside.split(",");
            if (parts.length == 2) {
                try {
                    int x = Integer.parseInt(parts[0]);
                    int y = Integer.parseInt(parts[1]);
                    if (low.contains("touch") || low.contains("💥")) {
                        markMyCellHit(x, y);
                    } else if (low.contains("coul")) {
                        markMyCellSunk(x, y);
                    } else if (low.contains("manqu") || low.contains("rat")) {
                        markMyCellMiss(x, y);
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        // Fin partie
        if (low.contains("gagn") || low.contains("perd") || low.contains("coul")) {
            myTurn = false;
        }
    }

    /* MARQUAGES VISUELS */
    private void markEnemyCellHit(int x, int y) {
        enemyButtons[x][y].setBackground(Color.RED);
        enemyButtons[x][y].setText("X");
    }

    private void markEnemyCellSunk(int x, int y) {
        enemyButtons[x][y].setBackground(Color.BLACK);
        enemyButtons[x][y].setForeground(Color.WHITE);
        enemyButtons[x][y].setText("S");
    }

    private void markEnemyCellMiss(int x, int y) {
        enemyButtons[x][y].setBackground(Color.CYAN);
        enemyButtons[x][y].setText("o");
    }

    private void markMyCellHit(int x, int y) {
        myButtons[x][y].setBackground(Color.RED);
        myButtons[x][y].setText("X");
    }

    private void markMyCellSunk(int x, int y) {
        myButtons[x][y].setBackground(Color.BLACK);
        myButtons[x][y].setForeground(Color.WHITE);
        myButtons[x][y].setText("S");
    }

    private void markMyCellMiss(int x, int y) {
        myButtons[x][y].setBackground(Color.CYAN);
        myButtons[x][y].setText("o");
    }

    private void log(String text) {
        logArea.append(text + "\n");
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(BattleshipClientAWT::new);
    }
}
