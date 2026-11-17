package com.zhuangxiu.ledger.update

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

internal object VerifiedDownload {
    fun copy(
        input: InputStream, target: File, expectedBytes: Long, expectedSha256: String,
        ensureActive: () -> Unit, progress: (Long) -> Unit,
    ) {
        val partial = File(target.parentFile, "${target.name}.part")
        try {
            require(expectedBytes in 1..UpdateSource.MAX_APK_BYTES)
            val digest = MessageDigest.getInstance("SHA-256")
            var count = 0L
            partial.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    ensureActive()
                    val read = input.read(buffer)
                    if (read == -1) break
                    count += read
                    if (count > expectedBytes) throw IOException("下载的文件大小不正确，请重新下载")
                    digest.update(buffer, 0, read)
                    output.write(buffer, 0, read)
                    progress(count)
                }
            }
            ensureActive()
            if (count != expectedBytes || digest.digest().hex() != expectedSha256) {
                throw IOException("安装包校验失败，请重新下载")
            }
            if (!partial.renameTo(target)) throw IOException("无法保存安装包，请检查剩余存储空间")
        } finally {
            partial.delete()
        }
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().hex()
    }
}

internal fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }
