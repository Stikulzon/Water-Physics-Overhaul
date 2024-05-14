package net.skds.wpo.fluidphysics;

import static net.skds.wpo.WPOConfig.COMMON;
import static net.skds.wpo.WPOConfig.MAX_FLUID_LEVEL;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.WaterFluid;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.server.level.ServerLevel;
import net.skds.wpo.WPOConfig;

public class FFluidEQ extends FFluidBasic {

	FFluidEQ(ServerLevel w, BlockPos pos, WorldWorkSet owner, FFluidBasic.Mode mode, int worker) {
		super(w, pos, mode, owner, worker);
	}

	@Override
	public void execute() {
		if (getBlockState(pos.above()).getFluidState().isEmpty() && !FFluidStatic.canOnlyFullCube(state)
				&& !canFlow(pos, pos.below(), state, getBlockState(pos.below()), true, false)) {
			equalize();
		}
	}
	
	public void equalize() {
		boolean slide = WPOConfig.COMMON.maxSlideDist.get() > 0;
		boolean slided = false;
		int i0 = random.nextInt(4);
		if (slide && !canReach(pos, pos.below(), state, getBlockState(pos.below())) && level == 1) {
			slided = slide();
		}
		int dist = COMMON.maxEqDist.get();
		if (!slided && dist > 0) {
			// if (isPassedEq(pos)) {
			// return;
			// }
			for (int index = 0; index < 4; ++index) {
				if (level <= 0) {
					break;
				}
				if (cancel) {
					return;
				}
				Direction dir = Direction.from2DDataValue((index + i0) % 4);
				equalizeLine(dir, false, dist);
			}
		}
	}

	public boolean slide() {
        int lenmin = WPOConfig.COMMON.maxSlideDist.get();

		boolean selPosb = false;
		BlockPos selPos = pos;
		BlockState selState = state;

		boolean[] diag2 = { false, true };

		for (Direction dir : FFluidStatic.getRandomizedDirections(random, false)) {
			for (boolean diag : diag2) {

				boolean selPosb2 = false;
				BlockPos selPos2 = pos;
				int dist = 0;
				int len = lenmin;
				BlockPos pos2 = pos;
				BlockPos pos1 = pos;
				boolean side = false;
				BlockState state1 = state;
				BlockState state2 = state;
				boolean bl = false;

				// System.out.println(len);
				wh: while (len > 0) {
					pos1 = pos2;
					state1 = state2;
					if (diag) {
						if (side) {
							dir = dir.getClockWise();
							side = false;
						} else {
							dir = dir.getCounterClockWise();
							side = true;
						}
					}
					pos2 = pos1.relative(dir);
					state2 = getBlockState(pos2);
					FluidState fs2 = state2.getFluidState();
					if (canReach(pos1, pos2, state1, state2)
							&& (fs2.isEmpty() || (fs2.getAmount() < 2 && fs2.getType().isSame(fluid)))) {
						if ((state1.getBlock() instanceof SimpleWaterloggedBlock || state2.getBlock() instanceof SimpleWaterloggedBlock)
								&& !(fluid instanceof WaterFluid)) {
							break wh;
						}
						if (dist > 0 && !selPosb2 && fs2.isEmpty()) {
							selPosb2 = true;
							selPos2 = pos1;
						}
						bl = (canFlow(pos1, pos1.below(), state1, getBlockState(pos1.below()), true, false))
								&& !FFluidStatic.canOnlyFullCube(state2);
					} else {
						break wh;
					}
					--len;
					if (bl && !cancel && selPosb2) {
						lenmin = Math.min(dist, lenmin);
						selPos = selPos2;
						selState = state1;
						selPosb = true;
					}
					++dist;
				}
			}
		}
		if (selPosb && validate(selPos)) {
			//System.out.println("bl");
			selState = getBlockState(selPos);
			selState = flowToPosEq(selState, -1);
			setState(selPos, selState);
			setState(pos, state);
			return true;
		}
		return false;
	}

