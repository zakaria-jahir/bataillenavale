package server;

import java.io.*;
import java.net.*;
import java.util.*;

public class Server {

    private static final int PORT = 1234;
    private static final int SIZE = 4;

    private static final List<ClientHandler> waitingPlayers = new ArrayList<>();

    public static void main(String[] args) {
        System.out.println("=== Serveur Bataille Navale ===");
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket socket = serverSocket.accept();
                new Thread(new ClientHandler(socket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

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

        private final int[][] myGrid = new int[SIZE][SIZE];
        private int[][] enemyGrid;

        public ClientHandler(Socket s) {
            this.socket = s;
        }

        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                send("MSG|Entrez votre pseudo :");
                pseudo = in.readLine();
                send("MSG|Bonjour " + pseudo);
                send("MSG|Choisissez un mode : 1 = JvJ, 2 = IA");
                send("ASKMODE");

                placeBoatsRandom(myGrid);

                String mode = in.readLine();
                if (mode == null) return;

                if (mode.equals("2")) startVsIA();
                else startVsPlayer();

                String line;
                while ((line = in.readLine()) != null) {
                    process(line);
                }

            } catch (Exception e) {
                System.out.println("Déconnexion de " + pseudo);
            } finally {
                handleDisconnect();
                try { socket.close(); } catch (IOException ignored) {}
            }
        }

        private void startVsIA() {
            vsIA = true;
            ia = new IAHandler(this);
            enemyGrid = ia.myGrid;
            send("MSG|Partie contre l'IA !");
            send("TURN|YOU");
            myTurn = true;
        }

        private void startVsPlayer() {
            synchronized (waitingPlayers) {
                if (waitingPlayers.isEmpty()) {
                    waitingPlayers.add(this);
                    send("MSG|En attente d'un adversaire...");
                } else {
                    opponent = waitingPlayers.remove(0);
                    opponent.opponent = this;

                    this.enemyGrid = opponent.myGrid;
                    opponent.enemyGrid = this.myGrid;

                    send("MSG|Adversaire trouvé : " + opponent.pseudo);
                    opponent.send("MSG|Adversaire trouvé : " + this.pseudo);

                    // Le joueur qui rejoint commence
                    send("TURN|YOU");
                    opponent.send("TURN|OPP");

                    myTurn = true;
                    opponent.myTurn = false;
                }
            }
        }

        private void process(String msg) {
            if (gameOver) return;

            if (msg.startsWith("SHOT")) handleShotMsg(msg);
            else if (msg.equalsIgnoreCase("QUIT")) handleQuit();
            else if (msg.equalsIgnoreCase("TIMEOUT")) handleTimeout();
            else if (msg.startsWith("CHAT")) broadcastChat(msg);
        }

        private void handleShotMsg(String msg) {
            String[] p = msg.split("\\|");
            if (p.length != 3) {
                send("ERROR|Format tir invalide");
                return;
            }
            if (!myTurn) {
                send("ERROR|Pas votre tour");
                return;
            }
            int x = Integer.parseInt(p[1]);
            int y = Integer.parseInt(p[2]);
            handleShot(x, y);
        }

        private void handleShot(int x, int y) {
            int cell = enemyGrid[x][y];
            if (cell == -1 || cell == 2) {
                send("RESULT|ALREADY|" + x + "|" + y);
                return;
            }

            String result;
            if (cell == 0) {
                enemyGrid[x][y] = -1;
                result = "MISS";
            } else {
                enemyGrid[x][y] = 2;
                result = isShipSunk(enemyGrid, x, y) ? "SUNK" : "HIT";
            }

            // Envoi résultat au joueur
            send("RESULT|" + result + "|" + x + "|" + y);

            // Envoi tir à l'adversaire
            if (!vsIA && opponent != null) {
                opponent.send("OPPONENT_FIRE|" + result + "|" + x + "|" + y);
            }

            if (isAllShipsDestroyed(enemyGrid)) {
                send("END|WIN");
                gameOver = true;
                if (!vsIA && opponent != null) {
                    opponent.send("END|LOSE");
                    opponent.gameOver = true;
                }
            } else nextTurn();
        }

        private void nextTurn() {
            myTurn = false;

            if (vsIA) ia.play();
            else if (opponent != null) {
                opponent.myTurn = true;
                opponent.send("TURN|YOU");
                send("TURN|OPP");
            }
        }

        private void handleQuit() {
            send("END|ABANDON");
            gameOver = true;
            if (!vsIA && opponent != null) {
                opponent.send("OPPONENT_LEFT|Votre adversaire a quitté la partie.");
                opponent.gameOver = true;
            }
        }

        private void handleTimeout() {
            send("MSG|Votre temps est écoulé !");
            nextTurn();
        }

        private void broadcastChat(String msg) {
            if (!vsIA && opponent != null) {
                opponent.send(msg);
            }
        }

        private void handleDisconnect() {
            if (!vsIA && opponent != null && !gameOver) {
                opponent.send("OPPONENT_LEFT|Votre adversaire s'est déconnecté.");
                opponent.gameOver = true;
            }
            synchronized (waitingPlayers) {
                waitingPlayers.remove(this);
            }
        }

        private void send(String s) {
            out.println(s);
        }

        private void placeBoatsRandom(int[][] g) {
            for (int k = 0; k < 2; k++) {
                boolean ok = false;
                while (!ok) {
                    int x = (int) (Math.random() * SIZE);
                    int y = (int) (Math.random() * SIZE);
                    boolean h = Math.random() < 0.5;
                    if (h && y < SIZE - 1 && g[x][y] == 0 && g[x][y + 1] == 0) {
                        g[x][y] = g[x][y + 1] = 1;
                        ok = true;
                    } else if (!h && x < SIZE - 1 && g[x][y] == 0 && g[x + 1][y] == 0) {
                        g[x][y] = g[x + 1][y] = 1;
                        ok = true;
                    }
                }
            }
        }

        private boolean isShipSunk(int[][] g, int x, int y) {
            int[][] d = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] a : d) {
                int nx = x + a[0], ny = y + a[1];
                if (nx >= 0 && nx < SIZE && ny >= 0 && ny < SIZE) {
                    if (g[nx][ny] == 1) return false;
                }
            }
            return true;
        }

        private boolean isAllShipsDestroyed(int[][] g) {
            for (int[] row : g)
                for (int c : row)
                    if (c == 1) return false;
            return true;
        }
    }

    static class IAHandler {
        private final ClientHandler human;
        public int[][] myGrid = new int[SIZE][SIZE];

        public IAHandler(ClientHandler h) {
            human = h;
            human.placeBoatsRandom(myGrid);
        }

        public void play() {
            new Thread(() -> {
                try { Thread.sleep(700); } catch (Exception ignored) {}
                int x, y;
                do {
                    x = (int) (Math.random() * SIZE);
                    y = (int) (Math.random() * SIZE);
                } while (human.myGrid[x][y] == -1 || human.myGrid[x][y] == 2);

                String result;
                if (human.myGrid[x][y] == 0) {
                    human.myGrid[x][y] = -1;
                    result = "MISS";
                } else {
                    human.myGrid[x][y] = 2;
                    result = human.isShipSunk(human.myGrid, x, y) ? "SUNK" : "HIT";
                }

                human.send("OPPONENT_FIRE|" + result + "|" + x + "|" + y);

                if (human.isAllShipsDestroyed(human.myGrid)) {
                    human.send("END|LOSE");
                    human.gameOver = true;
                    return;
                }

                human.myTurn = true;
                human.send("TURN|YOU");

            }).start();
        }
    }
}
