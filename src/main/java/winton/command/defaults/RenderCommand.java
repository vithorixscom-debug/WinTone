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
import winton.api.utils.BetterBlockPos;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class RenderCommand extends Command {

    public RenderCommand(IWinton winton) {
        super(winton, "render");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMax(0);
        BetterBlockPos origin = ctx.playerFeet();
        int renderDistance = (ctx.minecraft().options.renderDistance().get() + 1) * 16;
        ctx.minecraft().levelRenderer.setBlocksDirty(
                origin.x - renderDistance,
                ctx.world().getMinY(),
                origin.z - renderDistance,
                origin.x + renderDistance,
                ctx.world().getMaxY(),
                origin.z + renderDistance
        );
        logDirect("Done");
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Fix glitched chunks";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "The render command fixes glitched chunk rendering without having to reload all of them.",
                "",
                "Usage:",
                "> render"
        );
    }
}
