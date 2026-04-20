package com.adonis.fluid.util;

import com.simibubi.create.content.equipment.clipboard.ClipboardEntry;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ClipboardAddressUtil {
    private ClipboardAddressUtil() {
    }

    @Nullable
    public static String extractFirstAddress(ItemStack clipboardItem) {
        if (clipboardItem != null && !clipboardItem.isEmpty()) {
            List<List<ClipboardEntry>> pages = ClipboardEntry.readAll(clipboardItem);
            return pages.isEmpty() ? null : findFirstAddress(pages);
        } else {
            return null;
        }
    }

    private static String findFirstAddress(List<List<ClipboardEntry>> pages) {
        for (List<ClipboardEntry> page : pages) {
            for (ClipboardEntry entry : page) {
                String text = entry.text.getString();
                if (isValidAddress(text)) {
                    return stripAddressPrefix(text);
                }
            }
        }

        return null;
    }

    private static boolean isValidAddress(String text) {
        return text != null && text.startsWith("#") && !text.substring(1).isBlank();
    }

    private static String stripAddressPrefix(String text) {
        return text.substring(1).stripLeading();
    }

    public static boolean hasValidAddress(ItemStack clipboardItem) {
        return extractFirstAddress(clipboardItem) != null;
    }
}
