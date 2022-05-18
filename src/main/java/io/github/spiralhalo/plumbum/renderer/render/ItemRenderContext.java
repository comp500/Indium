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

import io.vram.frex.api.buffer.QuadEmitter;
import io.vram.frex.api.material.MaterialConstants;
import io.vram.frex.api.math.FastMatrix3f;
import io.vram.frex.api.mesh.Mesh;
import io.vram.frex.api.model.BlockItemModel;
import io.vram.frex.api.model.ItemModel;
import io.github.spiralhalo.plumbum.renderer.RenderMaterialImpl;
import io.github.spiralhalo.plumbum.renderer.helper.ColorHelper;
import io.github.spiralhalo.plumbum.renderer.mesh.EncodingFormat;
import io.github.spiralhalo.plumbum.renderer.mesh.MeshImpl;
import io.github.spiralhalo.plumbum.renderer.mesh.MutableQuadViewImpl;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.color.item.ItemColors;
import net.minecraft.client.render.*;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformation.Mode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Random;
import java.util.function.Consumer;

/**
 * The render context used for item rendering.
 */
public class ItemRenderContext extends MatrixRenderContext implements ItemModel.ItemInputContext {
	/** Value vanilla uses for item rendering.  The only sensible choice, of course.  */
	private static final long ITEM_RANDOM_SEED = 42L;

	@Override
	public Random random() {
		random.setSeed(ITEM_RANDOM_SEED);
		return random;
	}

	@Override
	public boolean isGui() {
		return transformMode.equals(Mode.GUI);
	}

	@Override
	public boolean isFrontLit() {
		return transformMode.equals(Mode.GUI); // ?
	}

	@Override
	public boolean isBlockItem() {
		return itemStack.getItem() instanceof BlockItem;
	}

	@Override
	public boolean drawTranslucencyToMainTarget() {
		return false;
	}

	@Override
	public boolean isLeftHand() {
		return transformMode.equals(Mode.FIRST_PERSON_LEFT_HAND);
	}

	@Override
	public int lightmap() {
		return lightmap;
	}

	@Override
	public Type type() {
		return Type.ITEM;
	}

	@Override
	public ItemStack itemStack() {
		return itemStack;
	}

	@Override
	public Mode mode() {
		return transformMode;
	}

	@Override
	public int indexedColor(int colorIndex) {
		return colorMap.getColor(itemStack, colorIndex);
	}

	@Override
	public RenderLayer defaultRenderType() {
		return isDefaultTranslucent ? RenderLayer.getTranslucent() : RenderLayer.getSolid();
	}

	@Override
	public int defaultPreset() {
		return isDefaultTranslucent ? MaterialConstants.PRESET_TRANSLUCENT : MaterialConstants.PRESET_DEFAULT;
	}

	@Override
	public boolean cullTest(int faceId) {
		return true;
	}

	@Override
	public @Nullable BakedModel bakedModel() {
		return model;
	}

	/** used to accept a method reference from the ItemRenderer. */
	@FunctionalInterface
	public interface VanillaQuadHandler {
		void accept(BakedModel model, ItemStack stack, int color, int overlay, MatrixStack matrixStack, VertexConsumer buffer);
	}

	private final ItemColors colorMap;
	private final Random random = new Random();
	private int packedNormalFrex;

	private final Maker editorQuad = new Maker();
	private final MeshConsumer meshConsumer = new MeshConsumer();
	private final FallbackConsumer fallbackConsumer = new FallbackConsumer();

	private ItemStack itemStack;
	private Mode transformMode;
	private VertexConsumerProvider vertexConsumerProvider;
	private int lightmap;
	private VanillaQuadHandler vanillaHandler;

	private boolean isDefaultTranslucent;
	private boolean isTranslucentDirect;
	private VertexConsumer translucentVertexConsumer;
	private VertexConsumer cutoutVertexConsumer;
	private VertexConsumer modelVertexConsumer;

	private BakedModel model;

	public ItemRenderContext(ItemColors colorMap) {
		this.colorMap = colorMap;
	}

	public void renderModel(ItemStack itemStack, Mode transformMode, boolean invert, MatrixStack matrixStack, VertexConsumerProvider vertexConsumerProvider, int lightmap, int overlay, BakedModel model, VanillaQuadHandler vanillaHandler) {
		this.itemStack = itemStack;
		this.transformMode = transformMode;
		this.matrixStack = matrixStack;
		this.vertexConsumerProvider = vertexConsumerProvider;
		this.lightmap = lightmap;
		this.overlay = overlay;
		this.vanillaHandler = vanillaHandler;
		this.model = model;
		computeOutputInfo();

		matrixStack.push();
		model.getTransformation().getTransformation(transformMode).apply(invert, matrixStack);
		matrixStack.translate(-0.5D, -0.5D, -0.5D);
		matrix = matrixStack.peek().getPositionMatrix();
		normalMatrix = (FastMatrix3f) (Object) matrixStack.peek().getNormalMatrix();

		((BlockItemModel) model).renderAsItem(this, getEmitter());

		matrixStack.pop();

		this.itemStack = null;
		this.matrixStack = null;
		this.vanillaHandler = null;
		translucentVertexConsumer = null;
		cutoutVertexConsumer = null;
		modelVertexConsumer = null;
		this.model = null;
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

			if (transformMode != Mode.GUI && !transformMode.isFirstPerson()) {
				isTranslucentDirect = false;
			}
		}

