package chihalu.horror;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

/** Small sealed scene; only builds after the entire destination volume has been checked. */
public final class CorridorDemo {
    private record ReturnPoint(ResourceKey<Level> dimension, Vec3 position, float yaw, float pitch) { }
    private static final Map<UUID, ReturnPoint> RETURNS = new HashMap<>();

    public static void register() {
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> RETURNS.clear());
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) ->
            dispatcher.register(Commands.literal("horror")
                .requires(s -> s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .then(Commands.literal("demo").executes(c -> build(c.getSource())))
                .then(Commands.literal("return").executes(c -> goBack(c.getSource())))));
    }

    private static int build(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        var level = source.getLevel();
        if (RETURNS.containsKey(player.getUUID())) {
            source.sendFailure(Component.literal("先に /horror return で戻ってください。"));
            return 0;
        }
        int y = Math.max(level.getMinY() + 1, Math.min(player.blockPosition().getY() + 32, level.getMaxY() - 7));
        BlockPos origin = new BlockPos(player.blockPosition().getX(), y, player.blockPosition().getZ());
        for (BlockPos pos : BlockPos.betweenClosed(origin, origin.offset(4, 4, 18))) {
            if (!level.getWorldBorder().isWithinBounds(pos) || !level.getBlockState(pos).isAir()) {
                source.sendFailure(Component.literal("生成範囲にブロックまたはワールド境界があります。開けた場所に移動してください。"));
                return 0;
            }
        }
        RETURNS.put(player.getUUID(), new ReturnPoint(level.dimension(), player.position(), player.getYRot(), player.getXRot()));
        for (int x = 0; x <= 4; x++) for (int z = 0; z <= 18; z++) {
            put(level, origin.offset(x, 0, z), HorrorBlocks.FLOOR);
            put(level, origin.offset(x, 4, z), HorrorBlocks.CONCRETE);
            for (int h = 1; h <= 3; h++) {
                if (x == 0 || x == 4 || z == 0 || z == 18 || (z == 13 && (x != 2 || h == 3))) {
                    put(level, origin.offset(x, h, z), HorrorBlocks.CONCRETE);
                }
            }
        }
        for (int z = 1; z < 18; z++) {
            if (z == 13) continue;
            put(level, origin.offset(1, 3, z), HorrorBlocks.PIPE);
            put(level, origin.offset(1, 1, z), HorrorBlocks.SKIRTING);
        }
        // The power is out: fixtures stay dark so the flashlight is the only light.
        var deadLamp = HorrorBlocks.LAMP.defaultBlockState().setValue(HorrorBlocks.CeilingLight.LIT, false);
        for (int z : new int[]{3, 4, 9, 10, 16}) level.setBlock(origin.offset(2, 3, z), deadLamp, Block.UPDATE_ALL);
        var door = HorrorBlocks.DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH);
        // Place both halves without neighbour updates between them.
        level.setBlock(origin.offset(2, 1, 13), door, Block.UPDATE_CLIENTS);
        level.setBlock(origin.offset(2, 2, 13), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
        if (!player.teleportTo(level, origin.getX() + 2.5, y + 1, origin.getZ() + 2.5, Set.of(), 0, 0, true)) {
            RETURNS.remove(player.getUUID());
            source.sendFailure(Component.literal("廊下は生成されましたが、移動に失敗しました。生成位置: "
                    + origin.toShortString()));
            return 0;
        }
        if (!player.getInventory().contains(s -> s.getItem() == Flashlight.ITEM)) {
            player.getInventory().add(new ItemStack(Flashlight.ITEM));
        }
        source.sendSuccess(() -> Component.literal("試作廊下を生成しました。懐中電灯を手に持つと照らせます（右クリックでオン/オフ）。"
                + "F1でHUD非表示。ドアは右クリック。戻る: /horror return（サーバー終了前に実行）"), false);
        return 1;
    }

    private static void put(net.minecraft.server.level.ServerLevel level, BlockPos pos, Block block) {
        level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_ALL);
    }

    private static int goBack(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ReturnPoint point = RETURNS.get(player.getUUID());
        if (point == null) {
            source.sendFailure(Component.literal("このサーバー起動中の戻り先がありません。"));
            return 0;
        }
        var level = source.getServer().getLevel(point.dimension());
        if (level == null) {
            source.sendFailure(Component.literal("戻り先のディメンションが見つかりません。戻り先は保持しています。"));
            return 0;
        }
        if (!player.teleportTo(level, point.position().x, point.position().y, point.position().z,
                Set.of(), point.yaw(), point.pitch(), true)) {
            source.sendFailure(Component.literal("元の場所への移動に失敗しました。/horror return で再試行できます。"));
            return 0;
        }
        RETURNS.remove(player.getUUID());
        source.sendSuccess(() -> Component.literal("元の場所に戻りました。試作廊下はワールド内に残ります。"), false);
        return 1;
    }
}
