package net.skds.wpo.mixins.block;

import net.skds.core.api.IBlockExtraStates;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.skds.wpo.registry.BlockStateProps;
import net.skds.wpo.util.interfaces.IBaseWL;

import net.minecraft.world.level.block.state.BlockBehaviour.Properties;

@Mixin(value = { DoorBlock.class, FenceGateBlock.class, LeavesBlock.class })
public class ExtraWLBlockMixin extends Block implements IBaseWL, SimpleWaterloggedBlock, IBlockExtraStates {

	public ExtraWLBlockMixin(Properties properties) {
		super(properties);
	}

	public void customStatesRegister(Block b, StateDefinition.Builder<Block, BlockState> builder) {

		builder.add(BlockStateProps.FFLUID_LEVEL);
		try {
			builder.add(BlockStateProperties.WATERLOGGED);
		} catch (Exception e) {
		}
	}

	@Override
	public void fixDS() { // TODO what is this doing? is this ever called?
		this.registerDefaultState(this.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, Boolean.valueOf(false)));
	}
}