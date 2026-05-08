package com.adonis.fluid.block.SmartUnpackager;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

import static com.adonis.fluid.CreateFluid.MOD_ID;

public class SmartUnpackagerRenderer extends SmartBlockEntityRenderer<SmartUnpackagerBlockEntity> {
	private static final ResourceLocation LINK_TEXTURE = ResourceLocation.fromNamespaceAndPath(MOD_ID, "textures/block/smart_unpackager_link.png");
	private static final ResourceLocation COLLAR_TEXTURE = ResourceLocation.fromNamespaceAndPath(MOD_ID, "textures/block/smart_unpackager_collar.png");
	private static final float START_Y_OFFSET = 1f / 16f;
	private static final int TUBE_SIDES = 4;
	private static final double WIRE_RADIUS = 2.05 / 16d;
	private static final double COLLAR_INNER_RADIUS = 1.8 / 16d;
	private static final double COLLAR_RADIUS = 2.7 / 16d;
	private static final double COLLAR_HALF_LENGTH = 1.25 / 16d;
	private static final double START_NORMAL_OFFSET = 3 / 16d;
	private static final double END_NORMAL_OFFSET = 2 / 16d;
	private static final double START_LIFT = 10 / 16d;
	private static final double END_TANGENT = 6 / 16d;
	private static final double HOSE_UNIT_LENGTH = 4 / 16d;
	private static final int MIN_RENDER_SEGMENTS = 6;
	private static final double CROSS_SECTION_ROTATION = Math.PI / 4d;
	private static final float HOSE_U0 = 12f / 16f;
	private static final float HOSE_U1 = 16f / 16f;
	private static final float HOSE_V0 = 0f;
	private static final float HOSE_V1 = 1f;
	private static final float COLLAR_U0 = 3.75f / 16f;
	private static final float COLLAR_U1 = 5.5f / 16f;
	private static final float COLLAR_V0 = 0.75f / 16f;
	private static final float COLLAR_V1 = 1.25f / 16f;

