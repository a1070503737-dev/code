package com.zhuangxiu.ledger.update

import java.io.ByteArrayOutputStream
import java.io.IOException

/** Reads only a release attachment. This client has no credential source and never calls REST. */
internal class PublicUpdateFeed(private val client: GitHubUpdateHttp = GitHubUpdateHttp()) {
    fun latest(): AppRelease {
        val json = client.open(UpdateSource.MANIFEST_URL, "application/json").use { response ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = response.input.read(buffer)
                if (count == -1) break
                if (output.size() + count > 2 * 1024 * 1024) throw IOException("更新文件过大，请联系维护者")
                output.write(buffer, 0, count)
            }
            output.toString(Charsets.UTF_8.name())
        }
        return AppRelease.parseManifest(json)
    }
}
