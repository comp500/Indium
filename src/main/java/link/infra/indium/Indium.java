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

package link.infra.indium;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.resource.ResourceType;

import link.infra.indium.other.SpriteFinderCache;
import link.infra.indium.renderer.aocalc.AoConfig;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.util.TriState;
import net.fabricmc.loader.api.FabricLoader;

public class Indium implements ClientModInitializer {
	/** This property isn't currently used since FREX always tessellates all model */
	public static final boolean ALWAYS_TESSELLATE_INDIUM;
	public static final AoConfig AMBIENT_OCCLUSION_MODE;
	/** Set true in dev env to confirm results match vanilla when they should. */
	public static final boolean DEBUG_COMPARE_LIGHTING;
	public static final boolean FIX_SMOOTH_LIGHTING_OFFSET;
	public static final boolean FIX_EXTERIOR_VERTEX_LIGHTING;
	public static final boolean FIX_LUMINOUS_AO_SHADE;

	public static final Logger LOGGER = LogManager.getLogger();

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

		// This property isn't currently used since FREX always tessellates all model
		ALWAYS_TESSELLATE_INDIUM = asBoolean((String) properties.computeIfAbsent("always-tesselate-blocks", (a) -> "auto"), false);
		AMBIENT_OCCLUSION_MODE = asEnum((String) properties.computeIfAbsent("ambient-occlusion-mode", (a) -> "auto"), AoConfig.ENHANCED);
		DEBUG_COMPARE_LIGHTING = asBoolean((String) properties.computeIfAbsent("debug-compare-lighting", (a) -> "auto"), false);
		FIX_SMOOTH_LIGHTING_OFFSET = asBoolean((String) properties.computeIfAbsent("fix-smooth-lighting-offset", (a) -> "auto"), true);
		FIX_EXTERIOR_VERTEX_LIGHTING = asBoolean((String) properties.computeIfAbsent("fix-exterior-vertex-lighting", (a) -> "auto"), true);
		FIX_LUMINOUS_AO_SHADE = asBoolean((String) properties.computeIfAbsent("fix-luminous-block-ambient-occlusion", (a) -> "auto"), false);

		try (OutputStream stream = Files.newOutputStream(configFile)) {
			properties.store(stream, "Indium properties file");
		} catch (IOException e) {
			LOGGER.warn("[Indium] Could not store property file '" + configFile.toAbsolutePath() + "'", e);
		}
	}

	@Override
	public void onInitializeClient() {
		ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(SpriteFinderCache.ReloadListener.INSTANCE);
	}
}
