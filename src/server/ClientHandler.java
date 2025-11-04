package server;

import model.Board;

import java.io.*;
import java.net.Socket;

/**
 * Gère une connexion client.
 * Supporte 2 modes :
 *  - Mode 1 : Joueur vs Joueur (via GameManager)
 *  - Mode 2 : Joueur vs Serveur (IA simple)
 */
public class ClientHandler implements Runnable {
    private final Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private String playerName;
    private Board board;
    private ClientHandler opponent;
    private volatile boolean myTurn = false;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            sendMessage("Bienvenue sur la Bataille Navale (mode console) !");
            sendMessage("Entrez votre pseudo :");
            playerName = in.readLine();
            if (playerName == null) {
                close();
                return;
            }
            sendMessage("Bonjour " + playerName + " !");

            sendMessage("Choisissez un mode :");
            sendMessage("1 - Jouer contre un autre joueur");
            sendMessage("2 - Jouer contre le serveur (IA)");
            sendMessage("Tapez 1 ou 2 :");

            String mode = in.readLine();
            if (mode == null) {
                close();
                return;
            }

            if (mode.trim().equals("2")) {
                startGameVsServer();
            } else {
                // mode 1: joueur vs joueur
                this.board = new Board();
                GameManager.addPlayer(this);
                // boucle principale gérée par GameManager / interactions
                String line;
                while ((line = in.readLine()) != null) {
                    line = line.trim();
                    if (line.equalsIgnoreCase("quit") || line.equalsIgnoreCase("exit")) {
                        sendMessage("Déconnexion...");
                        break;
                    }
                    if (line.startsWith("tir ")) {
                        if (!isMyTurn()) {
                            sendMessage("⏳ Ce n'est pas votre tour !");
                            continue;
                        }
                        try {
                            String[] parts = line.split("\\s+");
                            int x = Integer.parseInt(parts[1]);
                            int y = Integer.parseInt(parts[2]);
                            GameManager.handleShot(this, x, y);
                        } catch (Exception e) {
                            sendMessage("Format invalide. Utilisez : tir x y (ex: tir 1 2)");
                        }
                    } else if (line.equalsIgnoreCase("board")) {
                        sendMessage("Votre plateau :");
                        board.printBoardToWriter(out);
                    } else {
                        sendMessage("Commande inconnue. Exemples : tir 1 2 | board | quit");
                    }
                }
            }

        } catch (IOException e) {
            System.out.println("Connexion perdue avec " + playerName);
        } finally {
            close();
        }
    }

    // Mode joueur vs serveur (IA)
    private void startGameVsServer() throws IOException {
        sendMessage("✅ Mode : Joueur vs Serveur (IA)");
        sendMessage("Le placement des bateaux est automatique (plateau 5x5).");
        this.board = new Board();
        Board iaBoard = new Board();

        sendMessage("Vous commencez la partie. Commande : tir x y (ex: tir 1 2).");
        while (true) {
            sendMessage("À vous de jouer :");
            String request = in.readLine();
            if (request == null) break;
            request = request.trim();
            if (request.equalsIgnoreCase("quit") || request.equalsIgnoreCase("exit")) {
                sendMessage("Vous avez quitté la partie.");
                break;
            }
            if (!request.startsWith("tir ")) {
                sendMessage("Commande invalide. Utilisez : tir x y");
                continue;
            }
            try {
                String[] parts = request.split("\\s+");
                int x = Integer.parseInt(parts[1]);
                int y = Integer.parseInt(parts[2]);

                if (!iaBoard.isValidCoordinate(x, y)) {
                    sendMessage("Coordonnées hors plateau. Utilisez 0..4 pour x et y.");
                    continue;
                }

                boolean hit = iaBoard.fire(x, y);
                sendMessage(hit ? "🔥 Touché !" : "💨 Manqué !");
                if (iaBoard.isAllSunk()) {
                    sendMessage("🏆 Vous avez coulé tous les bateaux. Vous gagnez !");
                    break;
                }

                // Tour IA (simple aléatoire, évite de tirer 2x au même endroit)
                int iaX, iaY;
                do {
                    iaX = (int) (Math.random() * iaBoard.getSize()); // 0..4
                    iaY = (int) (Math.random() * iaBoard.getSize());
                } while (!board.isCellUntouched(iaX, iaY)); // évite repeats

                boolean iaHit = board.fire(iaX, iaY);
                sendMessage("🤖 Le serveur a tiré en (" + iaX + ", " + iaY + ") → " + (iaHit ? "Touché" : "Raté"));
                if (board.isAllSunk()) {
                    sendMessage("❌ Le serveur a coulé tous vos bateaux. Vous avez perdu.");
                    break;
                }
            } catch (NumberFormatException nfe) {
                sendMessage("Coordonnées invalides. Utilisez des entiers : tir x y");
            } catch (Exception ex) {
                sendMessage("Erreur : " + ex.getMessage());
            }
        }
    }

    /* UTILITAIRES */

    public void sendMessage(String msg) {
        out.println(msg);
    }

    public void close() {
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
    }

    /* Getters / Setters pour GameManager */

    public void setOpponent(ClientHandler opponent) { this.opponent = opponent; }
    public ClientHandler getOpponent() { return opponent; }

    public void setBoard(Board board) { this.board = board; }
    public Board getBoard() { return board; }

    public void setMyTurn(boolean myTurn) { this.myTurn = myTurn; }
    public boolean isMyTurn() { return myTurn; }

    public String getPlayerName() { return playerName; }
}
