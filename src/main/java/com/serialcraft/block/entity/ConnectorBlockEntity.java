package com.serialcraft.block.entity;

import com.serialcraft.block.ConnectorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class ConnectorBlockEntity extends BlockEntity {

    private boolean initialized = false;

    public ConnectorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CONNECTOR_BLOCK_ENTITY, pos, state);
    }

    // Este método se ejecutará en cada tick, pero usaremos 'initialized' para que solo actúe la primera vez.
    public static void tick(Level level, BlockPos pos, BlockState state, ConnectorBlockEntity entity) {
        if (!level.isClientSide() && !entity.initialized) {
            entity.initialized = true;
            // AL CARGAR: Si el bloque estaba encendido, lo apagamos forzosamente.
            // Esto corrige el bug visual al reentrar al mundo.
            if (state.getValue(ConnectorBlock.LIT)) {
                level.setBlock(pos, state.setValue(ConnectorBlock.LIT, false), 3);
            }
        }
    }
}