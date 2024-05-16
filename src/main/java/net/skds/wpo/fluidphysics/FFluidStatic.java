package net.skds.wpo.fluidphysics;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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

public class FFluidStatic {

	public final static int FCONST = 1000 / WPOConfig.MAX_FLUID_LEVEL;

	// ================ UTIL ================== //

	public static Direction[] getRandomizedDirections(Random r, boolean addVertical) {

		Direction[] dirs = new Direction[4];

		if (addVertical) {
			dirs = new Direction[6];
			dirs[4] = Direction.DOWN;
			dirs[5] = Direction.UP;
		}
		int i0 = r.nextInt(4);
		for (int i = 0; i < 4; ++i) {
			Direction dir = Direction.from2DDataValue((i + i0) % 4);
			dirs[i] = dir;
		}

		return dirs;
	}

	public static BlockState getUpdatedState(BlockState state0, int newlevel, Fluid fluid) {
		if ((newlevel < 0) || (newlevel > WPOConfig.MAX_FLUID_LEVEL)) {
			throw new RuntimeException("Incorrect fluid level!!!");
		}
		if (FFluidStatic.canOnlyFullCube(state0) && fluid instanceof WaterFluid) {
			if (state0.hasProperty(BlockStateProperties.WATERLOGGED)) {
				if (newlevel >= 1) {
					// FIXME: this creates water from nothing!!!
					return state0.setValue(BlockStateProperties.WATERLOGGED, true);
				} else {
					return state0.setValue(BlockStateProperties.WATERLOGGED, false);
				}
			}
		}
		if (state0.getBlock() instanceof IBaseWL && fluid instanceof WaterFluid) {
			if (state0.hasProperty(BlockStateProperties.WATERLOGGED)) {
				if (newlevel >= 1) {
					return state0.setValue(BlockStateProperties.WATERLOGGED, true)
							.setValue(BlockStateProps.FFLUID_LEVEL, newlevel);
				} else {
					return state0.setValue(BlockStateProperties.WATERLOGGED, false)
							.setValue(BlockStateProps.FFLUID_LEVEL, newlevel);
				}
			} else {
				return state0.setValue(BlockStateProps.FFLUID_LEVEL, newlevel);
			}
		}
		FluidState fs2;
		if (newlevel >= WPOConfig.MAX_FLUID_LEVEL) {
			// FIXME: this destroys water!!!
			fs2 = ((FlowingFluid) fluid).getSource(false);
		} else if (newlevel <= 0) {
			fs2 = Fluids.EMPTY.defaultFluidState();
		} else {
			fs2 = ((FlowingFluid) fluid).getFlowing(newlevel, false);
		}
		return fs2.createLegacyBlock();
	}

	public static float getHeight(int level) {
		float h = ((float) level / WPOConfig.MAX_FLUID_LEVEL) * 0.9375F;
        return switch (level) {
            case 3 -> h * 0.9F;
            case 2 -> h * 0.75F;
            case 1 -> h * 0.4F;
            default -> h;
        };
	}

	public static boolean isSameFluid(Fluid f1, Fluid f2) {
		if (f1 == Fluids.EMPTY)
			return false;
		if (f2 == Fluids.EMPTY)
			return false;
		return f1.isSame(f2);
	}

	public static int getTickRate(FlowingFluid fluid, LevelReader w) {
		int rate = fluid.getTickDelay(w);
		rate /= 2;
		return rate > 0 ? rate : 1;
	}

	public static Direction dirFromVec(BlockPos pos, BlockPos pos2) {
		return Direction.getNearest(pos2.getX() - pos.getX(), pos2.getY() - pos.getY(),
				pos2.getZ() - pos.getZ());
	}

	// ================ OTHER ================== //

