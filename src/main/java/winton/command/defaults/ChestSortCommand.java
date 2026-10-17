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
 * Based on Baritone (https://github.com/cabaletta/baritone), LGPL-3.0.
 */

package winton.command.defaults;

import winton.api.IWinton;
import winton.api.command.Command;
import winton.api.command.argument.IArgConsumer;
import winton.api.command.exception.CommandException;
import winton.api.command.exception.CommandInvalidStateException;
import winton.api.command.helpers.TabCompleteHelper;
import winton.api.process.IChestSortProcess;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

public class ChestSortCommand extends Command {

	public ChestSortCommand(IWinton winton) {
		super(winton, "chestsort", "sortchests", "sort");
	}

	@Override
	public void execute(String label, IArgConsumer args) throws CommandException {
		IChestSortProcess sort = winton.getChestSortProcess();
		if (!args.hasAny()) {
			sort.sortRadius(16);
			return;
		}
		String action = args.getString().toLowerCase(Locale.ROOT);
		switch (action) {
			case "radius", "r" -> {
				int r = args.hasAny() ? args.getAs(Integer.class) : 16;
				sort.sortRadius(r);
			}
			case "sel", "selection", "area" -> sort.sortSelection();
			case "mark" -> {
				if (!sort.markLookChest(Set.of())) {
					throw new CommandInvalidStateException("Look at a chest and hold the item that belongs there");
				}
			}
			case "clearmarks", "unmark" -> sort.clearMarks();
			case "stop", "cancel" -> sort.onLostControl();
			default -> throw new CommandInvalidStateException(
					"Usage: chestsort | chestsort radius <n> | chestsort sel | chestsort mark | chestsort clearmarks");
		}
	}

	@Override
	public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
		if (args.hasExactlyOne()) {
			return new TabCompleteHelper()
					.append("radius", "sel", "mark", "clearmarks", "stop")
					.filterPrefix(args.getString())
					.stream();
		}
		return Stream.empty();
	}

	@Override
	public String getShortDesc() {
		return "Sort items across nearby chests";
	}

	@Override
	public List<String> getLongDesc() {
		return Arrays.asList(
				"Sorts chests in a radius or selection.",
				"",
				"Chests keep their item types (learned from contents).",
				"Mark a chest while holding an item to force its category.",
				"",
				"Usage:",
				"> chestsort — radius 16",
				"> chestsort radius <n>",
				"> chestsort sel — current selection",
				"> chestsort mark — look at chest, hold item",
				"> chestsort clearmarks"
		);
	}
}
