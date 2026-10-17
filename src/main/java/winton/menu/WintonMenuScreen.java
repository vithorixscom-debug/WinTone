/*
 * Copyright (c) masa. Modifications Copyright (c) WinTone contributors.
 * This file is part of Winton.
 *
 * Winton is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Based on Baritone (https://github.com/cabaletta/baritone), LGPL-3.0.
 */

package winton.menu;

import winton.api.Settings;
import winton.api.WintonAPI;
import winton.api.utils.SettingsUtil;
import winton.minihud.MiniHudConfig;
import winton.minihud.MiniHudInfoLine;
import winton.minihud.MiniHudLines;
import winton.minihud.MiniHudOverlay;
import winton.utils.schematic.litematica.LitematicaHelper;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * In-game panel: actions / navigate / mine / clear / build / settings.
 */
public final class WintonMenuScreen extends Screen {
	private enum Tab {
		ACTIONS, NAV, MINE, CLEAR, SORT, BUILD, MINIHUD, SETTINGS
	}

	private enum Group {
		MOVE, LOOK, RENDER, MINE_DET, MINE_ADV, SAFETY, AVOID, PATH, ELYTRA, BUILD_DET, CHAT, PERF, KEYS,
		MINIHUD_GEN, MINIHUD_LINES, MINIHUD_OVERLAY
	}

	private enum ActionDetail {
		NONE, EXPLORE, TUNNEL, FARM, AXIS, FOLLOW, PICKUP, FIND
	}

	private Tab tab = Tab.ACTIONS;
	private ActionDetail expandedAction = ActionDetail.NONE;
	private final Set<Group> expanded = EnumSet.noneOf(Group.class);
	private final Map<String, Boolean> oreToggles = new LinkedHashMap<>(WintonMenuConfig.loadOreToggles());
	private EditBox gotoX;
	private EditBox gotoY;
	private EditBox gotoZ;
	private EditBox thisWayDist;
	private EditBox findBox;
	private EditBox tunnelH;
	private EditBox tunnelW;
	private EditBox tunnelD;
	private EditBox farmRange;
	private EditBox sortRadius;
	private EditBox exploreX;
	private EditBox exploreZ;
	private EditBox clear1x;
	private EditBox clear1y;
	private EditBox clear1z;
	private EditBox clear2x;
	private EditBox clear2y;
	private EditBox clear2z;
	private boolean mineAdvancedOpen;
	private int contentTop = 38;
	private int contentBottom = 200;
	private int scroll;
	private int liteIndex = 1;
	private boolean uiRestored;

	public WintonMenuScreen() {
		super(WintonLang.text("winton.menu.title"));
	}

	private void restoreUiOnce() {
		WintonMenuConfig.ensureLoaded();
		// Always re-apply persisted tab/scroll so reopen lands where you left off
		try {
			tab = Tab.valueOf(WintonMenuConfig.lastTab());
		} catch (Exception ignored) {
			tab = Tab.ACTIONS;
		}
		try {
			expandedAction = ActionDetail.valueOf(WintonMenuConfig.lastAction());
		} catch (Exception ignored) {
			expandedAction = ActionDetail.NONE;
		}
		expanded.clear();
		for (String g : WintonMenuConfig.lastGroups()) {
			try {
				expanded.add(Group.valueOf(g));
			} catch (Exception ignored) {
			}
		}
		scroll = WintonMenuConfig.lastScroll();
		liteIndex = WintonMenuConfig.lastLiteIndex();
		mineAdvancedOpen = WintonMenuConfig.lastMineAdvanced();
		uiRestored = true;
	}

	private void persistUi() {
		captureFields();
		WintonMenuConfig.saveUiState(tab.name(), expandedAction.name(), expanded, scroll, liteIndex, mineAdvancedOpen);
	}

	private void captureFields() {
		putField("tunnelH", tunnelH);
		putField("tunnelW", tunnelW);
		putField("tunnelD", tunnelD);
		putField("farmRange", farmRange);
		putField("sortRadius", sortRadius);
		putField("exploreX", exploreX);
		putField("exploreZ", exploreZ);
		putField("find", findBox);
		putField("thisWay", thisWayDist);
		putField("gotoX", gotoX);
		putField("gotoY", gotoY);
		putField("gotoZ", gotoZ);
		putField("clear1x", clear1x);
		putField("clear1y", clear1y);
		putField("clear1z", clear1z);
		putField("clear2x", clear2x);
		putField("clear2y", clear2y);
		putField("clear2z", clear2z);
	}

	private static void putField(String key, EditBox box) {
		if (box != null) {
			WintonMenuConfig.setField(key, box.getValue());
		}
	}

	private EditBox boxField(int x, int y, int w, String fieldKey, String placeholder, String tipKey) {
		EditBox b = box(x, y, w, placeholder, tipKey);
		String saved = WintonMenuConfig.field(fieldKey, "");
		if (saved != null && !saved.isEmpty()) {
			b.setValue(saved);
		} else if (placeholder != null && !"X".equals(placeholder) && !"Y".equals(placeholder) && !"Z".equals(placeholder)
			&& !"X1".equals(placeholder) && !"Y1".equals(placeholder) && !"Z1".equals(placeholder)
			&& !"X2".equals(placeholder) && !"Y2".equals(placeholder) && !"Z2".equals(placeholder)) {
			b.setValue(placeholder);
		}
		return b;
	}

	@Override
	protected void init() {
		restoreUiOnce();
		WintonMenuConfig.ensureLoaded();
		clearWidgets();
		// Title sits above the tabs. Content never draws into the tab row or the footer.
		final int top = 18;
		final int tabH = 20;
		final int contentTop = top + tabH + 6;
		final int bottomBar = 48;
		final int tabCount = 8;
		final int tabGap = 3;
		int tabW = Math.max(22, (this.width - 8 - tabGap * (tabCount - 1)) / tabCount);
		int tabRow = tabW * tabCount + tabGap * (tabCount - 1);
		int tabX = Math.max(2, (this.width - tabRow) / 2);

		int col = Math.max(120, Math.min(160, (this.width - 40) / 2 - 8));
		int left = this.width / 2 - col - 6;
		int right = this.width / 2 + 6;
		int full = col * 2 + 12;

		// Clamp scroll so first row stays at/below contentTop
		int maxScroll = Math.max(0, 800);
		scroll = Math.min(Math.max(0, scroll), maxScroll);
		int y = contentTop - scroll;

		this.contentTop = contentTop;
		this.contentBottom = this.height - bottomBar;
		if (tab == Tab.MINE) {
			int digW = Math.min(240, full);
			addChrome(Button.builder(WintonLang.text("winton.menu.mine_start"), b -> startMine())
				.bounds(this.width / 2 - digW / 2, contentTop, digW, 20)
				.tooltip(tip("winton.tip.mine_start"))
				.build());
			this.contentTop = contentTop + 26;
			y = this.contentTop - scroll;
		}

		switch (tab) {
			case ACTIONS -> initActions(left, right, col, full, y);
			case NAV -> initNav(left, right, col, y);
			case MINE -> initMine(left, right, col, y);
			case CLEAR -> initClear(left, right, col, full, y);
			case SORT -> initSort(left, right, col, full, y);
			case BUILD -> initBuild(left, right, col, full, y);
			case MINIHUD -> initMiniHud(left, right, col, y);
			case SETTINGS -> initSettings(left, right, col, y);
		}

		// Tabs LAST so they always paint above content and stay clickable
		int step = tabW + tabGap;
		addTab(tabX, top, tabW, Tab.ACTIONS, "winton.menu.tab.actions");
		addTab(tabX + step, top, tabW, Tab.NAV, "winton.menu.tab.nav");
		addTab(tabX + step * 2, top, tabW, Tab.MINE, "winton.menu.tab.mine");
		addTab(tabX + step * 3, top, tabW, Tab.CLEAR, "winton.menu.tab.clear");
		addTab(tabX + step * 4, top, tabW, Tab.SORT, "winton.menu.tab.sort");
		addTab(tabX + step * 5, top, tabW, Tab.BUILD, "winton.menu.tab.build");
		addTab(tabX + step * 6, top, tabW, Tab.MINIHUD, "winton.menu.tab.minihud");
		addTab(tabX + step * 7, top, tabW, Tab.SETTINGS, "winton.menu.tab.settings");

		int bottom = this.height - 24;
		int gap = 4;
		int langW = Math.min(92, Math.max(56, this.width / 9));
		int rest = this.width - 8 - langW - gap;
		int bw = Math.max(32, Math.min(96, (rest - gap * 3) / 4));
		int rowW = langW + gap + bw * 4 + gap * 3;
		int x = Math.max(4, (this.width - rowW) / 2);
		addChrome(CycleButton.builder(c -> Component.literal(c.label), WintonLang.current())
			.withValues(
				WintonLang.Code.EN,
				WintonLang.Code.RU,
				WintonLang.Code.ZH,
				WintonLang.Code.DE,
				WintonLang.Code.FR
			)
			.create(x, bottom, langW, 20, WintonLang.text("winton.menu.lang"), (b, lang) -> {
				WintonMenuConfig.saveLanguage(lang, oreToggles);
				rebuildWidgets();
			}));
		x += langW + gap;

		addChrome(Button.builder(WintonLang.text("winton.menu.stop"), b -> {
			WintonMenuActions.stop();
			onClose();
		}).bounds(x, bottom, bw, 20)
			.tooltip(tip("winton.tip.stop"))
			.build());
		x += bw + gap;
		addChrome(Button.builder(WintonLang.text("winton.menu.kill_all"), b -> {
			WintonMenuActions.killAll();
			onClose();
		}).bounds(x, bottom, bw, 20)
			.tooltip(tip("winton.tip.kill_all"))
			.build());
		x += bw + gap;
		addChrome(Button.builder(WintonLang.text("winton.menu.close"), b -> onClose())
			.bounds(x, bottom, bw, 20)
			.tooltip(tip("winton.tip.close"))
			.build());
		x += bw + gap;
		addChrome(Button.builder(WintonLang.text("winton.menu.about"), b -> {
			if (this.minecraft != null && this.minecraft.player != null) {
				this.minecraft.player.displayClientMessage(Component.literal(
					"§6[WinTone]§r 1.17.23 · LGPL-3.0 · based on Baritone"), false);
			}
			onClose();
		}).bounds(x, bottom, bw, 20)
			.tooltip(tip("winton.tip.about"))
			.build());
	}

