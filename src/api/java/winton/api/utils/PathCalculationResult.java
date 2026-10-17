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

package winton.api.utils;

import winton.api.pathing.calc.IPath;

import java.util.Objects;
import java.util.Optional;

public class PathCalculationResult {

    private final IPath path;
    private final Type type;

    public PathCalculationResult(Type type) {
        this(type, null);
    }

    public PathCalculationResult(Type type, IPath path) {
        Objects.requireNonNull(type);
        this.path = path;
        this.type = type;
    }

    public final Optional<IPath> getPath() {
        return Optional.ofNullable(this.path);
    }

    public final Type getType() {
        return this.type;
    }

    public enum Type {
        SUCCESS_TO_GOAL,
        SUCCESS_SEGMENT,
        FAILURE,
        CANCELLATION,
        EXCEPTION,
    }
}
