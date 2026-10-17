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

package winton.utils.player;

import winton.Winton;
import winton.api.cache.IWorldData;
import winton.api.utils.*;
import winton.api.utils.input.Input;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

/**
 * Implementation of {@link IPlayerContext} that provides information about the primary player.
 *
 * @author Brady
 * @since 11/12/2018
 */
public final class WintonPlayerContext implements IPlayerContext {

    private final Winton winton;
    private final Minecraft mc;
    private final IPlayerController playerController;

    public WintonPlayerContext(Winton winton, Minecraft mc) {
        this.winton = winton;
        this.mc = mc;
        this.playerController = new WintonPlayerController(mc);
    }

    @Override
    public Minecraft minecraft() {
        return this.mc;
    }

    @Override
    public LocalPlayer player() {
        return this.mc.player;
    }

    @Override
    public IPlayerController playerController() {
        return this.playerController;
    }

    @Override
    public Level world() {
        return this.mc.level;
    }

    @Override
    public IWorldData worldData() {
        return this.winton.getWorldProvider().getCurrentWorld();
    }

    @Override
    public BetterBlockPos viewerPos() {
        final Entity entity = this.mc.getCameraEntity();
        return entity == null ? this.playerFeet() : BetterBlockPos.from(entity.blockPosition());
    }

    @Override
    public Rotation playerRotations() {
        return this.winton.getLookBehavior().getEffectiveRotation().orElseGet(IPlayerContext.super::playerRotations);
    }

    @Override
    public HitResult objectMouseOver() {
        Entity entity = player();
        if (entity == null) {
            return null;
        }
        Rotation rot = playerRotations();
        try {
            Rotation aim = this.winton.getLookBehavior().interactionAim();
            if (aim != null) {
                rot = aim;
            }
        } catch (Throwable ignored) {
        }
        boolean sneak = entity.isCrouching();
        try {
            sneak = sneak || this.winton.getInputOverrideHandler().isInputForcedDown(Input.SNEAK);
        } catch (Throwable ignored) {
        }
        return RayTraceUtils.rayTraceTowards(entity, rot, playerController().getBlockReachDistance(), sneak);
    }
}
