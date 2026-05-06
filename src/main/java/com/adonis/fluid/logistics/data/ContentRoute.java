package com.adonis.fluid.logistics.data;

import com.mojang.serialization.Codec;

import io.netty.buffer.ByteBuf;
import net.createmod.catnip.codecs.stream.CatnipStreamCodecBuilders;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

public enum ContentRoute implements StringRepresentable {
	UP,
	DOWN,
	NONE;

	public static final Codec<ContentRoute> CODEC = StringRepresentable.fromEnum(ContentRoute::values);
	public static final StreamCodec<ByteBuf, ContentRoute> STREAM_CODEC =
		CatnipStreamCodecBuilders.ofEnum(ContentRoute.class);

	@Override
	public String getSerializedName() {
		return name().toLowerCase(java.util.Locale.ROOT);
	}
}
