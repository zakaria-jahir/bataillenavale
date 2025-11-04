package model;

/**
 * Classe de modèle pour futur usage (extensions).
 * Pour l'instant utile comme placeholder si tu veux gérer des bateaux multi-cases.
 */
public class Ship {
    private Position start;
    private int length;
    private boolean horizontal;

    public Ship(Position start, int length, boolean horizontal) {
        this.start = start;
        this.length = length;
        this.horizontal = horizontal;
    }

    public Position getStart() { return start; }
    public int getLength() { return length; }
    public boolean isHorizontal() { return horizontal; }
}
