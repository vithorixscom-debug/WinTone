/*
 * Copyright (c) masa. Modifications Copyright (c) WinTone contributors.
 * This file is part of Winton (WinTone).
 *
 * Winton is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Info-line identifiers adapted from MiniHUD (masa) under LGPL-3.0.
 * Original: https://github.com/maruohon/minihud
 */

package winton.minihud;

/**
 * Info HUD lines — subset of MiniHUD InfoLineToggle, optimized for 1.21 client.
 */
public enum MiniHudInfoLine {
	FPS(false, 0),
	MEMORY_USAGE(false, 1),
	TIME_REAL(false, 2),
	TIME_WORLD(false, 3),
	TIME_WORLD_FORMATTED(false, 4),
	COORDINATES(false, 5),
	DIMENSION(false, 6),
	BLOCK_POS(false, 7),
	CHUNK_POS(false, 8),
	BLOCK_IN_CHUNK(false, 9),
	REGION_FILE(false, 10),
	PLAYER_FACING(false, 11),
	PLAYER_YAW(false, 12),
	PLAYER_PITCH(false, 13),
	SPEED(false, 14),
	SPEED_AXIS(false, 15),
	LIGHT_LEVEL(false, 16),
	BIOME(false, 17),
	BIOME_REG_NAME(false, 18),
	DIFFICULTY(false, 19),
	SLIME_CHUNK(false, 20),
	ENTITIES(false, 21),
	ENTITIES_CLIENT_WORLD(false, 22),
	LOOKING_AT_ENTITY(false, 23),
	ENTITY_REG_NAME(false, 24),
	LOOKING_AT_BLOCK(false, 25),
	LOOKING_AT_BLOCK_CHUNK(false, 26),
	BLOCK_PROPS(false, 27),
	DISTANCE(false, 28),
	SERVER_TPS(false, 29),
	PING(false, 30),
	LOADED_CHUNKS(false, 31),
	PARTICLE_COUNT(false, 32);

	public final boolean defaultOn;
	public final int order;

	MiniHudInfoLine(boolean defaultOn, int order) {
		this.defaultOn = defaultOn;
		this.order = order;
	}

	public String configKey() {
		return "info." + name().toLowerCase();
	}

	public String nameKey() {
		return "winton.minihud.line." + name().toLowerCase();
	}

	public String tipKey() {
		return "winton.minihud.line.tip." + name().toLowerCase();
	}
}
