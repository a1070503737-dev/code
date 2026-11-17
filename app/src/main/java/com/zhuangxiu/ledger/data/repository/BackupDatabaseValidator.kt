package com.zhuangxiu.ledger.data.repository

import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.io.IOException

/** 恢复前读取完整性和 Room 身份，避免仅凭 SQLite 文件头覆盖现有账本。 */
internal object BackupDatabaseValidator {
    fun validate(file: File, expectedVersion: Int, expectedIdentity: String) {
        try {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { backup ->
                backup.rawQuery("PRAGMA quick_check(1)", null).use {
                    if (!it.moveToFirst() || it.getString(0) != "ok") throw IOException("备份数据库已损坏")
                }
                if (backup.version != expectedVersion) throw IOException("备份版本与当前应用不兼容")
                backup.rawQuery("SELECT identity_hash FROM room_master_table WHERE id = 42", null).use {
                    if (!it.moveToFirst() || it.getString(0) != expectedIdentity) {
                        throw IOException("备份数据库结构与当前账本不匹配")
                    }
                }
                backup.rawQuery("PRAGMA foreign_key_check", null).use {
                    if (it.moveToFirst()) throw IOException("备份中有不完整的账单关联数据")
                }
            }
        } catch (error: IOException) {
            throw error
        } catch (error: Exception) {
            throw IOException("无法读取备份数据库，当前账本未被替换", error)
        }
    }
}
