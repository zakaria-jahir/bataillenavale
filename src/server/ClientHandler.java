package server;

import model.Board;
import java.io.*;
import java.net.Socket;

public class ClientHandler implements Runnable {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private String playerName;
    private Board board;
    private ClientHandler opponent;
    private boolean myTurn = false;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            sendMessage("Bienvenue sur la bataille navale !");
            sendMessage("Entrez votre pseudo : ");
            playerName = in.readLine();

            sendMessage("Bonjour " + playerName + " !");
            GameManager.addPlayer(this);

            String request;
            while ((request = in.readLine()) != null) {
                if (request.equalsIgnoreCase("quit")) {
                    sendMessage("Déconnexion...");
                    break;
                }

                if (request.startsWith("tir ")) {
                    if (!myTurn) {
                        sendMessage("⏳ Ce n’est pas votre tour !");
                        continue;
                    }

                    try {
                        String[] parts = request.split(" ");
                        int x = Integer.parseInt(parts[1]);
                        int y = Integer.parseInt(parts[2]);
                        GameManager.handleShot(this, x, y);
                    } catch (Exception e) {
                        sendMessage("Format invalide. Utilisez : tir x y");
                    }
                }
            }

            socket.close();
        } catch (IOException e) {
            System.out.println("Client déconnecté.");
        }
    }

    public void sendMessage(String msg) {
        out.println(msg);
    }

    // Getters & setters
    public void setOpponent(ClientHandler opponent) { this.opponent = opponent; }
    public ClientHandler getOpponent() { return opponent; }

    public void setBoard(Board board) { this.board = board; }
    public Board getBoard() { return board; }

    public void setMyTurn(boolean turn) { this.myTurn = turn; }
    public boolean isMyTurn() { return myTurn; }

    public String getPlayerName() { return playerName; }
}
