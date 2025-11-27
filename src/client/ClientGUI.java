package client;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;

/**
 * ClientGUI Swing pour la Bataille Navale.
 *
 * Usage:
 * - Met à jour SERVER_HOST si nécessaire (IP du serveur).
 * - Lance Server.java sur la machine serveur.
 * - Lance ce ClientGUI sur chaque machine cliente.
 *
 * Comportement:
 * - envoie le pseudo immédiatement après connexion,
 * - attend le prompt "Tapez 1 ou 2" du serveur pour envoyer le mode sélectionné,
 * - clic sur grille ennemie -> envoie "tir x y" (si c'est votre tour),
 * - met à jour l'UI en fonction des messages retournés par le serveur.
 */
public class ClientGUI extends JFrame {

    // ======= CONFIGURATION =======
    private static final String SERVER_HOST = "172.20.10.3"; // ← change si besoin
    private static final int SERVER_PORT = 1234;

    // ======= UI =======
    private final JButton[][] gridMe = new JButton[4][4];
    private final JButton[][] gridEnemy = new JButton[4][4];

    private final JTextArea messages = new JTextArea();
    private final JTextField inputField = new JTextField();
    private final JTextField pseudoField = new JTextField("zack", 10);

    private final JRadioButton modeJvJ = new JRadioButton("Joueur vs Joueur");
    private final JRadioButton modeIA  = new JRadioButton("Joueur vs Serveur (IA)");

    // ======= Réseau =======
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    // ======= État =======
    private volatile boolean myTurn = false;
    private volatile int[] lastShot = null; // {x,y} du dernier tir envoyé (pour associer résultat)

    public ClientGUI() {
        super("Bataille Navale - Client GUI");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(600, 400);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(8, 8));

        // TOP - connexion + mode
        JPanel top = new JPanel();
        top.add(new JLabel("Pseudo:"));
        top.add(pseudoField);

        JButton connectBtn = new JButton("Se connecter");
        top.add(connectBtn);

        ButtonGroup bg = new ButtonGroup();
        bg.add(modeJvJ);
        bg.add(modeIA);
        modeIA.setSelected(true);
        top.add(modeJvJ);
        top.add(modeIA);

        add(top, BorderLayout.NORTH);

        // CENTER - deux grilles
        JPanel center = new JPanel(new GridLayout(1, 2, 10, 10));
        center.add(createMyGridPanel());
        center.add(createEnemyGridPanel());
        add(center, BorderLayout.CENTER);

        // BOTTOM - messages + input
        messages.setEditable(false);
        JScrollPane scroll = new JScrollPane(messages);
        scroll.setPreferredSize(new Dimension(100, 180));

        inputField.addActionListener(e -> {
            String txt = inputField.getText().trim();
            inputField.setText("");
            if (txt.isEmpty() || out == null) return;
            out.println(txt);
            log("→ Vous: " + txt);
        });

        JPanel bottom = new JPanel(new BorderLayout(6, 6));
        bottom.add(scroll, BorderLayout.CENTER);
        bottom.add(inputField, BorderLayout.SOUTH);
        add(bottom, BorderLayout.SOUTH);

        // Actions
        connectBtn.addActionListener(e -> {
            connectBtn.setEnabled(false);
            connectToServer();
        });

        // Désactiver grille ennemie au départ
        setEnemyGridEnabled(false);

