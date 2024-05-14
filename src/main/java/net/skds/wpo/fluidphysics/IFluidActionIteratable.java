package net.skds.wpo.fluidphysics;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;

public interface IFluidActionIteratable {
    default void addZero(Set<BlockPos> set, BlockPos p0) {
        set.add(p0);
    }

    boolean isComplete();

    void run(BlockPos pos, BlockState state);

    Level getWorld();

    boolean isValidState(BlockState state);

    default boolean isValidPos(BlockPos pos) {
        return true;
    }

    void finish();

    default void fail() {
    }
}
