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
import winton.api.cache.IWaypoint;
import winton.api.command.Command;
import winton.api.command.argument.IArgConsumer;
import winton.api.command.datatypes.ForWaypoints;
import winton.api.command.exception.CommandException;
import winton.api.command.exception.CommandInvalidStateException;
import winton.api.utils.BetterBlockPos;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class FarmCommand extends Command {

    public FarmCommand(IWinton winton) {
        super(winton, "farm");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMax(2);
        int range = 0;
        BetterBlockPos origin = null;
        //range
        if (args.has(1)) {
            range = args.getAs(Integer.class);
        }
        //waypoint
        if (args.has(1)) {
            IWaypoint[] waypoints = args.getDatatypeFor(ForWaypoints.INSTANCE);
            IWaypoint waypoint = null;
            switch (waypoints.length) {
                case 0:
                    throw new CommandInvalidStateException("No waypoints found");
                case 1:
                    waypoint = waypoints[0];
                    break;
                default:
                    throw new CommandInvalidStateException("Multiple waypoints were found");
            }
            origin = waypoint.getLocation();
        }

        winton.getFarmProcess().farm(range, origin);
        logDirect("Farming");
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Farm nearby crops";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "The farm command starts farming nearby plants. It harvests mature crops and plants new ones.",
                "",
                "Usage:",
                "> farm - farms every crop it can find.",
                "> farm <range> - farm crops within range from the starting position.",
                "> farm <range> <waypoint> - farm crops within range from waypoint."
        );
    }
}
