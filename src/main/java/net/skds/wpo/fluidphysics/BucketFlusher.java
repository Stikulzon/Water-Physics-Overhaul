package net.skds.wpo.fluidphysics;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MobBucketItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.PistonEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.Event.Result;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.skds.core.api.IBlockExtended;
import net.skds.core.api.IWWSG;
import net.skds.core.api.IWorldExtended;
import net.skds.wpo.WPOConfig;
import net.skds.wpo.registry.BlockStateProps;
import net.skds.wpo.util.ExtendedFHIS;
import net.skds.wpo.util.interfaces.IBaseWL;
import net.skds.wpo.util.pars.FluidPars;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import static net.skds.wpo.fluidphysics.FFluidStatic.*;

public class BucketFlusher implements IFluidActionIteratable {

    int mfl = WPOConfig.MAX_FLUID_LEVEL;
    int bucketLevels = WPOConfig.MAX_FLUID_LEVEL;
    int sl = bucketLevels;
    boolean complete = false;
    Level world;
    Fluid fluid;
    FillBucketEvent event;
    IFluidHandlerItem bucket;
    Long2ObjectLinkedOpenHashMap<BlockState> states = new Long2ObjectLinkedOpenHashMap<>();

    BucketFlusher(Level w, Fluid f, IFluidHandlerItem b, FillBucketEvent e) {
        world = w;
        fluid = f;
        bucket = b;
        event = e;
        sl = bucket.getFluidInTank(0).getAmount() / FFluidStatic.FCONST;
        fluid = bucket.getFluidInTank(0).getFluid();
    }

    @Override
    public boolean isComplete() {
        return complete;
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
    public void finish() {
        fillStates(states, world);

        event.setResult(Event.Result.ALLOW);
        Player p = event.getEntity();
        Item item = bucket.getContainer().getItem();
        p.awardStat(Stats.ITEM_USED.get(item));
        SoundEvent soundevent = fluid.is(FluidTags.LAVA) ? SoundEvents.BUCKET_EMPTY_LAVA
                : SoundEvents.BUCKET_EMPTY;
        p.playSound(soundevent, 1.0F, 1.0F);
        if (!p.getAbilities().instabuild) {
            // bucket.fill(FluidStack.EMPTY, FluidAction.EXECUTE);
            // event.setFilledBucket(bucket.getContainer());
            event.setFilledBucket(new ItemStack(Items.BUCKET));
        }
    }
}