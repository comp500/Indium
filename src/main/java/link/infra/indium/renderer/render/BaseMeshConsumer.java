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

import java.util.function.Consumer;

import link.infra.indium.renderer.aocalc.AoCalculator;
import link.infra.indium.renderer.mesh.EncodingFormat;
import link.infra.indium.renderer.mesh.MeshImpl;
import link.infra.indium.renderer.mesh.MutableQuadViewImpl;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;

import net.fabricmc.fabric.api.renderer.v1.mesh.Mesh;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext.QuadTransform;
import link.infra.indium.renderer.IndiumRenderer;
import link.infra.indium.renderer.RenderMaterialImpl;

/**
 * Consumer for pre-baked meshes.  Works by copying the mesh data to a
 * "editor" quad held in the instance, where all transformations are applied before buffering.
 */
public class BaseMeshConsumer extends BaseQuadRenderer implements Consumer<Mesh> {
	protected BaseMeshConsumer(QuadBufferer bufferer, BlockRenderInfo blockInfo, AoCalculator aoCalc, QuadTransform transform) {
		super(bufferer, blockInfo, aoCalc, transform);
	}

	/**
	 * Where we handle all pre-buffer coloring, lighting, transformation, etc.
	 * Reused for all mesh quads. Fixed baking array sized to hold largest possible mesh quad.
	 */
	private class Maker extends MutableQuadViewImpl {
		{
			data = new int[EncodingFormat.TOTAL_STRIDE];
			material(IndiumRenderer.MATERIAL_STANDARD);
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
		if (!transform.transform(editorQuad)) {
			return;
		}

		if (!blockInfo.shouldDrawFace(quad.cullFace())) {
			return;
		}

		final RenderMaterialImpl.Value mat = quad.material();

		if (!mat.disableAo(0) && MinecraftClient.isAmbientOcclusionEnabled()) {
			// needs to happen before offsets are applied
			aoCalc.compute(quad, false);
		}

		tessellateQuad(quad, mat, 0);
	}

	/**
	 * Determines color index and render layer, then routes to appropriate
	 * tessellate routine based on material properties.
	 */
	private void tessellateQuad(MutableQuadViewImpl quad, RenderMaterialImpl.Value mat, int textureIndex) {
		final int colorIndex = mat.disableColorIndex(textureIndex) ? -1 : quad.colorIndex();
		final RenderLayer renderLayer = blockInfo.effectiveRenderLayer(mat.blendMode(textureIndex));

		if (blockInfo.defaultAo && !mat.disableAo(textureIndex)) {
			if (mat.emissive(textureIndex)) {
				tessellateSmoothEmissive(quad, renderLayer, colorIndex);
			} else {
				tessellateSmooth(quad, renderLayer, colorIndex);
			}
		} else {
			if (mat.emissive(textureIndex)) {
				tessellateFlatEmissive(quad, renderLayer, colorIndex);
			} else {
				tessellateFlat(quad, renderLayer, colorIndex);
			}
		}
	}
}
