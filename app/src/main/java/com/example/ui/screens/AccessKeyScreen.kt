package com.example.ui.screens

import android.content.Intent
import android.widget.Toast
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.download.FileDownloadHelper
import com.example.data.model.ActiveKeyStatus
import com.example.data.model.DownloadedRecord
import com.example.ui.components.formatExpiryCountdown
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.AmberKey
import com.example.ui.theme.CrimsonContainer
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.CyanSecondary
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.ObsidianOutline
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceHigh
import com.example.ui.theme.OnAmberContainer
import com.example.ui.theme.OnCrimsonContainer
import com.example.ui.theme.OnEmeraldContainer
import com.example.ui.theme.OnEmeraldPrimary
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary

@Composable
fun AccessKeyScreen(
    activeKeyStatus: ActiveKeyStatus,
    isRedeeming: Boolean,
    keyFeedback: String?,
    downloadedRecords: List<DownloadedRecord>,
    onRedeemKey: (String) -> Unit,
    onClearKey: () -> Unit,
) {
    val context = LocalContext.current
    var codeInput by remember { mutableStateOf("") }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    text = "Ключи и Загрузки",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Активация доступа к закрытым публикациям в реальном времени",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }

            item {
                when (activeKeyStatus) {
                    is ActiveKeyStatus.Valid -> {
                        val key = activeKeyStatus.key
                        Surface(
                            color = EmeraldContainer.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.6f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = EmeraldPrimary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Эксклюзивный доступ активен",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = OnEmeraldContainer
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = key.code,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontFamily = JetBrainsMonoFamily,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = EmeraldPrimary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${formatExpiryCountdown(key.expiresAt)} • Активаций: ${key.usedCount}/${key.maxActivations}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                                if (key.label.isNotBlank()) {
                                    Text(
                                        text = "Тариф: ${key.label}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = CyanSecondary
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedButton(
                                    onClick = onClearKey,
                                    modifier = Modifier.testTag("clear_active_key_button")
                                ) {
                                    Text("Отвязать ключ на этом устройстве")
                                }
                            }
                        }
                    }

                    is ActiveKeyStatus.RevokedOrInvalid -> {
                        Surface(
                            color = CrimsonContainer.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, CrimsonError.copy(alpha = 0.6f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.ErrorOutline,
                                        contentDescription = null,
                                        tint = CrimsonError
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Ключ ${activeKeyStatus.code} недействителен",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = OnCrimsonContainer
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = activeKeyStatus.reason,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                        }
                    }

                    else -> {
                        Surface(
                            color = ObsidianSurface,
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, ObsidianOutline)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = AmberKey,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Нет активного ключа",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Открытые публикации доступны сразу. Для закрытых постов введите ключ ниже.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Surface(
                    color = ObsidianSurface,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, ObsidianOutline)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Активировать код доступа",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = codeInput,
                            onValueChange = { codeInput = it.uppercase() },
                            label = { Text("Ключ активации") },
                            placeholder = { Text("NJG-XXXX-XXXX-XXXX") },
                            leadingIcon = {
                                Icon(Icons.Default.Key, contentDescription = null, tint = AmberKey)
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("key_code_input"),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EmeraldPrimary,
                                unfocusedBorderColor = ObsidianOutline
                            )
                        )

                        if (!keyFeedback.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                color = AmberContainer.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = keyFeedback,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OnAmberContainer,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { onRedeemKey(codeInput) },
                            enabled = !isRedeeming && codeInput.trim().length >= 4,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("activate_key_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EmeraldPrimary,
                                contentColor = OnEmeraldPrimary
                            )
                        ) {
                            if (isRedeeming) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = OnEmeraldPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Активировать ключ", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Мои загрузки (${downloadedRecords.size})",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            if (downloadedRecords.isEmpty()) {
                item {
                    Surface(
                        color = ObsidianSurface,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, ObsidianOutline.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = "Вы ещё не скачивали файлы. Выберите любой пост в ленте и нажмите кнопку загрузки.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(downloadedRecords, key = { "${it.postId}_${it.downloadedAtMillis}" }) { record ->
                    Surface(
                        color = ObsidianSurfaceHigh,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, ObsidianOutline.copy(alpha = 0.6f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                                contentDescription = null,
                                tint = EmeraldPrimary,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = record.fileName,
                                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFamily),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${record.title} • ${FileDownloadHelper.formatFileSize(record.fileSizeBytes)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                            IconButton(
                                onClick = {
                                    val intent = FileDownloadHelper.buildOpenFileIntent(context, record)
                                    if (intent != null) {
                                        runCatching { context.startActivity(intent) }
                                            .onFailure {
                                                Toast.makeText(context, "Не найдено приложение для открытия файла", Toast.LENGTH_SHORT).show()
                                            }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = "Открыть файл",
                                    tint = CyanSecondary
                                )
                            }
                            IconButton(
                                onClick = {
                                    val intent = FileDownloadHelper.buildShareFileIntent(context, record)
                                    if (intent != null) {
                                        context.startActivity(Intent.createChooser(intent, "Поделиться файлом"))
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Поделиться файлом",
                                    tint = EmeraldPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
