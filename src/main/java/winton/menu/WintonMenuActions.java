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

package winton.menu;

import winton.api.IWinton;
import winton.api.Settings;
import winton.api.WintonAPI;
import winton.api.utils.SettingsUtil;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Menu → Winton command / settings bridge. */
public final class WintonMenuActions {
	private WintonMenuActions() {
	}

	public static IWinton primary() {
		return WintonAPI.getProvider().getPrimaryWinton();
	}

	public static boolean run(String command) {
		IWinton w = primary();
		if (w == null) {
			return false;
		}
		return w.getCommandManager().execute(command);
	}

	public static void stop() {
		run("stop");
		hardCleanup();
	}

	public static void forceCancel() {
		run("forcecancel");
		hardCleanup();
	}

	/** Hard stop: pause processes, cancel pathing, clear forced inputs, disable freecam. */
	public static void killAll() {
		run("stop");
		run("forcecancel");
		hardCleanup();
		try {
			WintonFreecam.disable();
		} catch (Throwable ignored) {
		}
		chat("winton.menu.kill_all_done");
	}

	/** Drop stuck inputs / aim / inventory swaps so FPS returns after clear/build. */
	private static void hardCleanup() {
		IWinton w = primary();
		if (w == null) {
			return;
		}
		try {
			w.getInputOverrideHandler().clearAllKeys();
		} catch (Throwable ignored) {
		}
		try {
			w.getPathingBehavior().forceCancel();
		} catch (Throwable ignored) {
		}
		if (w instanceof winton.Winton ww) {
			try {
				ww.getLookBehavior().stopAim();
			} catch (Throwable ignored) {
			}
			try {
				ww.getInventoryBehavior().clearPendingMoves();
			} catch (Throwable ignored) {
			}
			try {
				ww.getBuilderProcess().onLostControl();
			} catch (Throwable ignored) {
			}
			try {
				ww.getInputOverrideHandler().getBlockBreakHelper().stopBreakingBlock();
			} catch (Throwable ignored) {
			}
			try {
				var player = ww.getPlayerContext().player();
				if (player != null) {
					player.input = new net.minecraft.client.player.KeyboardInput(
						net.minecraft.client.Minecraft.getInstance().options);
				}
			} catch (Throwable ignored) {
			}
		}
		try {
			WintonClearMode.resetPending();
		} catch (Throwable ignored) {
		}
	}

	public static void oresAllOff(Map<String, Boolean> ores) {
		for (String id : ores.keySet()) {
			ores.put(id, false);
		}
		WintonMenuConfig.saveOreToggles(ores);
		// Full reset: stop mining + clear world selections
		try {
			run("stop");
		} catch (Throwable ignored) {
		}
		IWinton w = primary();
		if (w != null) {
			try {
				w.getSelectionManager().removeAllSelections();
			} catch (Throwable ignored) {
			}
		}
	}

	public static void oresAllOn(Map<String, Boolean> ores) {
		for (String id : ores.keySet()) {
			ores.put(id, true);
		}
		WintonMenuConfig.saveOreToggles(ores);
	}

	public static void mineSelected(Map<String, Boolean> ores) {
		List<String> names = new ArrayList<>();
		for (Map.Entry<String, Boolean> e : ores.entrySet()) {
			if (Boolean.TRUE.equals(e.getValue())) {
				names.add(e.getKey());
			}
		}
		if (names.isEmpty()) {
			return;
		}
		run("mine " + String.join(" ", names));
	}

	public static void tunnel() {
		run("tunnel");
	}

	public static void tunnel(int height, int width, int depth) {
		run("tunnel " + height + " " + width + " " + depth);
	}

	public static void farm() {
		run("farm");
	}

	public static void farm(int range) {
		run("farm " + Math.max(1, range));
	}

	public static void explore() {
		run("explore");
	}

	public static void explore(int x, int z) {
		run("explore " + x + " " + z);
	}

