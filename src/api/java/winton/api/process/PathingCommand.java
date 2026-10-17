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

package winton.api.process;

import winton.api.pathing.goals.Goal;

import java.util.Objects;

/**
 * @author leijurv
 */
public class PathingCommand {

    /**
     * The target goal, may be {@code null}.
     */
    public final Goal goal;

    /**
     * The command type.
     *
     * @see PathingCommandType
     */
    public final PathingCommandType commandType;

    /**
     * Create a new {@link PathingCommand}.
     *
     * @param goal        The target goal, may be {@code null}.
     * @param commandType The command type, cannot be {@code null}.
     * @throws NullPointerException if {@code commandType} is {@code null}.
     * @see Goal
     * @see PathingCommandType
     */
    public PathingCommand(Goal goal, PathingCommandType commandType) {
        Objects.requireNonNull(commandType);

        this.goal = goal;
        this.commandType = commandType;
    }

    @Override
    public String toString() {
        return commandType + " " + goal;
    }
}
