package net.skds.wpo.fluidphysics;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.event.level.PistonEvent;
import net.minecraftforge.eventbus.api.Event;
import net.skds.wpo.WPOConfig;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static net.skds.wpo.fluidphysics.FFluidStatic.*;

public class PistonDisplacer implements IFluidActionIteratable {

    int mfl = WPOConfig.MAX_FLUID_LEVEL;
    int sl;
    boolean complete = false;
    Level world;
    Random random;
    Fluid fluid;
    Set<BlockPos> movepos = new HashSet<>();
    PistonEvent.Pre event;
    Long2ObjectLinkedOpenHashMap<BlockState> states = new Long2ObjectLinkedOpenHashMap<>();
    BlockState obs;

    PistonDisplacer(Level w, PistonEvent.Pre e, BlockState os, PistonStructureResolver ps) {
        this.obs = os;
        FluidState ofs = obs.getFluidState();
        this.fluid = ofs.getType();
        this.sl = ofs.getAmount();
        this.world = w;
        this.random = new Random();
        this.event = e;
        movepos.addAll(ps.getToDestroy());
        movepos.addAll(ps.getToPush());
        for (BlockPos p : ps.getToPush()) {
            movepos.add(p.relative(event.getDirection()));
        }
    }

    @Override
    public boolean isComplete() {
        return complete;
    }

    @Override
    public void addZero(Set<BlockPos> set, BlockPos p0) {
        for (Direction d : getRandomizedDirections(random, true)) {
            BlockPos pos2 = p0.relative(d);
            BlockState state2 = world.getBlockState(pos2);
            if (isValidState(state2) && canReach(p0, pos2, obs, state2, fluid, world)) {
                set.add(pos2);
            }
        }
    }

    @Override
    public void run(BlockPos pos, BlockState state) {
        if (canOnlyFullCube(state) && state.hasProperty(BlockStateProperties.WATERLOGGED) && !state.getValue(BlockStateProperties.WATERLOGGED)) {
            states.clear();
            states.put(pos.asLong(), getUpdatedState(state, mfl, fluid));
            complete = true;
            return;
        }
        FluidState fs = state.getFluidState();
        int el = mfl - fs.getAmount();
        int osl = sl;
        sl -= el;
        int nl = mfl;
        if (sl <= 0) {
            nl = mfl + sl;
            complete = true;
        }
        if (osl != sl)
            states.put(pos.asLong(), getUpdatedState(state, nl, fluid));
    }

    @Override
    public Level getWorld() {
        return world;
    }

    @Override
    public boolean isValidState(BlockState state) {
        return fluid.isSame(state.getFluidState().getType()) || state.getFluidState().isEmpty();
    }

    @Override
    public boolean isValidPos(BlockPos pos) {
        return !movepos.contains(pos);
    }

    @Override
    public void finish() {
        fillStates(states, world);
        event.setResult(Event.Result.ALLOW);
    }

    @Override
    public void fail() {
        event.setCanceled(true);
    }
}