	private static Tooltip tip(String key) {
		return Tooltip.create(WintonLang.text(key));
	}

	/** Header and footer. These stay put and are never covered by scrolling content. */
	private <T extends net.minecraft.client.gui.components.AbstractWidget> T addChrome(T widget) {
		return addRenderableWidget(widget);
	}

	/** Content that would sit on a tab or a footer button is not added. */
	private <T extends net.minecraft.client.gui.components.AbstractWidget> T addContentWidget(T widget) {
		int wy = widget.getY();
		int wh = widget.getHeight();
		if (wy < contentTop || wy + wh > contentBottom) {
			return widget;
		}
		return addRenderableWidget(widget);
	}

	private void addTab(int x, int y, int w, Tab t, String key) {
		String label = (tab == t ? "▶ " : "") + WintonLang.get(key);
		addChrome(Button.builder(Component.literal(label), b -> switchTab(t))
			.bounds(x, y, w, 20).build());
	}

	private void switchTab(Tab next) {
		captureFields();
		this.tab = next;
		this.scroll = 0;
		// keep expandedAction when staying in actions flow; reset only when leaving ACTIONS
		if (next != Tab.ACTIONS) {
			this.expandedAction = ActionDetail.NONE;
		}
		persistUi();
		rebuildWidgets();
	}

	private void initActions(int left, int right, int col, int full, int y) {
		y = addActionRow(left, y, full, ActionDetail.EXPLORE, "winton.menu.act.explore", WintonMenuActions::explore);
		if (expandedAction == ActionDetail.EXPLORE) {
			String here = "—";
			if (minecraft != null && minecraft.player != null) {
				var p = minecraft.player.blockPosition();
				here = "X=" + p.getX() + "  Y=" + p.getY() + "  Z=" + p.getZ();
			}
			y = labeledTip(left, y, full, WintonLang.get("winton.menu.explore_here") + " " + here);
			y = labeledTip(left, y, full, WintonLang.get("winton.menu.explore_help"));
			exploreX = boxField(left, y, 70, "exploreX", "X", "winton.tip.explore_xz");
			exploreZ = boxField(left + 76, y, 70, "exploreZ", "Z", "winton.tip.explore_xz");
			addContentWidget(exploreX);
			addContentWidget(exploreZ);
			addContentWidget(Button.builder(WintonLang.text("winton.menu.explore_fill"), b -> {
				if (minecraft != null && minecraft.player != null) {
					var p = minecraft.player.blockPosition();
					exploreX.setValue(String.valueOf(p.getX()));
					exploreZ.setValue(String.valueOf(p.getZ()));
				}
			}).bounds(right, y, col, 20).tooltip(tip("winton.tip.explore_fill")).build());
			y += 22;
			addContentWidget(Button.builder(WintonLang.text("winton.menu.act.run_detail"), b -> {
				try {
					WintonMenuActions.explore(Integer.parseInt(exploreX.getValue()), Integer.parseInt(exploreZ.getValue()));
					onClose();
				} catch (NumberFormatException ex) {
					badCoords();
				}
			}).bounds(left, y, full, 20).tooltip(tip("winton.tip.explore_run")).build());
			y += 24;
		}

		y = addActionRow(left, y, full, ActionDetail.TUNNEL, "winton.menu.act.tunnel", WintonMenuActions::tunnel);
		if (expandedAction == ActionDetail.TUNNEL) {
			tunnelH = boxField(left, y, 44, "tunnelH", "2", "winton.tip.tunnel_h");
			tunnelW = boxField(left + 50, y, 44, "tunnelW", "1", "winton.tip.tunnel_w");
			tunnelD = boxField(left + 100, y, 44, "tunnelD", "64", "winton.tip.tunnel_d");
			addContentWidget(tunnelH);
			addContentWidget(tunnelW);
			addContentWidget(tunnelD);
			addContentWidget(Button.builder(WintonLang.text("winton.menu.act.run_detail"), b -> {
				try {
					WintonMenuActions.tunnel(
						Integer.parseInt(tunnelH.getValue()),
						Integer.parseInt(tunnelW.getValue()),
						Integer.parseInt(tunnelD.getValue()));
					onClose();
				} catch (NumberFormatException ex) {
					badCoords();
				}
			}).bounds(right, y, col, 20).tooltip(tip("winton.tip.tunnel_run")).build());
			y += 24;
		}

		y = addActionRow(left, y, full, ActionDetail.FARM, "winton.menu.act.farm", WintonMenuActions::farm);
		if (expandedAction == ActionDetail.FARM) {
			farmRange = boxField(left, y, 80, "farmRange", "64", "winton.tip.farm_range");
			addContentWidget(farmRange);
			addContentWidget(Button.builder(WintonLang.text("winton.menu.act.run_detail"), b -> {
				try {
					WintonMenuActions.farm(Integer.parseInt(farmRange.getValue()));
					onClose();
				} catch (NumberFormatException ex) {
					badCoords();
				}
			}).bounds(right, y, col, 20).tooltip(tip("winton.tip.farm_run")).build());
			y += 24;
		}

		y = addActionRow(left, y, full, ActionDetail.AXIS, "winton.menu.act.axis", WintonMenuActions::axis);
		if (expandedAction == ActionDetail.AXIS) {
			y = labeledTip(left, y, full, WintonLang.get("winton.menu.act.axis_how"));
			Settings s = WintonAPI.getSettings();
			addCycleInt(left, y, col, "winton.menu.set.axisHeight", "winton.tip.axisHeight",
				s.axisHeight, 64, 100, 120, 150, 200);
			y += 24;
		}

		y = addActionRow(left, y, full, ActionDetail.FOLLOW, "winton.menu.act.follow", WintonMenuActions::followPlayers);
		if (expandedAction == ActionDetail.FOLLOW) {
			y = labeledTip(left, y, full, WintonLang.get("winton.menu.act.follow_how"));
			Settings s = WintonAPI.getSettings();
			addCycleInt(left, y, col, "winton.menu.set.followRadius", "winton.tip.followRadius",
				s.followRadius, 0, 3, 5, 8, 12);
			y += 24;
		}

		y = addActionRow(left, y, full, ActionDetail.PICKUP, "winton.menu.act.pickup", WintonMenuActions::pickup);
		if (expandedAction == ActionDetail.PICKUP) {
			y = tip(left, y, full, "winton.menu.act.pickup_tip");
		}

		y = addSimpleAction(left, y, col, "winton.menu.act.click", WintonMenuActions::clickPath);
		addSimpleAction(right, y - 22, col, "winton.menu.act.blacklist", WintonMenuActions::blacklist);
		y = addSimpleAction(left, y, col, "winton.menu.act.repack", WintonMenuActions::repack);
		addSimpleAction(right, y - 22, col, "winton.menu.act.render", WintonMenuActions::render);
		y = addSimpleAction(left, y, col, "winton.menu.act.pause", WintonMenuActions::pause);
		addSimpleAction(right, y - 22, col, "winton.menu.act.resume", WintonMenuActions::resume);
		y = addSimpleAction(left, y, col, "winton.menu.act.eta", WintonMenuActions::eta);
		addSimpleAction(right, y - 22, col, "winton.menu.act.proc", WintonMenuActions::proc);
		y = addSimpleAction(left, y, col, "winton.menu.act.help", WintonMenuActions::help);
		addSimpleAction(right, y - 22, col, "winton.menu.act.version", () -> {
			WintonMenuActions.version();
			if (minecraft != null && minecraft.player != null) {
				minecraft.player.displayClientMessage(Component.literal("§6[WinTone]§r 1.17.23"), false);
			}
		});

		y = addActionRow(left, y, full, ActionDetail.FIND, "winton.menu.act.find", () -> {
			if (findBox != null) {
				WintonMenuActions.find(findBox.getValue());
			}
		});
		if (expandedAction == ActionDetail.FIND) {
			findBox = new EditBox(this.font, left, y, col, 20, Component.literal("block"));
			findBox.setValue(WintonMenuConfig.field("find", "diamond_ore"));
			addContentWidget(findBox);
			addContentWidget(Button.builder(WintonLang.text("winton.menu.act.run_detail"), b -> {
				WintonMenuActions.find(findBox.getValue());
				onClose();
			}).bounds(right, y, col, 20).build());
			y += 24;
		}
	}

