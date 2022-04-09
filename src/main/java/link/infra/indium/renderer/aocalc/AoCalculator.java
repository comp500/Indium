/*
 * Copyright (c) 2016, 2017, 2018, 2019 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package link.infra.indium.renderer.aocalc;

import link.infra.indium.Indium;
import link.infra.indium.renderer.accessor.AccessAmbientOcclusionCalculator;
import link.infra.indium.renderer.aocalc.AoFace.WeightFunction;
import link.infra.indium.renderer.mesh.EncodingFormat;
import link.infra.indium.renderer.mesh.MutableQuadViewImpl;
import link.infra.indium.renderer.mesh.QuadViewImpl;
import link.infra.indium.renderer.render.BlockRenderInfo;
import me.jellysquid.mods.sodium.client.model.light.data.QuadLightData;
import me.jellysquid.mods.sodium.common.util.DirectionUtil;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3f;
import net.minecraft.world.BlockRenderView;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.BitSet;
import java.util.function.ToIntFunction;

import static java.lang.Math.max;
import static link.infra.indium.renderer.helper.GeometryHelper.*;
import static net.minecraft.util.math.Direction.*;

/**
 * Adaptation of inner, non-static class in BlockModelRenderer that serves same purpose.
 */
@Environment(EnvType.CLIENT)
public class AoCalculator {
	/** Used to receive a method reference in constructor for ao value lookup. */
	@FunctionalInterface
	public interface AoFunc {
		float apply(BlockPos pos);
	}

	/**
	 * Vanilla models with cubic quads have vertices in a certain order, which allows
	 * us to map them using a lookup. Adapted from enum in vanilla AoCalculator.
	 */
	private static final int[][] VERTEX_MAP = new int[6][4];
	static {
		VERTEX_MAP[DOWN.getId()] = new int[] { 0, 1, 2, 3 };
		VERTEX_MAP[UP.getId()] = new int[] { 2, 3, 0, 1 };
		VERTEX_MAP[NORTH.getId()] = new int[] { 3, 0, 1, 2 };
		VERTEX_MAP[SOUTH.getId()] = new int[] { 0, 1, 2, 3 };
		VERTEX_MAP[WEST.getId()] = new int[] { 3, 0, 1, 2 };
		VERTEX_MAP[EAST.getId()] = new int[] { 1, 2, 3, 0 };
	}

	private static final Logger LOGGER = LogManager.getLogger();

	private final AccessAmbientOcclusionCalculator vanillaCalc;
	private final BlockPos.Mutable lightPos = new BlockPos.Mutable();
	private final BlockPos.Mutable searchPos = new BlockPos.Mutable();
	private final BlockRenderInfo blockInfo;
	private final ToIntFunction<BlockPos> brightnessFunc;
	private final AoFunc aoFunc;

	/** caches results of {@link #computeFace(Direction, boolean)} for the current block. */
	private final AoFaceData[] faceData = new AoFaceData[12];

	/** indicates which elements of {@link #faceData} have been computed for the current block. */
	private int completionFlags = 0;

	/** holds per-corner weights - used locally to avoid new allocation. */
	private final float[] w = new float[4];

	public AoCalculator(BlockRenderInfo blockInfo, ToIntFunction<BlockPos> brightnessFunc, AoFunc aoFunc) {
		this.blockInfo = blockInfo;
		this.brightnessFunc = brightnessFunc;
		this.aoFunc = aoFunc;
		this.vanillaCalc = VanillaAoHelper.get();

		for (int i = 0; i < 12; i++) {
			faceData[i] = new AoFaceData();
		}
	}

	/** call at start of each new block. */
	public void clear() {
		completionFlags = 0;
	}