	public static void litematicaBuild(int index1based) {
		if (!winton.utils.schematic.litematica.LitematicaHelper.isLitematicaPresent()) {
			chat("winton.build.no_litematica");
			return;
		}
		int n = winton.utils.schematic.litematica.LitematicaHelper.placementCount();
		if (n <= 0) {
			chat("winton.build.no_placement");
			return;
		}
		int idx = Math.max(1, index1based);
		if (idx > n) {
			chat("winton.build.bad_index");
			return;
		}
		// Ensure inventory pull + place work for Litematica builds (survival servers)
		try {
			var s = winton.api.WintonAPI.getSettings();
			s.allowInventory.value = true;
			s.allowPlace.value = true;
			s.buildOnlySelection.value = false;
			s.inventoryMoveOnlyIfStationary.value = false;
			s.ticksBetweenInventoryMoves.value = 1;
			winton.api.utils.SettingsUtil.save(s);
		} catch (Throwable ignored) {
		}
		hardCleanup();
		run("litematica " + idx);
	}

	public static void chat(String langKey) {
		net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
		if (mc.player != null) {
			mc.player.displayClientMessage(WintonLang.text(langKey), false);
		}
	}

	public static void surface() {
		run("surface");
	}

	public static void come() {
		run("come");
	}

	public static void axis() {
		run("axis");
		run("path");
	}

	public static void elytra() {
		IWinton w = primary();
		if (w == null) {
			return;
		}
		if (!w.getElytraProcess().isLoaded()) {
			chat("winton.menu.elytra_unsupported");
			return;
		}
		String xs = WintonMenuConfig.field("gotoX", "");
		String zs = WintonMenuConfig.field("gotoZ", "");
		if (xs != null && zs != null && !xs.isBlank() && !zs.isBlank()) {
			try {
				elytraToXZ(Integer.parseInt(xs.trim()), Integer.parseInt(zs.trim()));
				return;
			} catch (NumberFormatException ignored) {
			}
		}
		if (w.getCustomGoalProcess().mostRecentGoal() == null) {
			chat("winton.menu.elytra_need_goal");
			return;
		}
		prepareElytraFlightSettings();
		run("elytra");
	}

	public static void elytraTo(int x, int y, int z) {
		// Y ignored — always surface at XZ
		elytraToXZ(x, z);
	}

	/** Fly to X Z; Y = highest surface block. */
	public static void elytraToXZ(int x, int z) {
		IWinton w = primary();
		if (w == null) {
			return;
		}
		if (!w.getElytraProcess().isLoaded()) {
			chat("winton.menu.elytra_unsupported");
			return;
		}
		prepareElytraFlightSettings();
		run("elytra " + x + " " + z);
	}

	private static void prepareElytraFlightSettings() {
		Settings s = WintonAPI.getSettings();
		s.elytraTermsAccepted.value = true;
		s.elytraAutoJump.value = true;
		s.elytraFreeLook.value = false;
		s.elytraSmoothLook.value = true;
		s.elytraAutoSwap.value = true;
		s.allowInventory.value = true; // pull fireworks/elytra from inventory
		s.elytraAllowAboveBuildLimit.value = false; // descend to surface, don't cruise at build limit
        s.elytraRenderSimulation.value = false; // less FPS cost while flying
		s.elytraRenderRaytraces.value = false;
		s.elytraSimulationTicks.value = 8;
		s.renderPath.value = false; // path ribbon is expensive on long elytra trips
		SettingsUtil.save(s);
	}

	public static void pickup() {
		run("pickup");
	}

	public static void followPlayers() {
		run("follow players");
	}

	public static void clickPath() {
		run("click");
	}

	public static void blacklist() {
		run("blacklist");
	}

	public static void eta() {
		run("eta");
	}

	public static void proc() {
		run("proc");
	}

	public static void pause() {
		run("pause");
	}

	public static void resume() {
		run("resume");
	}

	public static void pathGoal() {
		run("path");
	}

	public static void invert() {
		run("invert");
	}

	public static void home() {
		run("home");
	}

	public static void setHome() {
		run("sethome");
	}

