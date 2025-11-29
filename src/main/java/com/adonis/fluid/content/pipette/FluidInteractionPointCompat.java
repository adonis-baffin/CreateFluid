package com.adonis.fluid.content.pipette;

import com.adonis.fluid.CreateFluid;
import com.adonis.fluid.registry.CFBlock;
import com.simibubi.create.AllBlocks;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.fml.ModList;

/**
 * 流体交互点兼容性注册
 * 
 * 在这里集中注册所有支持的方块类型：
 * 1. 现有的方块使用现有的交互点类（保持原有逻辑不变）
 * 2. 新增模组的方块使用 GenericFluidInteractionPoint
 */
public class FluidInteractionPointCompat {
    
    // 模组ID常量
    public static final String CREATE_MOB_SPAWNER = "create_mob_spawner";
    public static final String CREATE_ENCHANTMENT_INDUSTRY = "create_enchantment_industry";
    public static final String CREATEADDITION = "createaddition";  // Create Crafts & Additions
    public static final String CREATE_DIESEL = "createdieselgenerators";  // 柴油动力
    
    /**
     * 初始化所有兼容性注册
     * 应在 FMLCommonSetupEvent 中调用
     */
    public static void init() {
        // 注册现有的方块（使用现有的交互点类）
        registerExistingBlocks();
        
        // 注册新模组的兼容
        registerModCompat();
        
        CreateFluid.LOGGER.info("Fluid interaction point compatibility initialized");
    }
    
    /**
     * 注册现有的方块 - 使用你已有的交互点类
     * 这些保持原有逻辑完全不变
     */
    private static void registerExistingBlocks() {
        // ===== Create 原版方块 =====
        
        // 置物台 - 使用现有的 DepotFluidInteractionPoint
        FluidInteractionPointTypes.register(
            AllBlocks.DEPOT.get(),
            DepotFluidInteractionPoint::new
        );
        
        // 弹射置物台 - 使用现有的 DepotFluidInteractionPoint
        FluidInteractionPointTypes.register(
            AllBlocks.WEIGHTED_EJECTOR.get(),
            DepotFluidInteractionPoint::new
        );
        
        // 分液池 - 使用现有的 ItemDrainFluidInteractionPoint
        FluidInteractionPointTypes.register(
            AllBlocks.ITEM_DRAIN.get(),
            ItemDrainFluidInteractionPoint::new
        );
        
        // 工作盆
        FluidInteractionPointTypes.register(
            AllBlocks.BASIN.get(),
            FluidInteractionPoint::new
        );
        
        // 烈焰人燃烧室 - 保持原有的特殊处理
        FluidInteractionPointTypes.register(
            AllBlocks.BLAZE_BURNER.get(),
            FluidInteractionPoint::new
        );
        
        FluidInteractionPointTypes.register(
            AllBlocks.LIT_BLAZE_BURNER.get(),
            FluidInteractionPoint::new
        );
        
        // 传送带 - 使用条件注册，检查是否可传输物品
        FluidInteractionPointTypes.registerConditional(
            state -> AllBlocks.BELT.has(state) && 
                     com.simibubi.create.content.kinetics.belt.BeltBlock.canTransportObjects(state),
            FluidInteractionPoint::new,
            100
        );
        
        // ===== 原版方块 =====
        
        // 蜂巢/蜂箱
        FluidInteractionPointTypes.registerConditional(
            state -> state.getBlock() instanceof net.minecraft.world.level.block.BeehiveBlock,
            FluidInteractionPoint::new,
            100
        );
        
        // 炼药锅（所有类型）
        FluidInteractionPointTypes.register(Blocks.CAULDRON, FluidInteractionPoint::new);
        FluidInteractionPointTypes.register(Blocks.WATER_CAULDRON, FluidInteractionPoint::new);
        FluidInteractionPointTypes.register(Blocks.LAVA_CAULDRON, FluidInteractionPoint::new);
        FluidInteractionPointTypes.register(Blocks.POWDER_SNOW_CAULDRON, FluidInteractionPoint::new);
        
        // ===== 你自己模组的方块 =====
        
        // 流体接口
        if (CFBlock.FLUID_INTERFACE != null) {
            FluidInteractionPointTypes.register(
                CFBlock.FLUID_INTERFACE.get(),
                FluidInteractionPoint::new
            );
        }
        
        // 智能流体接口
        if (CFBlock.SMART_FLUID_INTERFACE != null) {
            FluidInteractionPointTypes.register(
                CFBlock.SMART_FLUID_INTERFACE.get(),
                FluidInteractionPoint::new
            );
        }
    }
    
