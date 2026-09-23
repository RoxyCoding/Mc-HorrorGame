package chihalu.horror.client.surface;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Decides which cells take part in the continuous surface. Uses vanilla tags only, so it works on
 * servers without this mod; tags are read while meshing because they are unbound at model bake.
 */
final class SurfaceMaterials {
    static final byte EMPTY = 0;
    static final byte ORGANIC = 1;
    static final byte ANCHOR = 2;

    private static final Set<Block> EXTRA_ORGANIC = Set.of(Blocks.GRAVEL, Blocks.SUSPICIOUS_GRAVEL,
            Blocks.SUSPICIOUS_SAND, Blocks.CLAY, Blocks.SANDSTONE, Blocks.RED_SANDSTONE, Blocks.END_STONE,
            Blocks.SOUL_SOIL, Blocks.MAGMA_BLOCK, Blocks.PACKED_ICE, Blocks.BLUE_ICE, Blocks.SNOW_BLOCK,
            Blocks.TUFF, Blocks.CALCITE, Blocks.DRIPSTONE_BLOCK, Blocks.SMOOTH_BASALT, Blocks.MOSS_BLOCK,
            Blocks.PALE_MOSS_BLOCK, Blocks.OBSIDIAN, Blocks.MUSHROOM_STEM, Blocks.BROWN_MUSHROOM_BLOCK,
            Blocks.RED_MUSHROOM_BLOCK);

    /** Natural terrain and trees get rounded; built blocks keep straight architectural edges. */
    static boolean isOrganic(BlockState state) {
        return state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.BASE_STONE_NETHER)
                || state.is(BlockTags.SUBSTRATE_OVERWORLD) || state.is(BlockTags.DIRT) || state.is(BlockTags.SAND)
                || state.is(BlockTags.ORES) || state.is(BlockTags.BADLANDS_TERRACOTTA) || state.is(BlockTags.NYLIUM)
                || state.is(BlockTags.MUD) || state.is(BlockTags.SNOW) || state.is(BlockTags.LEAVES)
                || state.is(BlockTags.OVERWORLD_NATURAL_LOGS) || EXTRA_ORGANIC.contains(state.getBlock());
    }

    static byte classify(BlockState state, boolean wrapped, BlockGetter level, BlockPos pos) {
        if (wrapped && isOrganic(state)) return ORGANIC;
        return isLoose(state, level, pos) ? EMPTY : ANCHOR;
    }

    /**
     * Cells the surface may move into: air, invisible markers and ground plants that stand on the
     * block below. Vines, lichen, torches, water, doors and every other shape anchor the corner.
     */
    static boolean isLoose(BlockState state, BlockGetter level, BlockPos pos) {
        if (state.isAir()) return true;
        if (!state.getFluidState().isEmpty() || !state.getCollisionShape(level, pos).isEmpty()) return false;
        if (state.getRenderShape() == RenderShape.INVISIBLE) return true;
        Block block = state.getBlock();
        if (block instanceof VineBlock || block instanceof MultifaceBlock || state.is(BlockTags.CAVE_VINES)) return false;
        return state.is(BlockTags.FLOWERS) || state.is(BlockTags.SAPLINGS) || state.is(BlockTags.CROPS)
                || state.is(BlockTags.REPLACEABLE_BY_TREES);
    }

    private SurfaceMaterials() { }
}
