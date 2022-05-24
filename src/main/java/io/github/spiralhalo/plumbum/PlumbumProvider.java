package io.github.spiralhalo.plumbum;

import io.github.spiralhalo.plumbum.renderer.PlumbumRenderer;

import io.vram.frex.api.renderer.Renderer;
import io.vram.frex.api.renderer.RendererProvider;

public class PlumbumProvider implements RendererProvider {
	@Override
	public Renderer getRenderer() {
		return PlumbumRenderer.INSTANCE;
	}
}
