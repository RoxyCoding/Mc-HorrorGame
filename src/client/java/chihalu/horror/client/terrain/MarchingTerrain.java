package chihalu.horror.client.terrain;

import chihalu.horror.Horror;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Smooth marching cubes terrain: Minecraft blocks -> density field -> marching cubes -> triangle mesh
 * -> own draw pass. The blocks stay in the world; only their chunk-mesh cubes are left out while the
 * surface replaces them. F9 cycles vanilla / marching cubes / wireframe.
 */
public final class MarchingTerrain {
    private static volatile TerrainRenderMode mode = TerrainRenderMode.MARCHING_CUBES;

    public static TerrainRenderMode mode() {
        return mode;
    }

    /** Section compiles build the surface and leave the terrain blocks out of the chunk mesh. */
    public static boolean replacesBlocks() {
        return mode.replacesBlocks();
    }

    public static void initialize() {
        TerrainRenderer.registerPipelines();
        KeyMapping cycle = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.horror.terrain",
                InputConstants.KEY_F9, KeyMapping.Category.register(Horror.id("terrain"))));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            TerrainRenderer.releasePending();
            while (cycle.consumeClick()) {
                if (client.level == null || client.gui.screen() != null) continue;
                TerrainRenderMode next = mode.next();
                boolean skipped = next == TerrainRenderMode.WIREFRAME && !RenderSystem.isWireframeAvailable();
                if (skipped) next = next.next();
                setMode(client, next);
                client.gui.hud.setOverlayMessage(Component.translatable(skipped
                        ? "message.horror.terrain.no_wireframe" : "message.horror.terrain", next.label()), false);
            }
        });
        // Wrap only the terrain blocks' models; the wrapper hides them in chunk meshes while the surface is on.
        ModelLoadingPlugin.register(context -> context.modifyBlockModelAfterBake().register((model, ctx) ->
                TerrainBlocks.isTerrain(ctx.state()) ? new HiddenTerrainModel(model) : model));
    }

    /**
     * Switching between vanilla and the surface rebuilds every chunk mesh (blocks in or out);
     * switching between the surface and its wireframe only changes the draw pipeline.
     */
    public static void setMode(Minecraft client, TerrainRenderMode next) {
        TerrainRenderMode previous = mode;
        mode = next;
        if (previous.replacesBlocks() != next.replacesBlocks() && client.level != null) client.levelExtractor.allChanged();
    }

    private MarchingTerrain() { }
}
