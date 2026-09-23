package chihalu.horror.client.terrain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Dependency-free checks of the density field and marching cubes mesher; runs as part of Gradle check. */
public final class MarchingCubesTest {
    /** Terrain code of a world block. */
    interface World {
        byte code(int x, int y, int z);
    }

    private static int assertions;

    public static void main(String[] args) {
        tables();
        flatGround();
        staircases();
        smoothNormals();
        topologyAndBounds();
        tunnelsStayOpen();
        chunkBorders();
        System.out.println("Marching cubes checks passed: " + assertions);
    }

    /** Every triangle of every case faces the outside corners; the edge table is Bourke's. */
    private static void tables() {
        int[] bourke = {0x0, 0x109, 0x203, 0x30a, 0x406, 0x50f, 0x605, 0x70c, 0x80c, 0x905, 0xa0f, 0xb06, 0xc0a, 0xd03, 0xe09, 0xf00};
        for (int i = 0; i < bourke.length; i++) check(MarchingCubesTables.EDGE_TABLE[i] == bourke[i], "Edge table " + i);
        check(MarchingCubesTables.EDGE_TABLE[255] == 0, "Edge table 255");
        for (int cube = 0; cube < 256; cube++) {
            int[] tris = MarchingCubesTables.TRI_TABLE[cube];
            check(tris.length % 3 == 0 && tris.length <= 15, "Too many triangles in case " + cube);
            check((cube == 0 || cube == 255) == (tris.length == 0), "Empty case mismatch " + cube);
            int used = 0;
            for (int t = 0; t < tris.length; t += 3) {
                double[] a = midpoint(tris[t]), b = midpoint(tris[t + 1]), c = midpoint(tris[t + 2]);
                double[] n = cross(sub(b, a), sub(c, a));
                double[] out = new double[3];
                for (int k = 0; k < 3; k++) {
                    int edge = tris[t + k];
                    used |= 1 << edge;
                    int[] ends = MarchingCubesTables.EDGE_CORNERS[edge];
                    boolean firstOutside = (cube & (1 << ends[0])) != 0;
                    int[] from = MarchingCubesTables.CORNERS[firstOutside ? ends[1] : ends[0]];
                    int[] to = MarchingCubesTables.CORNERS[firstOutside ? ends[0] : ends[1]];
                    for (int i = 0; i < 3; i++) out[i] += to[i] - from[i];
                }
                check(dot(n, out) > 0, "Triangle faces into the terrain in case " + cube);
            }
            check(used == MarchingCubesTables.EDGE_TABLE[cube], "Triangles do not use exactly the crossed edges in case " + cube);
        }
    }

    /** Flat ground stays exactly on the block tops, with straight-up normals. */
    private static void flatGround() {
        for (int top : new int[]{3, 8, 16, 0}) {
            World world = (x, y, z) -> y < top ? TerrainType.GRASS.code() : TerrainType.NONE;
            TerrainMesh mesh = mesh(world, 0, 0, 0);
            check(!mesh.isEmpty() || top == 0 || top == 16, "Flat ground produced no surface at " + top);
            for (int v = 0; v < mesh.vertexCount(); v++) {
                near(mesh.positions()[v * 3 + 1], top, 1e-6);
                near(mesh.normals()[v * 3 + 1], 1, 1e-6);
            }
        }
    }

    /**
     * Staircases of 1..4 blocks per step become (nearly) straight slopes, where plain solid = 1 /
     * air = -1 densities keep 45 degree chamfered steps.
     */
    private static void staircases() {
        double[] allowed = {0.001, 0.07, 0.1, 0.14};
        for (int run = 1; run <= 4; run++) {
            double worst = staircaseDeviation(run, false);
            check(worst <= allowed[run - 1], "Staircase " + run + ":1 deviates " + worst);
        }
        double binary = staircaseDeviation(2, true);
        check(binary >= 0.24, "Reference solid/air field should keep 2:1 steps, deviates only " + binary);
    }

