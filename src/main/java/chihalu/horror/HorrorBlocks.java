package chihalu.horror;

import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class HorrorBlocks {
    public static final Block CONCRETE = register("concrete", Block::new, base());
    public static final Block FLOOR = register("floor", Block::new, base());
    public static final Block PIPE = register("pipe", p -> new DetailBlock(p,
            Block.box(1, 9, 0, 5, 13, 16)), base().noOcclusion().sound(SoundType.METAL));
    public static final Block LAMP = register("ceiling_light", CeilingLight::new, base().noOcclusion()
            .lightLevel(s -> s.getValue(CeilingLight.LIT) ? 13 : 0).sound(SoundType.METAL));
    public static final Block SKIRTING = register("skirting", p -> new DetailBlock(p,
            Block.box(0, 0, 0, 0.75, 2, 16)), base().noOcclusion());
    public static final Block DOOR = register("service_door", ServiceDoor::new,
            base().noOcclusion().sound(SoundType.METAL));

    private static BlockBehaviour.Properties base() {
        return BlockBehaviour.Properties.of().strength(2.5F).sound(SoundType.STONE).noLootTable();
    }

    private static Block register(String name, Function<BlockBehaviour.Properties, Block> factory,
                                  BlockBehaviour.Properties properties) {
        var id = Horror.id(name);
        Block block = Registry.register(BuiltInRegistries.BLOCK, id,
                factory.apply(properties.setId(ResourceKey.create(Registries.BLOCK, id))));
        Registry.register(BuiltInRegistries.ITEM, id, new BlockItem(block,
                new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id)).useBlockDescriptionPrefix()));
        return block;
    }

    public static void initialize() { }

    private static final class ServiceDoor extends DoorBlock {
        private ServiceDoor(BlockBehaviour.Properties properties) {
            super(BlockSetType.COPPER, properties);
        }
    }

    public static final class CeilingLight extends DetailBlock {
        public static final BooleanProperty LIT = BlockStateProperties.LIT;
        private CeilingLight(BlockBehaviour.Properties properties) {
            super(properties, Block.box(4, 14, 0, 12, 16, 16));
            registerDefaultState(stateDefinition.any().setValue(LIT, true));
        }
        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(LIT);
        }
    }

    private static class DetailBlock extends Block {
        private final VoxelShape shape;
        private DetailBlock(BlockBehaviour.Properties properties, VoxelShape shape) {
            super(properties);
            this.shape = shape;
        }
        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return shape;
        }
    }
}
