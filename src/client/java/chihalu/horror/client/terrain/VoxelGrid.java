package chihalu.horror.client.terrain;

/**
 * Terrain codes of one 16x16x16 chunk section plus the neighbouring blocks its surface depends on.
 * Local index 0 is {@link #PAD_LOW} blocks below the section origin on every axis.
 *
 * <p>The surface of a section reads the density on lattice points -1..17 (section-local): 0..16
 * for its own cells and one more on each side for gradient normals. Each density sample reads the
 * blocks within {@link DensityField#BLUR_RADIUS} of it, so the grid reaches that much further.
 */
public final class VoxelGrid {
    public static final int PAD_LOW = 1 + DensityField.BLUR_RADIUS;
    public static final int PAD_HIGH = 2 + DensityField.BLUR_RADIUS;
    public static final int SIZE = 16 + PAD_LOW + PAD_HIGH;

    private final byte[] codes = new byte[SIZE * SIZE * SIZE];

    public static int index(int x, int y, int z) {
        return (z * SIZE + y) * SIZE + x;
    }

    /** Sets a block by section-local coordinates in [-PAD_LOW, 16 + PAD_HIGH). */
    public void set(int x, int y, int z, byte code) {
        codes[index(x + PAD_LOW, y + PAD_LOW, z + PAD_LOW)] = code;
    }

    /** Terrain code by section-local coordinates; {@link TerrainType#NONE} for non-terrain. */
    public byte get(int x, int y, int z) {
        return codes[index(x + PAD_LOW, y + PAD_LOW, z + PAD_LOW)];
    }

    /** Raw codes by grid index, see {@link #index}. */
    byte code(int index) {
        return codes[index];
    }

    public boolean isEmpty() {
        for (byte code : codes) if (code != TerrainType.NONE) return false;
        return true;
    }
}