	private int tip(int x, int y, int w, String key) {
		addContentWidget(Button.builder(WintonLang.text(key), b -> {
		}).bounds(x, y, w, 20).build());
		return y + 22;
	}

	private int labeledTip(int x, int y, int w, String text) {
		String clipped = text.length() > 60 ? text.substring(0, 57) + "…" : text;
		addContentWidget(Button.builder(Component.literal(clipped), b -> {
		}).bounds(x, y, w, 20).tooltip(Tooltip.create(Component.literal(text))).build());
		return y + 22;
	}

	private int addActionRow(int x, int y, int w, ActionDetail id, String langKey, Runnable run) {
		boolean open = expandedAction == id;
		String arrow = open ? "▾ " : "▸ ";
		int mainW = w - 48;
		addContentWidget(Button.builder(Component.literal(arrow + WintonLang.get(langKey)), b -> {
			expandedAction = open ? ActionDetail.NONE : id;
			persistUi();
			rebuildWidgets();
		}).bounds(x, y, mainW, 20).build());
		addContentWidget(Button.builder(WintonLang.text("winton.menu.act.go"), b -> {
			run.run();
			onClose();
		}).bounds(x + mainW + 4, y, 44, 20).build());
		return y + 22;
	}

	private int addSimpleAction(int x, int y, int w, String key, Runnable run) {
		addContentWidget(Button.builder(WintonLang.text(key), b -> {
			run.run();
			onClose();
		}).bounds(x, y, w, 20).build());
		return y + 22;
	}

	private void initNav(int left, int right, int col, int y) {
		y = addSimpleAction(left, y, col, "winton.menu.act.surface", WintonMenuActions::surface);
		addSimpleAction(right, y - 22, col, "winton.menu.act.come", WintonMenuActions::come);
		y = addSimpleAction(left, y, col, "winton.menu.act.home", WintonMenuActions::home);
		addSimpleAction(right, y - 22, col, "winton.menu.act.sethome", WintonMenuActions::setHome);
		y = addSimpleAction(left, y, col, "winton.menu.act.path", WintonMenuActions::pathGoal);
		addSimpleAction(right, y - 22, col, "winton.menu.act.invert", WintonMenuActions::invert);
		thisWayDist = boxField(left, y, col, "thisWay", "100", "winton.tip.thisway");
		addContentWidget(thisWayDist);
		addContentWidget(Button.builder(WintonLang.text("winton.menu.act.thisway"), b -> {
			try {
				WintonMenuActions.thisWay(Integer.parseInt(thisWayDist.getValue()));
				onClose();
			} catch (NumberFormatException ex) {
				badCoords();
			}
		}).bounds(right, y, col, 20).tooltip(tip("winton.tip.thisway")).build());
		y += 24;

		gotoX = boxField(left, y, 72, "gotoX", "X", "winton.tip.elytra_fly");
		gotoZ = boxField(left + 76, y, 72, "gotoZ", "Z", "winton.tip.elytra_fly");
		// Y unused for elytra — always lands on highest block at XZ
		gotoY = null;
		addContentWidget(gotoX);
		addContentWidget(gotoZ);
		addContentWidget(Button.builder(WintonLang.text("winton.menu.act.elytra_fly"), b -> {
			captureFields();
			try {
				int x = Integer.parseInt(gotoX.getValue().trim());
				int z = Integer.parseInt(gotoZ.getValue().trim());
				WintonMenuActions.elytraToXZ(x, z);
				onClose();
			} catch (NumberFormatException ex) {
				badCoords();
			}
		}).bounds(right, y, col, 20).tooltip(tip("winton.tip.elytra_fly")).build());
	}

	private void initSort(int left, int right, int col, int full, int y) {
		y = tip(left, y, full, "winton.sort.howto");
		sortRadius = boxField(left, y, col, "sortRadius", "16", "winton.tip.sort_radius");
		addContentWidget(sortRadius);
		addContentWidget(Button.builder(WintonLang.text("winton.sort.start_radius"), b -> {
			var w = WintonAPI.getProvider().getPrimaryWinton();
			if (w == null) {
				return;
			}
			try {
				int r = Integer.parseInt(sortRadius.getValue().trim());
				w.getChestSortProcess().sortRadius(Math.max(1, Math.min(64, r)));
				onClose();
			} catch (Exception ex) {
				badCoords();
			}
		}).bounds(right, y, col, 20).tooltip(tip("winton.tip.sort_radius")).build());
		y += 24;
		y = addSimpleAction(left, y, col, "winton.sort.start_sel", () -> {
			var w = WintonAPI.getProvider().getPrimaryWinton();
			if (w != null) {
				w.getChestSortProcess().sortSelection();
			}
		});
		addSimpleAction(right, y - 22, col, "winton.sort.mark", () -> {
			var w = WintonAPI.getProvider().getPrimaryWinton();
			if (w != null) {
				w.getChestSortProcess().markLookChest(java.util.Set.of());
			}
		});
		addSimpleAction(left, y, col, "winton.sort.clearmarks", () -> {
			var w = WintonAPI.getProvider().getPrimaryWinton();
			if (w != null) {
				w.getChestSortProcess().clearMarks();
			}
		});
	}

	private void initClear(int left, int right, int col, int full, int y) {
		addContentWidget(Button.builder(Component.literal(WintonClearMode.statusLine()), b -> {
		}).bounds(left, y, full, 20).build());
		y += 24;

		y = addActionKeepOpen(left, y, col, "winton.clear.pos1_here", "winton.tip.clear_here", WintonClearMode::setPos1Here);
		addActionKeepOpen(right, y - 22, col, "winton.clear.pos2_here", "winton.tip.clear_here", WintonClearMode::setPos2Here);

		clear1x = boxField(left, y, 44, "clear1x", "X1", "winton.tip.clear_xyz");
		clear1y = boxField(left + 50, y, 44, "clear1y", "Y1", "winton.tip.clear_xyz");
		clear1z = boxField(left + 100, y, 44, "clear1z", "Z1", "winton.tip.clear_xyz");
		addContentWidget(clear1x);
		addContentWidget(clear1y);
		addContentWidget(clear1z);
		addContentWidget(Button.builder(WintonLang.text("winton.clear.apply_pos1"), b -> {
			try {
				WintonClearMode.setPos1Coords(
					Integer.parseInt(clear1x.getValue()),
					Integer.parseInt(clear1y.getValue()),
					Integer.parseInt(clear1z.getValue()));
				rebuildWidgets();
			} catch (NumberFormatException ex) {
				badCoords();
			}
		}).bounds(right, y, col, 20).tooltip(tip("winton.tip.clear_xyz")).build());
		y += 24;

		clear2x = boxField(left, y, 44, "clear2x", "X2", "winton.tip.clear_xyz");
		clear2y = boxField(left + 50, y, 44, "clear2y", "Y2", "winton.tip.clear_xyz");
		clear2z = boxField(left + 100, y, 44, "clear2z", "Z2", "winton.tip.clear_xyz");
		prefillClearFromWorld();
		addContentWidget(clear2x);
		addContentWidget(clear2y);
		addContentWidget(clear2z);
		addContentWidget(Button.builder(WintonLang.text("winton.clear.apply_pos2"), b -> {
			try {
				WintonClearMode.setPos2Coords(
					Integer.parseInt(clear2x.getValue()),
					Integer.parseInt(clear2y.getValue()),
					Integer.parseInt(clear2z.getValue()));
				rebuildWidgets();
			} catch (NumberFormatException ex) {
				badCoords();
			}
		}).bounds(right, y, col, 20).tooltip(tip("winton.tip.clear_xyz")).build());
		y += 24;

		y = addActionKeepOpen(left, y, col, "winton.clear.pos1_look", "winton.tip.clear_look", WintonClearMode::setPos1Look);
		addActionKeepOpen(right, y - 22, col, "winton.clear.pos2_look", "winton.tip.clear_look", WintonClearMode::setPos2Look);
		y = addActionKeepOpen(left, y, col, "winton.clear.clear_sel", "winton.tip.clear_sel", WintonClearMode::clearSelections);
		addSimpleAction(right, y - 22, col, "winton.clear.start", () -> WintonClearMode.startClearArea());
		y += 8;
		addContentWidget(Button.builder(WintonLang.text("winton.clear.hint_box"), b -> {
			if (minecraft != null && minecraft.player != null) {
				minecraft.player.displayClientMessage(WintonLang.text("winton.clear.howto"), false);
			}
		}).bounds(left, y, full, 20).tooltip(tip("winton.clear.howto")).build());
	}

