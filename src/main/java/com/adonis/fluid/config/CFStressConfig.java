package com.adonis.fluid.config;

import com.adonis.fluid.CreateFluid;
import com.simibubi.create.api.stress.BlockStressValues;
import net.createmod.catnip.config.ConfigBase;
import net.createmod.catnip.registry.RegisteredObjectsHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.ModConfigSpec;
import com.tterrag.registrate.builders.BlockBuilder;
import com.tterrag.registrate.util.nullness.NonNullUnaryOperator;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.function.DoubleSupplier;

public class CFStressConfig extends ConfigBase {
    private final String modId;
    private final Map<ResourceLocation, Double> defaultImpacts = new HashMap<>();
    private final Map<ResourceLocation, Double> defaultCapacities = new HashMap<>();
    private final Map<ResourceLocation, ModConfigSpec.ConfigValue<Double>> impacts = new HashMap<>();
    private final Map<ResourceLocation, ModConfigSpec.ConfigValue<Double>> capacities = new HashMap<>();

    public CFStressConfig(String modId) {
        this.modId = modId;
    }

    protected int getVersion() {
        return 1;
    }

    @Override
    public void registerAll(ModConfigSpec.Builder builder) {
        builder.comment("Stress impact configurations for " + modId, "[in Stress Units]")
                .push("impact");
        
        defaultImpacts.forEach((id, defaultValue) -> {
            impacts.put(id, builder.define(id.getPath(), defaultValue));
        });
        builder.pop();

        if (!defaultCapacities.isEmpty()) {
            builder.comment("Stress capacity configurations for " + modId, "[in Stress Units]")
                    .push("capacity");
            defaultCapacities.forEach((id, defaultValue) -> {
                capacities.put(id, builder.define(id.getPath(), defaultValue));
            });
            builder.pop();
        }

        BlockStressValues.IMPACTS.registerProvider(this::getImpact);
        BlockStressValues.CAPACITIES.registerProvider(this::getCapacity);
    }

    @Override
    public String getName() {
        return modId + "-stressValues.v" + getVersion();
    }

    @Nullable
    public DoubleSupplier getImpact(Block block) {
        ResourceLocation id = RegisteredObjectsHelper.getKeyOrThrow(block);
        if (!id.getNamespace().equals(this.modId)) {
            return null;
        }
        ModConfigSpec.ConfigValue<Double> configValue = this.impacts.get(id);
        return configValue == null ? null : configValue::get;
    }

    @Nullable
    public DoubleSupplier getCapacity(Block block) {
        ResourceLocation id = RegisteredObjectsHelper.getKeyOrThrow(block);
        if (!id.getNamespace().equals(this.modId)) {
            return null;
        }
        ModConfigSpec.ConfigValue<Double> configValue = this.capacities.get(id);
        return configValue == null ? null : configValue::get;
    }

    public <B extends Block, P> NonNullUnaryOperator<BlockBuilder<B, P>> setImpact(double value) {
        return builder -> {
            if (!builder.getOwner().getModid().equals(this.modId)) {
                throw new IllegalStateException("Attempting to set stress impact for block '" + builder.getName()
                        + "' from mod '" + builder.getOwner().getModid() + "' using config for mod '" + this.modId + "'.");
            }
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(this.modId, builder.getName());
            this.defaultImpacts.put(id, value);
            return builder;
        };
    }

    public <B extends Block, P> NonNullUnaryOperator<BlockBuilder<B, P>> setCapacity(double value) {
        return builder -> {
            if (!builder.getOwner().getModid().equals(this.modId)) {
                throw new IllegalStateException("Attempting to set stress capacity for block '" + builder.getName()
                        + "' from mod '" + builder.getOwner().getModid() + "' using config for mod '" + this.modId + "'.");
            }
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(this.modId, builder.getName());
            this.defaultCapacities.put(id, value);
            return builder;
        };
    }

    public <B extends Block, P> NonNullUnaryOperator<BlockBuilder<B, P>> setNoImpact() {
        return setImpact(0);
    }
}