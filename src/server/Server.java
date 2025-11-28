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

    //------------------------------------------------------------------------
    // CLIENT HANDLER
    //------------------------------------------------------------------------

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

                in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                send("MSG|Entrez votre pseudo :");
                pseudo = in.readLine();

                send("MSG|Bonjour " + pseudo);
                send("MSG|Choisissez un mode");
                send("MSG|1 = Joueur vs Joueur");
                send("MSG|2 = Joueur vs IA");
                send("ASKMODE");

                placeBoatsRandom(myGrid);

                String mode = in.readLine();
                if (mode == null) return;

                if (mode.equals("2")) {
                    startVsIA();
                } else {
                    startVsPlayer();
                }

                String line;
                while ((line = in.readLine()) != null) {
                    process(line);
                }

            } catch (Exception e) {
                System.out.println("Déconnexion de " + pseudo);
            } finally {
                try { socket.close(); } catch (IOException ignored) {}
            }
        }

        //------------------------------------------------------------------------
        // MATCHMAKING
        //------------------------------------------------------------------------

        private void startVsIA() {
            vsIA = true;
            ia = new IAHandler(this);
            enemyGrid = ia.myGrid;

            send("MSG|Partie contre l’IA !");
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

                    send("TURN|YOU");
                    opponent.send("TURN|OPP");

                    myTurn = true;
                    opponent.myTurn = false;
                }
            }
        }

        //------------------------------------------------------------------------
        // GESTION TIRS
        //------------------------------------------------------------------------

        private void process(String msg) {
            if (gameOver) return;

            if (msg.startsWith("SHOT")) {
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
        }

        private void handleShot(int x, int y) {
            int cell = enemyGrid[x][y];

            if (cell == -1 || cell == 2) {
                send("ERROR|Case déjà jouée");
                return;
            }

            if (cell == 0) {
                enemyGrid[x][y] = -1;
                send("RESULT|MISS|" + x + "|" + y);
                nextTurn();
                return;
            }

            if (cell == 1) {
                enemyGrid[x][y] = 2;

                if (isShipSunk(enemyGrid, x, y)) {
                    send("RESULT|SUNK|" + x + "|" + y);
                } else {
                    send("RESULT|HIT|" + x + "|" + y);
                }

                if (isAllShipsDestroyed(enemyGrid)) {
                    send("END|WIN");
                    if (!vsIA)
                        opponent.send("END|LOSE");

                    gameOver = true;
                    return;
                }

                nextTurn();
            }
        }

        private void nextTurn() {
            myTurn = false;

            if (vsIA) {
                ia.play();
                return;
            }

            opponent.myTurn = true;
            opponent.send("TURN|YOU");
            send("TURN|OPP");
        }

        //------------------------------------------------------------------------
        // OUTILS
        //------------------------------------------------------------------------

        private void send(String s) {
            out.println(s);
        }

        private void placeBoatsRandom(int[][] g) {
            for (int k=0; k<2; k++) {
                boolean ok = false;
                while (!ok) {
                    int x = (int)(Math.random()*SIZE);
                    int y = (int)(Math.random()*SIZE);
                    boolean h = Math.random() < 0.5;
                    if (h && y < SIZE-1 && g[x][y]==0 && g[x][y+1]==0) {
                        g[x][y]=g[x][y+1]=1;
                        ok=true;
                    }
                    else if (!h && x < SIZE-1 && g[x][y]==0 && g[x+1][y]==0) {
                        g[x][y]=g[x+1][y]=1;
                        ok=true;
                    }
                }
            }
        }

        private boolean isShipSunk(int[][] g, int x, int y) {
            int[][] d = {{1,0},{-1,0},{0,1},{0,-1}};
            for (int[] a : d) {
                int nx=x+a[0], ny=y+a[1];
                if (nx>=0 && nx<SIZE && ny>=0 && ny<SIZE) {
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

    //------------------------------------------------------------------------
    // IA
    //------------------------------------------------------------------------

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
                    x = (int)(Math.random()*SIZE);
                    y = (int)(Math.random()*SIZE);
                } while (human.myGrid[x][y] == -1 || human.myGrid[x][y] == 2);

                int cell = human.myGrid[x][y];

                if (cell == 0) {
                    human.myGrid[x][y] = -1;
                    human.send("RESULT|MISS|" + x + "|" + y);
                } else if (cell == 1) {
                    human.myGrid[x][y] = 2;

                    if (human.isShipSunk(human.myGrid, x, y))
                        human.send("RESULT|SUNK|" + x + "|" + y);
                    else
                        human.send("RESULT|HIT|" + x + "|" + y);
                }

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
