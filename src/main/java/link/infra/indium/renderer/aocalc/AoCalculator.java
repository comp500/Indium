/*
 * Copyright (c) 2016, 2017, 2018, 2019 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *	 http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package link.infra.indium.renderer.aocalc;

import static link.infra.indium.renderer.helper.GeometryHelper.AXIS_ALIGNED_FLAG;
import static link.infra.indium.renderer.helper.GeometryHelper.CUBIC_FLAG;
import static link.infra.indium.renderer.helper.GeometryHelper.LIGHT_FACE_FLAG;
import static me.jellysquid.mods.sodium.client.model.light.data.LightDataAccess.getLightmap;
import static me.jellysquid.mods.sodium.client.model.light.data.LightDataAccess.unpackAO;
import static me.jellysquid.mods.sodium.client.model.light.data.LightDataAccess.unpackEM;
import static me.jellysquid.mods.sodium.client.model.light.data.LightDataAccess.unpackFO;
import static me.jellysquid.mods.sodium.client.model.light.data.LightDataAccess.unpackOP;
import static net.minecraft.util.math.Direction.DOWN;
import static net.minecraft.util.math.Direction.EAST;
import static net.minecraft.util.math.Direction.NORTH;
import static net.minecraft.util.math.Direction.SOUTH;
import static net.minecraft.util.math.Direction.UP;
import static net.minecraft.util.math.Direction.WEST;

import java.util.BitSet;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import link.infra.indium.Indium;
import link.infra.indium.mixin.renderer.AccessAmbientOcclusionCalculator;
import link.infra.indium.renderer.aocalc.AoFace.WeightFunction;
import link.infra.indium.renderer.mesh.EncodingFormat;
import link.infra.indium.renderer.mesh.MutableQuadViewImpl;
import link.infra.indium.renderer.mesh.QuadViewImpl;
import link.infra.indium.renderer.render.BlockRenderInfo;
import me.jellysquid.mods.sodium.client.model.light.data.LightDataAccess;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;

/**
 * Adaptation of inner, non-static class in BlockModelRenderer that serves same purpose.
 */
public class AoCalculator {
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

	@Nullable
	private final AccessAmbientOcclusionCalculator vanillaCalc;
	private final BlockRenderInfo blockInfo;
	private final LightDataAccess lightCache;

	/** caches results of {@link #computeFace(Direction, boolean, boolean)} for the current block. */
	private final AoFaceData[] faceData = new AoFaceData[24];

	/** indicates which elements of {@link #faceData} have been computed for the current block. */
	private int completionFlags = 0;

	/** holds per-corner weights - used locally to avoid new allocation. */
	private final float[] w = new float[4];

	// outputs
	public final float[] ao = new float[4];
	public final int[] light = new int[4];

	public AoCalculator(BlockRenderInfo blockInfo, LightDataAccess lightCache) {
		this.blockInfo = blockInfo;
		this.lightCache = lightCache;
		this.vanillaCalc = VanillaAoHelper.get();

		for (int i = 0; i < 24; i++) {
			faceData[i] = new AoFaceData();
		}
	}

	/** call at start of each new block. */
	public void clear() {
		completionFlags = 0;
	}

	public void compute(MutableQuadViewImpl quad, boolean isVanilla) {
		final AoConfig config = Indium.AMBIENT_OCCLUSION_MODE;

		switch (config) {
		case VANILLA:
			// prevent NPE in error case of failed reflection for vanilla calculator access
			if (vanillaCalc == null) {
				calcFastVanilla(quad);
			} else {
				calcVanilla(quad);
			}

			break;

		case EMULATE:
			calcFastVanilla(quad);
			break;

		case HYBRID:
		default:
			if (isVanilla) {
				calcFastVanilla(quad);
			} else {
				calcEnhanced(quad);
			}

			break;

		case ENHANCED:
			calcEnhanced(quad);
		}
	}

	private void calcVanilla(MutableQuadViewImpl quad) {
		calcVanilla(quad, ao, light);
	}

