package net.skds.wpo.fluidphysics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.skds.core.api.IWWSG;
import net.skds.core.util.blockupdate.BasicExecutor;
import net.skds.core.util.blockupdate.UpdateTask;
import net.skds.wpo.WPOConfig;
import net.skds.wpo.util.interfaces.IFlowingFluid;

import java.util.Random;

public abstract class FFluidBasic extends BasicExecutor {

	protected final int worker;
	protected final Mode mode;
	protected final int MFL = WPOConfig.MAX_FLUID_LEVEL;
	protected final Fluid fluid;
	protected final ServerLevel w;
	protected final Random random;
	protected final BlockPos pos;
	protected final long longpos;

	protected final WorldWorkSet castOwner;

	protected int level;
	protected FluidState fs;
	protected BlockState state;

	protected FFluidBasic(ServerLevel w, BlockPos pos, Mode mode, WorldWorkSet owner, int worker) {
		super(w, FFluidBasic::updater, owner);
		this.castOwner = owner;
		this.worker = worker;
		this.w = w;
		this.random = new Random();
		this.mode = mode;
		this.state = getBlockState(pos);
		this.fs = this.state.getFluidState();
		this.fluid = fs.getType();
		this.pos = pos;
		this.longpos = pos.asLong();
		this.level = fs.getAmount();
	}

	public static void updater(UpdateTask task, ServerLevel world) {
	}

	@Override
	protected void applyAction(BlockPos pos, BlockState newState, BlockState oldState, ServerLevel world) {
		if (newState == oldState) {
			return;
		}
		ChunkAccess ichunk = getChunk(pos);
		if (!(ichunk instanceof LevelChunk chunk)) {
			return;
		}
		Block block = newState.getBlock();

		BlockPos posu = pos.above();
		if (getBlockState(posu).getFluidState().isEmpty()) {
			for (Direction dir : Direction.Plane.HORIZONTAL) {
				BlockPos posu2 = posu.relative(dir);
				if (!getBlockState(posu2).getFluidState().isEmpty()) {
					WorldWorkSet.pushTask(new FluidTask.DefaultTask(castOwner, posu2.asLong()));
				}
			}
		}

		Fluid fluid = newState.getFluidState().getType();
		if (fluid != Fluids.EMPTY) {
			castOwner.excludedTasks.add(longpos);
		}
		synchronized (world) {
			if (fluid != Fluids.EMPTY && !oldState.isAir() && !fluid.isSame(oldState.getFluidState().getType())
					&& !(oldState.getBlock() instanceof SimpleWaterloggedBlock)) {
				((IFlowingFluid) fluid).beforeReplacingBlockCustom(world, pos, oldState);
			}

            chunk.getFullStatus();
            if (chunk.getFullStatus().isOrAfter(FullChunkStatus.BLOCK_TICKING)) {
				world.sendBlockUpdated(pos, oldState, newState, 3);
			}

			world.updateNeighborsAt(pos, block);
			if (state.hasAnalogOutputSignal()) {
				world.updateNeighbourForOutputSignal(pos, block);
			}

			newState.updateNeighbourShapes(world, pos, 0);

			newState.onPlace(world, pos, oldState, false);

		}

		if ((newState.getFluidState().isEmpty() ^ oldState.getFluidState().isEmpty())
				&& (newState.getLightBlock(world, pos) != oldState.getLightBlock(world, pos)
						|| newState.getLightEmission(world, pos) != oldState.getLightEmission(world, pos)
						|| newState.useShapeForLightOcclusion() || oldState.useShapeForLightOcclusion())) {
			world.getChunkSource().getLightEngine().checkBlock(pos);
		}
	}

	protected int getAbsoluteLevel(int y, int l) {
		return (y * MFL) + l;
	}

	@Override
	public void run() {
		if (level > 0 && (fluid instanceof FlowingFluid)) {
			execute();
		}
		IWWSG wwsg = owner.getG();
		banPoses.forEach(p -> wwsg.unbanPos(p.asLong()));
	}

	protected abstract void execute();

	protected boolean validate(BlockPos p) {
		long l = p.asLong();
		boolean ss = owner.getG().banPos(l);
		if (ss) {
			banPoses.add(p);
		}
		return ss;
	}

	protected void addPassedEq(BlockPos addPos) {
		long l = addPos.asLong();
		castOwner.addEqLock(l);
		castOwner.addNTTask(l, FFluidStatic.getTickRate((FlowingFluid) fluid, w));
	}

	protected boolean isPassedEq(BlockPos isPos) {
		long l = isPos.asLong();
		return castOwner.isEqLocked(l);
	}

	protected void flowFullCube(BlockPos pos2, BlockState state2) {
		if (state2 == null) {
			cancel = true;
			return;
		}
		FluidState fs2 = state2.getFluidState();
		int level2 = fs2.getAmount();

		state = getUpdatedState(state, level2);
		state2 = getUpdatedState(state2, level);
		setState(pos, state);
		setState(pos2, state2);
	}

	protected BlockState getUpdatedState(BlockState state0, int newLevel) {
		return FFluidStatic.getUpdatedState(state0, newLevel, fluid);
	}

	// ================ UTIL ================== //

	protected boolean isThisFluid(Fluid f2) {
		if (fluid == Fluids.EMPTY)
			return false;
		if (f2 == Fluids.EMPTY)
			return false;
		return fluid.isSame(f2);
	}

	protected boolean canReach(BlockPos pos1, BlockPos pos2, BlockState state1, BlockState state2) {
		if (state1 == nullreturnstate || state2 == nullreturnstate) {
			return false;
		}
		return FFluidStatic.canReach(pos1, pos2, state1, state2, fluid, w);
	}

	public enum Mode {
		DEFAULT, EQUALIZER
    }

}