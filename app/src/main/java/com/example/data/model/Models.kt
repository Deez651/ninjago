package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class PostItem(
    @DocumentId val id: String = "",
    val title: String = "",
    val description: String = "",
    val category: String = "Файлы",
    val tags: List<String> = emptyList(),
    val fileName: String = "",
    val fileSizeBytes: Long = 0L,
    val mimeType: String = "application/octet-stream",
    val hasFile: Boolean = false,
    val requiresKey: Boolean = false,
    val downloadCount: Long = 0L,
    val authorLogin: String = "",
    val deleted: Boolean = false,
    val sessionProofId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class PostPayload(
    val postId: String = "",
    val fileUrl: String? = null,
    val embeddedBase64: String? = null,
    val chunkCount: Long = 0L,
    val sha256: String? = null,
    val requiresKey: Boolean = false,
    val sessionProofId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class AccessKeyItem(
    @DocumentId val id: String = "",
    val code: String = "",
    val label: String = "",
    val scope: String = "all",
    val maxActivations: Long = 1L,
    val usedCount: Long = 0L,
    val active: Boolean = true,
    val expiresAt: Timestamp? = null,
    val lastUsedAt: Timestamp? = null,
    val createdBy: String = "",
    val sessionProofId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun isExpired(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val exp = expiresAt?.toDate()?.time ?: return false
        return exp <= nowMillis
    }

    fun isExhausted(): Boolean = usedCount >= maxActivations

    fun isCurrentlyValid(nowMillis: Long = System.currentTimeMillis()): Boolean =
        active && !isExpired(nowMillis) && !isExhausted()

    fun isStillActiveForHolder(nowMillis: Long = System.currentTimeMillis()): Boolean =
        active && !isExpired(nowMillis)

    fun coversPost(postId: String): Boolean =
        scope == "all" || scope == postId
}

@Keep
@IgnoreExtraProperties
data class KeyActivationItem(
    @DocumentId val id: String = "",
    val keyCode: String = "",
    val deviceLabel: String = "",
    val deviceHash: String = "",
    @ServerTimestamp val activatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class AdminRosterItem(
    @DocumentId val id: String = "",
    val login: String = "",
    val role: String = "admin",
    val active: Boolean = true,
    val createdBy: String = "",
    val sessionProofId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

data class AdminSessionState(
    val login: String,
    val role: String,
    val vaultId: String,
    val clientHash: String,
    val currentSessionId: String,
    val lastActivityMillis: Long = System.currentTimeMillis(),
)

data class DownloadedRecord(
    val postId: String,
    val title: String,
    val fileName: String,
    val localPath: String,
    val contentUri: String?,
    val fileSizeBytes: Long,
    val mimeType: String,
    val downloadedAtMillis: Long,
)

data class DownloadProgressState(
    val isDownloading: Boolean = false,
    val progressPercent: Int = 0,
    val error: String? = null,
)

sealed interface ActiveKeyStatus {
    data object None : ActiveKeyStatus
    data class Checking(val code: String) : ActiveKeyStatus
    data class Valid(val key: AccessKeyItem) : ActiveKeyStatus
    data class RevokedOrInvalid(val code: String, val reason: String) : ActiveKeyStatus
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class AccessFilter(val label: String) {
    ALL("Все"),
    OPEN("Открытые"),
    KEY_ONLY("VIP / По ключу")
}

object PostCategories {
    val ALL = listOf("Все", "Скрипты", "Читы", "Конфиги", "Файлы", "Гайды")
    val PUBLISHABLE = listOf("Скрипты", "Читы", "Конфиги", "Файлы", "Гайды")
}
