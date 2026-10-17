/*
 * Copyright (c) masa. Modifications Copyright (c) WinTone contributors.
 * This file is part of Winton (WinTone).
 *
 * Winton is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Adapted concepts from MiniHUD (masa), LGPL-3.0.
 */

package winton.minihud;

import winton.api.event.events.ChatEvent;
import winton.api.event.events.RenderEvent;
import winton.api.event.events.TickEvent;
import winton.api.event.listener.AbstractGameEventListener;
import winton.menu.WintonLang;
import winton.utils.IRenderer;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.awt.Color;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** MiniHUD runtime: info HUD, overlays, seed chat hooks. */
public final class MiniHudBehavior implements AbstractGameEventListener, IRenderer {
	private static final Pattern SEED_CHAT = Pattern.compile("(?i)^(?:minihud-seed|winton-seed)\\s+(-?\\d+)\\s*$");
	private static final Pattern SEED_RESPONSE = Pattern.compile("(?i)seed[^\\d-]*(-?\\d+)");
	private static MiniHudBehavior instance;
	private static final Color SLIME = new Color(32, 240, 32, 160);
	private static final Color REGION = new Color(255, 200, 40, 180);
	private static final Color SPAWN = new Color(80, 160, 255, 160);
	private static final Color RANDOM = new Color(255, 80, 80, 140);

	private MiniHudBehavior() {
	}

	public static MiniHudBehavior get() {
		if (instance == null) {
			instance = new MiniHudBehavior();
			MiniHudConfig.ensureLoaded();
		}
		return instance;
	}

	@Override
	public void onTick(TickEvent event) {
		Minecraft mc = Minecraft.getInstance();
		MiniHudLines.onClientTick(mc.level);
		if (mc.hasSingleplayerServer() && !MiniHudConfig.isSeedKnown() && mc.getSingleplayerServer() != null) {
			try {
				MiniHudConfig.setWorldSeed(mc.getSingleplayerServer().overworld().getSeed());
			} catch (Throwable ignored) {
			}
		}
	}

	@Override
	public void onSendChatMessage(ChatEvent event) {
		String msg = event.getMessage();
		if (msg == null) {
			return;
		}
		Matcher m = SEED_CHAT.matcher(msg.trim());
		if (!m.matches()) {
			return;
		}
		try {
			long seed = Long.parseLong(m.group(1));
			MiniHudConfig.setWorldSeed(seed);
			event.cancel();
			LocalPlayer p = Minecraft.getInstance().player;
			if (p != null) {
				p.displayClientMessage(Component.literal(
					WintonLang.get("winton.minihud.msg.seed_set").replace("%s", Long.toString(seed))), false);
			}
			MiniHudLines.invalidate();
		} catch (NumberFormatException ignored) {
		}
	}

	@Override
	public void onReceivePacket(winton.api.event.events.PacketEvent event) {
		// Seed from /seed system chat is handled via chat intercept when available
	}

	/** Parse seed from system chat text (e.g. after /seed). */
	public static void onGameMessage(String plain) {
		if (plain == null || MiniHudConfig.isSeedKnown()) {
			return;
		}
		Matcher m = SEED_RESPONSE.matcher(plain);
		if (m.find()) {
			try {
				MiniHudConfig.setWorldSeed(Long.parseLong(m.group(1)));
				MiniHudLines.invalidate();
			} catch (NumberFormatException ignored) {
			}
		}
	}

	@Override
	public void onRenderPass(RenderEvent event) {
		MiniHudConfig.ensureLoaded();
		if (!MiniHudConfig.overlaysEnabled()) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		Level level = mc.level;
		if (player == null || level == null) {
			return;
		}
		PoseStack stack = event.getModelViewStack();
		BlockPos feet = player.blockPosition();
		int pcx = feet.getX() >> 4;
		int pcz = feet.getZ() >> 4;

		if (MiniHudConfig.overlay(MiniHudOverlay.SLIME_CHUNKS) && MiniHudConfig.isSeedKnown()
			&& level.dimension() == Level.OVERWORLD) {
			int r = MiniHudConfig.slimeRadius();
			double top = MiniHudConfig.slimeTopY();
			BufferBuilder buf = IRenderer.startLines(SLIME, 0.55f);
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					int cx = pcx + dx;
					int cz = pcz + dz;
					if (!MiniHudConfig.isSlimeChunk(cx, cz, MiniHudConfig.worldSeed())) {
						continue;
					}
					int x0 = cx << 4;
					int z0 = cz << 4;
					IRenderer.emitAABB(buf, stack, new AABB(x0, level.getMinY(), z0, x0 + 16, top, z0 + 16), 2.0f);
				}
			}
			IRenderer.endLines(buf, true);
		}

		if (MiniHudConfig.overlay(MiniHudOverlay.REGION_FILE)) {
			int rx = feet.getX() >> 9;
			int rz = feet.getZ() >> 9;
			int x0 = rx << 9;
			int z0 = rz << 9;
			BufferBuilder buf = IRenderer.startLines(REGION, 0.7f);
			double y = player.getY();
			IRenderer.emitAABB(buf, stack, new AABB(x0, y - 16, z0, x0 + 512, y + 32, z0 + 512), 2.5f);
			IRenderer.endLines(buf, true);
		}

		if (MiniHudConfig.overlay(MiniHudOverlay.SPAWN_CHUNKS_REAL)) {
			BlockPos spawn = level.getRespawnData().pos();
			int scx = spawn.getX() >> 4;
			int scz = spawn.getZ() >> 4;
			BufferBuilder buf = IRenderer.startLines(SPAWN, 0.5f);
			// Entity-processing spawn roughly ±8 chunks; lazy ±11 — draw entity box
			drawChunkRing(buf, stack, scx, scz, 8, level.getMinY(), level.getMaxY());
			IRenderer.endLines(buf, true);
		}

		if (MiniHudConfig.overlay(MiniHudOverlay.RANDOM_TICKS_PLAYER)) {
			BufferBuilder buf = IRenderer.startLines(RANDOM, 0.45f);
			drawChunkRing(buf, stack, pcx, pcz, 8, feet.getY() - 4, feet.getY() + 20);
			IRenderer.endLines(buf, true);
		}
	}

	private static void drawChunkRing(BufferBuilder buf, PoseStack stack, int ccx, int ccz, int radius, double y0, double y1) {
		int x0 = (ccx - radius) << 4;
		int z0 = (ccz - radius) << 4;
		int x1 = (ccx + radius + 1) << 4;
		int z1 = (ccz + radius + 1) << 4;
		IRenderer.emitAABB(buf, stack, new AABB(x0, y0, z0, x1, y1, z1), 2.0f);
	}

	/** 2D info HUD — called from Gui mixin. */
	public static void renderHud(GuiGraphics g, Minecraft mc) {
		MiniHudConfig.ensureLoaded();
		if (!MiniHudConfig.infoHudEnabled() || mc.options.hideGui) {
			return;
		}
		if (mc.screen != null) {
			return;
		}
		List<String> lines = MiniHudLines.lines(mc);
		if (lines.isEmpty()) {
			return;
		}
		int x = 4;
		int y = 4;
		for (String line : lines) {
			g.drawString(mc.font, line, x, y, 0xFFE0E0E0, true);
			y += mc.font.lineHeight + 1;
		}
	}
}
