package chihalu.horror.test;

import chihalu.horror.client.surface.SurfaceRendering;
import chihalu.horror.client.terrain.MarchingTerrain;
import chihalu.horror.client.terrain.TerrainRenderMode;
import chihalu.horror.client.terrain.TerrainRenderer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Screenshots of the marching cubes terrain: a built hill with 1:1, 2:1 and 3:1 staircases (and a
 * diagonal one) in grass, dirt, stone, sand and gravel, laid across chunk borders, plus a tunnel,
 * a tree, a pond, ore in the cliff and plants on a slope; and the generated landscape around it.
 * Every view is taken in the vanilla, marching cubes and wireframe modes, and a few close-ups again
 * without the horror visuals pack.
 */
public class MarchingTerrainClientTest implements FabricClientGameTest {
    private record Shot(String name, Vec3 eye, Vec3 target) { }

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext game = context.worldBuilder().setUseConsistentSettings(false)
                .adjustSettings(settings -> {
                    settings.setSeed("horror-marching-cubes");
                    settings.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
                    settings.setAllowCommands(true);
                }).create()) {
            game.getConnection().waitForChunksRender();
            game.getServer().runCommand("weather clear 1000000");
            game.getServer().runCommand("time set 6000");
            List<Shot> shots = game.getServer().computeOnServer(server ->
                    buildScene(server.overworld(), game.getConnection().getServerPlayer()));
            context.getInput().pressKey(options -> options.keyToggleGui);
            captureModes(context, game, shots, "terrain_");
            // The same close-ups without the horror visuals pack (haze and colour grading), in plain colours.
            CompletableFuture<Void> plain = context.computeOnClient(client -> SurfaceRendering.reload(client, false));
            if (plain == null) throw new AssertionError("Horror visuals pack is not registered");
            context.waitFor(client -> plain.isDone(), 2400);
            context.waitTicks(60);
            captureModes(context, game, shots.stream().filter(shot -> PLAIN_VIEWS.contains(shot.name())).toList(), "terrain_plain_");
            CompletableFuture<Void> restore = context.computeOnClient(client -> SurfaceRendering.reload(client, true));
            context.waitFor(client -> restore.isDone(), 2400);
            context.runOnClient(client -> MarchingTerrain.setMode(client, TerrainRenderMode.MARCHING_CUBES));
            context.getInput().pressKey(options -> options.keyToggleGui);
        }
    }

    private static final Set<String> PLAIN_VIEWS = Set.of("slopes", "chunk_border", "details", "landscape");

    private static void captureModes(ClientGameTestContext context, TestSingleplayerContext game, List<Shot> shots, String prefix) {
        for (TerrainRenderMode mode : TerrainRenderMode.values()) {
            context.runOnClient(client -> MarchingTerrain.setMode(client, mode));
            context.waitTicks(5);
            game.getConnection().waitForChunksRender();
            for (Shot shot : shots) capture(context, game, shot, mode, prefix);
        }
    }

    private static void capture(ClientGameTestContext context, TestSingleplayerContext game, Shot shot, TerrainRenderMode mode,
                                String prefix) {
        game.getServer().runOnServer(server -> {
            ServerPlayer player = game.getConnection().getServerPlayer();
            Vec3 look = shot.target().subtract(shot.eye());
            float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
            float pitch = (float) -Math.toDegrees(Math.atan2(look.y, Math.hypot(look.x, look.z)));
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            player.teleportTo(server.overworld(), shot.eye().x, shot.eye().y - player.getEyeHeight(), shot.eye().z,
                    Set.of(), yaw, pitch, true);
        });
        context.waitTicks(10);
        game.getConnection().waitForChunksRender();
        context.waitTicks(20);
        Path image = context.takeScreenshot(prefix + mode.name().toLowerCase() + "_" + shot.name());
        int sections = context.computeOnClient(client -> TerrainRenderer.drawnSections());
        int triangles = context.computeOnClient(client -> TerrainRenderer.drawnTriangles());
        System.out.println("[horror-terrain-test] " + mode + " " + shot.name() + ": " + sections + " sections, "
                + triangles + " triangles");
        if (mode.replacesBlocks() && sections == 0) throw new AssertionError("No marching cubes surface drawn for " + shot.name());
        String copyTo = System.getenv("HORROR_SCREENSHOT_DIR");
        if (copyTo != null) {
            try {
                Files.createDirectories(Path.of(copyTo));
                Files.copy(image, Path.of(copyTo).resolve(image.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    /**
     * A hill falling along +x in 1:1, 2:1 and 3:1 steps onto a sand flat, rising along +z in 3:1
     * steps, so its corner is a diagonal staircase. The base is chunk aligned: the hill crosses the
     * chunk borders at base + 16 and base + 32.
     */
    private static List<Shot> buildScene(ServerLevel level, ServerPlayer player) {
        BlockPos spawn = player.blockPosition();
        int bx = (spawn.getX() >> 4 << 4) + 16, bz = (spawn.getZ() >> 4 << 4) + 16;
        int by = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx + 24, bz + 16) - 2;

        fill(level, bx - 8, by - 12, bz - 8, bx + 55, by + 40, bz + 47, Blocks.AIR.defaultBlockState());
        for (int u = -8; u < 56; u++) for (int w = -8; w < 48; w++) {
            int h = height(u, w);
            boolean beach = u >= 36, cliff = u >= 6 && u < 11, gravel = u >= 24 && u < 30 && w >= 4 && w < 10;
            for (int y = -12; y < h; y++) {
                BlockState state;
                if (y == h - 1) {
                    state = beach ? Blocks.SAND.defaultBlockState() : cliff ? Blocks.STONE.defaultBlockState()
                            : gravel ? Blocks.GRAVEL.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
                } else if (y >= h - 4 && !cliff) {
                    state = beach ? Blocks.SAND.defaultBlockState() : Blocks.DIRT.defaultBlockState();
                } else {
                    state = Blocks.STONE.defaultBlockState();
                }
                level.setBlock(new BlockPos(bx + u, by + y, bz + w), state, Block.UPDATE_CLIENTS);
            }
        }
        // A one-block bump and a one-block pit on the sand.
        set(level, bx + 44, by + 4, bz + 4, Blocks.SAND.defaultBlockState());
        set(level, bx + 47, by + 3, bz + 7, Blocks.AIR.defaultBlockState());
        // A 1x2 tunnel into the cliff and plateau.
        fill(level, bx - 2, by + 10, bz + 8, bx + 12, by + 11, bz + 8, Blocks.AIR.defaultBlockState());
        // A pond at the foot of the beach.
        fill(level, bx + 40, by + 3, bz + 14, bx + 45, by + 3, bz + 19, Blocks.WATER.defaultBlockState());
        // Ore and andesite in the stone cliff, plants on the 2:1 slope: they stay vanilla blocks.
        set(level, bx + 7, by + height(7, 14) - 1, bz + 14, Blocks.COAL_ORE.defaultBlockState());
        set(level, bx + 8, by + height(8, 15) - 1, bz + 15, Blocks.ANDESITE.defaultBlockState());
        set(level, bx + 9, by + height(9, 13) - 2, bz + 13, Blocks.ANDESITE.defaultBlockState());
        for (int i = 0; i < 12; i++) {
            int u = 12 + Math.floorMod(i * 5, 9), w = 12 + Math.floorMod(i * 7, 8);
            set(level, bx + u, by + height(u, w), bz + w, (i % 3 == 0 ? Blocks.POPPY : Blocks.SHORT_GRASS).defaultBlockState());
        }
        // A tree on the plateau: logs and leaves stay vanilla cubes on the smooth ground.
        int tx = bx + 2, tz = bz + 2, ty = by + height(2, 2);
        for (int y = 0; y < 5; y++) set(level, tx, ty + y, tz, Blocks.OAK_LOG.defaultBlockState());
        for (int x = -2; x <= 2; x++) for (int y = 3; y <= 6; y++) for (int z = -2; z <= 2; z++) {
            if (x * x + (y - 4.5) * (y - 4.5) + z * z <= 6.5 && !(x == 0 && z == 0 && y < 5)) {
                set(level, tx + x, ty + y, tz + z, Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
            }
        }
        return List.of(
                new Shot("overview", new Vec3(bx + 24, by + 34, bz - 20), new Vec3(bx + 24, by + 8, bz + 18)),
                new Shot("slopes", new Vec3(bx + 17, by + 17, bz - 9), new Vec3(bx + 19, by + 10, bz + 8)),
                new Shot("profile", new Vec3(bx + 20, by + 11, bz - 16), new Vec3(bx + 20, by + 10, bz + 10)),
                new Shot("diagonal", new Vec3(bx + 34, by + 30, bz + 56), new Vec3(bx + 18, by + 14, bz + 24)),
                new Shot("chunk_border", new Vec3(bx + 16, by + 16, bz + 2), new Vec3(bx + 16.5, by + 9, bz + 14)),
                new Shot("beach", new Vec3(bx + 50, by + 11, bz - 4), new Vec3(bx + 42, by + 4, bz + 12)),
                new Shot("tunnel", new Vec3(bx + 13.5, by + 11.5, bz + 8.5), new Vec3(bx + 4, by + 11, bz + 8.5)),
                new Shot("details", new Vec3(bx + 19, by + 17, bz + 9), new Vec3(bx + 10, by + 12, bz + 15)),
                new Shot("landscape", new Vec3(spawn.getX() - 40, surface(level, spawn.getX() - 40, spawn.getZ() - 40) + 24,
                        spawn.getZ() - 40), new Vec3(spawn.getX() + 20, surface(level, spawn.getX(), spawn.getZ()), spawn.getZ() + 20)));
    }

    /** Terrain height above the base at local (u, w). */
    private static int height(int u, int w) {
        int h;
        if (u < 6) h = 18;
        else if (u < 11) h = 18 - (u - 5);
        else if (u < 21) h = 13 - (u - 9) / 2;
        else if (u < 33) h = 8 - (u - 19) / 3;
        else h = 4;
        return w >= 20 ? h + (w - 20) / 3 : h;
    }

    private static int surface(ServerLevel level, int x, int z) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
    }

    private static void fill(ServerLevel level, int x1, int y1, int z1, int x2, int y2, int z2, BlockState state) {
        for (BlockPos pos : BlockPos.betweenClosed(x1, y1, z1, x2, y2, z2)) level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    }

    private static void set(ServerLevel level, int x, int y, int z, BlockState state) {
        level.setBlock(new BlockPos(x, y, z), state, Block.UPDATE_CLIENTS);
    }
}
