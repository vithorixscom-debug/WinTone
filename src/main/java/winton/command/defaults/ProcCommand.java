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
import winton.api.command.exception.CommandInvalidStateException;
import winton.api.pathing.calc.IPathingControlManager;
import winton.api.process.IWintonProcess;
import winton.api.process.PathingCommand;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class ProcCommand extends Command {

    public ProcCommand(IWinton winton) {
        super(winton, "proc");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMax(0);
        IPathingControlManager pathingControlManager = winton.getPathingControlManager();
        IWintonProcess process = pathingControlManager.mostRecentInControl().orElse(null);
        if (process == null) {
            throw new CommandInvalidStateException("No process in control");
        }
        logDirect(String.format(
                "Class: %s\n" +
                        "Priority: %f\n" +
                        "Temporary: %b\n" +
                        "Display name: %s\n" +
                        "Last command: %s",
                process.getClass().getTypeName(),
                process.priority(),
                process.isTemporary(),
                process.displayName(),
                pathingControlManager
                        .mostRecentCommand()
                        .map(PathingCommand::toString)
                        .orElse("None")
        ));
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "View process state information";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "The proc command provides miscellaneous information about the process currently controlling WinTone.",
                "",
                "You are not expected to understand this if you aren't familiar with how WinTone works.",
                "",
                "Usage:",
                "> proc - View process information, if present"
        );
    }
}
