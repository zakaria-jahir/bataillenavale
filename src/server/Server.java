package server;

import java.io.*;
import java.net.*;
import java.util.*;

public class Server {

    private static final int PORT = 1234;
    private static final int SIZE = 4; // grille 4x4

    private static final List<ClientHandler> waitingPlayers = new ArrayList<>();

    public static void main(String[] args) {
        System.out.println("🟢 Serveur Bataille Navale lancé sur " + PORT);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("Connexion : " + socket.getInetAddress());
                new Thread(new ClientHandler(socket)).start();
            }
        } catch (IOException e) {
            System.err.println("Erreur Serveur : " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------
    //                           CLIENT HANDLER
    // -------------------------------------------------------------------

    static class ClientHandler implements Runnable {

        private final Socket socket;
        private BufferedReader in;
        private PrintWriter out;

        private String pseudo;
        private ClientHandler opponent;
        private IAHandler ia;

        private boolean vsIA = false;
        private boolean myTurn = false;
        private boolean gameOver = false;

        private int[][] myGrid = new int[SIZE][SIZE];     // bateaux du joueur
        private int[][] enemyGrid = new int[SIZE][SIZE];  // bateaux adverses

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {

            try {

                in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                // demander pseudo
                out.println("Entrez votre pseudo :");
                pseudo = in.readLine();
                if (pseudo == null) return;

                out.println("Bonjour " + pseudo + " !");
                out.println("Choisissez un mode :");
                out.println("1 - Jouer contre un autre joueur");
                out.println("2 - Jouer contre l'IA");
                out.println("Tapez 1 ou 2 :");

                String mode = in.readLine();
                if (mode == null) return;

                // préparer la grille du joueur
                placeBoatsRandom(myGrid);

                // MODE IA
                if (mode.equals("2")) {
                    vsIA = true;
                    ia = new IAHandler(this);
                    enemyGrid = ia.myGrid;

                    out.println("Vous jouez contre l'IA !");
                    out.println("Vous commencez !");
                    myTurn = true;
                }

                // MODE JvJ
                else {
                    synchronized (waitingPlayers) {
                        if (waitingPlayers.isEmpty()) {
                            waitingPlayers.add(this);
                            out.println("En attente d'un adversaire...");
                        } else {
                            opponent = waitingPlayers.remove(0);
                            opponent.opponent = this;

                            // Copier les grilles adverses
                            opponent.enemyGrid = this.myGrid;
                            this.enemyGrid    = opponent.myGrid;

                            out.println("Partie trouvée ! Vous affrontez " + opponent.pseudo);
                            opponent.out.println("Partie trouvée ! Vous affrontez " + pseudo);

                            out.println("Vous commencez !");
                            opponent.out.println("Votre adversaire commence.");

                            myTurn = true;
                            opponent.myTurn = false;
                        }
                    }
                }

                // boucle principale
                String cmd;
                while ((cmd = in.readLine()) != null) {
                    process(cmd);
                }

            } catch (Exception e) {
                System.out.println("Déconnexion de " + pseudo);
            } finally {
                try { socket.close(); } catch (IOException ignored) {}
            }
        }

        // -------------------------------------------------------------------
        //                           LOGIQUE DE TIR
        // -------------------------------------------------------------------

        private void process(String cmd) {
            if (gameOver) return;

            // quitter
            if (cmd.equalsIgnoreCase("quit")) {
                gameOver = true;
                return;
            }

            // tir x y
            if (cmd.startsWith("tir")) {

                if (!myTurn) {
                    out.println("Ce n'est pas votre tour !");
                    return;
                }

                String[] parts = cmd.split(" ");
                if (parts.length != 3) return;

                int x = Integer.parseInt(parts[1]);
                int y = Integer.parseInt(parts[2]);

                handleShot(x, y);
            }
        }

        private void handleShot(int x, int y) {

            int cell = enemyGrid[x][y];
            String result;

            if (cell == 0) { // eau
                enemyGrid[x][y] = -1; // tir raté
                result = "💦 Manqué";
            }
            else if (cell == 1) { // bateau intact
                enemyGrid[x][y] = 2; // touché
                if (isShipSunk(enemyGrid, x, y)) {
                    result = "🔥 Coulé";
                } else {
                    result = "💥 Touché";
                }
            }
            else { // déjà tiré
                out.println("Case déjà jouée !");
                return;
            }

            out.println("→ Vous tirez : " + result);

            // check victoire
            if (isAllShipsDestroyed(enemyGrid)) {
                gameOver = true;
                out.println("🏆 VICTOIRE ! Tous les bateaux ennemis sont coulés.");
                if (!vsIA)
                    opponent.out.println("💀 DÉFAITE ! Tous vos bateaux sont coulés.");
                return;
            }

            // tour de l'autre maintenant
            myTurn = false;

            if (vsIA) {
                ia.play();
            } else {
                opponent.myTurn = true;
                opponent.out.println("C'est votre tour !");
            }
        }

        // -------------------------------------------------------------------
        //                        PLACEMENT BATEAUX 2X2
        // -------------------------------------------------------------------

        private void placeBoatsRandom(int[][] grid) {
            for (int i = 0; i < 2; i++) { // 2 bateaux
                boolean placed = false;
                while (!placed) {

                    int x = (int)(Math.random() * SIZE);
                    int y = (int)(Math.random() * SIZE);
                    boolean horizontal = Math.random() < 0.5;

                    if (horizontal && y <= SIZE-2) {
                        if (grid[x][y] == 0 && grid[x][y+1] == 0) {
                            grid[x][y] = grid[x][y+1] = 1;
                            placed = true;
                        }
                    }
                    else if (!horizontal && x <= SIZE-2) {
                        if (grid[x][y] == 0 && grid[x+1][y] == 0) {
                            grid[x][y] = grid[x+1][y] = 1;
                            placed = true;
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------------------
        //                     DETECTION COULÉ / FIN
        // -------------------------------------------------------------------

        private boolean isShipSunk(int[][] grid, int x, int y) {
            // Cherche les voisins touchés
            int[][] dirs = { {1,0}, {-1,0}, {0,1}, {0,-1} };

            for (int[] d : dirs) {
                int nx = x + d[0];
                int ny = y + d[1];
                if (nx >= 0 && nx < SIZE && ny >=0 && ny < SIZE) {
                    if (grid[nx][ny] == 1) {
                        return false; // partie intacte → pas coulé
                    }
                }
            }
            return true;
        }

        private boolean isAllShipsDestroyed(int[][] grid) {
            for (int i = 0; i < SIZE; i++)
                for (int j = 0; j < SIZE; j++)
                    if (grid[i][j] == 1)
                        return false;
            return true;
        }
    }

    // -------------------------------------------------------------------
    //                          IA HANDLER
    // -------------------------------------------------------------------

    static class IAHandler {

        private final ClientHandler human;
        public int[][] myGrid = new int[SIZE][SIZE];

        public IAHandler(ClientHandler human) {
            this.human = human;
            human.placeBoatsRandom(myGrid);
        }

        public void play() {
            new Thread(() -> {

                try { Thread.sleep(800); } catch (Exception ignored) {}

                if (human.gameOver) return;

                // Tir IA = case aléatoire
                int x, y;
                while (true) {
                    x = (int)(Math.random()*SIZE);
                    y = (int)(Math.random()*SIZE);
                    if (human.myGrid[x][y] >= 0) break;
                }

                int cell = human.myGrid[x][y];
                String result;

                if (cell == 0) {
                    human.myGrid[x][y] = -1;
                    result = "a manqué 💦";
                }
                else if (cell == 1) {
                    human.myGrid[x][y] = 2;

                    if (human.isShipSunk(human.myGrid, x, y))
                        result = "vous a coulé 🔥";
                    else
                        result = "vous a touché 💥";
                }
                else {
                    result = "a manqué 💦";
                }

                human.out.println("🤖 IA : " + result);

                // Vérifier défaite
                if (human.isAllShipsDestroyed(human.myGrid)) {
                    human.out.println("💀 DÉFAITE ! Tous vos bateaux sont coulés.");
                    human.gameOver = true;
                    return;
                }

                human.myTurn = true;
                human.out.println("C'est votre tour !");

            }).start();
        }
    }
}
