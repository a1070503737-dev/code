package com.zhuangxiu.ledger

import com.zhuangxiu.ledger.update.UpdateCredentials
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class UpdateCredentialsTest {
    @Test fun sharedAccessWorksEvenIfOldDeviceStorageCannotBeRead() {
        val resolved = UpdateCredentials.resolve(storedToken = { throw IOException("Old device key is unavailable") },
            bundledToken = "test_shared_access")
        assertEquals("test_shared_access", resolved)
    }

    @Test fun sharedAccessSupersedesAnExpiredUserToken() {
        assertEquals("test_shared_access", UpdateCredentials.resolve({ "expired_user_access" }, "test_shared_access"))
    }

    @Test fun buildWithoutSharedAccessRetainsExistingManualConfiguration() {
        assertEquals("user_access", UpdateCredentials.resolve({ "user_access" }, ""))
        assertThrows(IOException::class.java) { UpdateCredentials.resolve({ throw IOException("Read failed") }, "") }
    }
}
