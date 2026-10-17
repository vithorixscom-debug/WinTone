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

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class ForceCancelCommand extends Command {

    public ForceCancelCommand(IWinton winton) {
        super(winton, "forcecancel");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMax(0);
        IPathingBehavior pathingBehavior = winton.getPathingBehavior();
        pathingBehavior.cancelEverything();
        pathingBehavior.forceCancel();
        logDirect("ok force canceled");
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Force cancel";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Like cancel, but more forceful.",
                "",
                "Usage:",
                "> forcecancel"
        );
    }
}
