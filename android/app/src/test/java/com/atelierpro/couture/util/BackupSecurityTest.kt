package com.atelierpro.couture.util

import com.atelierpro.couture.data.AtelierDatabase
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupSecurityTest {

    @Test
    fun safeArchiveEntriesAllowOnlyDatabaseAndPhotoImages() {
        assertTrue(BackupManager.isSafeArchiveEntryName(AtelierDatabase.DB_FILE_NAME))
        assertTrue(BackupManager.isSafeArchiveEntryName("photos/cam_123.webp"))
        assertTrue(BackupManager.isSafeArchiveEntryName("photos/logo.jpg"))

        assertFalse(BackupManager.isSafeArchiveEntryName("../evil.db"))
        assertFalse(BackupManager.isSafeArchiveEntryName("photos/../../etc/passwd"))
        assertFalse(BackupManager.isSafeArchiveEntryName("/absolute/path.jpg"))
        assertFalse(BackupManager.isSafeArchiveEntryName("photos/script.sh"))
        assertFalse(BackupManager.isSafeArchiveEntryName("photos/sub/nested.jpg"))
    }

    @Test
    fun sqliteHeaderValidatorAcceptsValidHeaderAndRejectsJunk() {
        val valid = "SQLite format 3\u0000extraBytes".toByteArray(Charsets.US_ASCII)
        val invalid = "PK\u0003\u0004notSqliteData".toByteArray(Charsets.US_ASCII)
        val tooShort = "SQLite".toByteArray(Charsets.US_ASCII)

        assertTrue(BackupManager.hasValidSqliteHeader(valid))
        assertFalse(BackupManager.hasValidSqliteHeader(invalid))
        assertFalse(BackupManager.hasValidSqliteHeader(tooShort))
    }
}