	public void equalizeLine(Direction dir, boolean diag, int len) {
		BlockPos pos2 = pos;
		BlockPos pos1 = pos;
		int len2 = len;
		boolean side = false;
		BlockState state1 = state;
		BlockState state2 = state;
		int hmod = 0;

		boolean blocked = false;

		while (len > 0) {

			if (!diag && len2 - len == 1) {
				equalizeLine(dir, true, len);
			}

			if (diag) {
				if (side) {
					dir = dir.getClockWise();
					side = false;
				} else {
					dir = dir.getCounterClockWise();
					side = true;
				}
			}
			pos1 = pos2;
			state1 = state2;

			BlockPos pos1u = pos1.above();
			BlockState state1u = getBlockState(pos1u);
			FluidState fs1u = state1u.getFluidState();

			if (!blocked && canReach(pos1u, pos1, state1u, state1)
					&& (!fs1u.isEmpty() && isThisFluid(fs1u.getType()))) {
				pos2 = pos1u;
				state2 = state1u;
				++hmod;
			} else {
				pos2 = pos1.relative(dir);
				state2 = getBlockState(pos2);
			}

			FluidState fs2 = state2.getFluidState();

			if (isPassedEq(pos2)) {
				break;
			}

			if (canReach(pos1, pos2, state1, state2)
					&& (isThisFluid(fs2.getType()) || (fs2.isEmpty() && level > 1))) {
				if ((state1.getBlock() instanceof SimpleWaterloggedBlock || state2.getBlock() instanceof SimpleWaterloggedBlock)
						&& !(fluid instanceof WaterFluid)) {
					break;
				}
				blocked = false;

			} else {
				pos2 = pos1.below();
				state1 = state2;
				state2 = getBlockState(pos2);
				fs2 = state2.getFluidState();
				if (canReach(pos1, pos2, state1, state2)
						&& (!fs2.isEmpty() && isThisFluid(fs2.getType()) || fs2.isEmpty())) {
					--hmod;
					blocked = true;

				} else {
					break;
				}
			}

			if (!cancel && validate(pos2)) {
				int level2 = fs2.getAmount();
				int l1 = getAbsoluteLevel(pos.getY(), level);
				int l2 = getAbsoluteLevel(pos2.getY(), level2);
				if (Mth.abs(l1 - l2) > 1
						&& !FFluidStatic.canOnlyFullCube(state2)) {
					state2 = flowToPosEq(state2, hmod);
					setState(pos2, state2);
					setState(pos, state);
					addPassedEq(pos2);
					return;
				}
			}
			--len;
		}
	}

	private BlockState flowToPosEq(BlockState state2, int l) {

		BlockState state2n = state2;

		FluidState fs2 = state2.getFluidState();
		int level2 = fs2.getAmount();
		int delta = (level - level2) / 2;
		// l = 0;
		if (l != 0) {
			if (l == -1) {
				level2 += level;
				if (level2 > MAX_FLUID_LEVEL) {
					level = level2 - MAX_FLUID_LEVEL;
					level2 = MAX_FLUID_LEVEL;
				} else {
					level = 0;
				}
			} else {
				// System.out.println(l);
				level += level2;
				if (level > MAX_FLUID_LEVEL) {
					level2 = level - MAX_FLUID_LEVEL;
					level = MAX_FLUID_LEVEL;
				} else {
					level2 = 0;
				}
			}
			state = getUpdatedState(state, level);
			state2n = getUpdatedState(state2, level2);

		} else if (Mth.abs(delta) >= 1) {

			level -= delta;
			level2 += delta;
			// System.out.println("Delta " + level + " ss: " + level2);
			state = getUpdatedState(state, level);
			state2n = getUpdatedState(state2, level2);

		} else if (level2 == 0) {
			level2 = level;
			level = 0;
			state = getUpdatedState(state, level);
			state2n = getUpdatedState(state2, level2);
		}
		return state2n;

	}

	private boolean canFlow(BlockPos pos1, BlockPos pos2, BlockState state1, BlockState state2, boolean down,
			boolean ignoreLevels) {
		if (state2 == null) {
			cancel = true;
			return false;
		}
		if ((FFluidStatic.canOnlyFullCube(state2) || FFluidStatic.canOnlyFullCube(state)) && !down) {
			return false;
		}
		if (FFluidStatic.canOnlyFullCube(state2) && state1.getFluidState().getAmount() < WPOConfig.MAX_FLUID_LEVEL) {
			return false;
		}

		if ((state1.getBlock() instanceof SimpleWaterloggedBlock || state2.getBlock() instanceof SimpleWaterloggedBlock)
				&& !(fluid instanceof WaterFluid)) {
			return false;
		}

		if (!canReach(pos1, pos2, state1, state2)) {
			return false;
		}

		FluidState fs2 = state2.getFluidState();

		int level2 = fs2.getAmount();
		if (level2 >= MAX_FLUID_LEVEL && !ignoreLevels) {
			return false;
		}

		if (level == 1 && !down && !ignoreLevels) {
			if (fs2.isEmpty()) {
				pos1 = pos2;
				pos2 = pos2.below();
				state1 = state2;
				state2 = getBlockState(pos2);
				if (isThisFluid(state2.getFluidState().getType()) || state2.getFluidState().isEmpty()) {
					return canFlow(pos1, pos2, state1, state2, true, false);
				} else {
					return false;
				}
			} else {
				return (level2 + 2 < level);
			}
		}

		return true;
	}
}