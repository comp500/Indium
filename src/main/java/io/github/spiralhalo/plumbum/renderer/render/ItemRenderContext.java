/*
 * Copyright (c) 2016-2022 Contributors
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

package io.github.spiralhalo.plumbum.renderer.render;

import io.github.spiralhalo.plumbum.renderer.accessor.AccessItemRenderer;
import io.github.spiralhalo.plumbum.renderer.helper.ColorHelper;
import io.github.spiralhalo.plumbum.renderer.mesh.QuadEmitterImpl;
import io.vram.frex.api.buffer.QuadEmitter;
import io.vram.frex.api.material.MaterialConstants;
import io.vram.frex.api.material.MaterialMap;
import io.vram.frex.api.material.RenderMaterial;
import io.vram.frex.api.math.FastMatrix3f;
import io.vram.frex.api.math.MatrixStack;
import io.vram.frex.api.model.ItemModel;
import io.vram.frex.base.renderer.context.input.BaseItemInputContext;
import io.vram.frex.base.renderer.mesh.MeshEncodingHelper;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.color.item.ItemColors;
import net.minecraft.client.render.*;
import net.minecraft.client.render.item.ItemModels;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformation.Mode;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.Matrix4f;

/**
 * The render context used for item rendering.
 */
public class ItemRenderContext extends BaseItemInputContext {
	protected Matrix4f matrix;
	protected FastMatrix3f normalMatrix;
	private final ItemColors rendererColorMap;
	protected MaterialMap materialMap = MaterialMap.defaultMaterialMap();

	private final Maker editorQuad = new Maker();

	private VertexConsumerProvider vertexConsumerProvider;

	private boolean isDefaultTranslucent;
	private boolean isTranslucentDirect;
	private VertexConsumer translucentVertexConsumer;
	private VertexConsumer cutoutVertexConsumer;

	public ItemRenderContext(ItemColors rendererColorMap) {
		this.rendererColorMap = rendererColorMap;
	}

	public void renderModel(ItemModels models, ItemStack itemStack, Mode renderMode, boolean invert, MatrixStack matrixStack, VertexConsumerProvider vertexConsumerProvider, int lightmap, int overlay, BakedModel model) {
		// Prepare
		this.vertexConsumerProvider = vertexConsumerProvider;
		final boolean itemIsLeftHand = renderMode.equals(Mode.FIRST_PERSON_LEFT_HAND) || renderMode.equals(Mode.THIRD_PERSON_LEFT_HAND);
		final net.minecraft.client.util.math.MatrixStack vanillaStack = matrixStack.toVanilla();
		prepareForItem(model, itemStack, renderMode, lightmap, overlay, itemIsLeftHand, matrixStack);
		computeOutputInfo();
		materialMap = MaterialMap.get(itemStack);
		final var itemTransforms = model.getTransformation();

		// Magic
		final boolean detachedPerspective = renderMode == Mode.GUI || renderMode == Mode.GROUND || renderMode == Mode.FIXED;

		if (detachedPerspective) {
			if (itemStack.isOf(Items.TRIDENT)) {
				model = models.getModel(Items.TRIDENT);
			} else if (itemStack.isOf(Items.SPYGLASS)) {
				model = models.getModel(Items.SPYGLASS);
			}
		}

		// Transform
		if (itemTransforms != null) {
			matrixStack.push();
			itemTransforms.getTransformation(renderMode).apply(itemIsLeftHand, vanillaStack);
			matrixStack.translate(-0.5f, -0.5f, -0.5f);
		}

		matrix = (Matrix4f) (Object) matrixStack.modelMatrix();
		normalMatrix = matrixStack.normalMatrix();

		// Render
		if (model.isBuiltin() || itemStack.getItem() == Items.TRIDENT && !detachedPerspective) {
			((AccessItemRenderer)MinecraftClient.getInstance().getItemRenderer()).plumbum_builtInRenderer().render(itemStack, renderMode, vanillaStack, vertexConsumerProvider, lightmap, overlay);
		} else {
			((ItemModel) model).renderAsItem(this, getEmitter());
		}

		// Closing
		if (itemTransforms != null) {
			matrixStack.pop();
		}

		this.itemStack = null;
		this.matrixStack = null;
		this.bakedModel = null;
		translucentVertexConsumer = null;
		cutoutVertexConsumer = null;
	}

	@Override
	public int indexedColor(int colorIndex) {
		return colorIndex == -1 ? -1 : (rendererColorMap.getColor(itemStack, colorIndex) | 0xFF000000);
	}