    /** Largest distance of a vertex from the ideal slope through the middle of every step's top. */
    private static double staircaseDeviation(int run, boolean binary) {
        World world = (x, y, z) -> y < 40 - Math.floorDiv(x, run) ? TerrainType.STONE.code() : TerrainType.NONE;
        double worst = 0;
        for (int sx = 0; sx < 3; sx++) for (int sy = 0; sy < 3; sy++) {
            VoxelGrid grid = grid(world, sx * 16, sy * 16, 0);
            DensityField field = binary ? DensityField.binary(grid) : DensityField.compute(grid);
            TerrainMesh mesh = MarchingCubes.mesh(sx * 16, sy * 16, 0, grid, field, (x, y, z) -> 0xF0);
            for (int v = 0; v < mesh.vertexCount(); v++) {
                double x = mesh.worldX(v), y = mesh.worldY(v);
                if (x < 8 || x > 40) continue;
                worst = Math.max(worst, Math.abs(y - (40 - (x - run / 2.0) / run)));
            }
        }
        return worst;
    }

    /**
     * Normals come from the blurred terrain, not from the steps: on a 2:1 staircase they stay close to
     * the normal of the average slope, and on rolling hills no vertex normal opposes its triangle.
     */
    private static void smoothNormals() {
        World stairs = (x, y, z) -> y < 40 - Math.floorDiv(x, 2) ? TerrainType.DIRT.code() : TerrainType.NONE;
        double slopeX = 0.5 / Math.sqrt(1.25), slopeY = 1 / Math.sqrt(1.25), worstStair = 1;
        for (int sx = 0; sx < 3; sx++) for (int sy = 0; sy < 3; sy++) {
            TerrainMesh mesh = mesh(stairs, sx * 16, sy * 16, 0);
            for (int v = 0; v < mesh.vertexCount(); v++) {
                if (mesh.worldX(v) < 8 || mesh.worldX(v) > 40) continue;
                float[] n = mesh.normals();
                worstStair = Math.min(worstStair, n[v * 3] * slopeX + n[v * 3 + 1] * slopeY);
            }
        }
        // cos 15 degrees; plain +-1 central differences are off by up to 45 degrees at every step.
        check(worstStair > 0.966, "Staircase normals are not smooth: min cos " + worstStair);

        World hills = (x, y, z) -> y < 24 + 6 * Math.sin(x * 0.21) * Math.cos(z * 0.17) + 3 * Math.sin((x - z) * 0.43)
                ? TerrainType.GRASS.code() : TerrainType.NONE;
        for (int sx = -1; sx <= 1; sx++) for (int sy = 0; sy <= 2; sy++) for (int sz = -1; sz <= 1; sz++) {
            TerrainMesh mesh = mesh(hills, sx * 16, sy * 16, sz * 16);
            float[] p = mesh.positions(), n = mesh.normals();
            int[] idx = mesh.indices();
            for (int t = 0; t < idx.length; t += 3) {
                int a = idx[t], b = idx[t + 1], c = idx[t + 2];
                double[] face = cross(new double[]{p[b * 3] - p[a * 3], p[b * 3 + 1] - p[a * 3 + 1], p[b * 3 + 2] - p[a * 3 + 2]},
                        new double[]{p[c * 3] - p[a * 3], p[c * 3 + 1] - p[a * 3 + 1], p[c * 3 + 2] - p[a * 3 + 2]});
                double[] sum = {n[a * 3] + n[b * 3] + n[c * 3], n[a * 3 + 1] + n[b * 3 + 1] + n[c * 3 + 1], n[a * 3 + 2] + n[b * 3 + 2] + n[c * 3 + 2]};
                check(dot(face, sum) > 0, "Vertex normals oppose their triangle");
            }
        }
    }

