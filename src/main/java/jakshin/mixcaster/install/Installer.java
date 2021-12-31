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
import org.jetbrains.annotations.VisibleForTesting;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * Installs and uninstalls the Mixcaster service as a launchd user agent.
 * The service is started immediately after installation, and stopped before uninstallation.
 */
public class Installer {
    /**
     * Installs the service, and starts it via launchd.
     * @return A code indicating success (0) or failure (1 or 2).
     */
    public int install() {
        this.log("Installing the Mixcaster service...");

        try {
            if (this.checkLaunchdAgent()) {
                this.getJarPath();  // avoid damaging an actual local installation when running/debugging in an IDE

                this.log("%nThe service is already registered with launchd");
                this.log("Removing the existing service registration so reinstallation can continue");

                boolean removed = (this.removeLaunchdAgent() == 0);

                if (removed) {
                    // 'launchctl remove' returns without waiting for the service to be removed,
                    // and barreling ahead attempting to install it again will fail (silently);
                    // before continuing, wait up to 10s for the service to actually be removed
                    removed = this.waitForLaunchdAgentRemoval();
                }

                if (! removed) {
                    this.log("%nWARNING: Failed to remove the existing service registration");
                    this.log("Installation will continue, but you may need to reboot to complete it");
                }
            }

            this.log("%nCreating the service's launchd configuration file...");

            String resourcePath = "install/launchd-plist.xml";
            String plistXml = ResourceLoader.loadResourceAsText(resourcePath, 600).toString();

            plistXml = plistXml.replace("{{serviceLabel}}", this.getServiceLabel());
            plistXml = plistXml.replace("{{mixcaster.jar}}", this.getJarPath());

            Path plistPath = this.getPlistPath();
            Files.writeString(plistPath, plistXml, StandardCharsets.UTF_8);

            this.log("Created %s", plistPath);
            this.log("%nLoading the service via launchd...");

            int result = this.loadLaunchdAgent(plistPath);

            if (result != 0) {
                this.log("Error: Failed to load the service (%d)", result);
                return 2;
            }

            this.log("Loaded the service");
            this.log("%nSuccess: The Mixcaster service was installed and started");
            return 0;
        }
        catch (IllegalStateException ex) {
            this.log(ex.getMessage());
            return 1;
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

            this.log("%nDeleting the service's launchd configuration file...");

            Path plistPath = this.getPlistPath();
            boolean existed = Files.deleteIfExists(plistPath);

            String msg = (existed) ? "Removed %s" : "File %s doesn't exist";
            this.log(msg, plistPath);

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
     */
    @NotNull
    private String getServiceLabel() {
        String userName = System.getProperty("user.name").replaceAll("\\W+", "_");
        return String.format("jakshin.mixcaster.%s", userName);
    }

    /**
     * Gets the path to the currently running jar file.
     * @throws IllegalStateException if the code isn't running in a jar file.
     */
    @NotNull
    @VisibleForTesting
    String getJarPath() {
        Path path = Paths.get(Installer.class.getProtectionDomain().getCodeSource().getLocation().getPath());
        String jarPath = path.toString();

        if (! jarPath.toLowerCase(Locale.ROOT).endsWith(".jar")) {
            String message = "Error: Installation must be performed using the jar file, not in an IDE";
            throw new IllegalStateException(message);
        }

        return jarPath;
    }

    /**
     * Gets the path to the service's launchd configuration file, for this user.
     */
    @NotNull
    @VisibleForTesting
    Path getPlistPath() {
        String pathStr = String.format("Library/LaunchAgents/%s.plist", this.getServiceLabel());
        return Paths.get(System.getProperty("user.home"), pathStr);
    }

    /**
     * Loads the service's launchd configuration file into launchd.
     * The service will be started immediately, because of how we've set up the configuration plist.
     *
     * @param plistPath The path to the user-specific service configuration file.
     * @return The launchctl program's exit code.
     */
    private int loadLaunchdAgent(@NotNull Path plistPath) throws IOException, InterruptedException {
        String cmd = String.format("/bin/launchctl load %s", plistPath);
        return execute(cmd);
    }

    /**
     * Removes the service from launchd.
     * This also stops the service immediately.
     *
     * @return The launchctl program's exit code.
     */
    private int removeLaunchdAgent() throws IOException, InterruptedException {
        String cmd = String.format("/bin/launchctl remove %s", this.getServiceLabel());
        return execute(cmd);
    }

    /**
     * Checks whether the service is already registered with launchd.
     */
    private boolean checkLaunchdAgent() throws IOException, InterruptedException {
        String cmd = String.format("/bin/launchctl list %s", this.getServiceLabel());
        int result = execute(cmd);
        return (result == 0);  // assume any failure means "service doesn't exist"
    }

    /**
     * Waits for up to 10 seconds for removal of the service to complete.
     *
     * ('launchctl remove' returns immediately, without waiting for success/completion,
     * which causes silent installation failure if we're reinstalling -- we install
     * while the service is still being removed).
     *
     * @return Whether removal of the service completed before our 10s timeout.
     */
    @VisibleForTesting
    boolean waitForLaunchdAgentRemoval() throws IOException, InterruptedException {
        int waitMillis = 50;
        long start = System.nanoTime();

        while (this.checkLaunchdAgent()) {
            if (System.nanoTime() - start >= 10_000_000_000L) {
                return false;
            }

            //noinspection BusyWait
            Thread.sleep(waitMillis);

            if (waitMillis < 800) {
                waitMillis *= 2;
            }
        }

        return true;
    }

    /**
     * Executes a command line in a separate process, and returns its exit code.
     * @param cmd The command line to execute.
     */
    @VisibleForTesting
    int execute(String cmd) throws IOException, InterruptedException {
        this.log("Executing: %s", cmd);

        Process proc = Runtime.getRuntime().exec(cmd);
        return proc.waitFor();
    }

    /**
     * "Logs" a message by displaying it using System.out.
     * This class doesn't use the logging facilities used by the rest of the program.
     *
     * @param fmt The format string, or just the string to display; processed through String.format().
     * @param args Any arguments needed to format the format string.
     */
    @VisibleForTesting
    void log(@NotNull String fmt, Object... args) {
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
