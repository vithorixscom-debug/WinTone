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
 * Based on Baritone (https://github.com/cabaletta/baritone), LGPL-3.0.
 */

package winton.menu;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Menu-only i18n (independent of Minecraft language). Default: English. */
public final class WintonLang {
	public enum Code {
		EN("en", "English", "en_us"),
		RU("ru", "Русский", "ru_ru"),
		ZH("zh", "中文", "zh_cn"),
		DE("de", "Deutsch", "de_de"),
		FR("fr", "Français", "fr_fr");

		public final String id;
		public final String label;
		public final String asset;

		Code(String id, String label, String asset) {
			this.id = id;
			this.label = label;
			this.asset = asset;
		}

		public static Code fromId(String raw) {
			if (raw == null || raw.isBlank()) {
				return EN;
			}
			String s = raw.trim().toLowerCase(Locale.ROOT);
			for (Code c : values()) {
				if (c.id.equals(s) || c.asset.equals(s) || c.name().equalsIgnoreCase(s)) {
					return c;
				}
			}
			return EN;
		}
	}

	private static Code current = Code.EN;
	private static Map<String, String> strings = Collections.emptyMap();
	private static Map<String, String> fallback = Collections.emptyMap();

	static {
		fallback = loadAsset(Code.EN.asset);
		set(Code.EN);
	}

	private WintonLang() {
	}

	public static Code current() {
		return current;
	}

	public static void set(Code code) {
		current = code == null ? Code.EN : code;
		strings = loadAsset(current.asset);
		if (strings.isEmpty() && current != Code.EN) {
			strings = fallback;
		}
	}

	public static String get(String key) {
		String v = strings.get(key);
		if (v != null) {
			return v;
		}
		v = fallback.get(key);
		return v != null ? v : key;
	}

	/** Locale-ROOT formatting against the active WinTone language string. */
	public static String format(String key, Object... args) {
		String pattern = get(key);
		try {
			return String.format(Locale.ROOT, pattern, args);
		} catch (Exception e) {
			return pattern;
		}
	}

	public static Component text(String key) {
		return Component.literal(get(key));
	}

	private static Map<String, String> loadAsset(String asset) {
		String path = "/assets/winton/lang/" + asset + ".json";
		try (InputStream in = WintonLang.class.getResourceAsStream(path)) {
			if (in == null) {
				return Collections.emptyMap();
			}
			JsonObject obj = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			Map<String, String> map = new HashMap<>();
			for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
				if (e.getValue().isJsonPrimitive()) {
					map.put(e.getKey(), e.getValue().getAsString());
				}
			}
			return map;
		} catch (Exception ex) {
			ex.printStackTrace();
			return Collections.emptyMap();
		}
	}
}
