package com.adonis.fluid.block.LogisticsJunction;

import static com.adonis.fluid.CreateFluid.MOD_ID;

import java.util.ArrayList;
import java.util.List;

import com.adonis.fluid.registry.CFPartialModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;

import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class LogisticsJunctionRenderer extends KineticBlockEntityRenderer<LogisticsJunctionBlockEntity> {
	private static final ResourceLocation LINK_TEXTURE =
		ResourceLocation.fromNamespaceAndPath(MOD_ID, "textures/block/logistics_junction_link.png");
	private static final ResourceLocation ATTACH_TEXTURE =
		ResourceLocation.fromNamespaceAndPath(MOD_ID, "textures/block/junction_upper.png");
	private static final double THICKNESS_SCALE = 1.6d;
	private static final float START_Y_OFFSET = 1f / 16f;
	private static final int TUBE_SIDES = 4;
	private static final double WIRE_RADIUS = (2.05 / 16d) * THICKNESS_SCALE;
	private static final double COLLAR_INNER_RADIUS = (1.8 / 16d) * THICKNESS_SCALE;
	private static final double COLLAR_RADIUS = (2.7 / 16d) * THICKNESS_SCALE;
	private static final double COLLAR_HALF_LENGTH = 1.25 / 16d;
	private static final double START_NORMAL_OFFSET = 3 / 16d;
	private static final double END_NORMAL_OFFSET = 3 / 16d;
	private static final double START_LIFT = 10 / 16d;
	private static final double END_TANGENT = 6 / 16d;
	private static final double CURVE_SAMPLE_LENGTH = 7 / 16d;
	private static final double COLLAR_TARGET_SPACING = 4 / 16d;
	private static final int MIN_RENDER_SEGMENTS = 3;
	private static final double END_FILL_OVERLAP = 2 / 16d;
	private static final double CROSS_SECTION_ROTATION = Math.PI / 4d;
	private static final float HOSE_U0 = 12f / 16f;
	private static final float HOSE_U1 = 16f / 16f;
	private static final float HOSE_V0 = 0f;
	private static final float HOSE_V1 = 1f;
	private static final float COLLAR_U0 = 3.75f / 16f;
	private static final float COLLAR_U1 = 5.5f / 16f;
	private static final float COLLAR_V0 = 0.75f / 16f;
	private static final float COLLAR_V1 = 1.25f / 16f;
	private static final float ATTACH_X0 = 1.95f / 16f;
	private static final float ATTACH_X1 = 14.05f / 16f;
	private static final float ATTACH_Y0 = -0.1f / 16f;
	private static final float ATTACH_Y1 = 2.95f / 16f;
	private static final float ATTACH_Z0 = 1.95f / 16f;
	private static final float ATTACH_Z1 = 14.05f / 16f;
	private static final float ATTACH_SIDE_U0 = 0f / 16f;
	private static final float ATTACH_SIDE_U1 = 6f / 16f;
	private static final float ATTACH_SIDE_V0 = 6.5f / 16f;
	private static final float ATTACH_SIDE_V1 = 8f / 16f;
	private static final float ATTACH_TOP_U0 = 6f / 16f;
	private static final float ATTACH_TOP_U1 = 0f / 16f;
	private static final float ATTACH_TOP_V0 = 6f / 16f;
	private static final float ATTACH_TOP_V1 = 0f / 16f;
	private static final float ATTACH_BOTTOM_U0 = 9f / 16f;
	private static final float ATTACH_BOTTOM_U1 = 15f / 16f;
	private static final float ATTACH_BOTTOM_V0 = 1f / 16f;
	private static final float ATTACH_BOTTOM_V1 = 7f / 16f;
	private static final double ATTACH_HALF_WIDTH_X = (ATTACH_X1 - ATTACH_X0) / 2d;
	private static final double ATTACH_HALF_WIDTH_Z = (ATTACH_Z1 - ATTACH_Z0) / 2d;
	private static final double ATTACH_SURFACE_INSET = 3.1 / 16d;

	public LogisticsJunctionRenderer(BlockEntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	protected SuperByteBuffer getRotatedModel(LogisticsJunctionBlockEntity be, BlockState state) {
		return CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, be.getBlockState(), Direction.DOWN);
	}

	@Override
	protected BlockState getRenderedBlockState(LogisticsJunctionBlockEntity be) {
		return KineticBlockEntityRenderer.shaft(KineticBlockEntityRenderer.getRotationAxisOf(be));
	}

	@Override
	protected void renderSafe(LogisticsJunctionBlockEntity be, float partialTicks, PoseStack ms,
		MultiBufferSource buffer, int light, int overlay) {
		super.renderSafe(be, partialTicks, ms, buffer, light, overlay);

		BlockPos targetPos = be.getFlexibleTargetPos();
		Direction targetFace = be.getFlexibleTargetFace();
		if (targetPos == null || targetFace == null || !be.hasRenderableFlexibleTarget())
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
		CollarLayout collarLayout = computeCollarLayout(curve);
		ms.pushPose();
		ms.translate(-be.getBlockPos().getX(), -be.getBlockPos().getY(), -be.getBlockPos().getZ());
		renderAttachModel(end, endNormal.normalize(), ms, buffer, light);
		VertexConsumer hose = buffer.getBuffer(RenderType.entityCutoutNoCull(LINK_TEXTURE));
		renderTubeBody(hose, ms, curve, light, collarLayout.spacing());
		renderAllCollars(be, ms, buffer, curve, light, collarLayout);
		ms.popPose();
	}

	@Override
	public boolean shouldRenderOffScreen(LogisticsJunctionBlockEntity blockEntity) {
		return blockEntity.hasFlexibleTarget();
	}

	private static void renderAttachModel(Vec3 end, Vec3 normal, PoseStack ms, MultiBufferSource buffer, int light) {
		VertexConsumer attach = buffer.getBuffer(RenderType.entityCutoutNoCull(ATTACH_TEXTURE));
		Vec3 reference = Math.abs(normal.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
		Vec3 right = reference.cross(normal).normalize();
		if (right.lengthSqr() < 1.0e-6)
			right = new Vec3(1, 0, 0);
		Vec3 forward = normal.cross(right).normalize();
		Vec3 center = end.subtract(normal.scale(ATTACH_SURFACE_INSET));

		Vec3 topOffset = normal.scale(ATTACH_Y1);
		Vec3 bottomOffset = normal.scale(ATTACH_Y0);
		Vec3 rightOffset = right.scale(ATTACH_HALF_WIDTH_X);
		Vec3 forwardOffset = forward.scale(ATTACH_HALF_WIDTH_Z);

		Vec3 topA = center.add(topOffset).subtract(rightOffset).subtract(forwardOffset);
		Vec3 topB = center.add(topOffset).add(rightOffset).subtract(forwardOffset);
		Vec3 topC = center.add(topOffset).add(rightOffset).add(forwardOffset);
		Vec3 topD = center.add(topOffset).subtract(rightOffset).add(forwardOffset);

		Vec3 bottomA = center.add(bottomOffset).subtract(rightOffset).subtract(forwardOffset);
		Vec3 bottomB = center.add(bottomOffset).add(rightOffset).subtract(forwardOffset);
		Vec3 bottomC = center.add(bottomOffset).add(rightOffset).add(forwardOffset);
		Vec3 bottomD = center.add(bottomOffset).subtract(rightOffset).add(forwardOffset);

		renderQuadCustomUv(attach, ms, topA, topB, topC, topD,
			ATTACH_TOP_U0, ATTACH_TOP_V0,
			ATTACH_TOP_U1, ATTACH_TOP_V0,
			ATTACH_TOP_U1, ATTACH_TOP_V1,
			ATTACH_TOP_U0, ATTACH_TOP_V1,
			normal, light);
		renderQuadCustomUv(attach, ms, bottomA, bottomB, bottomC, bottomD,
			ATTACH_BOTTOM_U0, ATTACH_BOTTOM_V0,
			ATTACH_BOTTOM_U1, ATTACH_BOTTOM_V0,
			ATTACH_BOTTOM_U1, ATTACH_BOTTOM_V1,
			ATTACH_BOTTOM_U0, ATTACH_BOTTOM_V1,
			normal.scale(-1), light);
		renderQuadCustomUv(attach, ms, bottomD, topD, topA, bottomA,
			ATTACH_SIDE_U0, ATTACH_SIDE_V0,
			ATTACH_SIDE_U0, ATTACH_SIDE_V1,
			ATTACH_SIDE_U1, ATTACH_SIDE_V1,
			ATTACH_SIDE_U1, ATTACH_SIDE_V0,
			right.scale(-1), light);
		renderQuadCustomUv(attach, ms, bottomA, topA, topB, bottomB,
			ATTACH_SIDE_U0, ATTACH_SIDE_V0,
			ATTACH_SIDE_U0, ATTACH_SIDE_V1,
			ATTACH_SIDE_U1, ATTACH_SIDE_V1,
			ATTACH_SIDE_U1, ATTACH_SIDE_V0,
			forward.scale(-1), light);
		renderQuadCustomUv(attach, ms, bottomB, topB, topC, bottomC,
			ATTACH_SIDE_U0, ATTACH_SIDE_V0,
			ATTACH_SIDE_U0, ATTACH_SIDE_V1,
			ATTACH_SIDE_U1, ATTACH_SIDE_V1,
			ATTACH_SIDE_U1, ATTACH_SIDE_V0,
			right, light);
		renderQuadCustomUv(attach, ms, bottomC, topC, topD, bottomD,
			ATTACH_SIDE_U0, ATTACH_SIDE_V0,
			ATTACH_SIDE_U0, ATTACH_SIDE_V1,
			ATTACH_SIDE_U1, ATTACH_SIDE_V1,
			ATTACH_SIDE_U1, ATTACH_SIDE_V0,
			forward, light);
	}

	private static void renderTubeBody(VertexConsumer out, PoseStack transform, CurveData curve, int light,
		double hoseRepeatLength) {
		double accumulatedLength = 0;
		// Align the hose texture seam with collar centers so the brass rings cover the wrap.
		double hosePhaseOffset = -hoseRepeatLength * 0.5d;
		int lastSegment = curve.segmentCount() - 1;
		for (int i = 0; i < curve.segmentCount(); ++i) {
			Vec3 start = curve.getRenderPoint(i);
			Vec3 end = curve.getRenderPoint(i + 1);
			if (i == 0)
				start = start.subtract(curve.getTangent(0).scale(END_FILL_OVERLAP));
			if (i == lastSegment)
				end = end.add(curve.getTangent(lastSegment).scale(END_FILL_OVERLAP));
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
				Vec3 radius0 = radialOffset(basisA, basisB, angle0, WIRE_RADIUS);
				Vec3 radius1 = radialOffset(basisA, basisB, angle1, WIRE_RADIUS);
				Vec3 normal = radius0.add(radius1).scale(0.5).normalize();
				renderTubeQuad(out, transform, start, end, radius0, radius1, normal, light, false,
					getHoseU(accumulatedLength + hosePhaseOffset, hoseRepeatLength),
					getHoseU(accumulatedLength + segmentLength + hosePhaseOffset, hoseRepeatLength));
			}

			accumulatedLength += segmentLength;
		}
	}

	private static CollarLayout computeCollarLayout(CurveData curve) {
		double totalLength = curve.totalLength();
		if (totalLength <= 1.0e-6) {
			return new CollarLayout(1, 0);
		}

		int collarCount = Math.max(2, Mth.floor(totalLength / COLLAR_TARGET_SPACING) + 1);
		double actualSpacing = totalLength / (collarCount - 1);
		return new CollarLayout(collarCount, actualSpacing);
	}

	private static void renderAllCollars(LogisticsJunctionBlockEntity be, PoseStack transform, MultiBufferSource buffer,
		CurveData curve, int light,
		CollarLayout collarLayout) {
		if (collarLayout.count() <= 1) {
			renderCollarAtPoint(be, transform, buffer, curve.getRenderPoint(0), curve.getTangent(0), light);
			return;
		}

		double totalLength = curve.totalLength();
		double actualSpacing = collarLayout.spacing();
		for (int i = 0; i < collarLayout.count(); i++) {
			double distance = i == collarLayout.count() - 1 ? totalLength : actualSpacing * i;
			renderCollarAtPoint(be, transform, buffer, curve.samplePoint(distance), curve.sampleTangent(distance), light);
		}
	}

	private static void renderCollarAtPoint(LogisticsJunctionBlockEntity be, PoseStack transform, MultiBufferSource buffer,
		Vec3 center, Vec3 tangent, int light) {
		Vec3 direction = tangent.normalize();
		if (direction.lengthSqr() < 1.0e-6)
			return;

		float yaw = (float) Math.atan2(direction.x, direction.z);
		float pitch = (float) -Math.asin(Mth.clamp(direction.y, -1, 1));

		transform.pushPose();
		transform.translate(center.x, center.y, center.z);
		transform.mulPose(Axis.YP.rotation(yaw));
		transform.mulPose(Axis.XP.rotation(pitch));

		CachedBuffers.partial(CFPartialModels.LOGISTICS_JUNCTION_COLLAR, be.getBlockState())
			.light(light)
			.renderInto(transform, buffer.getBuffer(RenderType.cutoutMipped()));
		transform.popPose();
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
		return Math.max(MIN_RENDER_SEGMENTS, Mth.ceil(length / CURVE_SAMPLE_LENGTH));
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

	private static Vec3 radialOffset(Vec3 basisA, Vec3 basisB, double angle, double radius) {
		return basisA.scale(Math.cos(angle) * radius).add(basisB.scale(Math.sin(angle) * radius));
	}

	private static void renderCollarSegment(VertexConsumer out, PoseStack transform, Vec3 start, Vec3 end, Vec3 basisA,
		Vec3 basisB, double angle0, double angle1, int light) {
		Vec3 outer0 = radialOffset(basisA, basisB, angle0, COLLAR_RADIUS);
		Vec3 outer1 = radialOffset(basisA, basisB, angle1, COLLAR_RADIUS);
		Vec3 inner0 = radialOffset(basisA, basisB, angle0, COLLAR_INNER_RADIUS);
		Vec3 inner1 = radialOffset(basisA, basisB, angle1, COLLAR_INNER_RADIUS);

		Vec3 outerNormal = outer0.add(outer1).scale(0.5).normalize();
		Vec3 innerNormal = outerNormal.scale(-1);
		Vec3 axisNormal = end.subtract(start).normalize();

		renderTubeQuad(out, transform, start, end, outer0, outer1, outerNormal, light, true, COLLAR_U0, COLLAR_U1);
		renderTubeQuad(out, transform, start, end, inner1, inner0, innerNormal, light, true, COLLAR_U0, COLLAR_U1);
		renderRingCap(out, transform, start, outer0, outer1, inner0, inner1, axisNormal.scale(-1), light);
		renderRingCap(out, transform, end, outer1, outer0, inner1, inner0, axisNormal, light);
	}

	private static void renderRingCap(VertexConsumer out, PoseStack transform, Vec3 center, Vec3 outer0, Vec3 outer1,
		Vec3 inner0, Vec3 inner1, Vec3 normal, int light) {
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

	private static void renderQuad(VertexConsumer out, PoseStack transform, Vec3 v0, Vec3 v1, Vec3 v2, Vec3 v3,
		float u0, float vMin, float u1, float vMax, Vec3 normal, int light) {
		putVertex(out, transform, v0, u0, vMin, normal, light);
		putVertex(out, transform, v1, u0, vMax, normal, light);
		putVertex(out, transform, v2, u1, vMax, normal, light);
		putVertex(out, transform, v3, u1, vMin, normal, light);
	}

	private static void renderQuadCustomUv(VertexConsumer out, PoseStack transform, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3,
		float u0, float vv0, float u1, float vv1, float u2, float vv2, float u3, float vv3, Vec3 normal, int light) {
		putVertex(out, transform, p0, u0, vv0, normal, light);
		putVertex(out, transform, p1, u1, vv1, normal, light);
		putVertex(out, transform, p2, u2, vv2, normal, light);
		putVertex(out, transform, p3, u3, vv3, normal, light);
	}

	private static void renderTubeQuad(VertexConsumer out, PoseStack transform, Vec3 start, Vec3 end, Vec3 radius0,
		Vec3 radius1, Vec3 normal, int light, boolean collar, float u0, float u1) {
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

	private static float getHoseU(double length, double hoseRepeatLength) {
		double repeatLength = hoseRepeatLength <= 1.0e-6 ? COLLAR_TARGET_SPACING : hoseRepeatLength;
		double repeats = length / repeatLength;
		return HOSE_U0 + (float) repeats * (HOSE_U1 - HOSE_U0);
	}

	private static void putVertex(VertexConsumer out, PoseStack transform, Vec3 pos, float u, float v, Vec3 normal,
		int light) {
		out.addVertex(transform.last(), (float) pos.x, (float) pos.y, (float) pos.z)
			.setColor(1f, 1f, 1f, 1f)
			.setUv(u, v)
			.setOverlay(OverlayTexture.NO_OVERLAY)
			.setLight(light)
			.setNormal(transform.last(), (float) normal.x, (float) normal.y, (float) normal.z);
	}

	private record CurveData(List<Vec3> points, double[] cumulativeLengths, double totalLength) {
		private CurveData(List<Vec3> points) {
			this(points, buildCumulativeLengths(points), computeTotalLength(points));
		}

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

		public Vec3 samplePoint(double distance) {
			if (distance <= 0 || segmentCount() <= 0)
				return points.get(0);
			if (distance >= totalLength)
				return points.get(points.size() - 1);

			int segment = findSegment(distance);
			double startLength = cumulativeLengths[segment];
			double segmentLength = cumulativeLengths[segment + 1] - startLength;
			if (segmentLength <= 1.0e-6)
				return points.get(segment);

			double localT = (distance - startLength) / segmentLength;
			return points.get(segment).lerp(points.get(segment + 1), localT);
		}

		public Vec3 sampleTangent(double distance) {
			if (segmentCount() <= 0)
				return new Vec3(0, 1, 0);
			if (distance <= 0)
				return getTangent(0);
			if (distance >= totalLength)
				return getTangent(segmentCount() - 1);
			return getTangent(findSegment(distance));
		}

		private int findSegment(double distance) {
			for (int i = 0; i < segmentCount(); i++)
				if (distance <= cumulativeLengths[i + 1] + 1.0e-6)
					return i;
			return segmentCount() - 1;
		}

		private static double[] buildCumulativeLengths(List<Vec3> points) {
			double[] lengths = new double[points.size()];
			double accumulated = 0;
			lengths[0] = 0;
			for (int i = 1; i < points.size(); i++) {
				accumulated += points.get(i).distanceTo(points.get(i - 1));
				lengths[i] = accumulated;
			}
			return lengths;
		}

		private static double computeTotalLength(List<Vec3> points) {
			if (points.size() < 2)
				return 0;
			double total = 0;
			for (int i = 1; i < points.size(); i++)
				total += points.get(i).distanceTo(points.get(i - 1));
			return total;
		}
	}

	private record CollarLayout(int count, double spacing) {
	}
}
