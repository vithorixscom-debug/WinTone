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
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * Built-in detached freecam for Clear mode. Body stays put; camera flies.
 */
public final class WintonFreecam {
	private static boolean active;
	private static double x;
	private static double y;
	private static double z;
	private static float yRot;
	private static float xRot;
	private static double speed = 1.15D;
	private static double lastCursorX = Double.NaN;
	private static double lastCursorY = Double.NaN;

	private static final ClientInput FROZEN_INPUT = new ClientInput() {
		@Override
		public void tick() {
			this.keyPresses = Input.EMPTY;
			this.moveVector = Vec2.ZERO;
		}
	};

	private WintonFreecam() {
	}

	public static boolean isActive() {
		return active;
	}

	public static double x() {
		return x;
	}

	public static double y() {
		return y;
	}

	public static double z() {
		return z;
	}

	public static float yRot() {
		return yRot;
	}

	public static float xRot() {
		return xRot;
	}

	public static void setSpeed(double s) {
		speed = Math.max(0.2D, Math.min(8.0D, s));
	}

	public static double speed() {
		return speed;
	}

	public static void enable() {
		Minecraft mc = Minecraft.getInstance();
		Entity cam = mc.getCameraEntity();
		if (cam == null) {
			cam = mc.player;
		}
		if (cam == null) {
			return;
		}
		Vec3 eye = cam.getEyePosition(1.0F);
		x = eye.x;
		y = eye.y;
		z = eye.z;
		yRot = cam.getYRot();
		xRot = cam.getXRot();
		resetCursorDelta();
		if (mc.player != null && !botDriving()) {
			mc.player.input = FROZEN_INPUT;
		}
		active = true;
	}

	public static void resetCursorDelta() {
		lastCursorX = Double.NaN;
		lastCursorY = Double.NaN;
	}

	public static void disable() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			// Always restore vanilla keyboard input; pathing re-applies PlayerMovementInput next tick if needed
			mc.player.input = new net.minecraft.client.player.KeyboardInput(mc.options);
		}
		active = false;
		resetCursorDelta();
	}

	/** Call every client tick while active. */
	public static void tick() {
		if (!active) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) {
			disable();
			return;
		}
		if (mc.player.input != FROZEN_INPUT && !botDriving()) {
			mc.player.input = FROZEN_INPUT;
		}
		if (!botDriving()) {
			FROZEN_INPUT.tick();
			mc.player.setDeltaMovement(0, mc.player.getDeltaMovement().y * 0.6D, 0);
		}
		applyLookFromCursor(mc);
		applyMoveFromKeys(mc);
	}

	/** While a path is running, the body keeps walking. The camera does not drive it. */
	private static boolean botDriving() {
		try {
			var primary = winton.api.WintonAPI.getProvider().getPrimaryWinton();
			return primary != null && primary.getPathingBehavior().isPathing();
		} catch (Throwable ignored) {
			return false;
		}
	}

	public static void applyToCamera(Camera camera) {
		if (!active) {
			return;
		}
		try {
			((WintonCameraAccess) camera).winton$setFreecam(x, y, z, yRot, xRot);
		} catch (Throwable t) {
			System.err.println("[WinTone] Freecam camera apply suppressed: " + t);
		}
	}

	private static void applyLookFromCursor(Minecraft mc) {
		long handle = mc.getWindow().handle();
		double[] cx = new double[1];
		double[] cy = new double[1];
		GLFW.glfwGetCursorPos(handle, cx, cy);
		if (!Double.isNaN(lastCursorX)) {
			double dx = cx[0] - lastCursorX;
			double dy = cy[0] - lastCursorY;
			if (Math.abs(dx) > 0.01D || Math.abs(dy) > 0.01D) {
				double sens = mc.options.sensitivity().get() * 0.6D + 0.2D;
				double mult = sens * sens * sens * 8.0D * 0.15D;
				yRot += (float) (dx * mult);
				xRot = Mth.clamp(xRot + (float) (dy * mult), -90.0F, 90.0F);
			}
		}
		lastCursorX = cx[0];
		lastCursorY = cy[0];
	}

	private static void applyMoveFromKeys(Minecraft mc) {
		var window = mc.getWindow();
		boolean forward = down(window, mc.options.keyUp) || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_W);
		boolean back = down(window, mc.options.keyDown) || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_S);
		boolean left = down(window, mc.options.keyLeft) || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_A);
		boolean right = down(window, mc.options.keyRight) || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_D);
		boolean up = down(window, mc.options.keyJump) || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_SPACE);
		boolean downKey = down(window, mc.options.keyShift) || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT);
		boolean sprint = down(window, mc.options.keySprint) || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL);

		float yawRad = yRot * ((float) Math.PI / 180F);
		double sin = Mth.sin(yawRad);
		double cos = Mth.cos(yawRad);

		double mx = 0;
		double mz = 0;
		if (forward) {
			mx += -sin;
			mz += cos;
		}
		if (back) {
			mx -= -sin;
			mz -= cos;
		}
		if (left) {
			mx += cos;
			mz += sin;
		}
		if (right) {
			mx -= cos;
			mz -= sin;
		}
		double my = 0;
		if (up) {
			my += 1;
		}
		if (downKey) {
			my -= 1;
		}

		double len = Math.sqrt(mx * mx + mz * mz);
		if (len > 1.0E-4) {
			mx /= len;
			mz /= len;
		}

		double step = speed * (sprint ? 3.2D : 1.0D);
		x += mx * step;
		y += my * step;
		z += mz * step;
	}

	private static boolean down(com.mojang.blaze3d.platform.Window window, net.minecraft.client.KeyMapping mapping) {
		if (mapping == null) {
			return false;
		}
		try {
			InputConstants.Key key = InputConstants.getKey(mapping.saveString());
			if (key.getType() != InputConstants.Type.KEYSYM) {
				return false;
			}
			return InputConstants.isKeyDown(window, key.getValue());
		} catch (Throwable ignored) {
			return false;
		}
	}

	/** Bridge implemented by MixinCamera. */
	public interface WintonCameraAccess {
		void winton$setFreecam(double x, double y, double z, float yRot, float xRot);
	}
}
