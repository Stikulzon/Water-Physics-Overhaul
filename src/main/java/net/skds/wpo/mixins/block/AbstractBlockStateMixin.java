package net.skds.wpo.mixins.block;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.block.AbstractBlock.AbstractBlockState;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.fluid.FlowingFluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IWorld;
import net.minecraft.world.World;
import net.skds.wpo.WPOConfig;
import net.skds.wpo.fluidphysics.FFluidStatic;
import net.skds.wpo.registry.BlockStateProps;
import net.skds.wpo.util.interfaces.IBaseWL;

@Mixin(value = { AbstractBlockState.class })
public abstract class AbstractBlockStateMixin {

	@Inject(method = "getFluidState", at = @At(value = "HEAD"), cancellable = true)
	public void getFluidStateM(CallbackInfoReturnable<FluidState> ci) {
		BlockState bs = (BlockState) (Object) this;
		if (bs.getBlock() instanceof IBaseWL) {
			int level = bs.getValue(BlockStateProps.FFLUID_LEVEL);
			FluidState fs;
			if (bs.getValue(BlockStateProperties.WATERLOGGED)) {
				level = (level == 0) ? WPOConfig.MAX_FLUID_LEVEL : level;
				if (level >= WPOConfig.MAX_FLUID_LEVEL) {
					fs = ((FlowingFluid) Fluids.WATER).getSource(false);
				} else if (level <= 0) {
					fs = Fluids.EMPTY.defaultFluidState();
				} else {
					fs = ((FlowingFluid) Fluids.WATER).getFlowing(level, false);
				}
			} else {
				fs = Fluids.EMPTY.defaultFluidState();
			}
			ci.setReturnValue(fs);
		}

	}

	@Inject(method = "isRandomlyTicking", at = @At(value = "HEAD"), cancellable = true)
	public void isRandomlyTickingM(CallbackInfoReturnable<Boolean> ci) {
	}

	@Inject(method = "neighborChanged", at = @At(value = "HEAD"), cancellable = false)
	public void neighborChangedM(World worldIn, BlockPos posIn, Block blockIn, BlockPos fromPosIn, boolean isMoving,
			CallbackInfo ci) {
		// super.neighborChanged(worldIn, posIn, blockIn, fromPosIn, isMoving);
		if (((BlockState) (Object) this).getBlock() instanceof IBaseWL) {
			BlockState s = (BlockState) (Object) this;
			fixFFLNoWL((World) worldIn, s, posIn);
			if (s.getValue(BlockStateProperties.WATERLOGGED))
				worldIn.getLiquidTicks().scheduleTick(posIn, s.getFluidState().getType(),
						FFluidStatic.getTickRate((FlowingFluid) s.getFluidState().getType(), worldIn));
		}
	}

	@Inject(method = "updateShape", at = @At(value = "HEAD"), cancellable = false)
	public void updateShapeM(Direction face, BlockState queried, IWorld worldIn, BlockPos currentPos,
			BlockPos offsetPos, CallbackInfoReturnable<BlockState> ci) {
		if (((BlockState) (Object) this).getBlock() instanceof IBaseWL) {
			BlockState s = (BlockState) (Object) this;
			fixFFLNoWL(worldIn, s, currentPos);
			if (s.getValue(BlockStateProperties.WATERLOGGED))
				worldIn.getLiquidTicks().scheduleTick(currentPos, s.getFluidState().getType(),
						FFluidStatic.getTickRate((FlowingFluid) s.getFluidState().getType(), worldIn));
		}
	}

	private void fixFFLNoWL(IWorld w, BlockState s, BlockPos p) {
		if (!s.getValue(BlockStateProperties.WATERLOGGED) && s.getValue(BlockStateProps.FFLUID_LEVEL) > 0) {
			w.setBlock(p, s.setValue(BlockStateProps.FFLUID_LEVEL, 0), 3);
		}
	}
}