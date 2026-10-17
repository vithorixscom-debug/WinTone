/*
 * This file is part of Winton.
 *
 * Derived from Tweakeroo (https://github.com/maruohon/tweakeroo).
 * Copyright (c) masa. Modifications Copyright (c) WinTone contributors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * LGPL-3.0-only. See LICENSE_TWEAKEROO.txt and NOTICE_TWEAKEROO.txt.
 */

package winton.tweakeroo;

import net.minecraft.client.Minecraft;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

/** Persist tweak/disable toggles + numeric generics. File: .minecraft/winton/tweakeroo.txt */
public final class TweakerooConfig {
	private static final String FILE = "tweakeroo.txt";
	private static boolean loaded;
	private static final EnumMap<TweakFeature, Boolean> features = new EnumMap<>(TweakFeature.class);
	private static final EnumMap<TweakDisable, Boolean> disables = new EnumMap<>(TweakDisable.class);

	private static double gammaValue = 16.0;
	private static float zoomFov = 30F;
	private static float staticFov = 70F;
	private static double flySpeed = 0.05;
	private static int fastClickCount = 2;
	private static int periodicAttackInterval = 20;
	private static int periodicUseInterval = 20;
	private static int durabilitySwapThreshold = 10;
	private static int cloudHeight = 192;
	private static float snapYawStep = 45F;

	private TweakerooConfig() {
	}

	public static void ensureLoaded() {
		if (loaded) {
			return;
		}
		loaded = true;
		for (TweakFeature f : TweakFeature.values()) {
			features.put(f, false);
		}
		for (TweakDisable d : TweakDisable.values()) {
			disables.put(d, false);
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
					case "gammaValue" -> gammaValue = parseDouble(v, 16);
					case "zoomFov" -> zoomFov = (float) parseDouble(v, 30);
					case "staticFov" -> staticFov = (float) parseDouble(v, 70);
					case "flySpeed" -> flySpeed = parseDouble(v, 0.05);
					case "fastClickCount" -> fastClickCount = clamp(parseInt(v, 2), 1, 16);
					case "periodicAttackInterval" -> periodicAttackInterval = clamp(parseInt(v, 20), 1, 200);
					case "periodicUseInterval" -> periodicUseInterval = clamp(parseInt(v, 20), 1, 200);
					case "durabilitySwapThreshold" -> durabilitySwapThreshold = clamp(parseInt(v, 10), 1, 100);
					case "cloudHeight" -> cloudHeight = parseInt(v, 192);
					case "snapYawStep" -> snapYawStep = (float) parseDouble(v, 45);
					default -> {
						if (k.startsWith("feat.")) {
							for (TweakFeature f : TweakFeature.values()) {
								if (("feat." + f.configId).equals(k)) {
									features.put(f, parseBool(v));
									break;
								}
							}
						} else if (k.startsWith("dis.")) {
							for (TweakDisable d : TweakDisable.values()) {
								if (("dis." + d.configId).equals(k)) {
									disables.put(d, parseBool(v));
									break;
								}
							}
						}
					}
				}
			}
		} catch (Exception e) {
			System.err.println("[WinTone Tweakeroo] load failed: " + e);
		}
	}

	public static void save() {
		ensureLoaded();
		try {
			Path path = file();
			Files.createDirectories(path.getParent());
			try (BufferedWriter bw = Files.newBufferedWriter(path)) {
				bw.write("# WinTone Tweakeroo (LGPL-3.0 only) — adapted from masa Tweakeroo\n");
				bw.write("gammaValue=" + gammaValue + "\n");
				bw.write("zoomFov=" + zoomFov + "\n");
				bw.write("staticFov=" + staticFov + "\n");
				bw.write("flySpeed=" + flySpeed + "\n");
				bw.write("fastClickCount=" + fastClickCount + "\n");
				bw.write("periodicAttackInterval=" + periodicAttackInterval + "\n");
				bw.write("periodicUseInterval=" + periodicUseInterval + "\n");
				bw.write("durabilitySwapThreshold=" + durabilitySwapThreshold + "\n");
				bw.write("cloudHeight=" + cloudHeight + "\n");
				bw.write("snapYawStep=" + snapYawStep + "\n");
				for (Map.Entry<TweakFeature, Boolean> e : features.entrySet()) {
					bw.write("feat." + e.getKey().configId + "=" + e.getValue() + "\n");
				}
				for (Map.Entry<TweakDisable, Boolean> e : disables.entrySet()) {
					bw.write("dis." + e.getKey().configId + "=" + e.getValue() + "\n");
				}
			}
		} catch (Exception e) {
			System.err.println("[WinTone Tweakeroo] save failed: " + e);
		}
	}

	private static Path file() {
		return Minecraft.getInstance().gameDirectory.toPath().resolve("winton").resolve(FILE);
	}

	public static boolean feature(TweakFeature f) {
		ensureLoaded();
		return Boolean.TRUE.equals(features.get(f)) && f.implemented;
	}

	public static boolean featureRaw(TweakFeature f) {
		ensureLoaded();
		return Boolean.TRUE.equals(features.get(f));
	}

	public static void setFeature(TweakFeature f, boolean on) {
		ensureLoaded();
		features.put(f, on);
		save();
	}

	public static boolean disable(TweakDisable d) {
		ensureLoaded();
		return Boolean.TRUE.equals(disables.get(d)) && d.implemented;
	}

	public static boolean disableRaw(TweakDisable d) {
		ensureLoaded();
		return Boolean.TRUE.equals(disables.get(d));
	}

	public static void setDisable(TweakDisable d, boolean on) {
		ensureLoaded();
		disables.put(d, on);
		save();
	}

	public static double gammaValue() { ensureLoaded(); return gammaValue; }
	public static void setGammaValue(double v) { ensureLoaded(); gammaValue = v; save(); }
	public static float zoomFov() { ensureLoaded(); return zoomFov; }
	public static float staticFov() { ensureLoaded(); return staticFov; }
	public static double flySpeed() { ensureLoaded(); return flySpeed; }
	public static int fastClickCount() { ensureLoaded(); return fastClickCount; }
	public static int periodicAttackInterval() { ensureLoaded(); return periodicAttackInterval; }
	public static int periodicUseInterval() { ensureLoaded(); return periodicUseInterval; }
	public static int durabilitySwapThreshold() { ensureLoaded(); return durabilitySwapThreshold; }
	public static int cloudHeight() { ensureLoaded(); return cloudHeight; }
	public static float snapYawStep() { ensureLoaded(); return snapYawStep; }

	private static boolean parseBool(String v) {
		return "true".equalsIgnoreCase(v) || "1".equals(v);
	}

	private static int parseInt(String v, int def) {
		try { return Integer.parseInt(v.trim()); } catch (Exception e) { return def; }
	}

	private static double parseDouble(String v, double def) {
		try { return Double.parseDouble(v.trim()); } catch (Exception e) { return def; }
	}

	private static int clamp(int v, int lo, int hi) {
		return Math.max(lo, Math.min(hi, v));
	}
}
