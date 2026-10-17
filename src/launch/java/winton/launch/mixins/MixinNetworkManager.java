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

import winton.api.WintonAPI;
import winton.api.IWinton;
import winton.api.event.events.PacketEvent;
import winton.api.event.events.type.EventState;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * @author Brady
 * @since 8/6/2018
 */
@Mixin(Connection.class)
public class MixinNetworkManager {

    @Shadow
    private Channel channel;

    @Shadow
    @Final
    private PacketFlow receiving;

    @Inject(
            method = "sendPacket",
            at = @At("HEAD")
    )
    private void preDispatchPacket(final Packet<?> packet, final ChannelFutureListener channelFutureListener, final boolean flush, final CallbackInfo ci) {
        if (this.receiving != PacketFlow.CLIENTBOUND) {
            return;
        }

        for (IWinton iwinton : WintonAPI.getProvider().getAllWintons()) {
            if (iwinton.getPlayerContext().player() != null && iwinton.getPlayerContext().player().connection.getConnection() == (Connection) (Object) this) {
                iwinton.getGameEventHandler().onSendPacket(new PacketEvent((Connection) (Object) this, EventState.PRE, packet));
            }
        }
    }

    @Inject(
            method = "sendPacket",
            at = @At("RETURN")
    )
    private void postDispatchPacket(Packet<?> packet, ChannelFutureListener packetSendListener, boolean flush, CallbackInfo ci) {
        if (this.receiving != PacketFlow.CLIENTBOUND) {
            return;
        }

        for (IWinton iwinton : WintonAPI.getProvider().getAllWintons()) {
            if (iwinton.getPlayerContext().player() != null && iwinton.getPlayerContext().player().connection.getConnection() == (Connection) (Object) this) {
                iwinton.getGameEventHandler().onSendPacket(new PacketEvent((Connection) (Object) this, EventState.POST, packet));
            }
        }
    }

    @Inject(
            method = "channelRead0",
            at = @At(
                    value = "INVOKE",
                    target = "net/minecraft/network/Connection.genericsFtw(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;)V"
            )
    )
    private void preProcessPacket(ChannelHandlerContext context, Packet<?> packet, CallbackInfo ci) {
        if (this.receiving != PacketFlow.CLIENTBOUND) {
            return;
        }
        for (IWinton iwinton : WintonAPI.getProvider().getAllWintons()) {
            if (iwinton.getPlayerContext().player() != null && iwinton.getPlayerContext().player().connection.getConnection() == (Connection) (Object) this) {
                iwinton.getGameEventHandler().onReceivePacket(new PacketEvent((Connection) (Object) this, EventState.PRE, packet));
            }
        }
    }

    @Inject(
            method = "channelRead0",
            at = @At("RETURN")
    )
    private void postProcessPacket(ChannelHandlerContext context, Packet<?> packet, CallbackInfo ci) {
        if (!this.channel.isOpen() || this.receiving != PacketFlow.CLIENTBOUND) {
            return;
        }
        for (IWinton iwinton : WintonAPI.getProvider().getAllWintons()) {
            if (iwinton.getPlayerContext().player() != null && iwinton.getPlayerContext().player().connection.getConnection() == (Connection) (Object) this) {
                iwinton.getGameEventHandler().onReceivePacket(new PacketEvent((Connection) (Object) this, EventState.POST, packet));
            }
        }
    }
}
