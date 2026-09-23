package chihalu.horror.client.mixin;

import chihalu.horror.client.terrain.MarchingTerrain;
import chihalu.horror.client.terrain.SectionTerrainMesher;
import chihalu.horror.client.terrain.TerrainMeshHolder;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Builds the marching cubes surface in the same worker task and from the same snapshot as the chunk mesh. */
@Mixin(SectionCompiler.class)
abstract class SectionCompilerMixin {
    @Inject(method = "compile", at = @At("RETURN"))
    private void horror$buildTerrainSurface(SectionPos sectionPos, RenderSectionRegion region, VertexSorting vertexSorting,
                                            SectionBufferBuilderPack builders, CallbackInfoReturnable<SectionCompiler.Results> cir) {
        if (MarchingTerrain.replacesBlocks()) {
            ((TerrainMeshHolder) (Object) cir.getReturnValue()).horror$setTerrainMesh(SectionTerrainMesher.mesh(sectionPos, region));
        }
    }
}
