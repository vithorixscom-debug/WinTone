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
import winton.api.command.datatypes.BlockById;
import winton.api.command.exception.CommandException;
import winton.api.command.helpers.TabCompleteHelper;
import winton.api.utils.BetterBlockPos;
import winton.cache.CachedChunk;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static winton.api.command.IWintonChatControl.FORCE_COMMAND_PREFIX;

public class FindCommand extends Command {

    public FindCommand(IWinton winton) {
        super(winton, "find");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMin(1);
        List<Block> toFind = new ArrayList<>();
        while (args.hasAny()) {
            toFind.add(args.getDatatypeFor(BlockById.INSTANCE));
        }
        BetterBlockPos origin = ctx.playerFeet();
        Component[] components = toFind.stream()
                .flatMap(block ->
                        ctx.worldData().getCachedWorld().getLocationsOf(
                                BuiltInRegistries.BLOCK.getKey(block).getPath(),
                                Integer.MAX_VALUE,
                                origin.x,
                                origin.y,
                                4
                        ).stream()
                )
                .map(BetterBlockPos::new)
                .map(this::positionToComponent)
                .toArray(Component[]::new);
        if (components.length > 0) {
            Arrays.asList(components).forEach(this::logDirect);
        } else {
            logDirect("No positions known, are you sure the blocks are cached?");
        }
    }

    private Component positionToComponent(BetterBlockPos pos) {
        String positionText = String.format("%s %s %s", pos.x, pos.y, pos.z);
        String command = String.format("%sgoal %s", FORCE_COMMAND_PREFIX, positionText);
        MutableComponent baseComponent = Component.literal(pos.toString());
        MutableComponent hoverComponent = Component.literal("Click to set goal to this position");
        baseComponent.setStyle(baseComponent.getStyle()
                .withColor(ChatFormatting.GRAY)
                .withInsertion(positionText)
                .withClickEvent(new ClickEvent.RunCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(hoverComponent)));
        return baseComponent;
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        return new TabCompleteHelper()
                .append(
                        CachedChunk.BLOCKS_TO_KEEP_TRACK_OF.stream()
                                .map(BuiltInRegistries.BLOCK::getKey)
                                .map(Object::toString)
                )
                .filterPrefixNamespaced(args.getString())
                .sortAlphabetically()
                .stream();
    }

    @Override
    public String getShortDesc() {
        return "Find positions of a certain block";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "The find command searches through WinTone's cache and attempts to find the location of the block.",
                "Tab completion will suggest only cached blocks and uncached blocks can not be found.",
                "",
                "Usage:",
                "> find <block> [...] - Try finding the listed blocks"
        );
    }
}
