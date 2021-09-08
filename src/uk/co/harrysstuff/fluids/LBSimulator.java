package uk.co.harrysstuff.fluids;

import uk.co.harrysstuff.colours.Conversions;
import uk.co.harrysstuff.colours.HSV;
import uk.co.harrysstuff.colours.RGB;
import uk.co.harrysstuff.engine.AbstractGame;
import uk.co.harrysstuff.engine.GameContainer;

import javax.vecmath.Vector2d;
import java.awt.*;

public class LBSimulator extends AbstractGame {
    private int latticeW, latticeH;
    private double[][][] lattice;
    private double[][][] latticeBuffer;
    private PointType [][] latticeLayout;
    private final static Vector2d[] D2Q9Vectors = new Vector2d[]{
            new Vector2d(0, 0),
            new Vector2d(1, 0),
            new Vector2d(0, 1),
            new Vector2d(-1, 0),
            new Vector2d(0, -1),
            new Vector2d(1, 1),
            new Vector2d(-1, 1),
            new Vector2d(-1, -1),
            new Vector2d(1, -1)
    };
    private final static double[] vectorWeights = new double[]{
            4./9.,
            1/9., 1/9., 1/9., 1/9.,
            1/36., 1/36., 1/36., 1/36.
    };

    private static final double RELAXATION_RATE = 1.5;  // should be in [0.5, 2]
    private double globalDensity = 0;

    public LBSimulator(int width, int height) {
        this.latticeW = width;
        this.latticeH = height;
        lattice = new double[latticeW][latticeH][9];
        latticeBuffer = new double[latticeW][latticeH][9];
        latticeLayout = new PointType[latticeW][latticeH];

        // Zero lattice
        for (int x = 0; x < latticeW; x++) {
            for (int y = 0; y < latticeH; y++) {
                for (int i = 0; i < 9; i++) {
                    if (i == VectorDirections.REST.getIndex()) lattice[x][y][i] = 0;
                    else lattice[x][y][i] = 0;
                    latticeBuffer[x][y][i] = 0;
                }
            }
        }

        // Initialise lattice layout
        for (int x = 0; x < latticeW; x++) {
            for (int y = 0; y < latticeH; y++) {
                latticeLayout[x][y] = PointType.AIR;
            }
        }
        // Set lattice boundary
        for (int x = 0; x < latticeW; x++) latticeLayout[x][0] = latticeLayout[x][latticeH-1] = PointType.BOUNDARY;
        for (int y = 0; y < latticeH; y++) latticeLayout[0][y] = latticeLayout[latticeW-1][y] = PointType.BOUNDARY;
        for (int x = 0; x < latticeW; x += 20) {
            for (int y = latticeH/2; y < latticeH; y++) {
                latticeLayout[x][y] = PointType.BOUNDARY;
            }
        }

        // Place fluid
        for (int y = 60; y < 80; y++) {
            lattice[10][y][VectorDirections.RIGHT.getIndex()] = 30;
        }
    }

    @Override
    public void render() {
        // TODO: Interpolate larger lattice onto screen
        // Calculate maximum local density across lattice
        float maxDensity = -1f;
        for (int x = 0; x < latticeW; x++) {
            for (int y = 0; y < latticeH; y++) {
                float localDensity = calculateLocalDensity(x, y);
                if (localDensity > maxDensity) maxDensity = localDensity;
            }
        }

        // Render Lattice
        for (int x = 0; x < latticeW; x++) {
            for (int y = 0; y < latticeH; y++) {
                int colour = -1;
                switch (latticeLayout[x][y]) {
                    case AIR -> {
                        float localDensity = calculateLocalDensity(x, y);
                        double proportion = 1 - localDensity / maxDensity;
                        colour = Conversions.HSVtoRGB(new HSV(0, 0, 1- proportion)).asInt();
                        if (localDensity == 0) colour = 0;
                    }
                    case BOUNDARY -> colour = 0xffff00;
                }
                gc.getRenderer().setPixel(x, y, colour);
            }
        }

    }
    private int counter = 0;
    @Override
    public void update() {
        // TODO: Parallelize
        counter++;
        if (counter == 2) counter = 0; else return;
        // Place fluid
        for (int y = 20; y < 60; y++) {
            lattice[1][y][VectorDirections.DOWNRIGHT.getIndex()] = 7;
            lattice[1][y][VectorDirections.UP.getIndex()] = 1;
            lattice[1][y][VectorDirections.DOWN.getIndex()] = 8;
            lattice[1][y][VectorDirections.UPRIGHT.getIndex()] = 2;
            lattice[1][y][VectorDirections.RIGHT.getIndex()] = 5;
        }
        globalDensity = 0;
        for (int x = 0; x < latticeW; x++) {
            for (int y = 0; y < latticeH; y++) {
                if (latticeLayout[x][y] == PointType.AIR) globalDensity += calculateLocalDensity(x, y);
            }
        }
        globalDensity /= (latticeW * latticeH);
        collisions();
        stream();
    }

