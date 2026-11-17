package com.zhuangxiu.ledger

import com.zhuangxiu.ledger.update.VerifiedDownload
import com.zhuangxiu.ledger.update.hex
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.security.MessageDigest

class VerifiedDownloadTest {
    @get:Rule val temporary = TemporaryFolder()
    private val bytes = ByteArray(150_000) { (it % 251).toByte() }
    private val digest get() = MessageDigest.getInstance("SHA-256").digest(bytes).hex()
    private fun target() = File(temporary.root, "update.apk")

    @Test fun hashMatchesPublishedSha256Encoding() {
        target().writeText("abc")
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", VerifiedDownload.sha256(target()))
    }

    @Test fun verifiedDownloadIsCommittedAndReportsProgress() {
        var progress = 0L
        VerifiedDownload.copy(bytes.inputStream(), target(), bytes.size.toLong(), digest, {}, { progress = it })
        assertArrayEquals(bytes, target().readBytes())
        assertEquals(bytes.size.toLong(), progress)
        assertFalse(File(temporary.root, "update.apk.part").exists())
    }

    @Test fun corruptOrTruncatedOrOversizedDownloadsAreNotInstallable() {
        for ((input, size, hash) in listOf(Triple(bytes, bytes.size.toLong(), "0".repeat(64)),
            Triple(bytes.copyOf(100), bytes.size.toLong(), digest), Triple(bytes, 100L, digest))) {
            assertThrows(IOException::class.java) {
                VerifiedDownload.copy(input.inputStream(), target(), size, hash, {}, {})
            }
            assertFalse(target().exists())
            assertFalse(File(temporary.root, "update.apk.part").exists())
        }
    }

    @Test fun cancellationRemovesPartialDownload() {
        var checks = 0
        assertThrows(CancellationException::class.java) {
            VerifiedDownload.copy(bytes.inputStream(), target(), bytes.size.toLong(), digest,
                { if (++checks == 2) throw CancellationException() }, {})
        }
        assertFalse(target().exists())
        assertFalse(File(temporary.root, "update.apk.part").exists())
    }
}
