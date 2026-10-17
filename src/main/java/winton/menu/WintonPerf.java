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

import winton.api.Settings;
import winton.api.WintonAPI;
import winton.api.utils.SettingsUtil;

/** FPS / idle / shaft-safe preset: no path render, no place spam, low idle cost. */
public final class WintonPerf {
	private WintonPerf() {
	}

	public static void applyMax() {
		Settings s = WintonAPI.getSettings();

		s.freeLook.value = true;
		s.blockFreeLook.value = false;
		s.smoothLook.value = true;
		s.smoothLookTicks.value = 10;
		s.elytraSmoothLook.value = true;
		s.randomLooking.value = 0.008D;
		s.randomLooking113.value = 0.0D;
		s.remainWithExistingLookDirection.value = false;
		s.lookInWalkDirection.value = true;
		s.antiCheatCompatibility.value = true;
		s.itemSaver.value = true;
		s.legitMineIncludeDiagonals.value = true;
		s.allowOnlyExposedOres.value = false;
		s.allowOnlyExposedOresDistance.value = 1;
		s.sprintInWater.value = true;
		s.assumeWalkOnWater.value = false;
		s.walkOnWaterOnePenalty.value = 8.0D;
		s.allowPlace.value = true;
		s.allowInventory.value = true;
		s.allowDiagonalAscend.value = false;
		s.allowParkourAscend.value = false;
		s.allowParkour.value = false;
		s.autoSafety.value = true;
		s.autoEat.value = false;
		s.exploreForBlocks.value = true;
		s.lavaAvoidRadius.value = 4;
		s.lavaAvoidCoefficient.value = 2.0D;
		s.assumeWalkOnLava.value = false;
		s.safetySealLava.value = false;
		s.splicePath.value = true;

		s.renderPath.value = true;
		s.renderGoal.value = true;
		s.renderPathAsLine.value = true;
		s.fadePath.value = false;
		s.renderGoalAnimated.value = false;
		s.renderSelectionBoxes.value = false;
		s.renderGoalIgnoreDepth.value = false;
		s.renderPathIgnoreDepth.value = false;
		s.renderSelectionBoxesIgnoreDepth.value = false;
		s.renderGoalXZBeacon.value = false;
		s.renderCachedChunks.value = false;
		s.renderSelection.value = false;
		s.renderSelectionCorners.value = false;
		s.pathRenderLineWidthPixels.value = 1F;
		s.goalRenderLineWidthPixels.value = 1F;

		s.repackOnAnyBlockChange.value = false;
		s.avoidance.value = false;
		s.chatDebug.value = false;
		s.logAsToast.value = false;
		s.echoCommands.value = false;
		s.desktopNotifications.value = false;
		s.slowPath.value = false;
		s.backfill.value = false;
		s.chunkCaching.value = true;
		s.pruneRegionsFromRAM.value = true;
		s.cutoffAtLoadBoundary.value = true;
		s.costVerificationLookahead.value = 1;

		s.chunkPackerQueueMaxSize.value = 128;
		s.maxPathHistoryLength.value = 24;
		s.pathHistoryCutoffAmount.value = 8;
		s.mineMaxOreLocationsCount.value = 32;
		s.mineGoalUpdateInterval.value = 10;
		s.mineScanChunkRadius.value = 32;
		s.mineScanRadiusAuto.value = true;
		s.allowWaterBucketFall.value = false;
		s.maxFallHeightBucket.value = 20;
		s.maxFallHeightNoWater.value = 3;
		s.minYLevelWhileMining.value = 0;
		s.maxYLevelWhileMining.value = 2031;
		s.pathingMaxChunkBorderFetch.value = 12;
		s.primaryTimeoutMS.value = 800L;
		s.failureTimeoutMS.value = 2800L;
		s.planAheadPrimaryTimeoutMS.value = 3200L;
		s.planAheadFailureTimeoutMS.value = 5500L;
		s.planningTickLookahead.value = 150;
		s.costHeuristic.value = 4.6D;
		s.pauseMiningForFallingBlocks.value = true;

		SettingsUtil.save(s);
	}

	/**
	 * Faster / less thrashy pathing. May dip FPS a bit while calculating paths.
	 */
	public static void applyPathFast() {
		Settings s = WintonAPI.getSettings();
		s.splicePath.value = true;
		s.mineGoalUpdateInterval.value = 8;
		s.primaryTimeoutMS.value = 700L;
		s.failureTimeoutMS.value = 2200L;
		s.planAheadPrimaryTimeoutMS.value = 3500L;
		s.planAheadFailureTimeoutMS.value = 6000L;
		s.planningTickLookahead.value = 160;
		s.costVerificationLookahead.value = 2;
		s.costHeuristic.value = 4.85D;
		s.pathingMaxChunkBorderFetch.value = 14;
		s.blacklistClosestOnFailure.value = true;
		SettingsUtil.save(s);
	}
}
