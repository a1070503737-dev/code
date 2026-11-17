package com.zhuangxiu.ledger.update

import android.content.Context
import com.zhuangxiu.ledger.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppUpdateRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val tokens: GitHubTokenStore,
) {
    private fun token(): String = UpdateCredentials.resolve(tokens::read)
    private val http = GitHubUpdateHttp(token = ::token)
    private val publicHttp = GitHubUpdateHttp()
    private val publicFeed = PublicUpdateFeed(publicHttp)
    private val downloadLock = Mutex()
    private val directory get() = File(context.cacheDir, "updates").apply { mkdirs() }

    suspend fun check(): AppRelease? = withContext(Dispatchers.IO) {
        val release = publicFeed.latest()
        currentCoroutineContext().ensureActive()
        release.takeIf {
            it.version > requireNotNull(AppVersion.parse(BuildConfig.VERSION_NAME)) &&
                requireNotNull(it.versionCode) > BuildConfig.VERSION_CODE
        }
    }

    suspend fun accessConfigured(): Boolean = withContext(Dispatchers.IO) { token().isNotBlank() }

    suspend fun configureAccess(value: String): AppRelease? = withContext(Dispatchers.IO) {
        val token = value.trim()
        if (!token.matches(Regex("[A-Za-z0-9_]{20,512}"))) throw IOException("请粘贴完整的 GitHub 访问令牌")
        // Explicit manual token validation must not silently pass through anonymous access.
        val release = checkWith(GitHubUpdateHttp(token = { token }, preferAnonymous = false))
        tokens.save(token)
        release
    }

    suspend fun clearAccess() = withContext(Dispatchers.IO) { tokens.clear() }

    private suspend fun checkWith(client: GitHubUpdateHttp): AppRelease? {
        val json = client.open(UpdateSource.LATEST_URL, "application/vnd.github+json").use { response ->
            val bytes = response.input.takeBytes(2 * 1024 * 1024)
            bytes.toString(Charsets.UTF_8)
        }
        currentCoroutineContext().ensureActive()
        return AppRelease.parse(json)?.takeIf { it.version > requireNotNull(AppVersion.parse(BuildConfig.VERSION_NAME)) }
    }

    suspend fun download(release: AppRelease, onProgress: (Long) -> Unit, onVerifying: () -> Unit): File =
        withContext(Dispatchers.IO) {
            downloadLock.withLock {
                val file = File(directory, "update-${release.version}-${release.sha256.take(12)}.apk")
                val coroutine = currentCoroutineContext()
                try {
                    // A valid completed download can be reused after returning from system settings.
                    if (!file.exists() || file.length() != release.size || VerifiedDownload.sha256(file) != release.sha256) {
                        directory.listFiles()?.forEach { it.delete() }
                        val client = if (release.versionCode != null) publicHttp else http
                        client.open(release.downloadUrl, "application/octet-stream").use { response ->
                            VerifiedDownload.copy(response.input, file, release.size, release.sha256,
                                ensureActive = { coroutine.ensureActive() }, progress = onProgress)
                        }
                    }
                    coroutine.ensureActive()
                    onVerifying()
                    UpdateApkVerifier.verify(context, file, release)
                    file
                } catch (e: Exception) {
                    file.delete()
                    throw e
                }
            }
        }

    suspend fun verifyForInstall(file: File, release: AppRelease) = withContext(Dispatchers.IO) {
        UpdateApkVerifier.verify(context, file, release)
    }
}

private fun java.io.InputStream.takeBytes(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer)
        if (count == -1) return output.toByteArray()
        if (output.size() + count > limit) throw IOException("发行版信息过大，请稍后重试")
        output.write(buffer, 0, count)
    }
}
