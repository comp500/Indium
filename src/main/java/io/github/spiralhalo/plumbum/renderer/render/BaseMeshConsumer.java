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

import io.github.spiralhalo.plumbum.renderer.aocalc.AoCalculator;
import io.github.spiralhalo.plumbum.renderer.mesh.QuadEmitterImpl;
import io.vram.frex.api.buffer.QuadEmitter;
import io.vram.frex.api.material.RenderMaterial;
import io.vram.frex.api.renderer.Renderer;
import io.vram.frex.base.renderer.mesh.MeshEncodingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;

/**
 * Consumer for pre-baked meshes.  Works by copying the mesh data to an
 * "editor" quad held in the instance, where all transformations are applied before buffering.
 */
public class BaseMeshConsumer extends BaseQuadRenderer {
	protected BaseMeshConsumer(QuadBufferer bufferer, BlockRenderInfo blockInfo, AoCalculator aoCalc) {
		super(bufferer, blockInfo, aoCalc);
	}

	/**
	 * Where we handle all pre-buffer coloring, lighting, transformation, etc.
	 * Reused for all mesh quads. Fixed baking array sized to hold largest possible mesh quad.
	 */
	private class Maker extends QuadEmitterImpl {
		{
			data = new int[MeshEncodingHelper.TOTAL_MESH_QUAD_STRIDE];
			material(Renderer.get().materials().defaultMaterial());
		}

		// only used via RenderContext.getEmitter()
		@Override
		public Maker emit() {
			complete();
			renderQuad(this);
			clear();
			return this;
		}
	}

	private final Maker editorQuad = new Maker();

	public QuadEmitter getEmitter() {
		editorQuad.clear();
		return editorQuad;
	}

	private void renderQuad(QuadEmitterImpl quad) {
		if (!blockInfo.cullTest(quad.cullFaceId())) {
			return;
		}

		quad.mapMaterial(blockInfo.materialMap);
		final RenderMaterial mat = quad.material();

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
	private void tessellateQuad(QuadEmitterImpl quad, RenderMaterial mat) {
		final int colorIndex = mat.disableColorIndex() ? -1 : quad.colorIndex();
		final RenderLayer renderLayer = blockInfo.effectiveRenderLayer(mat);

		if (blockInfo.defaultAo && !mat.disableAo()) {
			if (mat.emissive() || mat.unlit()) {
				tessellateSmoothUnlit(quad, renderLayer, colorIndex);
			} else {
				tessellateSmooth(quad, renderLayer, colorIndex);
			}
		} else {
			if (mat.emissive() || mat.unlit()) {
				tessellateFlatUnlit(quad, renderLayer, colorIndex);
			} else {
				tessellateFlat(quad, renderLayer, colorIndex);
			}
		}
	}
}
