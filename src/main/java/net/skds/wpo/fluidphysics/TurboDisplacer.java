package net.skds.wpo.fluidphysics;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.skds.wpo.WPOConfig;
import net.skds.wpo.util.interfaces.IBaseWL;

public class TurboDisplacer {


	public static void markForDisplace(ServerLevel serverLevel, BlockPos pos, BlockState oldBS, BlockState newBS) {
		FluidState oldFS = oldBS.getFluidState();
		Fluid oldFluid = oldFS.getType();
		Block newBlock = newBS.getBlock();
		int oldLevel = oldFS.getAmount();
		if (oldFS.isEmpty()) {
			return;
		}
		if (newBlock instanceof SimpleWaterloggedBlock && oldFluid.isSame(Fluids.WATER)) {
			if (oldLevel == WPOConfig.MAX_FLUID_LEVEL) {
				serverLevel.setBlockAndUpdate(pos, FFluidStatic.getUpdatedState(newBS, oldLevel, oldFluid));
				return;
			} else if (newBlock instanceof IBaseWL) {
				serverLevel.setBlockAndUpdate(pos, FFluidStatic.getUpdatedState(newBS, oldLevel, oldFluid));
				return;
			}
		}
		
		if (!FFluidStatic.canOnlyFullCube(newBS) && newBlock instanceof IBaseWL && oldFluid.isSame(Fluids.WATER)) {
			newBS = FFluidStatic.getUpdatedState(newBS, oldFS.getAmount(), Fluids.WATER);
			serverLevel.setBlockAndUpdate(pos, newBS);
			return;
		}

		FluidDisplacer2 displacer = new FluidDisplacer2(serverLevel, oldBS);
		FFluidStatic.iterateFluidWay(10, pos, displacer);
	}
    
}
