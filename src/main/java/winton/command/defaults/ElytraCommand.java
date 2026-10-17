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

package winton.command.defaults;

import winton.Winton;
import winton.api.IWinton;
import winton.api.command.Command;
import winton.api.command.argument.IArgConsumer;
import winton.api.command.exception.CommandException;
import winton.api.command.exception.CommandInvalidStateException;
import winton.api.command.helpers.TabCompleteHelper;
import winton.api.pathing.goals.Goal;
import winton.api.process.ICustomGoalProcess;
import winton.api.process.IElytraProcess;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.Level;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static winton.api.command.IWintonChatControl.FORCE_COMMAND_PREFIX;

public class ElytraCommand extends Command {

    public ElytraCommand(IWinton winton) {
        super(winton, "elytra");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        final ICustomGoalProcess customGoalProcess = winton.getCustomGoalProcess();
        final IElytraProcess elytra = winton.getElytraProcess();
        if (args.hasExactlyOne() && args.peekString().equals("supported")) {
            logDirect(elytra.isLoaded() ? "yes" : unsupportedSystemMessage());
            return;
        }
        if (!elytra.isLoaded()) {
            throw new CommandInvalidStateException(unsupportedSystemMessage());
        }

        if (!args.hasAny()) {
            if (Winton.settings().elytraTermsAccepted.value) {
                if (detectOn2b2t()) {
                    warn2b2t();
                }
            } else {
                gatekeep();
            }
            Goal iGoal = customGoalProcess.mostRecentGoal();
            if (iGoal == null) {
                throw new CommandInvalidStateException("No goal has been set");
            }
            try {
                elytra.pathTo(iGoal);
            } catch (IllegalArgumentException ex) {
                throw new CommandInvalidStateException(ex.getMessage());
            }
            return;
        }

        // elytra <x> <z> or elytra <x> <y> <z> — Y always ignored; land on highest block
        if (args.hasExactly(2) || args.hasExactly(3)) {
            final String first = args.peekString();
            if (!first.isEmpty() && (Character.isDigit(first.charAt(0)) || first.charAt(0) == '-' || first.charAt(0) == '~')) {
                prepareElytraFlightSettings();
                try {
                    final boolean hasY = args.hasExactly(3);
                    final int x = args.getAs(Integer.class);
                    if (hasY) {
                        args.getAs(Integer.class); // ignore Y
                    }
                    final int z = args.getAs(Integer.class);
                    elytra.pathToXZ(x, z);
                    logDirect(String.format("Elytra → %d ~ %d (surface)", x, z));
                } catch (IllegalArgumentException ex) {
                    throw new CommandInvalidStateException(ex.getMessage());
                }
                return;
            }
        }

        final String action = args.getString();
        switch (action) {
            case "reset": {
                try {
                    elytra.resetState();
                } catch (IllegalArgumentException ex) {
                    throw new CommandInvalidStateException(ex.getMessage());
                }
                logDirect("Reset state but still flying to same goal");
                break;
            }
            case "repack": {
                elytra.repackChunks();
                logDirect("Queued all loaded chunks for repacking");
                break;
            }
            default: {
                throw new CommandInvalidStateException("Invalid action");
            }
        }
    }

    private static void prepareElytraFlightSettings() {
        Winton.settings().elytraTermsAccepted.value = true;
        Winton.settings().elytraAutoJump.value = true;
        // CLIENT camera so the player sees yaw/pitch change while flying
        Winton.settings().elytraFreeLook.value = false;
        Winton.settings().elytraSmoothLook.value = true;
        Winton.settings().elytraAutoSwap.value = true;
        Winton.settings().allowInventory.value = true;
        Winton.settings().elytraAllowAboveBuildLimit.value = false;
        Winton.settings().elytraRenderSimulation.value = false;
        Winton.settings().elytraRenderRaytraces.value = false;
        Winton.settings().elytraSimulationTicks.value = 8;
        Winton.settings().renderPath.value = false;
    }

