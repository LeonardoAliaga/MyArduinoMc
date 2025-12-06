package com.serialcraft.block;

import com.mojang.serialization.MapCodec;
import com.serialcraft.SerialCraft;
import com.serialcraft.block.entity.ArduinoIOBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ArduinoIOBlock extends BaseEntityBlock {

    public static final MapCodec<ArduinoIOBlock> CODEC = simpleCodec(ArduinoIOBlock::new);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final IntegerProperty MODE = IntegerProperty.create("mode", 0, 2);

    public static final BooleanProperty NORTH = BooleanProperty.create("north");
    public static final BooleanProperty SOUTH = BooleanProperty.create("south");
    public static final BooleanProperty EAST = BooleanProperty.create("east");
    public static final BooleanProperty WEST = BooleanProperty.create("west");
    public static final BooleanProperty UP = BooleanProperty.create("up");
    public static final BooleanProperty DOWN = BooleanProperty.create("down");

    // Hitboxes Físicas
    private static final VoxelShape SHAPE_BASE = Shapes.or(
            Block.box(0, 0, 0, 16, 2, 16),
            Block.box(7, 2, 2, 9, 7, 4), Block.box(7, 2, 12, 9, 7, 14),
            Block.box(12, 2, 7, 14, 7, 9), Block.box(2, 2, 7, 4, 7, 9),
            Block.box(7, 2, 8, 9, 6, 10), Block.box(7, 2, 6, 9, 5, 8)
    );

    // Hitboxes Lógicas (Botones)
    private static final AABB BTN_NORTE = new AABB(7/16d, 2/16d, 2/16d, 9/16d, 7/16d, 4/16d);
    private static final AABB BTN_SUR   = new AABB(7/16d, 2/16d, 12/16d, 9/16d, 7/16d, 14/16d);
    private static final AABB BTN_ESTE  = new AABB(12/16d, 2/16d, 7/16d, 14/16d, 7/16d, 9/16d);
    private static final AABB BTN_OESTE = new AABB(2/16d, 2/16d, 7/16d, 4/16d, 7/16d, 9/16d);
    private static final AABB BTN_UP    = new AABB(7/16d, 2/16d, 8/16d, 9/16d, 6/16d, 10/16d);
    private static final AABB BTN_DOWN  = new AABB(7/16d, 2/16d, 6/16d, 9/16d, 5/16d, 8/16d);

    public ArduinoIOBlock(Properties settings) {
        super(settings);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(POWERED, false).setValue(MODE, 0)
                .setValue(NORTH, false).setValue(SOUTH, false)
                .setValue(EAST, false).setValue(WEST, false)
                .setValue(UP, false).setValue(DOWN, false));
    }

    // --- DETECCIÓN "GENEROSA" DE CLICS ---
    public Direction getHitButton(Vec3 localHit) {
        // CORRECCIÓN: Inflamos las cajas 0.05 (casi 1 pixel extra) para asegurar detección
        double margin = 0.05;

        if (BTN_NORTE.inflate(margin).contains(localHit)) return Direction.NORTH;
        if (BTN_SUR.inflate(margin).contains(localHit))   return Direction.SOUTH;
        if (BTN_ESTE.inflate(margin).contains(localHit))  return Direction.EAST;
        if (BTN_OESTE.inflate(margin).contains(localHit)) return Direction.WEST;
        if (BTN_UP.inflate(margin).contains(localHit))    return Direction.UP;
        if (BTN_DOWN.inflate(margin).contains(localHit))  return Direction.DOWN;
        return null;
    }

    @NotNull
    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof ArduinoIOBlockEntity io)) return InteractionResult.FAIL;

        Vec3 hitPos = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        Direction btn = getHitButton(hitPos);

        if (btn != null) {
            io.onButtonInteract(player, btn, player.isShiftKeyDown());
        } else {
            io.onPlayerInteract(player);
        }
        return InteractionResult.SUCCESS;
    }

    // --- RESTO DEL CÓDIGO (Redstone, etc) ---
    @Override
    public boolean isSignalSource(BlockState state) { return true; }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        if (!state.getValue(POWERED)) return 0;
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof ArduinoIOBlockEntity io) {
            // Filtramos la salida según el modo
            if (io.ioMode != 0) { // Input
                if (io.activeDirections.isEmpty()) return 15;
                return io.activeDirections.contains(direction) ? 15 : 0;
            } else { // Output
                return io.passThroughDirections.contains(direction) ? 15 : 0;
            }
        }
        return 0;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED, MODE, NORTH, SOUTH, EAST, WEST, UP, DOWN);
    }

    // ... (Métodos estándar: codec, newBlockEntity, renderShape, tick, playerWillDestroy, getTicker) ...
    // Asegúrate de que estén igual que en el archivo anterior.
    @NotNull @Override public MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new ArduinoIOBlockEntity(pos, state); }
    @NotNull @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE_BASE; }
    @Override public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(POWERED)) {
            level.setBlock(pos, state.setValue(POWERED, false), 3);
            level.updateNeighborsAt(pos, this);
        }
    }
    @Override public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()) {
            if (level.getBlockEntity(pos) instanceof ArduinoIOBlockEntity io) SerialCraft.activeIOBlocks.remove(io);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
    @Nullable @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type == com.serialcraft.block.entity.ModBlockEntities.IO_BLOCK_ENTITY) {
            return (lvl, p, st, be) -> { if (!lvl.isClientSide() && be instanceof ArduinoIOBlockEntity io) io.tickServer(); };
        }
        return null;
    }
}