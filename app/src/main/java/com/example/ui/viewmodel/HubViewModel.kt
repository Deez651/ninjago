package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.download.FileDownloadHelper
import com.example.data.model.AccessFilter
import com.example.data.model.AccessKeyItem
import com.example.data.model.ActiveKeyStatus
import com.example.data.model.AdminRosterItem
import com.example.data.model.AdminSessionState
import com.example.data.model.DownloadProgressState
import com.example.data.model.DownloadedRecord
import com.example.data.model.KeyActivationItem
import com.example.data.model.PostItem
import com.example.data.model.UiState
import com.example.data.notifications.NinjagoNotificationHelper
import com.example.data.prefs.AppPreferences
import com.example.data.repository.HubRepository
import com.example.data.security.SecurityUtils
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class HubViewModel(
    private val repository: HubRepository,
    private val prefs: AppPreferences,
    private val appContext: Context,
) : ViewModel() {

    val onboardingCompleted: StateFlow<Boolean?> = prefs.onboardingCompleted
        .map<Boolean, Boolean?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val notificationsEnabled: StateFlow<Boolean> = prefs.notificationsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), true)

    val downloadedRecords: StateFlow<List<DownloadedRecord>> = prefs.downloadedRecords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val postsState: StateFlow<UiState<List<PostItem>>> = repository.observePosts()
        .map<List<PostItem>, UiState<List<PostItem>>> { UiState.Success(it) }
        .catch { error ->
            val fsErr = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsErr == null && error is CancellationException) throw error
            val root = fsErr ?: error
            emit(UiState.Error(root.message ?: "Не удалось загрузить публикации"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow("Все")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _accessFilter = MutableStateFlow(AccessFilter.ALL)
    val accessFilter: StateFlow<AccessFilter> = _accessFilter.asStateFlow()

    val filteredPostsState: StateFlow<UiState<List<PostItem>>> = combine(
        postsState,
        _searchQuery,
        _selectedCategory,
        _accessFilter
    ) { state, query, category, filter ->
        when (state) {
            is UiState.Loading -> UiState.Loading
            is UiState.Error -> state
            is UiState.Success -> {
                val q = query.trim().lowercase()
                val filtered = state.data.filter { post ->
                    val matchesCategory = category == "Все" || post.category == category
                    val matchesAccess = when (filter) {
                        AccessFilter.ALL -> true
                        AccessFilter.OPEN -> !post.requiresKey
                        AccessFilter.KEY_ONLY -> post.requiresKey
                    }
                    val matchesQuery = q.isEmpty() ||
                        post.title.lowercase().contains(q) ||
                        post.description.lowercase().contains(q) ||
                        post.fileName.lowercase().contains(q) ||
                        post.category.lowercase().contains(q) ||
                        post.tags.any { it.lowercase().contains(q) }
                    matchesCategory && matchesAccess && matchesQuery
                }
                UiState.Success(filtered)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val activeKeyStatus: StateFlow<ActiveKeyStatus> = prefs.activeKeyCode
        .flatMapLatest { savedCode ->
            if (savedCode.isNullOrBlank()) {
                flowOf(ActiveKeyStatus.None)
            } else {
                repository.observeAccessKey(savedCode)
                    .map { key ->
                        val now = System.currentTimeMillis()
                        when {
                            key == null -> ActiveKeyStatus.RevokedOrInvalid(savedCode, "Ключ удалён или не найден")
                            !key.active -> ActiveKeyStatus.RevokedOrInvalid(savedCode, "Ключ отключён админом")
                            key.isExpired(now) -> ActiveKeyStatus.RevokedOrInvalid(savedCode, "Срок действия ключа истёк")
                            else -> ActiveKeyStatus.Valid(key)
                        }
                    }
                    .catch { error ->
                        val fsErr = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (fsErr == null && error is CancellationException) throw error
                        emit(ActiveKeyStatus.RevokedOrInvalid(savedCode, fsErr?.message ?: "Ошибка проверки ключа"))
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), ActiveKeyStatus.None)

    private val _downloadProgress = MutableStateFlow<Map<String, DownloadProgressState>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, DownloadProgressState>> = _downloadProgress.asStateFlow()

    private val _unreadNotifications = MutableStateFlow<List<PostItem>>(emptyList())
    val unreadNotifications: StateFlow<List<PostItem>> = _unreadNotifications.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    // --- ADMIN STATE ---

    private val _adminSession = MutableStateFlow<AdminSessionState?>(null)
    val adminSession: StateFlow<AdminSessionState?> = _adminSession.asStateFlow()

    private val _isAdminBusy = MutableStateFlow(false)
    val isAdminBusy: StateFlow<Boolean> = _isAdminBusy.asStateFlow()

    private val _adminError = MutableStateFlow<String?>(null)
    val adminError: StateFlow<String?> = _adminError.asStateFlow()

    private val _lockoutRemainingSeconds = MutableStateFlow(0L)
    val lockoutRemainingSeconds: StateFlow<Long> = _lockoutRemainingSeconds.asStateFlow()

    val adminKeysState: StateFlow<List<AccessKeyItem>> = _adminSession
        .flatMapLatest { session ->
            if (session == null) flowOf(emptyList())
            else repository.observeAllAdminKeys(session.vaultId)
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    emit(emptyList())
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val adminRosterState: StateFlow<List<AdminRosterItem>> = _adminSession
        .flatMapLatest { session ->
            if (session == null) flowOf(emptyList())
            else repository.observeAdminRoster(session.vaultId)
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    emit(emptyList())
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _inspectedKeyCode = MutableStateFlow<String?>(null)
    val inspectedKeyCode: StateFlow<String?> = _inspectedKeyCode.asStateFlow()

    val inspectedKeyActivations: StateFlow<List<KeyActivationItem>> = _inspectedKeyCode
        .flatMapLatest { code ->
            if (code.isNullOrBlank()) flowOf(emptyList())
            else repository.observeKeyActivations(code)
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    emit(emptyList())
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _isRedeemingKey = MutableStateFlow(false)
    val isRedeemingKey: StateFlow<Boolean> = _isRedeemingKey.asStateFlow()

    private val _keyFeedback = MutableStateFlow<String?>(null)
    val keyFeedback: StateFlow<String?> = _keyFeedback.asStateFlow()

    init {
        startNewPostNotificationWatcher()
        startLockoutTicker()
        startSessionIdleWatcher()
    }

    private fun startNewPostNotificationWatcher() {
        viewModelScope.launch {
            var initialized = false
            postsState.collect { state ->
                if (state is UiState.Success) {
                    val posts = state.data
                    val savedLastSeen = prefs.lastSeenPostMillis.first()
                    if (!initialized) {
                        initialized = true
                        if (savedLastSeen == 0L) {
                            val newest = posts.maxOfOrNull { it.createdAt?.toDate()?.time ?: 0L }
                                ?: System.currentTimeMillis()
                            prefs.setLastSeenPostMillis(newest.coerceAtLeast(System.currentTimeMillis()))
                        } else {
                            val newPosts = posts.filter {
                                (it.createdAt?.toDate()?.time ?: 0L) > savedLastSeen
                            }
                            if (newPosts.isNotEmpty()) {
                                _unreadNotifications.value = newPosts
                                if (notificationsEnabled.value) {
                                    newPosts.take(2).forEach {
                                        NinjagoNotificationHelper.showNewPostNotification(appContext, it)
                                    }
                                }
                            }
                        }
                    } else {
                        val currentMarker = prefs.lastSeenPostMillis.first()
                        val newPosts = posts.filter {
                            (it.createdAt?.toDate()?.time ?: 0L) > currentMarker
                        }
                        if (newPosts.isNotEmpty()) {
                            val existingIds = _unreadNotifications.value.map { it.id }.toSet()
                            val freshOnly = newPosts.filter { it.id !in existingIds }
                            if (freshOnly.isNotEmpty()) {
                                _unreadNotifications.value = freshOnly + _unreadNotifications.value
                                if (notificationsEnabled.value) {
                                    freshOnly.take(2).forEach {
                                        NinjagoNotificationHelper.showNewPostNotification(appContext, it)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun startLockoutTicker() {
        viewModelScope.launch {
            while (true) {
                val until = prefs.adminLockoutUntilMillis.first()
                val remaining = ((until - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)
                _lockoutRemainingSeconds.value = remaining
                delay(1000L)
            }
        }
    }

    private fun startSessionIdleWatcher() {
        viewModelScope.launch {
            while (true) {
                delay(30_000L)
                val session = _adminSession.value
                if (session != null && System.currentTimeMillis() - session.lastActivityMillis > 30 * 60_000L) {
                    _adminSession.value = null
                    _snackbarMessage.value = "Сессия администратора завершена из-за неактивности."
                }
            }
        }
    }

    // --- USER ACTIONS ---

    fun completeOnboarding() {
        viewModelScope.launch {
            prefs.setOnboardingCompleted(true)
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setNotificationsEnabled(enabled)
        }
    }

    fun markNotificationsRead() {
        viewModelScope.launch {
            val newest = (postsState.value as? UiState.Success)?.data
                ?.maxOfOrNull { it.createdAt?.toDate()?.time ?: 0L }
                ?: System.currentTimeMillis()
            prefs.setLastSeenPostMillis(maxOf(newest, System.currentTimeMillis()))
            _unreadNotifications.value = emptyList()
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectCategory(category: String) {
        _selectedCategory.value = category
    }

    fun selectAccessFilter(filter: AccessFilter) {
        _accessFilter.value = filter
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun showMessage(msg: String) {
        _snackbarMessage.value = msg
    }

    fun clearKeyFeedback() {
        _keyFeedback.value = null
    }

    fun canAccessPost(post: PostItem): Boolean {
        if (!post.requiresKey) return true
        val status = activeKeyStatus.value
        return status is ActiveKeyStatus.Valid &&
            status.key.isStillActiveForHolder() &&
            status.key.coversPost(post.id)
    }

    fun redeemKey(rawCode: String) {
        val code = rawCode.trim().uppercase()
        if (code.length < 4) {
            _keyFeedback.value = "Введите корректный код ключа (минимум 4 символа)."
            return
        }
        viewModelScope.launch {
            _isRedeemingKey.value = true
            _keyFeedback.value = null
            val deviceLabel = SecurityUtils.getDeviceLabel()
            val deviceHash = SecurityUtils.getDeviceHash(appContext)
            repository.redeemAccessKey(code, deviceLabel, deviceHash)
                .onSuccess { key ->
                    prefs.setActiveKeyCode(key.code)
                    _keyFeedback.value = "Ключ ${key.code} успешно активирован!"
                    _snackbarMessage.value = "Доступ открыт по ключу ${key.code}"
                }
                .onFailure { err ->
                    _keyFeedback.value = err.message ?: "Не удалось активировать ключ."
                }
            _isRedeemingKey.value = false
        }
    }

    fun clearSavedKey() {
        viewModelScope.launch {
            prefs.setActiveKeyCode(null)
            _keyFeedback.value = "Ключ отключён на этом устройстве."
        }
    }

    fun downloadPostFile(post: PostItem) {
        if (!canAccessPost(post)) {
            _snackbarMessage.value = "Требуется действующий ключ доступа для скачивания."
            return
        }
        viewModelScope.launch {
            _downloadProgress.value = _downloadProgress.value + (
                post.id to DownloadProgressState(isDownloading = true, progressPercent = 5)
            )
            val payloadResult = repository.fetchPostPayload(post.id)
            if (payloadResult.isFailure) {
                val msg = payloadResult.exceptionOrNull()?.message ?: "Не удалось получить файл"
                _downloadProgress.value = _downloadProgress.value + (
                    post.id to DownloadProgressState(isDownloading = false, error = msg)
                )
                _snackbarMessage.value = msg
                return@launch
            }

            val (payload, chunks) = payloadResult.getOrThrow()
            val dlResult = FileDownloadHelper.downloadPostFile(
                context = appContext,
                post = post,
                payload = payload,
                chunks = chunks,
                onProgress = { pct ->
                    _downloadProgress.value = _downloadProgress.value + (
                        post.id to DownloadProgressState(isDownloading = true, progressPercent = pct)
                    )
                }
            )
            dlResult
                .onSuccess { record ->
                    prefs.saveDownloadRecord(record)
                    repository.incrementDownloadCount(post.id)
                    _downloadProgress.value = _downloadProgress.value + (
                        post.id to DownloadProgressState(isDownloading = false, progressPercent = 100)
                    )
                    _snackbarMessage.value = "Файл сохранён: ${record.fileName}"
                }
                .onFailure { err ->
                    val msg = err.message ?: "Ошибка при скачивании"
                    _downloadProgress.value = _downloadProgress.value + (
                        post.id to DownloadProgressState(isDownloading = false, error = msg)
                    )
                    _snackbarMessage.value = msg
                }
        }
    }

    // --- ADMIN ACTIONS ---

    fun clearAdminError() {
        _adminError.value = null
    }

    fun loginAdmin(login: String, password: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            if (_lockoutRemainingSeconds.value > 0L) {
                _adminError.value = "Слишком много попыток. Повторите через ${_lockoutRemainingSeconds.value} сек."
                return@launch
            }
            _isAdminBusy.value = true
            _adminError.value = null
            repository.loginAdmin(login, password)
                .onSuccess { session ->
                    prefs.resetAdminLockout()
                    _adminSession.value = session
                    _snackbarMessage.value = "Вход выполнен: ${session.login}"
                    onSuccess()
                }
                .onFailure { err ->
                    val (attempts, _) = prefs.recordFailedAdminAttempt()
                    val remainingHint = if (attempts >= 5) {
                        "Доступ временно заблокирован."
                    } else {
                        "Осталось попыток: ${5 - attempts}."
                    }
                    _adminError.value = "${err.message ?: "Ошибка входа"} $remainingHint"
                }
            _isAdminBusy.value = false
        }
    }

    fun logoutAdmin() {
        _adminSession.value = null
        _inspectedKeyCode.value = null
        _snackbarMessage.value = "Вы вышли из панели администратора."
    }

    fun readFileFromUri(uri: Uri, onLoaded: (name: String, mime: String, bytes: ByteArray?) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val resolver = appContext.contentResolver
                    val mime = resolver.getType(uri) ?: "application/octet-stream"
                    var displayName = "attachment.bin"
                    resolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIdx >= 0 && cursor.moveToFirst()) {
                            displayName = cursor.getString(nameIdx) ?: displayName
                        }
                    }
                    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                    Triple(displayName, mime, bytes)
                }
            }
            result
                .onSuccess { (name, mime, bytes) ->
                    if (bytes != null && bytes.size > 2 * 1024 * 1024) {
                        _adminError.value = "Файл превышает 2 МБ (${FileDownloadHelper.formatFileSize(bytes.size.toLong())}). Укажите прямую HTTPS-ссылку."
                        onLoaded(name, mime, null)
                    } else {
                        onLoaded(name, mime, bytes)
                    }
                }
                .onFailure {
                    _adminError.value = "Не удалось прочитать выбранный файл."
                }
        }
    }

    fun publishPost(
        title: String,
        description: String,
        category: String,
        tagsRaw: String,
        fileName: String,
        mimeType: String,
        fileUrl: String?,
        fileBytes: ByteArray?,
        inlineScriptText: String?,
        requiresKey: Boolean,
        createLinkedKey: Boolean = false,
        linkedKeyCode: String? = null,
        linkedKeyMaxActivations: Long = 5L,
        linkedKeyDurationMillis: Long? = null,
        linkedKeyScopeAll: Boolean = false,
        onPublished: () -> Unit
    ) {
        val session = _adminSession.value ?: return
        viewModelScope.launch {
            _isAdminBusy.value = true
            _adminError.value = null

            val finalBytes: ByteArray?
            val finalName: String
            val finalMime: String
            if (fileBytes != null) {
                finalBytes = fileBytes
                finalName = fileName
                finalMime = mimeType
            } else if (!inlineScriptText.isNullOrBlank()) {
                finalBytes = inlineScriptText.toByteArray(Charsets.UTF_8)
                finalName = fileName.ifBlank { "script.txt" }
                finalMime = "text/plain"
            } else {
                finalBytes = null
                finalName = fileName
                finalMime = mimeType
            }

            val tags = tagsRaw.split(",", ";", "#")
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            runCatching {
                repository.publishPost(
                    session = session,
                    title = title,
                    description = description,
                    category = category,
                    tags = tags,
                    fileName = finalName,
                    mimeType = finalMime,
                    fileUrl = fileUrl,
                    fileBytes = finalBytes,
                    requiresKey = requiresKey
                )
            }.onSuccess { res ->
                res.onSuccess { (postId, newSession) ->
                    _adminSession.value = newSession
                    if (requiresKey && createLinkedKey) {
                        val scope = if (linkedKeyScopeAll) "all" else postId
                        val expiresAtMillis = linkedKeyDurationMillis?.let { System.currentTimeMillis() + it }
                        val keyRes = runCatching {
                            repository.createAccessKey(
                                session = newSession,
                                customCode = linkedKeyCode,
                                label = title.trim().take(40),
                                scope = scope,
                                maxActivations = linkedKeyMaxActivations.coerceIn(1L, 100000L),
                                expiresAtMillis = expiresAtMillis
                            )
                        }.getOrElse { Result.failure(it) }

                        keyRes.onSuccess { (createdKey, keySession) ->
                            _adminSession.value = keySession
                            _snackbarMessage.value = "VIP-пост опубликован! Ключ: ${createdKey.code}"
                            onPublished()
                        }.onFailure { keyErr ->
                            _adminError.value = "Пост опубликован, но ключ не создан: ${keyErr.message}"
                            _snackbarMessage.value = "Пост опубликован, но не удалось создать ключ"
                            onPublished()
                        }
                    } else {
                        _snackbarMessage.value = "Публикация «$title» опубликована!"
                        onPublished()
                    }
                }.onFailure { err ->
                    _adminError.value = err.message ?: "Ошибка публикации"
                }
            }.onFailure { err ->
                _adminError.value = err.message ?: "Проверьте введённые данные"
            }

            _isAdminBusy.value = false
        }
    }

    fun deletePost(postId: String) {
        val session = _adminSession.value ?: return
        viewModelScope.launch {
            _isAdminBusy.value = true
            repository.deletePost(session, postId)
                .onSuccess { newSession ->
                    _adminSession.value = newSession
                    _snackbarMessage.value = "Публикация удалена."
                }
                .onFailure { err ->
                    _adminError.value = err.message ?: "Не удалось удалить публикацию"
                }
            _isAdminBusy.value = false
        }
    }

    fun createAccessKey(
        customCode: String?,
        label: String,
        scope: String,
        maxActivations: Long,
        durationMillis: Long?,
        onCreated: (AccessKeyItem) -> Unit
    ) {
        val session = _adminSession.value ?: return
        viewModelScope.launch {
            _isAdminBusy.value = true
            _adminError.value = null
            val expiresAtMillis = durationMillis?.let { System.currentTimeMillis() + it }
            runCatching {
                repository.createAccessKey(
                    session = session,
                    customCode = customCode,
                    label = label,
                    scope = scope,
                    maxActivations = maxActivations,
                    expiresAtMillis = expiresAtMillis
                )
            }.onSuccess { res ->
                res.onSuccess { (createdKey, newSession) ->
                    _adminSession.value = newSession
                    _snackbarMessage.value = "Ключ ${createdKey.code} создан!"
                    onCreated(createdKey)
                }.onFailure { err ->
                    _adminError.value = err.message ?: "Ошибка создания ключа"
                }
            }.onFailure { err ->
                _adminError.value = err.message ?: "Некорректные параметры ключа"
            }
            _isAdminBusy.value = false
        }
    }

    fun toggleKeyActive(key: AccessKeyItem) {
        val session = _adminSession.value ?: return
        viewModelScope.launch {
            repository.toggleKeyActive(session, key.code, !key.active)
                .onSuccess { newSession ->
                    _adminSession.value = newSession
                    _snackbarMessage.value = if (!key.active) "Ключ ${key.code} включён" else "Ключ ${key.code} отключён"
                }
                .onFailure { err ->
                    _adminError.value = err.message ?: "Ошибка обновления ключа"
                }
        }
    }

    fun updateKeyLimits(keyCode: String, maxActivations: Long, durationMillis: Long?) {
        val session = _adminSession.value ?: return
        viewModelScope.launch {
            _isAdminBusy.value = true
            val expiresAt = durationMillis?.let { System.currentTimeMillis() + it }
            runCatching {
                repository.updateKeyLimits(session, keyCode, maxActivations, expiresAt)
            }.onSuccess { res ->
                res.onSuccess { newSession ->
                    _adminSession.value = newSession
                    _snackbarMessage.value = "Лимиты ключа $keyCode обновлены"
                }.onFailure { err ->
                    _adminError.value = err.message ?: "Ошибка обновления"
                }
            }.onFailure { err ->
                _adminError.value = err.message ?: "Некорректный лимит"
            }
            _isAdminBusy.value = false
        }
    }

    fun archiveKey(keyCode: String) {
        val session = _adminSession.value ?: return
        viewModelScope.launch {
            repository.archiveKey(session, keyCode)
                .onSuccess { newSession ->
                    _adminSession.value = newSession
                    if (_inspectedKeyCode.value == keyCode) _inspectedKeyCode.value = null
                    _snackbarMessage.value = "Ключ $keyCode удалён и деактивирован"
                }
                .onFailure { err ->
                    _adminError.value = err.message ?: "Ошибка удаления ключа"
                }
        }
    }

    fun inspectKeyActivations(keyCode: String?) {
        _inspectedKeyCode.value = keyCode
    }

    fun addAdminAccount(login: String, password: String, onAdded: () -> Unit) {
        val session = _adminSession.value ?: return
        viewModelScope.launch {
            _isAdminBusy.value = true
            _adminError.value = null
            runCatching {
                repository.addAdminAccount(session, login, password)
            }.onSuccess { res ->
                res.onSuccess { newSession ->
                    _adminSession.value = newSession
                    _snackbarMessage.value = "Администратор '${login.trim().lowercase()}' добавлен!"
                    onAdded()
                }.onFailure { err ->
                    _adminError.value = err.message ?: "Не удалось добавить администратора"
                }
            }.onFailure { err ->
                _adminError.value = err.message ?: "Проверьте логин и пароль"
            }
            _isAdminBusy.value = false
        }
    }

    fun toggleAdminActive(target: AdminRosterItem) {
        val session = _adminSession.value ?: return
        viewModelScope.launch {
            runCatching {
                repository.toggleAdminActive(session, target.login, !target.active)
            }.onSuccess { res ->
                res.onSuccess { newSession ->
                    _adminSession.value = newSession
                    _snackbarMessage.value = "Статус ${target.login} обновлён"
                }.onFailure { err ->
                    _adminError.value = err.message ?: "Ошибка обновления статуса"
                }
            }.onFailure { err ->
                _adminError.value = err.message ?: "Операция запрещена"
            }
        }
    }
}
