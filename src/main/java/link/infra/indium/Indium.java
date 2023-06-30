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

package link.infra.indium;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import link.infra.indium.other.SpriteFinderCache;
import link.infra.indium.renderer.IndiumRenderer;
import link.infra.indium.renderer.aocalc.AoConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.util.TriState;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.resource.ResourceType;

public class Indium implements ClientModInitializer {
	public static final boolean ALWAYS_TESSELLATE_INDIUM;
	public static final AoConfig AMBIENT_OCCLUSION_MODE;

	public static final Logger LOGGER = LogUtils.getLogger();

	private static boolean asBoolean(String property, boolean defValue) {
		switch (asTriState(property)) {
		case TRUE:
			return true;
		case FALSE:
			return false;
		default:
			return defValue;
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static <T extends Enum> T asEnum(String property, T defValue) {
		if (property == null || property.isEmpty()) {
			return defValue;
		} else {
			for (Enum obj : defValue.getClass().getEnumConstants()) {
				if (property.equalsIgnoreCase(obj.name())) {
					//noinspection unchecked
					return (T) obj;
				}
			}

			return defValue;
		}
	}

	private static TriState asTriState(String property) {
		if (property == null || property.isEmpty()) {
			return TriState.DEFAULT;
		} else {
			switch (property.toLowerCase(Locale.ROOT)) {
			case "true":
				return TriState.TRUE;
			case "false":
				return TriState.FALSE;
			case "auto":
			default:
				return TriState.DEFAULT;
			}
		}
	}

	static {
		Path configFile = FabricLoader.getInstance().getConfigDir().resolve("indium-renderer.properties");
		Properties properties = new Properties();

		if (Files.exists(configFile)) {
			try (InputStream stream = Files.newInputStream(configFile)) {
				properties.load(stream);
			} catch (IOException e) {
				LOGGER.warn("[Indium] Could not read property file '" + configFile.toAbsolutePath() + "'", e);
			}
		}

		ALWAYS_TESSELLATE_INDIUM = asBoolean((String) properties.computeIfAbsent("always-tesselate-blocks", (a) -> "auto"), false);
		AMBIENT_OCCLUSION_MODE = asEnum((String) properties.computeIfAbsent("ambient-occlusion-mode", (a) -> "auto"), AoConfig.ENHANCED);

		try (OutputStream stream = Files.newOutputStream(configFile)) {
			properties.store(stream, "Indium properties file");
		} catch (IOException e) {
			LOGGER.warn("[Indium] Could not store property file '" + configFile.toAbsolutePath() + "'", e);
		}
	}

	@Override
	public void onInitializeClient() {
		try {
			RendererAccess.INSTANCE.registerRenderer(IndiumRenderer.INSTANCE);
		} catch (UnsupportedOperationException e) {
			if (FabricLoader.getInstance().isModLoaded("canvas")) {
				throw new RuntimeException("Failed to load Indium: Indium and Sodium are not compatible with Canvas");
			} else if (FabricLoader.getInstance().isModLoaded("frex")) {
				String msg = "Failed to load Indium: Indium is not currently compatible with FREX 6.x, as it provides an incompatible implementation of the Fabric Rendering API; the FREX API may be directly supported in future.";
				Optional<ModContainer> container = FabricLoader.getInstance().getModContainer("frex");
				if (container.isPresent()) {
					StringBuilder sb = new StringBuilder(msg);
					boolean first = true;
					Optional<ModContainer> parent = container.get().getContainingMod();
					while (parent.isPresent()) {
						sb.append('\n').append(first ? "  FREX" : "  which");
						first = false;
						sb.append(" is bundled as part of ").append(parent.get().getMetadata().getName());

						parent = parent.get().getContainingMod();
					}
					throw new RuntimeException(sb.toString());
				}
				throw new RuntimeException(msg);
			}
			throw e;
		}

		ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(SpriteFinderCache.ReloadListener.INSTANCE);
	}
}
