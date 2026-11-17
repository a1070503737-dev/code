package com.zhuangxiu.ledger.data.repository

import android.content.Context
import android.net.Uri
import com.zhuangxiu.ledger.data.db.AppDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 备份与恢复：把数据库和凭证图片打包成一个 zip，或从 zip 还原。
 */
@Singleton
class BackupManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val imageStore: ImageStore,
) {
    /** 备份文件的默认文件名，例如 装修记账备份_20250308_203015.zip */
    fun defaultFileName(): String =
        "装修记账备份_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".zip"

    /** 生成备份文件，放在缓存目录里（用于系统分享），返回该文件 */
    suspend fun createBackupFile(): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "backups").apply {
            mkdirs()
            // 清掉以前生成的临时备份，避免占用空间
            listFiles()?.forEach { it.delete() }
        }
        val file = File(dir, defaultFileName())
        checkpoint()
        file.outputStream().use { BackupZip.zip(databaseFile(), imageStore.dir, it) }
        file
    }

    /** 把备份直接写到用户通过系统文件选择器指定的位置 */
    suspend fun exportTo(uri: Uri) = withContext(Dispatchers.IO) {
        checkpoint()
        val output = context.contentResolver.openOutputStream(uri) ?: throw IOException("无法写入所选位置")
        output.use { BackupZip.zip(databaseFile(), imageStore.dir, it) }
    }

    /**
     * 从备份文件恢复。成功后数据库连接已关闭，**调用方必须重启应用**。
     * @throws IOException 文件不是有效的备份时抛出，此时现有数据不受影响
     */
    suspend fun restoreFrom(uri: Uri) = withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, "restore_tmp")
        tempDir.deleteRecursively()
        try {
            // 1. 先解压到临时目录并校验，确认有效后才动现有数据
            val input = context.contentResolver.openInputStream(uri) ?: throw IOException("无法读取所选文件")
            input.use { BackupZip.unzip(it, tempDir) }
            if (!BackupZip.isValidBackup(tempDir)) throw IOException("这不是有效的装修记账备份文件")

            val current = db.openHelper.readableDatabase
            val identity = current.query("SELECT identity_hash FROM room_master_table WHERE id = 42").use {
                if (!it.moveToFirst()) throw IOException("无法验证当前账本结构")
                it.getString(0)
            }
            BackupDatabaseValidator.validate(File(tempDir, BackupZip.DB_ENTRY), current.version, identity)

            // 2. 关闭数据库，替换数据库文件（连同 WAL 附属文件一起删掉）
            db.close()
            val dbFile = databaseFile()
            File(dbFile.path + "-wal").delete()
            File(dbFile.path + "-shm").delete()
            File(tempDir, BackupZip.DB_ENTRY).copyTo(dbFile, overwrite = true)

            // 3. 替换凭证图片
            imageStore.clearAll()
            File(tempDir, BackupZip.RECEIPTS_DIR).listFiles()?.filter { it.isFile }?.forEach { image ->
                image.copyTo(imageStore.file(image.name), overwrite = true)
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun databaseFile(): File = context.getDatabasePath(AppDatabase.DB_NAME)

    /** 把 WAL 日志里的内容合并进主数据库文件，保证备份的是完整数据 */
    private fun checkpoint() {
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
    }
}
