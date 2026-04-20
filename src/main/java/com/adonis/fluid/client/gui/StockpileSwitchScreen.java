package com.adonis.fluid.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllPackets;
import com.simibubi.create.content.redstone.thresholdSwitch.ConfigureThresholdSwitchPacket;
import com.simibubi.create.content.redstone.thresholdSwitch.ThresholdSwitchBlockEntity;
import com.simibubi.create.content.redstone.thresholdSwitch.ThresholdSwitchBlockEntity.ThresholdType;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import com.simibubi.create.foundation.gui.widget.ScrollInput;
import com.simibubi.create.foundation.gui.widget.SelectionScrollInput;
import com.simibubi.create.foundation.utility.CreateLang;
import com.simibubi.create.infrastructure.ponder.AllCreatePonderTags;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.createmod.catnip.gui.widget.AbstractSimiWidget;
import net.createmod.ponder.foundation.ui.PonderTagScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneTorchBlock;

import java.util.ArrayList;
import java.util.List;

public class StockpileSwitchScreen extends AbstractSimiScreen {

    // Layout constants
    private static final int INPUT_AREA_HEIGHT = 52;
    private static final int INPUT_GAP = 6;
    private static final int EDITBOX_HEIGHT = 18;
    private static final int EDITBOX_WIDTH = 56;

    // Widgets – mirrors of the original ThresholdSwitchScreen
    private ScrollInput offBelow;
    private ScrollInput onAbove;
    private SelectionScrollInput inStacks;
    private IconButton confirmButton;
    private IconButton flipSignals;

    private EditBox lowerLimitBox;
    private EditBox upperLimitBox;

    // State
    private final Component invertSignal =
            CreateLang.translateDirect("gui.threshold_switch.invert_signal");
    private final ItemStack renderedItem = new ItemStack(AllBlocks.THRESHOLD_SWITCH.get());
    private final AllGuiTextures background = AllGuiTextures.THRESHOLD_SWITCH;
    private final ThresholdSwitchBlockEntity blockEntity;
    private int lastModification = -1;

    public StockpileSwitchScreen(ThresholdSwitchBlockEntity be) {
        super(CreateLang.translateDirect("gui.threshold_switch.title"));
        this.blockEntity = be;
    }

    @Override
    protected void init() {
        setWindowSize(background.getWidth(), background.getHeight() + INPUT_AREA_HEIGHT);
        setWindowOffset(-20, 0);
        super.init();

        int x = guiLeft;
        int y = guiTop;

        inStacks = (SelectionScrollInput) new SelectionScrollInput(x + 100, y + 23, 52, 42)
                .forOptions(List.of(
                        CreateLang.translateDirect("schedule.condition.threshold.items"),
                        CreateLang.translateDirect("schedule.condition.threshold.stacks")))
                .titled(CreateLang.translateDirect("schedule.condition.threshold.item_measure"))
                .calling(state -> {
                    lastModification = 0;
                    syncTextFieldsFromScrollInputs(true);
                })
                .setState(blockEntity.inStacks ? 1 : 0);

        offBelow = new ScrollInput(x + 48, y + 47, 1, 18)
                .withRange(blockEntity.getMinLevel(), blockEntity.getMaxLevel() + 1 - getValueStep())
                .titled(CreateLang.translateDirect("gui.threshold_switch.lower_threshold"))
                .calling(state -> {
                    lastModification = 0;
                    int step = getValueStep();
                    if (onAbove.getState() / step == 0 && state / step == 0) return;
                    if (onAbove.getState() / step <= state / step) {
                        onAbove.setState((state + step) / step * step);
                        onAbove.onChanged();
                    }
                    syncTextFieldsFromScrollInputs(false);
                })
                .withStepFunction(sc -> sc.shift ? 10 * getValueStep() : getValueStep())
                .setState(blockEntity.offWhenBelow);

        onAbove = new ScrollInput(x + 48, y + 23, 1, 18)
                .withRange(blockEntity.getMinLevel() + getValueStep(), blockEntity.getMaxLevel() + 1)
                .titled(CreateLang.translateDirect("gui.threshold_switch.upper_threshold"))
                .calling(state -> {
                    lastModification = 0;
                    int step = getValueStep();
                    if (offBelow.getState() / step == 0 && state / step == 0) return;
                    if (offBelow.getState() / step >= state / step) {
                        offBelow.setState((state - step) / step * step);
                        offBelow.onChanged();
                    }
                    syncTextFieldsFromScrollInputs(false);
                })
                .withStepFunction(sc -> sc.shift ? 10 * getValueStep() : getValueStep())
                .setState(blockEntity.onWhenAbove);

        onAbove.onChanged();
        offBelow.onChanged();

        addRenderableWidget(onAbove);
        addRenderableWidget(offBelow);
        addRenderableWidget(inStacks);

        confirmButton = new IconButton(
                x + background.getWidth() - 33,
                y + background.getHeight() - 24,
                AllIcons.I_CONFIRM);
        confirmButton.withCallback(this::onClose);
        addRenderableWidget(confirmButton);

        flipSignals = new IconButton(
                x + background.getWidth() - 62,
                y + background.getHeight() - 24,
                AllIcons.I_FLIP);
        flipSignals.withCallback(() -> send(!blockEntity.isInverted()));
        flipSignals.setToolTip(invertSignal);
        addRenderableWidget(flipSignals);

        updateInputBoxes();

        // Two new threshold EditBoxes (integer only)
        int inputStartY = y + background.getHeight() + INPUT_GAP;

        lowerLimitBox = new EditBox(
                font,
                x + 16,
                inputStartY + 22,
                EDITBOX_WIDTH,
                EDITBOX_HEIGHT,
                Component.translatable("fluid.gui.stockpile_switch.lower_input"));
        lowerLimitBox.setMaxLength(10);
        lowerLimitBox.setFilter(this::isIntegerText);
        lowerLimitBox.setValue(Integer.toString(offBelow.getState() / getValueStep()));

        upperLimitBox = new EditBox(
                font,
                x + background.getWidth() - EDITBOX_WIDTH - 16,
                inputStartY + 22,
                EDITBOX_WIDTH,
                EDITBOX_HEIGHT,
                Component.translatable("fluid.gui.stockpile_switch.upper_input"));
        upperLimitBox.setMaxLength(10);
        upperLimitBox.setFilter(this::isIntegerText);
        upperLimitBox.setValue(Integer.toString(onAbove.getState() / getValueStep()));

        addRenderableWidget(lowerLimitBox);
        addRenderableWidget(upperLimitBox);

        syncTextFieldsFromScrollInputs(true);
    }


    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int x = guiLeft;
        int y = guiTop;

