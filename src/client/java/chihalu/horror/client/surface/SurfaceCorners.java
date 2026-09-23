package chihalu.horror.client.surface;

/** Shared surface-net vertex, computed from the eight cells around a grid corner.
 * Pure geometry: no world state, renderer, or thread-local mutable data. */
public final class SurfaceCorners {
    public record Corner(float x, float y, float z, float nx, float ny, float nz) { }

    public static final Corner FLAT = new Corner(0, 0, 0, 0, 0, 0);
    private static final Corner[] TABLE = new Corner[256];

    static {
        for (int mask = 0; mask < TABLE.length; mask++) TABLE[mask] = calculate(mask);
    }

    public static Corner get(int mask) {
        return TABLE[mask & 255];
    }

    /** 3x3x3 cells, x fastest; 0=empty, 1=surface, 2=protected geometry. */
    public static Corner[] sample(byte[] cells) {
        if (cells.length != 27) throw new IllegalArgumentException("Expected 27 cells");
        Corner[] result = new Corner[8];
        for (int corner = 0; corner < 8; corner++) {
            int vx = corner & 1, vy = (corner >> 1) & 1, vz = (corner >> 2) & 1;
            int mask = 0;
            boolean anchored = false;
            for (int cell = 0; cell < 8; cell++) {
                byte kind = cells[(vz + ((cell >> 2) & 1)) * 9
                        + (vy + ((cell >> 1) & 1)) * 3 + vx + (cell & 1)];
                mask |= kind == 1 ? 1 << cell : 0;
                anchored |= kind == 2;
            }
            result[corner] = anchored ? FLAT : get(mask);
        }
        return result;
    }

    private static Corner calculate(int mask) {
        float[] center = new float[3];
        float[] normal = new float[3];
        int crossings = 0;
        for (int axis = 0; axis < 3; axis++) {
            int step = 1 << axis;
            for (int cell = 0; cell < 8; cell++) {
                if ((cell & step) != 0) continue;
                boolean low = (mask & (1 << cell)) != 0;
                boolean high = (mask & (1 << (cell | step))) != 0;
                if (low == high) continue;
                crossings++;
                normal[axis] += low ? 1 : -1;
                for (int component = 0; component < 3; component++) {
                    if (component != axis) center[component] += (cell & (1 << component)) == 0 ? -.5F : .5F;
                }
            }
        }
        if (crossings == 0) return FLAT;
        float length = (float) Math.sqrt(normal[0] * normal[0] + normal[1] * normal[1] + normal[2] * normal[2]);
        // Ambiguous checkerboards must stay on the grid rather than choose an arbitrary topology.
        if (length == 0) return FLAT;
        float nx = normal[0] / length, ny = normal[1] / length, nz = normal[2] / length;
        float x = center[0] / crossings, y = center[1] / crossings, z = center[2] / crossings;
        // Normals point into air. Rounding inner corners pushes the surface out into space the
        // player can occupy, so keep only part of that movement; chamfering outer edges stays whole.
        float outward = x * nx + y * ny + z * nz;
        if (outward > 0) {
            float cut = outward * (1 - OUTWARD_KEEP);
            x -= nx * cut; y -= ny * cut; z -= nz * cut;
        }
        return new Corner(x, y, z, nx, ny, nz);
    }

    /** Fraction of an outward (into-air) displacement that survives. */
    public static final float OUTWARD_KEEP = .35F;

    /** Stable value in [0, 1) for a world-space lattice vertex; identical for every block sharing it. */
    public static float jitter(int x, int y, int z) {
        int h = x * 0x1F1F1F1F ^ y * 0x5BD1E995 ^ z * 0x27D4EB2D;
        h ^= h >>> 15; h *= 0x2C1B3C6D; h ^= h >>> 12; h *= 0x297A2D39; h ^= h >>> 15;
        return (h >>> 8) / (float) (1 << 24);
    }

    private SurfaceCorners() { }
}