	private void initBuild(int left, int right, int col, int full, int y) {
		addCycleBool(left, y, full, "winton.menu.set.placeOnly", "winton.tip.placeOnly", WintonAPI.getSettings().placeOnly);
		y += 24;
		boolean present;
		try {
			present = LitematicaHelper.isLitematicaPresent();
		} catch (Throwable t) {
			present = false;
		}
		if (!present) {
			addContentWidget(Button.builder(WintonLang.text("winton.build.no_litematica"), b -> {
			}).bounds(left, y, full, 20).build());
			return;
		}
		int n;
		try {
			n = LitematicaHelper.placementCount();
		} catch (Throwable t) {
			addContentWidget(Button.builder(WintonLang.text("winton.build.no_litematica"), b -> {
			}).bounds(left, y, full, 20).build());
			return;
		}
		addContentWidget(Button.builder(Component.literal(
			WintonLang.get("winton.build.placements") + ": " + n
		), b -> rebuildWidgets()).bounds(left, y, full, 20).build());
		y += 24;

		if (n <= 0) {
			addContentWidget(Button.builder(WintonLang.text("winton.build.no_placement"), b -> {
			}).bounds(left, y, full, 20).build());
			y += 24;
			addContentWidget(Button.builder(WintonLang.text("winton.build.howto"), b -> {
				if (minecraft != null && minecraft.player != null) {
					minecraft.player.displayClientMessage(WintonLang.text("winton.build.howto_msg"), false);
				}
			}).bounds(left, y, full, 20).build());
			return;
		}

		liteIndex = Math.max(1, Math.min(liteIndex, n));
		for (int i = 0; i < n && i < 8; i++) {
			final int oneBased = i + 1;
			String name = LitematicaHelper.placementName(i);
			String label = (oneBased == liteIndex ? "▶ " : "  ") + oneBased + ". " + name;
			addContentWidget(Button.builder(Component.literal(label), b -> {
				liteIndex = oneBased;
				rebuildWidgets();
			}).bounds(left, y, full, 20).build());
			y += 22;
		}

		y += 4;
		Settings s = WintonAPI.getSettings();
		addContentWidget(CycleButton.onOffBuilder(s.buildInLayers.value)
			.create(left, y, col, 20, WintonLang.text("winton.build.buildInLayers"), (b, v) ->
				WintonMenuActions.setBool(s.buildInLayers, v)));
		addContentWidget(CycleButton.onOffBuilder(s.buildOnlySelection.value)
			.create(right, y, col, 20, WintonLang.text("winton.build.buildOnlySelection"), (b, v) ->
				WintonMenuActions.setBool(s.buildOnlySelection, v)));
		y += 22;
		addCycleBool(left, y, col, "winton.menu.set.layerOrder", "winton.tip.layerOrder", s.layerOrder);
		addCycleBool(right, y, col, "winton.menu.set.mapArtMode", "winton.tip.mapArtMode", s.mapArtMode);
		y += 22;
		addCycleBool(left, y, col, "winton.menu.set.skipFailedLayers", "winton.tip.skipFailedLayers", s.skipFailedLayers);
		addCycleBool(right, y, col, "winton.menu.set.okIfWater", "winton.tip.okIfWater", s.okIfWater);
		y += 22;
		addCycleBool(left, y, col, "winton.menu.set.buildIgnoreExisting", "winton.tip.buildIgnoreExisting", s.buildIgnoreExisting);
		y += 24;

		addContentWidget(Button.builder(WintonLang.text("winton.build.start"), b -> {
			WintonMenuActions.litematicaBuild(liteIndex);
			onClose();
		}).bounds(left, y, full, 20).build());
	}

	private EditBox box(int x, int y, int w, String hint, String tipKey) {
		EditBox box = new EditBox(this.font, x, y, w, 20, Component.literal(hint));
		box.setHint(Component.literal(hint));
		box.setFilter(s -> s.matches("-?\\d{0,8}"));
		if (tipKey != null) {
			box.setTooltip(tip(tipKey));
		}
		return box;
	}

	private void badCoords() {
		if (minecraft != null && minecraft.player != null) {
			minecraft.player.displayClientMessage(WintonLang.text("winton.menu.bad_coords"), false);
		}
	}

	private int addActionKeepOpen(int x, int y, int w, String key, Runnable run) {
		return addActionKeepOpen(x, y, w, key, null, run);
	}

	private int addActionKeepOpen(int x, int y, int w, String key, String tipKey, Runnable run) {
		Button.Builder b = Button.builder(WintonLang.text(key), btn -> {
			run.run();
			rebuildWidgets();
		}).bounds(x, y, w, 20);
		if (tipKey != null) {
			b.tooltip(tip(tipKey));
		}
		addContentWidget(b.build());
		return y + 22;
	}

	private void initMine(int left, int right, int col, int y) {
		Settings s = WintonAPI.getSettings();
		int full = col * 2 + 12;
		addContentWidget(Button.builder(WintonLang.text("winton.menu.ores_off"), b -> {
			WintonMenuActions.oresAllOff(oreToggles);
			rebuildWidgets();
		}).bounds(left, y, col, 20).tooltip(tip("winton.tip.ores_off")).build());
		addContentWidget(Button.builder(WintonLang.text("winton.menu.ores_on"), b -> {
			WintonMenuActions.oresAllOn(oreToggles);
			rebuildWidgets();
		}).bounds(right, y, col, 20).tooltip(tip("winton.tip.ores_on")).build());
		y += 24;

		List<WintonMenuActions.OreToggle> ores = WintonMenuActions.oreOptions();
		for (int i = 0; i < ores.size(); i++) {
			WintonMenuActions.OreToggle ore = ores.get(i);
			int x = i % 2 == 0 ? left : right;
			if (i % 2 == 0 && i > 0) {
				y += 24;
			}
			String id = ore.id();
			Checkbox.Builder cb = Checkbox.builder(ore.label(), this.font)
				.pos(x, y)
				.selected(oreToggles.getOrDefault(id, ore.defaultOn()))
				.maxWidth(col)
				.onValueChange((box, selected) -> {
					oreToggles.put(id, selected);
					WintonMenuConfig.saveOreToggles(oreToggles);
				});
			try {
				cb.tooltip(tip("winton.tip.ore"));
			} catch (Throwable ignored) {
			}
			addContentWidget(cb.build());
		}
		y += 28;
		addCycleBool(left, y, col, "winton.menu.set.mineAutoShaft", "winton.tip.mineAutoShaft", s.mineAutoShaft);
		addCycleBool(right, y, col, "winton.menu.set.legitMine", "winton.tip.legitMine", s.legitMine);
		y += 22;
		var winton = WintonAPI.getProvider().getPrimaryWinton();
		String shaftInfo = WintonLang.get("winton.mine.shaft.no_sel");
		if (winton != null) {
			var sel = winton.getSelectionManager().getOnlySelection();
			if (sel != null) {
				var sz = sel.size();
				shaftInfo = WintonLang.format("winton.mine.shaft.size", sz.getX(), sz.getY(), sz.getZ());
			}
		}
		addContentWidget(Button.builder(Component.literal(shaftInfo), b -> {
		}).bounds(left, y, full, 18).build()).active = false;
		y += 20;
		addContentWidget(Button.builder(WintonLang.text("winton.mine.shaft.pos1"), b -> {
			WintonClearMode.setPos1Here();
			rebuildWidgets();
		}).bounds(left, y, col, 20).tooltip(tip("winton.tip.shaft_pos1")).build());
		addContentWidget(Button.builder(WintonLang.text("winton.mine.shaft.pos2"), b -> {
			WintonClearMode.setPos2Here();
			rebuildWidgets();
		}).bounds(right, y, col, 20).tooltip(tip("winton.tip.shaft_pos2")).build());
		y += 22;
		addContentWidget(Button.builder(WintonLang.text("winton.mine.shaft.clear_sel"), b -> {
			WintonClearMode.clearSelections();
			rebuildWidgets();
		}).bounds(left, y, full, 20).tooltip(tip("winton.tip.shaft_clear_sel")).build());
		y += 22;
		addCycleBool(left, y, col, "winton.menu.set.mineScanDroppedItems", "winton.tip.mineScanDroppedItems", s.mineScanDroppedItems);
		addCycleBool(right, y, col, "winton.menu.set.freeLook", "winton.tip.freeLook", s.freeLook);
		y += 22;
		addCycleBool(left, y, col, "winton.menu.set.lookInWalkDirection", "winton.tip.lookInWalkDirection", s.lookInWalkDirection);
		addCycleBool(right, y, col, "winton.menu.set.allowSprint", "winton.tip.allowSprint", s.allowSprint);
		y += 22;
		addCycleBool(left, y, col, "winton.menu.set.allowPlace", "winton.tip.allowPlace", s.allowPlace);
		addCycleBool(right, y, col, "winton.menu.set.autoTool", "winton.tip.autoTool", s.autoTool);
		y += 22;
		addCycleBool(left, y, col, "winton.menu.set.walkWhileBreaking", "winton.tip.walkWhileBreaking", s.walkWhileBreaking);
		y += 22;
		// Scan radius always AUTO 8→16→32 (manual control removed)
		s.mineScanRadiusAuto.value = true;
		y += 4;
		String advLabel = (mineAdvancedOpen ? "▾ " : "▸ ") + WintonLang.get("winton.menu.mine_advanced");
		addContentWidget(Button.builder(Component.literal(advLabel), b -> {
			mineAdvancedOpen = !mineAdvancedOpen;
			persistUi();
			rebuildWidgets();
		}).bounds(left, y, full, 20).tooltip(tip("winton.tip.mine_advanced")).build());
		y += 22;
		if (mineAdvancedOpen) {
			addCycleBool(left, y, col, "winton.menu.set.exploreForBlocks", "winton.tip.exploreForBlocks", s.exploreForBlocks);
			addCycleBool(right, y, col, "winton.menu.set.allowOnlyExposedOres", "winton.tip.allowOnlyExposedOres", s.allowOnlyExposedOres);
			y += 22;
			addCycleBool(left, y, col, "winton.menu.set.itemSaver", "winton.tip.itemSaver", s.itemSaver);
			addCycleBool(right, y, col, "winton.menu.set.blacklistClosestOnFailure", "winton.tip.blacklistClosestOnFailure", s.blacklistClosestOnFailure);
			y += 22;
			addCycleBool(left, y, col, "winton.menu.set.legitMineIncludeDiagonals", "winton.tip.legitMineIncludeDiagonals", s.legitMineIncludeDiagonals);
			y += 24;
		}
	}