        background.render(graphics, x, y);
        graphics.drawString(font,
                title,
                x + background.getWidth() / 2 - font.width(title) / 2,
                y + 4,
                0x592424,
                false);

        ThresholdType type = blockEntity.getTypeOfCurrentTarget();
        boolean forItems = type == ThresholdType.ITEM;
        AllGuiTextures inputBg = forItems
                ? AllGuiTextures.THRESHOLD_SWITCH_ITEMCOUNT_INPUTS
                : AllGuiTextures.THRESHOLD_SWITCH_MISC_INPUTS;

        inputBg.render(graphics, x + 44, y + 21);
        inputBg.render(graphics, x + 44, y + 21 + 24);

        int valueStep = 1;
        boolean stacks = inStacks.getState() == 1;
        if (type == ThresholdType.FLUID) valueStep = 1000;

        if (forItems) {
            Component suffix = inStacks.getState() == 0
                    ? CreateLang.translateDirect("schedule.condition.threshold.items")
                    : CreateLang.translateDirect("schedule.condition.threshold.stacks");
            valueStep = inStacks.getState() == 0 ? 1 : 64;
            graphics.drawString(font, suffix, x + 105, y + 28, 0xFFFFFFFF, true);
            graphics.drawString(font, suffix, x + 105, y + 28 + 24, 0xFFFFFFFF, true);
        }

        graphics.drawString(font,
                Component.literal("\u2265 " + (type == ThresholdType.UNSUPPORTED ? ""
                        : forItems ? onAbove.getState() / valueStep
                        : blockEntity.format(onAbove.getState() / valueStep, stacks).getString())),
                x + 53, y + 28, 0xFFFFFFFF, true);

        graphics.drawString(font,
                Component.literal("\u2264 " + (type == ThresholdType.UNSUPPORTED ? ""
                        : forItems ? offBelow.getState() / valueStep
                        : blockEntity.format(offBelow.getState() / valueStep, stacks).getString())),
                x + 53, y + 28 + 24, 0xFFFFFFFF, true);

        GuiGameElement.of(renderedItem)
                .<GuiGameElement.GuiRenderBuilder>at(x + background.getWidth() + 6,
                        y + background.getHeight() - 56, -200)
                .scale(5)
                .render(graphics);

        int itemX = x + 13;
        int itemY = y + 80;
        ItemStack displayItem = blockEntity.getDisplayItemForScreen();
        GuiGameElement.of(displayItem.isEmpty() ? new ItemStack(Items.BARRIER) : displayItem)
                .<GuiGameElement.GuiRenderBuilder>at(itemX, itemY, 0)
                .render(graphics);

        int torchX = x + 23;
        int torchY = y + 24;
        boolean highlightTop = blockEntity.isInverted() ^ blockEntity.isPowered();
        AllGuiTextures.THRESHOLD_SWITCH_CURRENT_STATE.render(graphics, torchX - 3,
                torchY - 4 + (highlightTop ? 0 : 24));