    /** Solid blocks stay inside, air stays outside, and no vertex moves more than 0.25 off a block face. */
    private static void topologyAndBounds() {
        World world = noise(1234);
        for (int sy = 0; sy < 2; sy++) {
            VoxelGrid grid = grid(world, 0, sy * 16, 0);
            DensityField field = DensityField.compute(grid);
            for (int z = -1; z <= 17; z++) for (int y = -1; y <= 17; y++) for (int x = -1; x <= 17; x++) {
                boolean solid = grid.get(x, y, z) != TerrainType.NONE;
                float d = field.get(x, y, z);
                check(solid ? d >= DensityField.MIN_MAGNITUDE : d <= -DensityField.MIN_MAGNITUDE, "Density sign or magnitude at " + x + "," + y + "," + z);
                check(Math.abs(d) <= 1, "Density above 1");
            }
            TerrainMesh mesh = MarchingCubes.mesh(0, sy * 16, 0, grid, (x, y, z) -> 0);
            for (int v = 0; v < mesh.vertexCount(); v++) {
                float[] p = mesh.positions();
                int off = 0;
                for (int i = 0; i < 3; i++) {
                    double f = p[v * 3 + i] - Math.floor(p[v * 3 + i]);
                    // Two coordinates sit on block centres (.5); the interpolated one is within 0.25 of a face.
                    if (Math.abs(f - 0.5) > 1e-6) {
                        off++;
                        check(Math.min(f, 1 - f) <= 0.25 + 1e-6, "Vertex moved more than 0.25 from a block face");
                    }
                }
                check(off <= 1, "Vertex off the lattice edges");
                float[] n = mesh.normals();
                near(n[v * 3] * n[v * 3] + n[v * 3 + 1] * n[v * 3 + 1] + n[v * 3 + 2] * n[v * 3 + 2], 1, 1e-4);
            }
        }
    }

    /** A 1x2 tunnel through rock keeps at least its block size: the walls only move into the rock. */
    private static void tunnelsStayOpen() {
        World world = (x, y, z) -> (z == 7 && (y == 5 || y == 6)) || y >= 12 ? TerrainType.NONE : TerrainType.STONE.code();
        TerrainMesh mesh = mesh(world, 0, 0, 0);
        int walls = 0;
        for (int v = 0; v < mesh.vertexCount(); v++) {
            double x = mesh.positions()[v * 3], y = mesh.positions()[v * 3 + 1], z = mesh.positions()[v * 3 + 2];
            if (y > 11 || x < 2 || x > 14) continue;
            walls++;
            // The open space is z in [7, 8] and y in [5, 7]; no vertex may be strictly inside it.
            check(!(z > 7 + 1e-6 && z < 8 - 1e-6 && y > 5 + 1e-6 && y < 7 - 1e-6), "Tunnel narrowed at " + x + "," + y + "," + z);
        }
        check(walls > 0, "Tunnel has no surface");
    }

    /**
     * Meshes of neighbouring sections join without gaps: across a 3x3x3 block of sections every
     * triangle edge is shared by exactly two triangles in opposite directions, and vertices on
     * section borders have bit-identical positions and normals in both sections.
     */
    private static void chunkBorders() {
        World world = noise(99);
        Map<String, float[]> normalsByPosition = new HashMap<>();
        Map<String, Integer> directedEdges = new HashMap<>();
        List<TerrainMesh> meshes = new ArrayList<>();
        for (int sx = -1; sx <= 1; sx++) for (int sy = 0; sy <= 2; sy++) for (int sz = -1; sz <= 1; sz++) {
            meshes.add(mesh(world, sx * 16, sy * 16, sz * 16));
        }
        int shared = 0;
        for (TerrainMesh mesh : meshes) {
            String[] keys = new String[mesh.vertexCount()];
            for (int v = 0; v < mesh.vertexCount(); v++) {
                keys[v] = key(mesh, v);
                float[] normal = {mesh.normals()[v * 3], mesh.normals()[v * 3 + 1], mesh.normals()[v * 3 + 2]};
                float[] previous = normalsByPosition.putIfAbsent(keys[v], normal);
                if (previous != null) {
                    shared++;
                    check(java.util.Arrays.equals(previous, normal), "Normal differs across a section border at " + keys[v]);
                }
            }
            int[] idx = mesh.indices();
            for (int t = 0; t < idx.length; t += 3) {
                for (int k = 0; k < 3; k++) {
                    directedEdges.merge(keys[idx[t + k]] + ">" + keys[idx[t + (k + 1) % 3]], 1, Integer::sum);
                }
            }
        }
        check(shared > 100, "Expected vertices on section borders, found " + shared);
        int interior = 0;
        for (Map.Entry<String, Integer> entry : directedEdges.entrySet()) {
            check(entry.getValue() == 1, "Edge used twice in the same direction: " + entry.getKey());
            String[] ends = entry.getKey().split(">");
            boolean matched = directedEdges.containsKey(ends[1] + ">" + ends[0]);
            if (!matched) {
                // Allowed only on the outer boundary of the meshed block of sections.
                check(onOuterBoundary(ends[0]) && onOuterBoundary(ends[1]), "Open edge (crack) at " + entry.getKey());
            } else {
                interior++;
            }
        }
        check(interior > 1000, "Too few interior edges: " + interior);
    }