	public static Vec3 getVel(BlockGetter w, BlockPos pos, FluidState fs) {

		Vec3 vel = new Vec3(0, 0, 0);
		int level = fs.getAmount();
		BlockState state = fs.createLegacyBlock();
		Fluid fluid = fs.getType();
		BlockPos posu = pos.above();

		boolean flag = false;

		BlockState stateu = w.getBlockState(posu);

		if (canReach(pos, posu, state, stateu, fluid, w) && !stateu.getFluidState().isEmpty()) {
			level += stateu.getFluidState().getAmount();
			flag = true;
		}

		for (Direction dir : Direction.Plane.HORIZONTAL) {
			BlockPos pos2 = pos.relative(dir);

			BlockState state2 = w.getBlockState(pos2);
			FluidState fs2 = state2.getFluidState();

			if (!fs2.isEmpty() && canReach(pos, pos2, state, state2, fluid, w)) {
				int lvl2 = fs2.getAmount();
				if (flag) {
					FluidState fs2u = w.getFluidState(pos2.above());
					if (isSameFluid(fluid, fs2u.getType())) {
						lvl2 += fs2u.getAmount();
					}
				}
				int delta = level - lvl2;
				if (delta > 1 || delta < -1) {
					Vec3i v3i = dir.getNormal();
					vel = vel.add(v3i.getX() * delta, 0, v3i.getZ() * delta);
				}
			}
		}
		return vel.normalize();
	}

	// ================ RENDERER ================== //

	public static float getCornerHeight(BlockGetter bg, Fluid fluid, BlockPos centerPos, BlockPos side1Pos, BlockPos side2Pos, BlockPos cornerPos){
		BlockPos aboveCenterPos = centerPos.above();
		BlockPos belowCenterPos = centerPos.below();
		float minLvl = 0.0036F;  // minimal height so looks detached from ground
		float maxLvl = 0.99999F;  // prevent z-fighting with block above (probably?)

		/* reach (can flow between BlockPos') */
		boolean centerCanReachUp = canReach(centerPos, aboveCenterPos, fluid, bg);
		boolean centerCanReachSide1 = canReach(centerPos, side1Pos, fluid, bg);
		boolean centerCanReachSide2 = canReach(centerPos, side2Pos, fluid, bg);
		boolean side1CanReachDown = canReach(side1Pos, side1Pos.below(), fluid, bg);
		boolean side2CanReachDown = canReach(side2Pos, side2Pos.below(), fluid, bg);
		boolean side1CanReachCorner = canReach(side1Pos, cornerPos, fluid, bg);
		boolean side2CanReachCorner = canReach(side2Pos, cornerPos, fluid, bg);
		/* BlockPos is (same) fluid */
		boolean aboveCenterIsFluid = isSameFluid(aboveCenterPos, fluid, bg);
		boolean belowCenterIsFluid = isSameFluid(belowCenterPos, fluid, bg);
		boolean side1IsFluid = isSameFluid(side1Pos, fluid, bg);
		boolean side2IsFluid = isSameFluid(side2Pos, fluid, bg);
		boolean belowSide1IsFluid = isSameFluid(side1Pos.below(), fluid, bg);
		boolean belowSide2IsFluid = isSameFluid(side2Pos.below(), fluid, bg);
		boolean cornerIsFluid = isSameFluid(cornerPos, fluid, bg);
		boolean belowCornerIsFluid = isSameFluid(cornerPos, fluid, bg);
		/* connections: both have fluid and can flow between */
		boolean centerConnectUp = canReachAndSameFluid(centerPos, centerPos.above(), fluid, bg);
		boolean centerConnectSide1 = centerCanReachSide1 && side1IsFluid;
		boolean centerConnectSide2 = centerCanReachSide2 && side2IsFluid;
		boolean side1ConnectUp = canReachAndSameFluid(side1Pos, side1Pos.above(), fluid, bg);
		boolean side2ConnectUp = canReachAndSameFluid(side2Pos, side2Pos.above(), fluid, bg);
		boolean side1ConnectCorner = side1CanReachCorner && cornerIsFluid;
		boolean side2ConnectCorner = side2CanReachCorner && cornerIsFluid;
		boolean cornerConnectUp = canReachAndSameFluid(cornerPos, cornerPos.above(), fluid, bg);

		/* adapt min and max levels */
		if (centerCanReachUp){  // above can be flooded => no z-fighting
			maxLvl = 1.0F;
		}
		if (belowCenterIsFluid) {  // can connect smoothly when down-flowing without z-fighting
			minLvl =  0.0F + 0.001F; // LiquidBlockRenderer subtracts 0.001F; otherwise negative => render crash
		}

		// Fluid above => max level
		if (centerConnectUp){ // should never happen, because renderer catches this case using getHeight
			return maxLvl;
		}
		if (centerPos.getX() == -256 && centerPos.getZ() == -109){
			int a = 3;
		}
		// UP-FLOW: if fluid higher than block on sides or corner => max level
		if (centerConnectSide1 && side1ConnectUp){
			return maxLvl;
		}
		if (centerConnectSide2 && side2ConnectUp){
			return maxLvl;
		}
		if (centerConnectSide1 && side1ConnectCorner && cornerConnectUp){
			return maxLvl;
		}
		if (centerConnectSide2 && side2ConnectCorner && cornerConnectUp){
			return maxLvl;
		}
		// DOWN-FLOW: if fluid lower than block sides or corner => min level
		// (Up-flow dominates/takes precedence over this => guaranteed by returns in up-flow)
		if (centerCanReachSide1 && !side1IsFluid && side1CanReachDown && belowSide1IsFluid){
			return minLvl;
		}
		if (centerCanReachSide2 && !side2IsFluid && side2CanReachDown && belowSide2IsFluid){
			return minLvl;
		}
		if (centerConnectSide1 && side1CanReachCorner && !cornerIsFluid && belowCornerIsFluid){
			return minLvl;
		}
		if (centerConnectSide2 && side2CanReachCorner && !cornerIsFluid && belowCornerIsFluid){
			return minLvl;
		}
		// HORZ-FLOW: average over connected sides and corners
		// (Both Up-flow and down-flow dominates/takes precedence over this => guaranteed by returns in up-flow and down-flow)
		float sum = bg.getFluidState(centerPos).getOwnHeight();
		int count = 1;
		if (centerConnectSide1){
			sum += bg.getFluidState(side1Pos).getOwnHeight();
			count += 1;
		}
		if (centerConnectSide2){
			sum += bg.getFluidState(side2Pos).getOwnHeight();
			count += 1;
		}
		if (centerConnectSide1 && side1ConnectCorner || centerConnectSide2 && side2ConnectCorner){
			sum += bg.getFluidState(cornerPos).getOwnHeight();
			count += 1;
		}
		return sum / count;
	}


