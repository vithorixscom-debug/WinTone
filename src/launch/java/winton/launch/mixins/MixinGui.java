/*
 * Copyright (c) masa. Modifications Copyright (c) WinTone contributors.
 * This file is part of Winton.
 *
 * Winton is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Based on Baritone (https://github.com/cabaletta/baritone), LGPL-3.0.
 */

package winton.launch.mixins;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import winton.minihud.MiniHudBehavior;

@Mixin(Gui.class)
public class MixinGui {

	@Shadow
	@Final
	private Minecraft minecraft;

	@Inject(method = "render", at = @At("RETURN"))
	private void winton$renderMiniHud(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
		try {
			MiniHudBehavior.renderHud(graphics, this.minecraft);
		} catch (Throwable t) {
			// never crash vanilla HUD
		}
	}
}