    private static boolean onOuterBoundary(String key) {
        String[] c = key.split(",");
        double x = Double.parseDouble(c[0]), y = Double.parseDouble(c[1]), z = Double.parseDouble(c[2]);
        return x <= -15.5 || x >= 32.5 || y <= 0.5 || y >= 48.5 || z <= -15.5 || z >= 32.5;
    }

    private static String key(TerrainMesh mesh, int v) {
        return mesh.worldX(v) + "," + mesh.worldY(v) + "," + mesh.worldZ(v);
    }

    /** Rolling hills with overhangs and caves, from a hash: deterministic and chunk-independent. */
    static World noise(int seed) {
        return (x, y, z) -> {
            double h = 24 + 6 * Math.sin((x + seed) * 0.21) * Math.cos(z * 0.17) + 3 * Math.sin((x - z) * 0.43)
                    + (hash(x, 0, z, seed) & 1);
            boolean cave = Math.sin(x * 0.3 + seed) + Math.sin(y * 0.41) + Math.sin(z * 0.27 - seed) > 1.6;
            boolean rough = hash(x, y, z, seed) % 23 == 0;
            if (y >= h || cave || rough && y > h - 3) return TerrainType.NONE;
            int type = y > h - 1 ? 0 : y > h - 4 ? 1 : 2 + Math.floorMod(hash(x, y, z, seed + 1), 7) / 3;
            return TerrainType.values()[Math.min(type, 4)].code();
        };
    }

    private static int hash(int x, int y, int z, int seed) {
        int h = x * 73428767 ^ y * 912931 ^ z * 438289 ^ seed * 1234567;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        return h ^ (h >>> 15);
    }

    static VoxelGrid grid(World world, int originX, int originY, int originZ) {
        VoxelGrid grid = new VoxelGrid();
        for (int z = -VoxelGrid.PAD_LOW; z < 16 + VoxelGrid.PAD_HIGH; z++)
            for (int y = -VoxelGrid.PAD_LOW; y < 16 + VoxelGrid.PAD_HIGH; y++)
                for (int x = -VoxelGrid.PAD_LOW; x < 16 + VoxelGrid.PAD_HIGH; x++)
                    grid.set(x, y, z, world.code(originX + x, originY + y, originZ + z));
        return grid;
    }

    static TerrainMesh mesh(World world, int originX, int originY, int originZ) {
        return MarchingCubes.mesh(originX, originY, originZ, grid(world, originX, originY, originZ), (x, y, z) -> 0xF0);
    }

    private static double[] midpoint(int edge) {
        int[] a = MarchingCubesTables.CORNERS[MarchingCubesTables.EDGE_CORNERS[edge][0]];
        int[] b = MarchingCubesTables.CORNERS[MarchingCubesTables.EDGE_CORNERS[edge][1]];
        return new double[]{(a[0] + b[0]) / 2.0, (a[1] + b[1]) / 2.0, (a[2] + b[2]) / 2.0};
    }

    private static double[] sub(double[] a, double[] b) {
        return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static void near(double a, double b, double epsilon) {
        check(Math.abs(a - b) <= epsilon, a + " != " + b);
    }

    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }
}
