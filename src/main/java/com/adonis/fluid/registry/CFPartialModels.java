package com.adonis.fluid.registry;

import com.adonis.fluid.CreateFluid;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.Create;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.resources.ResourceLocation;

public class CFPartialModels {
    private static boolean initialized = false;
    private static boolean useFallback = false;

    // 移液器模型
    public static PartialModel PIPETTE_COG;
    public static PartialModel PIPETTE_BASE;
    public static PartialModel PIPETTE_LOWER_ARM;
    public static PartialModel PIPETTE_UPPER_ARM;
    public static PartialModel PIPETTE_HEAD_EMPTY;
    public static PartialModel PIPETTE_HEAD_250;
    public static PartialModel PIPETTE_HEAD_500;
    public static PartialModel PIPETTE_HEAD_750;
    public static PartialModel PIPETTE_HEAD_1000;
    public static PartialModel PIPETTE_HEAD;

    // 装罐机模型
    public static PartialModel CAN_FILLER_TRAY;
    public static PartialModel CAN_FILLER_HATCH_OPEN;
    public static PartialModel CAN_FILLER_HATCH_CLOSED;

    public static void register() {
        // 在模组构造时调用，用于触发静态初始化
    }

    public static void init() {
        if (initialized) return;

        try {
            // 使用Create的现有模型作为基础
            PIPETTE_COG = AllPartialModels.ARM_COG;

            // 尝试加载自定义模型
            try {
                PIPETTE_BASE = createPartialModel("pipette/base");
                PIPETTE_LOWER_ARM = createPartialModel("pipette/lower_arm");
                PIPETTE_UPPER_ARM = createPartialModel("pipette/upper_arm");
                PIPETTE_HEAD_EMPTY = createPartialModel("pipette/head_empty");
                PIPETTE_HEAD_250 = createPartialModel("pipette/head_250");
                PIPETTE_HEAD_500 = createPartialModel("pipette/head_500");
                PIPETTE_HEAD_750 = createPartialModel("pipette/head_750");
                PIPETTE_HEAD_1000 = createPartialModel("pipette/head_1000");
                PIPETTE_HEAD = PIPETTE_HEAD_EMPTY;

            } catch (Exception e) {
                initFallbackModels();
            }

            // 注册铜罐模型到Create的包裹模型映射中
            // 这样 PackageEntity 渲染 CopperCanItem 时能找到正确的模型
            registerCopperCanModels();

            // 注册装罐机托盘和舱门模型
            CAN_FILLER_TRAY = createPartialModel("can_filler/tray");
            CAN_FILLER_HATCH_OPEN = createPartialModel("can_filler/hatch_open");
            CAN_FILLER_HATCH_CLOSED = createPartialModel("can_filler/hatch_closed");

            initialized = true;

        } catch (Exception e) {
            initFallbackModels();
        }
    }

    private static void registerCopperCanModels() {
        try {
            ResourceLocation fluidPackageId = ResourceLocation.fromNamespaceAndPath(CreateFluid.MOD_ID, "copper_can");
            ResourceLocation newCopperCanModel = ResourceLocation.fromNamespaceAndPath(CreateFluid.MOD_ID, "item/new_copper_can");

            PartialModel copperCanModel = PartialModel.of(newCopperCanModel);
            PartialModel creeperRigging = AllPartialModels.PACKAGE_RIGGING.get(Create.asResource("rare_creeper_package"));

            if (copperCanModel != null) {
                AllPartialModels.PACKAGES.put(fluidPackageId, copperCanModel);
            }
            if (creeperRigging != null) {
                AllPartialModels.PACKAGE_RIGGING.put(fluidPackageId, creeperRigging);
            }
        } catch (Exception e) {
            // 静默失败，包裹会显示为黑紫但功能正常
        }
    }

    private static void initFallbackModels() {
        // 使用Create的机械臂模型作为后备
        PIPETTE_COG = AllPartialModels.ARM_COG;
        PIPETTE_BASE = AllPartialModels.ARM_BASE;
        PIPETTE_LOWER_ARM = AllPartialModels.ARM_LOWER_BODY;
        PIPETTE_UPPER_ARM = AllPartialModels.ARM_UPPER_BODY;

        // 使用机械爪基础作为头部
        PIPETTE_HEAD_EMPTY = AllPartialModels.ARM_CLAW_BASE;
        PIPETTE_HEAD_250 = AllPartialModels.ARM_CLAW_BASE;
        PIPETTE_HEAD_500 = AllPartialModels.ARM_CLAW_BASE;
        PIPETTE_HEAD_750 = AllPartialModels.ARM_CLAW_BASE;
        PIPETTE_HEAD_1000 = AllPartialModels.ARM_CLAW_BASE;
        PIPETTE_HEAD = AllPartialModels.ARM_CLAW_BASE;

        useFallback = true;
        initialized = true;
    }

    private static PartialModel createPartialModel(String path) {
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(CreateFluid.MOD_ID, "block/" + path);
        return PartialModel.of(location);
    }

    /**
     * 根据流体量获取对应的头部模型
     */
    public static PartialModel getPipetteHeadForFluidAmount(int fluidAmount) {
        if (!initialized) {
            init();
        }

        if (useFallback) {
            return PIPETTE_HEAD_EMPTY != null ? PIPETTE_HEAD_EMPTY : AllPartialModels.ARM_CLAW_BASE;
        }

        try {
            if (fluidAmount >= 1000 && PIPETTE_HEAD_1000 != null) return PIPETTE_HEAD_1000;
            if (fluidAmount >= 750 && PIPETTE_HEAD_750 != null) return PIPETTE_HEAD_750;
            if (fluidAmount >= 500 && PIPETTE_HEAD_500 != null) return PIPETTE_HEAD_500;
            if (fluidAmount >= 250 && PIPETTE_HEAD_250 != null) return PIPETTE_HEAD_250;
            if (PIPETTE_HEAD_EMPTY != null) return PIPETTE_HEAD_EMPTY;
        } catch (Exception e) {
            // 静默处理
        }

        return AllPartialModels.ARM_CLAW_BASE;
    }

    public static boolean isUsingFallback() {
        return useFallback;
    }

    public static PartialModel getModelOrFallback(PartialModel model, PartialModel fallback) {
        if (model != null) {
            return model;
        }
        if (fallback != null) {
            return fallback;
        }
        return AllPartialModels.ARM_BASE;
    }
}