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
import winton.api.event.events.PlayerUpdateEvent;
import winton.api.event.events.SprintStateEvent;
import winton.api.event.events.type.EventState;
import winton.behavior.LookBehavior;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * @author Brady
 * @since 8/1/2018
 */
@Mixin(LocalPlayer.class)
public class MixinClientPlayerEntity {
    @Unique
    private static final MethodHandle MAY_FLY = winton$resolveMayFly();

    @Unique
    private static MethodHandle winton$resolveMayFly() {
        try {
            var lookup = MethodHandles.publicLookup();
            return lookup.findVirtual(LocalPlayer.class, "mayFly", MethodType.methodType(boolean.class));
        } catch (NoSuchMethodException e) {
            return null;
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Inject(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "net/minecraft/client/player/AbstractClientPlayer.tick()V",
                    shift = At.Shift.AFTER
            )
    )
    private void onPreUpdate(CallbackInfo ci) {
        IWinton winton = WintonAPI.getProvider().getWintonForPlayer((LocalPlayer) (Object) this);
        if (winton != null) {
            winton.getGameEventHandler().onPlayerUpdate(new PlayerUpdateEvent(EventState.PRE));
        }
    }

    @Redirect(
            method = "aiStep",
            at = @At(
                    value = "FIELD",
                    target = "net/minecraft/world/entity/player/Abilities.mayfly:Z"
            )
    )
    @Group(name = "mayFly", min = 1, max = 1)
    private boolean isAllowFlying(Abilities capabilities) {
        IWinton winton = WintonAPI.getProvider().getWintonForPlayer((LocalPlayer) (Object) this);
        if (winton == null) {
            return capabilities.mayfly;
        }
        return !winton.getPathingBehavior().isPathing() && capabilities.mayfly;
    }

    @Redirect(
        method = "aiStep",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;mayFly()Z"
        )
    )
    @Group(name = "mayFly", min = 1, max = 1)
    private boolean onMayFlyNeoforge(LocalPlayer instance) throws Throwable {
        IWinton winton = WintonAPI.getProvider().getWintonForPlayer((LocalPlayer) (Object) this);
        if (winton == null) {
            return (boolean) MAY_FLY.invokeExact(instance);
        }
        return !winton.getPathingBehavior().isPathing() && (boolean) MAY_FLY.invokeExact(instance);
    }

    @Redirect(
            method = "aiStep",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Input;sprint()Z"
            )
    )
    private boolean redirectSprintInput(final Input instance) {
        IWinton winton = WintonAPI.getProvider().getWintonForPlayer((LocalPlayer) (Object) this);
        if (winton == null) {
            return instance.sprint();
        }
        SprintStateEvent event = new SprintStateEvent();
        winton.getGameEventHandler().onPlayerSprintState(event);
        if (event.getState() != null) {
            return event.getState();
        }
        if (winton != WintonAPI.getProvider().getPrimaryWinton()) {
            // hitting control shouldn't make all bots sprint
            return false;
        }
        return instance.sprint();
    }

    @Inject(
            method = "rideTick",
            at = @At(
                    value = "HEAD"
            )
    )
    private void updateRidden(CallbackInfo cb) {
        IWinton winton = WintonAPI.getProvider().getWintonForPlayer((LocalPlayer) (Object) this);
        if (winton != null) {
            ((LookBehavior) winton.getLookBehavior()).pig();
        }
    }

    @Redirect(
            method = "aiStep",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;tryToStartFallFlying()Z"
            )
    )
    private boolean tryToStartFallFlying(final LocalPlayer instance) {
        IWinton winton = WintonAPI.getProvider().getWintonForPlayer(instance);
        if (winton != null && winton.getPathingBehavior().isPathing()) {
            return false;
        }
        return instance.tryToStartFallFlying();
    }
}
