package com.zhuangxiu.ledger

import android.app.Application
import com.zhuangxiu.ledger.update.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], manifest = Config.NONE)
class AppUpdateTest {
    private fun release(version: String = "1.0.10"): JSONObject = JSONObject().apply {
        put("tag_name", "v$version")
        put("draft", false)
        put("prerelease", false)
        put("body", "改善下载与安装体验")
        put("assets", JSONArray().put(JSONObject().apply {
            put("id", 100)
            put("name", "renovation-ledger-$version-signed.apk")
            put("size", 3000)
            put("state", "uploaded")
            put("digest", "sha256:" + "a".repeat(64))
        }))
    }

    @Test fun versionsCompareNumericallyAndRejectPrereleaseNames() {
        assertTrue(AppVersion.parse("v1.0.10")!! > AppVersion.parse("1.0.9")!!)
        assertTrue(AppVersion.parse("1.10.0")!! > AppVersion.parse("1.9.99")!!)
        assertEquals(AppVersion.parse("v1.0.4"), AppVersion.parse("1.0.4"))
        assertNull(AppVersion.parse("1.0.5-beta"))
        assertNull(AppVersion.parse("99999999999999999.0.0"))
    }

    @Test fun stableReleaseUsesExactSignedApkAndAssetApi() {
        val result = AppRelease.parse(release().toString())!!
        assertEquals("1.0.10", result.version.toString())
        assertEquals("https://api.github.com/repos/${UpdateSource.REPOSITORY}/releases/assets/100", result.downloadUrl)
        assertEquals("a".repeat(64), result.sha256)
    }

    @Test fun draftAndPrereleaseAreNotOffered() {
        assertNull(AppRelease.parse(release().put("draft", true).toString()))
        assertNull(AppRelease.parse(release().put("prerelease", true).toString()))
    }

    @Test fun debugApkIsNotAnUpdateCandidate() {
        val json = release()
        json.getJSONArray("assets").getJSONObject(0).put("name", "renovation-ledger-1.0.10-debug.apk")
        assertThrows(IOException::class.java) { AppRelease.parse(json.toString()) }
    }

    @Test fun missingDigestOrOversizedAssetIsRejected() {
        val json = release()
        val asset = json.getJSONArray("assets").getJSONObject(0)
        asset.remove("digest")
        assertThrows(IOException::class.java) { AppRelease.parse(json.toString()) }
        asset.put("digest", "sha256:" + "a".repeat(64))
        asset.put("size", UpdateSource.MAX_APK_BYTES + 1)
        assertThrows(IOException::class.java) { AppRelease.parse(json.toString()) }
    }

    @Test fun redirectsStayOnGithubHttpsAndCredentialsStayOnRepositoryApi() {
        assertTrue(UpdateUrlPolicy.canAuthorize(UpdateSource.LATEST_URL))
        assertTrue(UpdateUrlPolicy.allowed("https://release-assets.githubusercontent.com/github-production-release-asset/1/a?token=temporary"))
        assertFalse(UpdateUrlPolicy.canAuthorize("https://release-assets.githubusercontent.com/a"))
        assertFalse(UpdateUrlPolicy.allowed("http://api.github.com/repos/${UpdateSource.REPOSITORY}/releases/latest"))
        assertFalse(UpdateUrlPolicy.allowed("https://api.github.com.evil.example/repos/${UpdateSource.REPOSITORY}/releases"))
        assertFalse(UpdateUrlPolicy.allowed("https://api.github.com/repos/other/repository/releases"))
        assertFalse(UpdateUrlPolicy.allowed("https://api.github.com/repos/${UpdateSource.REPOSITORY}/../../other/repository/releases"))
        assertFalse(UpdateUrlPolicy.allowed("https://user:password@api.github.com/repos/${UpdateSource.REPOSITORY}/releases"))
    }

    @Test fun packageVersionAndSignerMustAllPermitAnUpdate() {
        val installed = ApkIdentity("com.zhuangxiu.ledger", 4, "1.0.3", setOf("original"))
        val update = installed.copy(versionCode = 5, versionName = "1.0.4")
        UpdateApkVerifier.validateIdentity(installed, update, "1.0.4")
        for (bad in listOf(update.copy(packageName = "another.app"), update.copy(versionCode = 4),
            update.copy(versionName = "1.0.5"), update.copy(signers = setOf("different")), update.copy(signers = emptySet()))) {
            assertThrows(IOException::class.java) { UpdateApkVerifier.validateIdentity(installed, bad, "1.0.4") }
        }
    }
}
