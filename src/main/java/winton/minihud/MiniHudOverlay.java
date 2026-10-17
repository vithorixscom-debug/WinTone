/*
 * Copyright (c) masa. Modifications Copyright (c) WinTone contributors.
 * This file is part of Winton (WinTone).
 *
 * Winton is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Overlay identifiers adapted from MiniHUD (masa) under LGPL-3.0.
 */

package winton.minihud;

/** World overlays implemented in WinTone MiniHUD (optimized client subset). */
public enum MiniHudOverlay {
	SLIME_CHUNKS(false),
	REGION_FILE(false),
	SPAWN_CHUNKS_REAL(false),
	RANDOM_TICKS_PLAYER(false);

	public final boolean defaultOn;

	MiniHudOverlay(boolean defaultOn) {
		this.defaultOn = defaultOn;
	}

	public String configKey() {
		return "overlay." + name().toLowerCase();
	}

	public String nameKey() {
		return "winton.minihud.overlay." + name().toLowerCase();
	}

	public String tipKey() {
		return "winton.minihud.overlay.tip." + name().toLowerCase();
	}
}
