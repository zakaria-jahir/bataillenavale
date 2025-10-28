package model;

public class Board {
    private char[][] grid = new char[5][5]; // pour tester plus vite

    public Board() {
        for (int i = 0; i < grid.length; i++)
            for (int j = 0; j < grid[i].length; j++)
                grid[i][j] = '~';

        // Exemple : on place 3 bateaux manuellement
        grid[1][1] = 'B';
        grid[2][3] = 'B';
        grid[4][0] = 'B';
    }

    public boolean fire(int x, int y) {
        if (grid[x][y] == 'B') {
            grid[x][y] = 'X';
            return true;
        } else {
            grid[x][y] = 'O';
            return false;
        }
    }

    public boolean isAllSunk() {
        for (int i = 0; i < grid.length; i++)
            for (int j = 0; j < grid[i].length; j++)
                if (grid[i][j] == 'B') return false;
        return true;
    }
}
