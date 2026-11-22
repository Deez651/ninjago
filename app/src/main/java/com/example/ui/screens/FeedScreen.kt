package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.download.FileDownloadHelper
import com.example.data.model.AccessFilter
import com.example.data.model.ActiveKeyStatus
import com.example.data.model.DownloadProgressState
import com.example.data.model.DownloadedRecord
import com.example.data.model.PostCategories
import com.example.data.model.PostItem
import com.example.data.model.UiState
import com.example.ui.components.PostCard
import com.example.ui.components.formatTimestamp
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.AmberKey
import com.example.ui.theme.CyanSecondary
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.ObsidianOutline
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceHigh
import com.example.ui.theme.OnAmberContainer
import com.example.ui.theme.OnEmeraldContainer
import com.example.ui.theme.OnEmeraldPrimary
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    postsState: UiState<List<PostItem>>,
    searchQuery: String,
    selectedCategory: String,
    accessFilter: AccessFilter,
    activeKeyStatus: ActiveKeyStatus,
    downloadProgress: Map<String, DownloadProgressState>,
    downloadedRecords: List<DownloadedRecord>,
    unreadNotifications: List<PostItem>,
    initialOpenPostId: String?,
    onClearInitialPostId: () -> Unit,
    onSearchChange: (String) -> Unit,
    onCategorySelect: (String) -> Unit,
    onAccessFilterSelect: (AccessFilter) -> Unit,
    onDownloadPost: (PostItem) -> Unit,
    onRedeemKey: (String) -> Unit,
    onMarkNotificationsRead: () -> Unit,
    canAccessPost: (PostItem) -> Boolean,
    onNavigateToKeys: () -> Unit,
) {
    var selectedPost by remember { mutableStateOf<PostItem?>(null) }
    var showNotificationsSheet by remember { mutableStateOf(false) }

    // Open post if launched from notification
    if (initialOpenPostId != null && postsState is UiState.Success) {
        val target = postsState.data.firstOrNull { it.id == initialOpenPostId }
        if (target != null) {
            selectedPost = target
        }
        onClearInitialPostId()
    }

    // Keep selectedPost synced with live Firestore updates (e.g. downloadCount)
    val liveSelectedPost = selectedPost?.let { current ->
        (postsState as? UiState.Success)?.data?.firstOrNull { it.id == current.id } ?: current
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Ninjago Hub",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = when (activeKeyStatus) {
                            is ActiveKeyStatus.Valid -> "Ключ активен: ${activeKeyStatus.key.code}"
                            is ActiveKeyStatus.RevokedOrInvalid -> "Ключ недействителен"
                            else -> "Открытая лента • без входа"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = when (activeKeyStatus) {
                            is ActiveKeyStatus.Valid -> EmeraldPrimary
                            is ActiveKeyStatus.RevokedOrInvalid -> AmberKey
                            else -> TextSecondary
                        }
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = if (activeKeyStatus is ActiveKeyStatus.Valid) EmeraldContainer else ObsidianSurfaceHigh,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onNavigateToKeys)
                            .testTag("top_key_badge")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = "Ключ доступа",
                                tint = if (activeKeyStatus is ActiveKeyStatus.Valid) EmeraldPrimary else AmberKey,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (activeKeyStatus is ActiveKeyStatus.Valid) "VIP" else "Ключ",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (activeKeyStatus is ActiveKeyStatus.Valid) OnEmeraldContainer else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    IconButton(
                        onClick = { showNotificationsSheet = true },
                        modifier = Modifier.testTag("notifications_bell_button")
                    ) {
                        BadgedBox(
                            badge = {
                                if (unreadNotifications.isNotEmpty()) {
                                    Badge(containerColor = EmeraldPrimary, contentColor = OnEmeraldPrimary) {
                                        Text("${unreadNotifications.size}")
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "Уведомления о новых публикациях",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Search input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .testTag("search_posts_input"),
                placeholder = { Text("Поиск по постам, скриптам, тегам, файлам...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = EmeraldPrimary
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Очистить поиск")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = EmeraldPrimary,
                    unfocusedBorderColor = ObsidianOutline,
                    focusedContainerColor = ObsidianSurface,
                    unfocusedContainerColor = ObsidianSurface
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Category chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(PostCategories.ALL) { category ->
                    val selected = category == selectedCategory
                    FilterChip(
                        selected = selected,
                        onClick = { onCategorySelect(category) },
                        label = { Text(category) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmeraldContainer,
                            selectedLabelColor = OnEmeraldContainer,
                            containerColor = ObsidianSurface,
                            labelColor = TextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            borderColor = ObsidianOutline,
                            selectedBorderColor = EmeraldPrimary
                        )
                    )
                }
            }

            // Access filter row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AccessFilter.entries.forEach { filter ->
                    val selected = filter == accessFilter
                    FilterChip(
                        selected = selected,
                        onClick = { onAccessFilterSelect(filter) },
                        label = { Text(filter.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ObsidianSurfaceHigh,
                            selectedLabelColor = CyanSecondary,
                            containerColor = ObsidianSurface,
                            labelColor = TextMuted
                        )
                    )
                }
            }

            when (postsState) {
                is UiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = EmeraldPrimary)
                    }
                }
                is UiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = postsState.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                is UiState.Success -> {
                    val posts = postsState.data
                    if (posts.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Image(
                                    painter = painterResource(id = R.drawable.img_empty_vault),
                                    contentDescription = "Нет публикаций",
                                    modifier = Modifier
                                        .size(120.dp)
                                        .clip(RoundedCornerShape(24.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "Публикации не найдены",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (searchQuery.isNotBlank()) "Попробуйте изменить поисковый запрос или фильтры."
                                    else "Администратор скоро опубликует новые файлы и скрипты.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(posts, key = { it.id }) { post ->
                                val hasAccess = canAccessPost(post)
                                PostCard(
                                    post = post,
                                    hasAccess = hasAccess,
                                    downloadProgress = downloadProgress[post.id],
                                    onClick = { selectedPost = post },
                                    onQuickDownload = {
                                        if (hasAccess) onDownloadPost(post)
                                        else selectedPost = post
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (liveSelectedPost != null) {
        val hasAccess = canAccessPost(liveSelectedPost)
        val existingDownload = downloadedRecords.firstOrNull { it.postId == liveSelectedPost.id }
        PostDetailSheet(
            post = liveSelectedPost,
            hasAccess = hasAccess,
            progress = downloadProgress[liveSelectedPost.id],
            downloadedRecord = existingDownload,
            onDismiss = { selectedPost = null },
            onDownload = { onDownloadPost(liveSelectedPost) },
            onRedeemKey = onRedeemKey
        )
    }

    if (showNotificationsSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showNotificationsSheet = false
                onMarkNotificationsRead()
            },
            containerColor = ObsidianSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Новые публикации",
                        style = MaterialTheme.typography.titleLarge
                    )
                    if (unreadNotifications.isNotEmpty()) {
                        OutlinedButton(onClick = onMarkNotificationsRead) {
                            Text("Прочитано")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                if (unreadNotifications.isEmpty()) {
                    Text(
                        text = "Все новые публикации уже просмотрены.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(vertical = 20.dp)
                    )
                } else {
                    unreadNotifications.take(10).forEach { post ->
                        Surface(
                            color = ObsidianSurfaceHigh,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .clickable {
                                    showNotificationsSheet = false
                                    selectedPost = post
                                    onMarkNotificationsRead()
                                }
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = post.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = EmeraldPrimary
                                )
                                Text(
                                    text = "${post.category} • ${formatTimestamp(post.createdAt)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PostDetailSheet(
    post: PostItem,
    hasAccess: Boolean,
    progress: DownloadProgressState?,
    downloadedRecord: DownloadedRecord?,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onRedeemKey: (String) -> Unit,
) {
    val context = LocalContext.current
    var inlineKeyInput by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(color = ObsidianSurfaceHigh, shape = RoundedCornerShape(8.dp)) {
                    Text(
                        text = post.category,
                        style = MaterialTheme.typography.labelMedium,
                        color = CyanSecondary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Surface(
                    color = if (post.requiresKey && !hasAccess) AmberContainer else EmeraldContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (post.requiresKey && !hasAccess) "Закрыто ключом" else "Доступ открыт",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (post.requiresKey && !hasAccess) OnAmberContainer else OnEmeraldContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = post.title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Автор: ${post.authorLogin} • ${formatTimestamp(post.createdAt)} • Скачиваний: ${post.downloadCount}",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = post.description,
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary
            )

            if (post.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    post.tags.forEach { tag ->
                        Surface(color = ObsidianSurfaceHigh, shape = RoundedCornerShape(8.dp)) {
                            Text(
                                text = "#$tag",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            if (!hasAccess) {
                Surface(
                    color = AmberContainer.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, AmberKey.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = AmberKey)
                            Text(
                                text = "Эксклюзивный файл — требуется ключ доступа",
                                style = MaterialTheme.typography.titleSmall,
                                color = OnAmberContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = inlineKeyInput,
                            onValueChange = { inlineKeyInput = it.uppercase() },
                            placeholder = { Text("NJG-XXXX-XXXX-XXXX") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("sheet_key_input"),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { onRedeemKey(inlineKeyInput) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("sheet_activate_key_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AmberKey,
                                contentColor = MaterialTheme.colorScheme.background
                            )
                        ) {
                            Icon(Icons.Default.Key, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Активировать и разблокировать")
                        }
                    }
                }
            } else if (post.hasFile) {
                Surface(
                    color = ObsidianSurfaceHigh,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, ObsidianOutline)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = post.fileName.ifBlank { "Файл публикации" },
                            style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFamily),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (post.fileSizeBytes > 0L) {
                            Text(
                                text = "Размер: ${FileDownloadHelper.formatFileSize(post.fileSizeBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        if (progress?.isDownloading == true) {
                            LinearProgressIndicator(
                                progress = { (progress.progressPercent / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = EmeraldPrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Загрузка: ${progress.progressPercent}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = EmeraldPrimary
                            )
                        } else {
                            Button(
                                onClick = onDownload,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("sheet_download_button"),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = EmeraldPrimary,
                                    contentColor = OnEmeraldPrimary
                                )
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (downloadedRecord != null) "Скачать повторно" else "Скачать файл",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (downloadedRecord != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val intent = FileDownloadHelper.buildOpenFileIntent(context, downloadedRecord)
                                        if (intent != null) {
                                            runCatching { context.startActivity(intent) }
                                                .onFailure {
                                                    Toast.makeText(context, "Нет приложения для открытия этого типа файла", Toast.LENGTH_SHORT).show()
                                                }
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Открыть")
                                }
                                OutlinedButton(
                                    onClick = {
                                        val intent = FileDownloadHelper.buildShareFileIntent(context, downloadedRecord)
                                        if (intent != null) {
                                            context.startActivity(android.content.Intent.createChooser(intent, "Поделиться файлом"))
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Поделиться")
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
