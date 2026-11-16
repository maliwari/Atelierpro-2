package com.atelierpro.couture.util

import android.content.Context
import android.net.Uri
import com.atelierpro.couture.data.AtelierDatabase
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Exports and imports the local Room SQLite database + photos directory as a portable .zip archive.
 * Phase 3 Hardening:
 * - Flushes SQLite WAL journal before reading/overwriting files.
 * - Guards against Zip Slip path traversal and unsupported file extensions.
 * - Stages the imported SQLite file and validates its 16-byte SQLite header before replacing the live DB.
 */
object BackupManager {

    private val SQLITE_MAGIC = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
    private val ALLOWED_PHOTO_EXT = setOf("webp", "jpg", "jpeg", "png")

    fun isSafeArchiveEntryName(entryName: String): Boolean {
        if (entryName.isBlank() || entryName.startsWith("/") || entryName.startsWith("\\")) return false
        val normalized = entryName.replace('\\', '/')
        if (normalized.split('/').any { it == ".." || it.isBlank() }) return false
        if (normalized == AtelierDatabase.DB_FILE_NAME) return true
        if (normalized.startsWith("photos/")) {
            val fileName = normalized.removePrefix("photos/")
            if (fileName.contains('/')) return false
            val ext = fileName.substringAfterLast('.', "").lowercase()
            return ext in ALLOWED_PHOTO_EXT
        }
        return false
    }

    fun hasValidSqliteHeader(headerBytes: ByteArray): Boolean {
        if (headerBytes.size < SQLITE_MAGIC.size) return false
        for (i in SQLITE_MAGIC.indices) {
            if (headerBytes[i] != SQLITE_MAGIC[i]) return false
        }
        return true
    }

    fun exportBackupZip(context: Context, destUri: Uri): Boolean {
        return runCatching {
            AtelierDatabase.checkpointAndClose()

            val dbFile = context.getDatabasePath(AtelierDatabase.DB_FILE_NAME)
            val photosDir = File(context.filesDir, "photos")

            context.contentResolver.openOutputStream(destUri)?.use { outStream ->
                ZipOutputStream(outStream).use { zos ->
                    if (dbFile.exists()) {
                        zos.putNextEntry(ZipEntry(AtelierDatabase.DB_FILE_NAME))
                        dbFile.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                    if (photosDir.exists()) {
                        photosDir.listFiles()?.forEach { photo ->
                            if (photo.isFile && isSafeArchiveEntryName("photos/${photo.name}")) {
                                zos.putNextEntry(ZipEntry("photos/${photo.name}"))
                                photo.inputStream().use { it.copyTo(zos) }
                                zos.closeEntry()
                            }
                        }
                    }
                }
                true
            } ?: false
        }.getOrDefault(false)
    }

    fun importBackupZip(context: Context, sourceUri: Uri): Boolean {
        val stagingDb = File(context.cacheDir, "restore_staging.db")
        val stagingPhotosDir = File(context.cacheDir, "restore_photos_staging").apply {
            if (exists()) deleteRecursively()
            mkdirs()
        }

        return try {
            var foundDb = false

            context.contentResolver.openInputStream(sourceUri)?.use { inStream ->
                ZipInputStream(inStream).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        if (!entry.isDirectory && isSafeArchiveEntryName(name)) {
                            if (name == AtelierDatabase.DB_FILE_NAME) {
                                FileOutputStream(stagingDb).use { fos -> zis.copyTo(fos) }
                                foundDb = true
                            } else if (name.startsWith("photos/")) {
                                val safeName = File(name).name
                                FileOutputStream(File(stagingPhotosDir, safeName)).use { fos -> zis.copyTo(fos) }
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            } ?: return false

            if (!foundDb || !stagingDb.exists() || stagingDb.length() < SQLITE_MAGIC.size) {
                return false
            }

            val header = ByteArray(SQLITE_MAGIC.size)
            stagingDb.inputStream().use { it.read(header) }
            if (!hasValidSqliteHeader(header)) {
                return false
            }

            // Validated: checkpoint/close live DB and perform replacement
            AtelierDatabase.checkpointAndClose()

            val dbFile = context.getDatabasePath(AtelierDatabase.DB_FILE_NAME)
            dbFile.parentFile?.mkdirs()
            File("${dbFile.absolutePath}-wal").delete()
            File("${dbFile.absolutePath}-shm").delete()
            stagingDb.copyTo(dbFile, overwrite = true)

            val targetPhotosDir = File(context.filesDir, "photos").apply { if (!exists()) mkdirs() }
            stagingPhotosDir.listFiles()?.forEach { photo ->
                if (photo.isFile) {
                    photo.copyTo(File(targetPhotosDir, photo.name), overwrite = true)
                }
            }
            true
        } catch (_: Exception) {
            false
        } finally {
            stagingDb.delete()
            stagingPhotosDir.deleteRecursively()
        }
    }
}
