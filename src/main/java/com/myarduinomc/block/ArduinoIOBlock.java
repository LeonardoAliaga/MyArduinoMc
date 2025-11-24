package com.myarduinomc.block;

import com.myarduinomc.MyArduinoMc;
import com.myarduinomc.block.entity.ArduinoIOBlockEntity;
import com.myarduinomc.screen.IOScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class ArduinoIOBlock extends Block implements EntityBlock {
    public static final BooleanProperty LIT = BooleanProperty.create("lit");

    public ArduinoIOBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ArduinoIOBlockEntity(pos, state);
    }

    // --- TICKER (Importante para registrar el bloque) ---
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        // Solo tickeamos en el servidor para registrar la entidad
        return level.isClientSide() ? null : (lvl, pos, st, te) -> {
            if (te instanceof ArduinoIOBlockEntity myTe) myTe.tick();
        };
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(LIT) ? 15 : 0;
    }

    // Lógica OUTPUT (Minecraft -> Arduino)
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ArduinoIOBlockEntity entity) {
            if (entity.isOutputMode) {
                boolean hasPower = level.hasNeighborSignal(pos);
                if (hasPower && !state.getValue(LIT)) {
                    MyArduinoMc.enviarArduino(entity.targetData);
                    level.setBlock(pos, state.setValue(LIT, true), 3);
                } else if (!hasPower && state.getValue(LIT)) {
                    level.setBlock(pos, state.setValue(LIT, false), 3);
                }
            }
        }
    }

    // Apagar el bloque INPUT automáticamente
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(LIT)) {
            level.setBlock(pos, state.setValue(LIT, false), 3);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        // La GUI la abre el Client, aquí solo devolvemos éxito
        return InteractionResult.SUCCESS;
    }
}