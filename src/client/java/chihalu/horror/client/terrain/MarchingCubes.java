package chihalu.horror.client.terrain;

import static chihalu.horror.client.terrain.MarchingCubesTables.CORNERS;
import static chihalu.horror.client.terrain.MarchingCubesTables.EDGE_CORNERS;
import static chihalu.horror.client.terrain.MarchingCubesTables.EDGE_TABLE;
import static chihalu.horror.client.terrain.MarchingCubesTables.TRI_TABLE;

import java.util.Arrays;

/**
 * Turns the density of one section into a {@link TerrainMesh} with the standard marching cubes
 * steps: read the 8 corner densities of a cell, build the cube index, look up the crossed edges and
 * triangles, and place each vertex where the density crosses {@link DensityField#ISO_LEVEL} by
 * linear interpolation.
 *
 * <p>A section owns the 16x16x16 cells whose lowest corner is one of its own block centres. A cell
 * on the border also reads block centres of the neighbouring section, so the mesh continues exactly
 * where the neighbour's mesh starts. Vertices are shared per lattice edge inside a section, and are
 * always computed from the lower to the upper end of the edge, so both sides of a chunk border get
 * bit-identical positions and normals.
 */
public final class MarchingCubes {
    /** Light at a block, section-local coordinates in [0, 16]: block light low nibble, sky light high nibble. */
    @FunctionalInterface
    public interface LightSampler {
        int light(int x, int y, int z);
    }

    /** Minimum cosine between a vertex normal and its edge's direction from the solid to the air end. */
    static final float EDGE_FACING = 0.2F;
    private static final int POINTS = 17;

    private MarchingCubes() { }

    public static TerrainMesh mesh(int originX, int originY, int originZ, VoxelGrid grid, LightSampler light) {
        return mesh(originX, originY, originZ, grid, DensityField.compute(grid), light);
    }

    static TerrainMesh mesh(int originX, int originY, int originZ, VoxelGrid grid, DensityField density, LightSampler light) {
        return new Builder(grid, density, light).build(originX, originY, originZ);
    }

    private static final class Builder {
        private final VoxelGrid grid;
        private final DensityField density;
        private final LightSampler light;
        private final int[] edgeVertex = new int[POINTS * POINTS * POINTS * 3];
        private final float[] normal = new float[3];
        private float[] positions = new float[3 * 256];
        private float[] normals = new float[3 * 256];
        private byte[] types = new byte[256];
        private byte[] lights = new byte[256];
        private int[] indices = new int[3 * 512];
        private int vertexCount, indexCount;

        Builder(VoxelGrid grid, DensityField density, LightSampler light) {
            this.grid = grid;
            this.density = density;
            this.light = light;
            Arrays.fill(edgeVertex, -1);
        }

        TerrainMesh build(int originX, int originY, int originZ) {
            int[] cellVertex = new int[12];
            for (int z = 0; z < 16; z++) for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                int cube = 0;
                for (int c = 0; c < 8; c++) {
                    if (density.get(x + CORNERS[c][0], y + CORNERS[c][1], z + CORNERS[c][2]) < DensityField.ISO_LEVEL) cube |= 1 << c;
                }
                int edges = EDGE_TABLE[cube];
                if (edges == 0) continue;
                for (int e = 0; e < 12; e++) if ((edges & (1 << e)) != 0) cellVertex[e] = vertex(x, y, z, e);
                for (int edge : TRI_TABLE[cube]) {
                    if (indexCount == indices.length) indices = Arrays.copyOf(indices, indexCount * 2);
                    indices[indexCount++] = cellVertex[edge];
                }
            }
            return new TerrainMesh(originX, originY, originZ, Arrays.copyOf(positions, vertexCount * 3),
                    Arrays.copyOf(normals, vertexCount * 3), Arrays.copyOf(types, vertexCount),
                    Arrays.copyOf(lights, vertexCount), Arrays.copyOf(indices, indexCount));
        }

