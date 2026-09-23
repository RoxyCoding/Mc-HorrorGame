package chihalu.horror.client.terrain;

import java.util.ArrayList;
import java.util.List;

/**
 * Marching cubes edge and triangle tables, in Paul Bourke's corner and edge numbering.
 *
 * <p>The cube index has bit {@code i} set when corner {@code i} is below the iso level (outside the
 * terrain). {@link #EDGE_TABLE} marks the edges the surface crosses; {@link #TRI_TABLE} lists
 * triangles as edge numbers, wound counter-clockwise when seen from outside the terrain.
 *
 * <p>The triangle table is generated rather than copied: the contour on each cube face is traced
 * and every ambiguous face (two solid corners on a diagonal) joins its solid corners. The classic
 * table resolves such a face differently depending on the other four corners, so two cells sharing
 * the face could disagree and leave a hole. Here the choice depends only on the face, so neighbouring
 * cells, including cells in different chunks, always produce the same contour.
 */
public final class MarchingCubesTables {
    /** Corner offsets: 0 (0,0,0), 1 (1,0,0), 2 (1,1,0), 3 (0,1,0), 4..7 the same at z = 1. */
    public static final int[][] CORNERS = {
            {0, 0, 0}, {1, 0, 0}, {1, 1, 0}, {0, 1, 0}, {0, 0, 1}, {1, 0, 1}, {1, 1, 1}, {0, 1, 1}};
    /** The two corners of each edge. */
    public static final int[][] EDGE_CORNERS = {
            {0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
    /** Faces as corner loops, counter-clockwise seen from outside the cube. */
    static final int[][] FACES = {
            {0, 4, 7, 3}, {1, 2, 6, 5}, {0, 1, 5, 4}, {3, 7, 6, 2}, {0, 3, 2, 1}, {4, 5, 6, 7}};

    public static final int[] EDGE_TABLE = new int[256];
    public static final int[][] TRI_TABLE = new int[256][];

    static {
        for (int cube = 0; cube < 256; cube++) {
            int edges = 0;
            for (int edge = 0; edge < 12; edge++) {
                if (outside(cube, EDGE_CORNERS[edge][0]) != outside(cube, EDGE_CORNERS[edge][1])) edges |= 1 << edge;
            }
            EDGE_TABLE[cube] = edges;
            TRI_TABLE[cube] = triangulate(cube);
        }
    }

    private static boolean outside(int cube, int corner) {
        return (cube & (1 << corner)) != 0;
    }

    private static int edge(int a, int b) {
        for (int edge = 0; edge < 12; edge++) {
            int[] c = EDGE_CORNERS[edge];
            if (c[0] == a && c[1] == b || c[0] == b && c[1] == a) return edge;
        }
        throw new IllegalArgumentException("Not an edge: " + a + "-" + b);
    }

    private static int[] triangulate(int cube) {
        // next[e] = the crossing that follows e along the contour, or -1.
        int[] next = new int[12];
        java.util.Arrays.fill(next, -1);
        for (int[] face : FACES) {
            // Crossings in counter-clockwise order; solid-to-outside crossings start a segment.
            int[] crossing = new int[4];
            boolean[] leaving = new boolean[4];
            int count = 0;
            for (int i = 0; i < 4; i++) {
                int a = face[i], b = face[(i + 1) % 4];
                if (outside(cube, a) == outside(cube, b)) continue;
                crossing[count] = edge(a, b);
                leaving[count++] = !outside(cube, a);
            }
            // Each segment keeps the solid part of the face on its left, seen from outside the cube.
            // Pairing a leaving crossing with the next entering one cuts off the outside corners,
            // which joins the two solid corners of an ambiguous face.
            for (int i = 0; i < count; i++) {
                if (leaving[i]) next[crossing[i]] = crossing[(i + 1) % count];
            }
        }
        List<Integer> triangles = new ArrayList<>();
        boolean[] used = new boolean[12];
        for (int start = 0; start < 12; start++) {
            if (next[start] < 0 || used[start]) continue;
            List<Integer> loop = new ArrayList<>();
            for (int e = start; !used[e]; e = next[e]) {
                used[e] = true;
                loop.add(e);
            }
            // The traced loop runs clockwise seen from outside the terrain.
            java.util.Collections.reverse(loop);
            triangulateLoop(cube, loop.stream().mapToInt(Integer::intValue).toArray(), triangles);
        }
        return triangles.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * Triangulates a counter-clockwise contour loop. Loops of more than three edges are not planar
     * (saddles), and a plain fan can leave a triangle lying flat on a cube face or facing inwards. Of
     * all triangulations, keep the one whose worst triangle faces the outside corners best.
     */
    private static void triangulateLoop(int cube, int[] loop, List<Integer> out) {
        int n = loop.length;
        double[][] worst = new double[n][n], total = new double[n][n];
        int[][] split = new int[n][n];
        for (int gap = 2; gap < n; gap++) {
            for (int i = 0; i + gap < n; i++) {
                int j = i + gap;
                worst[i][j] = Double.NEGATIVE_INFINITY;
                for (int k = i + 1; k < j; k++) {
                    double score = facing(cube, loop[i], loop[k], loop[j]);
                    double w = Math.min(score, Math.min(k - i > 1 ? worst[i][k] : 1, j - k > 1 ? worst[k][j] : 1));
                    double t = score + (k - i > 1 ? total[i][k] : 0) + (j - k > 1 ? total[k][j] : 0);
                    if (w > worst[i][j] + 1e-9 || Math.abs(w - worst[i][j]) <= 1e-9 && t > total[i][j]) {
                        worst[i][j] = w;
                        total[i][j] = t;
                        split[i][j] = k;
                    }
                }
            }
        }
        emit(loop, split, 0, n - 1, out);
    }

    private static void emit(int[] loop, int[][] split, int i, int j, List<Integer> out) {
        if (j - i < 2) return;
        int k = split[i][j];
        out.add(loop[i]);
        out.add(loop[k]);
        out.add(loop[j]);
        emit(loop, split, i, k, out);
        emit(loop, split, k, j, out);
    }

    /** Cosine between a triangle's normal (edge midpoints) and the direction from its solid to outside corners. */
    private static double facing(int cube, int e0, int e1, int e2) {
        double[] a = midpoint(e0), b = midpoint(e1), c = midpoint(e2);
        double[] u = {b[0] - a[0], b[1] - a[1], b[2] - a[2]}, v = {c[0] - a[0], c[1] - a[1], c[2] - a[2]};
        double[] n = {u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0]};
        double[] o = new double[3];
        for (int edge : new int[]{e0, e1, e2}) {
            int[] ends = EDGE_CORNERS[edge];
            int[] from = CORNERS[outside(cube, ends[0]) ? ends[1] : ends[0]];
            int[] to = CORNERS[outside(cube, ends[0]) ? ends[0] : ends[1]];
            for (int i = 0; i < 3; i++) o[i] += to[i] - from[i];
        }
        double nl = Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]);
        double ol = Math.sqrt(o[0] * o[0] + o[1] * o[1] + o[2] * o[2]);
        return nl == 0 || ol == 0 ? 0 : (n[0] * o[0] + n[1] * o[1] + n[2] * o[2]) / (nl * ol);
    }

    private static double[] midpoint(int edge) {
        int[] a = CORNERS[EDGE_CORNERS[edge][0]], b = CORNERS[EDGE_CORNERS[edge][1]];
        return new double[]{(a[0] + b[0]) / 2.0, (a[1] + b[1]) / 2.0, (a[2] + b[2]) / 2.0};
    }

    private MarchingCubesTables() { }
}
