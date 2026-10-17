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

import winton.Winton;
import winton.api.WintonAPI;
import winton.api.IWinton;
import winton.api.event.events.BlockChangeEvent;
import winton.api.event.events.ChatEvent;
import winton.api.event.events.ChunkEvent;
import winton.api.event.events.type.EventState;
import winton.api.utils.Pair;
import winton.cache.CachedChunk;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.CommonListenerCookie;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Brady
 * @since 8/3/2018
 */
@Mixin(ClientPacketListener.class)
public abstract class MixinClientPlayNetHandler extends ClientCommonPacketListenerImpl {

    // unused lol
    /*@Inject(
            method = "handleChunkData",
            at = @At(
                    value = "INVOKE",
                    target = "net/minecraft/client/multiplayer/ChunkProviderClient.func_212474_a(IILnet/minecraft/network/PacketBuffer;IZ)Lnet/minecraft/world/chunk/Chunk;"
            )
    )
    private void preRead(SPacketChunkData packetIn, CallbackInfo ci) {
        for (IWinton iwinton : WintonAPI.getProvider().getAllWintons()) {
            ClientPlayerEntity player = iwinton.getPlayerContext().player();
            if (player != null && player.connection == (ClientPlayNetHandler) (Object) this) {
                iwinton.getGameEventHandler().onChunkEvent(
                        new ChunkEvent(
                                EventState.PRE,
                                packetIn.isFullChunk() ? ChunkEvent.Type.POPULATE_FULL : ChunkEvent.Type.POPULATE_PARTIAL,
                                packetIn.getChunkX(),
                                packetIn.getChunkZ()
                        )
                );
            }
        }
    }*/

    protected MixinClientPlayNetHandler(final Minecraft arg, final Connection arg2, final CommonListenerCookie arg3) {
        super(arg, arg2, arg3);
    }

    @Inject(
            method = "sendChat(Ljava/lang/String;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void sendChatMessage(String string, CallbackInfo ci) {
        ChatEvent event = new ChatEvent(string);
        IWinton winton = WintonAPI.getProvider().getWintonForPlayer(this.minecraft.player);
        if (winton == null) {
            return;
        }
        winton.getGameEventHandler().onSendChatMessage(event);
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(
            method = "handleLevelChunkWithLight",
            at = @At("RETURN")
    )
    private void postHandleChunkData(ClientboundLevelChunkWithLightPacket packetIn, CallbackInfo ci) {
        for (IWinton iwinton : WintonAPI.getProvider().getAllWintons()) {
            LocalPlayer player = iwinton.getPlayerContext().player();
            if (player != null && player.connection == (ClientPacketListener) (Object) this) {
                iwinton.getGameEventHandler().onChunkEvent(
                        new ChunkEvent(
                                EventState.POST,
                                !packetIn.isSkippable() ? ChunkEvent.Type.POPULATE_FULL : ChunkEvent.Type.POPULATE_PARTIAL,
                                packetIn.getX(),
                                packetIn.getZ()
                        )
                );
            }
        }
    }

    @Inject(
            method = "handleForgetLevelChunk",
            at = @At("HEAD")
    )
    private void preChunkUnload(ClientboundForgetLevelChunkPacket packet, CallbackInfo ci) {
        for (IWinton iwinton : WintonAPI.getProvider().getAllWintons()) {
            LocalPlayer player = iwinton.getPlayerContext().player();
            if (player != null && player.connection == (ClientPacketListener) (Object) this) {
                iwinton.getGameEventHandler().onChunkEvent(
                        new ChunkEvent(EventState.PRE, ChunkEvent.Type.UNLOAD, packet.pos().x, packet.pos().z)
                );
            }
        }
    }

    @Inject(
            method = "handleForgetLevelChunk",
            at = @At("RETURN")
    )
    private void postChunkUnload(ClientboundForgetLevelChunkPacket packet, CallbackInfo ci) {
        for (IWinton iwinton : WintonAPI.getProvider().getAllWintons()) {
            LocalPlayer player = iwinton.getPlayerContext().player();
            if (player != null && player.connection == (ClientPacketListener) (Object) this) {
                iwinton.getGameEventHandler().onChunkEvent(
                        new ChunkEvent(EventState.POST, ChunkEvent.Type.UNLOAD, packet.pos().x, packet.pos().z)
                );
            }
        }
    }

    @Inject(
            method = "handleBlockUpdate",
            at = @At("RETURN")
    )
    private void postHandleBlockChange(ClientboundBlockUpdatePacket packetIn, CallbackInfo ci) {
        if (!Winton.settings().repackOnAnyBlockChange.value) {
            return;
        }
        if (!CachedChunk.BLOCKS_TO_KEEP_TRACK_OF.contains(packetIn.getBlockState().getBlock())) {
            return;
        }
        for (IWinton iwinton : WintonAPI.getProvider().getAllWintons()) {
            LocalPlayer player = iwinton.getPlayerContext().player();
            if (player != null && player.connection == (ClientPacketListener) (Object) this) {
                iwinton.getGameEventHandler().onChunkEvent(
                        new ChunkEvent(
                                EventState.POST,
                                ChunkEvent.Type.POPULATE_FULL,
                                packetIn.getPos().getX() >> 4,
                                packetIn.getPos().getZ() >> 4
                        )
                );
            }
        }
    }

    @Inject(
            method = "handleChunkBlocksUpdate",
            at = @At("RETURN")
    )
    private void postHandleMultiBlockChange(ClientboundSectionBlocksUpdatePacket packetIn, CallbackInfo ci) {
        // Idle: don't allocate change lists when repack is off (default in WintonPerf)
        if (!Winton.settings().repackOnAnyBlockChange.value) {
            return;
        }
        IWinton winton = WintonAPI.getProvider().getWintonForConnection((ClientPacketListener) (Object) this);
        if (winton == null) {
            return;
        }

        List<Pair<BlockPos, BlockState>> changes = new ArrayList<>();
        packetIn.runUpdates((mutPos, state) -> {
            changes.add(new Pair<>(mutPos.immutable(), state));
        });
        if (changes.isEmpty()) {
            return;
        }
        winton.getGameEventHandler().onBlockChange(new BlockChangeEvent(
                new ChunkPos(changes.get(0).first()),
                changes
        ));
    }

    @Inject(
            method = "handlePlayerCombatKill",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;shouldShowDeathScreen()Z"
            )
    )
    private void onPlayerDeath(ClientboundPlayerCombatKillPacket packetIn, CallbackInfo ci) {
        for (IWinton iwinton : WintonAPI.getProvider().getAllWintons()) {
            LocalPlayer player = iwinton.getPlayerContext().player();
            if (player != null && player.connection == (ClientPacketListener) (Object) this) {
                iwinton.getGameEventHandler().onPlayerDeath();
            }
        }
    }

