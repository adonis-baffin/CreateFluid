package com.adonis.fluid.registry;

import com.adonis.fluid.CreateFluid;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;

public class CFPartialModels {
    // 动力移液器的各个部分
    public static final PartialModel PIPETTE_BASE = block("pipette/base");
    public static final PartialModel PIPETTE_LOWER_ARM = block("pipette/lower_arm");
    public static final PartialModel PIPETTE_UPPER_ARM = block("pipette/upper_arm");
    public static final PartialModel PIPETTE_HEAD = block("pipette/head");
    public static final PartialModel PIPETTE_TIP = block("pipette/tip");
    public static final PartialModel PIPETTE_NEEDLE = block("pipette/needle");
    public static final PartialModel PIPETTE_COG = block("pipette/cog");

    // 工程师护目镜版本（可选）
    public static final PartialModel PIPETTE_HEAD_GOGGLES = block("pipette/head_goggles");

    private static PartialModel block(String path) {
        return PartialModel.of(CreateFluid.asResource("block/" + path));
    }

    public static void init() {
        // 确保所有模型都被正确加载
        CreateFluid.LOGGER.info("Initializing partial models...");
        try {
            PIPETTE_BASE.get();
            PIPETTE_LOWER_ARM.get();
            PIPETTE_UPPER_ARM.get();
            PIPETTE_HEAD.get();
            PIPETTE_TIP.get();
            PIPETTE_NEEDLE.get();
            PIPETTE_COG.get();
            PIPETTE_HEAD_GOGGLES.get();
            CreateFluid.LOGGER.info("All partial models loaded successfully!");
        } catch (Exception e) {
            CreateFluid.LOGGER.error("Failed to load partial models: ", e);
        }
    }
}