	private void startMine() {
		Settings s = WintonAPI.getSettings();
		boolean any = oreToggles.values().stream().anyMatch(Boolean.TRUE::equals);
		if (!any) {
			if (minecraft != null && minecraft.player != null) {
				minecraft.player.displayClientMessage(WintonLang.text("winton.menu.no_ores"), false);
			}
			return;
		}
		if (s.mineAutoShaft.value) {
			var w = WintonAPI.getProvider().getPrimaryWinton();
			if (w == null || w.getSelectionManager().getOnlySelection() == null) {
				if (minecraft != null && minecraft.player != null) {
					minecraft.player.displayClientMessage(WintonLang.text("winton.mine.shaft.need_sel"), false);
				}
				return;
			}
		}
		WintonMenuActions.mineSelected(oreToggles);
		onClose();
	}

	private void addCycleBool(int x, int y, int w, String labelKey, String tipKey, Settings.Setting<Boolean> setting) {
		CycleButton<Boolean> btn = CycleButton.onOffBuilder(setting.value)
			.create(x, y, w, 20, WintonLang.text(labelKey), (b, v) -> WintonMenuActions.setBool(setting, v));
		btn.setTooltip(tip(tipKey));
		addContentWidget(btn);
	}

	private void addCycleInt(int x, int y, int w, String labelKey, String tipKey, Settings.Setting<Integer> setting, Integer... values) {
		Integer current = snapToValues(setting.value, values);
		if (!Objects.equals(current, setting.value)) {
			setting.value = current;
		}
		CycleButton<Integer> btn = CycleButton.builder(v -> Component.literal(String.valueOf(v)), current)
			.withValues(values)
			.create(x, y, w, 20, WintonLang.text(labelKey), (b, v) -> WintonMenuActions.setInt(setting, v));
		btn.setTooltip(tip(tipKey));
		addContentWidget(btn);
	}

	private void addCycleFloat(int x, int y, int w, String labelKey, String tipKey, Settings.Setting<Float> setting, Float... values) {
		Float current = snapToValues(setting.value, values);
		if (!Objects.equals(current, setting.value)) {
			setting.value = current;
			SettingsUtil.save(WintonAPI.getSettings());
		}
		CycleButton<Float> btn = CycleButton.builder(v -> Component.literal(String.valueOf(v)), current)
			.withValues(values)
			.create(x, y, w, 20, WintonLang.text(labelKey), (b, v) -> {
				setting.value = v;
				SettingsUtil.save(WintonAPI.getSettings());
			});
		btn.setTooltip(tip(tipKey));
		addContentWidget(btn);
	}

	private void addCycleDouble(int x, int y, int w, String labelKey, String tipKey, Settings.Setting<Double> setting, Double... values) {
		Double current = snapToValues(setting.value, values);
		if (!Objects.equals(current, setting.value)) {
			setting.value = current;
			SettingsUtil.save(WintonAPI.getSettings());
		}
		CycleButton<Double> btn = CycleButton.builder(v -> Component.literal(String.valueOf(v)), current)
			.withValues(values)
			.create(x, y, w, 20, WintonLang.text(labelKey), (b, v) -> {
				setting.value = v;
				SettingsUtil.save(WintonAPI.getSettings());
			});
		btn.setTooltip(tip(tipKey));
		addContentWidget(btn);
	}

	@SafeVarargs
	private static <T extends Number & Comparable<T>> T snapToValues(T current, T... values) {
		if (current == null || values == null || values.length == 0) {
			return current;
		}
		for (T v : values) {
			if (v.equals(current)) {
				return current;
			}
		}
		T best = values[0];
		double bestDist = Math.abs(current.doubleValue() - best.doubleValue());
		for (int i = 1; i < values.length; i++) {
			double d = Math.abs(current.doubleValue() - values[i].doubleValue());
			if (d < bestDist) {
				bestDist = d;
				best = values[i];
			}
		}
		return best;
	}

	private void initMiniHud(int left, int right, int col, int y) {
		MiniHudConfig.ensureLoaded();
		int full = col * 2 + 12;

		y = addGroupHeader(left, y, full, Group.MINIHUD_GEN, "winton.minihud.group.generic");
		if (expanded.contains(Group.MINIHUD_GEN)) {
			y = addMiniHudToggle(left, right, col, y,
				"winton.minihud.cfg.info_hud", "winton.minihud.cfg.tip.info_hud",
				MiniHudConfig.infoHudEnabled(), MiniHudConfig::setInfoHud,
				"winton.minihud.cfg.overlays", "winton.minihud.cfg.tip.overlays",
				MiniHudConfig.overlaysEnabled(), MiniHudConfig::setOverlays);
			y = addMiniHudToggle(left, right, col, y,
				"winton.minihud.cfg.require_sneak", "winton.minihud.cfg.tip.require_sneak",
				MiniHudConfig.requireSneak(), MiniHudConfig::setRequireSneak,
				null, null, false, null);
			addCycleIntFree(left, y, col, "winton.minihud.cfg.slime_radius", "winton.minihud.cfg.tip.slime_radius",
				MiniHudConfig.slimeRadius(), v -> {
					MiniHudConfig.setSlimeRadius(v);
					rebuildWidgets();
				}, 4, 6, 8, 12, 16, 24);
			addCycleIntFree(right, y, col, "winton.minihud.cfg.update_interval", "winton.minihud.cfg.tip.update_interval",
				MiniHudConfig.updateIntervalTicks(), v -> {
					MiniHudConfig.setUpdateIntervalTicks(v);
					rebuildWidgets();
				}, 1, 2, 4, 5, 10);
			y += 22;
			addContentWidget(Button.builder(WintonLang.text("winton.minihud.btn.set_ref"), b -> {
				if (this.minecraft != null && this.minecraft.player != null) {
					MiniHudConfig.setRefPoint(this.minecraft.player.blockPosition());
					MiniHudLines.invalidate();
					if (this.minecraft.player != null) {
						this.minecraft.player.displayClientMessage(net.minecraft.network.chat.Component.literal(
							WintonLang.get("winton.minihud.msg.ref_set")), false);
					}
				}
			}).bounds(left, y, col, 20).tooltip(tip("winton.minihud.btn.tip.set_ref")).build());
			addContentWidget(Button.builder(WintonLang.text("winton.minihud.btn.clear_seed"), b -> {
				MiniHudConfig.clearSeed();
				MiniHudLines.invalidate();
			}).bounds(right, y, col, 20).tooltip(tip("winton.minihud.btn.tip.clear_seed")).build());
			y += 24;
			y = labeledTip(left, y, full, WintonLang.get("winton.minihud.help.seed"));
		}

		y = addGroupHeader(left, y, full, Group.MINIHUD_LINES, "winton.minihud.group.lines");
		if (expanded.contains(Group.MINIHUD_LINES)) {
			MiniHudInfoLine[] all = MiniHudInfoLine.values();
			for (int i = 0; i < all.length; i += 2) {
				MiniHudInfoLine a = all[i];
				MiniHudInfoLine b = i + 1 < all.length ? all[i + 1] : null;
				addMiniHudLineToggle(left, y, col, a);
				if (b != null) {
					addMiniHudLineToggle(right, y, col, b);
				}
				y += 22;
			}
		}

		y = addGroupHeader(left, y, full, Group.MINIHUD_OVERLAY, "winton.minihud.group.overlays");
		if (expanded.contains(Group.MINIHUD_OVERLAY)) {
			MiniHudOverlay[] all = MiniHudOverlay.values();
			for (int i = 0; i < all.length; i += 2) {
				MiniHudOverlay a = all[i];
				MiniHudOverlay b = i + 1 < all.length ? all[i + 1] : null;
				addMiniHudOverlayToggle(left, y, col, a);
				if (b != null) {
					addMiniHudOverlayToggle(right, y, col, b);
				}
				y += 22;
			}
			y = labeledTip(left, y, full, WintonLang.get("winton.minihud.help.license"));
		}
	}

