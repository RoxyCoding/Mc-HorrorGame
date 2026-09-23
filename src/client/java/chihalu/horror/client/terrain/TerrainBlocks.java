package chihalu.horror.client.terrain;

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Blocks drawn by the marching cubes surface instead of as cubes. Deliberately short for now: trees,
 * built blocks, ores, water and everything else keep their vanilla models.
 */
public final class TerrainBlocks {
    private static final Map<Block, TerrainType> TYPES = new IdentityHashMap<>(Map.of(
            Blocks.GRASS_BLOCK, TerrainType.GRASS,
            Blocks.DIRT, TerrainType.DIRT,
            Blocks.STONE, TerrainType.STONE,
            Blocks.SAND, TerrainType.SAND,
            Blocks.GRAVEL, TerrainType.GRAVEL));

    public static @Nullable TerrainType type(BlockState state) {
        return TYPES.get(state.getBlock());
    }

    public static boolean isTerrain(BlockState state) {
        return TYPES.containsKey(state.getBlock());
    }

    /** Voxel code for {@link VoxelGrid}: the terrain type's code, or {@link TerrainType#NONE}. */
    public static byte code(BlockState state) {
        TerrainType type = TYPES.get(state.getBlock());
        return type == null ? TerrainType.NONE : type.code();
    }

    private TerrainBlocks() { }
}
