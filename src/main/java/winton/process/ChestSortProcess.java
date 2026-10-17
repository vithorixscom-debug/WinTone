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

package winton.process;

import winton.Winton;
import winton.api.pathing.goals.Goal;
import winton.api.pathing.goals.GoalGetToBlock;
import winton.api.process.IChestSortProcess;
import winton.api.process.PathingCommand;
import winton.api.process.PathingCommandType;
import winton.api.selection.ISelection;
import winton.api.utils.BetterBlockPos;
import winton.api.utils.Rotation;
import winton.api.utils.RotationUtils;
import winton.api.utils.input.Input;
import winton.utils.WintonProcessHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Content-aware chest sorter.
 * <ol>
 *   <li>Scan chests in radius / selection</li>
 *   <li>Learn filters (manual marks, or first non-empty contents per chest)</li>
 *   <li>Pull stacks that don't belong</li>
 *   <li>Deposit into matching / empty dump chests</li>
 * </ol>
 */
public final class ChestSortProcess extends WintonProcessHelper implements IChestSortProcess {

	private enum Phase {
		IDLE,
		SCAN,
		GOTO,
		OPEN,
		LEARN,
		EXTRACT,
		DEPOSIT,
		CLOSE,
		NEXT,
		DONE
	}

	private Phase phase = Phase.IDLE;
	private final Set<BlockPos> marked = new LinkedHashSet<>();
	private final Map<BlockPos, Set<Item>> filters = new LinkedHashMap<>();
	private final List<BlockPos> chests = new ArrayList<>();
	private int chestIndex;
	private BlockPos current;
	private Goal goal;
	private int waitTicks;
	private int openAttempts;
	private int clickCooldown;
	private int pass; // 0=learn, 1=extract, 2=deposit
	private int stallTicks;
	private String status = "idle";

	public ChestSortProcess(Winton winton) {
		super(winton);
	}

	@Override
	public void sortRadius(int radius) {
		onLostControl();
		int r = Math.max(4, Math.min(48, radius));
		BlockPos feet = ctx.playerFeet();
		chests.clear();
		filters.clear();
		for (int x = feet.getX() - r; x <= feet.getX() + r; x++) {
			for (int y = feet.getY() - r; y <= feet.getY() + r; y++) {
				for (int z = feet.getZ() - r; z <= feet.getZ() + r; z++) {
					BlockPos p = new BlockPos(x, y, z);
					if (isSortableContainer(p)) {
						chests.add(p.immutable());
					}
				}
			}
		}
		startJob("radius " + r);
	}

	@Override
	public void sortSelection() {
		onLostControl();
		ISelection[] sels = winton.getSelectionManager().getSelections();
		if (sels == null || sels.length == 0) {
			logDirect("No selection — set pos1/pos2 first (Clear tab or #sel)");
			return;
		}
		chests.clear();
		filters.clear();
		for (ISelection sel : sels) {
			BetterBlockPos min = sel.min();
			BetterBlockPos max = sel.max();
			for (int x = min.x; x <= max.x; x++) {
				for (int y = min.y; y <= max.y; y++) {
					for (int z = min.z; z <= max.z; z++) {
						BlockPos p = new BlockPos(x, y, z);
						if (isSortableContainer(p)) {
							chests.add(p.immutable());
						}
					}
				}
			}
		}
		startJob("selection");
	}

	private void startJob(String where) {
		if (chests.isEmpty()) {
			logDirect("No chests found (" + where + ")");
			return;
		}
		// Seed filters from manual marks
		for (BlockPos m : marked) {
			if (chests.contains(m) && filters.containsKey(m)) {
				// already have filter from markLookChest
			}
		}
		chestIndex = 0;
		pass = 0;
		current = null;
		goal = null;
		waitTicks = 0;
		openAttempts = 0;
		clickCooldown = 0;
		stallTicks = 0;
		phase = Phase.NEXT;
		status = "found " + chests.size() + " chests (" + where + ")";
		logDirect("Chest sort: " + chests.size() + " containers (" + where + ")");
	}

	@Override
	public boolean markLookChest(Set<Item> items) {
		Optional<BlockPos> look = ctx.getSelectedBlock();
		if (look.isEmpty() || !isSortableContainer(look.get())) {
			logDirect("Look at a chest/barrel/shulker to mark it");
			return false;
		}
		BlockPos pos = look.get().immutable();
		Set<Item> filter = new HashSet<>();
		if (items != null && !items.isEmpty()) {
			filter.addAll(items);
		} else {
			ItemStack held = ctx.player().getMainHandItem();
			if (!held.isEmpty()) {
				filter.add(held.getItem());
			}
		}
		if (filter.isEmpty()) {
			logDirect("Hold an item (or pass items) to define what belongs in this chest");
			return false;
		}
		marked.add(pos);
		filters.put(pos, filter);
		StringBuilder names = new StringBuilder();
		for (Item it : filter) {
			if (names.length() > 0) names.append(", ");
			names.append(BuiltInRegistries.ITEM.getKey(it));
		}
		logDirect("Marked " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " ← " + names);
		return true;
	}