	private int addMiniHudToggle(int left, int right, int col, int y,
		String leftKey, String leftTip, boolean leftOn, java.util.function.Consumer<Boolean> leftSet,
		String rightKey, String rightTip, boolean rightOn, java.util.function.Consumer<Boolean> rightSet) {
		addMiniHudCycle(left, y, col, leftKey, leftTip, leftOn, leftSet);
		if (rightKey != null && rightSet != null) {
			addMiniHudCycle(right, y, col, rightKey, rightTip, rightOn, rightSet);
		}
		return y + 22;
	}

	private void addMiniHudCycle(int x, int y, int w, String labelKey, String tipKey, boolean on,
		java.util.function.Consumer<Boolean> setter) {
		String label = WintonLang.get(labelKey) + ": " + (on ? "§aON" : "§cOFF");
		Button.Builder b = Button.builder(Component.literal(label), btn -> {
			setter.accept(!on);
			MiniHudLines.invalidate();
			rebuildWidgets();
		}).bounds(x, y, w, 20);
		if (tipKey != null) {
			b.tooltip(tip(tipKey));
		}
		addContentWidget(b.build());
	}

	private void addMiniHudLineToggle(int x, int y, int w, MiniHudInfoLine line) {
		boolean on = MiniHudConfig.line(line);
		String label = WintonLang.get(line.nameKey()) + ": " + (on ? "§aON" : "§cOFF");
		addContentWidget(Button.builder(Component.literal(label), b -> {
			MiniHudConfig.setLine(line, !on);
			MiniHudLines.invalidate();
			rebuildWidgets();
		}).bounds(x, y, w, 20).tooltip(tip(line.tipKey())).build());
	}

	private void addMiniHudOverlayToggle(int x, int y, int w, MiniHudOverlay o) {
		boolean on = MiniHudConfig.overlay(o);
		String label = WintonLang.get(o.nameKey()) + ": " + (on ? "§aON" : "§cOFF");
		addContentWidget(Button.builder(Component.literal(label), b -> {
			MiniHudConfig.setOverlay(o, !on);
			rebuildWidgets();
		}).bounds(x, y, w, 20).tooltip(tip(o.tipKey())).build());
	}

	private void addCycleIntFree(int x, int y, int w, String labelKey, String tipKey, int current,
		java.util.function.IntConsumer setter, Integer... values) {
		Integer snapped = snapToValues(current, values);
		CycleButton<Integer> btn = CycleButton.builder(v -> Component.literal(String.valueOf(v)), snapped)
			.withValues(values)
			.create(x, y, w, 20, WintonLang.text(labelKey), (b, v) -> setter.accept(v));
		btn.setTooltip(tip(tipKey));
		addContentWidget(btn);
	}

