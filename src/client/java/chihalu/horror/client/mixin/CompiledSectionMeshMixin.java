package chihalu.horror.client.mixin;

import chihalu.horror.client.terrain.GpuTerrainMesh;
import chihalu.horror.client.terrain.TerrainMeshHolder;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.chunk.TranslucencyPointOfView;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The surface lives exactly as long as the section's compiled mesh: it becomes visible in the same
 * frame vanilla swaps the new chunk mesh in, and is released when vanilla releases the old one.
 */
@Mixin(CompiledSectionMesh.class)
abstract class CompiledSectionMeshMixin implements TerrainMeshHolder {
    @Unique
    private volatile @Nullable GpuTerrainMesh horror$terrainMesh;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void horror$takeTerrainMesh(TranslucencyPointOfView pointOfView, SectionCompiler.Results results, long startTime,
                                        CallbackInfo ci) {
        horror$terrainMesh = ((TerrainMeshHolder) (Object) results).horror$terrainMesh();
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void horror$releaseTerrainMesh(CallbackInfo ci) {
        GpuTerrainMesh mesh = horror$terrainMesh;
        horror$terrainMesh = null;
        if (mesh != null) mesh.close();
    }

    @Override
    public @Nullable GpuTerrainMesh horror$terrainMesh() {
        return horror$terrainMesh;
    }

    @Override
    public void horror$setTerrainMesh(@Nullable GpuTerrainMesh mesh) {
        horror$terrainMesh = mesh;
    }
}
