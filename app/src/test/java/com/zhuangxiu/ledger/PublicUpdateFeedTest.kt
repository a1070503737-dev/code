package com.zhuangxiu.ledger

import android.app.Application
import com.zhuangxiu.ledger.update.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], manifest = Config.NONE)
class PublicUpdateFeedTest {
    private fun manifest() = JSONObject().apply {
        put("schemaVersion", 1)
        put("packageName", "com.zhuangxiu.ledger")
        put("versionName", "1.0.9")
        put("versionCode", 10)
        put("notes", "检查、下载和安装更新测试")
        put("apk", JSONObject().apply {
            put("fileName", "renovation-ledger-1.0.9-signed.apk")
            put("size", 3_000_000)
            put("sha256", "a".repeat(64))
        })
    }

    @Test fun checksUpdateWithoutRestApiEvenWhenItsQuotaIsExhausted() {
        val connections = mutableListOf<Connection>()
        val client = GitHubUpdateHttp(token = { throw AssertionError("Public feed must not read credentials") }, connect = { url ->
            if (URI(url).host == "api.github.com") throw AssertionError("REST API quota exhausted")
            Connection(url, if (connections.isEmpty()) 302 else 200,
                "https://release-assets.githubusercontent.com/update.json", manifest().toString().toByteArray()).also { connections += it }
        })
        val release = PublicUpdateFeed(client).latest()
        assertEquals(UpdateSource.MANIFEST_URL, connections.first().url.toString())
        assertEquals("1.0.9", release.version.toString())
        assertEquals(10L, release.versionCode)
        assertEquals("https://github.com/${UpdateSource.REPOSITORY}/releases/download/v1.0.9/renovation-ledger-1.0.9-signed.apk", release.downloadUrl)
        assertTrue(connections.all { it.disconnected && it.getRequestProperty("Authorization") == null })
        assertTrue(connections.all { it.getRequestProperty("X-GitHub-Api-Version") == null })
    }

    @Test fun downloadLocationIsDerivedFromTheTrustedRepository() {
        val root = manifest().apply { getJSONObject("apk").put("downloadUrl", "https://evil.example/app.apk") }
        val release = AppRelease.parseManifest(root.toString())
        assertEquals("github.com", URI(release.downloadUrl).host)
        assertFalse(release.downloadUrl.contains("evil"))
    }

    @Test fun invalidManifestCannotOfferAnInstallation() {
        val changes = listOf<(JSONObject) -> Unit>(
            { it.put("schemaVersion", 2) },
            { it.put("packageName", "another.app") },
            { it.put("versionName", "1.0.9-beta") },
            { it.put("versionCode", 0) },
            { it.put("versionCode", Long.MAX_VALUE) },
            { it.getJSONObject("apk").put("fileName", "renovation-ledger-1.0.9-debug.apk") },
            { it.getJSONObject("apk").put("size", UpdateSource.MAX_APK_BYTES + 1) },
            { it.getJSONObject("apk").put("sha256", "invalid") },
        )
        for (change in changes) {
            val root = manifest().also(change)
            assertThrows(IOException::class.java) { AppRelease.parseManifest(root.toString()) }
        }
    }

    @Test fun oversizedFeedIsRejectedAndConnectionIsClosed() {
        val connection = Connection(UpdateSource.MANIFEST_URL, 200, "", ByteArray(2 * 1024 * 1024 + 1))
        val client = GitHubUpdateHttp(connect = { connection })
        assertThrows(IOException::class.java) { PublicUpdateFeed(client).latest() }
        assertTrue(connection.disconnected)
    }

    @Test fun apkVersionCodeMustMatchTheManifestExactly() {
        val current = ApkIdentity("com.zhuangxiu.ledger", 8, "1.0.7", setOf("same-signer"))
        val updated = current.copy(versionCode = 10, versionName = "1.0.9")
        UpdateApkVerifier.validateIdentity(current, updated, "1.0.9", 10)
        assertThrows(IOException::class.java) {
            UpdateApkVerifier.validateIdentity(current, updated, "1.0.9", 11)
        }
    }

    private class Connection(url: String, private val status: Int, private val location: String, private val content: ByteArray) : HttpURLConnection(URI(url).toURL()) {
        var disconnected = false
        override fun connect() { }
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun getResponseCode() = status
        override fun getHeaderField(name: String?) = if (name == "Location") location else null
        override fun getInputStream() = ByteArrayInputStream(content)
    }
}
