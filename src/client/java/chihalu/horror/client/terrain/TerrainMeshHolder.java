package chihalu.horror.client.terrain;

import org.jspecify.annotations.Nullable;

/**
 * Added by mixins to vanilla's section compile results and compiled section meshes, so the surface of
 * a section is created, swapped in and released together with the section's vanilla geometry.
 */
public interface TerrainMeshHolder {
    @Nullable GpuTerrainMesh horror$terrainMesh();

    void horror$setTerrainMesh(@Nullable GpuTerrainMesh mesh);
}
