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

    private static final double RELAXATION_RATE = 2;  // should be in [0.5, 2]
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
                latticeLayout[x][y] = PointType.FLUID;
            }
        }
        // Set lattice boundary
        for (int x = 0; x < latticeW; x++) latticeLayout[x][0] = latticeLayout[x][latticeH-1] = PointType.BOUNDARY;
        for (int y = 0; y < latticeH; y++) latticeLayout[0][y] = latticeLayout[latticeW-1][y] = PointType.BOUNDARY;
        for (int x = 80; x < 110; x++) {
            latticeLayout[x][190-x] = latticeLayout[x][191-x] = PointType.BOUNDARY;
        }

        // Place fluid
        int downright = BasisVectors.getIndex(1, 1);
        Vector2d midpoint = new Vector2d(latticeW/2, latticeH/2);
        for (int x = -20; x <= 20; x++) {
            for (int y = -20; y <= 20; y++) {
                double dist = x*x + y*y;
                if (latticeLayout[(int)midpoint.x + x][(int)midpoint.y + y] == PointType.BOUNDARY) continue;
                if (dist <= 40)
                    lattice[(int)midpoint.x + x][(int)midpoint.y + y][downright]
                            = 100 * (1 - dist/40);
            }
        }

    }

    @Override
    public void render() {
        // TODO: Interpolate larger lattice onto screen
        // Calculate maximum local density across lattice
        // Calculate maxDensity
        maxDensity = 0;
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
                    case FLUID -> {
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
        for (int x = 0; x < latticeW; x++) {
            for (int y = 0; y < latticeH; y++) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        int i = BasisVectors.getIndex(dx, dy);
                        if (latticeLayout[x][y] != PointType.FLUID) continue;
                        if (latticeLayout[x + dx][y + dy] == PointType.BOUNDARY) {
                            boolean verticalBoundary = latticeLayout[x + dx][y] == PointType.BOUNDARY;
                            boolean horizontalBoundary = latticeLayout[x][y + dy] == PointType.BOUNDARY;
                            int newdx = dx * (verticalBoundary || !horizontalBoundary ? -1 : 1);
                            int newdy = dy * (horizontalBoundary || !verticalBoundary ? -1 : 1);
                            int newIndex = BasisVectors.getIndex(newdx, newdy);
                            latticeBuffer[x + dx + newdx][y + dy + newdy][newIndex] = lattice[x][y][i];
                        } else
                            latticeBuffer[x + dx][y + dy][i] = lattice[x][y][i];
                    }
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
                for (BasisVectors d : BasisVectors.values()) {
                    int i = BasisVectors.getIndex((int) d.getVector().x, (int) d.getVector().y);
                    eDotVel = d.getVector().dot(velocity);
                    eqDist = density * d.getWeight()
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
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                int i = BasisVectors.getIndex(dx, dy);
                localVelocity.x += lattice[x][y][i] * dx;
                localVelocity.y += lattice[x][y][i] * dy;
            }
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
