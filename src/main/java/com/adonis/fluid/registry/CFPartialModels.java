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

    // 不同流体量的头部模型
    public static final PartialModel PIPETTE_HEAD_EMPTY = createPartialModel("pipette/head_empty");
    public static final PartialModel PIPETTE_HEAD_250 = createPartialModel("pipette/head_250");
    public static final PartialModel PIPETTE_HEAD_500 = createPartialModel("pipette/head_500");
    public static final PartialModel PIPETTE_HEAD_750 = createPartialModel("pipette/head_750");
    public static final PartialModel PIPETTE_HEAD_1000 = createPartialModel("pipette/head_1000");

    // 保留原始头部作为默认（向后兼容）
    public static final PartialModel PIPETTE_HEAD = PIPETTE_HEAD_EMPTY;

    private static PartialModel createPartialModel(String path) {
        return PartialModel.of(new ResourceLocation(CreateFluid.MODID, "block/" + path));
    }

    /**
     * 根据流体量获取对应的头部模型
     * @param fluidAmount 流体量(mB)
     * @return 对应的头部模型
     */
    public static PartialModel getPipetteHeadForFluidAmount(int fluidAmount) {
        if (fluidAmount >= 1000) return PIPETTE_HEAD_1000;
        if (fluidAmount >= 750) return PIPETTE_HEAD_750;
        if (fluidAmount >= 500) return PIPETTE_HEAD_500;
        if (fluidAmount >= 250) return PIPETTE_HEAD_250;
        return PIPETTE_HEAD_EMPTY;
    }

    public static void init() {
        // 模型初始化
    }
}