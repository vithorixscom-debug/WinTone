/*
 * Copyright (c) masa. Modifications Copyright (c) WinTone contributors.
 * This file is part of Winton (WinTone).
 *
 * Winton is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Line formatting inspired by MiniHUD (masa), LGPL-3.0 only.
 */

package winton.minihud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import winton.menu.WintonLang;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Builds cached HUD strings; refresh throttled for performance. */
public final class MiniHudLines {
	private static final SimpleDateFormat REAL_TIME = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	private static final List<String> CACHE = new ArrayList<>();
	private static int lastTick = -1;
	private static double lastX, lastY, lastZ;
	private static double estTps = 20;
	private static long lastWorldTime = -1;
	private static long lastWorldNanos;

	private MiniHudLines() {
	}

	public static List<String> lines(Minecraft mc) {
		MiniHudConfig.ensureLoaded();
		if (!MiniHudConfig.infoHudEnabled() || mc.player == null || mc.level == null) {
			return List.of();
		}
		if (MiniHudConfig.requireSneak() && !mc.player.isShiftKeyDown()) {
			return List.of();
		}
		int tick = mc.player.tickCount;
		int interval = MiniHudConfig.updateIntervalTicks();
		if (tick != lastTick && (lastTick < 0 || tick - lastTick >= interval)) {
			rebuild(mc);
			lastTick = tick;
		}
		return CACHE;
	}

	public static void invalidate() {
		lastTick = -1;
	}

	/** Call each client tick for TPS estimate from world time packets. */
	public static void onClientTick(ClientLevel level) {
		if (level == null) {
			return;
		}
		long wt = level.getGameTime();
		long now = System.nanoTime();
		if (lastWorldTime >= 0 && wt > lastWorldTime && lastWorldNanos > 0) {
			long dTicks = wt - lastWorldTime;
			double dSec = (now - lastWorldNanos) / 1_000_000_000.0;
			if (dSec > 0.05 && dTicks > 0) {
				double tps = dTicks / dSec;
				estTps = Mth.clamp(tps, 0.1, 20.0);
			}
		}
		if (wt != lastWorldTime) {
			lastWorldTime = wt;
			lastWorldNanos = now;
		}
	}

