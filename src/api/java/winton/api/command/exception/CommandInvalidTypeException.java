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

package winton.api.command.exception;

import winton.api.command.argument.ICommandArgument;

public class CommandInvalidTypeException extends CommandInvalidArgumentException {

    public CommandInvalidTypeException(ICommandArgument arg, String expected) {
        super(arg, String.format("Expected %s", expected));
    }

    public CommandInvalidTypeException(ICommandArgument arg, String expected, Throwable cause) {
        super(arg, String.format("Expected %s", expected), cause);
    }

    public CommandInvalidTypeException(ICommandArgument arg, String expected, String got) {
        super(arg, String.format("Expected %s, but got %s instead", expected, got));
    }

    public CommandInvalidTypeException(ICommandArgument arg, String expected, String got, Throwable cause) {
        super(arg, String.format("Expected %s, but got %s instead", expected, got), cause);
    }
}
