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

package winton.api.schematic.format;

import winton.api.schematic.ISchematic;
import winton.api.schematic.IStaticSchematic;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * The base of a {@link ISchematic} file format
 *
 * @author Brady
 * @since 12/23/2019
 */
public interface ISchematicFormat {

    /**
     * @return The parser for creating schematics of this format
     */
    IStaticSchematic parse(InputStream input) throws IOException;

    /**
     * @param file The file to check against
     * @return Whether or not the specified file matches this schematic format
     */
    boolean isFileType(File file);

    /**
     * @return A list of file extensions used by this format
     */
    List<String> getFileExtensions();
}
