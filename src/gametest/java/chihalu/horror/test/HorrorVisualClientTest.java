package chihalu.horror.test;

import chihalu.horror.client.surface.SurfaceRendering;
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
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Screenshot scenes for the world renderer in an ordinary generated world: terrain, a stone
 * slope, a pond, a door in a plank wall, a tree, a torch at night and a carved cave. Every scene
 * is captured with the horror visuals on and then off, into the run directory's screenshots.
 */
public class HorrorVisualClientTest implements FabricClientGameTest {
    private record Shot(String name, long time, Vec3 eye, Vec3 target) { }

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext game = context.worldBuilder().setUseConsistentSettings(false)
                .adjustSettings(settings -> {
                    settings.setSeed("horror-visuals");
                    settings.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
                    settings.setAllowCommands(true);
                }).create()) {
            game.getConnection().waitForChunksRender();
            context.runOnClient(client -> {
                for (String path : List.of("post_effect/end_of_frame.json", "shaders/core/terrain.fsh")) {
                    var id = net.minecraft.resources.Identifier.withDefaultNamespace(path);
                    System.out.println("[horror-visual-test] " + path + " from "
                            + client.getResourceManager().getResource(id).map(r -> r.sourcePackId()).orElse("<missing>"));
                }
            });
            game.getServer().runCommand("weather clear 1000000");
            List<Shot> shots = game.getServer().computeOnServer(server ->
                    buildScenes(server.overworld(), game.getConnection().getServerPlayer()));
            context.getInput().pressKey(options -> options.keyToggleGui);

            context.runOnClient(client -> {
                client.options.enableVsync().set(false);
                client.options.framerateLimit().set(260); // 260 = unlimited
            });
            capture(context, game, shots, "on");
            measureFps(context, game, shots, "on");
            // HUD and first-person hand are drawn around the post effect; check they still appear.
            context.getInput().pressKey(options -> options.keyToggleGui);
            capture(context, game, List.of(shots.get(0)), "hud");
            context.getInput().pressKey(options -> options.keyToggleGui);
            CompletableFuture<Void> reload = context.computeOnClient(client -> SurfaceRendering.reload(client, false));
            if (reload == null) throw new AssertionError("Horror visuals pack is not registered");
            context.waitFor(client -> reload.isDone(), 2400);
            context.waitTicks(60); // let the reload overlay fade out
            capture(context, game, shots, "off");
            measureFps(context, game, shots, "off");
            CompletableFuture<Void> restore = context.computeOnClient(client -> SurfaceRendering.reload(client, true));
            context.waitFor(client -> restore.isDone(), 2400);
        }
    }

    private static void capture(ClientGameTestContext context, TestSingleplayerContext game, List<Shot> shots, String mode) {
        for (Shot shot : shots) {
            game.getServer().runCommand("time set " + shot.time());
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
            Path image = context.takeScreenshot("horror_" + mode + "_" + shot.name());
            // Optional copy for reviewers who cannot read the build directory.
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
    }

    /** Rough frame-rate comparison for the same views; printed to the log, not asserted. */
    private static void measureFps(ClientGameTestContext context, TestSingleplayerContext game, List<Shot> shots, String mode) {
        for (Shot shot : List.of(shots.get(0), shots.get(5))) {
            capture(context, game, List.of(shot), "fps_" + mode);
            context.waitTicks(60);
            int fps = context.computeOnClient(client -> client.getFps());
            System.out.println("[horror-visual-test] fps " + mode + " " + shot.name() + ": " + fps);
        }
    }

    private static List<Shot> buildScenes(ServerLevel level, ServerPlayer player) {
        BlockPos spawn = player.blockPosition();
        int x0 = spawn.getX() + 6, z0 = spawn.getZ();
        int y0 = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x0, z0);

        fill(level, x0 - 6, y0, z0 - 6, x0 + 20, y0 + 12, z0 + 18, Blocks.AIR.defaultBlockState());
        fill(level, x0 - 6, y0 - 5, z0 - 6, x0 + 20, y0 - 2, z0 + 18, Blocks.DIRT.defaultBlockState());
        fill(level, x0 - 6, y0 - 1, z0 - 6, x0 + 20, y0 - 1, z0 + 18, Blocks.GRASS_BLOCK.defaultBlockState());
        // Irregular stone slope: uneven steps like a natural outcrop.
        for (int x = 9; x <= 20; x++) for (int z = -6; z <= 18; z++) {
            int height = Math.min(7, (x - 8) / 2 + Math.floorMod(x * 7 + z * 3, 5) / 3);
            for (int y = 0; y < height; y++) set(level, x0 + x, y0 + y, z0 + z, Blocks.STONE.defaultBlockState());
            if (height > 0 && (x + z) % 4 == 0) set(level, x0 + x, y0 + height - 1, z0 + z, Blocks.GRAVEL.defaultBlockState());
        }
        // Pond with a sand edge.
        fill(level, x0 + 1, y0 - 1, z0 + 7, x0 + 5, y0 - 1, z0 + 12, Blocks.SAND.defaultBlockState());
        fill(level, x0 + 2, y0 - 2, z0 + 8, x0 + 4, y0 - 1, z0 + 11, Blocks.WATER.defaultBlockState());
        // Plank wall with a door: built blocks keep straight edges next to rounded ground.
        fill(level, x0 + 1, y0, z0 + 1, x0 + 6, y0 + 2, z0 + 1, Blocks.OAK_PLANKS.defaultBlockState());
        BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.WEST);
        level.setBlock(new BlockPos(x0 + 3, y0, z0 + 1), door, Block.UPDATE_CLIENTS);
        level.setBlock(new BlockPos(x0 + 3, y0 + 1, z0 + 1), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
        set(level, x0 + 7, y0, z0 + 5, Blocks.TORCH.defaultBlockState());
        for (int i = 0; i < 14; i++) {
            int x = x0 - 3 + Math.floorMod(i * 5, 11), z = z0 + 3 + Math.floorMod(i * 7, 12);
            if (level.getBlockState(new BlockPos(x, y0 - 1, z)).is(Blocks.GRASS_BLOCK)) {
                set(level, x, y0, z, (i % 3 == 0 ? Blocks.POPPY : Blocks.SHORT_GRASS).defaultBlockState());
            }
        }
        // A tree: log column and a leaf blob.
        int tx = x0 - 3, tz = z0 + 14;
        for (int y = 0; y < 5; y++) set(level, tx, y0 + y, tz, Blocks.OAK_LOG.defaultBlockState());
        for (int x = -2; x <= 2; x++) for (int y = 3; y <= 6; y++) for (int z = -2; z <= 2; z++) {
            if (x * x + (y - 4.5) * (y - 4.5) + z * z <= 6.5 && !(x == 0 && z == 0 && y < 5)) {
                set(level, tx + x, y0 + y, tz + z, Blocks.OAK_LEAVES.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true));
            }
        }

        // Cave: solid rock with a winding, uneven tunnel and two torches, well below the surface.
        int cy = Math.max(level.getMinY() + 10, y0 - 40);
        fill(level, x0 - 2, cy - 2, z0 - 2, x0 + 18, cy + 7, z0 + 10, Blocks.STONE.defaultBlockState());
        for (int x = 0; x <= 16; x++) {
            int centre = 4 + (int) Math.round(Math.sin(x * 0.45) * 1.5);
            int half = 1 + Math.floorMod(x * 5, 3) / 2;
            int height = 3 + Math.floorMod(x * 3, 3) / 2;
            fill(level, x0 + x, cy, z0 + centre - half, x0 + x, cy + height, z0 + centre + half, Blocks.AIR.defaultBlockState());
        }
        fill(level, x0 + 9, cy, z0 + 6, x0 + 12, cy + 2, z0 + 8, Blocks.AIR.defaultBlockState());
        set(level, x0 + 6, cy, z0 + 3, Blocks.TORCH.defaultBlockState());
        set(level, x0 + 13, cy, z0 + 5, Blocks.TORCH.defaultBlockState());

        Vec3 scene = new Vec3(x0 + 8, y0 + 1, z0 + 7);
        return List.of(
                new Shot("scene", 6000, new Vec3(x0 - 5, y0 + 3.5, z0 + 3), scene),
                new Shot("slope", 6000, new Vec3(x0 + 5.5, y0 + 1.8, z0 + 9.5), new Vec3(x0 + 12, y0 + 2, z0 + 6)),
                new Shot("door_pond", 6000, new Vec3(x0 + 0.5, y0 + 1.7, z0 + 7.5), new Vec3(x0 + 3.5, y0 + 0.5, z0 + 2)),
                new Shot("landscape", 6000, new Vec3(spawn.getX() - 24, surface(level, spawn.getX() - 24, spawn.getZ() - 24) + 9,
                        spawn.getZ() - 24), new Vec3(spawn.getX() + 30, y0, spawn.getZ() + 30)),
                new Shot("night", 18000, new Vec3(x0 + 2.5, y0 + 1.7, z0 + 3.5), new Vec3(x0 + 9, y0 + 1, z0 + 7)),
                new Shot("cave", 6000, new Vec3(x0 + 1.5, cy + 1.7, z0 + 4.5), new Vec3(x0 + 12, cy + 1, z0 + 5)));
    }

    private static int surface(ServerLevel level, int x, int z) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
    }

    private static void fill(ServerLevel level, int x1, int y1, int z1, int x2, int y2, int z2, BlockState state) {
        for (BlockPos pos : BlockPos.betweenClosed(x1, y1, z1, x2, y2, z2)) level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    }

    private static void set(ServerLevel level, int x, int y, int z, BlockState state) {
        level.setBlock(new BlockPos(x, y, z), state, Block.UPDATE_ALL);
    }
}
