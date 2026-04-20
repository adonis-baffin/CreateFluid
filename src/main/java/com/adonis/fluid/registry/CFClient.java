package com.adonis.fluid.registry;

import com.adonis.fluid.CreateFluid;
import com.adonis.fluid.block.CentrifugalPump.CentrifugalPumpRenderer;
import com.adonis.fluid.block.CopperSink.CopperSinkRenderer;
import com.adonis.fluid.block.GutterOutlet.GutterOutletRenderer;
import com.adonis.fluid.block.GutterOutlet.SmartGutterOutletRenderer;
import com.adonis.fluid.block.Pipette.PipetteRenderer;
import com.adonis.fluid.block.FluidInterface.FluidInterfaceRenderer;
import com.adonis.fluid.block.SmartFluidInterface.SmartFluidInterfaceRenderer;
import com.adonis.fluid.block.CopperTap.CopperTapRenderer;
import com.adonis.fluid.client.gui.StockpileSwitchScreen;
import com.adonis.fluid.handler.PipetteFluidInteractionPointHandler;
import com.adonis.fluid.item.BatonItemPropertyFunction;
import com.adonis.fluid.ponder.CFPonderPlugin;
import com.simibubi.create.content.redstone.thresholdSwitch.ThresholdSwitchBlockEntity;
import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = CreateFluid.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class CFClient {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        CFPartialModels.init();

        event.enqueueWork(() -> {
            // 设置渲染层
            ItemBlockRenderTypes.setRenderLayer(CFBlock.PIPETTE.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(CFBlock.FLUID_INTERFACE.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(CFBlock.SMART_FLUID_INTERFACE.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(CFBlock.CENTRIFUGAL_PUMP.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(CFBlock.COPPER_TAP.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(CFBlock.GUTTER_OUTLET.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(CFBlock.SMART_GUTTER_OUTLET.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(CFBlock.COPPER_SINK.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(CFBlock.REDSTONE_VALVE.get(), RenderType.cutoutMipped());
            ItemBlockRenderTypes.setRenderLayer(CFBlock.REDSTONE_TRIPLE_VALVE.get(), RenderType.cutoutMipped());

            // 注册方块实体渲染器
            BlockEntityRenderers.register(CFBlockEntity.PIPETTE.get(), PipetteRenderer::new);
            BlockEntityRenderers.register(CFBlockEntity.FLUID_INTERFACE.get(), FluidInterfaceRenderer::new);
            BlockEntityRenderers.register(CFBlockEntity.SMART_FLUID_INTERFACE.get(), SmartFluidInterfaceRenderer::new);
            BlockEntityRenderers.register(CFBlockEntity.CENTRIFUGAL_PUMP.get(), CentrifugalPumpRenderer::new);
            BlockEntityRenderers.register(CFBlockEntity.COPPER_TAP.get(), CopperTapRenderer::new);
            BlockEntityRenderers.register(CFBlockEntity.GUTTER_OUTLET.get(), GutterOutletRenderer::new);
            BlockEntityRenderers.register(CFBlockEntity.SMART_GUTTER_OUTLET.get(), SmartGutterOutletRenderer::new);
            BlockEntityRenderers.register(CFBlockEntity.COPPER_SINK.get(), CopperSinkRenderer::new);

            // 注册 Ponder 插件
            PonderIndex.addPlugin(new CFPonderPlugin());

            // 注册指挥棒的属性覆盖
            if (CFItem.BATON != null) {
                ItemProperties.register(CFItem.BATON.get(),
                        new ResourceLocation(CreateFluid.MODID, "selection_mode"),
                        new BatonItemPropertyFunction());
            }
        });
    }

    @Mod.EventBusSubscriber(modid = CreateFluid.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static class ClientForgeEvents {

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.END) {
                PipetteFluidInteractionPointHandler.tick();
            }
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
        public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            if (!event.getLevel().isClientSide()) return;
            if (event.getHand() != InteractionHand.MAIN_HAND) return;

            // Avoid duplicate openings
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