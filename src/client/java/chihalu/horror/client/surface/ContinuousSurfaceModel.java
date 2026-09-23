package chihalu.horror.client.surface;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.LightLayer;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.world.level.block.state.BlockState;

/** Joins neighbouring terrain cubes using shared world-space vertices, including across chunk edges. */
final class ContinuousSurfaceModel implements BlockStateModel {
    private final BlockStateModel original;
    private final Set<BlockState> surfaces;
    // Rounding reaches 25 cm inward; outward growth is cut by SurfaceCorners.OUTWARD_KEEP to ~9 cm.
    private static final float STRENGTH = .75F;
    // Inward-only unevenness on exposed faces: walls and cliffs vary more than walkable ground.
    private static final float WALL_RELIEF = .07F;
    private static final float FLOOR_RELIEF = .02F;

    ContinuousSurfaceModel(BlockStateModel original, Set<BlockState> surfaces) {
        this.original = original;
        this.surfaces = surfaces;
    }

    @Override
    public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state,
                          RandomSource random, Predicate<Direction> cullTest) {
        // Item displays, falling-block previews, etc. must keep their standalone geometry.
        if (!SurfaceRendering.enabled || level == BlockAndTintGetter.EMPTY || level.getBlockState(pos) != state
                || !SurfaceMaterials.isOrganic(state)) {
            original.emitQuads(emitter, level, pos, state, random, cullTest);
            return;
        }
        SurfaceCorners.Corner[][] corners = new SurfaceCorners.Corner[1][];
        emitter.pushTransform(quad -> {
            if (corners[0] == null) corners[0] = sampleCorners(level, pos);
            return deform(quad, corners[0], level, pos);
        });
        try {
            original.emitQuads(emitter, level, pos, state, random, cullTest);
        } finally {
            emitter.popTransform();
        }
    }

    private SurfaceCorners.Corner[] sampleCorners(BlockAndTintGetter level, BlockPos pos) {
        // Read each neighbouring cell once, not eight times per face. No shared mutable cache.
        byte[] cells = new byte[27];
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int z = -1; z <= 1; z++) for (int y = -1; y <= 1; y++) for (int x = -1; x <= 1; x++) {
            var neighbour = level.getBlockState(cursor.set(pos.getX() + x, pos.getY() + y, pos.getZ() + z));
            cells[(z + 1) * 9 + (y + 1) * 3 + x + 1] =
                    SurfaceMaterials.classify(neighbour, surfaces.contains(neighbour), level, cursor);
        }
        return SurfaceCorners.sample(cells);
    }

    private static boolean deform(MutableQuadView quad, SurfaceCorners.Corner[] corners, BlockAndTintGetter level,
                                  BlockPos pos) {
        boolean moved = false;
        for (int i = 0; i < 4; i++) {
            if (!endpoint(quad.x(i)) || !endpoint(quad.y(i)) || !endpoint(quad.z(i))) return true;
            moved |= corners[Math.round(quad.x(i)) | Math.round(quad.y(i)) << 1 | Math.round(quad.z(i)) << 2]
                    != SurfaceCorners.FLAT;
        }
        if (!moved) return true;
        Direction face = quad.nominalFace();
        for (int i = 0; i < 4; i++) {
            int x = Math.round(quad.x(i)), y = Math.round(quad.y(i)), z = Math.round(quad.z(i));
            var corner = corners[x | (y << 1) | (z << 2)];
            if (corner == SurfaceCorners.FLAT) {
                quad.pos(i, x, y, z);
                continue;
            }
            // Relief is keyed by the world vertex, so neighbouring blocks move the shared corner equally.
            float relief = (FLOOR_RELIEF + (WALL_RELIEF - FLOOR_RELIEF) * (1 - Math.max(0, corner.ny())))
                    * SurfaceCorners.jitter(pos.getX() + x, pos.getY() + y, pos.getZ() + z);
            quad.pos(i, x + corner.x() * STRENGTH - corner.nx() * relief,
                    y + corner.y() * STRENGTH - corner.ny() * relief,
                    z + corner.z() * STRENGTH - corner.nz() * relief);
        }
        if (face != null) {
            // A face moved off its block boundary is lit and occluded by the renderer as if it were
            // inside the rock, so it turns black. Give it exactly vanilla's flat lighting for the
            // unmoved face (the light of the block in front, no corner AO). Nothing is added on top:
            // shadows and shading are left to shader mods.
            quad.ambientOcclusion(TriState.FALSE);
            BlockPos front = pos.relative(face);
            quad.minLightmap(LightCoordsUtil.pack(level.getBrightness(LightLayer.BLOCK, front),
                    level.getBrightness(LightLayer.SKY, front)));
        }
        return true;
    }

    static boolean endpoint(float value) {
        return Math.abs(value) < .00001F || Math.abs(value - 1) < .00001F;
    }

    @Override
    public void collectParts(RandomSource random, List<BlockStateModelPart> parts) {
        original.collectParts(random, parts);
    }

    @Override
    public Material.Baked particleMaterial() { return original.particleMaterial(); }

    @Override
    public int materialFlags() { return original.materialFlags(); }

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
        return null; // Neighbours and the comparison toggle affect the mesh, not just block state.
    }
}
