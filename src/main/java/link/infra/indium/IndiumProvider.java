package link.infra.indium;

import link.infra.indium.renderer.IndiumRenderer;

import io.vram.frex.api.renderer.Renderer;
import io.vram.frex.api.renderer.RendererProvider;

public class IndiumProvider implements RendererProvider {
	@Override
	public Renderer getRenderer() {
		return IndiumRenderer.INSTANCE;
	}
}
