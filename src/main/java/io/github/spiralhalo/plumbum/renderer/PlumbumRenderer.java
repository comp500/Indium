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

package io.github.spiralhalo.plumbum.renderer;

import io.vram.frex.api.material.MaterialFinder;
import io.vram.frex.api.material.RenderMaterial;
import io.vram.frex.api.mesh.MeshBuilder;
import io.vram.frex.api.renderer.*;
import io.github.spiralhalo.plumbum.renderer.mesh.MeshBuilderImpl;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;

public class PlumbumRenderer implements Renderer {
	public static final PlumbumRenderer INSTANCE = new PlumbumRenderer();

	public static final RenderMaterialImpl.Value MATERIAL_STANDARD = (RenderMaterialImpl.Value) INSTANCE.materials().materialFinder().find();

	static {
		INSTANCE.materials().registerMaterial(RenderMaterial.STANDARD_MATERIAL_KEY, MATERIAL_STANDARD);
		INSTANCE.materials().registerMaterial(RenderMaterial.MISSING_MATERIAL_KEY, MATERIAL_STANDARD);
	}

	private final HashMap<Identifier, RenderMaterial> materialMap = new HashMap<>();
	private final MaterialManager materialManager = new PbMaterialManager();

	private PlumbumRenderer() { }

	@Override
	public MeshBuilder meshBuilder() {
		return new MeshBuilderImpl();
	}

	@Override
	public MaterialManager materials() {
		return materialManager;
	}

	@Override
	public ConditionManager conditions() {
		return null;
	}

	@Override
	public MaterialTextureManager textures() {
		return null;
	}

	@Override
	public MaterialShaderManager shaders() {
		return null;
	}

	class PbMaterialManager implements MaterialManager {

		@Override
		public MaterialFinder materialFinder() {
			return new RenderMaterialImpl.Finder();
		}

		@Override
		public @Nullable RenderMaterial materialFromId(Identifier identifier) {
			return materialMap.get(identifier);
		}

		@Override
		public RenderMaterial materialFromIndex(int index) {
			return RenderMaterialImpl.byIndex(index);
		}

		@Override
		public boolean registerMaterial(Identifier identifier, RenderMaterial renderMaterial) {
			if (materialMap.containsKey(identifier)) return false;

			// cast to prevent acceptance of impostor implementations
			materialMap.put(identifier, renderMaterial);
			return true;
		}

		@Override
		public RenderMaterial defaultMaterial() {
			return MATERIAL_STANDARD;
		}

		@Override
		public RenderMaterial missingMaterial() {
			return MATERIAL_STANDARD;
		}
	}
}
