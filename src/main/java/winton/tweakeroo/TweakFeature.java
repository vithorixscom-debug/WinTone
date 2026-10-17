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

/** All upstream FeatureToggle ids. implemented=true means live on 1.21.11 client. */
public enum TweakFeature {
	ACCURATE_BLOCK_PLACEMENT("tweakAccurateBlockPlacement", false),
	AFTER_CLICKER("tweakAfterClicker", false),
	AIM_LOCK("tweakAimLock", true),
	ANGEL_BLOCK("tweakAngelBlock", false),
	BLOCK_BREAKING_PARTICLES("tweakBlockBreakingParticleTweaks", true),
	BLOCK_MODEL_OVERRIDE("tweakBlockModelOverride", false),
	BLOCK_PLACEMENT_Y_MIRROR("tweakBlockPlacementYMirror", false),
	BLOCK_REACH_OVERRIDE("tweakBlockReachOverride", false),
	BLOCK_RENDER_TYPE_OVERRIDE("tweakBlockRenderTypeOverride", false),
	BREAKING_GRID("tweakBreakingGrid", false),
	BREAKING_RESTRICTION("tweakBreakingRestriction", false),
	CHAT_BACKGROUND_COLOR("tweakChatBackgroundColor", false),
	CHAT_PERSISTENT_TEXT("tweakChatPersistentText", false),
	CHAT_TIMESTAMP("tweakChatTimestamp", true),
	CHUNK_RENDER_MAIN_THREAD("tweakChunkRenderOnMainThread", false),
	CHUNK_RENDER_TIMEOUT("tweakChunkRenderTimeoutOverride", false),
	CLOUD_HEIGHT_OVERRIDE("tweakCloudHeightOverride", true),
	COMMAND_BLOCK_EXTRA_FIELDS("tweakCommandBlockExtraFields", false),
	CUSTOM_FLAT_PRESETS("tweakCustomFlatPresets", false),
	DEBUG_PIE_CHART_SCALE("tweakDebugPieChartScale", false),
	ELYTRA_CAMERA("tweakElytraCamera", true),
	SHULKERBOX_STACKING("tweakEmptyShulkerBoxesStack", false),
	SHULKERBOX_STACK_GROUND("tweakEmptyShulkerBoxesStackOnGround", false),
	EXPLOSION_REDUCED_PARTICLES("tweakExplosionReducedParticles", true),
	F3_CURSOR("tweakF3Cursor", true),
	FAKE_SNEAKING("tweakFakeSneaking", true),
	FAST_BLOCK_PLACEMENT("tweakFastBlockPlacement", false),
	FAST_LEFT_CLICK("tweakFastLeftClick", true),
	FAST_RIGHT_CLICK("tweakFastRightClick", true),
	FILL_CLONE_LIMIT("tweakFillCloneLimit", false),
	FLY_SPEED("tweakFlySpeed", true),
	FLEXIBLE_BLOCK_PLACEMENT("tweakFlexibleBlockPlacement", false),
	FREE_CAMERA("tweakFreeCamera", true),
	GAMMA_OVERRIDE("tweakGammaOverride", true),
	HAND_RESTOCK("tweakHandRestock", true),
	HANGABLE_ENTITY_BYPASS("tweakHangableEntityBypass", false),
	HOLD_ATTACK("tweakHoldAttack", true),
	HOLD_USE("tweakHoldUse", true),
	HOTBAR_SCROLL("tweakHotbarScroll", true),
	HOTBAR_SLOT_CYCLE("tweakHotbarSlotCycle", false),
	HOTBAR_SLOT_RANDOMIZER("tweakHotbarSlotRandomizer", false),
	HOTBAR_SWAP("tweakHotbarSwap", true),
	INVENTORY_PREVIEW("tweakInventoryPreview", true),
	ITEM_UNSTACKING_PROTECTION("tweakItemUnstackingProtection", false),
	LAVA_VISIBILITY("tweakLavaVisibility", true),
	LLAMA_STEERING("tweakLlamaSteering", false),
	MAP_PREVIEW("tweakMapPreview", true),
	MATCHING_SKY_FOG("tweakMatchingSkyFog", true),
	MOVEMENT_KEYS("tweakMovementKeysLast", false),
	PERIODIC_ATTACK("tweakPeriodicAttack", true),
	PERIODIC_USE("tweakPeriodicUse", true),
	PERMANENT_SNEAK("tweakPermanentSneak", true),
	PERMANENT_SPRINT("tweakPermanentSprint", true),
	PICK_BEFORE_PLACE("tweakPickBeforePlace", false),
	PLACEMENT_GRID("tweakPlacementGrid", false),
	PLACEMENT_LIMIT("tweakPlacementLimit", false),
	PLACEMENT_RESTRICTION("tweakPlacementRestriction", false),
	PLACEMENT_REST_FIRST("tweakPlacementRestrictionFirst", false),
	PLACEMENT_REST_HAND("tweakPlacementRestrictionHand", false),
	PLAYER_INVENTORY_PEEK("tweakPlayerInventoryPeek", false),
	PLAYER_LIST_ALWAYS_ON("tweakPlayerListAlwaysVisible", true),
	PLAYER_ON_FIRE_SCALE("tweakPlayerOnFireScale", false),
	POTION_WARNING("tweakPotionWarning", true),
	PRINT_DEATH_COORDINATES("tweakPrintDeathCoordinates", true),
	RELAXED_BLOCK_PLACEMENT("tweakRelaxedBlockPlacement", false),
	RENDER_EDGE_CHUNKS("tweakRenderEdgeChunks", false),
	RENDER_INVISIBLE_ENTITIES("tweakRenderInvisibleEntities", false),
	RENDER_LIMIT_ENTITIES("tweakRenderLimitEntities", true),
	REPAIR_MODE("tweakRepairMode", true),
	SHULKERBOX_DISPLAY("tweakShulkerBoxDisplay", true),
	SIGN_COPY("tweakSignCopy", false),
	SNAP_AIM("tweakSnapAim", true),
	SNAP_AIM_LOCK("tweakSnapAimLock", true),
	SPECTATOR_TELEPORT("tweakSpectatorTeleport", false),
	STATIC_FOV("tweakStaticFov", true),
	STRUCTURE_BLOCK_LIMIT("tweakStructureBlockLimit", false),
	SWAP_ALMOST_BROKEN_TOOLS("tweakSwapAlmostBrokenTools", true),
	TAB_COMPLETE_COORDINATE("tweakTabCompleteCoordinate", false),
	TILE_RENDER_DISTANCE("tweakTileEntityRenderDistance", false),
	TOOL_SWITCH("tweakToolSwitch", true),
	WORLD_LIST_DATE_FORMAT("tweakWorldListDateFormat", false),
	ZOOM("tweakZoom", true);

	public final String configId;
	public final boolean implemented;

	TweakFeature(String configId, boolean implemented) {
		this.configId = configId;
		this.implemented = implemented;
	}

	public String nameKey() { return "winton.tweak.feat." + configId.toLowerCase(); }
	public String tipKey() { return "winton.tweak.feat.tip." + configId.toLowerCase(); }
}
