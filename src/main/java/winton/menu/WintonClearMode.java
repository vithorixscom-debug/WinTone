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

import winton.api.IWinton;
import winton.api.WintonAPI;
import winton.api.selection.ISelection;
import winton.api.utils.BetterBlockPos;
import winton.utils.PathRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Territory clear: built-in freecam + 2-corner sel + cleararea (never paths for selection). */
public final class WintonClearMode {
	private static BetterBlockPos pendingPos1;

	private WintonClearMode() {
	}

	public static BetterBlockPos pendingPos1() {
		return pendingPos1;
	}

	public static void resetPending() {
		pendingPos1 = null;
	}

	public static BlockPos raycastCamera(double reach) {
		Minecraft mc = Minecraft.getInstance();
		Entity cam = mc.getCameraEntity();
		if (cam == null || cam.level() == null) {
			return null;
		}
		Vec3 start;
		Vec3 look;
		if (WintonFreecam.isActive()) {
			start = new Vec3(WintonFreecam.x(), WintonFreecam.y(), WintonFreecam.z());
			look = Vec3.directionFromRotation(WintonFreecam.xRot(), WintonFreecam.yRot());
		} else {
			start = cam.getEyePosition(1.0F);
			look = cam.getViewVector(1.0F);
		}
		Vec3 end = start.add(look.x * reach, look.y * reach, look.z * reach);
		HitResult hit = cam.level().clip(new ClipContext(
			start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, cam));
		if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
			return ((BlockHitResult) hit).getBlockPos();
		}
		return null;
	}

	/** Click / look corner: first = pos1, second = finish selection. */
	public static void acceptCorner(BetterBlockPos pos) {
		if (pos == null) {
			return;
		}
		if (pendingPos1 == null) {
			pendingPos1 = pos;
			msg("winton.clear.pos1_set", pos.x + " " + pos.y + " " + pos.z);
			return;
		}
		IWinton w = WintonAPI.getProvider().getPrimaryWinton();
		if (w == null) {
			return;
		}
		w.getSelectionManager().removeAllSelections();
		w.getSelectionManager().addSelection(pendingPos1, pos);
		pendingPos1 = null;
		msg("winton.clear.sel_ready");
	}

	public static boolean setPos1Look() {
		BlockPos hit = raycastCamera(256);
		if (hit == null) {
			msg("winton.clear.no_look");
			return false;
		}
		pendingPos1 = BetterBlockPos.from(hit);
		msg("winton.clear.pos1_set", pendingPos1.x + " " + pendingPos1.y + " " + pendingPos1.z);
		return true;
	}

	public static boolean setPos2Look() {
		if (pendingPos1 == null) {
			msg("winton.clear.need_pos1");
			return false;
		}
		BlockPos hit = raycastCamera(256);
		if (hit == null) {
			msg("winton.clear.no_look");
			return false;
		}
		acceptCorner(BetterBlockPos.from(hit));
		return true;
	}

	public static void clearSelections() {
		IWinton w = WintonAPI.getProvider().getPrimaryWinton();
		if (w != null) {
			w.getSelectionManager().removeAllSelections();
		}
		pendingPos1 = null;
		msg("winton.clear.sel_cleared");
	}

	public static boolean setPos1Here() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return false;
		}
		pendingPos1 = BetterBlockPos.from(mc.player.blockPosition());
		msg("winton.clear.pos1_set", pendingPos1.x + " " + pendingPos1.y + " " + pendingPos1.z);
		return true;
	}

	public static boolean setPos2Here() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return false;
		}
		if (pendingPos1 == null) {
			IWinton w = WintonAPI.getProvider().getPrimaryWinton();
			if (w != null) {
				ISelection[] sels = w.getSelectionManager().getSelections();
				if (sels.length > 0) {
					pendingPos1 = BetterBlockPos.from(sels[sels.length - 1].pos1());
				}
			}
		}
		if (pendingPos1 == null) {
			msg("winton.clear.need_pos1");
			return false;
		}
		acceptCorner(BetterBlockPos.from(mc.player.blockPosition()));
		return true;
	}

	public static boolean setPos1Coords(int x, int y, int z) {
		pendingPos1 = new BetterBlockPos(x, y, z);
		msg("winton.clear.pos1_set", x + " " + y + " " + z);
		return true;
	}

	public static boolean setPos2Coords(int x, int y, int z) {
		if (pendingPos1 == null) {
			IWinton w = WintonAPI.getProvider().getPrimaryWinton();
			if (w != null) {
				ISelection[] sels = w.getSelectionManager().getSelections();
				if (sels.length > 0) {
					pendingPos1 = BetterBlockPos.from(sels[sels.length - 1].pos1());
				}
			}
		}
		if (pendingPos1 == null) {
			msg("winton.clear.need_pos1");
			return false;
		}
		acceptCorner(new BetterBlockPos(x, y, z));
		return true;
	}

	public static boolean startClearArea() {
		IWinton w = WintonAPI.getProvider().getPrimaryWinton();
		if (w == null) {
			return false;
		}
		ISelection[] sels = w.getSelectionManager().getSelections();
		if (sels.length == 0) {
			msg("winton.clear.no_sel");
			return false;
		}
		WintonFreecam.disable();
		try {
			var s = WintonAPI.getSettings();
			s.allowInventory.value = true;
			s.allowPlace.value = true;
		} catch (Throwable ignored) {
		}
		// Abort any stuck dig before starting a new clear
		try {
			WintonMenuActions.forceCancel();
		} catch (Throwable ignored) {
		}
		boolean ok = w.getCommandManager().execute("sel cleararea");
		if (ok) {
			msg("winton.clear.started");
		}
		return ok;
	}

	public static void openSelectScreen() {
		// Freecam select removed — use stand / coords in Clear tab.
		msg("winton.clear.howto");
	}

	public static String statusLine() {
		IWinton w = WintonAPI.getProvider().getPrimaryWinton();
		int n = w == null ? 0 : w.getSelectionManager().getSelections().length;
		if (pendingPos1 != null) {
			return WintonLang.get("winton.clear.status_pos1") + " "
				+ pendingPos1.x + "," + pendingPos1.y + "," + pendingPos1.z + " | sel=" + n;
		}
		return WintonLang.get("winton.clear.status") + " sel=" + n;
	}

	public static Vec3 cameraPos() {
		if (WintonFreecam.isActive()) {
			return new Vec3(WintonFreecam.x(), WintonFreecam.y(), WintonFreecam.z());
		}
		return new Vec3(PathRenderer.posX(), PathRenderer.posY(), PathRenderer.posZ());
	}

	private static void msg(String key) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.player.displayClientMessage(WintonLang.text(key), false);
		}
	}

	private static void msg(String key, String extra) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.player.displayClientMessage(Component.literal(WintonLang.get(key) + " " + extra), false);
		}
	}
}