	// ================= UTIL ================== //
	private static boolean isSameFluid(BlockPos pos, Fluid fluid, BlockGetter bg){
		return fluid.isSame(bg.getFluidState(pos).getType());
	}

	/**
	 * checks if water can flow from given pos in given direction (to next pos), i.e. if:
	 * 1. there is place for water to flow in the collision shapes of the two blockstates (intersection not covered)
	 * 2. the destination accepts water (not solid OR solid and waterlogged)
	 */
	private static boolean canReach(BlockGetter world, BlockPos pos, Direction direction) {
		BlockState state1 = world.getBlockState(pos);
		BlockState state2 = world.getBlockState(pos.relative(direction));
		if (state2.canOcclude() && !(state2.getBlock() instanceof SimpleWaterloggedBlock)) {
			return false;
		}
		VoxelShape voxelShape2 = state2.getCollisionShape(world, pos.relative(direction));
		VoxelShape voxelShape1 = state1.getCollisionShape(world, pos);
		if (voxelShape1.isEmpty() && voxelShape2.isEmpty()) {
			return true;
		}
		return !Shapes.mergedFaceOccludes(voxelShape1, voxelShape2, direction);
	}

	public static boolean canReachAndSameFluid(BlockPos pos1, BlockPos pos2, Fluid f1, BlockGetter bg){
		return canReach(pos1, pos2, f1, bg) && f1.isSame(bg.getFluidState(pos2).getType());
	}

	public static boolean canReach(BlockPos pos1, BlockPos pos2, Fluid f, BlockGetter bg){
		BlockState state1 = bg.getBlockState(pos1);
		BlockState state2 = bg.getBlockState(pos2);
		return canReach(pos1, pos2, state1, state2, f, bg);
	}

