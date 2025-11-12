package clientawt;

import javax.swing.*;
import java.awt.*;

/**
 * Menu de démarrage : permet de saisir deux pseudos et le mode,
 * démarrer le serveur local si besoin et lancer une ou deux fenêtres client.
 */
public class MainMenu extends JFrame {

    private final JTextField pseudo1Field = new JTextField(12);
    private final JTextField pseudo2Field = new JTextField(12);
    private final JRadioButton modeJvJ = new JRadioButton("JvJ", true);
    private final JRadioButton modeJvIA = new JRadioButton("JvIA", false);
    private final JButton startBtn = new JButton("Démarrer la partie");

    public MainMenu() {
        super("Menu - Bataille Navale (Launcher)");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(420, 200);
        setLayout(new BorderLayout());
        setLocationRelativeTo(null);

        JPanel center = new JPanel(new GridLayout(4, 2, 8, 8));
        center.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        center.add(new JLabel("Pseudo Joueur 1 :"));
        center.add(pseudo1Field);

        center.add(new JLabel("Pseudo Joueur 2 :"));
        center.add(pseudo2Field);

        ButtonGroup bg = new ButtonGroup();
        bg.add(modeJvJ);
        bg.add(modeJvIA);

        center.add(new JLabel("Mode :"));
        JPanel modePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        modePanel.add(modeJvJ);
        modePanel.add(modeJvIA);
        center.add(modePanel);

        add(center, BorderLayout.CENTER);

        JPanel bottom = new JPanel();
        bottom.add(startBtn);
        add(bottom, BorderLayout.SOUTH);

        startBtn.addActionListener(e -> onStart());

        setVisible(true);
    }

    private void onStart() {
        String p1 = pseudo1Field.getText().trim();
        String p2 = pseudo2Field.getText().trim();
        if (p1.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Entrez le pseudo du Joueur 1.", "Erreur", JOptionPane.ERROR_MESSAGE);
            return;
        }
        int mode = modeJvIA.isSelected() ? 2 : 1;

        // Start server if not already running
        ServerLauncher.startServerIfNeeded();

        // Launch player 1 window (auto-connect)
        SwingUtilities.invokeLater(() -> new BattleshipClientAWT(p1, mode, true));

        // If JvJ, ensure a second pseudo exists; if empty, use "CPU_local" or a default
        if (mode == 1) {
            String p2final = p2.isEmpty() ? p1 + "_2" : p2;
            // Launch second client window to play on same machine
            SwingUtilities.invokeLater(() -> new BattleshipClientAWT(p2final, 1, true));
        }

        // Optionally hide the menu or keep it (we keep it so user can relaunch)
        // this.setVisible(false);
    }

    public static void main(String[] args) {
        // On lance l'UI du menu
        SwingUtilities.invokeLater(MainMenu::new);
    }
}
