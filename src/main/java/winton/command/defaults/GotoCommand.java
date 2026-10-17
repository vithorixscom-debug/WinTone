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
import winton.api.command.datatypes.ForBlockOptionalMeta;
import winton.api.command.datatypes.RelativeCoordinate;
import winton.api.command.datatypes.RelativeGoal;
import winton.api.command.exception.CommandException;
import winton.api.pathing.goals.Goal;
import winton.api.utils.BetterBlockPos;
import winton.api.utils.BlockOptionalMeta;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class GotoCommand extends Command {

    protected GotoCommand(IWinton winton) {
        super(winton, "goto");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        // If we have a numeric first argument, then parse arguments as coordinates.
        // Note: There is no reason to want to go where you're already at so there
        // is no need to handle the case of empty arguments.
        if (args.peekDatatypeOrNull(RelativeCoordinate.INSTANCE) != null) {
            args.requireMax(3);
            BetterBlockPos origin = ctx.playerFeet();
            Goal goal = args.getDatatypePost(RelativeGoal.INSTANCE, origin);
            logDirect(String.format("Going to: %s", goal.toString()));
            winton.getCustomGoalProcess().setGoalAndPath(goal);
            return;
        }
        args.requireMax(1);
        BlockOptionalMeta destination = args.getDatatypeFor(ForBlockOptionalMeta.INSTANCE);
        winton.getGetToBlockProcess().getToBlock(destination);
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        // since it's either a goal or a block, I don't think we can tab complete properly?
        // so just tab complete for the block variant
        args.requireMax(1);
        return args.tabCompleteDatatype(ForBlockOptionalMeta.INSTANCE);
    }

    @Override
    public String getShortDesc() {
        return "Go to a coordinate or block";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "The goto command tells WinTone to head towards a given goal or block.",
                "",
                "Wherever a coordinate is expected, you can use ~ just like in regular Minecraft commands. Or, you can just use regular numbers.",
                "",
                "Usage:",
                "> goto <block> - Go to a block, wherever it is in the world",
                "> goto <y> - Go to a Y level",
                "> goto <x> <z> - Go to an X,Z position",
                "> goto <x> <y> <z> - Go to an X,Y,Z position"
        );
    }
}
