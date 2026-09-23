package chihalu.horror;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Held flashlight. The server keeps one invisible light block in the air in front of whatever the
 * player is looking at, and moves it as they look around. Glow blocks remove themselves once no
 * flashlight claims them, so a crash or unload never leaves stray light in the world.
 */
public final class Flashlight {
    private static final int RANGE = 24;
    private static final int SPOT_LIGHT = 13;
    private static final int GLOW_CHECK_TICKS = 40;

    public static final Block GLOW = registerGlow();
    public static final Item ITEM = registerItem();

    private static final Set<UUID> SWITCHED_OFF = new HashSet<>();
    private static Map<GlobalPos, Integer> lit = new HashMap<>();
    private static int ticks;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(Flashlight::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            update(server, Map.of());
            SWITCHED_OFF.clear();
        });
    }

    public static boolean isHolding(Player player) {
        return player.getMainHandItem().getItem() == ITEM || player.getOffhandItem().getItem() == ITEM;
    }

    private static boolean isActive(ServerLevel level, BlockPos pos) {
        return lit.containsKey(GlobalPos.of(level.dimension(), pos));
    }

    private static void tick(MinecraftServer server) {
        if (++ticks % 2 != 0) return;
        Map<GlobalPos, Integer> wanted = new HashMap<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator() || SWITCHED_OFF.contains(player.getUUID()) || !isHolding(player)) continue;
            if (player.pick(RANGE, 1.0F, false) instanceof BlockHitResult hit && hit.getType() != HitResult.Type.MISS) {
                BlockPos spot = hit.getBlockPos().relative(hit.getDirection());
                wanted.merge(GlobalPos.of(player.level().dimension(), spot.immutable()), SPOT_LIGHT, Math::max);
            }
        }
        update(server, wanted);
    }

    /** Removes glow no longer wanted, then places or adjusts the rest; only ever replaces air. */
    private static void update(MinecraftServer server, Map<GlobalPos, Integer> wanted) {
        for (GlobalPos old : lit.keySet()) {
            if (wanted.containsKey(old)) continue;
            ServerLevel level = server.getLevel(old.dimension());
            if (level != null && level.isLoaded(old.pos()) && level.getBlockState(old.pos()).getBlock() == GLOW) {
                level.removeBlock(old.pos(), false);
            }
        }
        Map<GlobalPos, Integer> placed = new HashMap<>();
        wanted.forEach((target, brightness) -> {
            ServerLevel level = server.getLevel(target.dimension());
            if (level == null || !level.isLoaded(target.pos())) return;
            BlockState current = level.getBlockState(target.pos());
            BlockState glow = GLOW.defaultBlockState().setValue(Glow.LEVEL, brightness);
            if (current.getBlock() == GLOW) {
                if (current.getValue(Glow.LEVEL) != brightness) level.setBlock(target.pos(), glow, Block.UPDATE_ALL);
            } else if (current.isAir()) {
                level.setBlock(target.pos(), glow, Block.UPDATE_ALL);
            } else {
                return;
            }
            placed.put(target, brightness);
        });
        lit = placed;
    }

    private static Block registerGlow() {
        var id = Horror.id("flashlight_glow");
        return Registry.register(BuiltInRegistries.BLOCK, id, new Glow(BlockBehaviour.Properties.of()
                .setId(ResourceKey.create(Registries.BLOCK, id))
                .replaceable().noCollision().noLootTable().noTerrainParticles()
                .pushReaction(PushReaction.POPPED).lightLevel(s -> s.getValue(Glow.LEVEL))));
    }

    private static Item registerItem() {
        var id = Horror.id("flashlight");
        return Registry.register(BuiltInRegistries.ITEM, id, new FlashlightItem(new Item.Properties()
                .setId(ResourceKey.create(Registries.ITEM, id)).stacksTo(1)));
    }

    private static final class FlashlightItem extends Item {
        private FlashlightItem(Item.Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if (!level.isClientSide()) {
                // Removing succeeds only when it was off, so that toggles it on.
                boolean on = SWITCHED_OFF.remove(player.getUUID());
                if (!on) SWITCHED_OFF.add(player.getUUID());
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        on ? SoundEvents.STONE_BUTTON_CLICK_ON : SoundEvents.STONE_BUTTON_CLICK_OFF,
                        SoundSource.PLAYERS, 0.4F, on ? 1.4F : 1.1F);
            }
            return InteractionResult.SUCCESS;
        }
    }

    private static final class Glow extends Block {
        static final IntegerProperty LEVEL = IntegerProperty.create("level", 0, 15);

        private Glow(BlockBehaviour.Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(LEVEL, SPOT_LIGHT));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(LEVEL);
        }

        @Override
        protected RenderShape getRenderShape(BlockState state) {
            return RenderShape.INVISIBLE;
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return Shapes.empty();
        }

        @Override
        protected boolean propagatesSkylightDown(BlockState state) {
            return true;
        }

        @Override
        protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
            if (!level.isClientSide() && old.getBlock() != this) level.scheduleTick(pos, this, GLOW_CHECK_TICKS);
        }

        @Override
        protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            if (isActive(level, pos)) {
                level.scheduleTick(pos, this, GLOW_CHECK_TICKS);
            } else {
                level.removeBlock(pos, false);
            }
        }
    }
}
