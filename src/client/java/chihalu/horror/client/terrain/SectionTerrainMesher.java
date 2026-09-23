package chihalu.horror.client.terrain;

import chihalu.horror.Horror;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import org.jspecify.annotations.Nullable;

/**
 * Builds a section's surface during vanilla's section compile. Vanilla's compile snapshot covers the
 * section and all 26 neighbours, so the blocks around the section (up to {@link VoxelGrid#PAD_HIGH}
 * away) come from the same consistent copy; neighbouring sections therefore read identical blocks
 * where they overlap and their surfaces meet exactly.
 */
public final class SectionTerrainMesher {
    private static volatile boolean loggedFailure;

    /** Worker thread. Null when the section has no terrain surface. */
    public static @Nullable GpuTerrainMesh mesh(SectionPos section, RenderSectionRegion region) {
        try {
            int ox = section.minBlockX(), oy = section.minBlockY(), oz = section.minBlockZ();
            VoxelGrid grid = new VoxelGrid();
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            boolean terrain = false, open = false;
            for (int z = -VoxelGrid.PAD_LOW; z < 16 + VoxelGrid.PAD_HIGH; z++) {
                for (int y = -VoxelGrid.PAD_LOW; y < 16 + VoxelGrid.PAD_HIGH; y++) {
                    for (int x = -VoxelGrid.PAD_LOW; x < 16 + VoxelGrid.PAD_HIGH; x++) {
                        byte code = TerrainBlocks.code(region.getBlockState(cursor.set(ox + x, oy + y, oz + z)));
                        grid.set(x, y, z, code);
                        if (code == TerrainType.NONE) open = true;
                        else terrain = true;
                    }
                }
            }
            // All air or all buried: no surface can cross this section.
            if (!terrain || !open) return null;
            BlockPos.MutableBlockPos lightPos = new BlockPos.MutableBlockPos();
            TerrainMesh mesh = MarchingCubes.mesh(ox, oy, oz, grid, (x, y, z) -> light(region, lightPos.set(ox + x, oy + y, oz + z)));
            return mesh.isEmpty() ? null : new GpuTerrainMesh(mesh);
        } catch (RuntimeException e) {
            // A broken section should cost its surface, not the game.
            if (!loggedFailure) {
                loggedFailure = true;
                Horror.LOGGER.error("Failed to build the marching cubes surface of section {}", section, e);
            }
            return null;
        }
    }

    /**
     * Light of the open block next to a surface vertex. Inside an opaque non-terrain block (an ore
     * next to stone) the light is 0, so take the brightest open neighbour instead.
     */
    private static int light(RenderSectionRegion region, BlockPos.MutableBlockPos pos) {
        int block = region.getBrightness(LightLayer.BLOCK, pos), sky = region.getBrightness(LightLayer.SKY, pos);
        if (region.getBlockState(pos).isSolidRender()) {
            int x = pos.getX(), y = pos.getY(), z = pos.getZ();
            for (Direction direction : Direction.values()) {
                pos.set(x + direction.getStepX(), y + direction.getStepY(), z + direction.getStepZ());
                block = Math.max(block, region.getBrightness(LightLayer.BLOCK, pos));
                sky = Math.max(sky, region.getBrightness(LightLayer.SKY, pos));
            }
        }
        return sky << 4 | block;
    }

    private SectionTerrainMesher() { }
}
