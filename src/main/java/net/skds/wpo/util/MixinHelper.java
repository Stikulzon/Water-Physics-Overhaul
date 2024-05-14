package net.skds.wpo.util;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;
import net.skds.wpo.WPOConfig;

import java.util.Objects;

public class MixinHelper {
    public static boolean shouldAffectBlock(Block block) {
        ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(block);
        if (WPOConfig.COMMON.affectedBlocks.get().isEmpty()) {
            return true; // Affect all blocks if the list is empty
        }
        for (String affectedBlock : WPOConfig.COMMON.affectedBlocks.get()) {
            if (affectedBlock.startsWith("#")) {
                // Check against tag ID
                String tagId = affectedBlock.substring(1);
                if (Objects.requireNonNull(ForgeRegistries.BLOCKS.tags())
                        .getTag(BlockTags.create(new ResourceLocation(tagId)))
                        .contains(block)) {
                    return true;
                }
            } else if (affectedBlock.equals(blockId.toString())) {
                return true; // Check against exact block ID
            }
        }
        return false;
    }
}