        /** Vertex on cell edge {@code e} of the cell at (x, y, z), created once per lattice edge. */
        private int vertex(int x, int y, int z, int e) {
            int[] a = CORNERS[EDGE_CORNERS[e][0]], b = CORNERS[EDGE_CORNERS[e][1]];
            // Lower end of the edge, and its axis.
            int x0 = x + Math.min(a[0], b[0]), y0 = y + Math.min(a[1], b[1]), z0 = z + Math.min(a[2], b[2]);
            int axis = a[0] != b[0] ? 0 : a[1] != b[1] ? 1 : 2;
            int key = ((z0 * POINTS + y0) * POINTS + x0) * 3 + axis;
            int existing = edgeVertex[key];
            if (existing >= 0) return existing;

            int x1 = x0 + (axis == 0 ? 1 : 0), y1 = y0 + (axis == 1 ? 1 : 0), z1 = z0 + (axis == 2 ? 1 : 0);
            float d0 = density.get(x0, y0, z0), d1 = density.get(x1, y1, z1);
            float t = (DensityField.ISO_LEVEL - d0) / (d1 - d0);
            boolean solidLow = d0 > DensityField.ISO_LEVEL;

            int v = vertexCount++;
            if (v == types.length) grow();
            // Block centres sit at +0.5; the vertex moves along the edge by t.
            positions[v * 3] = x0 + 0.5F + (axis == 0 ? t : 0);
            positions[v * 3 + 1] = y0 + 0.5F + (axis == 1 ? t : 0);
            positions[v * 3 + 2] = z0 + 0.5F + (axis == 2 ? t : 0);
            normal(x0, y0, z0, axis, t, solidLow);
            System.arraycopy(normal, 0, normals, v * 3, 3);
            types[v] = solidLow ? grid.get(x0, y0, z0) : grid.get(x1, y1, z1);
            lights[v] = (byte) (solidLow ? light.light(x1, y1, z1) : light.light(x0, y0, z0));
            edgeVertex[key] = v;
            return v;
        }

        /**
         * Smooth normal, out of the terrain. The gradient of the blurred terrain turns a staircase
         * into one even slope; the density's own gradient follows small enclosed spaces (tunnels,
         * pockets) that the blur hardly sees. Both are interpolated along the edge like the position.
         */
        private void normal(int x0, int y0, int z0, int axis, float t, boolean solidLow) {
            int dx = axis == 0 ? 1 : 0, dy = axis == 1 ? 1 : 0, dz = axis == 2 ? 1 : 0;
            float[] n = normal;
            gradient(x0, y0, z0, dx, dy, dz, t, true, n);
            normalize(n);
            float enclosed = DensityField.protection(solidLow ? density.smooth(x0 + dx, y0 + dy, z0 + dz)
                    : density.smooth(x0, y0, z0));
            if (enclosed > 0) {
                float wx = n[0], wy = n[1], wz = n[2];
                gradient(x0, y0, z0, dx, dy, dz, t, false, n);
                normalize(n);
                n[0] = wx + (n[0] - wx) * enclosed;
                n[1] = wy + (n[1] - wy) * enclosed;
                n[2] = wz + (n[2] - wz) * enclosed;
                normalize(n);
            }
            // The vertex separates a solid block from an air block: never face into the solid one.
            float toward = solidLow ? 1 : -1;
            float facing = n[axis] * toward;
            if (facing < EDGE_FACING) {
                n[axis] += (EDGE_FACING - facing) * toward;
                normalize(n);
            }
            if (n[0] == 0 && n[1] == 0 && n[2] == 0) n[axis] = toward;
        }

        /** Negated central-difference gradient of the blur or the density, interpolated along the edge. */
        private void gradient(int x0, int y0, int z0, int dx, int dy, int dz, float t, boolean blurred, float[] out) {
            for (int c = 0; c < 3; c++) {
                int cx = c == 0 ? 1 : 0, cy = c == 1 ? 1 : 0, cz = c == 2 ? 1 : 0;
                float g0 = difference(x0, y0, z0, cx, cy, cz, blurred);
                float g1 = difference(x0 + dx, y0 + dy, z0 + dz, cx, cy, cz, blurred);
                out[c] = -(g0 + (g1 - g0) * t);
            }
        }

        private float difference(int x, int y, int z, int cx, int cy, int cz, boolean blurred) {
            return blurred ? (density.smooth(x + cx, y + cy, z + cz) - density.smooth(x - cx, y - cy, z - cz)) * 0.5F
                    : (density.get(x + cx, y + cy, z + cz) - density.get(x - cx, y - cy, z - cz)) * 0.5F;
        }

        private static void normalize(float[] n) {
            float length = (float) Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]);
            if (length < 1e-6F) {
                n[0] = n[1] = n[2] = 0;
                return;
            }
            n[0] /= length;
            n[1] /= length;
            n[2] /= length;
        }

        private void grow() {
            int capacity = types.length * 2;
            positions = Arrays.copyOf(positions, capacity * 3);
            normals = Arrays.copyOf(normals, capacity * 3);
            types = Arrays.copyOf(types, capacity);
            lights = Arrays.copyOf(lights, capacity);
        }
    }
}
