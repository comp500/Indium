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

import link.infra.indium.Indium;
import link.infra.indium.renderer.aocalc.AoCalculator;
import link.infra.indium.renderer.aocalc.AoConfig;
import link.infra.indium.renderer.helper.ColorHelper;
import link.infra.indium.renderer.mesh.EncodingFormat;
import link.infra.indium.renderer.mesh.MutableQuadViewImpl;
import me.jellysquid.mods.sodium.client.model.light.data.LightDataAccess;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.material.DefaultMaterials;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.material.Material;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.util.TriState;
import net.fabricmc.fabric.impl.renderer.VanillaModelEncoder;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import static link.infra.indium.renderer.helper.GeometryHelper.AXIS_ALIGNED_FLAG;
import static link.infra.indium.renderer.helper.GeometryHelper.LIGHT_FACE_FLAG;

/**
 * Subclasses must set the {@link #blockInfo} and {@link #aoCalc} fields in their constructor.
 */
public abstract class AbstractBlockRenderContext extends AbstractRenderContext {
	protected BlockRenderInfo blockInfo;
	protected AoCalculator aoCalc;

	private final MutableQuadViewImpl editorQuad = new MutableQuadViewImpl() {
		{
			data = new int[EncodingFormat.TOTAL_STRIDE];
			clear();
		}

		@Override
		public void emitDirectly() {
			renderQuad(this, false);
		}
	};

	private final MutableQuadViewImpl vanillaModelEditorQuad = new MutableQuadViewImpl() {
		{
			data = new int[EncodingFormat.TOTAL_STRIDE];
			clear();
		}

		@Override
		public void emitDirectly() {
			renderQuad(this, true);
		}
	};

	private final BakedModelConsumerImpl vanillaModelConsumer = new BakedModelConsumerImpl();

	protected abstract LightDataAccess getLightCache();

	protected abstract void bufferQuad(MutableQuadViewImpl quad, Material material);

	@Override
	public QuadEmitter getEmitter() {
		editorQuad.clear();
		return editorQuad;
	}

	public QuadEmitter getVanillaModelEmitter() {
		// Do not clear the editorQuad since it is not accessible to API users.
		return vanillaModelEditorQuad;
	}

	@Override
	public boolean isFaceCulled(@Nullable Direction face) {
		return !blockInfo.shouldDrawFace(face);
	}

	@Override
	public ModelTransformationMode itemTransformationMode() {
		throw new IllegalStateException("itemTransformationMode() can only be called on an item render context.");
	}

	@Override
	public BakedModelConsumer bakedModelConsumer() {
		return vanillaModelConsumer;
	}

	private void renderQuad(MutableQuadViewImpl quad, boolean isVanilla) {
		if (!transform(quad)) {
			return;
		}

		if (isFaceCulled(quad.cullFace())) {
			return;
		}

		final RenderMaterial mat = quad.material();
		final int colorIndex = mat.disableColorIndex() ? -1 : quad.colorIndex();
		final TriState aoMode = mat.ambientOcclusion();
		final boolean ao = blockInfo.useAo && (aoMode == TriState.TRUE || (aoMode == TriState.DEFAULT && blockInfo.defaultAo));
		final boolean emissive = mat.emissive();
		final RenderLayer renderLayer = blockInfo.effectiveRenderLayer(mat.blendMode());
		final Material sodiumMaterial = DefaultMaterials.forRenderLayer(renderLayer);

		colorizeQuad(quad, colorIndex);
		shadeQuad(quad, isVanilla, ao, emissive);
		bufferQuad(quad, sodiumMaterial);
	}

	/** handles block color, common to all renders. */
	private void colorizeQuad(MutableQuadViewImpl quad, int colorIndex) {
		if (colorIndex != -1) {
			final int blockColor = blockInfo.blockColor(colorIndex);

			for (int i = 0; i < 4; i++) {
				quad.color(i, ColorHelper.multiplyColor(blockColor, quad.color(i)));
			}
		}
	}

	protected void shadeQuad(MutableQuadViewImpl quad, boolean isVanilla, boolean ao, boolean emissive) {
		// routines below have a bit of copy-paste code reuse to avoid conditional execution inside a hot loop
		if (ao) {
			aoCalc.compute(quad, isVanilla);

			if (emissive) {
				for (int i = 0; i < 4; i++) {
					quad.color(i, ColorHelper.multiplyRGB(quad.color(i), aoCalc.ao[i]));
					quad.lightmap(i, LightmapTextureManager.MAX_LIGHT_COORDINATE);
				}
			} else {
				for (int i = 0; i < 4; i++) {
					quad.color(i, ColorHelper.multiplyRGB(quad.color(i), aoCalc.ao[i]));
					quad.lightmap(i, ColorHelper.maxBrightness(quad.lightmap(i), aoCalc.light[i]));
				}
			}
		} else {
			shadeFlatQuad(quad, isVanilla);

			if (emissive) {
				for (int i = 0; i < 4; i++) {
					quad.lightmap(i, LightmapTextureManager.MAX_LIGHT_COORDINATE);
				}
			} else {
				final int brightness = flatBrightness(quad);

				for (int i = 0; i < 4; i++) {
					quad.lightmap(i, ColorHelper.maxBrightness(quad.lightmap(i), brightness));
				}
			}
		}
	}