	private void computeOutputInfo() {
		isDefaultTranslucent = true;
		isTranslucentDirect = true;

		Item item = itemStack.getItem();

		if (item instanceof BlockItem blockItem) {
			BlockState state = blockItem.getBlock().getDefaultState();
			RenderLayer renderLayer = RenderLayers.getBlockLayer(state);

			if (renderLayer != RenderLayer.getTranslucent()) {
				isDefaultTranslucent = false;
			}

			if (renderMode != Mode.GUI && !renderMode.isFirstPerson()) {
				isTranslucentDirect = false;
			}
		}
	}

	/**
	 * Caches custom blend mode / vertex consumers and mimics the logic
	 * in {@code RenderLayers.getEntityBlockLayer}. Layers other than
	 * translucent are mapped to cutout.
	 */
	private VertexConsumer quadVertexConsumer(RenderMaterial mat) {
		boolean translucent;

		int preset = mat.preset();

		if (preset == MaterialConstants.PRESET_DEFAULT) {
			translucent = isDefaultTranslucent;
		} else if (preset == MaterialConstants.PRESET_NONE) {
			translucent = mat.target() != MaterialConstants.TARGET_MAIN;
		} else {
			translucent = preset == MaterialConstants.PRESET_TRANSLUCENT;
		}

		if (translucent) {
			if (translucentVertexConsumer == null) {
				if (isTranslucentDirect) {
					translucentVertexConsumer = ItemRenderer.getDirectItemGlintConsumer(vertexConsumerProvider, TexturedRenderLayers.getEntityTranslucentCull(), true, mat.foilOverlay());
				} else if (MinecraftClient.isFabulousGraphicsOrBetter()) {
					translucentVertexConsumer = ItemRenderer.getItemGlintConsumer(vertexConsumerProvider, TexturedRenderLayers.getItemEntityTranslucentCull(), true, mat.foilOverlay());
				} else {
					translucentVertexConsumer = ItemRenderer.getItemGlintConsumer(vertexConsumerProvider, TexturedRenderLayers.getEntityTranslucentCull(), true, mat.foilOverlay());
				}
			}

			return translucentVertexConsumer;
		} else {
			if (cutoutVertexConsumer == null) {
				cutoutVertexConsumer = ItemRenderer.getDirectItemGlintConsumer(vertexConsumerProvider, TexturedRenderLayers.getEntityCutout(), true, mat.foilOverlay());
			}

			return cutoutVertexConsumer;
		}
	}

	private void bufferQuad(QuadEmitterImpl quad) {
		VertexConsumerQuadBufferer.bufferQuad(quadVertexConsumer(quad.material()), quad, matrix, overlay, normalMatrix);
	}

	private void colorizeQuad(QuadEmitterImpl q, int colorIndex) {
		if (colorIndex == -1) {
			for (int i = 0; i < 4; i++) {
				q.vertexColor(i, ColorHelper.swapRedBlueIfNeeded(q.vertexColor(i)));
			}
		} else {
			final int itemColor = 0xFF000000 | indexedColor(colorIndex);

			for (int i = 0; i < 4; i++) {
				q.vertexColor(i, ColorHelper.swapRedBlueIfNeeded(ColorHelper.multiplyColor(itemColor, q.vertexColor(i))));
			}
		}
	}

	private void renderQuad(QuadEmitterImpl quad, int colorIndex) {
		colorizeQuad(quad, colorIndex);

		final int lightmap = this.lightmap;

		for (int i = 0; i < 4; i++) {
			quad.lightmap(i, ColorHelper.maxBrightness(quad.lightmap(i), lightmap));
		}

		bufferQuad(quad);
	}

	private void renderQuadEmissive(QuadEmitterImpl quad, int colorIndex) {
		colorizeQuad(quad, colorIndex);

		for (int i = 0; i < 4; i++) {
			quad.lightmap(i, LightmapTextureManager.MAX_LIGHT_COORDINATE);
		}

		bufferQuad(quad);
	}

	private void renderMeshQuad(QuadEmitterImpl quad) {
		final RenderMaterial mat = quad.material();
		final int colorIndex = mat.disableColorIndex() ? -1 : quad.colorIndex();

		if (mat.emissive()) {
			renderQuadEmissive(quad, colorIndex);
		} else {
			renderQuad(quad, colorIndex);
		}
	}

	private class Maker extends QuadEmitterImpl implements QuadEmitter {
		{
			data = new int[MeshEncodingHelper.TOTAL_MESH_QUAD_STRIDE];
			clear();
		}

		@Override
		public Maker emit() {
			computeGeometry();
			renderMeshQuad(this);
			clear();
			return this;
		}
	}

	public QuadEmitter getEmitter() {
		editorQuad.clear();
		return editorQuad;
	}
}