        PoseStack ms = graphics.pose();
        ms.pushPose();
        ms.translate(torchX - 5, torchY + 14, 200);
        TransformStack.of(ms)
                .rotateXDegrees(-22.5f)
                .rotateYDegrees(45);
        for (boolean power : Iterate.trueAndFalse) {
            GuiGameElement.of(Blocks.REDSTONE_TORCH.defaultBlockState()
                            .setValue(RedstoneTorchBlock.LIT, blockEntity.isInverted() ^ power))
                    .scale(20)
                    .render(graphics);
            ms.translate(0, 26, 0);
        }
        ms.popPose();

        int inputStartY = y + background.getHeight() + INPUT_GAP;
        graphics.drawString(font,
                Component.translatable("fluid.gui.stockpile_switch.input_title"),
                x,
                inputStartY,
                0xFFAAAAAA,
                false);
        graphics.drawString(font,
                Component.translatable("fluid.gui.stockpile_switch.lower_input"),
                x + 16,
                inputStartY + 11,
                0xFFCCCCCC,
                false);
        graphics.drawString(font,
                Component.translatable("fluid.gui.stockpile_switch.upper_input"),
                x + background.getWidth() - 16 - font.width(Component.translatable("fluid.gui.stockpile_switch.upper_input")),
                inputStartY + 11,
                0xFFCCCCCC,
                false);

        Component unit = getCurrentUnitText();
        int unitY = inputStartY + 28;
        graphics.drawString(font, unit, x + 16 + EDITBOX_WIDTH + 4, unitY, 0xFFCCCCCC, false);
        graphics.drawString(font, unit,
                x + background.getWidth() - EDITBOX_WIDTH - 16 + EDITBOX_WIDTH + 4,
                unitY,
                0xFFCCCCCC,
                false);

        if (mouseX >= itemX && mouseX < itemX + 16 && mouseY >= itemY && mouseY < itemY + 16) {
            ArrayList<Component> list = new ArrayList<>();
            if (displayItem.isEmpty()) {
                list.add(CreateLang.translateDirect("gui.threshold_switch.not_attached"));
                list.add(CreateLang.translateDirect("display_link.view_compatible")
                        .withStyle(ChatFormatting.DARK_GRAY));
            } else {
                list.add(displayItem.getHoverName());
                if (type == ThresholdType.UNSUPPORTED) {
                    list.add(CreateLang.translateDirect("gui.threshold_switch.incompatible")
                            .withStyle(ChatFormatting.GRAY));
                    list.add(CreateLang.translateDirect("display_link.view_compatible")
                            .withStyle(ChatFormatting.DARK_GRAY));
                } else {
                    CreateLang.translate("gui.threshold_switch.currently",
                                    blockEntity.format(blockEntity.currentLevel / valueStep, stacks))
                            .style(ChatFormatting.DARK_AQUA)
                            .addTo(list);
                    if (blockEntity.currentMinLevel / valueStep == 0)
                        CreateLang.translate("gui.threshold_switch.range_max",
                                        blockEntity.format(blockEntity.currentMaxLevel / valueStep, stacks))
                                .style(ChatFormatting.GRAY)
                                .addTo(list);
                    else
                        CreateLang.translate("gui.threshold_switch.range",
                                        blockEntity.currentMinLevel / valueStep,
                                        blockEntity.format(blockEntity.currentMaxLevel / valueStep, stacks))
                                .style(ChatFormatting.GRAY)
                                .addTo(list);
                    list.add(CreateLang.translateDirect("display_link.view_compatible")
                            .withStyle(ChatFormatting.DARK_GRAY));
                }
            }
            graphics.renderComponentTooltip(font, list, mouseX, mouseY);
            return;
        }

