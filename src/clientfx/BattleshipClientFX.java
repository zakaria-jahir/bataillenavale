package clientfx;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.util.Optional;

/**
 * Client JavaFX pour la bataille navale.
 * - 2 grilles 5x5 (ennemi + joueur) en boutons
 * - Clic sur une case ennemie envoie "tir x y" au serveur
 * - Les cases restent neutres tant qu'elles ne sont pas touchées / manquées
 * - Les messages serveur sont affichés dans la zone log
 *
 * NB : adapte SERVER_HOST / SERVER_PORT si besoin.
 */
public class BattleshipClientFX extends Application {

    private static final int SIZE = 5; // doit correspondre à la taille du serveur
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 1234;

    private final Button[][] enemyButtons = new Button[SIZE][SIZE];
    private final Button[][] myButtons = new Button[SIZE][SIZE];

    private final TextArea logArea = new TextArea();
    private final Label statusLabel = new Label("Non connecté");

    private NetworkClient netClient;

    // état local
    private volatile boolean myTurn = false;
    private volatile int[] lastShot = null; // {x,y} du dernier tir envoyé

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Bataille Navale - Client JavaFX");

        // Top: connection / pseudo / mode
        HBox topBox = new HBox(10);
        topBox.setPadding(new Insets(8));

        TextField pseudoField = new TextField();
        pseudoField.setPromptText("Pseudo");
        pseudoField.setPrefWidth(150);

        ToggleGroup modeGroup = new ToggleGroup();
        RadioButton rbPvP = new RadioButton("JvJ");
        rbPvP.setToggleGroup(modeGroup);
        rbPvP.setSelected(true);
        RadioButton rbPvIA = new RadioButton("JvIA");
        rbPvIA.setToggleGroup(modeGroup);

        Button connectBtn = new Button("Se connecter");
        Button disconnectBtn = new Button("Déconnecter");
        disconnectBtn.setDisable(true);

        topBox.getChildren().addAll(new Label("Pseudo:"), pseudoField, new Label("Mode:"), rbPvP, rbPvIA,
                connectBtn, disconnectBtn, new Separator(), statusLabel);

        // Center: grids
        GridPane myGrid = createGrid(myButtons, false);       // ta grille (tu ne vois pas tes bateaux)
        GridPane enemyGrid = createGrid(enemyButtons, true);  // grille ennemie (clic pour tirer)

        VBox leftBox = new VBox(8, new Label("Votre plateau"), myGrid);
        VBox rightBox = new VBox(8, new Label("Plateau ennemi (cliquez pour tirer)"), enemyGrid);

        HBox centerBox = new HBox(16, leftBox, rightBox);
        centerBox.setPadding(new Insets(8));

        // Bottom: log + actions
        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.setPrefHeight(180);

        Button quitBtn = new Button("Quitter");
        quitBtn.setOnAction(e -> {
            disconnect();
            Platform.exit();
        });

        VBox bottomBox = new VBox(8, new Label("Messages serveur / journal"), new ScrollPane(logArea), quitBtn);
        bottomBox.setPadding(new Insets(8));

        BorderPane root = new BorderPane();
        root.setTop(topBox);
        root.setCenter(centerBox);
        root.setBottom(bottomBox);

        Scene scene = new Scene(root, 850, 700);
        primaryStage.setScene(scene);
        primaryStage.show();

        // Actions connect / disconnect
        connectBtn.setOnAction(e -> {
            String pseudo = pseudoField.getText().trim();
            if (pseudo.isEmpty()) {
                showAlert("Pseudo requis", "Veuillez entrer un pseudo.");
                return;
            }
            int mode = rbPvIA.isSelected() ? 2 : 1;
            connect(pseudo, mode);
            connectBtn.setDisable(true);
            disconnectBtn.setDisable(false);
        });

        disconnectBtn.setOnAction(e -> {
            disconnect();
            connectBtn.setDisable(false);
            disconnectBtn.setDisable(true);
        });

