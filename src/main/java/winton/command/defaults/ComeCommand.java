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
import winton.api.command.exception.CommandException;
import winton.api.pathing.goals.GoalBlock;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class ComeCommand extends Command {

    public ComeCommand(IWinton winton) {
        super(winton, "come");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMax(0);
        winton.getCustomGoalProcess().setGoalAndPath(new GoalBlock(ctx.viewerPos()));
        logDirect("Coming");
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Start heading towards your camera";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "The come command tells WinTone to head towards your camera.",
                "",
                "This can be useful in hacked clients where freecam doesn't move your player position.",
                "",
                "Usage:",
                "> come"
        );
    }
}
