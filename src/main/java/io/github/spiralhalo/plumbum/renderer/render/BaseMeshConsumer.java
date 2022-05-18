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
import io.vram.frex.api.mesh.Mesh;
import io.github.spiralhalo.plumbum.renderer.PlumbumRenderer;
import io.github.spiralhalo.plumbum.renderer.RenderMaterialImpl;
import io.github.spiralhalo.plumbum.renderer.aocalc.AoCalculator;
import io.github.spiralhalo.plumbum.renderer.mesh.EncodingFormat;
import io.github.spiralhalo.plumbum.renderer.mesh.MeshImpl;
import io.github.spiralhalo.plumbum.renderer.mesh.MutableQuadViewImpl;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;

import java.util.function.Consumer;

/**
 * Consumer for pre-baked meshes.  Works by copying the mesh data to an
 * "editor" quad held in the instance, where all transformations are applied before buffering.
 */
public class BaseMeshConsumer extends BaseQuadRenderer implements Consumer<Mesh> {
	protected BaseMeshConsumer(QuadBufferer bufferer, BlockRenderInfo blockInfo, AoCalculator aoCalc) {
		super(bufferer, blockInfo, aoCalc);
	}

	/**
	 * Where we handle all pre-buffer coloring, lighting, transformation, etc.
	 * Reused for all mesh quads. Fixed baking array sized to hold largest possible mesh quad.
	 */
	private class Maker extends MutableQuadViewImpl {
		{
			data = new int[EncodingFormat.TOTAL_STRIDE];
			material(PlumbumRenderer.MATERIAL_STANDARD);
		}

		// only used via RenderContext.getEmitter()
		@Override
		public Maker emit() {
			computeGeometry();
			renderQuad(this);
			clear();
			return this;
		}
	}

	private final Maker editorQuad = new Maker();

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
			renderQuad(editorQuad);
		}
	}

	public QuadEmitter getEmitter() {
		editorQuad.clear();
		return editorQuad;
	}

	private void renderQuad(MutableQuadViewImpl quad) {

		if (!blockInfo.shouldDrawFace(quad.cullFace())) {
			return;
		}

		final RenderMaterialImpl.Value mat = quad.material();

		if (!mat.disableAo() && MinecraftClient.isAmbientOcclusionEnabled()) {
			// needs to happen before offsets are applied
			aoCalc.compute(quad, false);
		}

		tessellateQuad(quad, mat);
	}

	/**
	 * Determines color index and render layer, then routes to appropriate
	 * tessellate routine based on material properties.
	 */
	private void tessellateQuad(MutableQuadViewImpl quad, RenderMaterialImpl.Value mat) {
		final int colorIndex = mat.disableColorIndex() ? -1 : quad.colorIndex();
		final RenderLayer renderLayer = blockInfo.effectiveRenderLayer(mat.preset());

		if (blockInfo.defaultAo && !mat.disableAo()) {
			if (mat.emissive()) {
				tessellateSmoothEmissive(quad, renderLayer, colorIndex);
			} else {
				tessellateSmooth(quad, renderLayer, colorIndex);
			}
		} else {
			if (mat.emissive()) {
				tessellateFlatEmissive(quad, renderLayer, colorIndex);
			} else {
				tessellateFlat(quad, renderLayer, colorIndex);
			}
		}
	}
}