	public static boolean canReach(BlockPos pos1, BlockPos pos2, BlockState state1, BlockState state2, Fluid fluid,
			BlockGetter w) {

		Fluid f2 = state2.getFluidState().getType();
		if (f2.isSame(fluid) && state1.getBlock() instanceof LiquidBlock
				&& state2.getBlock() instanceof LiquidBlock) {
			return true;
		}

		FluidPars fp2 = (FluidPars) ((IBlockExtended) state2.getBlock()).getCustomBlockPars().get(FluidPars.class);
		FluidPars fp1 = (FluidPars) ((IBlockExtended) state1.getBlock()).getCustomBlockPars().get(FluidPars.class);
		boolean posos = false;
		if (fp1 != null) {
			if (fp1.isPassable == 1) {
				posos = true;
			}
		}
		Direction dir = dirFromVec(pos1, pos2);
		if (fp2 != null) {
			if (fp2.isPassable == 1) {
				return true;
			} else if (fp2.isPassable == -1) {
				return false;
			}
			if ((state2.getFluidState().isEmpty() || state1.getFluidState().canBeReplacedWith(w, pos1, f2, dir))
					&& fp2.isDestroyableBy(fluid))
				return true;
		}

		if (state2.canOcclude() && !posos && !(state2.getBlock() instanceof SimpleWaterloggedBlock)) {
			return false;
		}
		if (!(fluid instanceof WaterFluid)
				&& (state1.getBlock() instanceof SimpleWaterloggedBlock || state2.getBlock() instanceof SimpleWaterloggedBlock)) {
			return false;
		}
		VoxelShape voxelShape2 = state2.getCollisionShape(w, pos2);
		VoxelShape voxelShape1 = state1.getCollisionShape(w, pos1);
		if ((voxelShape1.isEmpty() || posos) && voxelShape2.isEmpty()) {
			return true;
		}
		return !Shapes.mergedFaceOccludes(voxelShape1, voxelShape2, dir);
	}

	public static boolean canOnlyFullCube(BlockState bs) {
		return canOnlyFullCube(bs.getBlock());
	}

	public static boolean canOnlyFullCube(Block b) {
		return b instanceof SimpleWaterloggedBlock && !(b instanceof IBaseWL);
	}

	// ================= ITEMS ==================//

	public static void onBucketEvent(FillBucketEvent e) {

		MobBucketItem mobBucketItem = null;
		ItemStack bucket = e.getEmptyBucket();
		Item bu = bucket.getItem();
		if (bu instanceof MobBucketItem) {
			mobBucketItem = (MobBucketItem) bu;
		}
		Optional<IFluidHandlerItem> op = bucket.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM)
				.resolve();
		IFluidHandlerItem bh;
        bh = op.orElseGet(() -> new ExtendedFHIS(bucket, 1000));
		Fluid f = bh.getFluidInTank(0).getFluid();
		if (!(f instanceof FlowingFluid) && f != Fluids.EMPTY) {
			return;
		}
		Player p = e.getEntity();
		Level w = e.getLevel();
		HitResult targ0 = e.getTarget();
		BlockHitResult targ = rayTrace(w, p,
				f == Fluids.EMPTY ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE);
		targ0 = targ;
		if (targ.getType() != HitResult.Type.BLOCK) {
			return;
		}
        BlockPos pos = targ.getBlockPos();
		BlockState bs = w.getBlockState(pos);
		FluidState fs = bs.getFluidState();
		if (fs.isEmpty() && f != Fluids.EMPTY && !(bs.getBlock() instanceof SimpleWaterloggedBlock)) {
			pos = pos.relative(targ.getDirection());
			bs = w.getBlockState(pos);
			fs = bs.getFluidState();
		}
		if (!w.isClientSide && f != Fluids.EMPTY && bs.getBlock() instanceof SimpleWaterloggedBlock) {
			FluidTasksManager.addFluidTask((ServerLevel) w, pos, bs);
		}
		Fluid fluid = fs.getType();
		if ((!f.isSame(Fluids.WATER) && f != Fluids.EMPTY) && bs.getBlock() instanceof SimpleWaterloggedBlock) {

			return;
		}

		if (!(w.mayInteract(p, pos) && p.mayUseItemAt(pos, targ.getDirection(), bh.getContainer()))) {
			return;
		}