	// These are what vanilla AO calc wants, per its usage in vanilla code
	// Because this instance is effectively thread-local, we preserve instances
	// to avoid making a new allocation each call.
	private final float[] vanillaAoData = new float[Direction.values().length * 2];
	private final BitSet vanillaAoControlBits = new BitSet(3);
	private final int[] vertexData = new int[EncodingFormat.QUAD_STRIDE];

	private void calcVanilla(MutableQuadViewImpl quad, float[] aoDest, int[] lightDest) {
		vanillaAoControlBits.clear();
		final Direction lightFace = quad.lightFace();
		quad.toVanilla(vertexData, 0);

		VanillaAoHelper.getQuadDimensions(blockInfo.blockView, blockInfo.blockState, blockInfo.blockPos, vertexData, lightFace, vanillaAoData, vanillaAoControlBits);
		vanillaCalc.indium$apply(blockInfo.blockView, blockInfo.blockState, blockInfo.blockPos, lightFace, vanillaAoData, vanillaAoControlBits, quad.hasShade());

		System.arraycopy(vanillaCalc.indium$brightness(), 0, aoDest, 0, 4);
		System.arraycopy(vanillaCalc.indium$light(), 0, lightDest, 0, 4);
	}

	private void calcFastVanilla(MutableQuadViewImpl quad) {
		int flags = quad.geometryFlags();

		// force to block face if shape is full cube - matches vanilla logic
		if ((flags & LIGHT_FACE_FLAG) == 0 && (flags & AXIS_ALIGNED_FLAG) != 0 && blockInfo.blockState.isFullCube(blockInfo.blockView, blockInfo.blockPos)) {
			flags |= LIGHT_FACE_FLAG;
		}

		if ((flags & CUBIC_FLAG) == 0) {
			vanillaPartialFace(quad, quad.lightFace(), (flags & LIGHT_FACE_FLAG) != 0, quad.hasShade());
		} else {
			vanillaFullFace(quad, quad.lightFace(), (flags & LIGHT_FACE_FLAG) != 0, quad.hasShade());
		}
	}

	private void calcEnhanced(MutableQuadViewImpl quad) {
		switch (quad.geometryFlags()) {
		case AXIS_ALIGNED_FLAG | CUBIC_FLAG | LIGHT_FACE_FLAG:
		case AXIS_ALIGNED_FLAG | LIGHT_FACE_FLAG:
			vanillaPartialFace(quad, quad.lightFace(), true, quad.hasShade());
			break;

		case AXIS_ALIGNED_FLAG | CUBIC_FLAG:
		case AXIS_ALIGNED_FLAG:
			blendedPartialFace(quad, quad.lightFace(), quad.hasShade());
			break;

		default:
			irregularFace(quad, quad.hasShade());
			break;
		}
	}

	private void vanillaFullFace(QuadViewImpl quad, Direction lightFace, boolean isOnLightFace, boolean shade) {
		computeFace(lightFace, isOnLightFace, shade).toArray(ao, light, VERTEX_MAP[lightFace.getId()]);
	}

	private void vanillaPartialFace(QuadViewImpl quad, Direction lightFace, boolean isOnLightFace, boolean shade) {
		AoFaceData faceData = computeFace(lightFace, isOnLightFace, shade);
		final WeightFunction wFunc = AoFace.get(lightFace).weightFunc;
		final float[] w = this.w;

		for (int i = 0; i < 4; i++) {
			wFunc.apply(quad, i, w);
			light[i] = faceData.weightedCombinedLight(w);
			ao[i] = faceData.weigtedAo(w);
		}
	}

	/** used in {@link #blendedInsetFace(QuadViewImpl quad, int vertexIndex, Direction lightFace, boolean shade)} as return variable to avoid new allocation. */
	AoFaceData tmpFace = new AoFaceData();

