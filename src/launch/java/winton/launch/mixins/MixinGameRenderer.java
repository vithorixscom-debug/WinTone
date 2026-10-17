/*
 * This file is part of Winton.
 *
 * Derived from Tweakeroo (https://github.com/maruohon/tweakeroo).
 * Copyright (c) masa. Modifications Copyright (c) WinTone contributors.
 * FOV override hook.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * LGPL-3.0-only. See LICENSE_TWEAKEROO.txt and NOTICE_TWEAKEROO.txt.
 */
package winton.launch.mixins;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import winton.tweakeroo.TweakerooBehavior;

@Mixin(GameRenderer.class)
public class MixinGameRenderer {
	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
	private void winton$tweakFov(Camera camera, float partialTick, boolean applyFov, CallbackInfoReturnable<Float> cir) {
		try {
			Float ov = TweakerooBehavior.overrideFov(cir.getReturnValue());
			if (ov != null) {
				cir.setReturnValue(ov);
			}
		} catch (Throwable ignored) {
		}
	}
}
