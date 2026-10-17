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
import winton.api.command.datatypes.RelativeFile;
import winton.api.command.exception.CommandException;
import winton.api.command.exception.CommandInvalidStateException;
import winton.api.command.exception.CommandInvalidTypeException;
import com.google.gson.JsonSyntaxException;

import java.io.File;
import java.nio.file.NoSuchFileException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class ExploreFilterCommand extends Command {

    public ExploreFilterCommand(IWinton winton) {
        super(winton, "explorefilter");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMax(2);
        File file = args.getDatatypePost(RelativeFile.INSTANCE, ctx.minecraft().gameDirectory.getAbsoluteFile().getParentFile());
        boolean invert = false;
        if (args.hasAny()) {
            if (args.getString().equalsIgnoreCase("invert")) {
                invert = true;
            } else {
                throw new CommandInvalidTypeException(args.consumed(), "either \"invert\" or nothing");
            }
        }
        try {
            winton.getExploreProcess().applyJsonFilter(file.toPath().toAbsolutePath(), invert);
        } catch (NoSuchFileException e) {
            throw new CommandInvalidStateException("File not found");
        } catch (JsonSyntaxException e) {
            throw new CommandInvalidStateException("Invalid JSON syntax");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        logDirect(String.format("Explore filter applied. Inverted: %s", Boolean.toString(invert)));
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        if (args.hasExactlyOne()) {
            return RelativeFile.tabComplete(args, RelativeFile.gameDir(ctx.minecraft()));
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Explore chunks from a json";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Apply an explore filter before using explore, which tells the explore process which chunks have been explored/not explored.",
                "",
                "The JSON file will follow this format: [{\"x\":0,\"z\":0},...]",
                "",
                "If 'invert' is specified, the chunks listed will be considered NOT explored, rather than explored.",
                "",
                "Usage:",
                "> explorefilter <path> [invert] - Load the JSON file referenced by the specified path. If invert is specified, it must be the literal word 'invert'."
        );
    }
}
