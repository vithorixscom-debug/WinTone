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
import winton.api.behavior.IPathingBehavior;
import winton.api.command.Command;
import winton.api.command.argument.IArgConsumer;
import winton.api.command.exception.CommandException;
import winton.api.command.exception.CommandInvalidStateException;
import winton.api.pathing.calc.IPathingControlManager;
import winton.api.process.IWintonProcess;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class ETACommand extends Command {

    public ETACommand(IWinton winton) {
        super(winton, "eta");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMax(0);
        IPathingControlManager pathingControlManager = winton.getPathingControlManager();
        IWintonProcess process = pathingControlManager.mostRecentInControl().orElse(null);
        if (process == null) {
            throw new CommandInvalidStateException("No process in control");
        }
        IPathingBehavior pathingBehavior = winton.getPathingBehavior();

        double ticksRemainingInSegment = pathingBehavior.ticksRemainingInSegment().orElse(Double.NaN);
        double ticksRemainingInGoal = pathingBehavior.estimatedTicksToGoal().orElse(Double.NaN);

        logDirect(String.format(
                "Next segment: %.1fs (%.0f ticks)\n" +
                        "Goal: %.1fs (%.0f ticks)",
                ticksRemainingInSegment / 20, // we just assume tps is 20, it isn't worth the effort that is needed to calculate it exactly
                ticksRemainingInSegment,
                ticksRemainingInGoal / 20,
                ticksRemainingInGoal
        ));
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "View the current ETA";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "The ETA command provides information about the estimated time until the next segment.",
                "and the goal",
                "",
                "Be aware that the ETA to your goal is really unprecise",
                "",
                "Usage:",
                "> eta - View ETA, if present"
        );
    }
}
