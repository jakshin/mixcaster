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

import jakshin.mixcaster.utils.FileUtils;
import java.io.*;
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
     */
    public void install() {
        this.log("Installing the Mixcaster service...");

        try {
            String jarPath = this.getJarPath();

            if (jarPath == null) {
                this.log("Error: Installation must be performed using the jar file, not in an IDE");
                return;
            }

            this.log("%nCreating the service's launchd configuration file...");

            String plistXml = this.loadPlistResource();
            plistXml = plistXml.replace("{{serviceLabel}}", this.getServiceLabel());
            plistXml = plistXml.replace("{{Mixcaster.jar}}", jarPath);

            File plistFile = new File(this.getPlistPath());
            FileUtils.writeStringToFile(plistFile.toString(), plistXml, "UTF-8");

            this.log("Created %s", plistFile);

            this.log("%nLoading the service via launchd...");

            if (this.checkLaunchdAgent()) {
                if (this.removeLaunchdAgent() != 0) {
                    this.log("Warning: Failed to remove the existing service registration");
                    this.log("Uninstallation will continue, but you may need to reboot");
                }
            }

            int result = this.loadLaunchdAgent(plistFile);

            if (result == 0) {
                this.log("Loaded the service");
                this.log("%nSuccess: The Mixcaster service was installed and started");
            }
            else {
                this.log("Error: Failed to load the service (%d)", result);
            }
        }
        catch (IOException | InterruptedException ex) {
            this.logFailure("Installation", ex);
        }
    }

    /**
     * Uninstalls the service, after stopping it via launchd.
     */
    public void uninstall() {
        this.log("Uninstalling the Mixcaster service...");
        File plistFile = new File(this.getPlistPath());

        try {
            this.log("%nRemoving the service from launchd...");

            if (!this.checkLaunchdAgent()) {
                this.log("The service is not registered with launchd");
            }
            else {
                int result = this.removeLaunchdAgent();

                if (result == 0) {
                    this.log("Removed the service");
                }
                else {
                    this.log("Warning: Failed to remove the service (%d)", result);
                    this.log("Uninstallation will continue, but you may need to reboot to stop the service");
                }
            }

            this.log("%nRemoving the service's launchd configuration file...");
            boolean exists = plistFile.exists();

            if (!exists || plistFile.delete()) {
                String msg = (exists) ? "Removed %s" : "File %s doesn't exist";
                this.log(msg, plistFile);

                this.log("%nSuccess: The Mixcaster service was uninstalled");
            }
            else {
                this.log("Error: Failed to remove %s", plistFile);
            }
        }
        catch (IOException | InterruptedException ex) {
            this.logFailure("Uninstallation", ex);
        }
    }

    /**
     * Gets the service's launchd label.
     * This is the unique name for the service (which we make per-user),
     * and also by convention the name of the service's plist configuration file.
     *
     * @return The service's launchd label.
     */
    private String getServiceLabel() {
        String userName = System.getProperty("user.name").replaceAll("\\W+", "_");
        return String.format("jakshin.mixcaster.%s", userName);
    }

    /**
     * Gets the path to the service's launchd configuration file, for this user.
     * @return The path to the service's launchd configuration file, for this user.
     */
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
     * @throws IOException
     * @throws InterruptedException
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
     * @throws IOException
     * @throws InterruptedException
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
     * @throws IOException
     * @throws InterruptedException
     */
    private boolean checkLaunchdAgent() throws IOException, InterruptedException {
        String cmd = String.format("/bin/launchctl list %s", this.getServiceLabel());

        Process proc = Runtime.getRuntime().exec(cmd);
        int result = proc.waitFor();
        return (result == 0);  // assume any failure means "service doesn't exist"
    }

    /**
     * Loads the launchd-plist.xml resource.
     *
     * @return The resource's XML content.
     * @throws IOException
     * @throws UnsupportedEncodingException
     */
    private String loadPlistResource() throws IOException, UnsupportedEncodingException {
        StringBuilder sb = new StringBuilder(600);

        try (InputStream in = this.getClass().getResourceAsStream("launchd-plist.xml")) {
            final char[] buf = new char[600];

            try (Reader reader = new InputStreamReader(in, "UTF-8")) {
                while (true) {
                    int count = reader.read(buf, 0, buf.length);
                    if (count < 0) break;

                    sb.append(buf, 0, count);
                }
            }
        }

        return sb.toString();
    }

    /**
     * "Logs" a message by displaying it using System.out.
     * This class doesn't use the logging facilities used by the rest of the program.
     *
     * @param fmt The format string, or just the string to display; processed through String.format().
     * @param args Any arguments needed to format the format string.
     */
    private void log(String fmt, Object... args) {
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
    private void logFailure(String operation, Exception ex) {
        String msg = ex.getMessage();
        if (msg == null || msg.isEmpty()) {
            msg = "Unknown error";
        }

        msg += " (" + ex.getClass().getCanonicalName() + ")";
        this.log("%nError: %s failed: %s", operation, msg);
    }
}
