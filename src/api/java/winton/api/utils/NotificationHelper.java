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

package winton.api.utils;

import org.apache.commons.lang3.SystemUtils;

import java.awt.*;
import java.io.IOException;

/**
 * This class is not called from the main game thread.
 * Do not refer to any Minecraft classes, it wouldn't be thread safe.
 *
 * @author aUniqueUser
 */
public class NotificationHelper {

    private static TrayIcon trayIcon;

    public static void notify(String text, boolean error) {
        if (SystemUtils.IS_OS_WINDOWS) {
            windows(text, error);
        } else if (SystemUtils.IS_OS_MAC_OSX) {
            mac(text);
        } else if (SystemUtils.IS_OS_LINUX) {
            linux(text);
        }
    }

    private static void windows(String text, boolean error) {
        if (SystemTray.isSupported()) {
            try {
                if (trayIcon == null) {
                    SystemTray tray = SystemTray.getSystemTray();
                    Image image = Toolkit.getDefaultToolkit().createImage("");

                    trayIcon = new TrayIcon(image, "Winton");
                    trayIcon.setImageAutoSize(true);
                    trayIcon.setToolTip("Winton");
                    tray.add(trayIcon);
                }

                trayIcon.displayMessage("Winton", text, error ? TrayIcon.MessageType.ERROR : TrayIcon.MessageType.INFO);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            System.out.println("SystemTray is not supported");
        }
    }

    private static void mac(String text) {
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command("osascript", "-e", "display notification \"" + text + "\" with title \"Winton\"");
        try {
            processBuilder.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // The only way to display notifications on linux is to use the java-gnome library,
    // or send notify-send to shell with a ProcessBuilder. Unfortunately the java-gnome
    // library is licenced under the GPL, see (https://en.wikipedia.org/wiki/Java-gnome)
    private static void linux(String text) {
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command("notify-send", "-a", "Winton", text);
        try {
            processBuilder.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
