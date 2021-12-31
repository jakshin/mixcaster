/*
 * Copyright (c) 2021 Jason Jackson
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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the Installer class's uninstall() method.
 */
@ExtendWith(MockitoExtension.class)
class UninstallTest {
    @Mock
    private Installer installer;

    private Path plistPath;
    private Path fileToPreventDirectoryDeletion;

    @BeforeEach
    void setUp() {
        when(installer.uninstall()).thenCallRealMethod();

        plistPath = Path.of(System.getProperty("java.io.tmpdir"), UUID.randomUUID() + ".plist");
        when(installer.getPlistPath()).thenReturn(plistPath);

        // no-ops: getJarPath(), waitForLaunchdAgentRemoval(), execute(), log()
    }

    @AfterEach
    void tearDown() throws IOException {
        if (plistPath != null) {
            if (fileToPreventDirectoryDeletion != null)
                Files.deleteIfExists(fileToPreventDirectoryDeletion);
            Files.deleteIfExists(plistPath);
        }
    }

    @Test
    void deletesPlistFileAndCallsLaunchctl() throws IOException, InterruptedException {
        when(installer.execute(contains("launchctl list"))).thenReturn(0);  // 0 == already installed
        Files.writeString(plistPath, "fake");

        installer.uninstall();

        assertThat(plistPath).doesNotExist();
        verify(installer).execute(startsWith("/bin/launchctl remove "));
    }

    @Test
    void showsWarningIfRemovingExistingLaunchdAgentFails() throws IOException, InterruptedException {
        when(installer.execute(contains("launchctl list"))).thenReturn(0);    // 0 == already installed
        when(installer.execute(contains("launchctl remove"))).thenReturn(1);  // couldn't uninstall

        installer.uninstall();

        verify(installer).log(contains("Warning:"), any());
    }

    @Test
    void showsErrorIfRemovingPlistFileFails() throws IOException, InterruptedException {
        when(installer.execute(contains("launchctl list"))).thenReturn(1);  // 1 == not already installed
        if (plistPath.toFile().mkdir()) {
            fileToPreventDirectoryDeletion = Path.of(plistPath.toString(), "nope");
            Files.writeString(fileToPreventDirectoryDeletion, "nope");
        }

        int result = installer.uninstall();

        verify(installer, never()).log(contains("Success"), any());
        verify(installer).log(contains("Error:"), any());
        assertThat(result).isGreaterThan(0);
    }

    @Test
    void showsSuccessIfUninstallationSucceeds() throws IOException, InterruptedException {
        when(installer.execute(contains("launchctl list"))).thenReturn(0);    // 0 == already installed
        when(installer.execute(contains("launchctl remove"))).thenReturn(0);  // successful uninstallation

        int result = installer.uninstall();

        verify(installer, never()).log(contains("Error"), any());
        verify(installer).log(contains("Success:"), any());
        assertThat(result).isEqualTo(0);
    }
}