	/** Returns linearly interpolated blend of outer and inner face based on depth of vertex in face. */
	private AoFaceData blendedInsetFace(QuadViewImpl quad, int vertexIndex, Direction lightFace, boolean shade) {
		final float w1 = AoFace.get(lightFace).depthFunc.apply(quad, vertexIndex);
		final float w0 = 1 - w1;
		return AoFaceData.weightedMean(computeFace(lightFace, true, shade), w0, computeFace(lightFace, false, shade), w1, tmpFace);
	}

	/**
	 * Like {@link #blendedInsetFace(QuadViewImpl quad, int vertexIndex, Direction lightFace, boolean shade)} but optimizes if depth is 0 or 1.
	 * Used for irregular faces when depth varies by vertex to avoid unneeded interpolation.
	 */
	private AoFaceData gatherInsetFace(QuadViewImpl quad, int vertexIndex, Direction lightFace, boolean shade) {
		final float w1 = AoFace.get(lightFace).depthFunc.apply(quad, vertexIndex);

		if (MathHelper.approximatelyEquals(w1, 0)) {
			return computeFace(lightFace, true, shade);
		} else if (MathHelper.approximatelyEquals(w1, 1)) {
			return computeFace(lightFace, false, shade);
		} else {
			final float w0 = 1 - w1;
			return AoFaceData.weightedMean(computeFace(lightFace, true, shade), w0, computeFace(lightFace, false, shade), w1, tmpFace);
		}
	}

	private void blendedPartialFace(QuadViewImpl quad, Direction lightFace, boolean shade) {
		AoFaceData faceData = blendedInsetFace(quad, 0, lightFace, shade);
		final WeightFunction wFunc = AoFace.get(lightFace).weightFunc;

		for (int i = 0; i < 4; i++) {
			wFunc.apply(quad, i, w);
			light[i] = faceData.weightedCombinedLight(w);
			ao[i] = faceData.weigtedAo(w);
		}
	}

	/** used exclusively in irregular face to avoid new heap allocations each call. */
	private final Vector3f vertexNormal = new Vector3f();

