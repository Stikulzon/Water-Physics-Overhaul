package net.skds.wpo.fluidphysics;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.FlowingFluid;
import net.skds.core.Events;
import net.skds.core.api.IWWS;
import net.skds.core.api.IWWSG;
import net.skds.core.api.multithreading.ITaskRunnable;
import net.skds.core.multithreading.MTHooks;
import net.skds.wpo.util.TaskBlocker;

import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListSet;

public class WorldWorkSet implements IWWS {
	public final IWWSG glob;
	public final ServerLevel world;

	public ConcurrentHashMap.KeySetView<Long, Boolean> excludedTasks = ConcurrentHashMap.newKeySet();

	private final ConcurrentHashMap.KeySetView<Long, Boolean> lockedEq = ConcurrentHashMap.newKeySet();
	private final ConcurrentHashMap<Long, Integer> ntt = new ConcurrentHashMap<>();
	private static final Comparator<FluidTask> comp = (k1, k2) -> {
        if (k1.pos == k2.pos && k1.owner == k2.owner) {
            return 0;
        }
        double dcomp = (k1.getPriority() - k2.getPriority());
        int comp = (int) dcomp;
        if (comp == 0) {
            comp = dcomp > 0 ? 1 : -1;
        }
        return comp;
    };
	private static final ConcurrentSkipListSet<FluidTask> TASKS = new ConcurrentSkipListSet<>(comp);
	private static final ConcurrentLinkedQueue<FluidTask> DELAYED_TASKS = new ConcurrentLinkedQueue<>();

	public WorldWorkSet(ServerLevel w, IWWSG owner) {
		world = w;
		glob = owner;
	}

	public void addNTTask(long l, int t) {
		ntt.put(l, t);
	}

	public void clearEqLock(long l) {
		lockedEq.remove(l);
	}

	public void addEqLock(long l) {
		lockedEq.add(l);
	}

	public boolean isEqLocked(long l) {
		return lockedEq.contains(l);
	}

	private void tickNTT(long pos, int t) {
		t--;
		if (t <= 0) {
			FluidTask task = new FluidTask.DefaultTask(this, pos);
			TASKS.add(task);
			ntt.remove(pos);
			clearEqLock(pos);
		} else {
			ntt.put(pos, t);
		}
	}

	public static ITaskRunnable nextTask(int i) {
		if (i > 3) {
			return null;
		}
		if (MTHooks.COUNTS > 0 || Events.getRemainingTickTimeMilis() > MTHooks.TIME) {
			MTHooks.COUNTS--;
			for (FluidTask t : DELAYED_TASKS) {
				if (TaskBlocker.test(i, t)) {
					DELAYED_TASKS.remove(t);
					t.worker = i;
					return t;
				}
			}
			boolean tested = false;
			FluidTask task;
			while ((task = TASKS.pollFirst()) != null) {
				tested = TaskBlocker.test(i, task);
				if (tested) {
					task.worker = i;
					return task;
				} else {
					DELAYED_TASKS.add(task);
				}
			}
		}
		return null;
	}

	public static void pushTask(FluidTask task) {
		TASKS.add(task);
	}

	// =========== Override ==========

	@Override
	public void tickIn() {
		excludedTasks.clear();
		ntt.forEach(this::tickNTT);
	}

	@Override
	public void tickOut() {

	}

	@Override
	public void close() {
		lockedEq.clear();
		ntt.forEach((lp, t) -> {
			BlockPos pos = BlockPos.of(lp);
			world.scheduleTick(pos, world.getFluidState(pos).getType(), t + 2);
		});
		ntt.clear();
		TASKS.forEach(t -> t.revoke(world));
		TASKS.clear();
		DELAYED_TASKS.forEach(t -> t.revoke(world));
		DELAYED_TASKS.clear();
	}

	@Override
	public IWWSG getG() {
		return glob;
	}
}