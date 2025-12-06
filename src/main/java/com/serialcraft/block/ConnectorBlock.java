package com.serialcraft.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

public class ConnectorBlock extends HorizontalDirectionalBlock {

    public static final MapCodec<ConnectorBlock> CODEC = simpleCodec(ConnectorBlock::new);
    public static final BooleanProperty LIT = BooleanProperty.create("lit");

    // ---------------------------------------------------------------------
    // HITBOXES EXACTAS (Basadas en connector_block.json)
    // ---------------------------------------------------------------------

    // El modelo tiene decimales (ej. 0.6), pero las hitboxes suelen redondearse
    // visualmente para que el jugador no se frustre.
    // He ajustado ligeramente los decimales de tu JSON a pixels de Minecraft (1/16).

    // NORTE (Pantalla al fondo, teclado al frente)
    private static final VoxelShape SHAPE_NORTH = Shapes.or(
            Block.box(1, 0, 1, 15, 1, 11),   // Base/Teclado
            Block.box(1, 1, 11, 15, 10, 12)  // Pantalla
    );

    // SUR (Pantalla al frente, teclado al fondo)
    private static final VoxelShape SHAPE_SOUTH = Shapes.or(
            Block.box(1, 0, 5, 15, 1, 15),   // Base
            Block.box(1, 1, 4, 15, 10, 5)    // Pantalla
    );

    // OESTE (Pantalla a la derecha, teclado a la izquierda)
    private static final VoxelShape SHAPE_WEST = Shapes.or(
            Block.box(1, 0, 1, 11, 1, 15),   // Base
            Block.box(11, 1, 1, 12, 10, 15)  // Pantalla
    );

    // ESTE (Pantalla a la izquierda, teclado a la derecha)
    private static final VoxelShape SHAPE_EAST = Shapes.or(
            Block.box(5, 0, 1, 15, 1, 15),   // Base
            Block.box(4, 1, 1, 5, 10, 15)    // Pantalla
    );

    public ConnectorBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(LIT, false));
    }

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