package com.adonis.fluid.registry;

import com.simibubi.create.foundation.data.CreateBlockEntityBuilder;
import com.simibubi.create.foundation.item.TooltipModifier;
import com.tterrag.registrate.AbstractRegistrate;
import com.tterrag.registrate.builders.BlockEntityBuilder;
import com.tterrag.registrate.builders.Builder;
import com.tterrag.registrate.util.entry.RegistryEntry;
import com.tterrag.registrate.util.nullness.NonNullFunction;
import com.tterrag.registrate.util.nullness.NonNullSupplier;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.registries.DeferredHolder;

import javax.annotation.Nullable;
import java.util.function.Function;

public class CFRegistrate extends AbstractRegistrate<CFRegistrate> {
    
    @Nullable
    protected Function<Item, TooltipModifier> currentTooltipModifierFactory;
    
    protected CFRegistrate(String modid) {
        super(modid);
        // 不设置默认创意标签页
        this.defaultCreativeTab((ResourceKey<CreativeModeTab>) null);
    }
    
    public static CFRegistrate create(String modid) {
        return new CFRegistrate(modid);
    }
    
    public CFRegistrate setTooltipModifierFactory(@Nullable Function<Item, TooltipModifier> factory) {
        currentTooltipModifierFactory = factory;
        return self();
    }
    
    @Override
    protected <R, T extends R> RegistryEntry<R, T> accept(String name, ResourceKey<? extends Registry<R>> type,
                                                          Builder<R, T, ?, ?> builder, 
                                                          NonNullSupplier<? extends T> creator, 
                                                          NonNullFunction<DeferredHolder<R, T>, ? extends RegistryEntry<R, T>> entryFactory) {
        RegistryEntry<R, T> entry = super.accept(name, type, builder, creator, entryFactory);
        
        if (type.equals(Registries.ITEM) && currentTooltipModifierFactory != null) {
            Function<Item, TooltipModifier> factory = currentTooltipModifierFactory;
            this.addRegisterCallback(name, Registries.ITEM, item -> {
                TooltipModifier modifier = factory.apply(item);
                TooltipModifier.REGISTRY.register(item, modifier);
            });
        }
        
        // 不添加到任何创意标签页
        return entry;
    }
    
    @Override
    public <T extends BlockEntity> CreateBlockEntityBuilder<T, CFRegistrate> blockEntity(String name,
                                                                                         BlockEntityBuilder.BlockEntityFactory<T> factory) {
        return blockEntity(self(), name, factory);
    }
    
    @Override
    public <T extends BlockEntity, P> CreateBlockEntityBuilder<T, P> blockEntity(P parent, String name,
                                                                                 BlockEntityBuilder.BlockEntityFactory<T> factory) {
        return (CreateBlockEntityBuilder<T, P>) entry(name,
                callback -> CreateBlockEntityBuilder.create(this, parent, name, callback, factory));
    }
}