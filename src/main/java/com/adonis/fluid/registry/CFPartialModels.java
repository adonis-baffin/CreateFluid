package com.adonis.fluid.registry;

import com.adonis.fluid.CreateFluid;
import com.simibubi.create.AllPartialModels;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.resources.ResourceLocation;

public class CFPartialModels {
    // 齿轮和底座继续使用Create的模型
    public static final PartialModel PIPETTE_COG = AllPartialModels.ARM_COG;


    // 使用自定义的移液器模型
    public static final PartialModel PIPETTE_BASE = createPartialModel("pipette/base");
    public static final PartialModel PIPETTE_LOWER_ARM = createPartialModel("pipette/lower_arm");
    public static final PartialModel PIPETTE_UPPER_ARM = createPartialModel("pipette/upper_arm");
    public static final PartialModel PIPETTE_HEAD = createPartialModel("pipette/head"); // 包含针头和玻璃室

    private static PartialModel createPartialModel(String path) {
        return PartialModel.of(new ResourceLocation(CreateFluid.MODID, "block/" + path));
    }

    public static void init() {
        // 模型初始化
    }
}