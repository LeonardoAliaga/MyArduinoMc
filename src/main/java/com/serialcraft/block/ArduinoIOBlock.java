package com.serialcraft.block;

import com.mojang.serialization.MapCodec;
import com.serialcraft.SerialCraft;
import com.serialcraft.block.entity.ArduinoIOBlockEntity;
import com.serialcraft.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

import net.minecraft.world.entity.player.Player;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ArduinoIOBlock extends BaseEntityBlock {

    // Required codec for BaseEntityBlock (Mojang mappings 1.21.10)
    public static final MapCodec<ArduinoIOBlock> CODEC = simpleCodec(ArduinoIOBlock::new);

    @NotNull
    @Override
    public MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    public ArduinoIOBlock(Properties settings) {
        super(settings);
    }

    // ---------------------------------------------------------------------
    // BLOCK ENTITY INSTANCE
    // ---------------------------------------------------------------------

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ArduinoIOBlockEntity(pos, state);
    }

    @NotNull
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    // ---------------------------------------------------------------------
    // RIGHT-CLICK INTERACTION
    // ---------------------------------------------------------------------

    @NotNull
    @Override
    public InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hit
    ) {
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ArduinoIOBlockEntity io) {
                io.onPlayerInteract(player);
            }
        }

        return InteractionResult.SUCCESS;
    }

    @NotNull
    @Override
    public InteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit
    ) {
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ArduinoIOBlockEntity io) {
                io.onPlayerInteract(player);
            }
        }

        return InteractionResult.SUCCESS;
    }


    // ---------------------------------------------------------------------
    // WHEN THE BLOCK IS BROKEN
    // (This method **does** exist in Mojang mappings)
    // ---------------------------------------------------------------------

    @NotNull
    @Override
    public BlockState playerWillDestroy(
            @NotNull Level level,
            BlockPos pos,
            BlockState state,
            Player player
    ) {

        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ArduinoIOBlockEntity io) {
                SerialCraft.activeIOBlocks.remove(io);
            }
        }

        return super.playerWillDestroy(level, pos, state, player);
    }







    // ---------------------------------------------------------------------
    // SERVER TICKER
    // ---------------------------------------------------------------------

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type
    ) {

        if (type == ModBlockEntities.IO_BLOCK_ENTITY) {
            return (lvl, position, blockState, be) -> {
                if (!lvl.isClientSide() && be instanceof ArduinoIOBlockEntity io) {
                    io.tickServer();
                }
            };
        }

        return null;
    }
}
