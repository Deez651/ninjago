package com.example.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.DownloadedRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ninjago_hub_prefs")

class AppPreferences(private val context: Context) {

    private object Keys {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val LAST_SEEN_POST_MILLIS = longPreferencesKey("last_seen_post_millis")
        val ACTIVE_KEY_CODE = stringPreferencesKey("active_key_code")
        val FAILED_ADMIN_ATTEMPTS = intPreferencesKey("failed_admin_attempts")
        val ADMIN_LOCKOUT_UNTIL_MILLIS = longPreferencesKey("admin_lockout_until_millis")
        val DOWNLOADED_RECORDS_JSON = stringPreferencesKey("downloaded_records_json")
    }

    val onboardingCompleted: Flow<Boolean> = context.dataStore.data.map {
        it[Keys.ONBOARDING_COMPLETED] ?: false
    }

    val notificationsEnabled: Flow<Boolean> = context.dataStore.data.map {
        it[Keys.NOTIFICATIONS_ENABLED] ?: true
    }

    val lastSeenPostMillis: Flow<Long> = context.dataStore.data.map {
        it[Keys.LAST_SEEN_POST_MILLIS] ?: 0L
    }

    val activeKeyCode: Flow<String?> = context.dataStore.data.map {
        it[Keys.ACTIVE_KEY_CODE]
    }

    val failedAdminAttempts: Flow<Int> = context.dataStore.data.map {
        it[Keys.FAILED_ADMIN_ATTEMPTS] ?: 0
    }

    val adminLockoutUntilMillis: Flow<Long> = context.dataStore.data.map {
        it[Keys.ADMIN_LOCKOUT_UNTIL_MILLIS] ?: 0L
    }

    val downloadedRecords: Flow<List<DownloadedRecord>> = context.dataStore.data.map { prefs ->
        parseDownloads(prefs[Keys.DOWNLOADED_RECORDS_JSON].orEmpty())
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setLastSeenPostMillis(millis: Long) {
        context.dataStore.edit { it[Keys.LAST_SEEN_POST_MILLIS] = millis }
    }

    suspend fun setActiveKeyCode(code: String?) {
        context.dataStore.edit {
            if (code.isNullOrBlank()) it.remove(Keys.ACTIVE_KEY_CODE)
            else it[Keys.ACTIVE_KEY_CODE] = code
        }
    }

    suspend fun recordFailedAdminAttempt(): Pair<Int, Long> {
        var newCount = 0
        var lockoutUntil = 0L
        context.dataStore.edit { prefs ->
            newCount = (prefs[Keys.FAILED_ADMIN_ATTEMPTS] ?: 0) + 1
            prefs[Keys.FAILED_ADMIN_ATTEMPTS] = newCount
            if (newCount >= 5) {
                val extraMultiplier = (newCount - 4).coerceAtMost(6)
                lockoutUntil = System.currentTimeMillis() + (extraMultiplier * 60_000L)
                prefs[Keys.ADMIN_LOCKOUT_UNTIL_MILLIS] = lockoutUntil
            } else {
                lockoutUntil = prefs[Keys.ADMIN_LOCKOUT_UNTIL_MILLIS] ?: 0L
            }
        }
        return newCount to lockoutUntil
    }

    suspend fun resetAdminLockout() {
        context.dataStore.edit { prefs ->
            prefs[Keys.FAILED_ADMIN_ATTEMPTS] = 0
            prefs[Keys.ADMIN_LOCKOUT_UNTIL_MILLIS] = 0L
        }
    }

    suspend fun saveDownloadRecord(record: DownloadedRecord) {
        context.dataStore.edit { prefs ->
            val current = parseDownloads(prefs[Keys.DOWNLOADED_RECORDS_JSON].orEmpty())
                .filterNot { it.postId == record.postId && it.fileName == record.fileName }
            val updated = (listOf(record) + current).take(50)
            val arr = JSONArray()
            updated.forEach { item ->
                arr.put(
                    JSONObject().apply {
                        put("postId", item.postId)
                        put("title", item.title)
                        put("fileName", item.fileName)
                        put("localPath", item.localPath)
                        put("contentUri", item.contentUri ?: "")
                        put("fileSizeBytes", item.fileSizeBytes)
                        put("mimeType", item.mimeType)
                        put("downloadedAtMillis", item.downloadedAtMillis)
                    }
                )
            }
            prefs[Keys.DOWNLOADED_RECORDS_JSON] = arr.toString()
        }
    }

    private fun parseDownloads(raw: String): List<DownloadedRecord> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    add(
                        DownloadedRecord(
                            postId = obj.optString("postId"),
                            title = obj.optString("title"),
                            fileName = obj.optString("fileName"),
                            localPath = obj.optString("localPath"),
                            contentUri = obj.optString("contentUri").takeIf { it.isNotBlank() },
                            fileSizeBytes = obj.optLong("fileSizeBytes"),
                            mimeType = obj.optString("mimeType", "application/octet-stream"),
                            downloadedAtMillis = obj.optLong("downloadedAtMillis"),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
}
