package net.skds.wpo;

import java.util.ArrayList;
import java.util.List;

import com.google.common.collect.Lists;
import net.minecraftforge.fml.config.ModConfig;
import org.apache.commons.lang3.tuple.Pair;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;

public class WPOConfig {

    public static final Main COMMON;
    private static final ForgeConfigSpec COMMON_SPEC;

    public static final int MAX_FLUID_LEVEL = 8;

    static {
        final Pair<Main, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder().configure(Main::new);
        COMMON_SPEC = specPair.getRight();
        COMMON = specPair.getLeft();
    }

    public static void init() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, COMMON_SPEC);
    }

    public static class Main {
        public final ForgeConfigSpec.IntValue maxSlideDist, maxEqDist, maxBucketDist;

        public Main(ForgeConfigSpec.Builder builder) {
            builder.comment("General Configuration").push("general");

            maxEqDist = builder
                    .comment("The distance over which water levels will equalize")
                    .translation("wpo.config.maxEqDist")
                    .defineInRange("maxEqualizeDistance", 16, 0, 256);

            maxSlideDist = builder
                    .comment("The maximum distance water will slide to reach lower ground")
                    .translation("wpo.config.maxSlideDist")
                    .defineInRange("maxSlidingDistance", 5, 0, 256);

            maxBucketDist = builder
                    .comment("Maximum horizontal bucket reach from click location (for water packet pickup)")
                    .translation("wpo.config.maxBucketDist")
                    .defineInRange("maxBucketDistance", 8, 0, MAX_FLUID_LEVEL);

            builder.pop();
        }
    }
}