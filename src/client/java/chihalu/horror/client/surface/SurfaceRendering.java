package chihalu.horror.client.surface;

import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.network.chat.Component;
import chihalu.horror.Horror;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Owns the "horror visuals" built-in resource pack (material, fog, sky, colour grading) and the mesh
 * rounding. Both follow the pack's selection, so the resource pack screen and F8 agree.
 */
public final class SurfaceRendering {
    public static final Identifier PACK = Horror.id("horror_visuals");
    public static volatile boolean enabled = true;

    public static void initialize() {
        ResourceLoader.registerBuiltinPack(PACK, FabricLoader.getInstance().getModContainer(Horror.MOD_ID).orElseThrow(),
                Component.translatable("pack.horror.visuals"), PackActivationType.DEFAULT_ENABLED);
        KeyMapping toggle = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.horror.surface",
                InputConstants.KEY_F8, KeyMapping.Category.register(Horror.id("visuals"))));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggle.consumeClick()) {
                if (client.level == null || client.gui.screen() != null) continue;
                boolean on = setEnabled(client, !enabled);
                client.gui.hud.setOverlayMessage(Component.translatable(on
                        ? "message.horror.surface_on" : "message.horror.surface_off"), false);
            }
        });
        ModelLoadingPlugin.register(context -> {
            // Runs on every resource reload, after the pack list is final.
            enabled = packSelected(Minecraft.getInstance());
            // A new set for each resource reload; models are baked concurrently.
            Set<BlockState> surfaces = ConcurrentHashMap.newKeySet();
            context.modifyBlockModelAfterBake().register((model, ctx) -> {
                var state = ctx.state();
                boolean leaves = state.getBlock() instanceof LeavesBlock;
                if (!(state.isSolidRender() || leaves) || state.hasBlockEntity() || !state.getFluidState().isEmpty()
                        || !state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)
                        || !isCube(model)) return model;
                surfaces.add(state);
                return new ContinuousSurfaceModel(model, surfaces);
            });
        });
    }

    /** Selects or removes the pack and reloads resources; the reload rebuilds all chunk meshes. */
    public static boolean setEnabled(Minecraft client, boolean on) {
        return reload(client, on) != null ? on : enabled;
    }

    /** As {@link #setEnabled}, returning the reload to wait for, or null when there is no pack. */
    public static CompletableFuture<Void> reload(Minecraft client, boolean on) {
        PackRepository packs = client.getResourcePackRepository();
        String id = packId(packs);
        if (id == null) return null;
        boolean changed = on ? packs.addPack(id) : packs.removePack(id);
        return changed ? client.reloadResourcePacks() : CompletableFuture.completedFuture(null);
    }

    private static boolean packSelected(Minecraft client) {
        PackRepository packs = client.getResourcePackRepository();
        String id = packId(packs);
        return id != null && packs.getSelectedIds().contains(id);
    }

    private static String packId(PackRepository packs) {
        for (String id : packs.getAvailableIds()) if (id.endsWith(PACK.getPath())) return id;
        return null;
    }

    private static boolean isCube(BlockStateModel model) {
        // Only vanilla model implementations: do not override another mod's dynamic renderer.
        if (!model.getClass().getName().startsWith("net.minecraft.")) return false;
        var parts = new ArrayList<BlockStateModelPart>();
        model.collectParts(RandomSource.create(42), parts);
        if (parts.isEmpty()) return false;
        int faceMask = 0;
        for (var part : parts) for (int face = 0; face <= 6; face++) {
            Direction side = face == 6 ? null : Direction.values()[face];
            for (var quad : part.getQuads(side)) {
                Direction normal = quad.direction();
                int vertices = 0;
                for (int i = 0; i < 4; i++) {
                    var p = quad.position(i);
                    if (!ContinuousSurfaceModel.endpoint(p.x()) || !ContinuousSurfaceModel.endpoint(p.y())
                            || !ContinuousSurfaceModel.endpoint(p.z())) return false;
                    float depth = switch (normal.getAxis()) { case X -> p.x(); case Y -> p.y(); case Z -> p.z(); };
                    float expected = normal.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : 0;
                    if (Math.abs(depth - expected) > .00001F) return false;
                    vertices |= 1 << (Math.round(p.x()) | Math.round(p.y()) << 1 | Math.round(p.z()) << 2);
                }
                if (Integer.bitCount(vertices) != 4) return false;
                faceMask |= 1 << normal.ordinal();
            }
        }
        return faceMask == 63;
    }

    private SurfaceRendering() { }
}