		if (f == Fluids.EMPTY) {
			if (!(fluid instanceof FlowingFluid)) {
				return;
			}
			if (targ0.getType() == HitResult.Type.BLOCK) {
				BlockHitResult targB0 = (BlockHitResult) targ0;
				FluidState fs0 = w.getFluidState(targB0.getBlockPos());
				if (fs0.isSource()) {
					return;
				}
			}
			BucketFiller filler = new BucketFiller(w, fluid, bh, e);
			iterateFluidWay(WPOConfig.COMMON.maxBucketDist.get(), pos, filler);

		} else {
			if (!f.isSame(fluid) && fluid != Fluids.EMPTY) {
				e.setCanceled(true);
				return;
			}
			BucketFlusher flusher = new BucketFlusher(w, f, bh, e);
			if (iterateFluidWay(WPOConfig.COMMON.maxBucketDist.get(), pos, flusher) && mobBucketItem != null) {
				mobBucketItem.checkExtraContent(e.getEntity(), w, bucket, pos);
			}
		}
	}

	public static BlockHitResult rayTrace(Level worldIn, Player player,
			ClipContext.Fluid fluidMode) {
		float f = player.getXRot();
		float f1 = player.getYRot();
		Vec3 vector3d = player.getEyePosition(1.0F);
		float f2 = Mth.cos(-f1 * ((float) Math.PI / 180F) - (float) Math.PI);
		float f3 = Mth.sin(-f1 * ((float) Math.PI / 180F) - (float) Math.PI);
		float f4 = -Mth.cos(-f * ((float) Math.PI / 180F));
		float f5 = Mth.sin(-f * ((float) Math.PI / 180F));
		float f6 = f3 * f4;
		float f7 = f2 * f4;
		double d0 = Objects.requireNonNull(player.getAttribute(ForgeMod.ENTITY_REACH.get())).getValue();
		Vec3 vector3d1 = vector3d.add((double) f6 * d0, (double) f5 * d0, (double) f7 * d0);
		return worldIn.clip(
				new ClipContext(vector3d, vector3d1, ClipContext.Block.OUTLINE, fluidMode, player));
	}

	public static boolean iterateFluidWay(int maxRange, BlockPos pos, IFluidActionIteratable actioner) {
		boolean frst = true;
		boolean client;
		Level w = actioner.getWorld();
		Random random = new Random();
		IWWSG wws = ((IWorldExtended) w).getWWS();
		Set<BlockPos> setBan = new HashSet<>();
		Set<BlockPos> setAll = new HashSet<>();
		client = wws == null;
		if (!client) {
            setBan.add(pos);
            if (!wws.banPos(pos.asLong())) {
                setBan.forEach(p -> wws.banPos(p.asLong()));
                return false;
            }
        }
		setAll.add(pos);
		Set<BlockPos> setLocal = new HashSet<>();
		actioner.addZero(setLocal, pos);
		int n = maxRange;
		while (n > 0 && !actioner.isComplete() && !setLocal.isEmpty()) {
			--n;
			Set<BlockPos> setLocal2 = new HashSet<>();
			for (BlockPos posn : setLocal) {
				if (frst) {
					frst = false;
					setAll.add(posn);
					BlockState bs = w.getBlockState(posn);
					if (!client && setBan.add(posn) && !wws.banPos(posn.asLong())) {
						setBan.forEach(p -> wws.unbanPos(p.asLong()));
						return false;
					}
					actioner.run(posn, bs);
				}
				if (actioner.isComplete()) {
					break;
				}
				for (Direction dir : getRandomizedDirections(random, true)) {
					BlockPos pos2 = posn.relative(dir);
					if (setAll.contains(pos2)) {
						continue;
					}
					BlockState bs2 = w.getBlockState(pos2);
					boolean cr = canReach(w, posn, dir);
					boolean eq = actioner.isValidState(bs2);
					if (cr && eq) {
						setLocal2.add(pos2);
						if (actioner.isValidPos(pos2)) {
							if (!client && setBan.add(pos2) && !wws.banPos(pos2.asLong())) {
								setBan.forEach(p -> wws.unbanPos(p.asLong()));
								return false;
							}
							actioner.run(pos2, bs2);
						}
					}

					setAll.add(pos2);
					if (actioner.isComplete()) {
						break;
					}
				}
			}
			setLocal = setLocal2;
		}
		if (actioner.isComplete()) {
			actioner.finish();
			if (!client)
				setBan.forEach(p -> wws.unbanPos(p.asLong()));
			return true;
		} else {
			actioner.fail();
			if (!client)
				setBan.forEach(p -> wws.unbanPos(p.asLong()));
			return false;
		}
	}

	public static void fillStates(Long2ObjectLinkedOpenHashMap<BlockState> states, Level world) {
		if (!world.isClientSide) {
			states.forEach((lpos, state) -> {
				world.setBlockAndUpdate(BlockPos.of(lpos), state);
			});
		}
	}

	public static void onBottleUse(Level w, Player p,
								   CallbackInfoReturnable<InteractionResultHolder<ItemStack>> ci, ItemStack stack) {
		BlockHitResult rt = rayTrace(w, p, ClipContext.Fluid.ANY);
		BlockPos pos = rt.getBlockPos();

		BottleFiller filler = new BottleFiller(w, Fluids.WATER, ci, stack);
		iterateFluidWay(WPOConfig.COMMON.maxBucketDist.get(), pos, filler);
	}

	public static void onBlockPlace(BlockEvent.EntityPlaceEvent e) {
		Level w = (Level) e.getLevel();
		BlockPos pos = e.getPos();
		BlockState oldState = e.getBlockSnapshot().getReplacedBlock();
		FluidState oldFluidState = oldState.getFluidState();
		Fluid oldFluid = oldFluidState.getType();
		BlockState newState = e.getPlacedBlock();
		Block newBlock = newState.getBlock();
		// if empty => do nothing
		if (oldFluidState.isEmpty()) {
			return;
		}
		// frost walker replaces water with water (idk why) => delete water (since it is created again from melting ice)
		// idk when FrostedIceBlock is placed...
		int frostWalkerLevel = EnchantmentHelper.getEnchantmentLevel(Enchantments.FROST_WALKER, (LivingEntity) e.getEntity());
		if (frostWalkerLevel > 0 && newBlock == Blocks.WATER){
			return; // does not create water since frost walker does not trigger on partially filled water blocks
		}
		// if sponge => do nothing (deletes water)
		if (newBlock instanceof SpongeBlock || newBlock instanceof WetSpongeBlock) {
			return; // TODO: check sponge interaction!
		}
		// if LiquidBlockContainer but NOT BucketPickup [e.g. Kelp/Seagrass] (SimpleWaterloggedBlock is both) => do nothing
		if (newBlock instanceof LiquidBlockContainer && !(newBlock instanceof SimpleWaterloggedBlock)) {
			return; // TODO check if creates water from nothing!
		}
		// if SimpleWaterloggedBlock (can be waterlogged with full water block only) => do nothing (sets itself to waterlogged)
		if (newBlock instanceof SimpleWaterloggedBlock && newState.getValue(BlockStateProperties.WATERLOGGED)) {
			return; // TODO check if creates water from nothing
		}
		// if level waterlogged (IBaseWL mixin) => set level in new block
		if (!canOnlyFullCube(newState) && newBlock instanceof IBaseWL && oldFluid.isSame(Fluids.WATER)) { // TODO why only water?
			newState = getUpdatedState(newState, oldFluidState.getAmount(), Fluids.WATER);
			w.setBlockAndUpdate(pos, newState); // FIXME: this somehow destroys the block and
			return;
		}

		// push water out
		FluidDisplacer displacer = new FluidDisplacer(w, e);
		iterateFluidWay(10, e.getPos(), displacer); // TODO make maxRange configurable
	}

	// ======================= PISTONS ======================= //

	public static void onPistonPre(PistonEvent.Pre e) {
		Level w = (Level) e.getLevel();
		if (w.isClientSide || e.isCanceled()) {
			return;
		}
		PistonStructureResolver ps = e.getStructureHelper();

		if (!Objects.requireNonNull(ps).resolve()) {
			return;
		}
		List<BlockPos> poslist = ps.getToDestroy();

		for (BlockPos pos : poslist) {
			BlockState state = w.getBlockState(pos);
			FluidState fs = state.getFluidState();

			if (!fs.isEmpty()) {

				PistonDisplacer displacer = new PistonDisplacer(w, e, state, ps);
				if (!iterateFluidWay(12, pos, displacer)) {
					e.setCanceled(true);
				}
			}
		}
	}
}