	public SmartUnpackagerRenderer(BlockEntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	protected void renderSafe(SmartUnpackagerBlockEntity be, float partialTicks, PoseStack ms,
		MultiBufferSource buffer, int light, int overlay) {
		super.renderSafe(be, partialTicks, ms, buffer, light, overlay);

		BlockPos targetPos = be.getFlexibleTargetPos();
		Direction targetFace = be.getFlexibleTargetFace();
		if (targetPos == null || targetFace == null || be.getLevel() == null)
			return;
		if (!be.getLevel().isLoaded(targetPos))
			return;

		Vec3 startNormal = new Vec3(0, 1, 0);
		Vec3 endNormal = Vec3.atLowerCornerOf(targetFace.getNormal());
		Vec3 start = Vec3.atBottomCenterOf(be.getBlockPos()).add(0, 1 + START_Y_OFFSET, 0)
			.add(startNormal.scale(START_NORMAL_OFFSET));
		Vec3 end = Vec3.atCenterOf(targetPos)
			.add(endNormal.scale(0.5 + END_NORMAL_OFFSET));
		Vec3 delta = end.subtract(start);
		if (delta.lengthSqr() < 1.0e-6)
			return;

		CurveData curve = makeCurveData(start, end, startNormal, endNormal);
		ms.pushPose();
		ms.translate(-be.getBlockPos().getX(), -be.getBlockPos().getY(), -be.getBlockPos().getZ());
		VertexConsumer hose = buffer.getBuffer(RenderType.entityCutoutNoCull(LINK_TEXTURE));
		renderConnection(hose, ms, curve, light, WIRE_RADIUS, false);
		VertexConsumer collar = buffer.getBuffer(RenderType.entityCutoutNoCull(COLLAR_TEXTURE));
		renderConnection(collar, ms, curve, light, COLLAR_RADIUS, true);
		ms.popPose();
	}

	@Override
	public boolean shouldRenderOffScreen(SmartUnpackagerBlockEntity blockEntity) {
		return blockEntity.hasFlexibleTarget();
	}

	private static void renderConnection(VertexConsumer out, PoseStack transform, CurveData curve, int light,
		double radius, boolean collarsOnly) {
		int segmentCount = curve.segmentCount();
		int startIndex = collarsOnly ? 1 : 0;
		int endIndex = segmentCount;
		double accumulatedLength = 0;
		for (int i = startIndex; i < endIndex; ++i) {
			Vec3 start = curve.getRenderPoint(i);
			Vec3 end = curve.getRenderPoint(i + 1);
			if (collarsOnly) {
				Vec3 center = start;
				Vec3 tangent = curve.getTangent(i);
				start = center.subtract(tangent.scale(COLLAR_HALF_LENGTH));
				end = center.add(tangent.scale(COLLAR_HALF_LENGTH));
			}
			Vec3 axis = end.subtract(start);
			if (axis.lengthSqr() < 1.0e-6)
				continue;
			double segmentLength = axis.length();
			Vec3 forward = axis.normalize();
			Vec3 reference = Math.abs(forward.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
			Vec3 basisA = forward.cross(reference).normalize();
			if (basisA.lengthSqr() < 1.0e-6)
				basisA = new Vec3(1, 0, 0);
			Vec3 basisB = forward.cross(basisA).normalize();

			for (int side = 0; side < TUBE_SIDES; side++) {
				double angle0 = CROSS_SECTION_ROTATION + (Math.PI * 2 * side) / TUBE_SIDES;
				double angle1 = CROSS_SECTION_ROTATION + (Math.PI * 2 * (side + 1)) / TUBE_SIDES;
				if (collarsOnly) {
					renderCollarSegment(out, transform, start, end, basisA, basisB, angle0, angle1, light);
					continue;
				}
				Vec3 radius0 = radialOffset(basisA, basisB, angle0, radius);
				Vec3 radius1 = radialOffset(basisA, basisB, angle1, radius);
				Vec3 normal = radius0.add(radius1).scale(0.5).normalize();
				renderTubeQuad(out, transform, start, end, radius0, radius1, normal, light, false,
					getHoseU(accumulatedLength), getHoseU(accumulatedLength + segmentLength));
			}
			if (!collarsOnly) {
				renderTubeCap(out, transform, start, basisA, basisB, forward.scale(-1), light, true);
				renderTubeCap(out, transform, end, basisA, basisB, forward, light, true);
			}
			accumulatedLength += segmentLength;
		}
	}

	private static CurveData makeCurveData(Vec3 start, Vec3 end, Vec3 startNormal, Vec3 endNormal) {
		double totalLength = start.distanceTo(end);
		double startLift = Math.max(START_LIFT, totalLength * 0.2);
		double endTangentLength = Math.max(END_TANGENT, totalLength * 0.15);

		Vec3 control1 = start.add(startNormal.normalize().scale(startLift));
		Vec3 control2 = end.add(endNormal.normalize().scale(endTangentLength));

		int segmentCount = estimateSegmentCount(start, control1, control2, end);
		List<Vec3> points = new ArrayList<>(segmentCount + 1);
		for (int i = 0; i <= segmentCount; i++) {
			double t = i / (double) segmentCount;
			points.add(interpolateBezier(start, control1, control2, end, t));
		}
		return new CurveData(points);
	}

	private static int estimateSegmentCount(Vec3 start, Vec3 control1, Vec3 control2, Vec3 end) {
		final int roughSamples = 32;
		double length = 0;
		Vec3 previous = start;
		for (int i = 1; i <= roughSamples; i++) {
			double t = i / (double) roughSamples;
			Vec3 point = interpolateBezier(start, control1, control2, end, t);
			length += point.distanceTo(previous);
			previous = point;
		}
		return Math.max(MIN_RENDER_SEGMENTS, Mth.ceil(length / HOSE_UNIT_LENGTH));
	}

	private static Vec3 interpolateBezier(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, double t) {
		double u = 1 - t;
		double tt = t * t;
		double uu = u * u;
		double uuu = uu * u;
		double ttt = tt * t;

		return p0.scale(uuu)
			.add(p1.scale(3 * uu * t))
			.add(p2.scale(3 * u * tt))
			.add(p3.scale(ttt));
	}

	private static Vec3 radialOffset(Vec3 basisA, Vec3 basisB, double angle, double radius)
	{
		return basisA.scale(Math.cos(angle) * radius).add(basisB.scale(Math.sin(angle) * radius));
	}

	private static void renderCollarSegment(
		VertexConsumer out, PoseStack transform, Vec3 start, Vec3 end, Vec3 basisA, Vec3 basisB,
		double angle0, double angle1, int light
	) {
		Vec3 outer0 = radialOffset(basisA, basisB, angle0, COLLAR_RADIUS);
		Vec3 outer1 = radialOffset(basisA, basisB, angle1, COLLAR_RADIUS);
		Vec3 inner0 = radialOffset(basisA, basisB, angle0, COLLAR_INNER_RADIUS);
		Vec3 inner1 = radialOffset(basisA, basisB, angle1, COLLAR_INNER_RADIUS);

		Vec3 outerNormal = outer0.add(outer1).scale(0.5).normalize();
		Vec3 innerNormal = outerNormal.scale(-1);

		renderTubeQuad(out, transform, start, end, outer0, outer1, outerNormal, light, true, COLLAR_U0, COLLAR_U1);
		renderTubeQuad(out, transform, start, end, inner1, inner0, innerNormal, light, true, COLLAR_U0, COLLAR_U1);
		renderRingCap(out, transform, start, outer0, outer1, inner0, inner1, end.subtract(start).normalize().scale(-1), light);
		renderRingCap(out, transform, end, outer1, outer0, inner1, inner0, end.subtract(start).normalize(), light);
	}

	private static void renderRingCap(
		VertexConsumer out, PoseStack transform, Vec3 center, Vec3 outer0, Vec3 outer1, Vec3 inner0, Vec3 inner1,
		Vec3 normal, int light
	) {
		Vec3[] vertices = {
			center.add(outer0),
			center.add(outer1),
			center.add(inner1),
			center.add(inner0)
		};
		float[][] uvs = {
			{COLLAR_U0, COLLAR_V0},
			{COLLAR_U1, COLLAR_V0},
			{COLLAR_U1, COLLAR_V1},
			{COLLAR_U0, COLLAR_V1}
		};
		for (int i = 0; i < vertices.length; i++)
			putVertex(out, transform, vertices[i], uvs[i][0], uvs[i][1], normal, light);
	}

	private static void renderTubeQuad(
		VertexConsumer out, PoseStack transform, Vec3 start, Vec3 end, Vec3 radius0, Vec3 radius1, Vec3 normal, int light,
		boolean collar, float u0, float u1
	) {
		float v0 = collar ? COLLAR_V0 : HOSE_V0;
		float v1 = collar ? COLLAR_V1 : HOSE_V1;
		Vec3[] vertices = {
			start.add(radius0),
			end.add(radius0),
			end.add(radius1),
			start.add(radius1)
		};
		float[][] uvs = {
			{u0, v0},
			{u1, v0},
			{u1, v1},
			{u0, v1}
		};
		for (int i = 0; i < vertices.length; i++)
			putVertex(out, transform, vertices[i], uvs[i][0], uvs[i][1], normal, light);
	}

	private static void renderTubeCap(
		VertexConsumer out, PoseStack transform, Vec3 center, Vec3 basisA, Vec3 basisB, Vec3 normal, int light, boolean hose
	) {
		float u0 = hose ? HOSE_U0 : COLLAR_U0;
		float u1 = hose ? HOSE_U1 : COLLAR_U1;
		float v0 = hose ? HOSE_V0 : COLLAR_V0;
		float v1 = hose ? HOSE_V1 : COLLAR_V1;
		Vec3[] ring = new Vec3[TUBE_SIDES];
		for (int side = 0; side < TUBE_SIDES; side++) {
			double angle = (Math.PI * 2 * side) / TUBE_SIDES;
			ring[side] = center.add(radialOffset(basisA, basisB, angle, WIRE_RADIUS));
		}
		if (TUBE_SIDES != 4)
			return;
		putVertex(out, transform, ring[0], u0, v0, normal, light);
		putVertex(out, transform, ring[1], u1, v0, normal, light);
		putVertex(out, transform, ring[2], u1, v1, normal, light);
		putVertex(out, transform, ring[3], u0, v1, normal, light);
	}

	private static float getHoseU(double length) {
		double repeats = length / HOSE_UNIT_LENGTH;
		return HOSE_U0 + (float) repeats * (HOSE_U1 - HOSE_U0);
	}

	private static void putVertex(
		VertexConsumer out, PoseStack transform, Vec3 pos, float u, float v, Vec3 normal, int light
	) {
		out.addVertex(transform.last(), (float) pos.x, (float) pos.y, (float) pos.z)
			.setColor(1f, 1f, 1f, 1f)
			.setUv(u, v)
			.setOverlay(OverlayTexture.NO_OVERLAY)
			.setLight(light)
			.setNormal(transform.last(), (float) normal.x, (float) normal.y, (float) normal.z);
	}

	private record CurveData(List<Vec3> points) {
		public int segmentCount() {
			return points.size() - 1;
		}

		public Vec3 getRenderPoint(int index) {
			return points.get(Mth.clamp(index, 0, segmentCount()));
		}

		public Vec3 getTangent(int index) {
			int clamped = Mth.clamp(index, 0, segmentCount() - 1);
			Vec3 from = points.get(clamped);
			Vec3 to = points.get(clamped + 1);
			Vec3 tangent = to.subtract(from);
			return tangent.lengthSqr() > 1.0e-6 ? tangent.normalize() : new Vec3(0, 1, 0);
		}
	}
}