	/**
	 * Starting in 1.16 flat shading uses dimension-specific diffuse factors that can be < 1.0
	 * even for un-shaded quads. These are also applied with AO shading but that is done in AO calculator.
	 */
	private void shadeFlatQuad(MutableQuadViewImpl quad, boolean isVanilla) {
		final boolean hasShade = quad.hasShade();

		// Check the AO mode to match how shade is applied during smooth lighting
		if ((Indium.AMBIENT_OCCLUSION_MODE == AoConfig.HYBRID && !isVanilla) || Indium.AMBIENT_OCCLUSION_MODE == AoConfig.ENHANCED) {
			if (quad.hasAllVertexNormals()) {
				for (int i = 0; i < 4; i++) {
					float shade = normalShade(quad.normalX(i), quad.normalY(i), quad.normalZ(i), hasShade);
					quad.color(i, ColorHelper.multiplyRGB(quad.color(i), shade));
				}
			} else {
				final float faceShade;

				if ((quad.geometryFlags() & AXIS_ALIGNED_FLAG) != 0) {
					faceShade = blockInfo.blockView.getBrightness(quad.lightFace(), hasShade);
				} else {
					Vector3f faceNormal = quad.faceNormal();
					faceShade = normalShade(faceNormal.x, faceNormal.y, faceNormal.z, hasShade);
				}

				if (quad.hasVertexNormals()) {
					for (int i = 0; i < 4; i++) {
						float shade;

						if (quad.hasNormal(i)) {
							shade = normalShade(quad.normalX(i), quad.normalY(i), quad.normalZ(i), hasShade);
						} else {
							shade = faceShade;
						}

						quad.color(i, ColorHelper.multiplyRGB(quad.color(i), shade));
					}
				} else {
					if (faceShade != 1.0f) {
						for (int i = 0; i < 4; i++) {
							quad.color(i, ColorHelper.multiplyRGB(quad.color(i), faceShade));
						}
					}
				}
			}
		} else {
			final float faceShade = blockInfo.blockView.getBrightness(quad.lightFace(), hasShade);

			if (faceShade != 1.0f) {
				for (int i = 0; i < 4; i++) {
					quad.color(i, ColorHelper.multiplyRGB(quad.color(i), faceShade));
				}
			}
		}
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

	/**
	 * Handles geometry-based check for using self brightness or neighbor brightness.
	 * That logic only applies in flat lighting.
	 */
	private int flatBrightness(MutableQuadViewImpl quad) {
		LightDataAccess lightCache = getLightCache();
		BlockPos pos = blockInfo.blockPos;
		Direction cullFace = quad.cullFace();

		// To match vanilla behavior, use the cull face if it exists/is available
		if (cullFace != null) {
			return getOffsetLightmap(lightCache, pos, cullFace);
		} else {
			final int flags = quad.geometryFlags();

			// If the face is aligned, use the light data above it
			// To match vanilla behavior, also treat the face as aligned if it is parallel and the block state is a full cube
			if ((flags & LIGHT_FACE_FLAG) != 0 || ((flags & AXIS_ALIGNED_FLAG) != 0 && LightDataAccess.unpackFC(lightCache.get(pos)))) {
				return getOffsetLightmap(lightCache, pos, quad.lightFace());
			} else {
				return LightDataAccess.getEmissiveLightmap(lightCache.get(pos));
			}
		}
	}

	/**
	 * When vanilla computes an offset lightmap with flat lighting, it passes the original BlockState but the
	 * offset BlockPos to {@link WorldRenderer#getLightmapCoordinates(BlockRenderView, BlockState, BlockPos)}.
	 * This does not make much sense but fixes certain issues, primarily dark quads on light-emitting blocks
	 * behind tinted glass. {@link LightDataAccess} cannot efficiently store lightmaps computed with
	 * inconsistent values so this method exists to mirror vanilla behavior as closely as possible.
	 */
	private static int getOffsetLightmap(LightDataAccess lightCache, BlockPos pos, Direction face) {
		int word = lightCache.get(pos);

		// Check emissivity of the origin state
		if (LightDataAccess.unpackEM(word)) {
			return LightmapTextureManager.MAX_LIGHT_COORDINATE;
		}

		// Use world light values from the offset pos, but luminance from the origin pos
		int adjWord = lightCache.get(pos, face);
		return LightmapTextureManager.pack(Math.max(LightDataAccess.unpackBL(adjWord), LightDataAccess.unpackLU(word)), LightDataAccess.unpackSL(adjWord));
	}

	/**
	 * Consumer for vanilla baked models. Generally intended to give visual results matching a vanilla render,
	 * however there could be subtle (and desirable) lighting variations so is good to be able to render
	 * everything consistently.
	 *
	 * <p>Also, the API allows multi-part models that hold multiple vanilla models to render them without
	 * combining quad lists, but the vanilla logic only handles one model per block. To route all of
	 * them through vanilla logic would require additional hooks.
	 */
	private class BakedModelConsumerImpl implements BakedModelConsumer {
		@Override
		public void accept(BakedModel model) {
			accept(model, blockInfo.blockState);
		}

		@Override
		public void accept(BakedModel model, @Nullable BlockState state) {
			VanillaModelEncoder.emitBlockQuads(model, state, blockInfo.randomSupplier, AbstractBlockRenderContext.this, vanillaModelEditorQuad);
		}
	}
}
