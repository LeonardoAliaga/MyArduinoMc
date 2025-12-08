package com.serialcraft.block;

import com.mojang.serialization.MapCodec;
import com.serialcraft.block.entity.ConnectorBlockEntity;
import com.serialcraft.block.entity.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock; // Importante
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// AÑADIDO: implements EntityBlock
public class ConnectorBlock extends HorizontalDirectionalBlock implements EntityBlock {

    public static final MapCodec<ConnectorBlock> CODEC = simpleCodec(ConnectorBlock::new);
    public static final BooleanProperty LIT = BooleanProperty.create("lit");

    // Hitboxes (Sin cambios)
    private static final VoxelShape SHAPE_NORTH = Shapes.or(Block.box(1, 0, 1, 15, 1, 11), Block.box(1, 1, 11, 15, 10, 12));
    private static final VoxelShape SHAPE_SOUTH = Shapes.or(Block.box(1, 0, 5, 15, 1, 15), Block.box(1, 1, 4, 15, 10, 5));
    private static final VoxelShape SHAPE_WEST = Shapes.or(Block.box(1, 0, 1, 11, 1, 15), Block.box(11, 1, 1, 12, 10, 15));
    private static final VoxelShape SHAPE_EAST = Shapes.or(Block.box(5, 0, 1, 15, 1, 15), Block.box(4, 1, 1, 5, 10, 15));

    public ConnectorBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(LIT, false));
    }

    // --- NUEVO: Métodos de EntityBlock ---

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ConnectorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        // Conectar el Ticker que apaga el bloque al cargar
        if (type == ModBlockEntities.CONNECTOR_BLOCK_ENTITY) {
            return (lvl, pos, st, be) -> ConnectorBlockEntity.tick(lvl, pos, st, (ConnectorBlockEntity) be);
        }
        return null;
    }

    // --- Fin Métodos Nuevos ---

    @Override
    public @NotNull VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case NORTH -> SHAPE_NORTH;
            case SOUTH -> SHAPE_SOUTH;
            case WEST -> SHAPE_WEST;
            case EAST -> SHAPE_EAST;
            default -> SHAPE_NORTH;
        };
    }

    @Override
    protected @NotNull MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return InteractionResult.SUCCESS;
    }
}