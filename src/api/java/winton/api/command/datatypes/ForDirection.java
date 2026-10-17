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

import winton.api.command.exception.CommandException;
import winton.api.command.helpers.TabCompleteHelper;
import java.util.Locale;
import java.util.stream.Stream;
import net.minecraft.core.Direction;

public enum ForDirection implements IDatatypeFor<Direction> {
    INSTANCE;

    @Override
    public Direction get(IDatatypeContext ctx) throws CommandException {
        return Direction.valueOf(ctx.getConsumer().getString().toUpperCase(Locale.US));
    }

    @Override
    public Stream<String> tabComplete(IDatatypeContext ctx) throws CommandException {
        return new TabCompleteHelper()
                .append(Stream.of(Direction.values())
                        .map(Direction::getName).map(String::toLowerCase))
                .filterPrefix(ctx.getConsumer().getString())
                .stream();
    }
}
