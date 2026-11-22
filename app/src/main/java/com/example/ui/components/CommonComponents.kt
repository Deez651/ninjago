package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.download.FileDownloadHelper
import com.example.data.model.DownloadProgressState
import com.example.data.model.PostItem
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
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Locale


fun openExternalUrl(context: Context, url: String) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

fun copyTextToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}

fun formatTimestamp(ts: Timestamp?): String {
    val date = ts?.toDate() ?: return "Только что"
    val sdf = SimpleDateFormat("dd MMM, HH:mm", Locale.forLanguageTag("ru"))
    return sdf.format(date)
}

fun formatExpiryCountdown(expiresAt: Timestamp?): String {
    if (expiresAt == null) return "Бессрочный"
    val diff = expiresAt.toDate().time - System.currentTimeMillis()
    if (diff <= 0L) return "Истёк"
    val hours = diff / 3_600_000L
    val days = hours / 24L
    return when {
        days >= 1L -> "Осталось $days дн. ${hours % 24} ч."
        hours >= 1L -> "Осталось $hours ч. ${(diff % 3_600_000L) / 60_000L} мин."
        else -> "Осталось ${(diff / 60_000L).coerceAtLeast(1L)} мин."
    }
}

@Composable
fun TelegramChannelCard(
    title: String,
    handle: String,
    url: String,
    accentColor: Color,
    testTag: String,
    onCopied: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag)
            .clip(RoundedCornerShape(16.dp))
            .clickable { openExternalUrl(context, url) },
        color = ObsidianSurfaceHigh,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(
                        listOf(accentColor.copy(alpha = 0.14f), Color.Transparent)
                    )
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = handle,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = url,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                    color = accentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = {
                    copyTextToClipboard(context, handle, url)
                    onCopied("Ссылка $handle скопирована")
                }
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Копировать ссылку",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = "Открыть канал",
                tint = accentColor,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun PostCard(
    post: PostItem,
    hasAccess: Boolean,
    downloadProgress: DownloadProgressState?,
    onClick: () -> Unit,
    onQuickDownload: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lockColor = when {
        !post.requiresKey -> EmeraldPrimary
        hasAccess -> CyanSecondary
        else -> AmberKey
    }
    val lockBg = when {
        !post.requiresKey -> EmeraldContainer
        hasAccess -> Color(0xFF0C354D)
        else -> AmberContainer
    }
    val lockTextColor = when {
        !post.requiresKey -> OnEmeraldContainer
        hasAccess -> Color(0xFFBAE6FD)
        else -> OnAmberContainer
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("post_card_${post.id}")
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
        border = BorderStroke(1.dp, ObsidianOutline.copy(alpha = 0.7f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = ObsidianSurfaceHigh,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = post.category,
                            style = MaterialTheme.typography.labelMedium,
                            color = CyanSecondary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    Surface(
                        color = lockBg,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (post.requiresKey && !hasAccess) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = null,
                                tint = lockColor,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = when {
                                    !post.requiresKey -> "Открыто"
                                    hasAccess -> "Открыт ключом"
                                    else -> "По ключу"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = lockTextColor
                            )
                        }
                    }
                }
                Text(
                    text = formatTimestamp(post.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = post.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = post.description,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            if (post.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    post.tags.take(4).forEach { tag ->
                        Text(
                            text = "#$tag",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldPrimary.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            if (post.hasFile) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    color = ObsidianSurfaceHigh,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, ObsidianOutline.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                            contentDescription = "Файл",
                            tint = lockColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = post.fileName.ifBlank { "Прикреплённый файл" },
                                style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFamily),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = buildString {
                                    if (post.fileSizeBytes > 0L) {
                                        append(FileDownloadHelper.formatFileSize(post.fileSizeBytes))
                                        append(" • ")
                                    }
                                    append("Скачиваний: ${post.downloadCount}")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                        }
                        IconButton(
                            onClick = onQuickDownload,
                            modifier = Modifier.testTag("download_btn_${post.id}")
                        ) {
                            Icon(
                                imageVector = if (hasAccess) Icons.Default.Download else Icons.Default.Lock,
                                contentDescription = if (hasAccess) "Скачать файл" else "Требуется ключ",
                                tint = lockColor
                            )
                        }
                    }
                }
            }

            if (downloadProgress?.isDownloading == true) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (downloadProgress.progressPercent / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = EmeraldPrimary,
                    trackColor = ObsidianSurfaceHigh
                )
            }
        }
    }
}
