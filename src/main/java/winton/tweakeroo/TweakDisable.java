/*
 * This file is part of Winton.
 *
 * Derived from Tweakeroo (https://github.com/maruohon/tweakeroo).
 * Copyright (c) masa. Modifications Copyright (c) WinTone contributors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * LGPL-3.0-only. See LICENSE_TWEAKEROO.txt and NOTICE_TWEAKEROO.txt.
 */
package winton.tweakeroo;

/** All upstream DisableToggle ids. */
public enum TweakDisable {
	BLOCK_BREAK_PARTICLES("disableBlockBreakingParticles", true),
	DOUBLE_TAP_SPRINT("disableDoubleTapSprint", true),
	BOSS_FOG("disableBossFog", true),
	CHRISTMAS_CHESTS("disableChristmasChests", true),
	CLIENT_ENTITY_UPDATES("disableClientEntityUpdates", true),
	DEAD_MOB_RENDERING("disableDeadMobRendering", true),
	DEAD_MOB_TARGETING("disableDeadMobTargeting", true),
	ENTITY_RENDERING("disableEntityRendering", true),
	ENTITY_TICKING("disableEntityTicking", false),
	FALLING_BLOCK_RENDER("disableFallingBlockEntityRendering", true),
	FP_POTION_EFFECTS("disableFirstPersonPotionEffects", true),
	INVENTORY_EFFECTS("disableInventoryEffectRendering", true),
	ITEM_GLINT("disableItemGlint", true),
	ITEM_SWITCH_COOLDOWN("disableItemSwitchRenderCooldown", true),
	LIGHT_UPDATES("disableLightUpdates", false),
	LIGHT_UPDATES_ALL("disableLightUpdatesAll", false),
	MOB_SPAWNER_MOB_RENDER("disableMobSpawnerMobRendering", true),
	NETHER_FOG("disableNetherFog", true),
	OBSERVER("disableObserver", false),
	OBSERVER_PLACE_UPDATE("disableObserverPlaceUpdate", false),
	OFFHAND_RENDERING("disableOffhandRendering", true),
	PARTICLES("disableParticles", true),
	PORTAL_GUI_CLOSING("disablePortalGuiClosing", true),
	RAIN_EFFECTS("disableRainEffects", true),
	RENDER_DISTANCE_FOG("disableRenderDistanceFog", true),
	SCOREBOARD_RENDERING("disableScoreboardRendering", true),
	SIGN_GUI("disableSignGui", true),
	SHULKER_BOX_TOOLTIP("disableShulkerBoxTooltip", true),
	SLIME_BLOCK_SLOWDOWN("disableSlimeBlockSlowdown", true),
	SOUNDS_ALL("disableSoundsAll", true),
	SOUNDS_LIST("disableSoundsList", false),
	TILE_ENTITY_RENDERING("disableTileEntityRendering", true),
	TILE_ENTITY_TICKING("disableTileEntityTicking", false),
	VILLAGER_TRADE_LOCKING("disableVillagerTradeLocking", false),
	WALL_UNSPRINT("disableWallUnsprint", true);

	public final String configId;
	public final boolean implemented;

	TweakDisable(String configId, boolean implemented) {
		this.configId = configId;
		this.implemented = implemented;
	}

	public String nameKey() { return "winton.tweak.dis." + configId.toLowerCase(); }
	public String tipKey() { return "winton.tweak.dis.tip." + configId.toLowerCase(); }
}
