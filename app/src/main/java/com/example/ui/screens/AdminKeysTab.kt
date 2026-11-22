package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.AccessKeyItem
import com.example.data.model.KeyActivationItem
import com.example.data.model.PostItem
import com.example.data.security.SecurityUtils
import com.example.ui.components.copyTextToClipboard
import com.example.ui.components.formatExpiryCountdown
import com.example.ui.components.formatTimestamp
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

private data class DurationPreset(val label: String, val millis: Long?)

private val DURATION_PRESETS = listOf(
    DurationPreset("1 час", 3_600_000L),
    DurationPreset("24 часа", 24L * 3_600_000L),
    DurationPreset("7 дней", 7L * 24L * 3_600_000L),
    DurationPreset("30 дней", 30L * 24L * 3_600_000L),
    DurationPreset("Бессрочно", null),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminKeysTab(
    keys: List<AccessKeyItem>,
    posts: List<PostItem>,
    inspectedKeyCode: String?,
    inspectedActivations: List<KeyActivationItem>,
    isBusy: Boolean,
    errorMessage: String?,
    onCreateKey: (customCode: String?, label: String, scope: String, maxActivations: Long, durationMillis: Long?) -> Unit,
    onToggleKeyActive: (AccessKeyItem) -> Unit,
    onUpdateKeyLimits: (keyCode: String, maxActivations: Long, durationMillis: Long?) -> Unit,
    onArchiveKey: (String) -> Unit,
    onInspectKey: (String?) -> Unit,
    onShowMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    var customCode by remember { mutableStateOf(SecurityUtils.generateAccessKeyCode()) }
    var label by remember { mutableStateOf("") }
    var maxActivationsText by remember { mutableStateOf("5") }
    var selectedDurationIndex by remember { mutableIntStateOf(2) } // 7 days default
    var selectedScope by remember { mutableStateOf("all") }
    var editingKey by remember { mutableStateOf<AccessKeyItem?>(null) }

    val totalActivations = keys.sumOf { it.usedCount }
    val activeKeysCount = keys.count { it.isCurrentlyValid() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Live metrics strip
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricTile(
                    title = "Всего ключей",
                    value = "${keys.size}",
                    accent = CyanSecondary,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    title = "Активных",
                    value = "$activeKeysCount",
                    accent = EmeraldPrimary,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    title = "Активаций (Live)",
                    value = "$totalActivations",
                    accent = AmberKey,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Create new key card
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
                    Text(
                        text = "Создать ключ доступа",
                        style = MaterialTheme.typography.titleMedium
                    )

                    OutlinedTextField(
                        value = customCode,
                        onValueChange = { customCode = it.uppercase() },
                        label = { Text("Код ключа") },
                        trailingIcon = {
                            IconButton(onClick = { customCode = SecurityUtils.generateAccessKeyCode() }) {
                                Icon(Icons.Default.AutoFixHigh, contentDescription = "Сгенерировать код", tint = EmeraldPrimary)
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_new_key_code_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = label,
                            onValueChange = { label = it },
                            label = { Text("Метка (опц.)") },
                            placeholder = { Text("VIP / Раздача") },
                            singleLine = true,
                            modifier = Modifier.weight(1.4f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                            value = maxActivationsText,
                            onValueChange = { maxActivationsText = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Лимит") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .weight(0.8f)
                                .testTag("admin_key_max_activations_input"),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    Text(
                        text = "Срок действия ключа:",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(DURATION_PRESETS.indices.toList()) { idx ->
                            FilterChip(
                                selected = selectedDurationIndex == idx,
                                onClick = { selectedDurationIndex = idx },
                                label = { Text(DURATION_PRESETS[idx].label) }
                            )
                        }
                    }

                    val keyPosts = posts.filter { it.requiresKey }
                    if (keyPosts.isNotEmpty()) {
                        Text(
                            text = "Область доступа:",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                FilterChip(
                                    selected = selectedScope == "all",
                                    onClick = { selectedScope = "all" },
                                    label = { Text("Весь эксклюзив") }
                                )
                            }
                            items(keyPosts) { post ->
                                FilterChip(
                                    selected = selectedScope == post.id,
                                    onClick = { selectedScope = post.id },
                                    label = { Text(post.title.take(22)) }
                                )
                            }
                        }
                    }

                    if (!errorMessage.isNullOrBlank()) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = CrimsonError
                        )
                    }

                    Button(
                        onClick = {
                            val maxAct = (maxActivationsText.toLongOrNull() ?: 1L).coerceIn(1L, 100000L)
                            onCreateKey(
                                customCode,
                                label,
                                selectedScope,
                                maxAct,
                                DURATION_PRESETS[selectedDurationIndex].millis
                            )
                            customCode = SecurityUtils.generateAccessKeyCode()
                        },
                        enabled = !isBusy && customCode.trim().length >= 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("admin_create_key_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldPrimary,
                            contentColor = OnEmeraldPrimary
                        )
                    ) {
                        Icon(Icons.Default.Key, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Выпустить ключ", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Text(
                text = "Управление ключами (в реальном времени)",
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (keys.isEmpty()) {
            item {
                Surface(
                    color = ObsidianSurface,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, ObsidianOutline.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "Ключи ещё не созданы. Выпустите первый ключ выше.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(keys, key = { it.code }) { key ->
                val isValid = key.isCurrentlyValid()
                val statusText = when {
                    !key.active -> "Отключён"
                    key.isExpired() -> "Истёк"
                    key.isExhausted() -> "Исчерпан"
                    else -> "Активен"
                }
                val statusBg = when {
                    isValid -> EmeraldContainer
                    !key.active -> CrimsonContainer
                    else -> AmberContainer
                }
                val statusFg = when {
                    isValid -> OnEmeraldContainer
                    !key.active -> OnCrimsonContainer
                    else -> OnAmberContainer
                }
                val ratio = if (key.maxActivations > 0) {
                    (key.usedCount.toFloat() / key.maxActivations.toFloat()).coerceIn(0f, 1f)
                } else 0f

                Surface(
                    color = ObsidianSurfaceHigh,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, if (isValid) EmeraldPrimary.copy(alpha = 0.4f) else ObsidianOutline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onInspectKey(key.code) }
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = key.code,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontFamily = JetBrainsMonoFamily,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = buildString {
                                        if (key.label.isNotBlank()) append("${key.label} • ")
                                        append(formatExpiryCountdown(key.expiresAt))
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                            Surface(color = statusBg, shape = RoundedCornerShape(8.dp)) {
                                Text(
                                    text = statusText,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = statusFg,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { ratio },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = if (isValid) EmeraldPrimary else AmberKey,
                            trackColor = ObsidianSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Использовано: ${key.usedCount} / ${key.maxActivations} • Автор: ${key.createdBy}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            val scopeTitle = if (key.scope == "all") {
                                "Весь VIP-контент"
                            } else {
                                posts.firstOrNull { it.id == key.scope }?.title?.let { "Файл: ${it.take(22)}" }
                                    ?: "Только 1 файл"
                            }
                            Text(
                                text = scopeTitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = CyanSecondary
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(
                                    checked = key.active,
                                    onCheckedChange = { onToggleKeyActive(key) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = OnEmeraldPrimary,
                                        checkedTrackColor = EmeraldPrimary
                                    )
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (key.active) "Вкл" else "Пауза",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextSecondary
                                )
                            }

                            Row {
                                IconButton(onClick = {
                                    copyTextToClipboard(context, "AccessKey", key.code)
                                    onShowMessage("Ключ ${key.code} скопирован")
                                }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Копировать", tint = EmeraldPrimary)
                                }
                                IconButton(onClick = { onInspectKey(key.code) }) {
                                    Icon(Icons.Default.History, contentDescription = "Лог активаций", tint = CyanSecondary)
                                }
                                IconButton(onClick = { editingKey = key }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Лимиты", tint = AmberKey)
                                }
                                IconButton(onClick = { onArchiveKey(key.code) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = CrimsonError)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (editingKey != null) {
        val current = editingKey!!
        var editMaxText by remember(current.code) { mutableStateOf(current.maxActivations.toString()) }
        var editDurIdx by remember(current.code) { mutableIntStateOf(2) }
        AlertDialog(
            onDismissRequest = { editingKey = null },
            containerColor = ObsidianSurface,
            title = { Text("Изменить ключ ${current.code}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editMaxText,
                        onValueChange = { editMaxText = it.filter { c -> c.isDigit() } },
                        label = { Text("Макс. активаций (текущих: ${current.usedCount})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    Text("Новый срок действия (от текущего момента):", style = MaterialTheme.typography.labelMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(DURATION_PRESETS.indices.toList()) { idx ->
                            FilterChip(
                                selected = editDurIdx == idx,
                                onClick = { editDurIdx = idx },
                                label = { Text(DURATION_PRESETS[idx].label) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val newMax = (editMaxText.toLongOrNull() ?: current.maxActivations)
                        .coerceAtLeast(current.usedCount.coerceAtLeast(1L))
                    onUpdateKeyLimits(current.code, newMax, DURATION_PRESETS[editDurIdx].millis)
                    editingKey = null
                }) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingKey = null }) { Text("Отмена") }
            }
        )
    }

    if (inspectedKeyCode != null) {
        ModalBottomSheet(
            onDismissRequest = { onInspectKey(null) },
            containerColor = ObsidianSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Live-отслеживание: $inspectedKeyCode",
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = JetBrainsMonoFamily),
                    color = EmeraldPrimary
                )
                Text(
                    text = "История активаций обновляется в реальном времени",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (inspectedActivations.isEmpty()) {
                    Text(
                        text = "Пока нет ни одной активации этого ключа.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted,
                        modifier = Modifier.padding(vertical = 20.dp)
                    )
                } else {
                    inspectedActivations.take(20).forEach { act ->
                        Surface(
                            color = ObsidianSurfaceHigh,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Smartphone,
                                    contentDescription = null,
                                    tint = CyanSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = act.deviceLabel,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = "ID: ${act.deviceHash.take(10)} • ${formatTimestamp(act.activatedAt)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun MetricTile(
    title: String,
    value: String,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = ObsidianSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = accent
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
    }
}
