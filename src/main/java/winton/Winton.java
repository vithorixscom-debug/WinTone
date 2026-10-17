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

package winton;

import winton.api.WintonAPI;
import winton.api.IWinton;
import winton.api.Settings;
import winton.api.behavior.IBehavior;
import winton.api.event.listener.IEventBus;
import winton.api.process.IWintonProcess;
import winton.api.process.IElytraProcess;
import winton.api.utils.IPlayerContext;
import winton.behavior.*;
import winton.cache.WorldProvider;
import winton.command.manager.CommandManager;
import winton.event.GameEventHandler;
import winton.process.*;
import winton.selection.SelectionManager;
import winton.utils.BlockStateInterface;
import winton.utils.GuiClick;
import winton.utils.InputOverrideHandler;
import winton.utils.PathingControlManager;
import winton.utils.player.WintonPlayerContext;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * @author Brady
 * @since 7/31/2018
 */
public class Winton implements IWinton {

    private static final ThreadPoolExecutor threadPool;

    static {
        threadPool = new ThreadPoolExecutor(4, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS, new SynchronousQueue<>());
    }

    private final Minecraft mc;
    private final Path directory;

    private final GameEventHandler gameEventHandler;

    private final PathingBehavior pathingBehavior;
    private final LookBehavior lookBehavior;
    private final InventoryBehavior inventoryBehavior;
    private final InputOverrideHandler inputOverrideHandler;

    private final FollowProcess followProcess;
    private final MineProcess mineProcess;
    private final GetToBlockProcess getToBlockProcess;
    private final CustomGoalProcess customGoalProcess;
    private final BuilderProcess builderProcess;
    private final ExploreProcess exploreProcess;
    private final FarmProcess farmProcess;
    private final InventoryPauserProcess inventoryPauserProcess;
    private final IElytraProcess elytraProcess;
    private final ChestSortProcess chestSortProcess;

    private final PathingControlManager pathingControlManager;
    private final SelectionManager selectionManager;
    private final CommandManager commandManager;

    private final IPlayerContext playerContext;
    private final WorldProvider worldProvider;

    public BlockStateInterface bsi;

    Winton(Minecraft mc) {
        this.mc = mc;
        this.gameEventHandler = new GameEventHandler(this);

        this.directory = mc.gameDirectory.toPath().resolve("winton");
        if (!Files.exists(this.directory)) {
            try {
                Files.createDirectories(this.directory);
            } catch (IOException ignored) {}
        }

        // Define this before behaviors try and get it, or else it will be null and the builds will fail!
        this.playerContext = new WintonPlayerContext(this, mc);

        {
            this.lookBehavior         = this.registerBehavior(LookBehavior::new);
            this.pathingBehavior      = this.registerBehavior(PathingBehavior::new);
            this.inventoryBehavior    = this.registerBehavior(InventoryBehavior::new);
            this.inputOverrideHandler = this.registerBehavior(InputOverrideHandler::new);
            this.registerBehavior(WaypointBehavior::new);
            this.registerBehavior(SafetyBehavior::new);
            this.getGameEventHandler().registerEventListener(winton.minihud.MiniHudBehavior.get());
        }

        this.pathingControlManager = new PathingControlManager(this);
        {
            this.followProcess           = this.registerProcess(FollowProcess::new);
            this.mineProcess             = this.registerProcess(MineProcess::new);
            this.customGoalProcess       = this.registerProcess(CustomGoalProcess::new); // very high iq
            this.getToBlockProcess       = this.registerProcess(GetToBlockProcess::new);
            this.builderProcess          = this.registerProcess(BuilderProcess::new);
            this.exploreProcess          = this.registerProcess(ExploreProcess::new);
            this.farmProcess             = this.registerProcess(FarmProcess::new);
            this.inventoryPauserProcess  = this.registerProcess(InventoryPauserProcess::new);
            this.elytraProcess           = this.registerProcess(ElytraProcess::create);
            this.chestSortProcess        = this.registerProcess(ChestSortProcess::new);
            this.registerProcess(BackfillProcess::new);
        }

        this.worldProvider = new WorldProvider(this);
        this.selectionManager = new SelectionManager(this);
        this.commandManager = new CommandManager(this);
    }

    public void registerBehavior(IBehavior behavior) {
        this.gameEventHandler.registerEventListener(behavior);
    }

    public <T extends IBehavior> T registerBehavior(Function<Winton, T> constructor) {
        final T behavior = constructor.apply(this);
        this.registerBehavior(behavior);
        return behavior;
    }

    public <T extends IWintonProcess> T registerProcess(Function<Winton, T> constructor) {
        final T behavior = constructor.apply(this);
        this.pathingControlManager.registerProcess(behavior);
        return behavior;
    }

    @Override
    public PathingControlManager getPathingControlManager() {
        return this.pathingControlManager;
    }

    @Override
    public InputOverrideHandler getInputOverrideHandler() {
        return this.inputOverrideHandler;
    }

    @Override
    public CustomGoalProcess getCustomGoalProcess() {
        return this.customGoalProcess;
    }

    @Override
    public GetToBlockProcess getGetToBlockProcess() {
        return this.getToBlockProcess;
    }

    @Override
    public IPlayerContext getPlayerContext() {
        return this.playerContext;
    }

    @Override
    public FollowProcess getFollowProcess() {
        return this.followProcess;
    }

    @Override
    public BuilderProcess getBuilderProcess() {
        return this.builderProcess;
    }

    public InventoryBehavior getInventoryBehavior() {
        return this.inventoryBehavior;
    }

    @Override
    public LookBehavior getLookBehavior() {
        return this.lookBehavior;
    }

    @Override
    public ExploreProcess getExploreProcess() {
        return this.exploreProcess;
    }

    @Override
    public MineProcess getMineProcess() {
        return this.mineProcess;
    }

    @Override
    public FarmProcess getFarmProcess() {
        return this.farmProcess;
    }

    public InventoryPauserProcess getInventoryPauserProcess() {
        return this.inventoryPauserProcess;
    }

    @Override
    public PathingBehavior getPathingBehavior() {
        return this.pathingBehavior;
    }

    @Override
    public SelectionManager getSelectionManager() {
        return selectionManager;
    }

    @Override
    public WorldProvider getWorldProvider() {
        return this.worldProvider;
    }

    @Override
    public IEventBus getGameEventHandler() {
        return this.gameEventHandler;
    }

    @Override
    public CommandManager getCommandManager() {
        return this.commandManager;
    }

    @Override
    public IElytraProcess getElytraProcess() {
        return this.elytraProcess;
    }

    @Override
    public ChestSortProcess getChestSortProcess() {
        return this.chestSortProcess;
    }

    @Override
    public void openClick() {
        new Thread(() -> {
            try {
                Thread.sleep(100);
                mc.execute(() -> mc.setScreen(new GuiClick()));
            } catch (Exception ignored) {}
        }).start();
    }

    public Path getDirectory() {
        return this.directory;
    }

    public static Settings settings() {
        return WintonAPI.getSettings();
    }

    public static Executor getExecutor() {
        return threadPool;
    }
}
