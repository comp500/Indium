package link.infra.ogidni;

import link.infra.ogidni.renderer.OgidniRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;

public class Ogidni implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		System.out.println("Registering indigo 2 electric boogaloo!");
		RendererAccess.INSTANCE.registerRenderer(OgidniRenderer.INSTANCE);
	}
}