	private static void rebuild(Minecraft mc) {
		CACHE.clear();
		LocalPlayer player = mc.player;
		ClientLevel level = mc.level;
		if (player == null || level == null) {
			return;
		}

		trySeedFromSp(mc);

		double x = player.getX();
		double y = player.getY();
		double z = player.getZ();
		double dx = x - lastX;
		double dy = y - lastY;
		double dz = z - lastZ;
		double speed = Math.sqrt(dx * dx + dy * dy + dz * dz) * 20.0;
		lastX = x;
		lastY = y;
		lastZ = z;

		BlockPos feet = player.blockPosition();
		int cx = feet.getX() >> 4;
		int cz = feet.getZ() >> 4;

		Map<MiniHudInfoLine, String> built = new EnumMap<>(MiniHudInfoLine.class);

		if (MiniHudConfig.line(MiniHudInfoLine.FPS)) {
			built.put(MiniHudInfoLine.FPS, WintonLang.format("winton.minihud.val.fps", mc.getFps()));
		}
		if (MiniHudConfig.line(MiniHudInfoLine.MEMORY_USAGE)) {
			Runtime rt = Runtime.getRuntime();
			long used = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
			long max = rt.maxMemory() / (1024 * 1024);
			built.put(MiniHudInfoLine.MEMORY_USAGE, WintonLang.format("winton.minihud.val.memory", used, max));
		}
		if (MiniHudConfig.line(MiniHudInfoLine.TIME_REAL)) {
			built.put(MiniHudInfoLine.TIME_REAL, WintonLang.format("winton.minihud.val.time_real", REAL_TIME.format(new Date())));
		}
		if (MiniHudConfig.line(MiniHudInfoLine.TIME_WORLD)) {
			built.put(MiniHudInfoLine.TIME_WORLD, WintonLang.format("winton.minihud.val.time_world", level.getGameTime()));
		}
		if (MiniHudConfig.line(MiniHudInfoLine.TIME_WORLD_FORMATTED)) {
			long dayTime = level.getDayTime() % 24000L;
			long day = level.getDayTime() / 24000L;
			int hour = (int) ((dayTime / 1000L + 6) % 24);
			int min = (int) ((dayTime % 1000L) * 60 / 1000);
			built.put(MiniHudInfoLine.TIME_WORLD_FORMATTED,
				WintonLang.format("winton.minihud.val.time_world_fmt", day, hour, min));
		}
		if (MiniHudConfig.line(MiniHudInfoLine.COORDINATES)) {
			built.put(MiniHudInfoLine.COORDINATES,
				WintonLang.format("winton.minihud.val.xyz", x, y, z));
		}
		if (MiniHudConfig.line(MiniHudInfoLine.DIMENSION)) {
			Identifier dim = level.dimension().identifier();
			built.put(MiniHudInfoLine.DIMENSION, WintonLang.format("winton.minihud.val.dim", dim));
		}
		if (MiniHudConfig.line(MiniHudInfoLine.BLOCK_POS)) {
			built.put(MiniHudInfoLine.BLOCK_POS,
				WintonLang.format("winton.minihud.val.block", feet.getX(), feet.getY(), feet.getZ()));
		}
		if (MiniHudConfig.line(MiniHudInfoLine.CHUNK_POS)) {
			built.put(MiniHudInfoLine.CHUNK_POS, WintonLang.format("winton.minihud.val.chunk", cx, cz));
		}
		if (MiniHudConfig.line(MiniHudInfoLine.BLOCK_IN_CHUNK)) {
			built.put(MiniHudInfoLine.BLOCK_IN_CHUNK, WintonLang.format("winton.minihud.val.in_chunk",
				feet.getX() & 15, feet.getY(), feet.getZ() & 15));
		}
		if (MiniHudConfig.line(MiniHudInfoLine.REGION_FILE)) {
			int rx = feet.getX() >> 9;
			int rz = feet.getZ() >> 9;
			built.put(MiniHudInfoLine.REGION_FILE, WintonLang.format("winton.minihud.val.region", rx, rz));
		}
		if (MiniHudConfig.line(MiniHudInfoLine.PLAYER_FACING)) {
			Direction d = player.getDirection();
			built.put(MiniHudInfoLine.PLAYER_FACING,
				WintonLang.format("winton.minihud.val.facing", facingName(d), d.getSerializedName()));
		}
		if (MiniHudConfig.line(MiniHudInfoLine.PLAYER_YAW)) {
			built.put(MiniHudInfoLine.PLAYER_YAW, WintonLang.format("winton.minihud.val.yaw", Mth.wrapDegrees(player.getYRot())));
		}
		if (MiniHudConfig.line(MiniHudInfoLine.PLAYER_PITCH)) {
			built.put(MiniHudInfoLine.PLAYER_PITCH, WintonLang.format("winton.minihud.val.pitch", player.getXRot()));
		}
		if (MiniHudConfig.line(MiniHudInfoLine.SPEED)) {
			built.put(MiniHudInfoLine.SPEED, WintonLang.format("winton.minihud.val.speed", speed));
		}
		if (MiniHudConfig.line(MiniHudInfoLine.SPEED_AXIS)) {
			built.put(MiniHudInfoLine.SPEED_AXIS, WintonLang.format("winton.minihud.val.speed_axis",
				Math.abs(dx) * 20, Math.abs(dy) * 20, Math.abs(dz) * 20));
		}
		if (MiniHudConfig.line(MiniHudInfoLine.LIGHT_LEVEL)) {
			int block = level.getBrightness(LightLayer.BLOCK, feet);
			int sky = level.getBrightness(LightLayer.SKY, feet);
			built.put(MiniHudInfoLine.LIGHT_LEVEL, WintonLang.format("winton.minihud.val.light",
				Math.max(block, sky), block, sky));
		}
		if (MiniHudConfig.line(MiniHudInfoLine.BIOME) || MiniHudConfig.line(MiniHudInfoLine.BIOME_REG_NAME)) {
			Holder<Biome> biome = level.getBiome(feet);
			Identifier id = biome.unwrapKey().map(k -> k.identifier()).orElse(Identifier.withDefaultNamespace("unknown"));
			if (MiniHudConfig.line(MiniHudInfoLine.BIOME)) {
				built.put(MiniHudInfoLine.BIOME, WintonLang.format("winton.minihud.val.biome", id.getPath()));
			}
			if (MiniHudConfig.line(MiniHudInfoLine.BIOME_REG_NAME)) {
				built.put(MiniHudInfoLine.BIOME_REG_NAME, WintonLang.format("winton.minihud.val.biome_id", id));
			}
		}
		if (MiniHudConfig.line(MiniHudInfoLine.DIFFICULTY)) {
			built.put(MiniHudInfoLine.DIFFICULTY,
				WintonLang.format("winton.minihud.val.difficulty", difficultyName(level.getDifficulty())));
		}
		if (MiniHudConfig.line(MiniHudInfoLine.SLIME_CHUNK)) {
			if (!MiniHudConfig.isSeedKnown()) {
				built.put(MiniHudInfoLine.SLIME_CHUNK, WintonLang.get("winton.minihud.slime.no_seed"));
			} else {
				boolean slime = MiniHudConfig.isSlimeChunk(cx, cz, MiniHudConfig.worldSeed());
				built.put(MiniHudInfoLine.SLIME_CHUNK, slime
					? WintonLang.get("winton.minihud.slime.yes")
					: WintonLang.get("winton.minihud.slime.no"));
			}
		}
		if (MiniHudConfig.line(MiniHudInfoLine.ENTITIES) || MiniHudConfig.line(MiniHudInfoLine.ENTITIES_CLIENT_WORLD)) {
			int visible = 0;
			for (Entity ignored : level.entitiesForRendering()) {
				visible++;
			}
			if (MiniHudConfig.line(MiniHudInfoLine.ENTITIES)) {
				built.put(MiniHudInfoLine.ENTITIES, WintonLang.format("winton.minihud.val.entities_vis", visible));
			}
			if (MiniHudConfig.line(MiniHudInfoLine.ENTITIES_CLIENT_WORLD)) {
				built.put(MiniHudInfoLine.ENTITIES_CLIENT_WORLD, WintonLang.format("winton.minihud.val.entities_cli", visible));
			}
		}
		HitResult hit = mc.hitResult;
		if (hit instanceof EntityHitResult ehr) {
			Entity e = ehr.getEntity();
			if (MiniHudConfig.line(MiniHudInfoLine.LOOKING_AT_ENTITY)) {
				built.put(MiniHudInfoLine.LOOKING_AT_ENTITY,
					WintonLang.format("winton.minihud.val.look_entity", e.getName().getString()));
			}
			if (MiniHudConfig.line(MiniHudInfoLine.ENTITY_REG_NAME)) {
				built.put(MiniHudInfoLine.ENTITY_REG_NAME,
					WintonLang.format("winton.minihud.val.entity_id", BuiltInRegistries.ENTITY_TYPE.getKey(e.getType())));
			}
		}
		if (hit instanceof BlockHitResult bhr && bhr.getType() == HitResult.Type.BLOCK) {
			BlockPos bp = bhr.getBlockPos();
			BlockState st = level.getBlockState(bp);
			if (MiniHudConfig.line(MiniHudInfoLine.LOOKING_AT_BLOCK)) {
				built.put(MiniHudInfoLine.LOOKING_AT_BLOCK,
					WintonLang.format("winton.minihud.val.look_block", bp.getX(), bp.getY(), bp.getZ()));
			}
			if (MiniHudConfig.line(MiniHudInfoLine.LOOKING_AT_BLOCK_CHUNK)) {
				built.put(MiniHudInfoLine.LOOKING_AT_BLOCK_CHUNK, WintonLang.format("winton.minihud.val.look_in_chunk",
					bp.getX() & 15, bp.getY(), bp.getZ() & 15));
			}
			if (MiniHudConfig.line(MiniHudInfoLine.BLOCK_PROPS)) {
				String props = st.getValues().isEmpty() ? "" : " " + st.getValues();
				built.put(MiniHudInfoLine.BLOCK_PROPS,
					WintonLang.format("winton.minihud.val.block_props", BuiltInRegistries.BLOCK.getKey(st.getBlock()), props));
			}
		}
		if (MiniHudConfig.line(MiniHudInfoLine.DISTANCE)) {
			BlockPos ref = MiniHudConfig.refPoint();
			if (ref != null) {
				double dist = Math.sqrt(player.distanceToSqr(ref.getX() + 0.5, ref.getY() + 0.5, ref.getZ() + 0.5));
				built.put(MiniHudInfoLine.DISTANCE, WintonLang.format("winton.minihud.val.distance",
					dist, ref.getX(), ref.getY(), ref.getZ()));
			} else {
				built.put(MiniHudInfoLine.DISTANCE, WintonLang.get("winton.minihud.distance.no_ref"));
			}
		}
		if (MiniHudConfig.line(MiniHudInfoLine.SERVER_TPS)) {
			built.put(MiniHudInfoLine.SERVER_TPS, WintonLang.format("winton.minihud.val.tps", estTps));
		}
		if (MiniHudConfig.line(MiniHudInfoLine.PING) && !mc.hasSingleplayerServer() && mc.getConnection() != null) {
			PlayerInfo info = mc.getConnection().getPlayerInfo(player.getUUID());
			if (info != null) {
				built.put(MiniHudInfoLine.PING, WintonLang.format("winton.minihud.val.ping", info.getLatency()));
			}
		}
		if (MiniHudConfig.line(MiniHudInfoLine.LOADED_CHUNKS)) {
			int rd = mc.options.getEffectiveRenderDistance();
			int approx = (2 * rd + 1) * (2 * rd + 1);
			built.put(MiniHudInfoLine.LOADED_CHUNKS, WintonLang.format("winton.minihud.val.chunks", approx));
		}
		if (MiniHudConfig.line(MiniHudInfoLine.PARTICLE_COUNT)) {
			try {
				built.put(MiniHudInfoLine.PARTICLE_COUNT,
					WintonLang.format("winton.minihud.val.particles", mc.particleEngine.countParticles()));
			} catch (Throwable t) {
				built.put(MiniHudInfoLine.PARTICLE_COUNT, WintonLang.get("winton.minihud.val.particles_unk"));
			}
		}

		List<MiniHudInfoLine> order = new ArrayList<>(built.keySet());
		order.sort(Comparator.comparingInt(a -> a.order));
		for (MiniHudInfoLine line : order) {
			CACHE.add(built.get(line));
		}
	}

