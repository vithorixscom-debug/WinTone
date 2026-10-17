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
import winton.api.IWinton;
import winton.api.event.events.RotationMoveEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * @author Brady
 * @since 9/10/2018
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity extends Entity {

    /**
     * Event called to override the movement direction when jumping
     */
    @Unique
    private RotationMoveEvent jumpRotationEvent;

    @Unique
    private RotationMoveEvent elytraRotationEvent;

    private MixinLivingEntity(EntityType<?> entityTypeIn, Level worldIn) {
        super(entityTypeIn, worldIn);
    }

    @Inject(
            method = "jumpFromGround",
            at = @At("HEAD")
    )
    private void preMoveRelative(CallbackInfo ci) {
        this.getWinton().ifPresent(winton -> {
            this.jumpRotationEvent = new RotationMoveEvent(RotationMoveEvent.Type.JUMP, this.getYRot(), this.getXRot());
            winton.getGameEventHandler().onPlayerRotationMove(this.jumpRotationEvent);
        });
    }

    @Redirect(
            method = "jumpFromGround",
            at = @At(
                    value = "INVOKE",
                    target = "net/minecraft/world/entity/LivingEntity.getYRot()F"
            )
    )
    private float overrideYaw(LivingEntity self) {
        if (self instanceof LocalPlayer && WintonAPI.getProvider().getWintonForPlayer((LocalPlayer) (Object) this) != null) {
            return this.jumpRotationEvent.getYaw();
        }
        return self.getYRot();
    }

    @Inject(
            method = "updateFallFlyingMovement",
            at = @At(
                    value = "INVOKE",
                    target = "net/minecraft/world/entity/LivingEntity.getLookAngle()Lnet/minecraft/world/phys/Vec3;"
            )
    )
    private void onPreElytraMove(Vec3 direction, final CallbackInfoReturnable<Vec3> cir) {
        this.getWinton().ifPresent(winton -> {
            this.elytraRotationEvent = new RotationMoveEvent(RotationMoveEvent.Type.MOTION_UPDATE, this.getYRot(), this.getXRot());
            winton.getGameEventHandler().onPlayerRotationMove(this.elytraRotationEvent);
            this.setYRot(this.elytraRotationEvent.getYaw());
            this.setXRot(this.elytraRotationEvent.getPitch());
        });
    }

    @Inject(
            method = "travelFallFlying",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V",
                    shift = At.Shift.AFTER
            )
    )
    private void onPostElytraMove(final CallbackInfo ci) {
        if (this.elytraRotationEvent != null) {
            this.setYRot(this.elytraRotationEvent.getOriginal().getYaw());
            this.setXRot(this.elytraRotationEvent.getOriginal().getPitch());
            this.elytraRotationEvent = null;
        }
    }

    @Unique
    private Optional<IWinton> getWinton() {
        // noinspection ConstantConditions
        if (LocalPlayer.class.isInstance(this)) {
            return Optional.ofNullable(WintonAPI.getProvider().getWintonForPlayer((LocalPlayer) (Object) this));
        } else {
            return Optional.empty();
        }
    }
}
