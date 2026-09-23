package chihalu.horror.client.surface;

/** Dependency-free numerical regression tests; runs as part of Gradle check. */
public final class SurfaceCornersTest {
    public static void main(String[] args) {
        int assertions = 0;
        for (int mask = 0; mask < 256; mask++) {
            var corner = SurfaceCorners.get(mask);
            var inverse = SurfaceCorners.get(mask ^ 255);
            near(corner.nx(), -inverse.nx()); near(corner.ny(), -inverse.ny()); near(corner.nz(), -inverse.nz());
            // Movement into air (along the normal) is limited; movement into the solid is kept whole.
            float out = corner.x() * corner.nx() + corner.y() * corner.ny() + corner.z() * corner.nz();
            float inverseOut = inverse.x() * inverse.nx() + inverse.y() * inverse.ny() + inverse.z() * inverse.nz();
            check(out <= (float) Math.sqrt(3) / 3 * SurfaceCorners.OUTWARD_KEEP + 1e-6, "Protrudes into air for " + mask);
            if (out > 1e-6) near(out, -inverseOut * SurfaceCorners.OUTWARD_KEEP);
            check(Math.abs(corner.x()) <= 1F / 3 + 1e-6 && Math.abs(corner.y()) <= 1F / 3 + 1e-6
                    && Math.abs(corner.z()) <= 1F / 3 + 1e-6, "Unbounded displacement for " + mask);
            float norm = corner.nx() * corner.nx() + corner.ny() * corner.ny() + corner.nz() * corner.nz();
            check(norm == 0 || Math.abs(norm - 1) < 1e-5, "Invalid normal for " + mask);
            assertions += 8;
        }
        // Flat walls/floors/ceilings in all six orientations must be exactly unchanged.
        for (int axis = 0; axis < 3; axis++) for (int sign = 0; sign < 2; sign++) {
            int mask = 0;
            for (int cell = 0; cell < 8; cell++) if (((cell >> axis) & 1) == sign) mask |= 1 << cell;
            var c = SurfaceCorners.get(mask);
            near(c.x(), 0); near(c.y(), 0); near(c.z(), 0);
            float component = axis == 0 ? c.nx() : axis == 1 ? c.ny() : c.nz();
            near(component, sign == 0 ? 1 : -1);
            assertions += 4;
        }
        var convex = SurfaceCorners.get(1);
        near(convex.x(), -1F / 3); near(convex.y(), -1F / 3); near(convex.z(), -1F / 3);
        // Relief hash: deterministic, in [0, 1), and not constant across neighbouring vertices.
        float first = SurfaceCorners.jitter(-31, 64, 17);
        boolean varies = false;
        for (int x = -40; x <= 40; x++) for (int z = -40; z <= 40; z++) {
            float j = SurfaceCorners.jitter(x, 70, z);
            check(j >= 0 && j < 1 && j == SurfaceCorners.jitter(x, 70, z), "Unstable jitter at " + x + "," + z);
            varies |= Math.abs(j - SurfaceCorners.jitter(x + 1, 70, z)) > .05F;
            assertions++;
        }
        check(varies && first == SurfaceCorners.jitter(-31, 64, 17), "Jitter must vary between vertices");
        // Both blocks must derive an identical shared vertex, even at +/- chunk boundaries.
        for (int x = -33; x <= 33; x++) for (int y = -2; y <= 2; y++) for (int z = -17; z <= 17; z++) {
            var a = SurfaceCorners.sample(cells(x, y, z));
            for (int axis = 0; axis < 3; axis++) {
                var b = SurfaceCorners.sample(cells(x + (axis == 0 ? 1 : 0),
                        y + (axis == 1 ? 1 : 0), z + (axis == 2 ? 1 : 0)));
                for (int corner = 0; corner < 8; corner++) if ((corner & (1 << axis)) != 0) {
                    check(a[corner].equals(b[corner ^ (1 << axis)]), "Shared vertex split at " + x + "," + y + "," + z);
                    assertions++;
                }
            }
        }
        // A neighbouring door, water or custom mesh anchors only corners touching that cell.
        byte[] protectedCells = new byte[27];
        protectedCells[13] = 1;
        protectedCells[14] = 2;
        var protectedCorners = SurfaceCorners.sample(protectedCells);
        for (int corner = 0; corner < 8; corner++) {
            check((protectedCorners[corner] == SurfaceCorners.FLAT) == ((corner & 1) != 0), "Incorrect anchored corner");
            assertions++;
        }
        System.out.println("Surface geometry checks passed: " + (assertions + 3));
    }

    private static byte[] cells(int x, int y, int z) {
        byte[] result = new byte[27];
        for (int dz = -1; dz <= 1; dz++) for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
            int hash = (x + dx) * 73428767 ^ (y + dy) * 912931 ^ (z + dz) * 438289;
            int sample = Math.floorMod(hash, 11);
            result[(dz + 1) * 9 + (dy + 1) * 3 + dx + 1] = (byte) (sample == 0 ? 2 : sample < 6 ? 1 : 0);
        }
        return result;
    }

    private static void near(float a, float b) { check(Math.abs(a - b) < 1e-6F, a + " != " + b); }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
