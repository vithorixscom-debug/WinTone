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

package winton.pathing.movement;

import winton.api.pathing.movement.MovementStatus;
import winton.api.utils.Rotation;
import winton.api.utils.input.Input;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class MovementState {

    private MovementStatus status;
    private MovementTarget target = new MovementTarget();
    private final Map<Input, Boolean> inputState = new HashMap<>();

    public MovementState setStatus(MovementStatus status) {
        this.status = status;
        return this;
    }

    public MovementStatus getStatus() {
        return status;
    }

    public MovementTarget getTarget() {
        return this.target;
    }

    public MovementState setTarget(MovementTarget target) {
        this.target = target;
        return this;
    }

    public MovementState setInput(Input input, boolean forced) {
        this.inputState.put(input, forced);
        return this;
    }

    public Map<Input, Boolean> getInputStates() {
        return this.inputState;
    }

    public static class MovementTarget {

        /**
         * Yaw and pitch angles that must be matched
         */
        public Rotation rotation;

        /**
         * Whether or not this target must force rotations.
         * <p>
         * {@code true} if we're trying to place or break blocks, {@code false} if we're trying to look at the movement location
         */
        private boolean forceRotations;

        public MovementTarget() {
            this(null, false);
        }

        public MovementTarget(Rotation rotation, boolean forceRotations) {
            this.rotation = rotation;
            this.forceRotations = forceRotations;
        }

        public final Optional<Rotation> getRotation() {
            return Optional.ofNullable(this.rotation);
        }

        public boolean hasToForceRotations() {
            return this.forceRotations;
        }
    }
}
