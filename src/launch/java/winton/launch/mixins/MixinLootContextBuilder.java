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

package winton.launch.mixins;

import winton.api.utils.BlockOptionalMeta;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ReloadableServerRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.loot.LootContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LootContext.Builder.class)
public abstract class MixinLootContextBuilder {

    @Shadow public abstract ServerLevel getLevel();

    @Redirect(method = "create", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;reloadableRegistries()Lnet/minecraft/server/ReloadableServerRegistries$Holder;"))
    private ReloadableServerRegistries.Holder create(MinecraftServer instance) {
        if (instance != null) {
            return instance.reloadableRegistries();
        }
        if (getLevel() instanceof BlockOptionalMeta.ServerLevelStub sls) {
            return sls.holder();
        }
        return null;
    }

}
