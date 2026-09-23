package chihalu.horror.client.terrain;

import java.util.List;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Wraps the model of a terrain block. While the marching cubes surface is on, chunk meshes leave the
 * block out; the block state itself is untouched and still feeds the density field, collision and
 * everything else. Items, falling blocks, the block breaking overlay and other renders outside chunk
 * meshing keep the vanilla cube.
 */
final class HiddenTerrainModel implements BlockStateModel {
    private final BlockStateModel original;

    HiddenTerrainModel(BlockStateModel original) {
        this.original = original;
    }

    private static boolean hidden(BlockAndTintGetter level) {
        return level instanceof RenderSectionRegion && MarchingTerrain.replacesBlocks();
    }

    @Override
    public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state,
                          RandomSource random, Predicate<Direction> cullTest) {
        if (!hidden(level)) original.emitQuads(emitter, level, pos, state, random, cullTest);
    }

    @Override
    public void collectParts(RandomSource random, List<BlockStateModelPart> parts) {
        original.collectParts(random, parts);
    }

    @Override
    public Material.Baked particleMaterial() {
        return original.particleMaterial();
    }

    @Override
    public int materialFlags() {
        return original.materialFlags();
    }

    @Override
    public Material.Baked particleMaterial(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        return original.particleMaterial(level, pos, state);
    }

    @Override
    public int materialFlags(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
        return original.materialFlags(level, pos, state, random);
    }

    @Override
    public Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
        return hidden(level) ? null : original.createGeometryKey(level, pos, state, random);
    }
}