	public void compute(MutableQuadViewImpl quad, QuadLightData cachedQuadLightData, boolean isVanilla) {
		final AoConfig config = Indium.AMBIENT_OCCLUSION_MODE;
		final boolean shouldCompare;

		switch (config) {
		case VANILLA:
			// prevent NPE in error case of failed reflection for vanilla calculator access
			if (vanillaCalc == null) {
				calcFastVanilla(quad, cachedQuadLightData);
			} else {
				calcVanilla(quad, cachedQuadLightData);
			}

			// no point in comparing vanilla with itself
			shouldCompare = false;
			break;

		case EMULATE:
			calcFastVanilla(quad, cachedQuadLightData);
			shouldCompare = Indium.DEBUG_COMPARE_LIGHTING && isVanilla;
			break;

		case HYBRID:
		default:
			if (isVanilla) {
				shouldCompare = Indium.DEBUG_COMPARE_LIGHTING;
				calcFastVanilla(quad, cachedQuadLightData);
			} else {
				shouldCompare = false;
				calcEnhanced(quad, cachedQuadLightData);
			}

			break;

		case ENHANCED:
			shouldCompare = false;
			calcEnhanced(quad, cachedQuadLightData);
		}

		if (shouldCompare && vanillaCalc != null) {
			float[] vanillaAo = new float[4];
			int[] vanillaLight = new int[4];
			calcVanilla(quad, vanillaAo, vanillaLight);

			for (int i = 0; i < 4; i++) {
				if (cachedQuadLightData.br[i] != vanillaLight[i] || !MathHelper.approximatelyEquals(cachedQuadLightData.lm[i], vanillaAo[i])) {
					LOGGER.info("Mismatch for {} @ {}", blockInfo.blockState.toString(), blockInfo.blockPos.toString());
					LOGGER.info("Flags = {}, LightFace = {}", quad.geometryFlags(), quad.lightFace());

					LOGGER.info("    Old Brightness: {}, {}, {}, {}", vanillaAo[0], vanillaAo[1], vanillaAo[2], vanillaAo[3]);
					LOGGER.info("    New Brightness: {}, {}, {}, {}", cachedQuadLightData.br[0], cachedQuadLightData.br[1], cachedQuadLightData.br[2], cachedQuadLightData.br[3]);
					LOGGER.info("    Old Lightmap: {}, {}, {}, {}", Integer.toHexString(vanillaLight[0]), Integer.toHexString(vanillaLight[1]), Integer.toHexString(vanillaLight[2]), Integer.toHexString(vanillaLight[3]));
					LOGGER.info("    New Lightmap: {}, {}, {}, {}", Integer.toHexString(cachedQuadLightData.lm[0]), Integer.toHexString(cachedQuadLightData.lm[1]), Integer.toHexString(cachedQuadLightData.lm[2]), Integer.toHexString(cachedQuadLightData.lm[3]));
					break;
				}
			}
		}
	}


	// These are what vanilla AO calc wants, per its usage in vanilla code
	// Because this instance is effectively thread-local, we preserve instances
	// to avoid making a new allocation each call.
	private final float[] vanillaAoData = new float[DirectionUtil.ALL_DIRECTIONS.length * 2];
	private final BitSet vanillaAoControlBits = new BitSet(3);
	private final int[] vertexData = new int[EncodingFormat.QUAD_STRIDE];

	private void calcVanilla(MutableQuadViewImpl quad, QuadLightData cachedQuadLightData)
	{
		calcVanilla(quad, cachedQuadLightData.br, cachedQuadLightData.lm);
	}

	private void calcVanilla(MutableQuadViewImpl quad, float[] br, int[] lm)
	{
		vanillaAoControlBits.clear();
		final Direction face = quad.lightFace();
		quad.toVanilla(0, vertexData, 0, false);

		VanillaAoHelper.updateShape(blockInfo.blockView, blockInfo.blockState, blockInfo.blockPos, vertexData, face, vanillaAoData, vanillaAoControlBits);
		vanillaCalc.fabric_apply(blockInfo.blockView, blockInfo.blockState, blockInfo.blockPos, quad.lightFace(), vanillaAoData, vanillaAoControlBits, quad.hasShade());

		System.arraycopy(vanillaCalc.fabric_colorMultiplier(), 0, br, 0, 4);
		System.arraycopy(vanillaCalc.fabric_brightness(), 0, lm, 0, 4);
	}