	private void initSettings(int left, int right, int col, int y) {
		Settings s = WintonAPI.getSettings();
		int full = col * 2 + 12;

		y = addGroupHeader(left, y, full, Group.MOVE, "winton.menu.group.move");
		if (expanded.contains(Group.MOVE)) {
			y = addBoolPair(left, right, col, y,
				new WintonMenuActions.BoolSetting("winton.menu.set.allowBreak", s.allowBreak),
				new WintonMenuActions.BoolSetting("winton.menu.set.placeOnly", s.placeOnly));
			y = addBoolPair(left, right, col, y,
				new WintonMenuActions.BoolSetting("winton.menu.set.allowSprint", s.allowSprint),
				new WintonMenuActions.BoolSetting("winton.menu.set.allowParkour", s.allowParkour));
			y = addBoolPair(left, right, col, y,
				new WintonMenuActions.BoolSetting("winton.menu.set.allowParkourPlace", s.allowParkourPlace),
				new WintonMenuActions.BoolSetting("winton.menu.set.allowParkourAscend", s.allowParkourAscend));
			y = addBoolPair(left, right, col, y,
				new WintonMenuActions.BoolSetting("winton.menu.set.allowDiagonalAscend", s.allowDiagonalAscend),
				new WintonMenuActions.BoolSetting("winton.menu.set.allowDiagonalDescend", s.allowDiagonalDescend));
			y = addBoolPair(left, right, col, y,
				new WintonMenuActions.BoolSetting("winton.menu.set.allowDownward", s.allowDownward),
				new WintonMenuActions.BoolSetting("winton.menu.set.allowInventory", s.allowInventory));
			y = addBoolPair(left, right, col, y,
				new WintonMenuActions.BoolSetting("winton.menu.set.sprintAscends", s.sprintAscends),
				new WintonMenuActions.BoolSetting("winton.menu.set.assumeStep", s.assumeStep));
			y = addBoolPair(left, right, col, y,
				new WintonMenuActions.BoolSetting("winton.menu.set.assumeWalkOnWater", s.assumeWalkOnWater),
				new WintonMenuActions.BoolSetting("winton.menu.set.assumeWalkOnLava", s.assumeWalkOnLava));
			y = addBoolPair(left, right, col, y,
				new WintonMenuActions.BoolSetting("winton.menu.set.allowWaterBucketFall", s.allowWaterBucketFall),
				new WintonMenuActions.BoolSetting("winton.menu.set.allowJumpAtBuildLimit", s.allowJumpAtBuildLimit));
			y = addBoolPair(left, right, col, y,
				new WintonMenuActions.BoolSetting("winton.menu.set.pauseMiningForFallingBlocks", s.pauseMiningForFallingBlocks),
				new WintonMenuActions.BoolSetting("winton.menu.set.sprintInWater", s.sprintInWater));
			addCycleInt(left, y, col, "winton.menu.set.maxFallHeightNoWater", "winton.tip.maxFallHeightNoWater",
				s.maxFallHeightNoWater, 1, 2, 3, 4, 5, 6, 8);
			addCycleInt(right, y, col, "winton.menu.set.maxFallHeightBucket", "winton.tip.maxFallHeightBucket",
				s.maxFallHeightBucket, 20, 40, 60, 80, 100, 120);
			y += 22;
			addCycleInt(left, y, col, "winton.menu.set.blockBreakSpeed", "winton.tip.blockBreakSpeed",
				s.blockBreakSpeed, 1, 2, 4, 6, 8, 10);
			addCycleFloat(right, y, col, "winton.menu.set.blockReachDistance", "winton.tip.blockReachDistance",
				s.blockReachDistance, 4.0F, 4.5F, 5.0F, 5.5F, 6.0F);
			y += 22;
			y = labeledTip(left, y, full, WintonLang.get("winton.menu.move.elytra_overworld"));
			y = addBoolPair(left, right, col, y,
				new WintonMenuActions.BoolSetting("winton.menu.set.elytraAllowAboveBuildLimit", s.elytraAllowAboveBuildLimit),
				new WintonMenuActions.BoolSetting("winton.menu.set.elytraAutoJump", s.elytraAutoJump));
			addCycleInt(left, y, col, "winton.menu.set.elytraLongDistanceThreshold", "winton.tip.elytraLongDistanceThreshold",
				s.elytraLongDistanceThreshold, 32, 100, 250, 500, 1000, 2000);
			addCycleDouble(right, y, col, "winton.menu.set.elytraFireworkSpeed", "winton.tip.elytraFireworkSpeed",
				s.elytraFireworkSpeed, 1.0D, 1.2D, 1.5D, 2.0D);
			y += 24;
		}

		y = addGroupHeader(left, y, full, Group.LOOK, "winton.menu.group.look");
		if (expanded.contains(Group.LOOK)) {
			y = addBoolPair(left, right, col, y,
				new WintonMenuActions.BoolSetting("winton.menu.set.freeLook", s.freeLook),
				new WintonMenuActions.BoolSetting("winton.menu.set.blockFreeLook", s.blockFreeLook));
			y = addBoolPair(left, right, col, y,
				new WintonMenuActions.BoolSetting("winton.menu.set.smoothLook", s.smoothLook),
				new WintonMenuActions.BoolSetting("winton.menu.set.antiCheatCompatibility", s.antiCheatCompatibility));
			y = addBoolPair(left, right, col, y,
				new WintonMenuActions.BoolSetting("winton.menu.set.elytraFreeLook", s.elytraFreeLook),
				new WintonMenuActions.BoolSetting("winton.menu.set.elytraSmoothLook", s.elytraSmoothLook));
			y = addBoolPair(left, right, col, y,
				new WintonMenuActions.BoolSetting("winton.menu.set.remainWithExistingLookDirection", s.remainWithExistingLookDirection),
				new WintonMenuActions.BoolSetting("winton.menu.set.lookInWalkDirection", s.lookInWalkDirection));
			addCycleBool(left, y, col, "winton.menu.set.autoTool", "winton.tip.autoTool", s.autoTool);
			addCycleInt(right, y, col, "winton.menu.set.smoothLookTicks", "winton.tip.smoothLookTicks",
				s.smoothLookTicks, 6, 8, 10, 12, 14, 16, 18);
			y += 22;
			addCycleDouble(left, y, col, "winton.menu.set.randomLooking113", "winton.tip.randomLooking113",
				s.randomLooking113, 0.0D, 0.05D, 0.1D, 0.4D);
			y += 24;
		}

		y = addGroupHeader(left, y, full, Group.RENDER, "winton.menu.group.render");
		if (expanded.contains(Group.RENDER)) {
			y = addBoolPair(left, right, col, y,
				new WintonMenuActions.BoolSetting("winton.menu.set.renderGoal", s.renderGoal),
				new WintonMenuActions.BoolSetting("winton.menu.set.renderPath", s.renderPath));
			y = addBoolPair(left, right, col, y,
				new WintonMenuActions.BoolSetting("winton.menu.set.renderGoalIgnoreDepth", s.renderGoalIgnoreDepth),
				new WintonMenuActions.BoolSetting("winton.menu.set.renderPathIgnoreDepth", s.renderPathIgnoreDepth));
			y = addBoolPair(left, right, col, y,
				new WintonMenuActions.BoolSetting("winton.menu.set.renderPathAsLine", s.renderPathAsLine),
				new WintonMenuActions.BoolSetting("winton.menu.set.fadePath", s.fadePath));
			y = addBoolPair(left, right, col, y,
				new WintonMenuActions.BoolSetting("winton.menu.set.renderGoalAnimated", s.renderGoalAnimated),
				new WintonMenuActions.BoolSetting("winton.menu.set.renderSelectionBoxes", s.renderSelectionBoxes));
			addCycleBool(left, y, col, "winton.menu.set.renderGoalXZBeacon", "winton.tip.renderGoalXZBeacon", s.renderGoalXZBeacon);
			addCycleBool(right, y, col, "winton.menu.set.renderCachedChunks", "winton.tip.renderCachedChunks", s.renderCachedChunks);
			y += 22;
			addCycleFloat(left, y, col, "winton.menu.set.cachedChunksOpacity", "winton.tip.cachedChunksOpacity",
				s.cachedChunksOpacity, 0.25F, 0.5F, 0.75F, 1.0F);
			y += 24;
		}

		y = addGroupHeader(left, y, full, Group.MINE_DET, "winton.menu.group.mine");
		if (expanded.contains(Group.MINE_DET)) {
			y = addBoolPair(left, right, col, y,
				new WintonMenuActions.BoolSetting("winton.menu.set.legitMine", s.legitMine),
				new WintonMenuActions.BoolSetting("winton.menu.set.mineScanDroppedItems", s.mineScanDroppedItems));
			y = addBoolPair(left, right, col, y,
				new WintonMenuActions.BoolSetting("winton.menu.set.walkWhileBreaking", s.walkWhileBreaking),
				new WintonMenuActions.BoolSetting("winton.menu.set.legitMineIncludeDiagonals", s.legitMineIncludeDiagonals));
			y = addBoolPair(left, right, col, y,
				new WintonMenuActions.BoolSetting("winton.menu.set.exploreForBlocks", s.exploreForBlocks),
				new WintonMenuActions.BoolSetting("winton.menu.set.allowOnlyExposedOres", s.allowOnlyExposedOres));
			y = addBoolPair(left, right, col, y,
				new WintonMenuActions.BoolSetting("winton.menu.set.itemSaver", s.itemSaver),
				new WintonMenuActions.BoolSetting("winton.menu.set.blacklistClosestOnFailure", s.blacklistClosestOnFailure));
			addCycleBool(left, y, col, "winton.menu.set.cancelOnGoalInvalidation", "winton.tip.cancelOnGoalInvalidation", s.cancelOnGoalInvalidation);
			addCycleInt(right, y, col, "winton.menu.set.itemSaverThreshold", "winton.tip.itemSaverThreshold",
				s.itemSaverThreshold, 5, 10, 15, 20, 30, 50);
			y += 22;
			addCycleInt(left, y, col, "winton.menu.set.mineGoalUpdateInterval", "winton.tip.mineGoalUpdateInterval",
				s.mineGoalUpdateInterval, 5, 10, 15, 20, 30, 40);
			addCycleInt(right, y, col, "winton.menu.set.mineMaxOreLocationsCount", "winton.tip.mineMaxOreLocationsCount",
				s.mineMaxOreLocationsCount, 16, 24, 32, 40, 64, 96);
			y += 22;
			addCycleInt(left, y, col, "winton.menu.set.allowOnlyExposedOresDistance", "winton.tip.allowOnlyExposedOresDistance",
				s.allowOnlyExposedOresDistance, 1, 2, 3);
			y += 24;
		}

		y = addGroupHeader(left, y, full, Group.AVOID, "winton.menu.group.avoid");
		if (expanded.contains(Group.AVOID)) {
			y = addBoolPair(left, right, col, y,
				new WintonMenuActions.BoolSetting("winton.menu.set.avoidance", s.avoidance),
				new WintonMenuActions.BoolSetting("winton.menu.set.enterPortal", s.enterPortal));
			addCycleInt(left, y, col, "winton.menu.set.mobAvoidanceRadius", "winton.tip.mobAvoidanceRadius",
				s.mobAvoidanceRadius, 4, 6, 8, 12, 16);
			addCycleInt(right, y, col, "winton.menu.set.mobSpawnerAvoidanceRadius", "winton.tip.mobSpawnerAvoidanceRadius",
				s.mobSpawnerAvoidanceRadius, 8, 12, 16, 24, 32);
			y += 22;
			addCycleInt(left, y, col, "winton.menu.set.lavaAvoidRadius", "winton.tip.lavaAvoidRadius",
				s.lavaAvoidRadius, 0, 3, 4, 8, 12, 16);
			y += 24;
		}

		y = addGroupHeader(left, y, full, Group.SAFETY, "winton.menu.group.safety");
		if (expanded.contains(Group.SAFETY)) {
			y = addBoolPair(left, right, col, y,
				new WintonMenuActions.BoolSetting("winton.menu.set.autoSafety", s.autoSafety),
				new WintonMenuActions.BoolSetting("winton.menu.set.autoEat", s.autoEat));
			addCycleBool(left, y, col, "winton.menu.set.safetySealLava", "winton.tip.safetySealLava", s.safetySealLava);
			y += 22;
			addCycleFloat(left, y, col, "winton.menu.set.safetyHealthThreshold", "winton.tip.safetyHealthThreshold",
				s.safetyHealthThreshold, 3.0F, 4.0F, 6.0F, 8.0F, 10.0F);
			addCycleInt(right, y, col, "winton.menu.set.safetySealLavaRadius", "winton.tip.safetySealLavaRadius",
				s.safetySealLavaRadius, 1, 2, 3, 4);
			y += 24;
		}

		y = addGroupHeader(left, y, full, Group.PATH, "winton.menu.group.path");
		if (expanded.contains(Group.PATH)) {
			addContentWidget(Button.builder(WintonLang.text("winton.menu.path_fast"), b -> {
				WintonPerf.applyPathFast();
				if (minecraft != null && minecraft.player != null) {
					minecraft.player.displayClientMessage(WintonLang.text("winton.menu.path_fast_done"), false);
				}
				rebuildWidgets();
			}).bounds(left, y, full, 20).tooltip(tip("winton.tip.path_fast")).build());
			y += 22;
			y = addBoolPair(left, right, col, y,
				new WintonMenuActions.BoolSetting("winton.menu.set.splicePath", s.splicePath),
				new WintonMenuActions.BoolSetting("winton.menu.set.cancelOnGoalInvalidation", s.cancelOnGoalInvalidation));
			addCycleInt(left, y, col, "winton.menu.set.costVerificationLookahead", "winton.tip.costVerificationLookahead",
				s.costVerificationLookahead, 1, 2, 3, 5, 8);
			addCycleInt(right, y, col, "winton.menu.set.planningTickLookahead", "winton.tip.planningTickLookahead",
				s.planningTickLookahead, 50, 100, 150, 200);
			y += 24;
		}

		y = addGroupHeader(left, y, full, Group.ELYTRA, "winton.menu.group.elytra");
		if (expanded.contains(Group.ELYTRA)) {
			y = addBoolPair(left, right, col, y,
				new WintonMenuActions.BoolSetting("winton.menu.set.elytraTermsAccepted", s.elytraTermsAccepted),
				new WintonMenuActions.BoolSetting("winton.menu.set.elytraAutoJump", s.elytraAutoJump));
			y = addBoolPair(left, right, col, y,
				new WintonMenuActions.BoolSetting("winton.menu.set.elytraConserveFireworks", s.elytraConserveFireworks),
				new WintonMenuActions.BoolSetting("winton.menu.set.elytraAutoSwap", s.elytraAutoSwap));
			y = addBoolPair(left, right, col, y,
				new WintonMenuActions.BoolSetting("winton.menu.set.elytraAllowEmergencyLand", s.elytraAllowEmergencyLand),
				new WintonMenuActions.BoolSetting("winton.menu.set.elytraPredictTerrain", s.elytraPredictTerrain));
			addCycleDouble(left, y, col, "winton.menu.set.elytraFireworkSpeed", "winton.tip.elytraFireworkSpeed",
				s.elytraFireworkSpeed, 1.0D, 1.2D, 1.5D, 2.0D);
			y += 24;
		}

		y = addGroupHeader(left, y, full, Group.BUILD_DET, "winton.menu.group.build");
		if (expanded.contains(Group.BUILD_DET)) {
			y = addBoolPair(left, right, col, y,
				new WintonMenuActions.BoolSetting("winton.menu.set.buildInLayers", s.buildInLayers),
				new WintonMenuActions.BoolSetting("winton.menu.set.buildOnlySelection", s.buildOnlySelection));
			y = addBoolPair(left, right, col, y,
				new WintonMenuActions.BoolSetting("winton.menu.set.layerOrder", s.layerOrder),
				new WintonMenuActions.BoolSetting("winton.menu.set.mapArtMode", s.mapArtMode));
			y = addBoolPair(left, right, col, y,
				new WintonMenuActions.BoolSetting("winton.menu.set.skipFailedLayers", s.skipFailedLayers),
				new WintonMenuActions.BoolSetting("winton.menu.set.okIfWater", s.okIfWater));
			addCycleBool(left, y, col, "winton.menu.set.buildIgnoreExisting", "winton.tip.buildIgnoreExisting", s.buildIgnoreExisting);
			y += 24;
		}

		y = addGroupHeader(left, y, full, Group.CHAT, "winton.menu.group.chat");
		if (expanded.contains(Group.CHAT)) {
			y = addBoolPair(left, right, col, y,
				new WintonMenuActions.BoolSetting("winton.menu.set.chatControl", s.chatControl),
				new WintonMenuActions.BoolSetting("winton.menu.set.prefixControl", s.prefixControl));
			y = addBoolPair(left, right, col, y,
				new WintonMenuActions.BoolSetting("winton.menu.set.echoCommands", s.echoCommands),
				new WintonMenuActions.BoolSetting("winton.menu.set.logAsToast", s.logAsToast));
			y = addBoolPair(left, right, col, y,
				new WintonMenuActions.BoolSetting("winton.menu.set.censorCoordinates", s.censorCoordinates),
				new WintonMenuActions.BoolSetting("winton.menu.set.shortWinTonePrefix", s.shortWintonPrefix));
			y = addBoolPair(left, right, col, y,
				new WintonMenuActions.BoolSetting("winton.menu.set.doBedWaypoints", s.doBedWaypoints),
				new WintonMenuActions.BoolSetting("winton.menu.set.doDeathWaypoints", s.doDeathWaypoints));
			addCycleBool(left, y, col, "winton.menu.set.disconnectOnArrival", "winton.tip.disconnectOnArrival", s.disconnectOnArrival);
			y += 24;
		}

		y = addGroupHeader(left, y, full, Group.PERF, "winton.menu.group.perf");
		if (expanded.contains(Group.PERF)) {
			addContentWidget(Button.builder(WintonLang.text("winton.menu.perf_max"), b -> {
				WintonPerf.applyMax();
				if (minecraft != null && minecraft.player != null) {
					minecraft.player.displayClientMessage(WintonLang.text("winton.menu.perf_max_done"), false);
				}
				rebuildWidgets();
			}).bounds(left, y, full, 20).tooltip(tip("winton.tip.perf_max")).build());
			y += 22;
			addCycleBool(left, y, col, "winton.menu.set.repackOnAnyBlockChange", "winton.tip.repackOnAnyBlockChange", s.repackOnAnyBlockChange);
			addCycleBool(right, y, col, "winton.menu.set.chatDebug", "winton.tip.chatDebug", s.chatDebug);
			y += 22;
			addCycleInt(left, y, col, "winton.menu.set.chunkPackerQueueMaxSize", "winton.tip.chunkPackerQueueMaxSize",
				s.chunkPackerQueueMaxSize, 128, 256, 384, 512, 768);
			CycleButton<Float> width = CycleButton.builder(v -> Component.literal(String.format("%.0f", v)), s.pathRenderLineWidthPixels.value)
				.withValues(1F, 2F, 3F, 4F)
				.create(right, y, col, 20, WintonLang.text("winton.menu.set.pathRenderLineWidth"), (b, v) -> {
					s.pathRenderLineWidthPixels.value = v;
					SettingsUtil.save(s);
				});
			width.setTooltip(tip("winton.tip.pathRenderLineWidth"));
			addContentWidget(width);
			y += 24;
		}

		y = addGroupHeader(left, y, full, Group.KEYS, "winton.menu.group.keys");
		if (expanded.contains(Group.KEYS)) {
			String label = WintonMenuKeys.isListeningForRebind()
				? WintonLang.get("winton.menu.key_listening")
				: WintonLang.get("winton.menu.key_bind") + ": " + WintonMenuKeys.displayLabel();
			addContentWidget(Button.builder(Component.literal(label), b -> {
				WintonMenuKeys.beginRebind();
				rebuildWidgets();
			}).bounds(left, y, full, 20).build());
			y += 22;
			addContentWidget(Button.builder(WintonLang.text("winton.menu.keybinds"), b -> {
				if (this.minecraft != null) {
					this.minecraft.setScreen(new net.minecraft.client.gui.screens.options.controls.KeyBindsScreen(this, this.minecraft.options));
				}
			}).bounds(left, y, full, 20).build());
		}
	}

