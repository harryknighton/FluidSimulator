package uk.co.harrysstuff.fluids;

import uk.co.harrysstuff.colours.Conversions;
import uk.co.harrysstuff.colours.HSV;
import uk.co.harrysstuff.engine.AbstractGame;
import uk.co.harrysstuff.engine.GameContainer;

import javax.vecmath.Vector2d;


/**
 * Represents a simulator for fluid flows over a lattice using a Lattice Boltzmann Method
 * @author Harry Knighton
 */
public class LBSimulator extends AbstractGame {
    private final int latticeW;
    private final int latticeH;
    private final double[][][] lattice;
    private final double[][][] latticeBuffer;
    private final PointType [][] latticeLayout;
    // TODO: Combine all basis vector data into VectorDirections
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

    private static final double RELAXATION_RATE = 1;  // should be in [0.5, 2]
    private float maxDensity = -1f;

    /**
     * Initialises the lattice arrays and layout
     * @param width The width of the lattice in discrete points/particles
     * @param height The height of the lattice
     */
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
                    lattice[x][y][i] = 5;
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
        for (int x = 80; x < 110; x++) {
            latticeLayout[x][190-x] = latticeLayout[x][191-x] = PointType.BOUNDARY;
        }

        // Place fluid
        Vector2d midpoint = new Vector2d(latticeW/2, latticeH/2);
        for (int x = -20; x <= 20; x++) {
            for (int y = -20; y <= 20; y++) {
                double dist = x*x + y*y;
                if (dist <= 40)
                    lattice[(int)midpoint.x + x][(int)midpoint.y + y][VectorDirections.DOWNRIGHT.getIndex()]
                            = 100 * (1 - dist/40);
            }
        }

        // Calculate maxDensity
        for (int x = 0; x < latticeW; x++) {
            for (int y = 0; y < latticeH; y++) {
                float localDensity = calculateLocalDensity(x, y);
                if (localDensity > maxDensity) maxDensity = localDensity;
            }
        }

    }

    @Override
    public void render() {
        // TODO: Interpolate larger lattice onto screen
        // Calculate maximum local density across lattice


        // Render Lattice
        for (int x = 0; x < latticeW; x++) {
            for (int y = 0; y < latticeH; y++) {
                int colour = -1;
                switch (latticeLayout[x][y]) {
                    case AIR -> {
                        float localDensity = calculateLocalDensity(x, y);
                        double proportion = 1 - localDensity / maxDensity;
                        //colour = Conversions.HSVtoRGB(new HSV(240*proportion, 1, 0.8)).asInt();
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
        // TODO: Add timestep
        if (counter == 2) {
            counter = 0;
            collisions();
            stream();
        }
        counter++;
    }

    /**
     * Streaming step of the LBM
     * Moves all particles in the direction of their basis vector one unit
     */
    private void stream() {
        // TODO: Clean Up
        for (int x = 0; x < latticeW; x++) {
            for (int y = 0; y < latticeH; y++) {
                for (VectorDirections d : VectorDirections.values()) {
                    int i = d.getIndex();
                    if (latticeLayout[x][y] == PointType.BOUNDARY) continue;
                    if (latticeLayout[x + (int) D2Q9Vectors[i].x][y + (int) D2Q9Vectors[i].y] == PointType.BOUNDARY) {
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

    /**
     * Collision step of the LBM
     * Resolves internal collisions between particles and redistribution of particles between basis vectors
     */
    private void collisions() {
        Vector2d velocity;
        double eqDist, density, eDotVel;
        for (int x = 0; x < latticeW; x++) {
            for (int y = 0; y < latticeH; y++) {
                density = calculateLocalDensity(x, y);
                velocity = calculateLocalVelocity(x, y);
                for (VectorDirections d : VectorDirections.values()) {
                    int i = d.getIndex();
                    eDotVel = D2Q9Vectors[i].dot(velocity);
                    eqDist = density * vectorWeights[i]
                            * (1 + 3 * eDotVel
                            - (3./2.) * velocity.dot(velocity)
                            + (9./2.) * eDotVel * eDotVel);
                    lattice[x][y][i] += (1. / RELAXATION_RATE) * (eqDist - lattice[x][y][i]);

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
            localVelocity.scale(1f / localDensity);
        return localVelocity;
    }

    public static void main(String[] args) {
        // TODO: Add menu
        // TODO: Add examples
        // TODO: Load lattice from image/in-app creator
        GameContainer gc = new GameContainer();
        LBSimulator sim = new LBSimulator(128, 128);
        sim.bind_container(gc);
        gc.start(sim, "Lattice-Boltzmann Simulator");
    }
}
