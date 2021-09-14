package uk.co.harrysstuff.fluids;

import javax.vecmath.Vector2d;

public enum BasisVectors {
    REST (0, 0),
    RIGHT (1, 0),
    DOWN (0, 1),
    LEFT (-1, 0),
    UP (0, -1),
    DOWNRIGHT (1, 1),
    DOWNLEFT (-1, 1),
    UPLEFT (-1, -1),
    UPRIGHT (1, -1);

    private final Vector2d vec;
    private final double weight;

    BasisVectors(int x, int y) {
        vec = new Vector2d(x, y);
        if (x == 0 && y == 0) weight = 4/9.;
        else if (x == 0 || y == 0) weight = 1/9.;
        else weight = 1/36.;
    }

    public Vector2d getVector() {
        return vec;
    }

    public double getWeight() {
        return weight;
    }

    public static int getIndex(int dx, int dy) {
        return dx + 3*dy + 4;
    }

}
