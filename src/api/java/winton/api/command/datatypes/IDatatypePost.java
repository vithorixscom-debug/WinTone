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

import java.util.function.Function;

/**
 * An {@link IDatatype} which acts as a {@link Function}, in essence. The only difference
 * is that it requires an {@link IDatatypeContext} to be provided due to the expectation that
 * implementations of {@link IDatatype} are singletons.
 */
public interface IDatatypePost<T, O> extends IDatatype {

    /**
     * Takes the expected input and transforms it based on the value held by {@code original}. If {@code original}
     * is null, it is expected that the implementation of this method has a case to handle it, such that a
     * {@link NullPointerException} will never be thrown as a result.
     *
     * @param ctx      The datatype context
     * @param original The transformable value
     * @return The transformed value
     */
    T apply(IDatatypeContext ctx, O original) throws CommandException;
}
