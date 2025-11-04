package server;

import model.Board;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple matchmaking : met en attente le premier joueur,
 * quand un second arrive lance la partie entre les deux.
 */
public class GameManager {
    private static final List<ClientHandler> waitingPlayers = new ArrayList<>();

    public static synchronized void addPlayer(ClientHandler player) {
        if (waitingPlayers.isEmpty()) {
            waitingPlayers.add(player);
            player.sendMessage("En attente d'un adversaire...");
        } else {
            ClientHandler opponent = waitingPlayers.remove(0);
            startGame(player, opponent);
        }
    }

    private static void startGame(ClientHandler p1, ClientHandler p2) {
        p1.setOpponent(p2);
        p2.setOpponent(p1);

        p1.setBoard(new Board());
        p2.setBoard(new Board());

        // Choix aléatoire du premier joueur
        boolean p1Starts = Math.random() < 0.5;
        if (p1Starts) {
            p1.setMyTurn(true);
            p2.setMyTurn(false);
            p1.sendMessage("Adversaire trouvé ! Vous commencez.");
            p2.sendMessage("Adversaire trouvé ! Votre adversaire commence.");
        } else {
            p1.setMyTurn(false);
            p2.setMyTurn(true);
            p1.sendMessage("Adversaire trouvé ! Votre adversaire commence.");
            p2.sendMessage("Adversaire trouvé ! Vous commencez.");
        }

        // Indique rules minimalistes
        p1.sendMessage("Commandes : tir x y | board | quit");
        p2.sendMessage("Commandes : tir x y | board | quit");
    }

    /**
     * Gère le tir effectué par shooter vers (x,y) sur l'ennemi.
     */
    public static synchronized void handleShot(ClientHandler shooter, int x, int y) {
        ClientHandler opponent = shooter.getOpponent();
        if (opponent == null) {
            shooter.sendMessage("Aucun adversaire disponible !");
            return;
        }

        Board oppBoard = opponent.getBoard();
        if (!oppBoard.isValidCoordinate(x, y)) {
            shooter.sendMessage("Coordonnées hors plateau. Utilisez 0.." + (oppBoard.getSize()-1));
            return;
        }

        boolean hit = oppBoard.fire(x, y);
        if (hit) {
            shooter.sendMessage("🔥 Touché !");
            opponent.sendMessage("💥 Votre bateau a été touché en (" + x + ", " + y + ")");
        } else {
            shooter.sendMessage("💨 Manqué !");
            opponent.sendMessage("💦 Tir ennemi en (" + x + ", " + y + ") raté !");
        }

        if (oppBoard.isAllSunk()) {
            shooter.sendMessage("🏆 Vous avez gagné !");
            opponent.sendMessage("❌ Vous avez perdu !");
            // Plus de logique de fin (replay / déconnexion) possible ici
        } else {
            // bascule les tours
            shooter.setMyTurn(false);
            opponent.setMyTurn(true);
            opponent.sendMessage("C'est votre tour !");
        }
    }
}
