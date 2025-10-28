package server;

import model.Board;
import java.util.ArrayList;
import java.util.List;

public class GameManager {
    private static final List<ClientHandler> waitingPlayers = new ArrayList<>();

    public static synchronized void addPlayer(ClientHandler player) {
        if (waitingPlayers.isEmpty()) {
            waitingPlayers.add(player);
            player.sendMessage("En attente d’un autre joueur...");
        } else {
            ClientHandler opponent = waitingPlayers.remove(0);
            startGame(player, opponent);
        }
    }

    private static void startGame(ClientHandler p1, ClientHandler p2) {
        p1.setOpponent(p2);
        p2.setOpponent(p1);

        p1.sendMessage("Un adversaire a été trouvé ! Vous commencez la partie.");
        p2.sendMessage("Un adversaire a été trouvé ! Votre adversaire commence.");

        p1.setMyTurn(true);
        p2.setMyTurn(false);

        p1.setBoard(new Board());
        p2.setBoard(new Board());
    }

    public static synchronized void handleShot(ClientHandler shooter, int x, int y) {
        ClientHandler opponent = shooter.getOpponent();

        if (opponent == null) {
            shooter.sendMessage("Aucun adversaire trouvé !");
            return;
        }

        boolean hit = opponent.getBoard().fire(x, y);
        if (hit) {
            shooter.sendMessage("🔥 Touché !");
            opponent.sendMessage("💥 Votre bateau a été touché en (" + x + ", " + y + ")");
        } else {
            shooter.sendMessage("💨 Manqué !");
            opponent.sendMessage("💦 Tir ennemi en (" + x + ", " + y + ") raté !");
        }

        if (opponent.getBoard().isAllSunk()) {
            shooter.sendMessage("🏆 Vous avez gagné !");
            opponent.sendMessage("❌ Vous avez perdu !");
        } else {
            shooter.setMyTurn(false);
            opponent.setMyTurn(true);
            opponent.sendMessage("C’est votre tour !");
        }
    }
}
