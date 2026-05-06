package com.adonis.fluid.block.SmartRepackager;

import com.adonis.fluid.registry.CFBlockEntities;
import com.simibubi.create.content.logistics.packager.PackagerBlock;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;

import net.minecraft.world.level.block.entity.BlockEntityType;

public class SmartRepackagerBlock extends PackagerBlock {
	public SmartRepackagerBlock(Properties properties) {
		super(properties);
	}

	@Override
	@SuppressWarnings("unchecked")
	public Class<PackagerBlockEntity> getBlockEntityClass() {
		return (Class<PackagerBlockEntity>) (Class<?>) SmartRepackagerBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends PackagerBlockEntity> getBlockEntityType() {
		return CFBlockEntities.SMART_REPACKAGER.get();
	}
}
