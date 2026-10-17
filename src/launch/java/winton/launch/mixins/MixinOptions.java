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

import winton.menu.WintonMenuKeys;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Options.class)
public class MixinOptions {
	@Mutable
	@Shadow
	@Final
	public KeyMapping[] keyMappings;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void winton$registerMenuKey(CallbackInfo ci) {
		this.keyMappings = WintonMenuKeys.withMenu(this.keyMappings);
	}

	/** Register before options.txt is applied so the bind is remembered. */
	@Inject(method = "load", at = @At("HEAD"))
	private void winton$beforeLoad(CallbackInfo ci) {
		this.keyMappings = WintonMenuKeys.withMenu(this.keyMappings);
	}

	@Inject(method = "load", at = @At("RETURN"))
	private void winton$afterLoad(CallbackInfo ci) {
		WintonMenuKeys.applySavedBinding();
		WintonMenuKeys.persistFromMapping();
	}

	@Inject(method = "save", at = @At("RETURN"))
	private void winton$afterSave(CallbackInfo ci) {
		WintonMenuKeys.persistFromMapping();
	}
}
