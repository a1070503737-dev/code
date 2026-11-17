package com.zhuangxiu.ledger

import com.zhuangxiu.ledger.update.GitHubUpdateHttp
import com.zhuangxiu.ledger.update.TokenCipher
import com.zhuangxiu.ledger.update.UpdateSource
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.security.GeneralSecurityException
import javax.crypto.spec.SecretKeySpec

class GitHubAccessTest {
    private val key = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")

    @Test fun tokenIsEncryptedWithRandomNonceAndCanBeReadBack() {
        val token = "unit_test_token_not_a_real_credential"
        val first = TokenCipher.encrypt(token, key)
        val second = TokenCipher.encrypt(token, key)
        assertNotEquals(first, second)
        assertFalse(first.contains(token))
        assertEquals(token, TokenCipher.decrypt(first, key))
    }

    @Test fun wrongKeyCannotReadStoredToken() {
        val encrypted = TokenCipher.encrypt("unit_test_token", key)
        val different = SecretKeySpec(ByteArray(32) { 99 }, "AES")
        assertThrows(GeneralSecurityException::class.java) { TokenCipher.decrypt(encrypted, different) }
    }

    @Test fun authHeaderIsNotForwardedOnAssetRedirect() {
        val connections = mutableListOf<FakeConnection>()
        val client = GitHubUpdateHttp(token = { "unit_test_token" }, preferAnonymous = false, connect = { url ->
            FakeConnection(url, if (connections.isEmpty()) 302 else 200,
                "https://release-assets.githubusercontent.com/example.apk?token=temporary").also { connections += it }
        })
        client.open(UpdateSource.LATEST_URL, "application/octet-stream").use { assertEquals(3, it.input.readBytes().size) }
        assertEquals("Bearer unit_test_token", connections[0].getRequestProperty("Authorization"))
        assertNull(connections[1].getRequestProperty("Authorization"))
        assertTrue(connections.all { it.disconnected })
    }

    @Test fun untrustedRedirectIsRejectedBeforeOpeningAnotherConnection() {
        var calls = 0
        val first = FakeConnection(UpdateSource.LATEST_URL, 302, "https://evil.example/update.apk")
        val client = GitHubUpdateHttp(token = { "unit_test_token" }, connect = { calls++; first })
        assertThrows(IOException::class.java) { client.open(UpdateSource.LATEST_URL, "application/json") }
        assertEquals(1, calls)
        assertTrue(first.disconnected)
    }

    @Test fun insufficientPermissionShowsAccessAdviceWithoutRevealingToken() {
        val connection = FakeConnection(UpdateSource.LATEST_URL, 403, "")
        val client = GitHubUpdateHttp(token = { "unit_test_token" }, preferAnonymous = false, connect = { connection })
        val error = assertThrows(IOException::class.java) { client.open(UpdateSource.LATEST_URL, "application/json") }
        assertTrue(error.message!!.contains("只读权限"))
        assertFalse(error.message!!.contains("unit_test_token"))
        assertTrue(connection.disconnected)
    }

    @Test fun publicReleaseWorksWithoutReadingOrSendingAStaleToken() {
        val connection = FakeConnection(UpdateSource.LATEST_URL, 200, "")
        val client = GitHubUpdateHttp(token = { throw IOException("Expired or unreadable token") }, connect = { connection })
        client.open(UpdateSource.LATEST_URL, "application/json").use { assertEquals(3, it.input.readBytes().size) }
        assertNull(connection.getRequestProperty("Authorization"))
        assertTrue(connection.disconnected)
    }

    @Test fun privateReleaseRetriesWithAuthorizationOnlyAfterAnonymousFailure() {
        val connections = mutableListOf<FakeConnection>()
        val client = GitHubUpdateHttp(token = { "unit_test_token" }, connect = { url ->
            FakeConnection(url, if (connections.isEmpty()) 404 else 200, "").also { connections += it }
        })
        client.open(UpdateSource.LATEST_URL, "application/json").close()
        assertEquals(2, connections.size)
        assertNull(connections[0].getRequestProperty("Authorization"))
        assertEquals("Bearer unit_test_token", connections[1].getRequestProperty("Authorization"))
        assertTrue(connections.all { it.disconnected })
    }

    @Test fun publicAssetRedirectNeverReadsTokenOrForwardsAuthorization() {
        val connections = mutableListOf<FakeConnection>()
        val client = GitHubUpdateHttp(token = { throw IOException("Token must not be used") }, connect = { url ->
            FakeConnection(url, if (connections.isEmpty()) 302 else 200,
                "https://release-assets.githubusercontent.com/public.apk").also { connections += it }
        })
        client.open(UpdateSource.LATEST_URL, "application/octet-stream").close()
        assertEquals(2, connections.size)
        assertTrue(connections.all { it.getRequestProperty("Authorization") == null && it.disconnected })
    }

    @Test fun absentCredentialsDoNotCauseAnAnonymousRetryLoop() {
        var calls = 0
        val connection = FakeConnection(UpdateSource.LATEST_URL, 404, "")
        val client = GitHubUpdateHttp(connect = { calls++; connection })
        assertThrows(IOException::class.java) { client.open(UpdateSource.LATEST_URL, "application/json") }
        assertEquals(1, calls)
        assertTrue(connection.disconnected)
    }

    @Test fun manifestApkDownloadsDirectlyWithoutAnyRestApiRequest() {
        val connections = mutableListOf<FakeConnection>()
        val release = com.zhuangxiu.ledger.update.AppRelease(
            com.zhuangxiu.ledger.update.AppVersion(1, 0, 9), "test", 0, 3, "a".repeat(64), 10)
        val client = GitHubUpdateHttp(token = { throw AssertionError("Must not read credentials") }, connect = { url ->
            if (URI(url).host == "api.github.com") throw AssertionError("Must not use REST API")
            FakeConnection(url, if (connections.isEmpty()) 302 else 200,
                "https://release-assets.githubusercontent.com/app.apk").also { connections += it }
        })
        client.open(release.downloadUrl, "application/octet-stream").close()
        assertEquals("github.com", connections.first().url.host)
        assertTrue(connections.all { it.getRequestProperty("Authorization") == null && it.disconnected })
    }

    private class FakeConnection(url: String, private val status: Int, private val location: String) : HttpURLConnection(URI(url).toURL()) {
        var disconnected = false
        override fun connect() { }
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun getResponseCode() = status
        override fun getHeaderField(name: String?) = if (name == "Location") location else null
        override fun getInputStream() = ByteArrayInputStream(byteArrayOf(1, 2, 3))
    }
}