	private void calcFastVanilla(MutableQuadViewImpl quad, QuadLightData cachedQuadLightData) {
		int flags = quad.geometryFlags();

		// force to block face if shape is full cube - matches vanilla logic
		if ((flags & LIGHT_FACE_FLAG) == 0 && (flags & AXIS_ALIGNED_FLAG) == AXIS_ALIGNED_FLAG && Block.isShapeFullCube(blockInfo.blockState.getCollisionShape(blockInfo.blockView, blockInfo.blockPos))) {
			flags |= LIGHT_FACE_FLAG;
		}

		if ((flags & CUBIC_FLAG) == 0) {
			vanillaPartialFace(quad, cachedQuadLightData, (flags & LIGHT_FACE_FLAG) != 0);
		} else {
			vanillaFullFace(quad, cachedQuadLightData, (flags & LIGHT_FACE_FLAG) != 0);
		}
	}

	private void calcEnhanced(MutableQuadViewImpl quad, QuadLightData cachedQuadLightData) {
		switch (quad.geometryFlags()) {
		case AXIS_ALIGNED_FLAG | CUBIC_FLAG | LIGHT_FACE_FLAG:
		case AXIS_ALIGNED_FLAG | LIGHT_FACE_FLAG:
			vanillaPartialFace(quad, cachedQuadLightData, true);
			break;

		case AXIS_ALIGNED_FLAG | CUBIC_FLAG:
		case AXIS_ALIGNED_FLAG:
			blendedPartialFace(quad, cachedQuadLightData);
			break;

		default:
			irregularFace(quad, cachedQuadLightData);
			break;
		}
	}

	private void vanillaFullFace(QuadViewImpl quad, QuadLightData cachedQuadLightData, boolean isOnLightFace) {
		final Direction lightFace = quad.lightFace();
		computeFace(lightFace, isOnLightFace, quad.hasShade()).toArray(cachedQuadLightData.br, cachedQuadLightData.lm, VERTEX_MAP[lightFace.getId()]);
	}

	private void vanillaPartialFace(QuadViewImpl quad, QuadLightData cachedQuadLightData, boolean isOnLightFace) {
		final Direction lightFace = quad.lightFace();
		AoFaceData faceData = computeFace(lightFace, isOnLightFace, quad.hasShade());
		final WeightFunction wFunc = AoFace.get(lightFace).weightFunc;
		final float[] w = this.w;

		for (int i = 0; i < 4; i++) {
			wFunc.apply(quad, i, w);
			cachedQuadLightData.lm[i] = faceData.weightedCombinedLight(w);
			cachedQuadLightData.br[i] = faceData.weigtedAo(w);
		}
	}

	/** used in {@link #blendedInsetFace(QuadViewImpl quad, int vertexIndex, Direction lightFace)} as return variable to avoid new allocation. */
	AoFaceData tmpFace = new AoFaceData();

	/** Returns linearly interpolated blend of outer and inner face based on depth of vertex in face. */
	private AoFaceData blendedInsetFace(QuadViewImpl quad, int vertexIndex, Direction lightFace) {
		final float w1 = AoFace.get(lightFace).depthFunc.apply(quad, vertexIndex);
		final float w0 = 1 - w1;
		return AoFaceData.weightedMean(computeFace(lightFace, true, quad.hasShade()), w0, computeFace(lightFace, false, quad.hasShade()), w1, tmpFace);
	}

	/**
	 * Like {@link #blendedInsetFace(QuadViewImpl quad, int vertexIndex, Direction lightFace)} but optimizes if depth is 0 or 1.
	 * Used for irregular faces when depth varies by vertex to avoid unneeded interpolation.
	 */
	private AoFaceData gatherInsetFace(QuadViewImpl quad, int vertexIndex, Direction lightFace) {
		final float w1 = AoFace.get(lightFace).depthFunc.apply(quad, vertexIndex);

		if (MathHelper.approximatelyEquals(w1, 0)) {
			return computeFace(lightFace, true, quad.hasShade());
		} else if (MathHelper.approximatelyEquals(w1, 1)) {
			return computeFace(lightFace, false, quad.hasShade());
		} else {
			final float w0 = 1 - w1;
			return AoFaceData.weightedMean(computeFace(lightFace, true, quad.hasShade()), w0, computeFace(lightFace, false, quad.hasShade()), w1, tmpFace);
		}
	}