	private void irregularFace(MutableQuadViewImpl quad, boolean shade) {
		final Vector3f faceNorm = quad.faceNormal();
		Vector3f normal;
		final float[] w = this.w;
		final float[] aoResult = this.ao;
		final int[] lightResult = this.light;

		for (int i = 0; i < 4; i++) {
			normal = quad.hasNormal(i) ? quad.copyNormal(i, vertexNormal) : faceNorm;
			float ao = 0, sky = 0, block = 0, maxAo = 0;
			int maxSky = 0, maxBlock = 0;

			final float x = normal.x();

			if (!MathHelper.approximatelyEquals(0f, x)) {
				final Direction face = x > 0 ? Direction.EAST : Direction.WEST;
				final AoFaceData fd = gatherInsetFace(quad, i, face, shade);
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

			final float y = normal.y();

			if (!MathHelper.approximatelyEquals(0f, y)) {
				final Direction face = y > 0 ? Direction.UP : Direction.DOWN;
				final AoFaceData fd = gatherInsetFace(quad, i, face, shade);
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

			final float z = normal.z();

			if (!MathHelper.approximatelyEquals(0f, z)) {
				final Direction face = z > 0 ? Direction.SOUTH : Direction.NORTH;
				final AoFaceData fd = gatherInsetFace(quad, i, face, shade);
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

			aoResult[i] = (ao + maxAo) * 0.5f;
			lightResult[i] = (((int) ((sky + maxSky) * 0.5f) & 0xF0) << 16) | ((int) ((block + maxBlock) * 0.5f) & 0xF0);
		}
	}

	private AoFaceData computeFace(Direction lightFace, boolean isOnBlockFace, boolean shade) {
		final int faceDataIndex = shade ? (isOnBlockFace ? lightFace.getId() : lightFace.getId() + 6) : (isOnBlockFace ? lightFace.getId() + 12 : lightFace.getId() + 18);
		final int mask = 1 << faceDataIndex;
		final AoFaceData result = faceData[faceDataIndex];

		if ((completionFlags & mask) == 0) {
			completionFlags |= mask;
			computeFace(result, lightFace, isOnBlockFace, shade);
		}

		return result;
	}

	/**
	 * Computes smoothed brightness and Ao shading for four corners of a block face.
	 * Outer block face is what you normally see and what you get when the second
	 * parameter is true. Inner is light *within* the block and usually darker.
	 * It is blended with the outer face for inset surfaces, but is also used directly
	 * in vanilla logic for some blocks that aren't full opaque cubes.
	 * Except for parameterization, the logic itself is practically identical to vanilla.
	 */
	private void computeFace(AoFaceData result, Direction lightFace, boolean isOnBlockFace, boolean shade) {
		final LightDataAccess cache = lightCache;
		final BlockPos pos = blockInfo.blockPos;

		final int x = pos.getX();
		final int y = pos.getY();
		final int z = pos.getZ();

		final int lightPosX;
		final int lightPosY;
		final int lightPosZ;

		if (isOnBlockFace) {
			lightPosX = x + lightFace.getOffsetX();
			lightPosY = y + lightFace.getOffsetY();
			lightPosZ = z + lightFace.getOffsetZ();
		} else {
			lightPosX = x;
			lightPosY = y;
			lightPosZ = z;
		}

		AoFace aoFace = AoFace.get(lightFace);
		Direction[] neighbors = aoFace.neighbors;

		// Vanilla was further offsetting the positions for opaque block checks in the
		// direction of the light face, but it was actually mis-sampling and causing
		// visible artifacts in certain situations

		final int word0 = cache.get(lightPosX, lightPosY, lightPosZ, neighbors[0]);
		final int light0 = getLightmap(word0);
		final float ao0 = unpackAO(word0);
		final boolean opaque0 = unpackOP(word0);
		final boolean em0 = unpackEM(word0);

		final int word1 = cache.get(lightPosX, lightPosY, lightPosZ, neighbors[1]);
		final int light1 = getLightmap(word1);
		final float ao1 = unpackAO(word1);
		final boolean opaque1 = unpackOP(word1);
		final boolean em1 = unpackEM(word1);

		final int word2 = cache.get(lightPosX, lightPosY, lightPosZ, neighbors[2]);
		final int light2 = getLightmap(word2);
		final float ao2 = unpackAO(word2);
		final boolean opaque2 = unpackOP(word2);
		final boolean em2 = unpackEM(word2);

		final int word3 = cache.get(lightPosX, lightPosY, lightPosZ, neighbors[3]);
		final int light3 = getLightmap(word3);
		final float ao3 = unpackAO(word3);
		final boolean opaque3 = unpackOP(word3);
		final boolean em3 = unpackEM(word3);

		// c = corner - values at corners of face
		int cLight0, cLight1, cLight2, cLight3;
		float cAo0, cAo1, cAo2, cAo3;
		boolean cEm0, cEm1, cEm2, cEm3;

		// If neighbors on both sides of the corner are opaque, then apparently we use the light/shade
		// from one of the sides adjacent to the corner.  If either neighbor is clear (no light subtraction)
		// then we use values from the outwardly diagonal corner. (outwardly = position is one more away from light face)
		if (opaque2 && opaque0) {
			cAo0 = ao0;
			cLight0 = light0;
			cEm0 = em0;
		} else {
			final int word02 = cache.get(lightPosX, lightPosY, lightPosZ, neighbors[0], neighbors[2]);
			cAo0 = unpackAO(word02);
			cLight0 = getLightmap(word02);
			cEm0 = unpackEM(word02);
		}

		if (opaque3 && opaque0) {
			cAo1 = ao0;
			cLight1 = light0;
			cEm1 = em0;
		} else {
			final int word03 = cache.get(lightPosX, lightPosY, lightPosZ, neighbors[0], neighbors[3]);
			cAo1 = unpackAO(word03);
			cLight1 = getLightmap(word03);
			cEm1 = unpackEM(word03);
		}

		if (opaque2 && opaque1) {
			cAo2 = ao1;
			cLight2 = light1;
			cEm2 = em1;
		} else {
			final int word12 = cache.get(lightPosX, lightPosY, lightPosZ, neighbors[1], neighbors[2]);
			cAo2 = unpackAO(word12);
			cLight2 = getLightmap(word12);
			cEm2 = unpackEM(word12);
		}

		if (opaque3 && opaque1) {
			cAo3 = ao1;
			cLight3 = light1;
			cEm3 = em1;
		} else {
			final int word13 = cache.get(lightPosX, lightPosY, lightPosZ, neighbors[1], neighbors[3]);
			cAo3 = unpackAO(word13);
			cLight3 = getLightmap(word13);
			cEm3 = unpackEM(word13);
		}

		int centerWord = cache.get(lightPosX, lightPosY, lightPosZ);

		// If on block face or neighbor isn't occluding, "center" will be neighbor brightness
		// Doesn't use light pos because logic not based solely on this block's geometry
		int lightCenter;
		boolean emCenter;

		if (isOnBlockFace && unpackFO(centerWord)) {
			final int originWord = cache.get(x, y, z);
			lightCenter = getLightmap(originWord);
			emCenter = unpackEM(originWord);
		} else {
			lightCenter = getLightmap(centerWord);
			emCenter = unpackEM(centerWord);
		}

		float aoCenter = unpackAO(centerWord);
		float worldBrightness = blockInfo.blockView.getBrightness(lightFace, shade);

		result.a0 = ((ao3 + ao0 + cAo1 + aoCenter) * 0.25F) * worldBrightness;
		result.a1 = ((ao2 + ao0 + cAo0 + aoCenter) * 0.25F) * worldBrightness;
		result.a2 = ((ao2 + ao1 + cAo2 + aoCenter) * 0.25F) * worldBrightness;
		result.a3 = ((ao3 + ao1 + cAo3 + aoCenter) * 0.25F) * worldBrightness;

		result.l0(calculateCornerBrightness(light3, light0, cLight1, lightCenter, em3, em0, cEm1, emCenter));
		result.l1(calculateCornerBrightness(light2, light0, cLight0, lightCenter, em2, em0, cEm0, emCenter));
		result.l2(calculateCornerBrightness(light2, light1, cLight2, lightCenter, em2, em1, cEm2, emCenter));
		result.l3(calculateCornerBrightness(light3, light1, cLight3, lightCenter, em3, em1, cEm3, emCenter));
	}

	private static int calculateCornerBrightness(int a, int b, int c, int d, boolean aem, boolean bem, boolean cem, boolean dem) {
		// FIX: Normalize corner vectors correctly to the minimum non-zero value between each one to prevent
		// strange issues
		if ((a == 0) || (b == 0) || (c == 0) || (d == 0)) {
			// Find the minimum value between all corners
			final int min = minNonZero(minNonZero(a, b), minNonZero(c, d));

			// Normalize the corner values
			a = Math.max(a, min);
			b = Math.max(b, min);
			c = Math.max(c, min);
			d = Math.max(d, min);
		}

		// FIX: Apply the fullbright lightmap from emissive blocks at the very end so it cannot influence
		// the minimum lightmap and produce incorrect results (for example, sculk sensors in a dark room)
		if (aem) {
			a = LightmapTextureManager.MAX_LIGHT_COORDINATE;
		}
		if (bem) {
			b = LightmapTextureManager.MAX_LIGHT_COORDINATE;
		}
		if (cem) {
			c = LightmapTextureManager.MAX_LIGHT_COORDINATE;
		}
		if (dem) {
			d = LightmapTextureManager.MAX_LIGHT_COORDINATE;
		}

		return ((a + b + c + d) >> 2) & 0xFF00FF;
	}

	private static int minNonZero(int a, int b) {
		if (a == 0) {
			return b;
		} else if (b == 0) {
			return a;
		}

		return Math.min(a, b);
	}
}
