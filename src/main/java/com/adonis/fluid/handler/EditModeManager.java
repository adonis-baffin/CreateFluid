package com.adonis.fluid.handler;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

public class EditModeManager {
    private static EditMode currentMode = EditMode.NONE;
    private static final Map<EditMode, ModeHandler> modeHandlers = new HashMap<>();

    public static EditMode getCurrentMode() {
        return currentMode;
    }

    public static boolean isInEditMode() {
        return currentMode != EditMode.NONE;
    }

    public static boolean canEnterMode(EditMode mode, Player player, Level level) {
        if (mode == EditMode.NONE) {
            return false;
        } else if (!isInEditMode()) {
            return true;
        } else if (currentMode == mode) {
            return true;
        } else {
            ModeHandler currentHandler = modeHandlers.get(currentMode);
            return currentHandler != null && currentHandler.canExit(null, player, level);
        }
    }

    public static boolean enterMode(EditMode mode, BlockPos pos, Player player, Level level) {
        if (mode == EditMode.NONE) {
            return false;
        } else if (!canEnterMode(mode, player, level)) {
            return false;
        } else {
            if (isInEditMode()) {
                exitMode(player, level);
            }

            // 同时退出旧的选择模式
            if (BatonInteractionHandler.isInSelectionMode()) {
                BatonInteractionHandler.cancelSelection();
            }

            currentMode = mode;
            ModeHandler handler = modeHandlers.get(mode);
            if (handler != null) {
                try {
                    handler.onEnter(pos, player, level);
                    return true;
                } catch (Exception e) {
                    currentMode = EditMode.NONE;
                    return false;
                }
            } else {
                currentMode = EditMode.NONE;
                return false;
            }
        }
    }

    public static boolean exitMode(Player player, Level level) {
        return exitMode(player, level, true);
    }

    public static boolean exitMode(Player player, Level level, boolean showMessage) {
        if (!isInEditMode()) {
            return false;
        } else {
            EditMode exitingMode = currentMode;
            currentMode = EditMode.NONE;
            ModeHandler handler = modeHandlers.get(exitingMode);
            if (handler != null) {
                try {
                    handler.onExit(player, level, showMessage);
                    return true;
                } catch (Exception e) {
                    return false;
                }
            } else {
                return true;
            }
        }
    }

    public static void tick(Player player, Level level) {
        if (isInEditMode()) {
            ModeHandler handler = modeHandlers.get(currentMode);
            if (handler != null) {
                try {
                    handler.onTick(player, level);
                } catch (Exception e) {
                    exitMode(player, level);
                }
            }
        }
    }

    public static boolean isValidInteraction(BlockPos pos, BlockState state, BlockEntity be, Level level) {
        if (!isInEditMode()) {
            return true;
        } else {
            ModeHandler handler = modeHandlers.get(currentMode);
            return handler != null && handler.isValidInteraction(pos, state, be, level);
        }
    }

    public static void registerHandler(EditMode mode, ModeHandler handler) {
        modeHandlers.put(mode, handler);
    }

    public enum EditMode {
        NONE, FROGPORT, MAILBOX
    }

    public interface ModeHandler {
        void onEnter(BlockPos pos, Player player, Level level);

        void onExit(Player player, Level level);

        void onExit(Player player, Level level, boolean showMessage);

        boolean canExit(BlockPos pos, Player player, Level level);

        void onTick(Player player, Level level);

        boolean isValidInteraction(BlockPos pos, BlockState state, BlockEntity be, Level level);
    }
}
