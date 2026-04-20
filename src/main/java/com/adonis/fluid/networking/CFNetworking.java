package com.adonis.fluid.networking;

import com.adonis.fluid.packet.*;
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
        channel.registerMessage(id++, CopperTapParticlePacket.class,
                (msg, buf) -> msg.write(buf),
                CopperTapParticlePacket::new,
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

        // 注册离心泵模式切换包
        channel.registerMessage(id++, CentrifugalPumpModeTogglePacket.class,
                (msg, buf) -> msg.write(buf),
                CentrifugalPumpModeTogglePacket::new,
                (msg, ctxSupplier) -> {
                    msg.handle(ctxSupplier.get());
                    ctxSupplier.get().setPacketHandled(true);
                });

        // 注册动力臂交互点同步包（客户端接收）
        channel.registerMessage(id++, ArmInteractionPointSyncPacket.class,
                (msg, buf) -> msg.write(buf),
                ArmInteractionPointSyncPacket::new,
                (msg, ctxSupplier) -> {
                    msg.handle(ctxSupplier.get());
                    ctxSupplier.get().setPacketHandled(true);
                });

        // Packager 状态切换包
        channel.registerMessage(id++, com.adonis.fluid.packet.PackagerTogglePacket.class,
                (msg, buf) -> msg.write(buf),
                com.adonis.fluid.packet.PackagerTogglePacket::new,
                (msg, ctxSupplier) -> {
                    msg.handle(ctxSupplier.get());
                    ctxSupplier.get().setPacketHandled(true);
                });

        // Packager 地址清除包
        channel.registerMessage(id++, com.adonis.fluid.packet.PackagerClearAddressPacket.class,
                (msg, buf) -> msg.write(buf),
                com.adonis.fluid.packet.PackagerClearAddressPacket::new,
                (msg, ctxSupplier) -> {
                    msg.handle(ctxSupplier.get());
                    ctxSupplier.get().setPacketHandled(true);
                });

        // Clipboard 设置地址包
        channel.registerMessage(id++, com.adonis.fluid.packet.ClipboardSetAddressPacket.class,
                (msg, buf) -> msg.write(buf),
                com.adonis.fluid.packet.ClipboardSetAddressPacket::new,
                (msg, ctxSupplier) -> {
                    msg.handle(ctxSupplier.get());
                    ctxSupplier.get().setPacketHandled(true);
                });

        // Clipboard 地址粒子效果包
        channel.registerMessage(id++, com.adonis.fluid.packet.ClipboardAddressParticlePacket.class,
                (msg, buf) -> msg.write(buf),
                com.adonis.fluid.packet.ClipboardAddressParticlePacket::new,
                (msg, ctxSupplier) -> {
                    msg.handle(ctxSupplier.get());
                    ctxSupplier.get().setPacketHandled(true);
                });

        // Frogport 连接包
        channel.registerMessage(id++, com.adonis.fluid.packet.FrogportConnectionPacket.class,
                (msg, buf) -> msg.write(buf),
                com.adonis.fluid.packet.FrogportConnectionPacket::new,
                (msg, ctxSupplier) -> {
                    msg.handle(ctxSupplier.get());
                    ctxSupplier.get().setPacketHandled(true);
                });

        // Frogport 连接反馈包
        channel.registerMessage(id++, com.adonis.fluid.packet.FrogportConnectionFeedbackPacket.class,
                (msg, buf) -> msg.write(buf),
                com.adonis.fluid.packet.FrogportConnectionFeedbackPacket::new,
                (msg, ctxSupplier) -> {
                    msg.handle(ctxSupplier.get());
                    ctxSupplier.get().setPacketHandled(true);
                });

        // Mailbox-Station 连接包
        channel.registerMessage(id++, com.adonis.fluid.packet.MailboxStationConnectionPacket.class,
                (msg, buf) -> msg.write(buf),
                com.adonis.fluid.packet.MailboxStationConnectionPacket::new,
                (msg, ctxSupplier) -> {
                    msg.handle(ctxSupplier.get());
                    ctxSupplier.get().setPacketHandled(true);
                });

        // Mailbox-Station 断开连接包
        channel.registerMessage(id++, com.adonis.fluid.packet.MailboxStationDisconnectPacket.class,
                (msg, buf) -> msg.write(buf),
                com.adonis.fluid.packet.MailboxStationDisconnectPacket::new,
                (msg, ctxSupplier) -> {
                    msg.handle(ctxSupplier.get());
                    ctxSupplier.get().setPacketHandled(true);
                });
    }
}