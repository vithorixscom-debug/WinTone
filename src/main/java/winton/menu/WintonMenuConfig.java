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

import net.minecraft.client.Minecraft;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Persists menu prefs (.minecraft/winton/menu.txt). */
public final class WintonMenuConfig {
	private static final String FILE_NAME = "menu.txt";
	private static final String CFG_VERSION = "1.17.2w";
	private static boolean loaded;
	private static String cfgVersion = "";
	private static String menuKeyName = "";
	private static double freecamSpeed = 1.15D;
	private static Map<String, Boolean> lastOres = new LinkedHashMap<>();

	/** Last open menu tab / expanders / fields — restored on reopen. */
	private static String lastTab = "ACTIONS";
	private static String lastAction = "NONE";
	private static String lastGroups = "";
	private static int lastScroll;
	private static int lastLiteIndex = 1;
	private static boolean lastMineAdvanced;
	private static final Map<String, String> fields = new LinkedHashMap<>();

	static {
		fields.put("tunnelH", "2");
		fields.put("tunnelW", "1");
		fields.put("tunnelD", "64");
		fields.put("farmRange", "64");
		fields.put("exploreX", "");
		fields.put("exploreZ", "");
		fields.put("find", "diamond_ore");
		fields.put("thisWay", "100");
		fields.put("gotoX", "");
		fields.put("gotoY", "");
		fields.put("gotoZ", "");
		fields.put("clear1x", "");
		fields.put("clear1y", "");
		fields.put("clear1z", "");
		fields.put("clear2x", "");
		fields.put("clear2y", "");
		fields.put("clear2z", "");
	}

	private WintonMenuConfig() {
	}

	private static Path file() {
		return Minecraft.getInstance().gameDirectory.toPath().resolve("winton").resolve(FILE_NAME);
	}

	public static void ensureLoaded() {
		if (loaded) {
			return;
		}
		loaded = true;
		Path path = file();
		if (!Files.isRegularFile(path)) {
			WintonLang.set(WintonLang.Code.EN);
			try {
				WintonPerf.applyMax();
			} catch (Throwable ignored) {
			}
			cfgVersion = CFG_VERSION;
			return;
		}
		try (BufferedReader in = Files.newBufferedReader(path)) {
			String line;
			while ((line = in.readLine()) != null) {
				line = line.trim();
				if (line.isEmpty() || line.startsWith("#")) {
					continue;
				}
				int sp = line.indexOf(' ');
				if (sp <= 0) {
					continue;
				}
				String key = line.substring(0, sp);
				String val = line.substring(sp + 1).trim();
				switch (key) {
					case "lang" -> WintonLang.set(WintonLang.Code.fromId(val));
					case "cfg" -> cfgVersion = val;
					case "menuKey" -> menuKeyName = val;
					case "freecamSpeed" -> {
						try {
							freecamSpeed = Double.parseDouble(val);
							WintonFreecam.setSpeed(freecamSpeed);
						} catch (NumberFormatException ignored) {
						}
					}
					case "menuTab" -> lastTab = val;
					case "menuAction" -> lastAction = val;
					case "menuGroups" -> lastGroups = val;
					case "menuScroll" -> {
						try {
							lastScroll = Math.max(0, Integer.parseInt(val));
						} catch (NumberFormatException ignored) {
						}
					}
					case "menuLite" -> {
						try {
							lastLiteIndex = Math.max(1, Integer.parseInt(val));
						} catch (NumberFormatException ignored) {
						}
					}
					case "menuMineAdv" -> lastMineAdvanced = Boolean.parseBoolean(val);
					default -> {
						if (key.startsWith("field.")) {
							fields.put(key.substring("field.".length()), val);
						}
					}
				}
			}
		} catch (Exception ex) {
			ex.printStackTrace();
			WintonLang.set(WintonLang.Code.EN);
		}
		if (!CFG_VERSION.equals(cfgVersion)) {
			try {
				WintonPerf.applyMax();
			} catch (Throwable ignored) {
			}
			cfgVersion = CFG_VERSION;
			try {
				flush();
			} catch (Throwable ignored) {
			}
		}
	}

	public static String menuKeyName() {
		ensureLoaded();
		return menuKeyName;
	}

	public static void setMenuKeyName(String name) {
		ensureLoaded();
		if (name == null) {
			return;
		}
		menuKeyName = name;
		flush();
	}

	public static double freecamSpeed() {
		ensureLoaded();
		return freecamSpeed;
	}

	public static void setFreecamSpeed(double speed) {
		ensureLoaded();
		freecamSpeed = Math.max(0.2D, Math.min(8.0D, speed));
		WintonFreecam.setSpeed(freecamSpeed);
		flush();
	}

	public static String lastTab() {
		ensureLoaded();
		return lastTab;
	}

	public static String lastAction() {
		ensureLoaded();
		return lastAction;
	}

