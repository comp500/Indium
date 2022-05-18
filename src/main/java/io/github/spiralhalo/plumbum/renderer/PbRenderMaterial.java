package io.github.spiralhalo.plumbum.renderer;

import io.vram.frex.base.renderer.material.BaseMaterialManager;
import io.vram.frex.base.renderer.material.BaseMaterialView;
import io.vram.frex.base.renderer.material.BaseRenderMaterial;

public class PbRenderMaterial extends BaseRenderMaterial {
	public PbRenderMaterial(BaseMaterialManager<? extends BaseRenderMaterial> manager, int index, BaseMaterialView template) {
		super(manager, index, template);
	}
}
