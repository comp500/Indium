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

import io.vram.frex.api.material.MaterialConstants;
import io.vram.frex.api.material.MaterialFinder;
import io.vram.frex.api.material.MaterialView;
import io.vram.frex.api.material.RenderMaterial;
import net.minecraft.util.math.MathHelper;

/**
 * Default implementation of the standard render materials.
 * The underlying representation is simply an int with bit-wise
 * packing of the various material properties. This offers
 * easy/fast interning via int/object hashmap.
 */
public abstract class RenderMaterialImpl implements MaterialView {
	private static final int[] PRESETS = new int[]{
		MaterialConstants.PRESET_NONE,
		MaterialConstants.PRESET_DEFAULT,
		MaterialConstants.PRESET_SOLID,
		MaterialConstants.PRESET_CUTOUT_MIPPED,
		MaterialConstants.PRESET_CUTOUT,
		MaterialConstants.PRESET_TRANSLUCENT,
	};

	private static final int PRESET_MASK = MathHelper.smallestEncompassingPowerOfTwo(PRESETS.length) - 1;
	private static final int COLOR_DISABLE_FLAG = PRESET_MASK + 1;
	private static final int EMISSIVE_FLAG = COLOR_DISABLE_FLAG << 1;
	private static final int DIFFUSE_FLAG = EMISSIVE_FLAG << 1;
	private static final int AO_FLAG = DIFFUSE_FLAG << 1;
	public static final int VALUE_COUNT = (AO_FLAG << 1);

	private static final Value[] VALUES = new Value[VALUE_COUNT];

	static {
		for (int i = 0; i < VALUE_COUNT; i++) {
			VALUES[i] = new Value(i);
		}
	}

	public static RenderMaterialImpl.Value byIndex(int index) {
		Value result = VALUES[index];
		return result;
	}

	protected int bits;

	@Override
	public int preset() {
		return PRESETS[bits & PRESET_MASK];
	}

	@Override
	public boolean disableColorIndex() {
		return (bits & COLOR_DISABLE_FLAG) != 0;
	}

	@Override
	public boolean emissive() {
		return (bits & EMISSIVE_FLAG) != 0;
	}

	@Override
	public boolean disableDiffuse() {
		return (bits & DIFFUSE_FLAG) != 0;
	}

	@Override
	public boolean disableAo() {
		return (bits & AO_FLAG) != 0;
	}

	/* CURRENTLY UNSUPPORTED */

	@Override
	public boolean blur() {
		return false;
	}

	@Override
	public int conditionIndex() {
		return 0;
	}

	@Override
	public boolean cull() {
		return false;
	}

	@Override
	public int cutout() {
		return 0;
	}

	@Override
	public int decal() {
		return 0;
	}

	@Override
	public int depthTest() {
		return 0;
	}

	@Override
	public boolean discardsTexture() {
		return false;
	}

	@Override
	public boolean unlit() {
		return false;
	}

	@Override
	public boolean flashOverlay() {
		return false;
	}

	@Override
	public boolean fog() {
		return false;
	}

	@Override
	public boolean hurtOverlay() {
		return false;
	}

	@Override
	public boolean lines() {
		return false;
	}

	@Override
	public boolean sorted() {
		return false;
	}

	@Override
	public int target() {
		return 0;
	}

	@Override
	public int textureIndex() {
		return 0;
	}

	@Override
	public int transparency() {
		return 0;
	}

	@Override
	public boolean unmipped() {
		return false;
	}

	@Override
	public int shaderIndex() {
		return 0;
	}

	@Override
	public int writeMask() {
		return 0;
	}

	@Override
	public String label() {
		return null;
	}

	@Override
	public boolean castShadows() {
		return false;
	}

	@Override
	public boolean foilOverlay() {
		return false;
	}

	public static class Value extends RenderMaterialImpl implements RenderMaterial {
		private Value(int bits) {
			this.bits = bits;
		}

		public int index() {
			return bits;
		}
	}

	public static class Finder extends RenderMaterialImpl implements MaterialFinder {
		@Override
		public RenderMaterial find() {
			return VALUES[bits];
		}

		@Override
		public MaterialFinder clear() {
			bits = 0;
			return this;
		}

		@Override
		public MaterialFinder preset(int preset) {
			bits = (bits & ~PRESET_MASK) | preset;
			return this;
		}

		@Override
		public MaterialFinder disableColorIndex(boolean disable) {
			bits = disable ? (bits | COLOR_DISABLE_FLAG) : (bits & ~COLOR_DISABLE_FLAG);
			return this;
		}

		@Override
		public MaterialFinder emissive(boolean isEmissive) {
			bits = isEmissive ? (bits | EMISSIVE_FLAG) : (bits & ~EMISSIVE_FLAG);
			return this;
		}

		@Override
		public MaterialFinder disableDiffuse(boolean disable) {
			bits = disable ? (bits | DIFFUSE_FLAG) : (bits & ~DIFFUSE_FLAG);
			return this;
		}

		@Override
		public MaterialFinder disableAo(boolean disable) {
			bits = disable ? (bits | AO_FLAG) : (bits & ~AO_FLAG);
			return this;
		}

		@Override
		public MaterialFinder copyFrom(RenderMaterial material) {
			preset(material.preset());
			disableColorIndex(material.disableColorIndex());
			emissive(material.emissive());
			disableDiffuse(material.disableDiffuse());
			disableAo(material.disableAo());
			return this;
		}

		/** CURRENTLY UNSUPPORTED **/

		@Override
		public MaterialFinder blur(boolean blur) {
			return this;
		}

		@Override
		public MaterialFinder conditionIndex(int index) {
			return this;
		}

		@Override
		public MaterialFinder cull(boolean cull) {
			return this;
		}

		@Override
		public MaterialFinder cutout(int cutout) {
			return this;
		}

		@Override
		public MaterialFinder decal(int decal) {
			return this;
		}

		@Override
		public MaterialFinder depthTest(int depthTest) {
			return this;
		}

		@Override
		public MaterialFinder discardsTexture(boolean discardsTexture) {
			return this;
		}

		@Override
		public MaterialFinder flashOverlay(boolean flashOverlay) {
			return this;
		}

		@Override
		public MaterialFinder foilOverlay(boolean foilOverlay) {
			return this;
		}

		@Override
		public MaterialFinder fog(boolean enable) {
			return this;
		}

		@Override
		public MaterialFinder hurtOverlay(boolean hurtOverlay) {
			return this;
		}

		@Override
		public MaterialFinder lines(boolean lines) {
			return this;
		}

		@Override
		public MaterialFinder shaderIndex(int shaderIndex) {
			return this;
		}

		@Override
		public MaterialFinder sorted(boolean sorted) {
			return this;
		}

		@Override
		public MaterialFinder target(int target) {
			return this;
		}

		@Override
		public MaterialFinder textureIndex(int textureIndex) {
			return this;
		}

		@Override
		public MaterialFinder transparency(int transparency) {
			return this;
		}

		@Override
		public MaterialFinder unmipped(boolean unmipped) {
			return this;
		}

		@Override
		public MaterialFinder writeMask(int writeMask) {
			return this;
		}

		@Override
		public MaterialFinder castShadows(boolean castShadows) {
			return this;
		}

		@Override
		public MaterialFinder label(String name) {
			return this;
		}

		@Override
		public MaterialFinder unlit(boolean unlit) {
			return this;
		}
	}
}
