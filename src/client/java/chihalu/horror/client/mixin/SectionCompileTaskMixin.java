package chihalu.horror.client.mixin;

import chihalu.horror.client.terrain.TerrainMeshHolder;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A section whose only geometry is the terrain surface (all its blocks are hidden terrain) would get
 * vanilla's shared EMPTY mesh, losing the surface. Keep its own compiled mesh instead.
 */
@Mixin(targets = "net.minecraft.client.renderer.chunk.SectionRenderDispatcher$RenderSection$CompileTask")
abstract class SectionCompileTaskMixin {
    @ModifyExpressionValue(method = "doTask", at = @At(value = "INVOKE", target = "Ljava/util/List;isEmpty()Z"))
    private boolean horror$keepSurfaceMesh(boolean noBlockEntities, @Local SectionCompiler.Results results) {
        return noBlockEntities && ((TerrainMeshHolder) (Object) results).horror$terrainMesh() == null;
    }
}
