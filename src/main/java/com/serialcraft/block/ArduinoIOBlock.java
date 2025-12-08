package com.serialcraft.block;

import com.mojang.serialization.MapCodec;
import com.serialcraft.SerialCraft;
import com.serialcraft.block.entity.ArduinoIOBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
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
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED; // Señal Serial (Virtual)
    public static final BooleanProperty ENABLED = BooleanProperty.create("enabled"); // Señal Física (Cables)
    public static final BooleanProperty BLINKING = BooleanProperty.create("blinking");
    public static final IntegerProperty MODE = IntegerProperty.create("mode", 0, 2);

    public static final EnumProperty<IOSide> NORTH = EnumProperty.create("north", IOSide.class);
    public static final EnumProperty<IOSide> SOUTH = EnumProperty.create("south", IOSide.class);
    public static final EnumProperty<IOSide> EAST = EnumProperty.create("east", IOSide.class);
    public static final EnumProperty<IOSide> WEST = EnumProperty.create("west", IOSide.class);
    public static final EnumProperty<IOSide> UP = EnumProperty.create("up", IOSide.class);
    public static final EnumProperty<IOSide> DOWN = EnumProperty.create("down", IOSide.class);

    private static final VoxelShape SHAPE_BASE = Shapes.or(
            Block.box(0, 0, 0, 16, 2, 16),
            Block.box(7, 2, 0, 9, 6, 2.5),
            Block.box(7, 2, 13.5, 9, 6, 16),
            Block.box(13.6, 2, 7, 16, 6, 9),
            Block.box(0, 2, 7, 2.5, 6, 9)
    );

    private static final AABB BTN_NORTE = new AABB(7/16d, 2/16d, 0/16d, 9/16d, 6/16d, 2.475/16d);
    private static final AABB BTN_SUR   = new AABB(7/16d, 2/16d, 13.575/16d, 9/16d, 6/16d, 16/16d);
    private static final AABB BTN_ESTE  = new AABB(13.6/16d, 2/16d, 7/16d, 16/16d, 6/16d, 9/16d);
    private static final AABB BTN_OESTE = new AABB(0/16d, 2/16d, 7/16d, 2.45/16d, 6/16d, 9/16d);
    private static final AABB BTN_DOWN  = new AABB(6.6/16d, 2/16d, 11/16d, 9.45/16d, 4/16d, 13/16d);

    public ArduinoIOBlock(Properties settings) {
        super(settings);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(POWERED, false)
                .setValue(ENABLED, false)
                .setValue(BLINKING, false)
                .setValue(MODE, 0)
                .setValue(NORTH, IOSide.NONE).setValue(SOUTH, IOSide.NONE)
                .setValue(EAST, IOSide.NONE).setValue(WEST, IOSide.NONE)
                .setValue(UP, IOSide.NONE).setValue(DOWN, IOSide.NONE));
    }

    public Direction getHitButton(Vec3 localHit) {
        double margin = 0.03;
        if (BTN_NORTE.inflate(margin).contains(localHit)) return Direction.NORTH;
        if (BTN_SUR.inflate(margin).contains(localHit))   return Direction.SOUTH;
        if (BTN_ESTE.inflate(margin).contains(localHit))  return Direction.EAST;
        if (BTN_OESTE.inflate(margin).contains(localHit)) return Direction.WEST;
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

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && placer instanceof Player player) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ArduinoIOBlockEntity ioEntity) {
                ioEntity.setOwner(player.getUUID());
                player.displayClientMessage(Component.literal("§7[SerialCraft] Placa vinculada a: " + player.getName().getString()), true);
            }
        }
    }

    @Override
    public boolean isSignalSource(BlockState state) { return true; }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        // Obtenemos el lado de la placa que está mirando hacia el vecino que pide energía
        Direction side = direction.getOpposite();
        EnumProperty<IOSide> property = getPropertyForDirection(side);
        IOSide ioState = state.getValue(property);

        // Solo emitimos si ese lado está configurado como SALIDA (Rojo)
        if (ioState == IOSide.OUTPUT) {
            int powerLevel = 15;
            if (level.getBlockEntity(pos) instanceof ArduinoIOBlockEntity io) {
                powerLevel = io.signalStrength;
            }

            int currentMode = state.getValue(MODE);
            boolean physicalPower = state.getValue(ENABLED); // Energía que entra por los verdes
            boolean virtualPower = state.getValue(POWERED);  // Señal del Arduino

            // Lógica de Emisión
            switch (currentMode) {
                case 0: // SALIDA (Puente)
                    // Actúa como un cable: Si entra energía (ENABLED), sale energía.
                    return physicalPower ? powerLevel : 0;

                case 1: // ENTRADA (Controlado por Arduino)
                    // Solo obedece al Arduino.
                    return virtualPower ? powerLevel : 0;

                case 2: // HÍBRIDO (OR Gate)
                    // Emite si (Entra energía física) O (Arduino dice que emita).
                    return (physicalPower || virtualPower) ? powerLevel : 0;
            }
        }
        return 0;
    }

    public static EnumProperty<IOSide> getPropertyForDirection(Direction dir) {
        return switch (dir) {
            case NORTH -> NORTH;
            case SOUTH -> SOUTH;
            case EAST  -> EAST;
            case WEST  -> WEST;
            case UP    -> UP;
            case DOWN  -> DOWN;
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED, ENABLED, BLINKING, MODE, NORTH, SOUTH, EAST, WEST, UP, DOWN);
    }

    @NotNull @Override public MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new ArduinoIOBlockEntity(pos, state); }
    @NotNull @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE_BASE; }

    @Override public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {}

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