	private void blendedPartialFace(QuadViewImpl quad, QuadLightData cachedQuadLightData) {
		final Direction lightFace = quad.lightFace();
		AoFaceData faceData = blendedInsetFace(quad, 0, lightFace);
		final WeightFunction wFunc = AoFace.get(lightFace).weightFunc;

		for (int i = 0; i < 4; i++) {
			wFunc.apply(quad, i, w);
			cachedQuadLightData.lm[i] = faceData.weightedCombinedLight(w);
			cachedQuadLightData.br[i] = faceData.weigtedAo(w);
		}
	}

	/** used exclusively in irregular face to avoid new heap allocations each call. */
	private final Vec3f vertexNormal = new Vec3f();

	private void irregularFace(MutableQuadViewImpl quad, QuadLightData cachedQuadLightData) {
		final Vec3f faceNorm = quad.faceNormal();
		Vec3f normal;
		final float[] w = this.w;

		for (int i = 0; i < 4; i++) {
			normal = quad.hasNormal(i) ? quad.copyNormal(i, vertexNormal) : faceNorm;
			float ao = 0, sky = 0, block = 0, maxAo = 0;
			int maxSky = 0, maxBlock = 0;

			final float x = normal.getX();

			if (!MathHelper.approximatelyEquals(0f, x)) {
				final Direction face = x > 0 ? Direction.EAST : Direction.WEST;
				final AoFaceData fd = gatherInsetFace(quad, i, face);
				AoFace.get(face).weightFunc.apply(quad, i, w);
				final float n = x * x;
				final float a = fd.weigtedAo(w);
				final int s = fd.weigtedSkyLight(w);
				final int b = fd.weigtedBlockLight(w);
				ao += n * a;
				sky += n * s;
				block += n * b;
				maxAo = a;
				maxSky = s;
				maxBlock = b;
			}

			final float y = normal.getY();

			if (!MathHelper.approximatelyEquals(0f, y)) {
				final Direction face = y > 0 ? Direction.UP : Direction.DOWN;
				final AoFaceData fd = gatherInsetFace(quad, i, face);
				AoFace.get(face).weightFunc.apply(quad, i, w);
				final float n = y * y;
				final float a = fd.weigtedAo(w);
				final int s = fd.weigtedSkyLight(w);
				final int b = fd.weigtedBlockLight(w);
				ao += n * a;
				sky += n * s;
				block += n * b;
				maxAo = Math.max(maxAo, a);
				maxSky = Math.max(maxSky, s);
				maxBlock = Math.max(maxBlock, b);
			}

			final float z = normal.getZ();

			if (!MathHelper.approximatelyEquals(0f, z)) {
				final Direction face = z > 0 ? Direction.SOUTH : Direction.NORTH;
				final AoFaceData fd = gatherInsetFace(quad, i, face);
				AoFace.get(face).weightFunc.apply(quad, i, w);
				final float n = z * z;
				final float a = fd.weigtedAo(w);
				final int s = fd.weigtedSkyLight(w);
				final int b = fd.weigtedBlockLight(w);
				ao += n * a;
				sky += n * s;
				block += n * b;
				maxAo = Math.max(maxAo, a);
				maxSky = Math.max(maxSky, s);
				maxBlock = Math.max(maxBlock, b);
			}

			cachedQuadLightData.br[i] = (ao + maxAo) * 0.5f;
			cachedQuadLightData.lm[i] = (((int) ((sky + maxSky) * 0.5f) & 0xF0) << 16) | ((int) ((block + maxBlock) * 0.5f) & 0xF0);
		}
	}

