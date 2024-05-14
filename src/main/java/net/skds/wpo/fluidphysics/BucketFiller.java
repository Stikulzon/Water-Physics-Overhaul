package net.skds.wpo.fluidphysics;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.*;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.eventbus.api.Event.Result;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.skds.wpo.WPOConfig;
import net.skds.wpo.util.ExtendedFHIS;
import static net.skds.wpo.fluidphysics.FFluidStatic.*;

public class BucketFiller implements IFluidActionIteratable {
    int bucketLevels = WPOConfig.MAX_FLUID_LEVEL;
    int sl = 0;
    boolean complete = false;
    Level world;
    Fluid fluid;
    FillBucketEvent event;
    IFluidHandlerItem bucket;
    Long2ObjectLinkedOpenHashMap<BlockState> states = new Long2ObjectLinkedOpenHashMap<>();

    BucketFiller(Level w, Fluid f, IFluidHandlerItem b, FillBucketEvent e) {
        world = w;
        fluid = f;
        bucket = b;
        event = e;
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
        sl += l;
        int nl = 0;
        if (sl >= bucketLevels) {
            nl = sl - bucketLevels;
            complete = true;
        }
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
    public void fail() {
        fillStates(states, world);
        event.setResult(Result.ALLOW);
        Player p = event.getEntity();
        Item item = bucket.getContainer().getItem();
        p.awardStat(Stats.ITEM_USED.get(item));
        SoundEvent soundevent = fluid.is(FluidTags.LAVA) ? SoundEvents.BUCKET_FILL_LAVA
                : SoundEvents.BUCKET_FILL;
        p.playSound(soundevent, 1.0F, 1.0F);

        if (!p.getAbilities().instabuild) {
            ItemStack stack = new ItemStack(net.skds.wpo.registry.Items.ADVANCED_BUCKET.get());
            ExtendedFHIS st2 = new ExtendedFHIS(stack, 1000);
            Fluid f2 = fluid instanceof FlowingFluid ? ((FlowingFluid) fluid).getSource() : fluid;
            FluidStack fluidStack = new FluidStack(f2, sl * FFluidStatic.FCONST);
            st2.fill(fluidStack, FluidAction.EXECUTE);

            stack = st2.getContainer();
            event.setFilledBucket(stack);
        }
    }

    @Override
    public void finish() {
        fillStates(states, world);

        event.setResult(Result.ALLOW);
        Player p = event.getEntity();
        Item item = bucket.getContainer().getItem();
        p.awardStat(Stats.ITEM_USED.get(item));
        SoundEvent soundevent = fluid.is(FluidTags.LAVA) ? SoundEvents.BUCKET_FILL_LAVA
                : SoundEvents.BUCKET_FILL;
        p.playSound(soundevent, 1.0F, 1.0F);
        if (!p.getAbilities().instabuild) {
            // bucket.fill(new FluidStack(fluid, 1000), FluidAction.EXECUTE);
            event.setFilledBucket(new ItemStack(fluid.getBucket()));
        }
    }
}
