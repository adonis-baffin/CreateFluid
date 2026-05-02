package com.adonis.fluid.compat;

import com.simibubi.create.content.contraptions.Contraption;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

public final class ContraptionBlockEntityHelper {

    private static final @Nullable Method GET_BLOCK_ENTITY_CLIENT_SIDE =
            findMethod("getBlockEntityClientSide");
    private static final @Nullable Method GET_BLOCK_ENTITY =
            findMethod("getBlockEntity");
    private static final @Nullable Field PRESENT_BLOCK_ENTITIES =
            findField("presentBlockEntities");

    private ContraptionBlockEntityHelper() {}

    @Nullable
    public static BlockEntity getBlockEntity(Contraption contraption, BlockPos localPos) {
        BlockEntity blockEntity = invoke(contraption, GET_BLOCK_ENTITY_CLIENT_SIDE, localPos);
        if (blockEntity != null) {
            return blockEntity;
        }

        blockEntity = invoke(contraption, GET_BLOCK_ENTITY, localPos);
        if (blockEntity != null) {
            return blockEntity;
        }

        if (PRESENT_BLOCK_ENTITIES == null) {
            return null;
        }

        try {
            Object value = PRESENT_BLOCK_ENTITIES.get(contraption);
            if (value instanceof Map<?, ?> map) {
                Object blockEntityValue = map.get(localPos);
                if (blockEntityValue instanceof BlockEntity be) {
                    return be;
                }
            }
        } catch (ReflectiveOperationException ignored) {
        }

        return null;
    }

    private static @Nullable BlockEntity invoke(Contraption contraption, @Nullable Method method, BlockPos localPos) {
        if (method == null) {
            return null;
        }

        try {
            Object value = method.invoke(contraption, localPos);
            if (value instanceof BlockEntity blockEntity) {
                return blockEntity;
            }
        } catch (ReflectiveOperationException ignored) {
        }

        return null;
    }

    private static @Nullable Method findMethod(String name) {
        try {
            return Contraption.class.getMethod(name, BlockPos.class);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static @Nullable Field findField(String name) {
        try {
            Field field = Contraption.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
