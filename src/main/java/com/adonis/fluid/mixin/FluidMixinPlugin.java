package com.adonis.fluid.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class FluidMixinPlugin implements IMixinConfigPlugin {

	private static final String CMPC_SCREEN = "com.kreidev.cmpackagecouriers.stock_ticker.PortableStockTickerScreen";

	@Override
	public void onLoad(String mixinPackage) {
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		if (mixinClassName.equals("com.adonis.fluid.mixin.compat.cmpc.PortableStockTickerScreenMixin")) {
			return classExists(CMPC_SCREEN);
		}
		return true;
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
	}

	@Override
	public List<String> getMixins() {
		return null;
	}

	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}

	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}

	private static boolean classExists(String className) {
		String resourcePath = className.replace('.', '/') + ".class";
		return FluidMixinPlugin.class.getClassLoader().getResource(resourcePath) != null;
	}
}
