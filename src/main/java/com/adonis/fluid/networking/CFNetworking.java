package com.adonis.fluid.networking;

import com.adonis.fluid.packet.CopperFaucetParticlePacket;
import com.adonis.fluid.packet.PipetteFluidPlacementPacket;
import com.adonis.fluid.packet.PipetteParticlePacket;
import com.adonis.fluid.packet.QuartzLampTogglePacket;
import com.simibubi.create.AllPackets;
import net.minecraftforge.network.simple.SimpleChannel;

public class CFNetworking {

    public static void register() {
        SimpleChannel channel = AllPackets.getChannel();
        int id = 200;

        // 注册交互点配置包
        channel.registerMessage(id++, PipetteFluidPlacementPacket.class,
                (msg, buf) -> msg.write(buf),
                PipetteFluidPlacementPacket::new,
                (msg, ctxSupplier) -> {
                    msg.handle(ctxSupplier.get());
                    ctxSupplier.get().setPacketHandled(true);
                });

        // 注册客户端请求包
        channel.registerMessage(id++, PipetteFluidPlacementPacket.ClientBoundRequest.class,
                (msg, buf) -> msg.write(buf),
                PipetteFluidPlacementPacket.ClientBoundRequest::new,
                (msg, ctxSupplier) -> {
                    msg.handle(ctxSupplier.get());
                    ctxSupplier.get().setPacketHandled(true);
                });

        // 注册移液器粒子效果包
        channel.registerMessage(id++, PipetteParticlePacket.class,
                (msg, buf) -> msg.write(buf),
                PipetteParticlePacket::new,
                (msg, ctxSupplier) -> {
                    msg.handle(ctxSupplier.get());
                    ctxSupplier.get().setPacketHandled(true);
                });

        // 注册铜龙头粒子效果包
        channel.registerMessage(id++, CopperFaucetParticlePacket.class,
                (msg, buf) -> msg.write(buf),
                CopperFaucetParticlePacket::new,
                (msg, ctxSupplier) -> {
                    msg.handle(ctxSupplier.get());
                    ctxSupplier.get().setPacketHandled(true);
                });

        // 注册石英灯切换包
        channel.registerMessage(id++, QuartzLampTogglePacket.class,
                (msg, buf) -> msg.write(buf),
                QuartzLampTogglePacket::new,
                (msg, ctxSupplier) -> {
                    msg.handle(ctxSupplier.get());
                    ctxSupplier.get().setPacketHandled(true);
                });
    }
}