    private void warn2b2t() {
        if (Winton.settings().elytraPredictTerrain.value) {
            long seed = Winton.settings().elytraNetherSeed.value;
            if (seed != NEW_2B2T_SEED && seed != OLD_2B2T_SEED) {
                logDirect(Component.literal("It looks like you're on 2b2t, but elytraNetherSeed is incorrect.")); // match color
                logDirect(suggest2b2tSeeds());
            }
        }
    }

    private Component suggest2b2tSeeds() {
        MutableComponent clippy = Component.literal("");
        clippy.append("Within a few hundred blocks of spawn/axis/highways/etc, the terrain is too fragmented to be predictable. WinTone Elytra will still work, just with backtracking. ");
        clippy.append("However, once you get more than a few thousand blocks out, you should try ");
        MutableComponent olderSeed = Component.literal("the older seed (click here)");
        olderSeed.setStyle(olderSeed.getStyle().withUnderlined(true).withBold(true).withHoverEvent(new HoverEvent.ShowText(Component.literal(Winton.settings().prefix.value + "set elytraNetherSeed " + OLD_2B2T_SEED))).withClickEvent(new ClickEvent.RunCommand(FORCE_COMMAND_PREFIX + "set elytraNetherSeed " + OLD_2B2T_SEED)));
        clippy.append(olderSeed);
        clippy.append(". Once you're further out into newer terrain generation (this includes everything up through 1.12), you should try ");
        MutableComponent newerSeed = Component.literal("the newer seed (click here)");
        newerSeed.setStyle(newerSeed.getStyle().withUnderlined(true).withBold(true).withHoverEvent(new HoverEvent.ShowText(Component.literal(Winton.settings().prefix.value + "set elytraNetherSeed " + NEW_2B2T_SEED))).withClickEvent(new ClickEvent.RunCommand(FORCE_COMMAND_PREFIX + "set elytraNetherSeed " + NEW_2B2T_SEED)));
        clippy.append(newerSeed);
        clippy.append(". Once you get into 1.19 terrain, the terrain becomes unpredictable again, due to custom non-vanilla generation, and you should set #elytraPredictTerrain to false. ");
        return clippy;
    }