	/**
	 * Computes smoothed brightness and Ao shading for four corners of a block face.
	 * Outer block face is what you normally see and what you get get when second
	 * parameter is true. Inner is light *within* the block and usually darker.
	 * It is blended with the outer face for inset surfaces, but is also used directly
	 * in vanilla logic for some blocks that aren't full opaque cubes.
	 * Except for parameterization, the logic itself is practically identical to vanilla.
	 */
	private AoFaceData computeFace(Direction lightFace, boolean isOnBlockFace, boolean shade) {
		final int faceDataIndex = isOnBlockFace ? lightFace.getId() : lightFace.getId() + 6;
		final int mask = 1 << faceDataIndex;
		final AoFaceData result = faceData[faceDataIndex];

		if ((completionFlags & mask) == 0) {
			completionFlags |= mask;

			final BlockRenderView world = blockInfo.blockView;
			final BlockPos pos = blockInfo.blockPos;
			final BlockPos.Mutable lightPos = this.lightPos;
			final BlockPos.Mutable searchPos = this.searchPos;

			lightPos.set(isOnBlockFace ? pos.offset(lightFace) : pos);
			AoFace aoFace = AoFace.get(lightFace);

			searchPos.set(lightPos).move(aoFace.neighbors[0]);
			final int light0 = brightnessFunc.applyAsInt(searchPos);
			final float ao0 = aoFunc.apply(searchPos);
			searchPos.set(lightPos).move(aoFace.neighbors[1]);
			final int light1 = brightnessFunc.applyAsInt(searchPos);
			final float ao1 = aoFunc.apply(searchPos);
			searchPos.set(lightPos).move(aoFace.neighbors[2]);
			final int light2 = brightnessFunc.applyAsInt(searchPos);
			final float ao2 = aoFunc.apply(searchPos);
			searchPos.set(lightPos).move(aoFace.neighbors[3]);
			final int light3 = brightnessFunc.applyAsInt(searchPos);
			final float ao3 = aoFunc.apply(searchPos);

			// vanilla was further offsetting these in the direction of the light face
			// but it was actually mis-sampling and causing visible artifacts in certain situation
			searchPos.set(lightPos).move(aoFace.neighbors[0]); //.setOffset(lightFace);
			if (!Indium.FIX_SMOOTH_LIGHTING_OFFSET) searchPos.move(lightFace);
			final boolean isClear0 = world.getBlockState(searchPos).getOpacity(world, searchPos) == 0;
			searchPos.set(lightPos).move(aoFace.neighbors[1]); //.setOffset(lightFace);
			if (!Indium.FIX_SMOOTH_LIGHTING_OFFSET) searchPos.move(lightFace);
			final boolean isClear1 = world.getBlockState(searchPos).getOpacity(world, searchPos) == 0;
			searchPos.set(lightPos).move(aoFace.neighbors[2]); //.setOffset(lightFace);
			if (!Indium.FIX_SMOOTH_LIGHTING_OFFSET) searchPos.move(lightFace);
			final boolean isClear2 = world.getBlockState(searchPos).getOpacity(world, searchPos) == 0;
			searchPos.set(lightPos).move(aoFace.neighbors[3]); //.setOffset(lightFace);
			if (!Indium.FIX_SMOOTH_LIGHTING_OFFSET) searchPos.move(lightFace);
			final boolean isClear3 = world.getBlockState(searchPos).getOpacity(world, searchPos) == 0;

			// c = corner - values at corners of face
			int cLight0, cLight1, cLight2, cLight3;
			float cAo0, cAo1, cAo2, cAo3;

			// If neighbors on both side of the corner are opaque, then apparently we use the light/shade
			// from one of the sides adjacent to the corner.  If either neighbor is clear (no light subtraction)
			// then we use values from the outwardly diagonal corner. (outwardly = position is one more away from light face)
			if (!isClear2 && !isClear0) {
				cAo0 = ao0;
				cLight0 = light0;
			} else {
				searchPos.set(lightPos).move(aoFace.neighbors[0]).move(aoFace.neighbors[2]);
				cAo0 = aoFunc.apply(searchPos);
				cLight0 = brightnessFunc.applyAsInt(searchPos);
			}

			if (!isClear3 && !isClear0) {
				cAo1 = ao0;
				cLight1 = light0;
			} else {
				searchPos.set(lightPos).move(aoFace.neighbors[0]).move(aoFace.neighbors[3]);
				cAo1 = aoFunc.apply(searchPos);
				cLight1 = brightnessFunc.applyAsInt(searchPos);
			}

			if (!isClear2 && !isClear1) {
				cAo2 = ao1;
				cLight2 = light1;
			} else {
				searchPos.set(lightPos).move(aoFace.neighbors[1]).move(aoFace.neighbors[2]);
				cAo2 = aoFunc.apply(searchPos);
				cLight2 = brightnessFunc.applyAsInt(searchPos);
			}

			if (!isClear3 && !isClear1) {
				cAo3 = ao1;
				cLight3 = light1;
			} else {
				searchPos.set(lightPos).move(aoFace.neighbors[1]).move(aoFace.neighbors[3]);
				cAo3 = aoFunc.apply(searchPos);
				cLight3 = brightnessFunc.applyAsInt(searchPos);
			}

			// If on block face or neighbor isn't occluding, "center" will be neighbor brightness
			// Doesn't use light pos because logic not based solely on this block's geometry
			int lightCenter;
			searchPos.set(pos).move(lightFace);

			if (isOnBlockFace || !world.getBlockState(searchPos).isOpaqueFullCube(world, searchPos)) {
				lightCenter = brightnessFunc.applyAsInt(searchPos);
			} else {
				lightCenter = brightnessFunc.applyAsInt(pos);
			}

			float aoCenter = aoFunc.apply(isOnBlockFace ? lightPos : pos);
			float worldBrightness = world.getBrightness(lightFace, shade);

			result.a0 = ((ao3 + ao0 + cAo1 + aoCenter) * 0.25F) * worldBrightness;
			result.a1 = ((ao2 + ao0 + cAo0 + aoCenter) * 0.25F) * worldBrightness;
			result.a2 = ((ao2 + ao1 + cAo2 + aoCenter) * 0.25F) * worldBrightness;
			result.a3 = ((ao3 + ao1 + cAo3 + aoCenter) * 0.25F) * worldBrightness;

			result.l0(meanBrightness(light3, light0, cLight1, lightCenter));
			result.l1(meanBrightness(light2, light0, cLight0, lightCenter));
			result.l2(meanBrightness(light2, light1, cLight2, lightCenter));
			result.l3(meanBrightness(light3, light1, cLight3, lightCenter));
		}

		return result;
	}