	private int addGroupHeader(int x, int y, int w, Group g, String langKey) {
		boolean open = expanded.contains(g);
		String arrow = open ? "▾ " : "▸ ";
		addContentWidget(Button.builder(Component.literal(arrow + WintonLang.get(langKey)), b -> {
			if (expanded.contains(g)) {
				expanded.remove(g);
			} else {
				expanded.add(g);
			}
			persistUi();
			rebuildWidgets();
		}).bounds(x, y, w, 20).build());
		return y + 22;
	}

	/** Prefer live selection / pending pos over empty saved fields. */
	private void prefillClearFromWorld() {
		winton.api.utils.BetterBlockPos p1 = WintonClearMode.pendingPos1();
		winton.api.utils.BetterBlockPos p2 = null;
		var w = WintonAPI.getProvider().getPrimaryWinton();
		if (w != null) {
			var sels = w.getSelectionManager().getSelections();
			if (sels.length > 0) {
				if (p1 == null) {
					p1 = sels[sels.length - 1].pos1();
				}
				p2 = sels[sels.length - 1].pos2();
			}
		}
		if (p1 != null) {
			if (clear1x.getValue().isEmpty()) {
				clear1x.setValue(String.valueOf(p1.x));
			}
			if (clear1y.getValue().isEmpty()) {
				clear1y.setValue(String.valueOf(p1.y));
			}
			if (clear1z.getValue().isEmpty()) {
				clear1z.setValue(String.valueOf(p1.z));
			}
		}
		if (p2 != null) {
			if (clear2x.getValue().isEmpty()) {
				clear2x.setValue(String.valueOf(p2.x));
			}
			if (clear2y.getValue().isEmpty()) {
				clear2y.setValue(String.valueOf(p2.y));
			}
			if (clear2z.getValue().isEmpty()) {
				clear2z.setValue(String.valueOf(p2.z));
			}
		}
	}

	private int addBoolPair(int left, int right, int col, int y,
							WintonMenuActions.BoolSetting a, WintonMenuActions.BoolSetting b) {
		addCycleBool(left, y, col, a.langKey(), tipKeyFor(a.langKey()), a.setting());
		addCycleBool(right, y, col, b.langKey(), tipKeyFor(b.langKey()), b.setting());
		return y + 22;
	}

	private static String tipKeyFor(String setKey) {
		if (setKey != null && setKey.startsWith("winton.menu.set.")) {
			return "winton.tip." + setKey.substring("winton.menu.set.".length());
		}
		return setKey;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (WintonMenuKeys.consumeRebindKey(event)) {
			rebuildWidgets();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		WintonMenuKeys.cancelRebind();
		persistUi();
		WintonMenuConfig.saveOreToggles(oreToggles);
		WintonMenuActions.persistSettings();
		super.onClose();
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		scroll = Math.max(0, scroll - (int) (scrollY * 14));
		persistUi();
		rebuildWidgets();
		return true;
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		graphics.drawCenteredString(this.font, "WinTone", this.width / 2, 4, 0xFFFFFF);
		graphics.drawCenteredString(this.font, WintonLang.get("winton.menu.subtitle"), this.width / 2, this.height - 38, 0x808080);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
