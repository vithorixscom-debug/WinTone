/*
 * This file is part of Winton, a modified version of Baritone.
 * Copyright (c) Baritone contributors.
 * Copyright (c) WinTone contributors, for modifications.
 *
 * Winton is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Based on Baritone (https://github.com/cabaletta/baritone), LGPL-3.0.
 */

package winton.menu;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Menu keybind — Options → Controls → Winton (default H).
 * Also persisted in winton/menu.txt so the bind survives Options load order.
 */
public final class WintonMenuKeys {
	private static KeyMapping.Category category;
	private static KeyMapping menu;
	private static boolean ready;
	private static boolean listeningForRebind;

	private WintonMenuKeys() {
	}

	public static synchronized void ensureReady() {
		if (ready) {
			return;
		}
		try {
			category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("winton", "keys"));
			menu = new KeyMapping(
				"key.winton.menu",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_H,
				category
			);
			ready = true;
			applySavedBinding();
		} catch (Throwable t) {
			System.err.println("[WinTone] Failed to register menu key: " + t);
		}
	}

	public static KeyMapping menu() {
		ensureReady();
		return menu;
	}

	public static KeyMapping[] withMenu(KeyMapping[] vanilla) {
		ensureReady();
		if (menu == null || vanilla == null) {
			return vanilla;
		}
		for (KeyMapping k : vanilla) {
			if (k == menu) {
				return vanilla;
			}
		}
		KeyMapping[] out = new KeyMapping[vanilla.length + 1];
		System.arraycopy(vanilla, 0, out, 0, vanilla.length);
		out[vanilla.length] = menu;
		return out;
	}

	public static void applySavedBinding() {
		ensureReady();
		if (menu == null) {
			return;
		}
		String saved = WintonMenuConfig.menuKeyName();
		if (saved == null || saved.isEmpty()) {
			return;
		}
		try {
			InputConstants.Key key = InputConstants.getKey(saved);
			menu.setKey(key);
			KeyMapping.resetMapping();
		} catch (Throwable t) {
			System.err.println("[WinTone] Bad saved menu key '" + saved + "': " + t);
		}
	}

	public static void persistFromMapping() {
		ensureReady();
		if (menu == null) {
			return;
		}
		try {
			WintonMenuConfig.setMenuKeyName(menu.saveString());
		} catch (Throwable t) {
			System.err.println("[WinTone] Persist menu key failed: " + t);
		}
	}

	public static String displayLabel() {
		ensureReady();
		if (menu == null) {
			return "?";
		}
		return menu.getTranslatedKeyMessage().getString();
	}

	public static boolean isListeningForRebind() {
		return listeningForRebind;
	}

	public static void beginRebind() {
		listeningForRebind = true;
	}

	public static void cancelRebind() {
		listeningForRebind = false;
	}

	public static boolean consumeRebindKey(KeyEvent event) {
		if (!listeningForRebind || menu == null) {
			return false;
		}
		int key = event.key();
		if (key == GLFW.GLFW_KEY_ESCAPE) {
			listeningForRebind = false;
			return true;
		}
		if (key == GLFW.GLFW_KEY_UNKNOWN) {
			return true;
		}
		try {
			InputConstants.Key bound = InputConstants.Type.KEYSYM.getOrCreate(key);
			menu.setKey(bound);
			KeyMapping.resetMapping();
			persistFromMapping();
			Minecraft mc = Minecraft.getInstance();
			if (mc.options != null) {
				mc.options.save();
			}
		} catch (Throwable t) {
			System.err.println("[WinTone] Rebind failed: " + t);
		}
		listeningForRebind = false;
		return true;
	}

	public static void tick(Minecraft client) {
		if (client == null || client.player == null) {
			return;
		}
		ensureReady();
		if (menu == null) {
			return;
		}
		try {
			WintonFreecam.tick();
			while (menu.consumeClick()) {
				if (listeningForRebind) {
					continue;
				}
				if (client.screen instanceof WintonMenuScreen) {
					client.setScreen(null);
				} else if (client.screen == null || client.screen instanceof WintonClearSelectScreen) {
					if (client.screen instanceof WintonClearSelectScreen) {
						client.screen.onClose();
					} else {
						client.setScreen(new WintonMenuScreen());
					}
				}
			}
		} catch (Throwable t) {
			System.err.println("[WinTone] Menu key tick suppressed: " + t);
		}
	}
}