	/**
	 * Vanilla code excluded missing light values from mean but was not isotropic.
	 * Still need to substitute or edges are too dark but consistently use the min
	 * value from all four samples.
	 */
	private static int meanBrightness(int a, int b, int c, int d) {
		if (Indium.FIX_SMOOTH_LIGHTING_OFFSET) {
			return a == 0 || b == 0 || c == 0 || d == 0 ? meanEdgeBrightness(a, b, c, d) : meanInnerBrightness(a, b, c, d);
		} else {
			return vanillaMeanBrightness(a, b, c, d);
		}
	}

	/** vanilla logic - excludes missing light values from mean and has anisotropy defect mentioned above. */
	private static int vanillaMeanBrightness(int a, int b, int c, int d) {
		if (a == 0) a = d;
		if (b == 0) b = d;
		if (c == 0) c = d;
		// bitwise divide by 4, clamp to expected (positive) range
		return a + b + c + d >> 2 & 16711935;
	}

	private static int meanInnerBrightness(int a, int b, int c, int d) {
		// bitwise divide by 4, clamp to expected (positive) range
		return a + b + c + d >> 2 & 16711935;
	}

	private static int nonZeroMin(int a, int b) {
		if (a == 0) return b;
		if (b == 0) return a;
		return Math.min(a, b);
	}

	private static int meanEdgeBrightness(int a, int b, int c, int d) {
		final int min = nonZeroMin(nonZeroMin(a, b), nonZeroMin(c, d));
		return meanInnerBrightness(max(a, min), max(b, min), max(c, min), max(d, min));
	}
}
