package model;

import java.io.PrintWriter;
import java.util.Random;

/**
 * Plateau 5x5 avec placement aléatoire des bateaux.
 * B = bateau, ~ = eau, X = touché, O = manqué
 */
public class Board {
    private final int SIZE = 5;           // Taille du plateau
    private final int NB_SHIPS = 3;       // Nombre de bateaux
    private final int SHIP_LENGTH = 1;    // Longueur des bateaux (1 case par défaut)

    private final char[][] grid;
    private final Random random = new Random();

    public Board() {
        grid = new char[SIZE][SIZE];

        for (int i = 0; i < SIZE; i++)
            for (int j = 0; j < SIZE; j++)
                grid[i][j] = '~';

        placeShipsRandomly();
    }

    private void placeShipsRandomly() {
        int placed = 0;

        while (placed < NB_SHIPS) {
            int x = random.nextInt(SIZE);
            int y = random.nextInt(SIZE);

            // Vérifie si la case est libre
            if (canPlaceShip(x, y)) {
                grid[x][y] = 'B';
                placed++;
            }
        }
    }

    private boolean canPlaceShip(int x, int y) {
        return grid[x][y] == '~'; // Case vide ?
    }

    public int getSize() {
        return SIZE;
    }

    public boolean isValidCoordinate(int x, int y) {
        return x >= 0 && x < SIZE && y >= 0 && y < SIZE;
    }

    public boolean isCellUntouched(int x, int y) {
        char c = grid[x][y];
        return c == '~' || c == 'B';
    }

    public synchronized boolean fire(int x, int y) {
        if (!isValidCoordinate(x, y)) throw new IllegalArgumentException("Coord hors plateau");
        char c = grid[x][y];
        if (c == 'B') {
            grid[x][y] = 'X';
            return true;
        } else if (c == '~') {
            grid[x][y] = 'O';
            return false;
        } else {
            return false;
        }
    }

    public boolean isAllSunk() {
        for (int i = 0; i < SIZE; i++)
            for (int j = 0; j < SIZE; j++)
                if (grid[i][j] == 'B') return false;
        return true;
    }

    public void printBoardToWriter(PrintWriter writer) {
        for (int i = 0; i < SIZE; i++) {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < SIZE; j++) {
                sb.append(grid[i][j]).append(' ');
            }
            writer.println(sb.toString());
        }
    }
}
