package com.serialcraft.block;

import com.mojang.serialization.MapCodec;
import com.serialcraft.SerialCraft;
import com.serialcraft.block.entity.ModBlockEntities; // Asegúrate de tener este import
import com.serialcraft.block.entity.ArduinoIOBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ArduinoIOBlock extends BaseEntityBlock {

    public static final MapCodec<ArduinoIOBlock> CODEC = simpleCodec(ArduinoIOBlock::new);

    // NUEVO: Propiedad para saber si está encendido
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public ArduinoIOBlock(Properties settings) {
        super(settings);
        // Por defecto nace apagado
        this.registerDefaultState(this.stateDefinition.any().setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED);
    }

    // ---------------------------------------------------------------------
    // LÓGICA REDSTONE (Para que emita energía)
    // ---------------------------------------------------------------------

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        // Si POWERED es true, emite fuerza 15. Si no, 0.
        return state.getValue(POWERED) ? 15 : 0;
    }

    // Se ejecuta cuando programamos un tick (para apagar el pulso automáticamente)
    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(POWERED)) {
            // Apagar el bloque
            level.setBlock(pos, state.setValue(POWERED, false), 3);
            level.updateNeighborsAt(pos, this);
        }
    }

    // ---------------------------------------------------------------------
    // MÉTODOS ESTÁNDAR
    // ---------------------------------------------------------------------

    @NotNull
    @Override
    public MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ArduinoIOBlockEntity(pos, state);
    }

    @NotNull
    @Override
    public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @NotNull
    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ArduinoIOBlockEntity io) {
                io.onPlayerInteract(player);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public @NotNull BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ArduinoIOBlockEntity io) {
                SerialCraft.activeIOBlocks.remove(io);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        // CORRECCIÓN: Validamos el tipo y devolvemos el ticker para que tickServer() funcione
        if (type == ModBlockEntities.IO_BLOCK_ENTITY) {
            return (lvl, pos, st, be) -> {
                // Solo ejecutamos en el servidor
                if (!lvl.isClientSide() && be instanceof ArduinoIOBlockEntity io) {
                    io.tickServer();
                }
            };
        }
        return null;
    }
}