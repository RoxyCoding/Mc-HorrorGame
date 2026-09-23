package chihalu.horror.client.mixin;

import chihalu.horror.client.terrain.MarchingTerrain;
import chihalu.horror.client.terrain.TerrainBlocks;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A hidden terrain block no longer covers its neighbours' faces: the smooth surface can sit up to a
 * quarter block inside it. Draw the faces of other blocks against terrain so no slit shows through.
 */
@Mixin(Block.class)
abstract class BlockMixin {
    @Inject(method = "shouldRenderFace", at = @At("HEAD"), cancellable = true)
    private static void horror$showFacesAgainstSurface(BlockState state, BlockState neighborState, Direction direction,
                                                       CallbackInfoReturnable<Boolean> cir) {
        if (MarchingTerrain.replacesBlocks() && TerrainBlocks.isTerrain(neighborState) && !TerrainBlocks.isTerrain(state)) {
            cir.setReturnValue(true);
        }
    }
}
