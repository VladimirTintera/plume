package eu.tintera.plume.core.update

import eu.tintera.plume.core.platform.DesktopOs
import java.io.File
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopReleasesTest {

    @Test
    fun versionFromTagIgnoresOtherPlatforms() {
        assertEquals(DesktopVersion(1, 2, 3), DesktopVersion.fromTag("desktop-1.2.3"))
        assertNull(DesktopVersion.fromTag("android-1.2.3-45"))
        assertNull(DesktopVersion.fromTag("desktop-1.2"))
        assertNull(DesktopVersion.fromTag("desktop-256.0.0"), "mimo meze MSI")
    }

    @Test
    fun versionsCompareNumericallyAndCodesDiffer() {
        assertTrue(DesktopVersion(1, 10, 0) > DesktopVersion(1, 9, 99))
        assertTrue(DesktopVersion(2, 0, 0) > DesktopVersion(1, 255, 65535))
        assertTrue(DesktopVersion(1, 0, 1).code != DesktopVersion(1, 1, 0).code)
    }

    @Test
    fun picksNewestDesktopReleaseWithAssetForThisSystem() {
        val release = latestDesktopRelease(RELEASES, DesktopOs.MacOs, arm64 = true)
        assertEquals(DesktopVersion(1, 3, 0), release?.version, "android, draft ani pre-release se nepočítají")
        assertEquals("Plume-1.3.0-macos-arm64.dmg", release?.asset?.name)
        assertEquals(DIGEST_AB, release?.asset?.sha256, "malými písmeny")

        val windows = latestDesktopRelease(RELEASES, DesktopOs.Windows, arm64 = false)
        assertEquals("Plume-1.3.0.msi", windows?.asset?.name)
    }

    @Test
    fun noReleaseWithoutMatchingAsset() {
        assertNull(latestDesktopRelease(RELEASES, DesktopOs.MacOs, arm64 = false), "Intel Mac balíček nemá")
        assertNull(latestDesktopRelease(RELEASES, DesktopOs.Linux, arm64 = false))
        assertNull(latestDesktopRelease("{\"message\":\"API rate limit exceeded\"}", DesktopOs.Windows, arm64 = false))
    }

    @Test
    fun olderReleaseIsUsedWhenNewestLacksAsset() {
        val json = """[
          {"tag_name":"desktop-1.4.0","draft":false,"prerelease":false,"assets":[{"name":"Plume-1.4.0.msi","browser_download_url":"https://example.com/a.msi","size":1,"digest":"sha256:$DIGEST_12"}]},
          $V120
        ]"""
        assertEquals(DesktopVersion(1, 2, 0), latestDesktopRelease(json, DesktopOs.MacOs, arm64 = true)?.version)
    }

    @Test
    fun macBundleIsThreeLevelsAboveLauncher() {
        val root = kotlin.io.path.createTempDirectory().toFile()
        val launcher = File(root, "Plume.app/Contents/MacOS/Plume").apply { parentFile.mkdirs(); writeText("") }
        assertEquals(File(root, "Plume.app"), DesktopInstaller.macBundle(launcher))
        val loose = File(root, "bin/Plume").apply { parentFile.mkdirs(); writeText("") }
        assertNull(DesktopInstaller.macBundle(loose))
        root.deleteRecursively()
    }

    @Test
    fun windowsPathsTravelInEnvironmentNotInScript() {
        // Typografický apostrof PowerShell bere jako konec řetězce, mezera dřív rozbila uvozovky
        // v příkazové řádce — obojí jde mimo text skriptu.
        val msi = "C:\\Users\\Jan O\u2019Brien\\AppData\\Local\\Plume\\updates\\Plume-1.3.0.msi"
        val launcher = "C:\\Users\\Jan O'Brien\\AppData\\Local\\Plume\\Plume.exe"
        assertEquals(
            mapOf("PLUME_PID" to "42", "PLUME_MSI" to msi, "PLUME_LAUNCHER" to launcher),
            DesktopInstaller.windowsEnvironment("42", msi, launcher),
        )
        val script = DesktopInstaller.WINDOWS_SCRIPT
        assertTrue("\$env:PLUME_MSI" in script && "\$env:PLUME_LAUNCHER" in script && "\$env:PLUME_PID" in script)
        assertFalse('"' in script, "uvozovky pro msiexec skládá skript, v příkazové řádce nejsou")
    }

    @Test
    fun encodedCommandIsBase64OfUtf16Le() {
        val encoded = DesktopInstaller.encodedPowerShell("Write-Output 'ž'")
        assertEquals("Write-Output 'ž'", String(Base64.getDecoder().decode(encoded), Charsets.UTF_16LE))
    }

    @Test
    fun releaseWithoutValidDigestIsNotOffered() {
        val json = """[
          {"tag_name":"desktop-1.5.0","draft":false,"prerelease":false,"assets":[{"name":"Plume-1.5.0.msi","browser_download_url":"https://example.com/5.msi","size":1}]},
          {"tag_name":"desktop-1.4.0","draft":false,"prerelease":false,"assets":[{"name":"Plume-1.4.0.msi","browser_download_url":"https://example.com/4.msi","size":1,"digest":"sha256:AB12"}]},
          $V120
        ]"""
        assertEquals(DesktopVersion(1, 2, 0), latestDesktopRelease(json, DesktopOs.Windows, arm64 = false)?.version)
    }

    @Test
    fun downloadedFileNameComesFromVersionNotFromAssetName() {
        val asset = ReleaseAsset("..\\..\\evil.msi", "https://example.com/a.msi", 1, DIGEST_AB)
        assertEquals("Plume-1.3.0.msi", DesktopRelease(DesktopVersion(1, 3, 0), asset).fileName)
        val dmg = asset.copy(name = "Plume-1.3.0-macos-arm64.dmg")
        assertEquals("Plume-1.3.0.dmg", DesktopRelease(DesktopVersion(1, 3, 0), dmg).fileName)
    }

    private companion object {
        val DIGEST_AB = "ab".repeat(32)
        val DIGEST_CD = "cd".repeat(32)
        val DIGEST_12 = "12".repeat(32)

        val V120 = """{"tag_name":"desktop-1.2.0","draft":false,"prerelease":false,"assets":[
            {"name":"Plume-1.2.0.msi","browser_download_url":"https://example.com/1.2.0.msi","size":10,"digest":"sha256:$DIGEST_12"},
            {"name":"Plume-1.2.0-macos-arm64.dmg","browser_download_url":"https://example.com/1.2.0.dmg","size":20,"digest":"sha256:$DIGEST_12"}]}"""

        val RELEASES = """[
          {"tag_name":"android-1.9.0-90","draft":false,"prerelease":false,"assets":[{"name":"app.aab","browser_download_url":"https://example.com/app.aab","size":1,"digest":"sha256:$DIGEST_12"}]},
          {"tag_name":"desktop-2.0.0","draft":true,"prerelease":false,"assets":[{"name":"Plume-2.0.0.msi","browser_download_url":"https://example.com/2.msi","size":1,"digest":"sha256:$DIGEST_12"}]},
          {"tag_name":"desktop-1.4.0","draft":false,"prerelease":true,"assets":[{"name":"Plume-1.4.0.msi","browser_download_url":"https://example.com/4.msi","size":1,"digest":"sha256:$DIGEST_12"}]},
          {"tag_name":"desktop-1.3.0","draft":false,"prerelease":false,"assets":[
            {"name":"Plume-1.3.0.msi","browser_download_url":"https://example.com/1.3.0.msi","size":10,"digest":"sha256:${DIGEST_CD.uppercase()}"},
            {"name":"Plume-1.3.0-macos-arm64.dmg","browser_download_url":"https://example.com/1.3.0.dmg","size":20,"digest":"sha256:${DIGEST_AB.uppercase()}"}]},
          $V120
        ]"""
    }
}