    /*
    @Inject(
            method = "handleChunkData",
            at = @At(
                    value = "INVOKE",
                    target = "net/minecraft/world/chunk/Chunk.read(Lnet/minecraft/network/PacketBuffer;IZ)V"
            )
    )
    private void preRead(SPacketChunkData packetIn, CallbackInfo ci) {
        IWinton winton = WintonAPI.getProvider().getWintonForConnection((NetHandlerPlayClient) (Object) this);
        if (winton == null) {
            return;
        }
        winton.getGameEventHandler().onChunkEvent(
                new ChunkEvent(
                        EventState.PRE,
                        packetIn.isFullChunk() ? ChunkEvent.Type.POPULATE_FULL : ChunkEvent.Type.POPULATE_PARTIAL,
                        packetIn.getChunkX(),
                        packetIn.getChunkZ()
                )
        );
    }

    @Inject(
            method = "handleChunkData",
            at = @At("RETURN")
    )
    private void postHandleChunkData(SPacketChunkData packetIn, CallbackInfo ci) {
        IWinton winton = WintonAPI.getProvider().getWintonForConnection((NetHandlerPlayClient) (Object) this);
        if (winton == null) {
            return;
        }
        winton.getGameEventHandler().onChunkEvent(
                new ChunkEvent(
                        EventState.POST,
                        packetIn.isFullChunk() ? ChunkEvent.Type.POPULATE_FULL : ChunkEvent.Type.POPULATE_PARTIAL,
                        packetIn.getChunkX(),
                        packetIn.getChunkZ()
                )
        );
    }

    @Inject(
            method = "handleBlockChange",
            at = @At("RETURN")
    )
    private void postHandleBlockChange(SPacketBlockChange packetIn, CallbackInfo ci) {
        IWinton winton = WintonAPI.getProvider().getWintonForConnection((NetHandlerPlayClient) (Object) this);
        if (winton == null) {
            return;
        }

        final ChunkPos pos = new ChunkPos(packetIn.getBlockPosition().getX() >> 4, packetIn.getBlockPosition().getZ() >> 4);
        final Pair<BlockPos, IBlockState> changed = new Pair<>(packetIn.getBlockPosition(), packetIn.getBlockState());
        winton.getGameEventHandler().onBlockChange(new BlockChangeEvent(pos, Collections.singletonList(changed)));
    }

    @Inject(
            method = "handleMultiBlockChange",
            at = @At("RETURN")
    )
    private void postHandleMultiBlockChange(SPacketMultiBlockChange packetIn, CallbackInfo ci) {

    }

     */
}
