package chihalu.horror.client.mixin;

import chihalu.horror.client.terrain.MarchingTerrain;
import chihalu.horror.client.terrain.VoxelGrid;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla rebuilds the sections within one block of a change. A section's surface reads further
 * ({@link VoxelGrid#PAD_LOW} below, {@link VoxelGrid#PAD_HIGH} above), so rebuild those sections too;
 * otherwise a neighbour would keep a stale border and open a crack.
 */
@Mixin(LevelExtractor.class)
abstract class LevelExtractorMixin {
    @Shadow
    protected abstract void setSectionDirty(int sectionX, int sectionY, int sectionZ, boolean playerChanged);

    @Inject(method = "setBlockDirty(Lnet/minecraft/core/BlockPos;Z)V", at = @At("TAIL"))
    private void horror$dirtySurfaceAroundBlock(BlockPos pos, boolean playerChanged, CallbackInfo ci) {
        horror$dirty(pos.getX(), pos.getY(), pos.getZ(), pos.getX(), pos.getY(), pos.getZ(), playerChanged);
    }

    @Inject(method = "setBlocksDirty", at = @At("TAIL"))
    private void horror$dirtySurfaceAroundBlocks(int x0, int y0, int z0, int x1, int y1, int z1, CallbackInfo ci) {
        horror$dirty(x0, y0, z0, x1, y1, z1, false);
    }

    @Unique
    private void horror$dirty(int x0, int y0, int z0, int x1, int y1, int z1, boolean playerChanged) {
        if (!MarchingTerrain.replacesBlocks()) return;
        // Sections whose grid (origin - PAD_LOW .. origin + 15 + PAD_HIGH) contains a changed block.
        int low = VoxelGrid.PAD_HIGH, high = VoxelGrid.PAD_LOW;
        for (int z = SectionPos.blockToSectionCoord(z0 - low); z <= SectionPos.blockToSectionCoord(z1 + high); z++) {
            for (int x = SectionPos.blockToSectionCoord(x0 - low); x <= SectionPos.blockToSectionCoord(x1 + high); x++) {
                for (int y = SectionPos.blockToSectionCoord(y0 - low); y <= SectionPos.blockToSectionCoord(y1 + high); y++) {
                    setSectionDirty(x, y, z, playerChanged);
                }
            }
        }
    }
}
