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

package winton.launch.mixins;

import winton.Winton;
import winton.api.IWinton;
import winton.api.WintonAPI;
import winton.menu.WintonFreecam;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class MixinCamera implements WintonFreecam.WintonCameraAccess {
	@Shadow
	protected abstract void setPosition(double x, double y, double z);

	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Inject(method = "setup", at = @At("TAIL"))
	private void winton$freecam(CallbackInfo ci) {
		if (WintonFreecam.isActive()) {
			WintonFreecam.applyToCamera((Camera) (Object) this);
			return;
		}
		IWinton iwinton = WintonAPI.getProvider().getPrimaryWinton();
		if (!(iwinton instanceof Winton winton)) {
			return;
		}
		float partial = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
		float[] look = new float[2];
		if (winton.getLookBehavior().sampleCamera(partial, look)) {
			setRotation(look[0], look[1]);
		}
	}

	@Override
	public void winton$setFreecam(double x, double y, double z, float yRot, float xRot) {
		setRotation(yRot, xRot);
		setPosition(x, y, z);
	}
}
