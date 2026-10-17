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

package winton.command.defaults;

import winton.api.IWinton;
import winton.api.command.Command;
import winton.api.command.argument.IArgConsumer;
import winton.api.command.datatypes.RelativeGoalXZ;
import winton.api.command.exception.CommandException;
import winton.api.pathing.goals.GoalXZ;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class ExploreCommand extends Command {

    public ExploreCommand(IWinton winton) {
        super(winton, "explore");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        if (args.hasAny()) {
            args.requireExactly(2);
        } else {
            args.requireMax(0);
        }
        GoalXZ goal = args.hasAny()
                ? args.getDatatypePost(RelativeGoalXZ.INSTANCE, ctx.playerFeet())
                : new GoalXZ(ctx.playerFeet());
        winton.getExploreProcess().explore(goal.getX(), goal.getZ());
        logDirect(String.format("Exploring from %s", goal.toString()));
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        if (args.hasAtMost(2)) {
            return args.tabCompleteDatatype(RelativeGoalXZ.INSTANCE);
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Explore things";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Tell WinTone to explore randomly. If you used explorefilter before this, it will be applied.",
                "",
                "Usage:",
                "> explore - Explore from your current position.",
                "> explore <x> <z> - Explore from the specified X and Z position."
        );
    }
}
