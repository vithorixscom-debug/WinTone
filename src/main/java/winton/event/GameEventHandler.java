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

package winton.event;

import winton.Winton;
import winton.api.event.events.*;
import winton.api.event.events.type.EventState;
import winton.api.event.listener.IEventBus;
import winton.api.event.listener.IGameEventListener;
import winton.api.utils.Helper;
import winton.api.utils.Pair;
import winton.cache.CachedChunk;
import winton.cache.WorldProvider;
import winton.utils.BlockStateInterface;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * @author Brady
 * @since 7/31/2018
 */
public final class GameEventHandler implements IEventBus, Helper {

    private final Winton winton;

    private final List<IGameEventListener> listeners = new CopyOnWriteArrayList<>();

    /** One cache fault stops per-chunk retries so a bad class load cannot freeze the render thread. */
    private final AtomicBoolean cacheFault = new AtomicBoolean();

    public GameEventHandler(Winton winton) {
        this.winton = winton;
    }

    private void noteCacheFault(String what, Throwable t) {
        if (cacheFault.compareAndSet(false, true)) {
            System.err.println("[WinTone] cache paused after " + what + ": " + t);
        }
    }

    private void safe(String what, Consumer<IGameEventListener> action) {
        for (IGameEventListener l : listeners) {
            try {
                action.accept(l);
            } catch (Throwable t) {
                System.err.println("[Winton] " + what + " failed: " + t);
            }
        }
    }

    @Override
    public final void onTick(TickEvent event) {
        try {
            if (event.getType() == TickEvent.Type.IN) {
                try {
                    // Main-thread BSI never needs a full chunk-map copy — pathing threads
                    // create their own CalculationContext(..., true). Reuse one live BSI.
                    var ctx = winton.getPlayerContext();
                    if (ctx != null && ctx.world() != null) {
                        if (winton.bsi == null || winton.bsi.getWorld() != ctx.world()) {
                            winton.bsi = new BlockStateInterface(ctx, false);
                        }
                    } else {
                        winton.bsi = null;
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                    winton.bsi = null;
                }
            }
            // Do not null BSI on OUT — PathExecutor / inventory need it; avoids NPE + realloc
        } catch (Throwable t) {
            System.err.println("[Winton] BSI tick setup failed: " + t);
            winton.bsi = null;
        }
        safe("onTick", l -> l.onTick(event));
    }

    @Override
    public void onPostTick(TickEvent event) {
        safe("onPostTick", l -> l.onPostTick(event));
    }

    @Override
    public final void onPlayerUpdate(PlayerUpdateEvent event) {
        safe("onPlayerUpdate", l -> l.onPlayerUpdate(event));
    }

    @Override
    public final void onSendChatMessage(ChatEvent event) {
        safe("onSendChatMessage", l -> l.onSendChatMessage(event));
    }

    @Override
    public void onPreTabComplete(TabCompleteEvent event) {
        safe("onPreTabComplete", l -> l.onPreTabComplete(event));
    }

    @Override
    public void onChunkEvent(ChunkEvent event) {
        if (cacheFault.get()) {
            safe("onChunkEvent", l -> l.onChunkEvent(event));
            return;
        }
        try {
            Level world = winton.getPlayerContext().world();
            if (world != null) {
                EventState state = event.getState();
                ChunkEvent.Type type = event.getType();

                // Whenever the server sends us to another dimension, chunks are unloaded
                // technically after the new world has been loaded, so we perform a check
                // to make sure the chunk being unloaded is already loaded.
                boolean isPreUnload = state == EventState.PRE
                        && type == ChunkEvent.Type.UNLOAD
                        && world.getChunkSource().getChunk(event.getX(), event.getZ(), null, false) != null;

                // Pack only on populate — skip unload packing (cuts hitch/“ping” spikes)
                if (event.isPostPopulate()) {
                    winton.getWorldProvider().ifWorldLoaded(worldData -> {
                        LevelChunk chunk = world.getChunk(event.getX(), event.getZ());
                        worldData.getCachedWorld().queueForPacking(chunk);
                    });
                } else if (isPreUnload) {
                    // keep unload pack for cache correctness, but swallow errors
                    try {
                        winton.getWorldProvider().ifWorldLoaded(worldData -> {
                            LevelChunk chunk = world.getChunk(event.getX(), event.getZ());
                            worldData.getCachedWorld().queueForPacking(chunk);
                        });
                    } catch (Throwable ignored) {
                    }
                }
            }
        } catch (Throwable t) {
            noteCacheFault("onChunkEvent", t);
        }

        safe("onChunkEvent", l -> l.onChunkEvent(event));
    }

    @Override
    public void onBlockChange(BlockChangeEvent event) {
        if (cacheFault.get()) {
            safe("onBlockChange", l -> l.onBlockChange(event));
            return;
        }
        try {
            if (Winton.settings().repackOnAnyBlockChange.value) {
                boolean keepingTrackOf = false;
                for (Pair<BlockPos, BlockState> pair : event.getBlocks()) {
                    if (CachedChunk.BLOCKS_TO_KEEP_TRACK_OF.contains(pair.second().getBlock())) {
                        keepingTrackOf = true;
                        break;
                    }
                }

                if (keepingTrackOf) {
                    winton.getWorldProvider().ifWorldLoaded(worldData -> {
                        final Level world = winton.getPlayerContext().world();
                        if (world == null) {
                            return;
                        }
                        ChunkPos pos = event.getChunkPos();
                        worldData.getCachedWorld().queueForPacking(world.getChunk(pos.x, pos.z));
                    });
                }
            }
        } catch (Throwable t) {
            noteCacheFault("onBlockChange", t);
        }

        safe("onBlockChange", l -> l.onBlockChange(event));
    }

    @Override
    public final void onRenderPass(RenderEvent event) {
        safe("onRenderPass", l -> l.onRenderPass(event));
    }

    @Override
    public final void onWorldEvent(WorldEvent event) {
        try {
            WorldProvider cache = winton.getWorldProvider();

            if (event.getState() == EventState.POST) {
                cache.closeWorld();
                if (event.getWorld() != null) {
                    cache.initWorld(event.getWorld());
                }
            }
        } catch (Throwable t) {
            noteCacheFault("onWorldEvent", t);
        }

        safe("onWorldEvent", l -> l.onWorldEvent(event));
    }

    @Override
    public final void onSendPacket(PacketEvent event) {
        safe("onSendPacket", l -> l.onSendPacket(event));
    }

    @Override
    public final void onReceivePacket(PacketEvent event) {
        safe("onReceivePacket", l -> l.onReceivePacket(event));
    }

    @Override
    public void onPlayerRotationMove(RotationMoveEvent event) {
        safe("onPlayerRotationMove", l -> l.onPlayerRotationMove(event));
    }

    @Override
    public void onPlayerSprintState(SprintStateEvent event) {
        safe("onPlayerSprintState", l -> l.onPlayerSprintState(event));
    }

    @Override
    public void onBlockInteract(BlockInteractEvent event) {
        safe("onBlockInteract", l -> l.onBlockInteract(event));
    }

    @Override
    public void onPlayerDeath() {
        safe("onPlayerDeath", IGameEventListener::onPlayerDeath);
    }

    @Override
    public void onPathEvent(PathEvent event) {
        safe("onPathEvent", l -> l.onPathEvent(event));
    }

    @Override
    public final void registerEventListener(IGameEventListener listener) {
        this.listeners.add(listener);
    }
}
