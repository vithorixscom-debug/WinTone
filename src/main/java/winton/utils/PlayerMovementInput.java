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

import winton.api.utils.input.Input;
import net.minecraft.client.player.ClientInput;
import net.minecraft.world.phys.Vec2;

public class PlayerMovementInput extends ClientInput {

    private final InputOverrideHandler handler;

    /** Smoothed strafe / forward — kills 0↔1 keyboard snapping. */
    private float smoothLeft;
    private float smoothForward;

    PlayerMovementInput(InputOverrideHandler handler) {
        this.handler = handler;
    }

    @Override
    public void tick() {
        float wantLeft = 0.0F;
        float wantForward = 0.0F;
        boolean jumping = handler.isInputForcedDown(Input.JUMP);

        boolean up = handler.isInputForcedDown(Input.MOVE_FORWARD);
        if (up) {
            wantForward++;
        }

        boolean down = handler.isInputForcedDown(Input.MOVE_BACK);
        if (down) {
            wantForward--;
        }

        boolean left = handler.isInputForcedDown(Input.MOVE_LEFT);
        if (left) {
            wantLeft++;
        }

        boolean right = handler.isInputForcedDown(Input.MOVE_RIGHT);
        if (right) {
            wantLeft--;
        }

        boolean sneaking = handler.isInputForcedDown(Input.SNEAK);
        if (sneaking) {
            wantLeft *= 0.3F;
            wantForward *= 0.3F;
        }

        // Ease into / out of presses. Attack (press) a bit faster than release
        // so starts feel responsive but stops don't telegraph.
        float attack = 0.92F;
        float release = 0.7F;
        this.smoothLeft = approach(this.smoothLeft, wantLeft, wantLeft == 0F ? release : attack);
        this.smoothForward = approach(this.smoothForward, wantForward, wantForward == 0F ? release : attack);

        // Deadzone so we fully stop
        float outLeft = Math.abs(this.smoothLeft) < 0.02F ? 0F : this.smoothLeft;
        float outForward = Math.abs(this.smoothForward) < 0.02F ? 0F : this.smoothForward;
        this.moveVector = new Vec2(outLeft, outForward);

        boolean sprinting = handler.isInputForcedDown(Input.SPRINT);

        this.keyPresses = new net.minecraft.world.entity.player.Input(up, down, left, right, jumping, sneaking, sprinting);
    }

    private static float approach(float current, float target, float factor) {
        return current + (target - current) * factor;
    }
}