    private void gatekeep() {
        MutableComponent gatekeep = Component.literal("");
        gatekeep.append("To disable this message, enable the setting elytraTermsAccepted\n");
        gatekeep.append("WinTone Elytra is an experimental feature. It is intended for long distance travel in the Nether but will also work in the Overworld, using fireworks for vanilla boost. It will not work with any other mods (\"hacks\") for non-vanilla boost. ");
        MutableComponent gatekeep2 = Component.literal("If you want WinTone to attempt to take off from the ground for you, you can enable the elytraAutoJump setting (not advisable on laggy servers!). ");
        gatekeep2.setStyle(gatekeep2.getStyle().withHoverEvent(new HoverEvent.ShowText(Component.literal(Winton.settings().prefix.value + "set elytraAutoJump true"))));
        gatekeep.append(gatekeep2);
        MutableComponent gatekeep3 = Component.literal("If you want WinTone to go slower, enable the elytraConserveFireworks setting and/or decrease the elytraFireworkSpeed setting. ");
        gatekeep3.setStyle(gatekeep3.getStyle().withHoverEvent(new HoverEvent.ShowText(Component.literal(Winton.settings().prefix.value + "set elytraConserveFireworks true\n" + Winton.settings().prefix.value + "set elytraFireworkSpeed 0.6\n(the 0.6 number is just an example, tweak to your liking)"))));
        gatekeep.append(gatekeep3);
        MutableComponent gatekeep4 = Component.literal("WinTone Elytra for use in the ");
        MutableComponent red1 = Component.literal("Nether");
        red1.setStyle(red1.getStyle().withColor(ChatFormatting.RED).withUnderlined(true).withBold(true));
        gatekeep4.append(red1);
        gatekeep4.append(", ");
        MutableComponent red2 = Component.literal("wants to know the seed");
        red2.setStyle(red2.getStyle().withColor(ChatFormatting.RED).withUnderlined(true).withBold(true));
        gatekeep4.append(red2);
        gatekeep4.append(" of the world you are in. If it doesn't have the correct seed, it will frequently backtrack. It uses the seed to generate terrain far beyond what you can see, since terrain obstacles in the Nether can be much larger than your render distance. ");
        gatekeep.append(gatekeep4);
        gatekeep.append("\n");
        if (detectOn2b2t()) {
            MutableComponent gatekeep5 = Component.literal("It looks like you're on 2b2t. Terrain prediction can be used but new nether terrain can not be predicted on 2b2t. ");
            gatekeep5.append(suggest2b2tSeeds());
            if (!Winton.settings().elytraPredictTerrain.value) {
                gatekeep5.append(Winton.settings().prefix.value + "elytraPredictTerrain is currently disabled. ");
            } else {
                if (Winton.settings().elytraNetherSeed.value == NEW_2B2T_SEED) {
                    gatekeep5.append("You are using the newer seed. ");
                } else if (Winton.settings().elytraNetherSeed.value == OLD_2B2T_SEED) {
                    gatekeep5.append("You are using the older seed. ");
                } else {
                    gatekeep5.append("Defaulting to the newer seed. ");
                    Winton.settings().elytraNetherSeed.value = NEW_2B2T_SEED;
                }
            }
            gatekeep.append(gatekeep5);
        } else {
            if (Winton.settings().elytraNetherSeed.value == NEW_2B2T_SEED) {
                MutableComponent gatekeep5 = Component.literal("WinTone doesn't know the seed of your world. Set it with: " + Winton.settings().prefix.value + "set elytraNetherSeed seedgoeshere\n");
                gatekeep5.append("For the time being, elytraPredictTerrain is defaulting to false since the seed is unknown.");
                gatekeep.append(gatekeep5);
                Winton.settings().elytraPredictTerrain.value = false;
            } else {
                if (Winton.settings().elytraPredictTerrain.value) {
                    MutableComponent gatekeep5 = Component.literal("WinTone Elytra is predicting terrain assuming that " + Winton.settings().elytraNetherSeed.value + " is the correct seed. Change that with " + Winton.settings().prefix.value + "set elytraNetherSeed seedgoeshere, or disable it with " + Winton.settings().prefix.value + "set elytraPredictTerrain false");
                    gatekeep.append(gatekeep5);
                } else {
                    MutableComponent gatekeep5 = Component.literal("WinTone Elytra is not predicting terrain. If you don't know the seed, this is the correct thing to do. If you do know the seed, input it with " + Winton.settings().prefix.value + "set elytraNetherSeed seedgoeshere, and then enable it with " + Winton.settings().prefix.value + "set elytraPredictTerrain true");
                    gatekeep.append(gatekeep5);
                }
            }
        }
        logDirect(gatekeep);
    }

    private boolean detectOn2b2t() {
        ServerData data = ctx.minecraft().getCurrentServer();
        return data != null && data.ip.toLowerCase().contains("2b2t.org");
    }

    private static final long OLD_2B2T_SEED = -4100785268875389365L;
    private static final long NEW_2B2T_SEED = 146008555100680L;

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        TabCompleteHelper helper = new TabCompleteHelper();
        if (args.hasExactlyOne()) {
            helper.append("reset", "repack", "supported");
        }
        return helper.filterPrefix(args.getString()).stream();
    }

    @Override
    public String getShortDesc() {
        return "elytra time";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "The elytra command tells winton to, in the nether, automatically fly to the current goal.",
                "",
                "Usage:",
                "> elytra - fly to the current goal",
                "> elytra <x> <y> <z> - take off and fly to coordinates",
                "> elytra reset - Resets the state of the process, but will try to keep flying to the same goal.",
                "> elytra repack - Queues all of the chunks in render distance to be given to the native library.",
                "> elytra supported - Tells you if winton ships a native library that is compatible with your PC."
        );
    }

    private static String unsupportedSystemMessage() {
        final String osArch = System.getProperty("os.arch");
        final String osName = System.getProperty("os.name");
        return String.format(
                "Failed loading native library. Your CPU is %s and your operating system is %s. " +
                        "Supported architectures are 64 bit x86, and 64 bit ARM. Supported operating systems are Windows, " +
                        "Linux, and Mac",
                osArch, osName
        );
    }
}
