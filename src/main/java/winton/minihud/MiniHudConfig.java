/*
 * Copyright (c) masa. Modifications Copyright (c) WinTone contributors.
 * This file is part of Winton (WinTone).
 *
 * Winton is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Slime-chunk RNG adapted from MiniHUD MiscUtils (masa), LGPL-3.0.
 */

package winton.minihud;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

/** Persisted MiniHUD toggles + runtime seed/ref point. File: .minecraft/winton/minihud.txt */
public final class MiniHudConfig {
	private static final String FILE = "minihud.txt";
	private static final Random SLIME_RAND = new Random();

	private static boolean loaded;
	private static boolean infoHud;
	private static boolean overlays;
	private static boolean requireSneak;
	private static int slimeRadius = 6;
	private static int updateIntervalTicks = 4;
	private static final EnumMap<MiniHudInfoLine, Boolean> lines = new EnumMap<>(MiniHudInfoLine.class);
	private static final EnumMap<MiniHudOverlay, Boolean> overlayMap = new EnumMap<>(MiniHudOverlay.class);

	private static boolean seedKnown;
	private static long worldSeed;
	private static BlockPos refPoint;
	private static double slimeTopY = 80;

	private MiniHudConfig() {
	}

	public static void ensureLoaded() {
		if (loaded) {
			return;
		}
		loaded = true;
		for (MiniHudInfoLine line : MiniHudInfoLine.values()) {
			lines.put(line, line.defaultOn);
		}
		for (MiniHudOverlay o : MiniHudOverlay.values()) {
			overlayMap.put(o, o.defaultOn);
		}
		Path path = file();
		if (!Files.isRegularFile(path)) {
			return;
		}
		try (BufferedReader br = Files.newBufferedReader(path)) {
			String row;
			while ((row = br.readLine()) != null) {
				row = row.trim();
				if (row.isEmpty() || row.startsWith("#")) {
					continue;
				}
				int eq = row.indexOf('=');
				if (eq <= 0) {
					continue;
				}
				String k = row.substring(0, eq).trim();
				String v = row.substring(eq + 1).trim();
				switch (k) {
					case "infoHud" -> infoHud = parseBool(v, false);
					case "overlays" -> overlays = parseBool(v, false);
					case "requireSneak" -> requireSneak = parseBool(v, false);
					case "slimeRadius" -> slimeRadius = clamp(parseInt(v, 6), 1, 32);
					case "updateInterval" -> updateIntervalTicks = clamp(parseInt(v, 4), 1, 20);
					case "slimeTopY" -> slimeTopY = parseDouble(v, 80);
					case "seed" -> {
						try {
							worldSeed = Long.parseLong(v);
							seedKnown = true;
						} catch (NumberFormatException ignored) {
						}
					}
					default -> {
						if (k.startsWith("info.")) {
							for (MiniHudInfoLine line : MiniHudInfoLine.values()) {
								if (line.configKey().equals(k)) {
									lines.put(line, parseBool(v, line.defaultOn));
									break;
								}
							}
						} else if (k.startsWith("overlay.")) {
							for (MiniHudOverlay o : MiniHudOverlay.values()) {
								if (o.configKey().equals(k)) {
									overlayMap.put(o, parseBool(v, o.defaultOn));
									break;
								}
							}
						}
					}
				}
			}
		} catch (Exception e) {
			System.err.println("[WinTone MiniHUD] Failed to load config: " + e);
		}
	}

	public static void save() {
		ensureLoaded();
		try {
			Path path = file();
			Files.createDirectories(path.getParent());
			try (BufferedWriter bw = Files.newBufferedWriter(path)) {
				bw.write("# WinTone MiniHUD (LGPL-3.0 only) — adapted from masa MiniHUD\n");
				bw.write("infoHud=" + infoHud + "\n");
				bw.write("overlays=" + overlays + "\n");
				bw.write("requireSneak=" + requireSneak + "\n");
				bw.write("slimeRadius=" + slimeRadius + "\n");
				bw.write("updateInterval=" + updateIntervalTicks + "\n");
				bw.write("slimeTopY=" + slimeTopY + "\n");
				if (seedKnown) {
					bw.write("seed=" + worldSeed + "\n");
				}
				for (Map.Entry<MiniHudInfoLine, Boolean> e : lines.entrySet()) {
					bw.write(e.getKey().configKey() + "=" + e.getValue() + "\n");
				}
				for (Map.Entry<MiniHudOverlay, Boolean> e : overlayMap.entrySet()) {
					bw.write(e.getKey().configKey() + "=" + e.getValue() + "\n");
				}
			}
		} catch (Exception e) {
			System.err.println("[WinTone MiniHUD] Failed to save config: " + e);
		}
	}

