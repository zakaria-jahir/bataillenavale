package server;

import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Serveur TCP Bataille Navale - gère plusieurs joueurs.
 * Apparie les joueurs 2 par 2 en mode JvJ.
 */
public class Server {

    private static final int PORT = 1234;
    private static final List<ClientHandler> waitingPlayers = new ArrayList<>();

    public static void main(String[] args) {
        System.out.println("🟢 Serveur Bataille Navale lancé sur le port " + PORT);
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("Nouvelle connexion de : " + socket.getInetAddress());
                ClientHandler handler = new ClientHandler(socket);
                new Thread(handler).start();
            }
        } catch (IOException e) {
            System.err.println("Erreur serveur : " + e.getMessage());
        }
    }

    // -------------------- CLASSE CLIENT --------------------
    static class ClientHandler implements Runnable {
        private final Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private String pseudo;
        private ClientHandler opponent;
        private boolean inGame = false;
        private boolean myTurn = false;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                out.println("Entrez votre pseudo :");
                pseudo = in.readLine();
                if (pseudo == null) return;

                out.println("Bonjour " + pseudo + " !");
                out.println("Choisissez un mode :");
                out.println("1 - Jouer contre un autre joueur");
                out.println("2 - Jouer contre le serveur (IA)");
                out.println("Tapez 1 ou 2 :");

                String mode = in.readLine();
                if (mode == null) return;

                if (mode.equals("1")) {
                    synchronized (waitingPlayers) {
                        if (waitingPlayers.isEmpty()) {
                            waitingPlayers.add(this);
                            out.println("En attente d'un adversaire...");
                        } else {
                            opponent = waitingPlayers.remove(0);
                            opponent.opponent = this;
                            opponent.inGame = true;
                            this.inGame = true;
                            startGame(opponent);
                        }
                    }
                } else {
                    out.println("Mode IA non encore disponible (sera ajouté plus tard)");
                }

                while (true) {
                    if (inGame && in.ready()) {
                        String line = in.readLine();
                        if (line == null) break;
                        processCommand(line);
                    }
                    Thread.sleep(50);
                }

            } catch (Exception e) {
                System.out.println("Connexion terminée avec " + pseudo);
            } finally {
                try { socket.close(); } catch (IOException ignored) {}
            }
        }

        private void startGame(ClientHandler other) {
            out.println("Partie trouvée ! Vous affrontez " + other.pseudo);
            other.out.println("Partie trouvée ! Vous affrontez " + pseudo);

            out.println("Vous commencez !");
            other.out.println("Votre adversaire commence.");
            this.myTurn = true;
            other.myTurn = false;
        }

        private void processCommand(String cmd) {
            if (cmd.equalsIgnoreCase("quit")) {
                out.println("Déconnexion...");
                try { socket.close(); } catch (IOException ignored) {}
                return;
            }

            if (opponent == null || !inGame) return;

            if (cmd.startsWith("tir")) {
                if (!myTurn) {
                    out.println("Ce n'est pas votre tour !");
                    return;
                }
                myTurn = false;
                opponent.myTurn = true;

                // Simple hasard pour le résultat
                double r = Math.random();
                String result;
                if (r < 0.3) result = "💦 Manqué !";
                else if (r < 0.9) result = "💥 Touché !";
                else result = "🔥 Coulé !";

                out.println("→ Vous tirez : " + result);
                opponent.out.println("L'adversaire a tiré sur vous : " + result);
                opponent.out.println("C'est votre tour !");
            }
        }
    }
}
