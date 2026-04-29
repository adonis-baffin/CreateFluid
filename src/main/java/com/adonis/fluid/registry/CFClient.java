package com.adonis.fluid.registry;

import com.adonis.fluid.CreateFluid;
import com.adonis.fluid.client.FluidManifestItemDecorator;
import com.adonis.fluid.client.gui.StockpileSwitchScreen;
import com.adonis.fluid.item.BatonItemPropertyFunction;
import com.adonis.fluid.ponder.CFPonderPlugin;
import com.simibubi.create.content.redstone.thresholdSwitch.ThresholdSwitchBlockEntity;
import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = CreateFluid.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class CFClient {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        CFPartialModels.init();

        event.enqueueWork(() -> {
            // 设置渲染层
            ItemBlockRenderTypes.setRenderLayer(CFBlocks.FLUID_INTERFACE.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(CFBlocks.SMART_FLUID_INTERFACE.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(CFBlocks.COPPER_TAP.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(CFBlocks.PIPETTE.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(CFBlocks.FLUID_ATOMIZER.get(), RenderType.cutoutMipped());

            // 注册 Ponder 插件
            PonderIndex.addPlugin(new CFPonderPlugin());

            // 注册指挥棒的属性覆盖
            if (CFItems.BATON != null) {
                ItemProperties.register(CFItems.BATON.get(),
                        ResourceLocation.fromNamespaceAndPath(CreateFluid.MOD_ID, "selection_mode"),
                        new BatonItemPropertyFunction());
            }
        });
    }

    @SubscribeEvent
    public static void onItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> {
            if (stack.getItem() instanceof com.adonis.fluid.item.FluidManifestItem) {
                com.adonis.fluid.datacomponent.FluidManifestContent content = stack.get(com.adonis.fluid.registry.CFDataComponents.FLUID_MANIFEST.get());
                if (content != null && content.fluidId() != null) {
                    net.minecraft.world.level.material.Fluid fluid = net.minecraft.core.registries.BuiltInRegistries.FLUID.get(content.fluidId());
                    if (fluid != null) {
                        return IClientFluidTypeExtensions.of(fluid).getTintColor() | 0xFF000000;
                    }
                }
            }
            return 0xFFFFFFFF;
        }, CFItems.FLUID_MANIFEST.get());
    }

    @SubscribeEvent
    public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        // 注册细雪流体的客户端扩展（纹理）
        event.registerFluidType(new IClientFluidTypeExtensions() {
            @Override
            public ResourceLocation getStillTexture() {
                return ResourceLocation.fromNamespaceAndPath("minecraft", "block/powder_snow");
            }

            @Override
            public ResourceLocation getFlowingTexture() {
                return ResourceLocation.fromNamespaceAndPath("minecraft", "block/powder_snow");
            }
        }, CFFluids.POWDER_SNOW_TYPE.get());

    }

    @SubscribeEvent
    public static void registerItemDecorations(RegisterItemDecorationsEvent event) {
        event.register(CFItems.FLUID_MANIFEST.get(), FluidManifestItemDecorator.DECORATOR);
    }

    @EventBusSubscriber(modid = CreateFluid.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
    public static class StockpileClientEvents {

        @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
        public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            if (!event.getLevel().isClientSide()) return;
            if (event.getHand() != InteractionHand.MAIN_HAND) return;

            // 避免重复打开
            if (mc.screen instanceof StockpileSwitchScreen) return;

            ItemStack held = event.getItemStack();
            if (held.isEmpty() || !(held.getItem() instanceof com.adonis.fluid.item.BatonItem)) return;

            BlockEntity be = event.getLevel().getBlockEntity(event.getPos());
            if (!(be instanceof ThresholdSwitchBlockEntity tsBE)) return;

            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
            ScreenOpener.open(new StockpileSwitchScreen(tsBE));
        }
    }
}
