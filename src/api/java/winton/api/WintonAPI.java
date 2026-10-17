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

package winton.api;

import winton.api.utils.SettingsUtil;

/**
 * Exposes the {@link IWintonProvider} instance and the {@link Settings} instance for API usage.
 *
 * @author Brady
 * @since 9/23/2018
 */
public final class WintonAPI {

    private static final IWintonProvider provider;
    private static final Settings settings;

    static {
        settings = new Settings();
        SettingsUtil.readAndApply(settings, SettingsUtil.SETTINGS_DEFAULT_NAME);
        // Hard defaults: do not steal hotbar food while idle, and do not wander with no goal.
        applySafeDefaults(settings);

        try {
            provider = (IWintonProvider) Class.forName("winton.WintonProvider").newInstance();
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }

    /** Kill auto-eat hotbar steal + prefer max mine scan over random walking + FPS-safe render. */
    public static void applySafeDefaults(Settings s) {
        if (s == null) {
            return;
        }
        s.autoEat.value = false;
        s.exploreForBlocks.value = false;
        s.mineScanRadiusAuto.value = true;
        s.mineScanChunkRadius.value = 32;
        s.allowBreak.value = true;
        s.mineScanDroppedItems.value = true;
        s.renderPath.value = false;
        s.renderGoal.value = false;
        s.fadePath.value = false;
        s.renderSelection.value = false;
        s.renderSelectionCorners.value = false;
        s.mineGoalUpdateInterval.value = 40;
    }

    public static IWintonProvider getProvider() {
        return WintonAPI.provider;
    }

    public static Settings getSettings() {
        return WintonAPI.settings;
    }
}
