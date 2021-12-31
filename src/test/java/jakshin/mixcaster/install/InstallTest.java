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
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the Installer class's install() method.
 */
@ExtendWith(MockitoExtension.class)
class InstallTest {
    @Mock
    private Installer installer;

    private Path plistPath;

    @BeforeEach
    void setUp() {
        when(installer.install()).thenCallRealMethod();
        when(installer.getJarPath()).thenReturn("/fake/path/to/fake.jar");

        plistPath = Path.of(System.getProperty("java.io.tmpdir"), UUID.randomUUID() + ".plist");
        when(installer.getPlistPath()).thenReturn(plistPath);

        // no-ops: waitForLaunchdAgentRemoval(), execute(), log()
    }

    @AfterEach
    void tearDown() throws IOException {
        if (plistPath != null) {
            Files.deleteIfExists(plistPath);
        }
    }

    @Test
    void writesPlistFileAndCallsLaunchctl() throws IOException, InterruptedException {
        when(installer.execute(contains("launchctl list"))).thenReturn(1);  // 1 == not already installed
        assertThat(plistPath).doesNotExist();  // sanity check

        installer.install();

        assertThat(plistPath).exists();
        if (plistPath.toFile().isFile()) {
            String plistXml = Files.readString(plistPath);
            assertThat(plistXml).contains(installer.getJarPath());
            assertThat(plistXml).matches("(?s).*<key>KeepAlive</key>\\s*<true\\s*/>.*");
            assertThat(plistXml).matches("(?s).*<key>RunAtLoad</key>\\s*<true\\s*/>.*");
            assertThat(plistXml).doesNotContain("{{").doesNotContain("}}");
        }

        verify(installer).execute(startsWith("/bin/launchctl load "));
    }

    @Test
    void firstRemovesAnyExistingLaunchdAgent() throws IOException, InterruptedException {
        when(installer.execute(contains("launchctl list"))).thenReturn(0);  // 0 == already installed
        when(installer.waitForLaunchdAgentRemoval()).thenReturn(true);

        installer.install();

        InOrder inOrder = inOrder(installer);
        inOrder.verify(installer).execute(startsWith("/bin/launchctl list "));
        inOrder.verify(installer).waitForLaunchdAgentRemoval();
    }

    @Test
    void showsWarningIfRemovingExistingLaunchdAgentFails() throws IOException, InterruptedException {
        when(installer.execute(contains("launchctl list"))).thenReturn(0);    // 0 == already installed
        when(installer.execute(contains("launchctl remove"))).thenReturn(1);  // couldn't uninstall

        installer.install();

        verify(installer).log(contains("WARNING:"), any());
    }

    @Test
    void showsErrorIfInstallationFails() throws IOException, InterruptedException {
        // launchctl might return non-zero to tell us something went wrong
        when(installer.execute(contains("launchctl list"))).thenReturn(1);  // 1 == not already installed
        when(installer.execute(contains("launchctl load"))).thenReturn(1);  // couldn't install

        int result = installer.install();

        verify(installer, never()).log(contains("Success"), any());
        verify(installer).log(contains("Error:"), any());
        assertThat(result).isGreaterThan(0);
    }

    @Test
    void showsErrorIfInstallationThrows() throws IOException, InterruptedException {
        // we might get an exception while writing the plist or calling launchctl
        when(installer.execute(contains("launchctl list"))).thenReturn(1);  // 1 == not already installed
        when(installer.execute(contains("launchctl load"))).thenThrow(new IOException((String) null));

        int result = installer.install();

        verify(installer, never()).log(contains("Success"), any());
        verify(installer).log(contains("failed"), any());
        verify(installer).log(contains("Error:"), any());
        assertThat(result).isGreaterThan(0);
    }

    @Test
    void showsSuccessIfInstallationSucceeds() throws IOException, InterruptedException {
        when(installer.execute(contains("launchctl list"))).thenReturn(1);  // 1 == not already installed
        when(installer.execute(contains("launchctl load"))).thenReturn(0);  // successful installation

        int result = installer.install();

        verify(installer, never()).log(contains("Error"), any());
        verify(installer).log(contains("Success:"), any());
        assertThat(result).isEqualTo(0);
    }
}
