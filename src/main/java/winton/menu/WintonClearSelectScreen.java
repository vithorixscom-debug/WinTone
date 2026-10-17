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

import winton.api.WintonAPI;
import winton.api.utils.BetterBlockPos;
import winton.utils.IRenderer;
import winton.utils.PathRenderer;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.Collections;

/**
 * Built-in freecam area select: two corners by click. Never paths to the block.
 * Freecam auto-enables on open and disables on close / start clear.
 */
public final class WintonClearSelectScreen extends Screen {
	private Matrix4f projectionViewMatrix;
	private BlockPos hover;
	private BlockPos dragStart;

	public WintonClearSelectScreen() {
		super(WintonLang.text("winton.clear.select_title"));
	}

	@Override
	protected void init() {
		WintonFreecam.setSpeed(WintonMenuConfig.freecamSpeed());
		if (!WintonFreecam.isActive()) {
			WintonFreecam.enable();
		}
		if (minecraft != null) {
			minecraft.mouseHandler.grabMouse();
			WintonFreecam.resetCursorDelta();
		}
	}

	@Override
	public void removed() {
		WintonFreecam.disable();
	}

	@Override
	public void onClose() {
		WintonFreecam.disable();
		super.onClose();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		updateHover();
		super.render(graphics, mouseX, mouseY, partialTick);
		graphics.drawCenteredString(this.font, WintonLang.get("winton.clear.select_title"), this.width / 2, 12, 0xFFFFFF);
		graphics.drawCenteredString(this.font, WintonLang.get("winton.clear.select_hint1"), this.width / 2, 28, 0xA0E0A0);
		graphics.drawCenteredString(this.font, WintonLang.get("winton.clear.select_hint2"), this.width / 2, 42, 0xC0C0C0);
		graphics.drawCenteredString(this.font, WintonClearMode.statusLine(), this.width / 2, 56, 0xFFE080);
		if (hover != null) {
			graphics.drawCenteredString(this.font,
				"look: " + hover.getX() + " " + hover.getY() + " " + hover.getZ(),
				this.width / 2, this.height - 28, 0x80C0FF);
		}
	}

	private void updateHover() {
		hover = WintonClearMode.raycastCamera(256);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0) {
			dragStart = hover;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (hover == null) {
			return super.mouseReleased(event);
		}
		if (event.button() == 0) {
			if (dragStart != null && !dragStart.equals(hover)) {
				var w = WintonAPI.getProvider().getPrimaryWinton();
				if (w != null) {
					w.getSelectionManager().removeAllSelections();
					w.getSelectionManager().addSelection(BetterBlockPos.from(dragStart), BetterBlockPos.from(hover));
					WintonClearMode.resetPending();
					if (minecraft != null && minecraft.player != null) {
						minecraft.player.displayClientMessage(WintonLang.text("winton.clear.sel_ready"), false);
					}
				}
			} else {
				WintonClearMode.acceptCorner(BetterBlockPos.from(hover));
			}
		} else if (event.button() == 1) {
			if (WintonClearMode.pendingPos1() != null) {
				WintonClearMode.resetPending();
				if (minecraft != null && minecraft.player != null) {
					minecraft.player.displayClientMessage(WintonLang.text("winton.clear.pos1_cancel"), false);
				}
			} else {
				onClose();
			}
		}
		dragStart = null;
		return super.mouseReleased(event);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
			WintonClearMode.startClearArea();
			onClose();
			return true;
		}
		if (key == GLFW.GLFW_KEY_C) {
			WintonClearMode.clearSelections();
			return true;
		}
		if (key == GLFW.GLFW_KEY_ESCAPE) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	public void onWorldRender(PoseStack modelViewStack, Matrix4f projectionMatrix) {
		this.projectionViewMatrix = new Matrix4f(projectionMatrix);
		this.projectionViewMatrix.mul(modelViewStack.last().pose());
		this.projectionViewMatrix.invert();

		if (hover == null || minecraft == null) {
			return;
		}
		Entity e = minecraft.getCameraEntity();
		if (e == null) {
			return;
		}
		try {
			PathRenderer.drawManySelectionBoxes(modelViewStack, e, Collections.singletonList(hover), Color.CYAN);
			BetterBlockPos p1 = WintonClearMode.pendingPos1();
			BlockPos a = dragStart != null ? dragStart : (p1 != null ? new BlockPos(p1.x, p1.y, p1.z) : null);
			if (a != null && !a.equals(hover)) {
				BufferBuilder bufferBuilder = IRenderer.startLines(Color.ORANGE);
				BetterBlockPos aa = BetterBlockPos.from(a);
				BetterBlockPos bb = BetterBlockPos.from(hover);
				IRenderer.emitAABB(bufferBuilder, modelViewStack, new AABB(
					Math.min(aa.x, bb.x), Math.min(aa.y, bb.y), Math.min(aa.z, bb.z),
					Math.max(aa.x, bb.x) + 1, Math.max(aa.y, bb.y) + 1, Math.max(aa.z, bb.z) + 1
				), 2F);
				IRenderer.endLines(bufferBuilder, true);
			}
		} catch (Throwable ignored) {
		}
	}
}
