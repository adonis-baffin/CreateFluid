package com.adonis.fluid.registry;

import com.adonis.fluid.CreateFluid;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.resources.ResourceLocation;

public class CFPartialModels {
    // 指向Create的机械臂模型文件
    public static final PartialModel PIPETTE_BASE = createPartialModel("mechanical_arm/base");
    public static final PartialModel PIPETTE_LOWER_ARM = createPartialModel("mechanical_arm/lower_body");
    public static final PartialModel PIPETTE_UPPER_ARM = createPartialModel("mechanical_arm/upper_body");
    public static final PartialModel PIPETTE_HEAD = createPartialModel("mechanical_arm/claw_base");
    public static final PartialModel PIPETTE_TIP = createPartialModel("mechanical_arm/upper_claw_grip");
    public static final PartialModel PIPETTE_NEEDLE = createPartialModel("mechanical_arm/lower_claw_grip");
    public static final PartialModel PIPETTE_COG = createPartialModel("mechanical_arm/cog");
    public static final PartialModel PIPETTE_HEAD_GOGGLES = createPartialModel("mechanical_arm/claw_base_goggles");

    private static PartialModel createPartialModel(String path) {
        // 使用Create的命名空间
        return PartialModel.of(new ResourceLocation("create", "block/" + path));
    }

    public static void init() {
        // 模型已经由Create加载
    }
}