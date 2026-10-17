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

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

public class CommandAlias extends Command {

    private final String shortDesc;
    public final String target;

    public CommandAlias(IWinton winton, List<String> names, String shortDesc, String target) {
        super(winton, names.toArray(new String[0]));
        this.shortDesc = shortDesc;
        this.target = target;
    }

    public CommandAlias(IWinton winton, String name, String shortDesc, String target) {
        super(winton, name);
        this.shortDesc = shortDesc;
        this.target = target;
    }

    @Override
    public void execute(String label, IArgConsumer args) {
        this.winton.getCommandManager().execute(String.format("%s %s", target, args.rawRest()));
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        return this.winton.getCommandManager().tabComplete(String.format("%s %s", target, args.rawRest()));
    }

    @Override
    public String getShortDesc() {
        return shortDesc;
    }

    @Override
    public List<String> getLongDesc() {
        return Collections.singletonList(String.format("This command is an alias, for: %s ...", target));
    }
}
