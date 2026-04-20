package com.adonis.fluid.goggle;

import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.List;

public class PackagerGoggleInfo {
    public static void addToTooltip(List<Component> tooltip, String address, boolean isRepackager) {
        if (isRepackager) {
            CreateLang.builder().translate("goggles.repackager_title").style(ChatFormatting.WHITE).forGoggles(tooltip);
            if (address != null && !address.isBlank()) {
                CreateLang.builder().translate("goggles.address_label").style(ChatFormatting.GRAY).forGoggles(tooltip, 1);
                CreateLang.builder().text(address).style(ChatFormatting.GOLD).forGoggles(tooltip, 1);
            }
        } else {
            CreateLang.builder().translate("goggles.packager_title").style(ChatFormatting.WHITE).forGoggles(tooltip);
            CreateLang.builder().translate("goggles.address_label").style(ChatFormatting.GRAY).forGoggles(tooltip);
            if (address != null && !address.isBlank()) {
                CreateLang.builder().text(address).style(ChatFormatting.GOLD).forGoggles(tooltip, 1);
            } else {
                CreateLang.builder().translate("goggles.no_address").style(ChatFormatting.DARK_GRAY).forGoggles(tooltip, 1);
            }
        }
    }
}
