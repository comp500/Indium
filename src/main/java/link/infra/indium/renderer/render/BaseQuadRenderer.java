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

package link.infra.indium.renderer.render;

import link.infra.indium.renderer.aocalc.AoCalculator;
import link.infra.indium.renderer.helper.ColorHelper;
import link.infra.indium.renderer.helper.GeometryHelper;
import link.infra.indium.renderer.mesh.MutableQuadViewImpl;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext.QuadTransform;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.math.*;

/**
 * Base quad-rendering class for fallback and mesh consumers.
 * Has most of the actual buffer-time lighting and coloring logic.
 */
public class BaseQuadRenderer {
	protected final QuadBufferer bufferer;
	protected final BlockRenderInfo blockInfo;
	protected final AoCalculator aoCalc;
	protected final QuadTransform transform;

	BaseQuadRenderer(QuadBufferer bufferer, BlockRenderInfo blockInfo, AoCalculator aoCalc, QuadTransform transform) {
		this.bufferer = bufferer;
		this.blockInfo = blockInfo;
		this.aoCalc = aoCalc;
		this.transform = transform;
	}

	/** handles block color and red-blue swizzle, common to all renders. */
	private void colorizeQuad(MutableQuadViewImpl q, int blockColorIndex) {
		if (blockColorIndex == -1) {
			for (int i = 0; i < 4; i++) {
				q.spriteColor(i, 0, ColorHelper.swapRedBlueIfNeeded(q.spriteColor(i, 0)));
			}
		} else {
			final int blockColor = blockInfo.blockColor(blockColorIndex);

			for (int i = 0; i < 4; i++) {
				q.spriteColor(i, 0, ColorHelper.swapRedBlueIfNeeded(ColorHelper.multiplyColor(blockColor, q.spriteColor(i, 0))));
			}
		}
	}

	/** final output step, common to all renders. */
	private void bufferQuad(MutableQuadViewImpl quad, RenderLayer renderLayer) {
		bufferer.bufferQuad(quad, renderLayer);
	}

	// routines below have a bit of copy-paste code reuse to avoid conditional execution inside a hot loop

	/** for non-emissive mesh quads and all fallback quads with smooth lighting. */
	protected void tessellateSmooth(MutableQuadViewImpl q, RenderLayer renderLayer, int blockColorIndex) {
		colorizeQuad(q, blockColorIndex);

		for (int i = 0; i < 4; i++) {
			q.spriteColor(i, 0, ColorHelper.multiplyRGB(q.spriteColor(i, 0), aoCalc.ao[i]));
			q.lightmap(i, ColorHelper.maxBrightness(q.lightmap(i), aoCalc.light[i]));
		}

		bufferQuad(q, renderLayer);
	}

	/** for emissive mesh quads with smooth lighting. */
	protected void tessellateSmoothEmissive(MutableQuadViewImpl q, RenderLayer renderLayer, int blockColorIndex) {
		colorizeQuad(q, blockColorIndex);

		for (int i = 0; i < 4; i++) {
			q.spriteColor(i, 0, ColorHelper.multiplyRGB(q.spriteColor(i, 0), aoCalc.ao[i]));
			q.lightmap(i, LightmapTextureManager.MAX_LIGHT_COORDINATE);
		}

		bufferQuad(q, renderLayer);
	}

	/** for non-emissive mesh quads and all fallback quads with flat lighting. */
	protected void tessellateFlat(MutableQuadViewImpl quad, RenderLayer renderLayer, int blockColorIndex) {
		colorizeQuad(quad, blockColorIndex);
		shadeFlatQuad(quad);

		final int brightness = flatBrightness(quad, blockInfo.blockState, blockInfo.blockPos);

		for (int i = 0; i < 4; i++) {
			quad.lightmap(i, ColorHelper.maxBrightness(quad.lightmap(i), brightness));
		}

		bufferQuad(quad, renderLayer);
	}

	/** for emissive mesh quads with flat lighting. */
	protected void tessellateFlatEmissive(MutableQuadViewImpl quad, RenderLayer renderLayer, int blockColorIndex) {
		colorizeQuad(quad, blockColorIndex);
		shadeFlatQuad(quad);

		for (int i = 0; i < 4; i++) {
			quad.lightmap(i, LightmapTextureManager.MAX_LIGHT_COORDINATE);
		}

		bufferQuad(quad, renderLayer);
	}