	@Override
	public void clearMarks() {
		marked.clear();
		logDirect("Chest marks cleared");
	}

	@Override
	public Set<BlockPos> getMarkedChests() {
		return Set.copyOf(marked);
	}

	@Override
	public boolean isActive() {
		return phase != Phase.IDLE && phase != Phase.DONE;
	}

	@Override
	public double priority() {
		return 1.5; // above custom goal, below inventory pauser
	}

	@Override
	public void onLostControl() {
		closeContainerQuiet();
		winton.getInputOverrideHandler().clearAllKeys();
		phase = Phase.IDLE;
		chests.clear();
		// keep marked + their filters
		current = null;
		goal = null;
		status = "idle";
	}

	@Override
	public String displayName0() {
		return "ChestSort — " + status;
	}

	@Override
	public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
		if (clickCooldown > 0) {
			clickCooldown--;
		}
		if (waitTicks > 0) {
			waitTicks--;
			return pause();
		}

		switch (phase) {
			case NEXT -> {
				if (chestIndex >= chests.size()) {
					if (pass == 0) {
						// After learn: ensure every non-empty unmarked chest got a filter from contents;
						// empty ones become dump (no filter entry = dump)
						pass = 1;
						chestIndex = 0;
						status = "extract misplaced";
						logDirect("Chest sort: extracting misplaced items…");
						return pause();
					}
					if (pass == 1) {
						pass = 2;
						chestIndex = 0;
						status = "deposit";
						logDirect("Chest sort: depositing…");
						return pause();
					}
					phase = Phase.DONE;
					status = "done";
					logDirect("Chest sort done ✓");
					closeContainerQuiet();
					phase = Phase.IDLE;
					return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
				}
				current = chests.get(chestIndex);
				if (!isSortableContainer(current)) {
					chestIndex++;
					return pause();
				}
				goal = new GoalGetToBlock(current);
				phase = Phase.GOTO;
				status = "goto " + current.getX() + " " + current.getY() + " " + current.getZ() + " (pass " + pass + ")";
				openAttempts = 0;
				return new PathingCommand(goal, PathingCommandType.SET_GOAL_AND_PATH);
			}
			case GOTO -> {
				if (calcFailed) {
					logDirect("Unreachable chest " + current + ", skipping");
					chestIndex++;
					phase = Phase.NEXT;
					return pause();
				}
				if (goal != null && goal.isInGoal(ctx.playerFeet()) && isSafeToCancel) {
					phase = Phase.OPEN;
					openAttempts = 0;
					return pause();
				}
				return new PathingCommand(goal, PathingCommandType.REVALIDATE_GOAL_AND_PATH);
			}
			case OPEN -> {
				if (isContainerOpen()) {
					phase = switch (pass) {
						case 0 -> Phase.LEARN;
						case 1 -> Phase.EXTRACT;
						default -> Phase.DEPOSIT;
					};
					stallTicks = 0;
					return pause();
				}
				if (tryOpen(current)) {
					return pause();
				}
				if (++openAttempts > 40) {
					logDirect("Could not open " + current + ", skip");
					chestIndex++;
					phase = Phase.NEXT;
				}
				return pause();
			}
			case LEARN -> {
				learnFromOpenChest();
				phase = Phase.CLOSE;
				return pause();
			}
			case EXTRACT -> {
				if (!isContainerOpen()) {
					phase = Phase.OPEN;
					return pause();
				}
				boolean moved = extractOneMisplaced();
				if (moved) {
					stallTicks = 0;
					clickCooldown = Math.max(2, Winton.settings().ticksBetweenInventoryMoves.value);
					return pause();
				}
				if (++stallTicks > 3) {
					phase = Phase.CLOSE;
				}
				return pause();
			}
			case DEPOSIT -> {
				if (!isContainerOpen()) {
					phase = Phase.OPEN;
					return pause();
				}
				boolean moved = depositOneMatching();
				if (moved) {
					stallTicks = 0;
					clickCooldown = Math.max(2, Winton.settings().ticksBetweenInventoryMoves.value);
					return pause();
				}
				if (++stallTicks > 3) {
					phase = Phase.CLOSE;
				}
				return pause();
			}
			case CLOSE -> {
				closeContainerQuiet();
				waitTicks = 2;
				chestIndex++;
				phase = Phase.NEXT;
				return pause();
			}
			default -> {
				onLostControl();
				return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
			}
		}
	}

	private PathingCommand pause() {
		winton.getInputOverrideHandler().clearAllKeys();
		return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
	}

	private void learnFromOpenChest() {
		if (filters.containsKey(current) && marked.contains(current)) {
			return; // manual mark wins
		}
		Set<Item> seen = new HashSet<>();
		forEachChestSlot(stack -> {
			if (!stack.isEmpty()) {
				seen.add(stack.getItem());
			}
		});
		if (!seen.isEmpty()) {
			filters.put(current, seen);
		}
	}

	private boolean extractOneMisplaced() {
		if (clickCooldown > 0) {
			return false;
		}
		Set<Item> filter = filters.get(current);
		// Dump / empty-learned chests: don't extract on pass 1 (they're sources for deposit only if we pulled elsewhere)
		// Category chest: pull items not in filter
		if (filter == null || filter.isEmpty()) {
			return false;
		}
		AbstractContainerMenu menu = ctx.player().containerMenu;
		int chestSlots = menu.slots.size() - 36;
		if (chestSlots <= 0) {
			return false;
		}
		for (int i = 0; i < chestSlots; i++) {
			ItemStack stack = menu.slots.get(i).getItem();
			if (stack.isEmpty()) {
				continue;
			}
			if (!filter.contains(stack.getItem())) {
				// Only extract if some other chest wants this item, or we have space
				if (!playerHasEmptySlot() && !playerCanMerge(stack)) {
					return false;
				}
				ctx.playerController().windowClick(menu.containerId, i, 0, ClickType.QUICK_MOVE, ctx.player());
				return true;
			}
		}
		return false;
	}

	private boolean depositOneMatching() {
		if (clickCooldown > 0) {
			return false;
		}
		Set<Item> filter = filters.get(current);
		boolean dump = filter == null || filter.isEmpty();
		AbstractContainerMenu menu = ctx.player().containerMenu;
		int chestSlots = menu.slots.size() - 36;
		if (chestSlots <= 0) {
			return false;
		}
		// Player inv slots are last 36
		for (int i = chestSlots; i < menu.slots.size(); i++) {
			ItemStack stack = menu.slots.get(i).getItem();
			if (stack.isEmpty()) {
				continue;
			}
			Item item = stack.getItem();
			boolean wants;
			if (dump) {
				// Dump only takes items that have NO category chest
				wants = !hasHome(item);
			} else {
				wants = filter.contains(item);
			}
			if (!wants) {
				continue;
			}
			if (!chestHasSpace(menu, chestSlots, stack)) {
				continue;
			}
			ctx.playerController().windowClick(menu.containerId, i, 0, ClickType.QUICK_MOVE, ctx.player());
			return true;
		}
		return false;
	}

	private boolean hasHome(Item item) {
		for (Map.Entry<BlockPos, Set<Item>> e : filters.entrySet()) {
			if (e.getValue() != null && e.getValue().contains(item)) {
				return true;
			}
		}
		return false;
	}

	private boolean chestHasSpace(AbstractContainerMenu menu, int chestSlots, ItemStack stack) {
		for (int i = 0; i < chestSlots; i++) {
			ItemStack s = menu.slots.get(i).getItem();
			if (s.isEmpty()) {
				return true;
			}
			if (ItemStack.isSameItemSameComponents(s, stack) && s.getCount() < s.getMaxStackSize()) {
				return true;
			}
		}
		return false;
	}

	private boolean playerHasEmptySlot() {
		var inv = ctx.player().getInventory();
		for (int i = 0; i < 36; i++) {
			if (inv.getItem(i).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	private boolean playerCanMerge(ItemStack stack) {
		var inv = ctx.player().getInventory();
		for (int i = 0; i < 36; i++) {
			ItemStack s = inv.getItem(i);
			if (ItemStack.isSameItemSameComponents(s, stack) && s.getCount() < s.getMaxStackSize()) {
				return true;
			}
		}
		return false;
	}

	private void forEachChestSlot(java.util.function.Consumer<ItemStack> consumer) {
		AbstractContainerMenu menu = ctx.player().containerMenu;
		int chestSlots = menu.slots.size() - 36;
		for (int i = 0; i < chestSlots; i++) {
			consumer.accept(menu.slots.get(i).getItem());
		}
	}

	private boolean isContainerOpen() {
		return !(ctx.player().containerMenu instanceof InventoryMenu);
	}

	private boolean tryOpen(BlockPos pos) {
		Optional<Rotation> rot = RotationUtils.reachable(ctx, pos, ctx.playerController().getBlockReachDistance());
		if (rot.isEmpty()) {
			return false;
		}
		winton.getLookBehavior().updateTarget(rot.get(), true);
		Optional<BlockPos> sel = ctx.getSelectedBlock();
		if (sel.isPresent() && sel.get().equals(pos)) {
			winton.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, true);
			return true;
		}
		return false;
	}

	private void closeContainerQuiet() {
		try {
			if (isContainerOpen()) {
				ctx.player().closeContainer();
			}
		} catch (Throwable ignored) {
		}
		winton.getInputOverrideHandler().clearAllKeys();
	}

	private boolean isSortableContainer(BlockPos pos) {
		if (ctx.world() == null || !ctx.world().isLoaded(pos)) {
			return false;
		}
		Block b = ctx.world().getBlockState(pos).getBlock();
		return b instanceof ChestBlock || b instanceof BarrelBlock || b instanceof ShulkerBoxBlock;
	}
}
