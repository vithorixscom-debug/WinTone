/*
 * This file is part of Winton.
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
 *
 * Based on Baritone (https://github.com/cabaletta/baritone).
 * Copyright (c) Baritone contributors. Modifications Copyright (c) WinTone contributors.
 * Compatibility entry: mods may still call baritone.api.BaritoneAPI.
 */

package baritone.api;

import winton.api.IWinton;
import winton.api.IWintonProvider;
import winton.api.Settings;
import winton.api.WintonAPI;

/**
 * Drop-in style entry for mods that expect {@code baritone.api.BaritoneAPI}.
 * Delegates to Winton's live provider/settings.
 */
public final class BaritoneAPI {
	private BaritoneAPI() {
	}

	public static IBaritoneProvider getProvider() {
		return BaritoneProviderBridge.INSTANCE;
	}

	/** Same settings instance the player configures as Winton. */
	public static Settings getSettings() {
		return WintonAPI.getSettings();
	}

	/**
	 * Bridge provider: Baritone method names → Winton instances.
	 */
	public interface IBaritoneProvider {
		IWinton getPrimaryBaritone();

		java.util.List<IWinton> getAllBaritones();

		default IWinton getBaritoneForPlayer(net.minecraft.client.player.LocalPlayer player) {
			return WintonAPI.getProvider().getWintonForPlayer(player);
		}
	}

	private enum BaritoneProviderBridge implements IBaritoneProvider {
		INSTANCE;

		@Override
		public IWinton getPrimaryBaritone() {
			return WintonAPI.getProvider().getPrimaryWinton();
		}

		@Override
		public java.util.List<IWinton> getAllBaritones() {
			return WintonAPI.getProvider().getAllWintons();
		}
	}
}
