package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Publish
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.download.FileDownloadHelper
import com.example.data.model.AccessKeyItem
import com.example.data.model.AdminRosterItem
import com.example.data.model.AdminSessionState
import com.example.data.model.KeyActivationItem
import com.example.data.model.PostCategories
import com.example.data.model.PostItem
import com.example.data.security.SecurityUtils
import com.example.ui.components.copyTextToClipboard
import com.example.ui.components.formatTimestamp
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.AmberKey
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.CyanSecondary
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianOutline
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceHigh
import com.example.ui.theme.OnAmberContainer
import com.example.ui.theme.OnEmeraldPrimary
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary

@Composable
fun AdminDashboardScreen(
    session: AdminSessionState,
    posts: List<PostItem>,
    keys: List<AccessKeyItem>,
    roster: List<AdminRosterItem>,
    inspectedKeyCode: String?,
    inspectedActivations: List<KeyActivationItem>,
    isBusy: Boolean,
    errorMessage: String?,
    onReadFileUri: (Uri, (String, String, ByteArray?) -> Unit) -> Unit,
    onPublishPost: (
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
        createLinkedKey: Boolean,
        linkedKeyCode: String?,
        linkedKeyMaxActivations: Long,
        linkedKeyDurationMillis: Long?,
        linkedKeyScopeAll: Boolean,
        onDone: () -> Unit
    ) -> Unit,
    onDeletePost: (String) -> Unit,
    onCreateKey: (String?, String, String, Long, Long?) -> Unit,
    onToggleKeyActive: (AccessKeyItem) -> Unit,
    onUpdateKeyLimits: (String, Long, Long?) -> Unit,
    onArchiveKey: (String) -> Unit,
    onInspectKey: (String?) -> Unit,
    onAddAdmin: (login: String, password: String, onDone: () -> Unit) -> Unit,
    onToggleAdminActive: (AdminRosterItem) -> Unit,
    onLogout: () -> Unit,
    onClose: () -> Unit,
    onShowMessage: (String) -> Unit,
) {
    BackHandler { onClose() }

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Публикации", "Ключи (Live)", "Админы")

    Scaffold(
        containerColor = ObsidianBg,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose, modifier = Modifier.testTag("admin_back_button")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Вернуться в приложение"
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Панель управления",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "${session.login} • ${if (session.role == "super_admin") "Главный админ" else "Админ"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = EmeraldPrimary
                        )
                    }
                    IconButton(onClick = onLogout, modifier = Modifier.testTag("admin_logout_button")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Выйти из админки",
                            tint = CrimsonError
                        )
                    }
                }
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = ObsidianSurface,
                    contentColor = EmeraldPrimary
                ) {
                    tabs.forEachIndexed { idx, label ->
                        Tab(
                            selected = selectedTab == idx,
                            onClick = { selectedTab = idx },
                            text = { Text(label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(modifier = Modifier.widthIn(max = 720.dp).fillMaxSize()) {
                when (selectedTab) {
                    0 -> AdminPostsTab(
                        posts = posts,
                        keys = keys,
                        isBusy = isBusy,
                        errorMessage = errorMessage,
                        onReadFileUri = onReadFileUri,
                        onPublishPost = onPublishPost,
                        onDeletePost = onDeletePost,
                        onCreateKey = onCreateKey,
                        onShowMessage = onShowMessage
                    )
                    1 -> AdminKeysTab(
                        keys = keys,
                        posts = posts,
                        inspectedKeyCode = inspectedKeyCode,
                        inspectedActivations = inspectedActivations,
                        isBusy = isBusy,
                        errorMessage = errorMessage,
                        onCreateKey = onCreateKey,
                        onToggleKeyActive = onToggleKeyActive,
                        onUpdateKeyLimits = onUpdateKeyLimits,
                        onArchiveKey = onArchiveKey,
                        onInspectKey = onInspectKey,
                        onShowMessage = onShowMessage
                    )
                    2 -> AdminTeamTab(
                        roster = roster,
                        isBusy = isBusy,
                        errorMessage = errorMessage,
                        onAddAdmin = onAddAdmin,
                        onToggleAdminActive = onToggleAdminActive
                    )
                }
            }
        }
    }
}

private val POST_KEY_DURATIONS: List<Pair<String, Long?>> = listOf(
    "1 час" to 3_600_000L,
    "24 часа" to 24L * 3_600_000L,
    "7 дней" to 7L * 24L * 3_600_000L,
    "30 дней" to 30L * 24L * 3_600_000L,
    "Бессрочно" to null
)

@Composable
private fun AdminPostsTab(
    posts: List<PostItem>,
    keys: List<AccessKeyItem>,
    isBusy: Boolean,
    errorMessage: String?,
    onReadFileUri: (Uri, (String, String, ByteArray?) -> Unit) -> Unit,
    onPublishPost: (
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
        createLinkedKey: Boolean,
        linkedKeyCode: String?,
        linkedKeyMaxActivations: Long,
        linkedKeyDurationMillis: Long?,
        linkedKeyScopeAll: Boolean,
        onDone: () -> Unit
    ) -> Unit,
    onDeletePost: (String) -> Unit,
    onCreateKey: (String?, String, String, Long, Long?) -> Unit,
    onShowMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(PostCategories.PUBLISHABLE.first()) }
    var tagsRaw by remember { mutableStateOf("") }
    var requiresKey by remember { mutableStateOf(false) }

    var createLinkedKey by remember { mutableStateOf(true) }
    var linkedKeyCode by remember { mutableStateOf(SecurityUtils.generateAccessKeyCode()) }
    var linkedKeyMaxText by remember { mutableStateOf("5") }
    var linkedKeyDurIdx by remember { mutableIntStateOf(2) } // 7 days
    var linkedKeyScopeAll by remember { mutableStateOf(false) }
    var quickKeyForPost by remember { mutableStateOf<PostItem?>(null) }

    var attachMode by remember { mutableIntStateOf(0) } // 0 = file picker, 1 = inline script, 2 = https url
    var pickedFileName by remember { mutableStateOf("") }
    var pickedMimeType by remember { mutableStateOf("application/octet-stream") }
    var pickedBytes by remember { mutableStateOf<ByteArray?>(null) }
    var inlineScriptText by remember { mutableStateOf("") }
    var inlineFileName by remember { mutableStateOf("script.lua") }
    var externalUrl by remember { mutableStateOf("") }
    var externalFileName by remember { mutableStateOf("") }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onReadFileUri(uri) { name, mime, bytes ->
                pickedFileName = name
                pickedMimeType = mime
                pickedBytes = bytes
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                color = ObsidianSurface,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, ObsidianOutline)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Новая публикация", style = MaterialTheme.typography.titleMedium)

                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Заголовок") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_post_title_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Описание и инструкция") },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_post_desc_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(PostCategories.PUBLISHABLE) { cat ->
                            FilterChip(
                                selected = category == cat,
                                onClick = { category = cat },
                                label = { Text(cat) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = tagsRaw,
                        onValueChange = { tagsRaw = it },
                        label = { Text("Теги (через запятую)") },
                        placeholder = { Text("ninjago, script, v2") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("VIP-секция (только по ключу)", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "Пост попадёт в раздел «VIP / По ключу», а файл будет скрыт под замком до ввода ключа",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        Switch(
                            checked = requiresKey,
                            onCheckedChange = { enabled ->
                                requiresKey = enabled
                                if (enabled && linkedKeyCode.isBlank()) {
                                    linkedKeyCode = SecurityUtils.generateAccessKeyCode()
                                }
                            },
                            modifier = Modifier.testTag("admin_post_requires_key_switch"),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = ObsidianBg,
                                checkedTrackColor = AmberKey
                            )
                        )
                    }

                    if (requiresKey) {
                        Surface(
                            color = AmberContainer.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, AmberKey.copy(alpha = 0.45f))
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Создать ключ сразу к файлу",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = AmberKey
                                        )
                                        Text(
                                            text = "Ключ автоматически привяжется к этой публикации",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSecondary
                                        )
                                    }
                                    Switch(
                                        checked = createLinkedKey,
                                        onCheckedChange = { createLinkedKey = it },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = ObsidianBg,
                                            checkedTrackColor = AmberKey
                                        )
                                    )
                                }

                                if (createLinkedKey) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = linkedKeyCode,
                                            onValueChange = { linkedKeyCode = it.uppercase() },
                                            label = { Text("Код ключа") },
                                            trailingIcon = {
                                                IconButton(onClick = {
                                                    linkedKeyCode = SecurityUtils.generateAccessKeyCode()
                                                }) {
                                                    Icon(
                                                        Icons.Default.AutoFixHigh,
                                                        contentDescription = "Сгенерировать код",
                                                        tint = EmeraldPrimary
                                                    )
                                                }
                                            },
                                            singleLine = true,
                                            modifier = Modifier.weight(1.6f),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        OutlinedTextField(
                                            value = linkedKeyMaxText,
                                            onValueChange = { linkedKeyMaxText = it.filter { ch -> ch.isDigit() } },
                                            label = { Text("Лимит") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true,
                                            modifier = Modifier.weight(0.8f),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                    }

                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        items(POST_KEY_DURATIONS.indices.toList()) { idx ->
                                            FilterChip(
                                                selected = linkedKeyDurIdx == idx,
                                                onClick = { linkedKeyDurIdx = idx },
                                                label = { Text(POST_KEY_DURATIONS[idx].first) }
                                            )
                                        }
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        FilterChip(
                                            selected = !linkedKeyScopeAll,
                                            onClick = { linkedKeyScopeAll = false },
                                            label = { Text("Только этот файл") }
                                        )
                                        FilterChip(
                                            selected = linkedKeyScopeAll,
                                            onClick = { linkedKeyScopeAll = true },
                                            label = { Text("Весь VIP-раздел") }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Text("Прикрепить файл:", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = attachMode == 0,
                            onClick = { attachMode = 0 },
                            label = { Text("Файл с устройства") }
                        )
                        FilterChip(
                            selected = attachMode == 1,
                            onClick = { attachMode = 1 },
                            label = { Text("Вставить скрипт") }
                        )
                        FilterChip(
                            selected = attachMode == 2,
                            onClick = { attachMode = 2 },
                            label = { Text("HTTPS ссылка") }
                        )
                    }

                    when (attachMode) {
                        0 -> {
                            OutlinedButton(
                                onClick = { filePicker.launch(arrayOf("*/*")) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("admin_pick_file_button")
                            ) {
                                Icon(Icons.Default.AttachFile, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    if (pickedBytes != null) {
                                        "$pickedFileName (${FileDownloadHelper.formatFileSize(pickedBytes!!.size.toLong())})"
                                    } else {
                                        "Выбрать файл (до 2 МБ в облако)"
                                    }
                                )
                            }
                        }
                        1 -> {
                            OutlinedTextField(
                                value = inlineFileName,
                                onValueChange = { inlineFileName = it },
                                label = { Text("Имя файла") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = inlineScriptText,
                                onValueChange = { inlineScriptText = it },
                                label = { Text("Содержимое скрипта / конфига") },
                                minLines = 4,
                                maxLines = 8,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        else -> {
                            OutlinedTextField(
                                value = externalUrl,
                                onValueChange = { externalUrl = it },
                                label = { Text("Прямая HTTPS-ссылка на файл") },
                                placeholder = { Text("https://example.com/release.zip") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = externalFileName,
                                onValueChange = { externalFileName = it },
                                label = { Text("Имя файла при скачивании") },
                                placeholder = { Text("release.zip") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    if (!errorMessage.isNullOrBlank()) {
                        Text(errorMessage, style = MaterialTheme.typography.bodySmall, color = CrimsonError)
                    }

                    Button(
                        onClick = {
                            val chosenFileName = when (attachMode) {
                                0 -> pickedFileName
                                1 -> inlineFileName
                                else -> externalFileName.ifBlank { "download.bin" }
                            }
                            val maxAct = (linkedKeyMaxText.toLongOrNull() ?: 5L).coerceIn(1L, 100000L)
                            val durMillis = POST_KEY_DURATIONS[linkedKeyDurIdx].second
                            onPublishPost(
                                title,
                                description,
                                category,
                                tagsRaw,
                                chosenFileName,
                                if (attachMode == 0) pickedMimeType else "text/plain",
                                if (attachMode == 2) externalUrl else null,
                                if (attachMode == 0) pickedBytes else null,
                                if (attachMode == 1) inlineScriptText else null,
                                requiresKey,
                                requiresKey && createLinkedKey,
                                linkedKeyCode,
                                maxAct,
                                durMillis,
                                linkedKeyScopeAll
                            ) {
                                title = ""
                                description = ""
                                tagsRaw = ""
                                pickedBytes = null
                                pickedFileName = ""
                                inlineScriptText = ""
                                externalUrl = ""
                                linkedKeyCode = SecurityUtils.generateAccessKeyCode()
                            }
                        },
                        enabled = !isBusy && title.isNotBlank() && description.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("admin_publish_post_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldPrimary,
                            contentColor = OnEmeraldPrimary
                        )
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = OnEmeraldPrimary, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Publish, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Опубликовать", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "Опубликовано (${posts.size})",
                style = MaterialTheme.typography.titleMedium
            )
        }

        items(posts, key = { it.id }) { post ->
            val matchingKeys = remember(keys, post.id) {
                keys.filter { it.isCurrentlyValid() && it.coversPost(post.id) }
            }
            Surface(
                color = ObsidianSurfaceHigh,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(
                    1.dp,
                    if (post.requiresKey) AmberKey.copy(alpha = 0.45f) else ObsidianOutline.copy(alpha = 0.7f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(post.title, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "${post.category} • ${if (post.requiresKey) "VIP (по ключу)" else "Открытый"} • Скачиваний: ${post.downloadCount}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (post.requiresKey) AmberKey else TextSecondary
                            )
                        }
                        if (post.requiresKey) {
                            OutlinedButton(
                                onClick = { quickKeyForPost = post },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(15.dp), tint = AmberKey)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("+ Ключ", style = MaterialTheme.typography.labelMedium, color = AmberKey)
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        IconButton(onClick = { onDeletePost(post.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Удалить пост", tint = CrimsonError)
                        }
                    }

                    if (post.requiresKey) {
                        if (matchingKeys.isEmpty()) {
                            Text(
                                text = "Нет активных ключей к этому файлу — нажмите «+ Ключ»",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                        } else {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(matchingKeys) { k ->
                                    Surface(
                                        color = AmberContainer.copy(alpha = 0.45f),
                                        shape = RoundedCornerShape(8.dp),
                                        onClick = {
                                            copyTextToClipboard(context, "AccessKey", k.code)
                                            onShowMessage("Ключ ${k.code} скопирован")
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "${k.code} (${k.usedCount}/${k.maxActivations})",
                                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                                color = OnAmberContainer
                                            )
                                            Icon(
                                                Icons.Default.ContentCopy,
                                                contentDescription = "Копировать ключ",
                                                modifier = Modifier.size(12.dp),
                                                tint = OnAmberContainer
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (quickKeyForPost != null) {
        val targetPost = quickKeyForPost!!
        var dialogCode by remember(targetPost.id) { mutableStateOf(SecurityUtils.generateAccessKeyCode()) }
        var dialogMaxText by remember(targetPost.id) { mutableStateOf("5") }
        var dialogDurIdx by remember(targetPost.id) { mutableIntStateOf(2) }
        var dialogScopeAll by remember(targetPost.id) { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { quickKeyForPost = null },
            containerColor = ObsidianSurface,
            title = { Text("Ключ к «${targetPost.title.take(24)}»") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = dialogCode,
                        onValueChange = { dialogCode = it.uppercase() },
                        label = { Text("Код ключа") },
                        trailingIcon = {
                            IconButton(onClick = { dialogCode = SecurityUtils.generateAccessKeyCode() }) {
                                Icon(Icons.Default.AutoFixHigh, contentDescription = "Сгенерировать", tint = EmeraldPrimary)
                            }
                        },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = dialogMaxText,
                        onValueChange = { dialogMaxText = it.filter { c -> c.isDigit() } },
                        label = { Text("Лимит активаций") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(POST_KEY_DURATIONS.indices.toList()) { idx ->
                            FilterChip(
                                selected = dialogDurIdx == idx,
                                onClick = { dialogDurIdx = idx },
                                label = { Text(POST_KEY_DURATIONS[idx].first) }
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !dialogScopeAll,
                            onClick = { dialogScopeAll = false },
                            label = { Text("Только этот файл") }
                        )
                        FilterChip(
                            selected = dialogScopeAll,
                            onClick = { dialogScopeAll = true },
                            label = { Text("Весь VIP") }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val maxAct = (dialogCode.trim().length >= 4).let {
                            (dialogMaxText.toLongOrNull() ?: 5L).coerceIn(1L, 100000L)
                        }
                        onCreateKey(
                            dialogCode,
                            targetPost.title.take(40),
                            if (dialogScopeAll) "all" else targetPost.id,
                            maxAct,
                            POST_KEY_DURATIONS[dialogDurIdx].second
                        )
                        quickKeyForPost = null
                    },
                    enabled = dialogCode.trim().length >= 4
                ) {
                    Text("Создать")
                }
            },
            dismissButton = {
                TextButton(onClick = { quickKeyForPost = null }) { Text("Отмена") }
            }
        )
    }
}

@Composable
private fun AdminTeamTab(
    roster: List<AdminRosterItem>,
    isBusy: Boolean,
    errorMessage: String?,
    onAddAdmin: (login: String, password: String, onDone: () -> Unit) -> Unit,
    onToggleAdminActive: (AdminRosterItem) -> Unit,
) {
    var newLogin by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                color = ObsidianSurface,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, ObsidianOutline)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Добавить администратора", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Новый администратор получит доступ к публикации контента, созданию ключей и отслеживанию активаций.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value = newLogin,
                        onValueChange = { newLogin = it.lowercase() },
                        label = { Text("Логин (латиница, цифры)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_admin_login_input"),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("Пароль (мин. 6 символов)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_admin_password_input"),
                        shape = RoundedCornerShape(12.dp)
                    )
                    if (!errorMessage.isNullOrBlank()) {
                        Text(errorMessage, style = MaterialTheme.typography.bodySmall, color = CrimsonError)
                    }
                    Button(
                        onClick = {
                            onAddAdmin(newLogin, newPassword) {
                                newLogin = ""
                                newPassword = ""
                            }
                        },
                        enabled = !isBusy && newLogin.trim().length >= 2 && newPassword.length >= 6,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("add_admin_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldPrimary,
                            contentColor = OnEmeraldPrimary
                        )
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Добавить админа", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Text("Список администраторов (${roster.size})", style = MaterialTheme.typography.titleMedium)
        }

        items(roster, key = { it.login }) { admin ->
            val isSuper = admin.login == SecurityUtils.SUPER_ADMIN_LOGIN || admin.role == "super_admin"
            Surface(
                color = ObsidianSurfaceHigh,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, ObsidianOutline)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = if (isSuper) AmberKey else CyanSecondary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = admin.login,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${if (isSuper) "Главный админ" else "Админ"} • Добавил: ${admin.createdBy} • ${formatTimestamp(admin.createdAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                    if (!isSuper) {
                        Switch(
                            checked = admin.active,
                            onCheckedChange = { onToggleAdminActive(admin) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = OnEmeraldPrimary,
                                checkedTrackColor = EmeraldPrimary
                            )
                        )
                    }
                }
            }
        }
    }
}
