package com.example.data.repository

import android.content.Context
import android.util.Base64
import com.example.R
import com.example.data.model.AccessKeyItem
import com.example.data.model.AdminRosterItem
import com.example.data.model.AdminSessionState
import com.example.data.model.KeyActivationItem
import com.example.data.model.PostItem
import com.example.data.model.PostPayload
import com.example.data.security.SecurityUtils
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Date

class HubRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    // --- PUBLIC USER OPERATIONS (NO LOGIN REQUIRED) ---

    fun observePosts(): Flow<List<PostItem>> {
        val path = "posts"
        return db.collection(path)
            .whereEqualTo("deleted", false)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    runCatching {
                        doc.toObject(
                            PostItem::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    }.getOrNull()
                }.sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                val fsErr = extractFirestoreException(error)
                if (fsErr == null && error is CancellationException) throw error
                handleFirestoreError(fsErr ?: error, OperationType.LIST, path)
                throw error
            }
    }

    fun observeAccessKey(keyCode: String): Flow<AccessKeyItem?> {
        val clean = keyCode.trim().uppercase()
        if (!SecurityUtils.isValidFirestoreId(clean) || clean.length < 4) {
            return flowOf(null)
        }
        val path = "access_keys/$clean"
        return db.collection("access_keys")
            .document(clean)
            .snapshots()
            .map { snapshot ->
                if (!snapshot.exists()) null
                else snapshot.toObject(
                    AccessKeyItem::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                val fsErr = extractFirestoreException(error)
                if (fsErr == null && error is CancellationException) throw error
                handleFirestoreError(fsErr ?: error, OperationType.GET, path)
                throw error
            }
    }

    suspend fun redeemAccessKey(
        rawCode: String,
        deviceLabel: String,
        deviceHash: String
    ): Result<AccessKeyItem> {
        val code = rawCode.trim().uppercase()
        val path = "access_keys/$code"
        return try {
            require(code.length in 4..40 && SecurityUtils.isValidFirestoreId(code)) {
                "Некорректный формат ключа. Допустимы латинские буквы, цифры и дефис."
            }
            val keyRef = db.collection("access_keys").document(code)
            val snapshot = keyRef.get().await()
            if (!snapshot.exists()) {
                return Result.failure(IllegalArgumentException("Ключ не найден. Проверьте правильность кода."))
            }
            val key = snapshot.toObject(
                AccessKeyItem::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return Result.failure(IllegalStateException("Ошибка чтения данных ключа."))

            if (!key.active) {
                return Result.failure(IllegalStateException("Ключ отключён администратором."))
            }
            if (key.isExpired()) {
                return Result.failure(IllegalStateException("Срок действия этого ключа истёк."))
            }
            if (key.isExhausted()) {
                return Result.failure(IllegalStateException("Достигнут лимит активаций (${key.usedCount}/${key.maxActivations})."))
            }

            val activationId = "act_${SecurityUtils.generateSecureHex(12)}"
            val actRef = keyRef.collection("activations").document(activationId)
            val batch = db.batch()
            batch.update(
                keyRef,
                mapOf(
                    "usedCount" to (key.usedCount + 1L),
                    "lastUsedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                actRef,
                mapOf(
                    "keyCode" to code,
                    "deviceLabel" to deviceLabel.take(80).ifBlank { "Android Device" },
                    "deviceHash" to deviceHash.take(64).ifBlank { "unknown_device" },
                    "activatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()

            val updatedSnap = keyRef.get().await()
            val updatedKey = updatedSnap.toObject(
                AccessKeyItem::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: key.copy(usedCount = key.usedCount + 1L)
            Result.success(updatedKey)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun fetchPostPayload(postId: String): Result<Pair<PostPayload, List<String>>> {
        val path = "post_payloads/$postId"
        return try {
            require(SecurityUtils.isValidFirestoreId(postId)) { "Некорректный ID публикации." }
            val payloadSnap = db.collection("post_payloads").document(postId).get().await()
            if (!payloadSnap.exists()) {
                return Result.failure(IllegalStateException("Файл публикации не найден."))
            }
            val payload = payloadSnap.toObject(
                PostPayload::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return Result.failure(IllegalStateException("Ошибка чтения файла публикации."))

            val chunks = if (payload.chunkCount > 0L) {
                val chunkDocs = db.collection("post_payloads")
                    .document(postId)
                    .collection("chunks")
                    .get()
                    .await()
                chunkDocs.documents
                    .sortedBy { (it.get("index") as? Number)?.toInt() ?: 0 }
                    .mapNotNull { it.getString("data") }
            } else {
                emptyList()
            }
            Result.success(payload to chunks)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    suspend fun incrementDownloadCount(postId: String): Result<Unit> {
        val path = "posts/$postId"
        return try {
            require(SecurityUtils.isValidFirestoreId(postId)) { "Некорректный ID публикации." }
            db.collection("posts").document(postId)
                .update(
                    mapOf(
                        "downloadCount" to FieldValue.increment(1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- ADMIN AUTHENTICATION & VAULT MANAGEMENT ---

    suspend fun loginAdmin(rawLogin: String, password: String): Result<AdminSessionState> {
        val login = rawLogin.trim().lowercase()
        val path = "admin_accounts/$login"
        return try {
            require(SecurityUtils.isValidLoginFormat(login)) {
                "Логин должен содержать 2–32 латинских символа, цифры, '_' или '-'."
            }
            require(password.isNotEmpty()) { "Введите пароль." }

            val accountRef = db.collection("admin_accounts").document(login)
            val snap = accountRef.get().await()

            val clientHash: String
            val vaultId: String
            val role: String

            if (!snap.exists()) {
                if (login == SecurityUtils.SUPER_ADMIN_LOGIN) {
                    val candidateClientHash =
                        SecurityUtils.sha256Hex("${SecurityUtils.SUPER_ADMIN_SALT}:$password")
                    val candidateVerifier = SecurityUtils.sha256Hex(candidateClientHash)
                    if (candidateVerifier != SecurityUtils.SUPER_ADMIN_VERIFIER_HASH) {
                        return Result.failure(IllegalArgumentException("Неверный логин или пароль."))
                    }
                    clientHash = candidateClientHash
                    vaultId = SecurityUtils.generateSecureHex(32)
                    role = "super_admin"
                    val encryptedVault = SecurityUtils.encryptVaultId(vaultId, clientHash)
                    val bootstrapSessionId = "sess_${SecurityUtils.generateSecureHex(16)}"

                    val batch = db.batch()
                    batch.set(
                        accountRef,
                        mapOf(
                            "login" to login,
                            "salt" to SecurityUtils.SUPER_ADMIN_SALT,
                            "verifierHash" to SecurityUtils.SUPER_ADMIN_VERIFIER_HASH,
                            "encryptedVaultId" to encryptedVault,
                            "role" to role,
                            "active" to true,
                            "createdBy" to login,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    batch.set(
                        db.collection("admin_vaults").document(vaultId)
                            .collection("admin_roster").document(login),
                        mapOf(
                            "login" to login,
                            "role" to role,
                            "active" to true,
                            "createdBy" to login,
                            "sessionProofId" to bootstrapSessionId,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    batch.commit().await()
                } else {
                    return Result.failure(IllegalArgumentException("Неверный логин или пароль."))
                }
            } else {
                val active = snap.getBoolean("active") ?: false
                if (!active) {
                    return Result.failure(IllegalStateException("Эта учётная запись администратора отключена."))
                }
                val salt = snap.getString("salt").orEmpty()
                val storedVerifier = snap.getString("verifierHash").orEmpty()
                val encryptedVault = snap.getString("encryptedVaultId").orEmpty()
                role = snap.getString("role") ?: "admin"

                val candidateClientHash = SecurityUtils.sha256Hex("$salt:$password")
                val candidateVerifier = SecurityUtils.sha256Hex(candidateClientHash)
                if (candidateVerifier != storedVerifier) {
                    return Result.failure(IllegalArgumentException("Неверный логин или пароль."))
                }
                clientHash = candidateClientHash
                vaultId = SecurityUtils.decryptVaultId(encryptedVault, clientHash)
            }

            val sessionId = createFreshSession(login, clientHash)
            Result.success(
                AdminSessionState(
                    login = login,
                    role = role,
                    vaultId = vaultId,
                    clientHash = clientHash,
                    currentSessionId = sessionId,
                    lastActivityMillis = System.currentTimeMillis()
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    private suspend fun createFreshSession(accountId: String, clientHash: String): String {
        val sessionId = "sess_${SecurityUtils.generateSecureHex(16)}"
        val expiresAt = Timestamp(Date(System.currentTimeMillis() + 12 * 3600_000L))
        db.collection("admin_sessions").document(sessionId)
            .set(
                mapOf(
                    "accountId" to accountId,
                    "authProof" to clientHash,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "expiresAt" to expiresAt
                )
            )
            .await()
        return sessionId
    }

    private suspend fun runWithRotatedSession(
        session: AdminSessionState,
        opPath: String,
        opType: OperationType,
        block: (batch: com.google.firebase.firestore.WriteBatch, proofSessionId: String) -> Unit
    ): Result<AdminSessionState> {
        return try {
            val activeSessionId = session.currentSessionId
            val nextSessionId = "sess_${SecurityUtils.generateSecureHex(16)}"
            val nextExpires = Timestamp(Date(System.currentTimeMillis() + 12 * 3600_000L))

            val batch = db.batch()
            batch.delete(db.collection("admin_sessions").document(activeSessionId))
            batch.set(
                db.collection("admin_sessions").document(nextSessionId),
                mapOf(
                    "accountId" to session.login,
                    "authProof" to session.clientHash,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "expiresAt" to nextExpires
                )
            )
            block(batch, activeSessionId)
            batch.commit().await()

            Result.success(
                session.copy(
                    currentSessionId = nextSessionId,
                    lastActivityMillis = System.currentTimeMillis()
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // If old session expired or was consumed by an interrupted write, try once with a freshly minted session
            val isPermissionErr = extractFirestoreException(e)?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
            if (isPermissionErr) {
                try {
                    val freshId = createFreshSession(session.login, session.clientHash)
                    val nextId = "sess_${SecurityUtils.generateSecureHex(16)}"
                    val nextExpires = Timestamp(Date(System.currentTimeMillis() + 12 * 3600_000L))
                    val retryBatch = db.batch()
                    retryBatch.delete(db.collection("admin_sessions").document(freshId))
                    retryBatch.set(
                        db.collection("admin_sessions").document(nextId),
                        mapOf(
                            "accountId" to session.login,
                            "authProof" to session.clientHash,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "expiresAt" to nextExpires
                        )
                    )
                    block(retryBatch, freshId)
                    retryBatch.commit().await()
                    return Result.success(
                        session.copy(
                            currentSessionId = nextId,
                            lastActivityMillis = System.currentTimeMillis()
                        )
                    )
                } catch (retryErr: CancellationException) {
                    throw retryErr
                } catch (retryErr: Exception) {
                    handleFirestoreError(retryErr, opType, opPath)
                    return Result.failure(retryErr)
                }
            }
            handleFirestoreError(e, opType, opPath)
            Result.failure(e)
        }
    }

    // --- ADMIN CONTENT PUBLISHING ---

    suspend fun publishPost(
        session: AdminSessionState,
        title: String,
        description: String,
        category: String,
        tags: List<String>,
        fileName: String,
        mimeType: String,
        fileUrl: String?,
        fileBytes: ByteArray?,
        requiresKey: Boolean
    ): Result<AdminSessionState> {
        val cleanTitle = title.trim()
        val cleanDesc = description.trim()
        require(cleanTitle.length in 1..140) { "Заголовок должен быть от 1 до 140 символов." }
        require(cleanDesc.length in 1..4000) { "Описание должно быть от 1 до 4000 символов." }

        val cleanUrl = fileUrl?.trim()?.takeIf { it.isNotEmpty() }
        if (cleanUrl != null) {
            require(SecurityUtils.isSafeHttpsUrl(cleanUrl)) {
                "Ссылка должна начинаться с https:// и указывать на публичный домен."
            }
        }
        if (fileBytes != null) {
            require(fileBytes.size <= 2 * 1024 * 1024) {
                "Максимальный размер встроенного файла — 2 МБ. Для больших файлов используйте HTTPS-ссылку."
            }
        }

        val postId = "post_${SecurityUtils.generateSecureHex(10)}"
        val cleanTags = tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(8)
        val hasFile = fileBytes != null || cleanUrl != null
        val sizeBytes = fileBytes?.size?.toLong() ?: 0L

        val base64All = fileBytes?.let { Base64.encodeToString(it, Base64.NO_WRAP) }
        val inlineBase64 = if (base64All != null && base64All.length <= 700_000) base64All else null
        val chunks = if (base64All != null && base64All.length > 700_000) {
            base64All.chunked(550_000)
        } else {
            emptyList()
        }
        val sha256 = fileBytes?.let { SecurityUtils.sha256BytesHex(it) }

        return runWithRotatedSession(session, "posts/$postId", OperationType.CREATE) { batch, proofId ->
            batch.set(
                db.collection("posts").document(postId),
                mapOf(
                    "title" to cleanTitle,
                    "description" to cleanDesc,
                    "category" to category.take(40).ifBlank { "Файлы" },
                    "tags" to cleanTags,
                    "fileName" to fileName.trim().take(180),
                    "fileSizeBytes" to sizeBytes,
                    "mimeType" to mimeType.trim().take(100).ifBlank { "application/octet-stream" },
                    "hasFile" to hasFile,
                    "requiresKey" to requiresKey,
                    "downloadCount" to 0L,
                    "authorLogin" to session.login,
                    "deleted" to false,
                    "sessionProofId" to proofId,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            val payloadMap = mapOf(
                "postId" to postId,
                "fileUrl" to cleanUrl,
                "embeddedBase64" to inlineBase64,
                "chunkCount" to chunks.size.toLong(),
                "sha256" to sha256,
                "requiresKey" to requiresKey,
                "sessionProofId" to proofId,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null } as Map<String, Any>

            val payloadRef = db.collection("post_payloads").document(postId)
            batch.set(payloadRef, payloadMap)

            chunks.forEachIndexed { index, chunkData ->
                batch.set(
                    payloadRef.collection("chunks").document("chunk_$index"),
                    mapOf(
                        "index" to index.toLong(),
                        "data" to chunkData,
                        "sessionProofId" to proofId,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }
        }
    }

    suspend fun deletePost(session: AdminSessionState, postId: String): Result<AdminSessionState> {
        return runWithRotatedSession(session, "posts/$postId", OperationType.UPDATE) { batch, proofId ->
            batch.update(
                db.collection("posts").document(postId),
                mapOf(
                    "deleted" to true,
                    "sessionProofId" to proofId,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }
    }

    // --- ADMIN ACCESS KEYS & REAL-TIME TRACKING ---

    suspend fun createAccessKey(
        session: AdminSessionState,
        customCode: String?,
        label: String,
        scope: String,
        maxActivations: Long,
        expiresAtMillis: Long?
    ): Result<Pair<AccessKeyItem, AdminSessionState>> {
        val code = customCode?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
            ?: SecurityUtils.generateAccessKeyCode()
        require(code.length in 4..40 && SecurityUtils.isValidFirestoreId(code)) {
            "Код ключа должен содержать 4–40 символов (латиница, цифры, '_' или '-')."
        }
        require(maxActivations in 1L..100000L) {
            "Количество активаций должно быть от 1 до 100 000."
        }

        val existing = runCatching { db.collection("access_keys").document(code).get().await() }.getOrNull()
        if (existing != null && existing.exists()) {
            return Result.failure(IllegalArgumentException("Ключ $code уже существует."))
        }

        val expiresTs = expiresAtMillis?.let { Timestamp(Date(it)) }
        val cleanLabel = label.trim().take(80)
        val cleanScope = scope.trim().take(128).ifBlank { "all" }

        val sessionRes = runWithRotatedSession(
            session,
            "access_keys/$code",
            OperationType.CREATE
        ) { batch, proofId ->
            val keyMap = mapOf(
                "code" to code,
                "label" to cleanLabel,
                "scope" to cleanScope,
                "maxActivations" to maxActivations,
                "usedCount" to 0L,
                "active" to true,
                "expiresAt" to expiresTs,
                "createdBy" to session.login,
                "sessionProofId" to proofId,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null } as Map<String, Any>

            batch.set(db.collection("access_keys").document(code), keyMap)
            batch.set(
                db.collection("admin_vaults").document(session.vaultId)
                    .collection("key_registry").document(code),
                mapOf(
                    "keyCode" to code,
                    "label" to cleanLabel,
                    "createdBy" to session.login,
                    "deleted" to false,
                    "sessionProofId" to proofId,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }

        return sessionRes.map { updatedSession ->
            AccessKeyItem(
                id = code,
                code = code,
                label = cleanLabel,
                scope = cleanScope,
                maxActivations = maxActivations,
                usedCount = 0L,
                active = true,
                expiresAt = expiresTs,
                createdBy = session.login
            ) to updatedSession
        }
    }

    suspend fun toggleKeyActive(
        session: AdminSessionState,
        keyCode: String,
        active: Boolean
    ): Result<AdminSessionState> {
        return runWithRotatedSession(session, "access_keys/$keyCode", OperationType.UPDATE) { batch, proofId ->
            batch.update(
                db.collection("access_keys").document(keyCode),
                mapOf(
                    "active" to active,
                    "sessionProofId" to proofId,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }
    }

    suspend fun updateKeyLimits(
        session: AdminSessionState,
        keyCode: String,
        maxActivations: Long,
        expiresAtMillis: Long?
    ): Result<AdminSessionState> {
        require(maxActivations in 1L..100000L) { "Лимит должен быть от 1 до 100 000." }
        return runWithRotatedSession(session, "access_keys/$keyCode", OperationType.UPDATE) { batch, proofId ->
            val updateMap = mutableMapOf<String, Any>(
                "maxActivations" to maxActivations,
                "sessionProofId" to proofId,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (expiresAtMillis != null) {
                updateMap["expiresAt"] = Timestamp(Date(expiresAtMillis))
            } else {
                updateMap["expiresAt"] = FieldValue.delete()
            }
            batch.update(db.collection("access_keys").document(keyCode), updateMap)
        }
    }

    suspend fun archiveKey(session: AdminSessionState, keyCode: String): Result<AdminSessionState> {
        return runWithRotatedSession(session, "access_keys/$keyCode", OperationType.UPDATE) { batch, proofId ->
            batch.update(
                db.collection("access_keys").document(keyCode),
                mapOf(
                    "active" to false,
                    "sessionProofId" to proofId,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                db.collection("admin_vaults").document(session.vaultId)
                    .collection("key_registry").document(keyCode),
                mapOf(
                    "deleted" to true,
                    "sessionProofId" to proofId,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeAllAdminKeys(vaultId: String): Flow<List<AccessKeyItem>> {
        val registryPath = "admin_vaults/$vaultId/key_registry"
        return db.collection("admin_vaults")
            .document(vaultId)
            .collection("key_registry")
            .whereEqualTo("deleted", false)
            .snapshots()
            .flatMapLatest { regSnapshot ->
                val keyCodes = regSnapshot.documents.map { it.id }.filter { SecurityUtils.isValidFirestoreId(it) }
                if (keyCodes.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    val keyFlows = keyCodes.map { code ->
                        db.collection("access_keys").document(code)
                            .snapshots()
                            .map { doc ->
                                doc.toObject(
                                    AccessKeyItem::class.java,
                                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                                )
                            }
                    }
                    combine(keyFlows) { arr ->
                        arr.filterNotNull()
                            .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                    }
                }
            }
            .catch { error ->
                val fsErr = extractFirestoreException(error)
                if (fsErr == null && error is CancellationException) throw error
                handleFirestoreError(fsErr ?: error, OperationType.LIST, registryPath)
                throw error
            }
    }

    fun observeKeyActivations(keyCode: String): Flow<List<KeyActivationItem>> {
        val path = "access_keys/$keyCode/activations"
        return db.collection("access_keys")
            .document(keyCode)
            .collection("activations")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    runCatching {
                        doc.toObject(
                            KeyActivationItem::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    }.getOrNull()
                }.sortedByDescending { it.activatedAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                val fsErr = extractFirestoreException(error)
                if (fsErr == null && error is CancellationException) throw error
                handleFirestoreError(fsErr ?: error, OperationType.LIST, path)
                throw error
            }
    }

    // --- ADMIN ACCOUNTS MANAGEMENT ---

    fun observeAdminRoster(vaultId: String): Flow<List<AdminRosterItem>> {
        val path = "admin_vaults/$vaultId/admin_roster"
        return db.collection("admin_vaults")
            .document(vaultId)
            .collection("admin_roster")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    runCatching {
                        doc.toObject(
                            AdminRosterItem::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    }.getOrNull()
                }.sortedBy { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                val fsErr = extractFirestoreException(error)
                if (fsErr == null && error is CancellationException) throw error
                handleFirestoreError(fsErr ?: error, OperationType.LIST, path)
                throw error
            }
    }

    suspend fun addAdminAccount(
        session: AdminSessionState,
        rawNewLogin: String,
        newPassword: String
    ): Result<AdminSessionState> {
        val newLogin = rawNewLogin.trim().lowercase()
        require(SecurityUtils.isValidLoginFormat(newLogin)) {
            "Логин должен содержать 2–32 символа (a-z, 0-9, '_', '-')."
        }
        require(newPassword.length >= 6) {
            "Пароль нового админа должен содержать минимум 6 символов."
        }
        val existingDoc = runCatching {
            db.collection("admin_accounts").document(newLogin).get().await()
        }.getOrNull()
        if (existingDoc != null && existingDoc.exists()) {
            return Result.failure(IllegalArgumentException("Администратор '$newLogin' уже существует."))
        }

        val newSalt = SecurityUtils.generateSecureHex(12)
        val newClientHash = SecurityUtils.sha256Hex("$newSalt:$newPassword")
        val newVerifierHash = SecurityUtils.sha256Hex(newClientHash)
        val newEncryptedVault = SecurityUtils.encryptVaultId(session.vaultId, newClientHash)

        return runWithRotatedSession(
            session,
            "admin_accounts/$newLogin",
            OperationType.CREATE
        ) { batch, proofId ->
            batch.set(
                db.collection("admin_accounts").document(newLogin),
                mapOf(
                    "login" to newLogin,
                    "salt" to newSalt,
                    "verifierHash" to newVerifierHash,
                    "encryptedVaultId" to newEncryptedVault,
                    "role" to "admin",
                    "active" to true,
                    "createdBy" to session.login,
                    "sessionProofId" to proofId,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                db.collection("admin_vaults").document(session.vaultId)
                    .collection("admin_roster").document(newLogin),
                mapOf(
                    "login" to newLogin,
                    "role" to "admin",
                    "active" to true,
                    "createdBy" to session.login,
                    "sessionProofId" to proofId,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }
    }

    suspend fun toggleAdminActive(
        session: AdminSessionState,
        targetLogin: String,
        active: Boolean
    ): Result<AdminSessionState> {
        require(targetLogin != SecurityUtils.SUPER_ADMIN_LOGIN) {
            "Главного администратора нельзя отключить."
        }
        return runWithRotatedSession(
            session,
            "admin_accounts/$targetLogin",
            OperationType.UPDATE
        ) { batch, proofId ->
            batch.update(
                db.collection("admin_accounts").document(targetLogin),
                mapOf(
                    "active" to active,
                    "sessionProofId" to proofId,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                db.collection("admin_vaults").document(session.vaultId)
                    .collection("admin_roster").document(targetLogin),
                mapOf(
                    "active" to active,
                    "sessionProofId" to proofId,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }
    }

    private fun extractFirestoreException(error: Throwable): FirebaseFirestoreException? =
        generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
}
