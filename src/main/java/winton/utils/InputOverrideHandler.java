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

package winton.utils;

import winton.Winton;
import winton.api.WintonAPI;
import winton.api.event.events.TickEvent;
import winton.api.utils.IInputOverrideHandler;
import winton.api.utils.input.Input;
import winton.behavior.Behavior;
import winton.menu.WintonFreecam;
import net.minecraft.client.player.KeyboardInput;

import java.util.HashMap;
import java.util.Map;

/**
 * An interface with the game's control system allowing the ability to
 * force down certain controls, having the same effect as if we were actually
 * physically forcing down the assigned key.
 *
 * @author Brady
 * @since 7/31/2018
 */
public final class InputOverrideHandler extends Behavior implements IInputOverrideHandler {

    private static final Input[] MOVEMENT_INPUTS = {
        Input.MOVE_FORWARD, Input.MOVE_BACK, Input.MOVE_LEFT, Input.MOVE_RIGHT, Input.SNEAK, Input.JUMP
    };

    /**
     * Maps inputs to whether or not we are forcing their state down.
     */
    private final Map<Input, Boolean> inputForceStateMap = new HashMap<>();

    private final BlockBreakHelper blockBreakHelper;
    private final BlockPlaceHelper blockPlaceHelper;

    public InputOverrideHandler(Winton winton) {
        super(winton);
        this.blockBreakHelper = new BlockBreakHelper(winton.getPlayerContext());
        this.blockPlaceHelper = new BlockPlaceHelper(winton.getPlayerContext());
    }

    /**
     * Returns whether or not we are forcing down the specified {@link Input}.
     *
     * @param input The input
     * @return Whether or not it is being forced down
     */
    @Override
    public final boolean isInputForcedDown(Input input) {
        return input == null ? false : this.inputForceStateMap.getOrDefault(input, false);
    }

    /**
     * Sets whether or not the specified {@link Input} is being forced down.
     *
     * @param input  The {@link Input}
     * @param forced Whether or not the state is being forced
     */
    @Override
    public final void setInputForceState(Input input, boolean forced) {
        this.inputForceStateMap.put(input, forced);
    }

    /**
     * Clears the override state for all keys
     */
    @Override
    public final void clearAllKeys() {
        this.inputForceStateMap.clear();
    }

    @Override
    public final void onTick(TickEvent event) {
        if (event.getType() == TickEvent.Type.OUT) {
            return;
        }
        // Freecam is a spectator camera. The body keeps pathing unless nothing is driving it.
        if (WintonFreecam.isActive() && !inControl()) {
            return;
        }
        boolean clickL = isInputForcedDown(Input.CLICK_LEFT);
        boolean clickR = isInputForcedDown(Input.CLICK_RIGHT);
        boolean moving = inControl();
        // Always tick break helper so stopBreakingBlock runs when click is released
        // (early-return used to leave destroy progress running → FPS crash after kill).
        if (clickL) {
            setInputForceState(Input.CLICK_RIGHT, false);
        }
        blockBreakHelper.tick(clickL);
        blockPlaceHelper.tick(clickR);
        if (!clickL && !clickR && !moving && ctx.player().input.getClass() != PlayerMovementInput.class) {
            return;
        }

        if (moving) {
            if (ctx.player().input.getClass() != PlayerMovementInput.class) {
                ctx.player().input = new PlayerMovementInput(this);
            }
        } else {
            if (ctx.player().input.getClass() == PlayerMovementInput.class) { // allow other movement inputs that aren't this one, e.g. for a freecam
                ctx.player().input = new KeyboardInput(ctx.minecraft().options);
            }
        }
        // only set it if it was previously incorrect
        // gotta do it this way, or else it constantly thinks you're beginning a double tap W sprint lol
    }

    private boolean inControl() {
        for (Input input : MOVEMENT_INPUTS) {
            if (isInputForcedDown(input)) {
                return true;
            }
        }
        // if we are not primary (a bot) we should set the movementinput even when idle (not pathing)
        return winton.getPathingBehavior().isPathing() || winton != WintonAPI.getProvider().getPrimaryWinton();
    }

    public BlockBreakHelper getBlockBreakHelper() {
        return blockBreakHelper;
    }
}
