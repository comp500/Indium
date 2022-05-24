package link.infra.indium.renderer;

import io.vram.frex.base.renderer.material.BaseMaterialManager;
import io.vram.frex.base.renderer.material.BaseMaterialView;
import io.vram.frex.base.renderer.material.BaseRenderMaterial;

public class IndiumRenderMaterial extends BaseRenderMaterial {
	public IndiumRenderMaterial(BaseMaterialManager<? extends BaseRenderMaterial> manager, int index, BaseMaterialView template) {
		super(manager, index, template);
	}
}