		modelVertexConsumer = quadVertexConsumer(MaterialConstants.PRESET_DEFAULT);
	}

	/**
	 * Caches custom blend mode / vertex consumers and mimics the logic
	 * in {@code RenderLayers.getEntityBlockLayer}. Layers other than
	 * translucent are mapped to cutout.
	 */
	private VertexConsumer quadVertexConsumer(int preset) {
		boolean translucent;

		if (preset == MaterialConstants.PRESET_DEFAULT) {
			translucent = isDefaultTranslucent;
		} else {
			translucent = preset == MaterialConstants.PRESET_TRANSLUCENT;
		}

		if (translucent) {
			if (translucentVertexConsumer == null) {
				if (isTranslucentDirect) {
					translucentVertexConsumer = ItemRenderer.getDirectItemGlintConsumer(vertexConsumerProvider, TexturedRenderLayers.getEntityTranslucentCull(), true, itemStack.hasGlint());
				} else if (MinecraftClient.isFabulousGraphicsOrBetter()) {
					translucentVertexConsumer = ItemRenderer.getItemGlintConsumer(vertexConsumerProvider, TexturedRenderLayers.getItemEntityTranslucentCull(), true, itemStack.hasGlint());
				} else {
					translucentVertexConsumer = ItemRenderer.getItemGlintConsumer(vertexConsumerProvider, TexturedRenderLayers.getEntityTranslucentCull(), true, itemStack.hasGlint());
				}
			}

			return translucentVertexConsumer;
		} else {
			if (cutoutVertexConsumer == null) {
				cutoutVertexConsumer = ItemRenderer.getDirectItemGlintConsumer(vertexConsumerProvider, TexturedRenderLayers.getEntityCutout(), true, itemStack.hasGlint());
			}

			return cutoutVertexConsumer;
		}
	}

	private void bufferQuad(MutableQuadViewImpl quad, int preset) {
		VertexConsumerQuadBufferer.bufferQuad(quadVertexConsumer(preset), quad, matrix, overlay, normalMatrix, packedNormalFrex);
	}

	private void colorizeQuad(MutableQuadViewImpl q, int colorIndex) {
		if (colorIndex == -1) {
			for (int i = 0; i < 4; i++) {
				q.vertexColor(i, ColorHelper.swapRedBlueIfNeeded(q.vertexColor(i)));
			}
		} else {
			final int itemColor = 0xFF000000 | colorMap.getColor(itemStack, colorIndex);

			for (int i = 0; i < 4; i++) {
				q.vertexColor(i, ColorHelper.swapRedBlueIfNeeded(ColorHelper.multiplyColor(itemColor, q.vertexColor(i))));
			}
		}
	}

	private void renderQuad(MutableQuadViewImpl quad, int preset, int colorIndex) {
		colorizeQuad(quad, colorIndex);

		final int lightmap = this.lightmap;

		for (int i = 0; i < 4; i++) {
			quad.lightmap(i, ColorHelper.maxBrightness(quad.lightmap(i), lightmap));
		}

		bufferQuad(quad, preset);
	}

	private void renderQuadEmissive(MutableQuadViewImpl quad, int preset, int colorIndex) {
		colorizeQuad(quad, colorIndex);

		for (int i = 0; i < 4; i++) {
			quad.lightmap(i, LightmapTextureManager.MAX_LIGHT_COORDINATE);
		}

		bufferQuad(quad, preset);
	}

	private void renderMeshQuad(MutableQuadViewImpl quad) {
		final RenderMaterialImpl.Value mat = quad.material();

		final int colorIndex = mat.disableColorIndex() ? -1 : quad.colorIndex();
		final int preset = mat.preset();

		if (mat.emissive()) {
			renderQuadEmissive(quad, preset, colorIndex);
		} else {
			renderQuad(quad, preset, colorIndex);
		}
	}

	private class Maker extends MutableQuadViewImpl implements QuadEmitter {
		{
			data = new int[EncodingFormat.TOTAL_STRIDE];
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

	private class MeshConsumer implements Consumer<Mesh> {
		@Override
		public void accept(Mesh mesh) {
			final MeshImpl m = (MeshImpl) mesh;
			final int[] data = m.data();
			final int limit = data.length;
			int index = 0;

			while (index < limit) {
				System.arraycopy(data, index, editorQuad.data(), 0, EncodingFormat.TOTAL_STRIDE);
				editorQuad.load();
				index += EncodingFormat.TOTAL_STRIDE;
				renderMeshQuad(editorQuad);
			}
		}
	}

	private class FallbackConsumer implements Consumer<BakedModel> {
		@Override
		public void accept(BakedModel model) {
			vanillaHandler.accept(model, itemStack, lightmap, overlay, matrixStack, modelVertexConsumer);
		}
	}

//	@Override
//	public Consumer<Mesh> meshConsumer() {
//		return meshConsumer;
//	}
//
//	@Override
//	public Consumer<BakedModel> fallbackConsumer() {
//		return fallbackConsumer;
//	}

	public QuadEmitter getEmitter() {
		editorQuad.clear();
		return editorQuad;
	}
}