	private final BlockPos.Mutable mpos = new BlockPos.Mutable();

	/**
	 * Handles geometry-based check for using self brightness or neighbor brightness.
	 * That logic only applies in flat lighting.
	 */
	int flatBrightness(MutableQuadViewImpl quad, BlockState blockState, BlockPos pos) {
		mpos.set(pos);

		// To mirror Vanilla's behavior, if the face has a cull-face, always sample the light value
		// offset in that direction. See net.minecraft.client.render.block.BlockModelRenderer.renderQuadsFlat
		// for reference.
		if (quad.cullFace() != null) {
			mpos.move(quad.cullFace());
		} else {
			final int flags = quad.geometryFlags();

			if ((flags & GeometryHelper.LIGHT_FACE_FLAG) != 0 || ((flags & GeometryHelper.AXIS_ALIGNED_FLAG) != 0 && blockState.isFullCube(blockInfo.blockView, pos))) {
				mpos.move(quad.lightFace());
			}
		}

		// Unfortunately cannot use brightness cache here unless we implement one specifically for flat lighting. See #329
		return WorldRenderer.getLightmapCoordinates(blockInfo.blockView, blockState, mpos);
	}

	/**
	 * Starting in 1.16 flat shading uses dimension-specific diffuse factors that can be < 1.0
	 * even for un-shaded quads. These are also applied with AO shading but that is done in AO calculator.
	 */
	private void shadeFlatQuad(MutableQuadViewImpl quad) {
		if ((quad.geometryFlags() & GeometryHelper.AXIS_ALIGNED_FLAG) == 0 || quad.hasVertexNormals()) {
			// Quads that aren't direction-aligned or that have vertex normals need to be shaded
			// using interpolation - vanilla can't handle them. Generally only applies to modded models.
			final float faceShade = blockInfo.blockView.getBrightness(quad.lightFace(), quad.hasShade());

			for (int i = 0; i < 4; i++) {
				quad.spriteColor(i, 0, ColorHelper.multiplyRGB(quad.spriteColor(i, 0), vertexShade(quad, i, faceShade)));
			}
		} else {
			final float diffuseShade = blockInfo.blockView.getBrightness(quad.lightFace(), quad.hasShade());

			if (diffuseShade != 1.0f) {
				for (int i = 0; i < 4; i++) {
					quad.spriteColor(i, 0, ColorHelper.multiplyRGB(quad.spriteColor(i, 0), diffuseShade));
				}
			}
		}
	}

	private float vertexShade(MutableQuadViewImpl quad, int vertexIndex, float faceShade) {
		return quad.hasNormal(vertexIndex) ? normalShade(quad.normalX(vertexIndex), quad.normalY(vertexIndex), quad.normalZ(vertexIndex), quad.hasShade()) : faceShade;
	}

	/**
	 * Finds mean of per-face shading factors weighted by normal components.
	 * Not how light actually works but the vanilla diffuse shading model is a hack to start with
	 * and this gives reasonable results for non-cubic surfaces in a vanilla-style renderer.
	 */
	private float normalShade(float normalX, float normalY, float normalZ, boolean hasShade) {
		float sum = 0;
		float div = 0;

		if (normalX > 0) {
			sum += normalX * blockInfo.blockView.getBrightness(Direction.EAST, hasShade);
			div += normalX;
		} else if (normalX < 0) {
			sum += -normalX * blockInfo.blockView.getBrightness(Direction.WEST, hasShade);
			div -= normalX;
		}

		if (normalY > 0) {
			sum += normalY * blockInfo.blockView.getBrightness(Direction.UP, hasShade);
			div += normalY;
		} else if (normalY < 0) {
			sum += -normalY * blockInfo.blockView.getBrightness(Direction.DOWN, hasShade);
			div -= normalY;
		}

		if (normalZ > 0) {
			sum += normalZ * blockInfo.blockView.getBrightness(Direction.SOUTH, hasShade);
			div += normalZ;
		} else if (normalZ < 0) {
			sum += -normalZ * blockInfo.blockView.getBrightness(Direction.NORTH, hasShade);
			div -= normalZ;
		}

		return sum / div;
	}

	protected interface QuadBufferer {
		void bufferQuad(MutableQuadViewImpl quad, RenderLayer renderLayer);
	}
}