    private void stream() {
        // TODO: Clean Up
        for (int x = 0; x < latticeW; x++) {
            for (int y = 0; y < latticeH; y++) {
                for (VectorDirections d : VectorDirections.values()) {
                    int i = d.getIndex();
                    if (latticeLayout[x][y] == PointType.BOUNDARY) continue;
                    // Skip edge cases
                    if ((x == 0 && D2Q9Vectors[i].x == -1
                        || x == latticeW-1 && D2Q9Vectors[i].x == 1
                        || y == 0 && D2Q9Vectors[i].y == -1
                        || y == latticeH-1 && D2Q9Vectors[i].y == 1)) {
                        continue;
                    }
                    else if (latticeLayout[x + (int) D2Q9Vectors[i].x][y + (int) D2Q9Vectors[i].y] == PointType.BOUNDARY) {
                        if (i == 1 || i == 3) latticeBuffer[x][y][4-i] = lattice[x][y][i];
                        if (i == 2 || i == 4) latticeBuffer[x][y][6-i] = lattice[x][y][i];
                        else if (i >= 5 && i <= 8) {
                            boolean verticalBoundary = latticeLayout[x + (int) D2Q9Vectors[i].x][y] == PointType.BOUNDARY;
                            boolean horizontalBoundary = latticeLayout[x][y + (int) D2Q9Vectors[i].y] == PointType.BOUNDARY;
                            // Corner boundary
                            if (verticalBoundary == horizontalBoundary)
                                if (i == 5 || i == 7)
                                    latticeBuffer[x][y][12-i] = lattice[x][y][i];
                                else
                                    latticeBuffer[x][y][14-i] = lattice[x][y][i];
                            // Floor or ceiling boundary
                            else if (horizontalBoundary) {
                                switch (i) {
                                    case 5 -> latticeBuffer[x + 2][y][8] = lattice[x][y][i];
                                    case 6 -> latticeBuffer[x - 2][y][7] = lattice[x][y][i];
                                    case 7 -> latticeBuffer[x - 2][y][6] = lattice[x][y][i];
                                    case 8 -> latticeBuffer[x + 2][y][5] = lattice[x][y][i];
                                }
                            }
                            // Wall
                            else {
                                switch (i) {
                                    case 5 -> latticeBuffer[x][y + 2][6] = lattice[x][y][i];
                                    case 6 -> latticeBuffer[x][y + 2][5] = lattice[x][y][i];
                                    case 7 -> latticeBuffer[x][y - 2][8] = lattice[x][y][i];
                                    case 8 -> latticeBuffer[x][y - 2][7] = lattice[x][y][i];
                                }
                            }
                        }
                    }
                    else
                        latticeBuffer[x + (int) D2Q9Vectors[i].x][y + (int) D2Q9Vectors[i].y][i] = lattice[x][y][i];
                }
            }
        }
        for (int x = 0; x < latticeW; x++){
            for (int y = 0; y < latticeH; y++){
                System.arraycopy(latticeBuffer[x][y], 0, lattice[x][y], 0, 9);
            }
        }
    }
    private void collisions() {
        Vector2d velocity;
        double eqDist;
        for (int x = 0; x < latticeW; x++) {
            for (int y = 0; y < latticeH; y++) {
                velocity = calculateLocalVelocity(x, y);
                for (VectorDirections d : VectorDirections.values()) {
                    int i = d.getIndex();
                    // TODO: Implement correct algorithm

                    double eDotVel = D2Q9Vectors[i].dot(velocity);
                    eqDist = globalDensity * vectorWeights[i]
                            * (1 + 3 * eDotVel
                            - (3/2.) * velocity.dot(velocity)
                            + (9/2.) * eDotVel * eDotVel);
                    lattice[x][y][i] += (1 / RELAXATION_RATE) * (eqDist - lattice[x][y][i]);
                    //lattice[x][y][i] -= Math.random() * 0.01;
                    lattice[x][y][i] = Math.max(lattice[x][y][i], 0);
                }
            }
        }
    }

    private float calculateLocalDensity(int x, int y) {
        float localDensity = 0f;
        for (int i = 0; i < 9; i++) {
            localDensity += lattice[x][y][i];
        }
        return localDensity;
    }

    private Vector2d calculateLocalVelocity(int x, int y) {
        Vector2d localVelocity = new Vector2d(0, 0);
        for (int i = 0; i < 9; i++) {
            localVelocity.x += lattice[x][y][i] * D2Q9Vectors[i].x;
            localVelocity.y += lattice[x][y][i] * D2Q9Vectors[i].y;
        }
        double localDensity = calculateLocalDensity(x, y);
        if (localDensity != 0)
            localVelocity.scale(1f / calculateLocalDensity(x, y));
        return localVelocity;
    }

    public static void main(String[] args) {
        GameContainer gc = new GameContainer();
        LBSimulator sim = new LBSimulator(128, 96);
        sim.bind_container(gc);
        gc.start(sim, "Lattice-Boltzmann Simulator");
    }
}