	private static Path file() {
		return Minecraft.getInstance().gameDirectory.toPath().resolve("winton").resolve(FILE);
	}

	public static boolean infoHudEnabled() {
		ensureLoaded();
		return infoHud;
	}

	public static void setInfoHud(boolean v) {
		ensureLoaded();
		infoHud = v;
		save();
	}

	public static boolean overlaysEnabled() {
		ensureLoaded();
		return overlays;
	}

	public static void setOverlays(boolean v) {
		ensureLoaded();
		overlays = v;
		save();
	}

	public static boolean requireSneak() {
		ensureLoaded();
		return requireSneak;
	}

	public static void setRequireSneak(boolean v) {
		ensureLoaded();
		requireSneak = v;
		save();
	}

	public static int slimeRadius() {
		ensureLoaded();
		return slimeRadius;
	}

	public static void setSlimeRadius(int v) {
		ensureLoaded();
		slimeRadius = clamp(v, 1, 32);
		save();
	}

	public static int updateIntervalTicks() {
		ensureLoaded();
		return updateIntervalTicks;
	}

	public static void setUpdateIntervalTicks(int v) {
		ensureLoaded();
		updateIntervalTicks = clamp(v, 1, 20);
		save();
	}

	public static double slimeTopY() {
		ensureLoaded();
		return slimeTopY;
	}

	public static void setSlimeTopY(double y) {
		ensureLoaded();
		slimeTopY = y;
		save();
	}

	public static boolean line(MiniHudInfoLine line) {
		ensureLoaded();
		return Boolean.TRUE.equals(lines.get(line));
	}

	public static void setLine(MiniHudInfoLine line, boolean on) {
		ensureLoaded();
		lines.put(line, on);
		save();
	}

	public static boolean overlay(MiniHudOverlay o) {
		ensureLoaded();
		return Boolean.TRUE.equals(overlayMap.get(o));
	}

	public static void setOverlay(MiniHudOverlay o, boolean on) {
		ensureLoaded();
		overlayMap.put(o, on);
		save();
	}

	public static boolean isSeedKnown() {
		ensureLoaded();
		return seedKnown;
	}

	public static long worldSeed() {
		ensureLoaded();
		return worldSeed;
	}

	public static void setWorldSeed(long seed) {
		ensureLoaded();
		worldSeed = seed;
		seedKnown = true;
		save();
	}

	public static void clearSeed() {
		ensureLoaded();
		seedKnown = false;
		save();
	}

	public static BlockPos refPoint() {
		ensureLoaded();
		return refPoint;
	}

	public static void setRefPoint(BlockPos pos) {
		ensureLoaded();
		refPoint = pos == null ? null : pos.immutable();
	}

	public static void setRefPoint(Vec3 pos) {
		setRefPoint(BlockPos.containing(pos));
	}

	/** MiniHUD / vanilla slime-chunk formula (masa MiscUtils). */
	public static boolean isSlimeChunk(int chunkX, int chunkZ, long seed) {
		long slimeSeed = 987234911L;
		long rngSeed = seed
			+ (long) (chunkX * chunkX * 4987142)
			+ (long) (chunkX * 5947611)
			+ (long) (chunkZ * chunkZ) * 4392871L
			+ (long) (chunkZ * 389711) ^ slimeSeed;
		SLIME_RAND.setSeed(rngSeed);
		return SLIME_RAND.nextInt(10) == 0;
	}

	private static boolean parseBool(String v, boolean def) {
		if (v == null || v.isEmpty()) {
			return def;
		}
		return "true".equalsIgnoreCase(v) || "1".equals(v) || "yes".equalsIgnoreCase(v);
	}

	private static int parseInt(String v, int def) {
		try {
			return Integer.parseInt(v.trim());
		} catch (Exception e) {
			return def;
		}
	}

	private static double parseDouble(String v, double def) {
		try {
			return Double.parseDouble(v.trim());
		} catch (Exception e) {
			return def;
		}
	}

	private static int clamp(int v, int lo, int hi) {
		return Math.max(lo, Math.min(hi, v));
	}
}
