/*
 * Copyright (C) 2016 Jason Jackson
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package jakshin.mixcaster.install;

import jakshin.mixcaster.utils.ResourceLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * Installs and uninstalls the Mixcaster service as a launchd user agent.
 * The service is started immediately after installation, and stopped before uninstallation.
 */
public final class Installer {
    /**
     * Installs the service, and starts it via launchd.
     * @return A code indicating success (0) or failure (1 or 2).
     */
    public int install() {
        this.log("Installing the Mixcaster service...");

        try {
            String jarPath = this.getJarPath();

            if (jarPath == null) {
                this.log("Error: Installation must be performed using the jar file, not in an IDE");
                return 1;
            }

            this.log("%nCreating the service's launchd configuration file...");

            String resourcePath = "install/launchd-plist.xml";
            String plistXml = ResourceLoader.loadResourceAsText(resourcePath, 600).toString();

            plistXml = plistXml.replace("{{serviceLabel}}", this.getServiceLabel());
            plistXml = plistXml.replace("{{mixcaster.jar}}", jarPath);

            File plistFile = new File(this.getPlistPath());
            Files.writeString(plistFile.toPath(), plistXml, StandardCharsets.UTF_8);

            this.log("Created %s", plistFile);
            this.log("%nLoading the service via launchd...");

            if (this.checkLaunchdAgent() && this.removeLaunchdAgent() != 0) {
                this.log("Warning: Failed to remove the existing service registration");
                this.log("Installation will continue, but you may need to reboot");
            }

            int result = this.loadLaunchdAgent(plistFile);

            if (result != 0) {
                this.log("Error: Failed to load the service (%d)", result);
                return 2;
            }

            this.log("Loaded the service");
            this.log("%nSuccess: The Mixcaster service was installed and started");
            return 0;
        }
        catch (IOException | InterruptedException ex) {
            this.logFailure("Installation", ex);
            return 2;
        }
    }

    /**
     * Uninstalls the service, after stopping it via launchd.
     * @return A code indicating success (0) or failure (1 or 2).
     */
    public int uninstall() {
        this.log("Uninstalling the Mixcaster service...");
        File plistFile = new File(this.getPlistPath());

        try {
            this.log("%nRemoving the service from launchd...");

            if (!this.checkLaunchdAgent()) {
                this.log("The service is not registered with launchd");
            }
            else {
                int result = this.removeLaunchdAgent();

                if (result != 0) {
                    this.log("Warning: Failed to remove the service (%d)", result);
                    this.log("Uninstallation will continue, but you may need to reboot to stop the service");
                }
                else {
                    this.log("Removed the service");
                }
            }

            this.log("%nRemoving the service's launchd configuration file...");
            boolean exists = plistFile.exists();

            if (exists && !plistFile.delete()) {
                this.log("Error: Failed to remove %s", plistFile);
                return 2;
            }

            String msg = (exists) ? "Removed %s" : "File %s doesn't exist";
            this.log(msg, plistFile);

            this.log("%nSuccess: The Mixcaster service was uninstalled");
            return 0;
        }
        catch (IOException | InterruptedException ex) {
            this.logFailure("Uninstallation", ex);
            return 2;
        }
    }

    /**
     * Gets the service's launchd label.
     * This is the unique name for the service (which we make per-user),
     * and also by convention the name of the service's plist configuration file.
     *
     * @return The service's launchd label.
     */
    @NotNull
    private String getServiceLabel() {
        String userName = System.getProperty("user.name").replaceAll("\\W+", "_");
        return String.format("jakshin.mixcaster.%s", userName);
    }

    /**
     * Gets the path to the service's launchd configuration file, for this user.
     * @return The path to the service's launchd configuration file, for this user.
     */
    @NotNull
    private String getPlistPath() {
        String pathStr = String.format("Library/LaunchAgents/%s.plist", this.getServiceLabel());
        Path path = Paths.get(System.getProperty("user.home"), pathStr);
        return path.toString();
    }

    /**
     * Gets the path to the currently running jar file.
     * Note that this returns null if the code isn't running in a jar file.
     *
     * @return The path to the currently running jar file.
     */
    @Nullable
    private String getJarPath() {
        Path path = Paths.get(Installer.class.getProtectionDomain().getCodeSource().getLocation().getPath());
        String jarPath = path.toString();

        if (jarPath.toLowerCase(Locale.ROOT).endsWith(".jar")) {
            return jarPath;
        }

        return null;
    }

    /**
     * Loads the service's launchd configuration file into launchd.
     * The service will be started immediately, because of how we've set up the configuration plist.
     *
     * @param plistFile The service's user-specific plist configuration file.
     * @return The launchctl program's exit code.
     */
    private int loadLaunchdAgent(File plistFile) throws IOException, InterruptedException {
        String cmd = String.format("/bin/launchctl load %s", plistFile);
        this.log("Executing: %s", cmd);

        Process proc = Runtime.getRuntime().exec(cmd);
        return proc.waitFor();
    }

    /**
     * Removes the service from launchd.
     * This also stops the service immediately.
     *
     * @return The launchctl program's exit code.
     */
    private int removeLaunchdAgent() throws IOException, InterruptedException {
        String cmd = String.format("/bin/launchctl remove %s", this.getServiceLabel());
        this.log("Executing: %s", cmd);

        Process proc = Runtime.getRuntime().exec(cmd);
        return proc.waitFor();
    }

    /**
     * Checks to see if the service is registered with launchd.
     *
     * @return Whether the service is registered with launchd.
     */
    private boolean checkLaunchdAgent() throws IOException, InterruptedException {
        String cmd = String.format("/bin/launchctl list %s", this.getServiceLabel());

        Process proc = Runtime.getRuntime().exec(cmd);
        int result = proc.waitFor();
        return (result == 0);  // assume any failure means "service doesn't exist"
    }

    /**
     * "Logs" a message by displaying it using System.out.
     * This class doesn't use the logging facilities used by the rest of the program.
     *
     * @param fmt The format string, or just the string to display; processed through String.format().
     * @param args Any arguments needed to format the format string.
     */
    private void log(@NotNull String fmt, Object... args) {
        String msg = String.format(fmt, args);
        System.out.println(msg);
    }

    /**
     * Convenience method which "logs" installation/uninstallation failure.
     * This calls the log() method to output information about the failure.
     *
     * @param operation The operation which failed.
     * @param ex The exception which gives details about the failure.
     */
    private void logFailure(@NotNull String operation, @NotNull Exception ex) {
        String msg = ex.getMessage();
        if (msg == null || msg.isEmpty()) {
            msg = "Unknown error";
        }

        msg += " (" + ex.getClass().getCanonicalName() + ")";
        this.log("%nError: %s failed: %s", operation, msg);
    }
}
