package chihalu.horror.client.mixin;

import chihalu.horror.client.terrain.GpuTerrainMesh;
import chihalu.horror.client.terrain.TerrainMeshHolder;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Carries a freshly built surface from the compiler to the compiled section mesh. */
@Mixin(SectionCompiler.Results.class)
abstract class SectionCompilerResultsMixin implements TerrainMeshHolder {
    @Unique
    private @Nullable GpuTerrainMesh horror$terrainMesh;

    @Override
    public @Nullable GpuTerrainMesh horror$terrainMesh() {
        return horror$terrainMesh;
    }

    @Override
    public void horror$setTerrainMesh(@Nullable GpuTerrainMesh mesh) {
        horror$terrainMesh = mesh;
    }
}