	private static String facingName(Direction d) {
		return switch (d) {
			case NORTH -> WintonLang.get("winton.minihud.dir.north");
			case SOUTH -> WintonLang.get("winton.minihud.dir.south");
			case EAST -> WintonLang.get("winton.minihud.dir.east");
			case WEST -> WintonLang.get("winton.minihud.dir.west");
			case UP -> WintonLang.get("winton.minihud.dir.up");
			case DOWN -> WintonLang.get("winton.minihud.dir.down");
		};
	}

	private static String difficultyName(Difficulty d) {
		return switch (d) {
			case PEACEFUL -> WintonLang.get("winton.minihud.diff.peaceful");
			case EASY -> WintonLang.get("winton.minihud.diff.easy");
			case NORMAL -> WintonLang.get("winton.minihud.diff.normal");
			case HARD -> WintonLang.get("winton.minihud.diff.hard");
		};
	}

	private static void trySeedFromSp(Minecraft mc) {
		if (MiniHudConfig.isSeedKnown() || !mc.hasSingleplayerServer() || mc.getSingleplayerServer() == null) {
			return;
		}
		try {
			long seed = mc.getSingleplayerServer().overworld().getSeed();
			MiniHudConfig.setWorldSeed(seed);
		} catch (Throwable ignored) {
		}
	}
}
