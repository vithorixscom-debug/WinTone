/*
 * This file is part of Winton, a fork of Baritone (https://github.com/cabaletta/baritone).
 * Copyright (c) WinTone contributors.
 * This file was written for WinTone and is not an upstream Baritone source file.
 *
 * Winton is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Winton is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Winton.  If not, see <https://www.gnu.org/licenses/>.
 */

package winton.utils;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Block display names in English and Russian for chat messages.
 */
public final class BlockNameUtil {

	private static final Gson GSON = new Gson();
	private static Map<String, String> EN = Collections.emptyMap();
	private static Map<String, String> RU = Collections.emptyMap();
	private static boolean loaded;

	private BlockNameUtil() {
	}

	/** e.g. {@code Oak Planks / Дубовые доски} */
	public static String enRu(BlockState state) {
		if (state == null) {
			return "?";
		}
		return enRu(state.getBlock());
	}

	public static String enRu(Block block) {
		if (block == null) {
			return "?";
		}
		ensureLoaded();
		String key = block.getDescriptionId();
		String en = lookup(EN, key);
		String ru = lookup(RU, key);
		if (en == null || en.isEmpty() || en.equals(key)) {
			en = block.getName().getString();
		}
		if (ru == null || ru.isEmpty() || ru.equals(key)) {
			ru = en;
		}
		String id = String.valueOf(BuiltInRegistries.BLOCK.getKey(block));
		if (en.equalsIgnoreCase(ru)) {
			return en + " [" + id + "]";
		}
		return en + " / " + ru;
	}

	private static String lookup(Map<String, String> map, String key) {
		String v = map.get(key);
		if (v != null) {
			return v;
		}
		// item.minecraft.x sometimes mirrors block.minecraft.x
		if (key.startsWith("block.")) {
			return map.get("item." + key.substring("block.".length()));
		}
		return null;
	}

	private static void ensureLoaded() {
		if (loaded) {
			return;
		}
		loaded = true;
		EN = loadLang("en_us");
		RU = loadLang("ru_ru");
	}

	private static Map<String, String> loadLang(String code) {
		Map<String, String> out = new HashMap<>();
		try {
			Minecraft mc = Minecraft.getInstance();
			if (mc != null && mc.getResourceManager() != null) {
				Identifier loc = Identifier.withDefaultNamespace("lang/" + code + ".json");
				Optional<Resource> res = mc.getResourceManager().getResource(loc);
				if (res.isPresent()) {
					try (Reader reader = res.get().openAsReader()) {
						Map<String, String> parsed = GSON.fromJson(reader, new TypeToken<Map<String, String>>() {}.getType());
						if (parsed != null) {
							out.putAll(parsed);
						}
					}
				}
			}
		} catch (Throwable ignored) {
		}
		if (out.isEmpty()) {
			try (InputStream in = BlockNameUtil.class.getResourceAsStream("/assets/minecraft/lang/" + code + ".json")) {
				if (in != null) {
					try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
						Map<String, String> parsed = GSON.fromJson(reader, new TypeToken<Map<String, String>>() {}.getType());
						if (parsed != null) {
							out.putAll(parsed);
						}
					}
				}
			} catch (Throwable ignored) {
			}
		}
		return out;
	}
}