	public static int lastScroll() {
		ensureLoaded();
		return lastScroll;
	}

	public static int lastLiteIndex() {
		ensureLoaded();
		return lastLiteIndex;
	}

	public static boolean lastMineAdvanced() {
		ensureLoaded();
		return lastMineAdvanced;
	}

	public static String field(String key, String fallback) {
		ensureLoaded();
		String v = fields.get(key);
		return v != null ? v : fallback;
	}

	public static void setField(String key, String value) {
		ensureLoaded();
		if (key == null) {
			return;
		}
		fields.put(key, value == null ? "" : value);
	}

	public static Set<String> lastGroups() {
		ensureLoaded();
		Set<String> out = new java.util.LinkedHashSet<>();
		if (lastGroups == null || lastGroups.isEmpty()) {
			return out;
		}
		for (String p : lastGroups.split(",")) {
			String t = p.trim();
			if (!t.isEmpty()) {
				out.add(t);
			}
		}
		return out;
	}

	public static void saveUiState(String tab, String action, Set<?> groups, int scroll, int liteIndex, boolean mineAdvanced) {
		ensureLoaded();
		if (tab != null && !tab.isEmpty()) {
			lastTab = tab;
		}
		if (action != null) {
			lastAction = action;
		}
		if (groups != null) {
			StringBuilder sb = new StringBuilder();
			for (Object g : groups) {
				if (sb.length() > 0) {
					sb.append(',');
				}
				sb.append(g);
			}
			lastGroups = sb.toString();
		}
		lastScroll = Math.max(0, scroll);
		lastLiteIndex = Math.max(1, liteIndex);
		lastMineAdvanced = mineAdvanced;
		flush();
	}

	public static Map<String, Boolean> loadOreToggles() {
		ensureLoaded();
		Map<String, Boolean> map = WintonMenuActions.defaultOreToggles();
		Path path = file();
		if (!Files.isRegularFile(path)) {
			lastOres = new LinkedHashMap<>(map);
			return map;
		}
		try (BufferedReader in = Files.newBufferedReader(path)) {
			String line;
			while ((line = in.readLine()) != null) {
				line = line.trim();
				if (line.isEmpty() || line.startsWith("#")) {
					continue;
				}
				int sp = line.indexOf(' ');
				if (sp <= 0) {
					continue;
				}
				String id = line.substring(0, sp);
				String val = line.substring(sp + 1).trim();
				if (isMetaKey(id) || id.startsWith("field.")) {
					continue;
				}
				if (map.containsKey(id)) {
					map.put(id, Boolean.parseBoolean(val));
				}
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		lastOres = new LinkedHashMap<>(map);
		return map;
	}

	private static boolean isMetaKey(String id) {
		return "lang".equals(id) || "cfg".equals(id) || "menuKey".equals(id) || "freecamSpeed".equals(id)
			|| "menuTab".equals(id) || "menuAction".equals(id) || "menuGroups".equals(id)
			|| "menuScroll".equals(id) || "menuLite".equals(id) || "menuMineAdv".equals(id);
	}

	public static void save(Map<String, Boolean> ores) {
		if (ores != null) {
			lastOres = new LinkedHashMap<>(ores);
		}
		flush();
	}

	private static void flush() {
		try {
			ensureLoaded();
			Path path = file();
			Files.createDirectories(path.getParent());
			Map<String, Boolean> ores = lastOres.isEmpty() ? WintonMenuActions.defaultOreToggles() : lastOres;
			try (BufferedWriter out = Files.newBufferedWriter(path)) {
				out.write("# WinTone menu\n");
				out.write("cfg " + CFG_VERSION + "\n");
				out.write("lang " + WintonLang.current().id + "\n");
				if (menuKeyName != null && !menuKeyName.isEmpty()) {
					out.write("menuKey " + menuKeyName + "\n");
				}
				out.write("freecamSpeed " + freecamSpeed + "\n");
				out.write("menuTab " + lastTab + "\n");
				out.write("menuAction " + lastAction + "\n");
				out.write("menuGroups " + lastGroups + "\n");
				out.write("menuScroll " + lastScroll + "\n");
				out.write("menuLite " + lastLiteIndex + "\n");
				out.write("menuMineAdv " + lastMineAdvanced + "\n");
				for (Map.Entry<String, String> e : fields.entrySet()) {
					out.write("field." + e.getKey() + " " + e.getValue() + "\n");
				}
				for (Map.Entry<String, Boolean> e : ores.entrySet()) {
					out.write(e.getKey() + " " + Boolean.TRUE.equals(e.getValue()) + "\n");
				}
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	public static void saveOreToggles(Map<String, Boolean> ores) {
		save(ores);
	}

	public static void saveLanguage(WintonLang.Code code, Map<String, Boolean> ores) {
		WintonLang.set(code);
		winton.minihud.MiniHudLines.invalidate();
		save(ores);
	}
}
