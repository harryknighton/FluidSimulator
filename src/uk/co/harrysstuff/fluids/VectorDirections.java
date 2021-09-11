package uk.co.harrysstuff.fluids;

public enum VectorDirections {
    REST (0),
    RIGHT (1),
    DOWN (2),
    LEFT (3),
    UP (4),
    DOWNRIGHT (5),
    DOWNLEFT (6),
    UPLEFT (7),
    UPRIGHT (8);

    private final int index;
    VectorDirections(int index) {
        this.index = index;
    }

    public int getIndex() {
        return index;
    }
}
