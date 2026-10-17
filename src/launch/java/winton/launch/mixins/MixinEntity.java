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
 * Winton is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Winton.  If not, see <https://www.gnu.org/licenses/>.
 */

package winton.launch.mixins;

import winton.api.WintonAPI;
import winton.api.event.events.RotationMoveEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class MixinEntity {

    @Shadow
    private float yRot;

    @Shadow
    private float xRot;

    @Unique
    private RotationMoveEvent motionUpdateRotationEvent;

    @Inject(
            method = "turn",
            at = @At("HEAD"),
            cancellable = true
    )
    private void winton$freecamIgnoresMouse(double yRot, double xRot, CallbackInfo info) {
        if (winton.menu.WintonFreecam.isActive() && (Object) this instanceof LocalPlayer) {
            info.cancel();
        }
    }

    @Inject(
            method = "moveRelative",
            at = @At("HEAD")
    )
    private void moveRelativeHead(CallbackInfo info) {
        // noinspection ConstantConditions
        if (!LocalPlayer.class.isInstance(this) || WintonAPI.getProvider().getWintonForPlayer((LocalPlayer) (Object) this) == null) {
            return;
        }
        this.motionUpdateRotationEvent = new RotationMoveEvent(RotationMoveEvent.Type.MOTION_UPDATE, this.yRot, this.xRot);
        WintonAPI.getProvider().getWintonForPlayer((LocalPlayer) (Object) this).getGameEventHandler().onPlayerRotationMove(motionUpdateRotationEvent);
        this.yRot = this.motionUpdateRotationEvent.getYaw();
        this.xRot = this.motionUpdateRotationEvent.getPitch();
    }

    @Inject(
            method = "moveRelative",
            at = @At("RETURN")
    )
    private void moveRelativeReturn(CallbackInfo info) {
        if (this.motionUpdateRotationEvent != null) {
            this.yRot = this.motionUpdateRotationEvent.getOriginal().getYaw();
            this.xRot = this.motionUpdateRotationEvent.getOriginal().getPitch();
            this.motionUpdateRotationEvent = null;
        }
    }
}
