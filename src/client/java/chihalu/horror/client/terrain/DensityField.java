package chihalu.horror.client.terrain;

/**
 * Density on the lattice of block centres around one section: positive inside terrain, negative
 * outside, surface at {@link #ISO_LEVEL}.
 *
 * <p>Plain solid = 1 / air = -1 samples put every crossing exactly half way between two block
 * centres, which keeps 45 degree chamfered steps. Instead each sample is a blur of the terrain
 * around it, so the surface follows the average slope of a staircase. The sign of every sample is
 * still the block's own (solid blocks positive, everything else negative): the surface never drops
 * a block, never closes a gap and never moves more than 0.25 blocks off the block faces. Air cells
 * enclosed by terrain (tunnels, one-block gaps) keep their full size so the camera stays outside.
 *
 * <p>The blur is integer arithmetic over world blocks only, so a lattice point gets the identical
 * value from every section that samples it: shared chunk borders match exactly.
 */
public final class DensityField {
    public static final float ISO_LEVEL = 0;
    public static final int BLUR_RADIUS = 3;
    /** Binomial weights; the blur is this kernel along x, y and z in turn. */
    private static final int[] KERNEL = {1, 6, 15, 20, 15, 6, 1};
    /** Blur of the top block of flat ground, (1 + 6 + 15 + 20 - 15 - 6 - 1) * 64 * 64: maps flat ground to +-1. */
    private static final float FLAT = 81920;
    /** Smallest |density|: crossings stay between 0.25 and 0.75 of the way between block centres. */
    public static final float MIN_MAGNITUDE = 1F / 3;
    /** Blur of an air cell above which it counts as enclosed by terrain, and where it is fully kept. */
    static final float PROTECT_START = 0.35F, PROTECT_FULL = 0.7F;

    /** Section-local lattice range covered: -1 .. 17 on each axis. */
    public static final int MIN = -1, SIZE = 19;

    private final float[] density = new float[SIZE * SIZE * SIZE];
    /** The normalised blur itself, unclamped; its gradient gives smooth normals. */
    private final float[] smooth = new float[SIZE * SIZE * SIZE];

    private DensityField() { }

    private static int index(int x, int y, int z) {
        return ((z - MIN) * SIZE + (y - MIN)) * SIZE + (x - MIN);
    }

    /** Density of the block centre at section-local lattice coordinates in [-1, 17]. */
    public float get(int x, int y, int z) {
        return density[index(x, y, z)];
    }

    /** Blurred terrain amount at a block centre (1 = top of flat ground), before the sign is enforced. */
    public float smooth(int x, int y, int z) {
        return smooth[index(x, y, z)];
    }

    public static DensityField compute(VoxelGrid grid) {
        final int g = VoxelGrid.SIZE, r = BLUR_RADIUS;
        // Grid index of section-local lattice point MIN, and one past the last one needed.
        final int lo = MIN + VoxelGrid.PAD_LOW, hi = lo + SIZE;
        int[] sign = new int[g * g * g];
        for (int i = 0; i < sign.length; i++) sign[i] = grid.code(i) != TerrainType.NONE ? 1 : -1;

        // Separable binomial blur, x then y then z, each pass only where the next one reads.
        int[] blurX = new int[g * g * SIZE];
        for (int z = 0; z < g; z++) for (int y = 0; y < g; y++) for (int x = lo; x < hi; x++) {
            int sum = 0;
            for (int k = 0; k < KERNEL.length; k++) sum += KERNEL[k] * sign[VoxelGrid.index(x + k - r, y, z)];
            blurX[(z * g + y) * SIZE + (x - lo)] = sum;
        }
        int[] blurY = new int[g * SIZE * SIZE];
        for (int z = 0; z < g; z++) for (int y = lo; y < hi; y++) for (int x = 0; x < SIZE; x++) {
            int sum = 0;
            for (int k = 0; k < KERNEL.length; k++) sum += KERNEL[k] * blurX[(z * g + y + k - r) * SIZE + x];
            blurY[(z * SIZE + (y - lo)) * SIZE + x] = sum;
        }
        DensityField field = new DensityField();
        for (int z = lo; z < hi; z++) for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) {
            int sum = 0;
            for (int k = 0; k < KERNEL.length; k++) sum += KERNEL[k] * blurY[((z + k - r) * SIZE + y) * SIZE + x];
            boolean solid = sign[VoxelGrid.index(x + lo, y + lo, z)] > 0;
            int i = ((z - lo) * SIZE + y) * SIZE + x;
            field.smooth[i] = sum / FLAT;
            field.density[i] = density(solid, field.smooth[i]);
        }
        return field;
    }

    /** The unsmoothed reference field: solid = 1, air = -1. Chamfers steps at 45 degrees. */
    static DensityField binary(VoxelGrid grid) {
        DensityField field = compute(grid);
        for (int z = MIN; z < MIN + SIZE; z++) for (int y = MIN; y < MIN + SIZE; y++) for (int x = MIN; x < MIN + SIZE; x++) {
            field.density[index(x, y, z)] = grid.get(x, y, z) != TerrainType.NONE ? 1 : -1;
        }
        return field;
    }

    /** Sign from the block itself, magnitude from its neighbourhood. */
    static float density(boolean solid, float blurred) {
        if (solid) return clamp(blurred, MIN_MAGNITUDE, 1);
        // An air cell with terrain on most sides is a tunnel or gap: do not grow the surface into it.
        float magnitude = clamp(-blurred, MIN_MAGNITUDE, 1);
        return -Math.max(magnitude, MIN_MAGNITUDE + (1 - MIN_MAGNITUDE) * protection(blurred));
    }

    /** 0 for open air, rising smoothly to 1 for an air cell enclosed by terrain. */
    static float protection(float blurred) {
        float t = clamp((blurred - PROTECT_START) / (PROTECT_FULL - PROTECT_START), 0, 1);
        return t * t * (3 - 2 * t);
    }

    private static float clamp(float value, float min, float max) {
        return value < min ? min : Math.min(value, max);
    }
}
