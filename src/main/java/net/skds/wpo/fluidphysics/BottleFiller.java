package net.skds.wpo.fluidphysics;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static net.skds.wpo.fluidphysics.FFluidStatic.*;

public class BottleFiller implements IFluidActionIteratable {

    int bucketLevels = 3;
    int sl = 0;
    boolean complete = false;
    Level world;
    ItemStack bottle;
    Fluid fluid;
    CallbackInfoReturnable<InteractionResultHolder<ItemStack>> ci;
    Long2ObjectLinkedOpenHashMap<BlockState> states = new Long2ObjectLinkedOpenHashMap<>();

    BottleFiller(Level w, Fluid f, CallbackInfoReturnable<InteractionResultHolder<ItemStack>> ci, ItemStack stack) {
        world = w;
        fluid = f;
        bottle = stack;
        this.ci = ci;
    }

    @Override
    public boolean isComplete() {
        return complete;
    }

    @Override
    public void run(BlockPos pos, BlockState state) {
        // world.addParticle(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 0.5,
        // pos.getZ() + 0.5, 0, 0, 0);

        if (canOnlyFullCube(state) && state.getValue(BlockStateProperties.WATERLOGGED)) {
            states.clear();
            states.put(pos.asLong(), getUpdatedState(state, 0, fluid));
            complete = true;
            return;
        }
        FluidState fs = state.getFluidState();
        int l = fs.getAmount();
        int osl = sl;
        sl += l;
        int nl = 0;
        if (sl >= bucketLevels) {
            nl = sl - bucketLevels;
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
        return fluid.isSame(state.getFluidState().getType());
    }

    @Override
    public void finish() {
        fillStates(states, world);
    }

    @Override
    public void fail() {
        ci.setReturnValue(InteractionResultHolder.fail(bottle));
    }
}
