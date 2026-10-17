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

package winton.api.command.datatypes;

import winton.api.command.argument.IArgConsumer;
import winton.api.command.exception.CommandException;
import winton.api.pathing.goals.Goal;
import winton.api.pathing.goals.GoalBlock;
import winton.api.pathing.goals.GoalXZ;
import winton.api.pathing.goals.GoalYLevel;
import winton.api.utils.BetterBlockPos;

import java.util.stream.Stream;

public enum RelativeGoal implements IDatatypePost<Goal, BetterBlockPos> {
    INSTANCE;

    @Override
    public Goal apply(IDatatypeContext ctx, BetterBlockPos origin) throws CommandException {
        if (origin == null) {
            origin = BetterBlockPos.ORIGIN;
        }

        final IArgConsumer consumer = ctx.getConsumer();

        GoalBlock goalBlock = consumer.peekDatatypePostOrNull(RelativeGoalBlock.INSTANCE, origin);
        if (goalBlock != null) {
            return goalBlock;
        }

        GoalXZ goalXZ = consumer.peekDatatypePostOrNull(RelativeGoalXZ.INSTANCE, origin);
        if (goalXZ != null) {
            return goalXZ;
        }

        GoalYLevel goalYLevel = consumer.peekDatatypePostOrNull(RelativeGoalYLevel.INSTANCE, origin);
        if (goalYLevel != null) {
            return goalYLevel;
        }

        // when the user doesn't input anything, default to the origin
        return new GoalBlock(origin);
    }

    @Override
    public Stream<String> tabComplete(IDatatypeContext ctx) {
        return ctx.getConsumer().tabCompleteDatatype(RelativeCoordinate.INSTANCE);
    }
}