        // initial styles
        initButtonStyles();
    }

    private void initButtonStyles() {
        // style par défaut pour boutons neutres
        String neutral = "-fx-background-color: #d3d3d3; -fx-border-color: #999;";
        for (int i = 0; i < SIZE; i++)
            for (int j = 0; j < SIZE; j++) {
                myButtons[i][j].setStyle(neutral);
                enemyButtons[i][j].setStyle(neutral);
            }
    }

    private GridPane createGrid(Button[][] buttons, boolean clickableEnemy) {
        GridPane grid = new GridPane();
        grid.setHgap(4);
        grid.setVgap(4);
        grid.setPadding(new Insets(6));
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                Button b = new Button(" ");
                b.setPrefSize(60, 60);
                final int x = i;
                final int y = j;
                if (clickableEnemy) {
                    b.setOnAction(e -> {
                        onEnemyCellClicked(x, y);
                    });
                } else {
                    // ta grille (non clickable)
                    b.setDisable(true);
                }
                grid.add(b, j, i); // col = y, row = x
                buttons[i][j] = b;
            }
        }
        return grid;
    }

    private void onEnemyCellClicked(int x, int y) {
        if (netClient == null || !netClient.isConnected()) {
            addLog("Non connecté au serveur.");
            return;
        }
        if (!myTurn) {
            addLog("Ce n'est pas votre tour.");
            return;
        }
        // Empêcher de tirer plusieurs fois sur la même case (si déjà marqué)
        String style = enemyButtons[x][y].getStyle();
        if (style.contains("#ff4d4d") || style.contains("#000000") || style.contains("#87cefa")) {
            addLog("Case déjà ciblée.");
            return;
        }

        // envoie du tir
        String cmd = String.format("tir %d %d", x, y);
        lastShot = new int[]{x, y};
        netClient.send(cmd);
        addLog("→ Vous tirez en (" + x + "," + y + ") ...");
        // Désactiver l'envoi tant que la réponse serveur n'est pas arrivée
        setButtonsDisabled(true);
    }

    private void setButtonsDisabled(boolean disabled) {
        // n'autorise pas le clic sur la grille ennemie si disabled
        for (int i = 0; i < SIZE; i++)
            for (int j = 0; j < SIZE; j++)
                enemyButtons[i][j].setDisable(disabled);
    }

    private void connect(String pseudo, int mode) {
        try {
            netClient = new NetworkClient(SERVER_HOST, SERVER_PORT, message -> {
                // callback pour traiter les messages serveur
                Platform.runLater(() -> processServerMessage(message));
            });
            netClient.connect();

            // start listener thread
            netClient.startListening();

            // envoyer pseudo et mode selon protocole texte actuel
            netClient.send(pseudo);
            netClient.send(String.valueOf(mode));
            addLog("Connecté en tant que '" + pseudo + "' (mode " + (mode==1?"JvJ":"JvIA") + ")");
            statusLabel.setText("Connecté en tant que " + pseudo);

        } catch (Exception e) {
            addLog("Erreur connexion : " + e.getMessage());
            statusLabel.setText("Erreur connexion");
            showAlert("Erreur", "Impossible de se connecter : " + e.getMessage());
        }
    }

    private void disconnect() {
        if (netClient != null) {
            netClient.send("quit");
            netClient.close();
            netClient = null;
            addLog("Déconnecté.");
            statusLabel.setText("Non connecté");
            initButtonStyles();
            setButtonsDisabled(true);
        }
    }

    private void processServerMessage(String msg) {
        if (msg == null) return;
        addLog("[Serveur] " + msg);

        // gestion basique des tours
        if (msg.toLowerCase().contains("vous commencez") || msg.toLowerCase().contains("c'est votre tour") || msg.toLowerCase().contains("c’est votre tour")) {
            myTurn = true;
            setButtonsDisabled(false);
            statusLabel.setText("À vous de jouer");
        }
        if (msg.toLowerCase().contains("votre adversaire commence") || msg.toLowerCase().contains("votre adversaire commence.")) {
            myTurn = false;
            setButtonsDisabled(true);
            statusLabel.setText("Attente du tour adverse");
        }
        if (msg.toLowerCase().contains("ce n'est pas votre tour")) {
            myTurn = false;
            setButtonsDisabled(true);
            statusLabel.setText("Attente du tour adverse");
        }

        // Réponse à notre tir : le serveur envoie souvent "Touché !" ou "Manqué !"
        if ((msg.contains("Touch") || msg.contains("Touché") || msg.contains("Touche") || msg.contains("Touche!"))
                && lastShot != null) {
            // marque case ennemie comme touchée (rouge ou noir)
            int x = lastShot[0], y = lastShot[1];
            // Si serveur indique victoire ou coulé, on marque en noir.
            if (msg.toLowerCase().contains("coul") || msg.toLowerCase().contains("gagn")) {
                markEnemyCellSunk(x, y);
            } else {
                markEnemyCellHit(x, y);
            }
            lastShot = null;
            myTurn = false; // on passe le tour
            setButtonsDisabled(true);
            return;
        }

        if ((msg.contains("Manqué") || msg.contains("manqué") || msg.contains("Manque") || msg.contains("Raté") || msg.contains("raté")) && lastShot != null) {
            int x = lastShot[0], y = lastShot[1];
            markEnemyCellMiss(x, y);
            lastShot = null;
            myTurn = false;
            setButtonsDisabled(true);
            return;
        }

        // Si le serveur nous annonce un tir ennemi (notre bateau touché ou raté), parse coords
        // Exemples de messages serveur (vu précédemment):
        // "💥 Votre bateau a été touché en (x, y)"
        // "💦 Tir ennemi en (x, y) raté !"
        // "🤖 Le serveur a tiré en (x, y) → Touché"
        // Nous cherchons un pattern "(x, y)" dans le message.
        Optional<int[]> coord = extractCoord(msg);
        if (coord.isPresent()) {
            int[] c = coord.get();
            int cx = c[0], cy = c[1];
            // Déterminer si c'était un hit ou miss selon message content
            if (msg.toLowerCase().contains("touch") || msg.toLowerCase().contains("💥") || msg.toLowerCase().contains("Touché") || msg.toLowerCase().contains("touché")) {
                // notre bateau touché : on affiche rouge (ou noir si coulé)
                if (msg.toLowerCase().contains("coul") || msg.toLowerCase().contains("coule")) {
                    markMyCellSunk(cx, cy);
                } else {
                    markMyCellHit(cx, cy);
                }
            } else if (msg.toLowerCase().contains("rat") || msg.toLowerCase().contains("manqu") || msg.toLowerCase().contains("miss")) {
                markMyCellMiss(cx, cy);
            }
        }

        // Si partie finie
        if (msg.toLowerCase().contains("gagn") || msg.toLowerCase().contains("perd") || msg.toLowerCase().contains("coul")) {
            // garde le log, désactive les actions
            myTurn = false;
            setButtonsDisabled(true);
            statusLabel.setText("Partie terminée");
        }
    }

    private Optional<int[]> extractCoord(String msg) {
        // Cherche "(x, y)" ou "(x,y)"
        int p1 = msg.indexOf('(');
        int p2 = msg.indexOf(')');
        if (p1 >= 0 && p2 > p1) {
            String inside = msg.substring(p1 + 1, p2);
            inside = inside.replaceAll("\\s", "");
            String[] parts = inside.split(",");
            if (parts.length == 2) {
                try {
                    int x = Integer.parseInt(parts[0]);
                    int y = Integer.parseInt(parts[1]);
                    if (x >= 0 && x < SIZE && y >= 0 && y < SIZE) return Optional.of(new int[]{x, y});
                } catch (NumberFormatException ignored) {}
            }
        }
        return Optional.empty();
    }

    /* MARQUAGES VISUELS (couleurs) :
       - neutre : gris
       - miss : bleu clair
       - hit partiel : rouge
       - sunk : noir
    */
    private void markEnemyCellHit(int x, int y) {
        enemyButtons[x][y].setStyle("-fx-background-color: #ff4d4d; -fx-border-color: #333;"); // rouge
        enemyButtons[x][y].setText("X");
    }

    private void markEnemyCellSunk(int x, int y) {
        enemyButtons[x][y].setStyle("-fx-background-color: #000000; -fx-text-fill: white;");
        enemyButtons[x][y].setText("S");
    }

    private void markEnemyCellMiss(int x, int y) {
        enemyButtons[x][y].setStyle("-fx-background-color: #87cefa; -fx-border-color: #333;"); // bleu clair
        enemyButtons[x][y].setText("o");
    }

    private void markMyCellHit(int x, int y) {
        myButtons[x][y].setStyle("-fx-background-color: #ff4d4d; -fx-border-color: #333;");
        myButtons[x][y].setText("X");
    }

    private void markMyCellSunk(int x, int y) {
        myButtons[x][y].setStyle("-fx-background-color: #000000; -fx-text-fill: white;");
        myButtons[x][y].setText("S");
    }

    private void markMyCellMiss(int x, int y) {
        myButtons[x][y].setStyle("-fx-background-color: #87cefa; -fx-border-color: #333;");
        myButtons[x][y].setText("o");
    }

    private void addLog(String text) {
        logArea.appendText(text + "\n");
    }

    private void showAlert(String title, String text) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(text);
        a.showAndWait();
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        disconnect();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
