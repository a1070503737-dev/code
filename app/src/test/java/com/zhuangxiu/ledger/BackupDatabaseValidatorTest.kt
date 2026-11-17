package com.zhuangxiu.ledger

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import com.zhuangxiu.ledger.data.repository.BackupDatabaseValidator
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class BackupDatabaseValidatorTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun rejectsFileWithOnlySqliteHeader() {
        val file = temp.newFile("corrupt.db")
        file.writeBytes("SQLite format 3\u0000".toByteArray() + ByteArray(256))
        assertThrows(IOException::class.java) { BackupDatabaseValidator.validate(file, 1, "expected") }
    }

    @Test fun acceptsMatchingDatabaseButRejectsDifferentSchemaOrVersion() {
        val file = temp.newFile("backup.db")
        SQLiteDatabase.openOrCreateDatabase(file, null).use {
            it.version = 1
            it.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            it.execSQL("INSERT INTO room_master_table VALUES (42, 'expected')")
        }
        BackupDatabaseValidator.validate(file, 1, "expected")
        assertThrows(IOException::class.java) { BackupDatabaseValidator.validate(file, 1, "other") }
        assertThrows(IOException::class.java) { BackupDatabaseValidator.validate(file, 2, "expected") }
    }
}
