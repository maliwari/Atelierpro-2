package com.atelierpro.couture.sync

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

interface SyncPreferencesStore {
    var authToken: String?
    var lastSyncTimestamp: String
    var apiBaseUrl: String
    var connectedEmail: String?
    val deviceUuid: String
    fun isAuthenticated(): Boolean
    fun clearSession()
}

/**
 * Stockage sécurisé (EncryptedSharedPreferences) pour conserver :
 * - auth_token (Bearer Token JWT reçu au login)
 * - last_sync_timestamp (Date UTC MySQL de la dernière synchronisation réussie)
 * - api_base_url (URL du backend PHP/MySQL AtelierPro)
 */
class SyncPreferences(context: Context) : SyncPreferencesStore {

    private val prefs: SharedPreferences = runCatching {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context.applicationContext,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }.getOrElse {
        // Fallback sécurisé privé si le Keystore matériel est indisponible sur un appareil ancien
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override var authToken: String?
        get() = prefs.getString(KEY_AUTH_TOKEN, null)
        set(value) {
            prefs.edit().putString(KEY_AUTH_TOKEN, value).apply()
        }

    override var lastSyncTimestamp: String
        get() = prefs.getString(KEY_LAST_SYNC, DEFAULT_LAST_SYNC) ?: DEFAULT_LAST_SYNC
        set(value) {
            prefs.edit().putString(KEY_LAST_SYNC, value).apply()
        }

    override var apiBaseUrl: String
        get() = prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
        set(value) {
            prefs.edit().putString(KEY_BASE_URL, value.trim().trimEnd('/')).apply()
        }

    override var connectedEmail: String?
        get() = prefs.getString(KEY_EMAIL, null)
        set(value) {
            prefs.edit().putString(KEY_EMAIL, value).apply()
        }

    /** Identifiant unique persistant de cet appareil Android pour le quota multi-appareils */
    override val deviceUuid: String
        get() {
            val existing = prefs.getString(KEY_DEVICE_UUID, null)
            if (!existing.isNullOrBlank()) return existing
            val generated = java.util.UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_UUID, generated).apply()
            return generated
        }

    override fun isAuthenticated(): Boolean = !authToken.isNullOrBlank()

    override fun clearSession() {
        prefs.edit()
            .remove(KEY_AUTH_TOKEN)
            .remove(KEY_EMAIL)
            .remove(KEY_LAST_SYNC)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "atelierpro_sync_secure_prefs"
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_LAST_SYNC = "last_sync_timestamp"
        private const val KEY_BASE_URL = "api_base_url"
        private const val KEY_EMAIL = "atelier_email"
        private const val KEY_DEVICE_UUID = "device_uuid"
        const val DEFAULT_LAST_SYNC = "1970-01-01 00:00:00"
        const val DEFAULT_BASE_URL = "https://api-atelierpro.crfdmali.org"
    }
}
