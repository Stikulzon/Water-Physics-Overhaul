package net.skds.wpo;

import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.PistonEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.skds.core.api.IWWSG;
import net.skds.core.events.OnWWSAttachEvent;
import net.skds.core.events.SyncTasksHookEvent;
import net.skds.core.multithreading.ThreadProvider;
import net.skds.wpo.fluidphysics.FFluidStatic;
import net.skds.wpo.fluidphysics.WorldWorkSet;
import net.skds.wpo.util.pars.ParsApplier;

public class Events {

	@SubscribeEvent
	public void onPistonEvent(PistonEvent.Pre e) {
		FFluidStatic.onPistonPre(e);
	}

	@SubscribeEvent
	public void onBucketEvent(FillBucketEvent e) {
		FFluidStatic.onBucketEvent(e);
	}

	@SubscribeEvent
	public void onBlockPlaceEvent(BlockEvent.EntityPlaceEvent e) {
		FFluidStatic.onBlockPlace(e);
	}

	@SubscribeEvent
	public void onWWSAttach(OnWWSAttachEvent e) {
		IWWSG wwsg = e.getWWS();
		Level world = e.getWorld();
		if (!world.isClientSide) {
			WorldWorkSet wws = new WorldWorkSet((ServerLevel) world, wwsg);
			wwsg.addWWS(wws);
		}
	}

	@SubscribeEvent
	public void onTagsUpdated(TagsUpdatedEvent e) {
		ParsApplier.refresh();
	}

	@SubscribeEvent
	public void onSyncMTHook(SyncTasksHookEvent e) {
		ThreadProvider.doSyncFork(WorldWorkSet::nextTask);
	}
}