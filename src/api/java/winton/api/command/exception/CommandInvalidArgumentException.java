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

public abstract class CommandInvalidArgumentException extends CommandErrorMessageException {

    public final ICommandArgument arg;

    protected CommandInvalidArgumentException(ICommandArgument arg, String message) {
        super(formatMessage(arg, message));
        this.arg = arg;
    }

    protected CommandInvalidArgumentException(ICommandArgument arg, String message, Throwable cause) {
        super(formatMessage(arg, message), cause);
        this.arg = arg;
    }

    private static String formatMessage(ICommandArgument arg, String message) {
        return String.format(
                "Error at argument #%s: %s",
                arg.getIndex() == -1 ? "<unknown>" : Integer.toString(arg.getIndex() + 1),
                message
        );
    }
}