	public static void render() {
		run("render");
	}

	public static void repack() {
		run("repack");
	}

	public static void version() {
		run("version");
	}

	public static void help() {
		run("help");
	}

	public static void thisWay(int blocks) {
		run("thisway " + Math.max(1, blocks));
	}

	public static void gotoBlock(int x, int y, int z) {
		run("goto " + x + " " + y + " " + z);
	}

	/** Walk/path to X Z on the highest surface block. */
	public static void gotoXZ(int x, int z) {
		IWinton w = primary();
		if (w == null || w.getPlayerContext().world() == null) {
			badWorld();
			return;
		}
		int y = w.getPlayerContext().world().getHeight(
			net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z);
		run("goto " + x + " " + y + " " + z);
	}

	private static void badWorld() {
		chat("winton.menu.bad_coords");
	}

	public static void find(String blockId) {
		if (blockId == null || blockId.isBlank()) {
			return;
		}
		run("find " + blockId.trim());
	}

	public static void setBool(Settings.Setting<Boolean> setting, boolean value) {
		setting.value = value;
		persistSettings();
	}

	public static void setInt(Settings.Setting<Integer> setting, int value) {
		setting.value = value;
		persistSettings();
	}

	/** Write Baritone settings to .minecraft/winton/settings.txt */
	public static void persistSettings() {
		SettingsUtil.save(WintonAPI.getSettings());
	}

	/** id → default enabled; UI label via translation key */
	public static List<OreToggle> oreOptions() {
		List<OreToggle> list = new ArrayList<>();
		list.add(new OreToggle("coal_ore", "winton.ore.coal", false));
		list.add(new OreToggle("deepslate_coal_ore", "winton.ore.coal", false));
		list.add(new OreToggle("iron_ore", "winton.ore.iron", false));
		list.add(new OreToggle("deepslate_iron_ore", "winton.ore.iron", false));
		list.add(new OreToggle("copper_ore", "winton.ore.copper", false));
		list.add(new OreToggle("deepslate_copper_ore", "winton.ore.copper", false));
		list.add(new OreToggle("gold_ore", "winton.ore.gold", false));
		list.add(new OreToggle("deepslate_gold_ore", "winton.ore.gold", false));
		list.add(new OreToggle("redstone_ore", "winton.ore.redstone", false));
		list.add(new OreToggle("deepslate_redstone_ore", "winton.ore.redstone", false));
		list.add(new OreToggle("lapis_ore", "winton.ore.lapis", false));
		list.add(new OreToggle("deepslate_lapis_ore", "winton.ore.lapis", false));
		list.add(new OreToggle("emerald_ore", "winton.ore.emerald", false));
		list.add(new OreToggle("deepslate_emerald_ore", "winton.ore.emerald", false));
		list.add(new OreToggle("diamond_ore", "winton.ore.diamond", false));
		list.add(new OreToggle("deepslate_diamond_ore", "winton.ore.diamond", false));
		list.add(new OreToggle("nether_quartz_ore", "winton.ore.quartz", false));
		list.add(new OreToggle("nether_gold_ore", "winton.ore.nether_gold", false));
		list.add(new OreToggle("ancient_debris", "winton.ore.debris", false));
		return list;
	}

	public static Map<String, Boolean> defaultOreToggles() {
		Map<String, Boolean> map = new LinkedHashMap<>();
		for (OreToggle o : oreOptions()) {
			map.put(o.id(), o.defaultOn());
		}
		return map;
	}

	public record OreToggle(String id, String langKey, boolean defaultOn) {
		public Component label() {
			String name = WintonLang.get(langKey);
			if (id.startsWith("deepslate_")) {
				name = name + " (deep)";
			}
			return Component.literal(name);
		}
	}

	public record BoolSetting(String langKey, Settings.Setting<Boolean> setting) {
		public boolean get() {
			return setting.value;
		}

		public void set(boolean v) {
			setBool(setting, v);
		}

		public Component label() {
			return WintonLang.text(langKey);
		}
	}
}