        setVisible(true);
    }

    // === UI helpers ===
    private JPanel createMyGridPanel() {
        JPanel panel = new JPanel(new GridLayout(4, 4, 2, 2));
        panel.setBorder(BorderFactory.createTitledBorder("Votre plateau (invisible)"));
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                JButton b = new JButton();
                b.setBackground(Color.LIGHT_GRAY);
                b.setEnabled(false); // on n'affiche pas les bateaux ; bouton non clickable
                gridMe[i][j] = b;
                panel.add(b);
            }
        }
        return panel;
    }

    private JPanel createEnemyGridPanel() {
        JPanel panel = new JPanel(new GridLayout(4, 4, 2, 2));
        panel.setBorder(BorderFactory.createTitledBorder("Grille ennemie (cliquez pour tirer)"));
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                final int x = i, y = j;
                JButton b = new JButton();
                b.setBackground(Color.WHITE);
                b.addActionListener(e -> {
                    onEnemyCellClicked(x, y);
                });
                gridEnemy[i][j] = b;
                panel.add(b);
            }
        }
        return panel;
    }

    private void setEnemyGridEnabled(boolean enabled) {
        for (int i = 0; i < 4; i++)
            for (int j = 0; j < 4; j++)
                gridEnemy[i][j].setEnabled(enabled);
    }

    private void onEnemyCellClicked(int x, int y) {
        if (out == null) {
            log("⚠ Non connecté au serveur.");
            return;
        }
        if (!myTurn) {
            log("⚠ Ce n'est pas votre tour.");
            return;
        }
        // Empêcher de tirer deux fois sur la même case visuellement (simple)
        Color bg = gridEnemy[x][y].getBackground();
        if (bg.equals(Color.RED) || bg.equals(Color.BLACK) || bg.equals(Color.CYAN) || bg.equals(Color.ORANGE)) {
            log("⚠ Case déjà ciblée.");
            return;
        }

        // Envoyer le tir au serveur
        lastShot = new int[] { x, y };
        out.println("tir " + x + " " + y);
        log("→ Tir envoyé en (" + x + "," + y + ")");
        // Désactiver clics jusqu'à réponse
        setEnemyGridEnabled(false);
    }

    private void connectToServer() {
        String pseudo = pseudoField.getText().trim();
        if (pseudo.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Entrez un pseudo.", "Erreur", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            socketOpenAndStartListener();
            // envoyer juste le pseudo ; le mode sera envoyé lorsque le serveur demandera "Tapez 1 ou 2"
            out.println(pseudo);
            log("Connexion établie, pseudo envoyé : " + pseudo);
        } catch (Exception e) {
            log("❌ Erreur connexion : " + e.getMessage());
            JOptionPane.showMessageDialog(this, "Impossible de se connecter : " + e.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
            // permettre re-tenter
            // (le bouton Se connecter était désactivé par l'appel; permet le relancer)
            // find the connect button and re-enable: iterate components (simple approach: enable all)
            setAllEnabled(true);
        }
    }

    private void setAllEnabled(boolean enable) {
        // rough: enable input field and radio buttons (not a perfect UI control)
        pseudoField.setEnabled(enable);
        modeIA.setEnabled(enable);
        modeJvJ.setEnabled(enable);
        inputField.setEnabled(enable);
    }

    // Ouvre socket et démarre thread d'écoute
    private void socketOpenAndStartListener() throws IOException {
        socket = new Socket(SERVER_HOST, SERVER_PORT);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(socket.getOutputStream(), true);

        Thread listener = new Thread(this::socketListenerLoop, "Listener-Thread");
        listener.setDaemon(true);
        listener.start();
    }

    // Boucle d'écoute réseau (fonctionne en thread de fond)
    private void socketListenerLoop() {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                final String msg = line;
                SwingUtilities.invokeLater(() -> handleServerMessage(msg));
            }
        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> {
                log("❌ Connexion perdue : " + e.getMessage());
                // désactiver grille
                setEnemyGridEnabled(false);
            });
        } finally {
            try { if (socket != null) socket.close(); } catch (IOException ignored) {}
            socket = null;
            in = null;
            out = null;
        }
    }

    // Traitement des messages venant du serveur
    private void handleServerMessage(String msg) {
        log("[Serveur] " + msg);

        String lower = msg.toLowerCase();

        // Le serveur demande le mode : on envoie 1 ou 2 maintenant
        if (msg.contains("Tapez 1") || msg.contains("Tapez 1 ou 2")) {
            if (modeJvJ.isSelected()) {
                out.println("1");
                log("Mode envoyé: JvJ (1)");
            } else {
                out.println("2");
                log("Mode envoyé: IA (2)");
            }
            return;
        }

        // Indicateurs de tour
        if (lower.contains("vous commencez") || lower.contains("c'est votre tour") || lower.contains("à vous de jouer") || lower.contains("vous commencez !")) {
            myTurn = true;
            setEnemyGridEnabled(true);
            log("→ C'est votre tour.");
        }
        if (lower.contains("votre adversaire commence") || lower.contains("en attente d'un adversaire") || lower.contains("votre adversaire commence.")) {
            myTurn = false;
            setEnemyGridEnabled(false);
            log("→ Attente du tour adverse...");
        }
        if (lower.contains("ce n'est pas votre tour")) {
            myTurn = false;
            setEnemyGridEnabled(false);
            log("→ Ce n'est pas votre tour.");
        }

        // Si le serveur inclut des coordonnées "(x,y)" dans le message, on peut marquer la case correspondante.
        // Ex: "Votre bateau a été touché en (2,3)" ou "Tir ennemi en (2,3) raté !"
        int p1 = msg.indexOf('(');
        int p2 = msg.indexOf(')');
        if (p1 >= 0 && p2 > p1) {
            String inside = msg.substring(p1 + 1, p2).replaceAll("\\s", "");
            String[] parts = inside.split(",");
            if (parts.length == 2) {
                try {
                    int cx = Integer.parseInt(parts[0]);
                    int cy = Integer.parseInt(parts[1]);

                    // Déterminer si c'est un tir ennemi en notre plateau ou information sur notre tir.
                    if (lower.contains("l'adversaire") || lower.contains("tir ennemi") || lower.contains("ia")) {
                        // C'est un tir ennemi sur nous
                        if (lower.contains("manqu") || lower.contains("a manqu") || lower.contains("miss")) {
                            markMyCellMiss(cx, cy);
                        } else if (lower.contains("touch") || lower.contains("vous a touch")) {
                            markMyCellHit(cx, cy);
                        } else if (lower.contains("coul") || lower.contains("coulé") || lower.contains("cou")) {
                            markMyCellSunk(cx, cy);
                        } else {
                            // si inconnu, on marque neutralement
                            markMyCellMiss(cx, cy);
                        }
                    } else {
                        // Probablement info relative à notre tir (rare)
                        if (lower.contains("manqu") || lower.contains("miss") || lower.contains("rat")) {
                            markEnemyCellMiss(cx, cy);
                        } else if (lower.contains("touch") || lower.contains("touché")) {
                            markEnemyCellHit(cx, cy);
                        } else if (lower.contains("coul") || lower.contains("gagn")) {
                            markEnemyCellSunk(cx, cy);
                        }
                    }
                    return; // déjà traité
                } catch (NumberFormatException ignored) {}
            }
        }

        // Si la réponse contient un résultat pour "Votre tir" sans coords, on associe au lastShot
        // exemples de messages: "→ Vous tirez : 💥 Touché", "→ Vous tirez : 💦 Manqué", "→ Vous tirez : 🔥 Coulé"
        if (msg.contains("→ Vous tirez") || msg.toLowerCase().contains("vous tirez")) {
            if (lastShot != null) {
                int x = lastShot[0], y = lastShot[1];
                if (lower.contains("manqu") || lower.contains("miss") || lower.contains("rat")) {
                    markEnemyCellMiss(x, y);
                } else if (lower.contains("touch") || lower.contains("touché")) {
                    markEnemyCellHit(x, y);
                } else if (lower.contains("coul") || lower.contains("coulé") || lower.contains("gagn")) {
                    markEnemyCellSunk(x, y);
                } else {
                    // fallback
                    markEnemyCellMiss(x, y);
                }
                lastShot = null;
                // after our shot, normally it's opponent turn until server says otherwise
                myTurn = false;
                setEnemyGridEnabled(false);
                return;
            }
        }

        // Si message de l'IA p.ex "🤖 IA : vous a touché" -> on peut traiter en heuristique
        if (msg.contains("🤖") || msg.toLowerCase().contains("ia")) {
            // si l'IA annonce quelque chose qui ressemble à hit/miss, on log et on laisse le serveur gérer tours
            if (lower.contains("manqu") || lower.contains("miss") || lower.contains("rat")) {
                log("IA: manqué");
            } else if (lower.contains("touch") || lower.contains("touché")) {
                log("IA: touché");
            } else if (lower.contains("coul") || lower.contains("coulé")) {
                log("IA: coulé");
            }
            return;
        }

        // Fin de partie - si serveur annonce gagnant/perdant
        if (lower.contains("gagn") || lower.contains("perd") || lower.contains("vous avez gagné") || lower.contains("vous avez perdu")) {
            myTurn = false;
            setEnemyGridEnabled(false);
            log("Partie terminée.");
        }
    }

    // ======= Marquages visuels =======
    private void markEnemyCellHit(int x, int y) {
        JButton b = gridEnemy[x][y];
        b.setBackground(Color.RED);
        b.setText("X");
    }

    private void markEnemyCellSunk(int x, int y) {
        JButton b = gridEnemy[x][y];
        b.setBackground(Color.BLACK);
        b.setForeground(Color.WHITE);
        b.setText("S");
    }

    private void markEnemyCellMiss(int x, int y) {
        JButton b = gridEnemy[x][y];
        b.setBackground(Color.CYAN);
        b.setText("o");
    }

    private void markMyCellHit(int x, int y) {
        JButton b = gridMe[x][y];
        b.setBackground(Color.RED);
        b.setText("X");
    }

    private void markMyCellSunk(int x, int y) {
        JButton b = gridMe[x][y];
        b.setBackground(Color.BLACK);
        b.setForeground(Color.WHITE);
        b.setText("S");
    }

    private void markMyCellMiss(int x, int y) {
        JButton b = gridMe[x][y];
        b.setBackground(Color.CYAN);
        b.setText("o");
    }

    // ======= util =======
    private void log(String s) {
        messages.append(s + "\n");
        // auto-scroll
        messages.setCaretPosition(messages.getDocument().getLength());
    }

    // ======= main =======
    public static void main(String[] args) {
        SwingUtilities.invokeLater(ClientGUI::new);
    }
}
