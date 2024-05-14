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

import java.util.*;

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