        for (boolean power : Iterate.trueAndFalse) {
            int thisTorchY = power ? torchY : torchY + 26;
            if (mouseX >= torchX && mouseX < torchX + 16 && mouseY >= thisTorchY && mouseY < thisTorchY + 16) {
                graphics.renderComponentTooltip(font,
                        List.of(CreateLang.translate(
                                        power ^ blockEntity.isInverted()
                                                ? "gui.threshold_switch.power_on_when"
                                                : "gui.threshold_switch.power_off_when")
                                .color(AbstractSimiWidget.HEADER_RGB)
                                .component()),
                        mouseX, mouseY);
                return;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int itemX = guiLeft + 13;
        int itemY = guiTop + 80;
        if (mouseX >= itemX && mouseX < itemX + 16 && mouseY >= itemY && mouseY < itemY + 16) {
            ScreenOpener.open(new PonderTagScreen(AllCreatePonderTags.THRESHOLD_SWITCH_TARGETS));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void tick() {
        super.tick();
        if (lastModification >= 0) lastModification++;
        if (lastModification >= 20) {
            lastModification = -1;
            send(blockEntity.isInverted());
        }
        if (inStacks != null)
            updateInputBoxes();
    }

    @Override
    public void removed() {
        send(blockEntity.isInverted());
    }

    protected void send(boolean invert) {
        applyTextFieldValues();

        AllPackets.getChannel().sendToServer(
                new ConfigureThresholdSwitchPacket(
                        blockEntity.getBlockPos(),
                        offBelow.getState(),
                        onAbove.getState(),
                        invert,
                        inStacks.getState() == 1));
    }

    private void updateInputBoxes() {
        ThresholdType type = blockEntity.getTypeOfCurrentTarget();
        boolean forItems = type == ThresholdType.ITEM;
        final int step = getValueStep();

        inStacks.active = inStacks.visible = forItems;
        onAbove.setWidth(forItems ? 48 : 103);
        offBelow.setWidth(forItems ? 48 : 103);
        onAbove.visible = type != ThresholdType.UNSUPPORTED;
        offBelow.visible = type != ThresholdType.UNSUPPORTED;

        int min = blockEntity.currentMinLevel + step;
        int max = blockEntity.currentMaxLevel;
        onAbove.withRange(min, max + 1);
        int rounded = Mth.clamp((onAbove.getState() / step) * step, min, max);
        if (rounded != onAbove.getState()) { onAbove.setState(rounded); onAbove.onChanged(); }

        min = blockEntity.currentMinLevel;
        max = blockEntity.currentMaxLevel - step;
        offBelow.withRange(min, max + 1);
        rounded = Mth.clamp((offBelow.getState() / step) * step, min, max);
        if (rounded != offBelow.getState()) { offBelow.setState(rounded); offBelow.onChanged(); }
    }

    private int getValueStep() {
        if (blockEntity.getTypeOfCurrentTarget() == ThresholdType.FLUID) return 1000;
        if (inStacks != null && inStacks.getState() == 1) return 64;
        return 1;
    }

    private boolean isIntegerText(String text) {
        return text.isEmpty() || text.chars().allMatch(Character::isDigit);
    }

    private Integer parseInteger(EditBox box) {
        String value = box.getValue();
        if (value == null || value.isEmpty()) return null;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void applyTextFieldValues() {
        int step = getValueStep();

        Integer lower = parseInteger(lowerLimitBox);
        Integer upper = parseInteger(upperLimitBox);

        int minUpper = blockEntity.currentMinLevel + step;
        int maxUpper = blockEntity.currentMaxLevel;
        int minLower = blockEntity.currentMinLevel;
        int maxLower = blockEntity.currentMaxLevel - step;

        if (upper != null) {
            int snappedUpper = upper * step;
            onAbove.setState(Mth.clamp(snappedUpper, minUpper, maxUpper));
        }

        if (lower != null) {
            int snappedLower = lower * step;
            offBelow.setState(Mth.clamp(snappedLower, minLower, maxLower));
        }

        if (offBelow.getState() >= onAbove.getState()) {
            if (upper != null) {
                offBelow.setState(Mth.clamp(onAbove.getState() - step, minLower, maxLower));
            } else {
                onAbove.setState(Mth.clamp(offBelow.getState() + step, minUpper, maxUpper));
            }
        }

        onAbove.onChanged();
        offBelow.onChanged();
        syncTextFieldsFromScrollInputs(true);
    }

    private void syncTextFieldsFromScrollInputs(boolean force) {
        int step = getValueStep();

        if (lowerLimitBox != null && (force || !lowerLimitBox.isFocused())) {
            if (!lowerLimitBox.getValue().isEmpty()) {
                String expected = Integer.toString(offBelow.getState() / step);
                if (!expected.equals(lowerLimitBox.getValue())) lowerLimitBox.setValue(expected);
            }
        }

        if (upperLimitBox != null && (force || !upperLimitBox.isFocused())) {
            if (!upperLimitBox.getValue().isEmpty()) {
                String expected = Integer.toString(onAbove.getState() / step);
                if (!expected.equals(upperLimitBox.getValue())) upperLimitBox.setValue(expected);
            }
        }
    }

    private Component getCurrentUnitText() {
        ThresholdType type = blockEntity.getTypeOfCurrentTarget();
        if (type == ThresholdType.FLUID) {
            return Component.translatable("fluid.gui.stockpile_switch.unit.bucket");
        }
        if (type == ThresholdType.ITEM) {
            return inStacks.getState() == 1
                    ? CreateLang.translateDirect("schedule.condition.threshold.stacks")
                    : CreateLang.translateDirect("schedule.condition.threshold.items");
        }
        return Component.empty();
    }
}