    /**
     * 注册跨模组兼容 - 使用 GenericFluidInteractionPoint
     */
    private static void registerModCompat() {
        
        // ===== Create Mob Spawner - 动力刷怪笼 =====
        if (isModLoaded(CREATE_MOB_SPAWNER)) {
            // 动力刷怪笼 - 需要输入流体（经验等）
            FluidInteractionPointTypes.registerDeferred(
                CREATE_MOB_SPAWNER, "mechanical_spawner",
                (level, pos, state) -> new GenericFluidInteractionPoint(level, pos, state, FluidInteractionPoint.Mode.DEPOSIT)
            );
            
            CreateFluid.LOGGER.info("Registered Create Mob Spawner compatibility");
        }
        
        // ===== Create Enchantment Industry - 机械动力：附魔工业 =====
        if (isModLoaded(CREATE_ENCHANTMENT_INDUSTRY)) {
            // 烈焰人附魔室
            FluidInteractionPointTypes.registerDeferred(
                CREATE_ENCHANTMENT_INDUSTRY, "blaze_enchanter",
                (level, pos, state) -> new GenericFluidInteractionPoint(level, pos, state, FluidInteractionPoint.Mode.DEPOSIT)
            );
            
            // 祛魔器
            FluidInteractionPointTypes.registerDeferred(
                CREATE_ENCHANTMENT_INDUSTRY, "disenchanter",
                GenericFluidInteractionPoint::new
            );
            
            // 印刷机
            FluidInteractionPointTypes.registerDeferred(
                CREATE_ENCHANTMENT_INDUSTRY, "printer",
                GenericFluidInteractionPoint::new
            );
            
            // 复制器
            FluidInteractionPointTypes.registerDeferred(
                CREATE_ENCHANTMENT_INDUSTRY, "copier",
                GenericFluidInteractionPoint::new
            );
            
            CreateFluid.LOGGER.info("Registered Create Enchantment Industry compatibility");
        }
        
        // ===== Create Crafts & Additions =====
        if (isModLoaded(CREATEADDITION)) {
            // 带吸管的烈焰人燃烧室
            FluidInteractionPointTypes.registerDeferred(
                CREATEADDITION, "liquid_blaze_burner",
                (level, pos, state) -> new GenericFluidInteractionPoint(level, pos, state, FluidInteractionPoint.Mode.DEPOSIT)
            );
            
            CreateFluid.LOGGER.info("Registered Create Crafts & Additions compatibility");
        }
        
        // ===== Create Diesel Generators - 柴油动力 =====
        if (isModLoaded(CREATE_DIESEL)) {
            // 可控燃烧室
            FluidInteractionPointTypes.registerDeferred(
                CREATE_DIESEL, "modular_combustion_chamber",
                (level, pos, state) -> new GenericFluidInteractionPoint(level, pos, state, FluidInteractionPoint.Mode.DEPOSIT)
            );
            
            // 柴油引擎
            FluidInteractionPointTypes.registerDeferred(
                CREATE_DIESEL, "diesel_engine",
                (level, pos, state) -> new GenericFluidInteractionPoint(level, pos, state, FluidInteractionPoint.Mode.DEPOSIT)
            );
            
            // 大型柴油引擎
            FluidInteractionPointTypes.registerDeferred(
                CREATE_DIESEL, "huge_diesel_engine",
                (level, pos, state) -> new GenericFluidInteractionPoint(level, pos, state, FluidInteractionPoint.Mode.DEPOSIT)
            );
            
            // 蒸馏塔
            FluidInteractionPointTypes.registerDeferred(
                CREATE_DIESEL, "distillation_tower",
                GenericFluidInteractionPoint::new
            );
            
            CreateFluid.LOGGER.info("Registered Create Diesel Generators compatibility");
        }
    }
    
    private static boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }
}