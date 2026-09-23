package chihalu.horror.client.terrain;

/**
 * Triangle mesh of the terrain surface in one chunk section. Plain arrays so the same data can feed
 * rendering now and collision later; treat them as read-only. Positions are relative to the section
 * origin in blocks. A section owns the cells between block centres 0..16, so its surface lies within
 * [0.5, 16.5]. Triangles are counter-clockwise seen from outside the terrain, and every normal
 * points out of the terrain.
 *
 * @param originX  world block X of the section origin (likewise Y and Z)
 * @param positions xyz per vertex, section-local
 * @param normals   unit xyz per vertex, smooth (from the density gradient)
 * @param types     {@link TerrainType} code per vertex (the terrain block the vertex belongs to)
 * @param light     per vertex: block light in the low 4 bits, sky light in the high 4 bits
 * @param indices   three vertex indices per triangle
 */
public record TerrainMesh(int originX, int originY, int originZ, float[] positions, float[] normals, byte[] types,
                          byte[] light, int[] indices) {
    public int vertexCount() {
        return types.length;
    }

    public int triangleCount() {
        return indices.length / 3;
    }

    public boolean isEmpty() {
        return indices.length == 0;
    }

    public TerrainType type(int vertex) {
        return TerrainType.fromCode(types[vertex]);
    }

    /** World-space position of a vertex, e.g. for collision or ray tests. */
    public double worldX(int vertex) {
        return originX + (double) positions[vertex * 3];
    }

    public double worldY(int vertex) {
        return originY + (double) positions[vertex * 3 + 1];
    }

    public double worldZ(int vertex) {
        return originZ + (double) positions[vertex * 3 + 2];
    }
}
