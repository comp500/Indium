package link.infra.indium.other;

import java.util.Collection;
import java.util.List;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.BakedModelManager;
import net.minecraft.resource.ResourceManager;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.Identifier;

import net.fabricmc.fabric.api.resource.ResourceReloadListenerKeys;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;

import io.vram.frex.api.texture.SpriteFinder;

/**
 * Cache SpriteFinders for maximum efficiency.
 *
 * <p><b>This class should not be used during a resource reload</b>, as returned SpriteFinders may be null or outdated.
 */
public class SpriteFinderCache {
	private static SpriteFinder blockAtlasSpriteFinder;

	public static SpriteFinder forBlockAtlas() {
		return blockAtlasSpriteFinder;
	}

	public static class ReloadListener implements SimpleSynchronousResourceReloadListener {
		public static final Identifier ID = new Identifier("indium", "sprite_finder_cache");
		public static final List<Identifier> DEPENDENCIES = List.of(ResourceReloadListenerKeys.MODELS);
		public static final ReloadListener INSTANCE = new ReloadListener();

		private ReloadListener() {
		}

		// BakedModelManager#getAtlas only returns correct results after the BakedModelManager is done reloading
		@Override
		public void reload(ResourceManager manager) {
			BakedModelManager modelManager = MinecraftClient.getInstance().getBakedModelManager();
			blockAtlasSpriteFinder = SpriteFinder.get(modelManager.getAtlas(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE));
		}

		@Override
		public Identifier getFabricId() {
			return ID;
		}

		@Override
		public Collection<Identifier> getFabricDependencies() {
			return DEPENDENCIES;
		}
	}
}
