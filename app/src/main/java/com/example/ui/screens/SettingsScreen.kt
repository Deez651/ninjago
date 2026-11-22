package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.AdminSessionState
import com.example.data.notifications.NinjagoNotificationHelper
import com.example.ui.components.TelegramChannelCard
import com.example.ui.theme.CyanSecondary
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.ObsidianOutline
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceHigh
import com.example.ui.theme.OnEmeraldPrimary
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary

@Composable
fun SettingsScreen(
    notificationsEnabled: Boolean,
    adminSession: AdminSessionState?,
    onToggleNotifications: (Boolean) -> Unit,
    onSixTapBuildTrigger: () -> Unit,
    onOpenAdminDashboard: () -> Unit,
    onLogoutAdmin: () -> Unit,
    onShowMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val mainTelegramUrl = stringResource(R.string.telegram_main_url)
    val secondTelegramUrl = stringResource(R.string.telegram_second_url)
    val buildVersionValue = stringResource(R.string.build_version_value)

    var buildTapCount by remember { mutableIntStateOf(0) }
    var lastTapMillis by remember { mutableLongStateOf(0L) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onToggleNotifications(true)
            onShowMessage("Уведомления о новых публикациях включены")
        } else {
            onToggleNotifications(false)
            onShowMessage("Разрешение на уведомления отклонено системой")
        }
    }

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
                    text = "Настройки",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Уведомления, каналы сообщества и сведения о сборке",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }

            // Notifications setting
            item {
                Surface(
                    color = ObsidianSurface,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, ObsidianOutline)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Уведомления о новых постах",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Системное оповещение при выходе новых файлов и скриптов",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        Switch(
                            checked = notificationsEnabled,
                            onCheckedChange = { enable ->
                                if (enable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    !NinjagoNotificationHelper.hasNotificationPermission(context)
                                ) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    onToggleNotifications(enable)
                                }
                            },
                            modifier = Modifier.testTag("notifications_switch"),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = OnEmeraldPrimary,
                                checkedTrackColor = EmeraldPrimary
                            )
                        )
                    }
                }
            }

            // Telegram channels
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
                            text = "Наши Telegram-каналы",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Возраждаем канал вместе! Подписывайтесь на оба канала для получения свежих ключей и обновлений.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        TelegramChannelCard(
                            title = "Основной канал NinjagoScript",
                            handle = "@NinjagoScript",
                            url = mainTelegramUrl,
                            accentColor = EmeraldPrimary,
                            testTag = "settings_telegram_main",
                            onCopied = onShowMessage
                        )
                        TelegramChannelCard(
                            title = "Второй канал EstaxCheats",
                            handle = "@EstaxCheats",
                            url = secondTelegramUrl,
                            accentColor = CyanSecondary,
                            testTag = "settings_telegram_second",
                            onCopied = onShowMessage
                        )
                    }
                }
            }

            // Active Admin Session card (only visible if already logged in as Admin!)
            if (adminSession != null) {
                item {
                    Surface(
                        color = EmeraldContainer.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, EmeraldPrimary)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AdminPanelSettings,
                                    contentDescription = null,
                                    tint = EmeraldPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Активна сессия админа: ${adminSession.login}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = onOpenAdminDashboard,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("open_admin_dashboard_button"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = EmeraldPrimary,
                                        contentColor = OnEmeraldPrimary
                                    )
                                ) {
                                    Text("Панель управления")
                                }
                                OutlinedButton(onClick = onLogoutAdmin) {
                                    Text("Выйти")
                                }
                            }
                        }
                    }
                }
            }

            // Build version (6-tap secret admin trigger)
            item {
                Surface(
                    color = ObsidianSurfaceHigh,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, ObsidianOutline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .testTag("build_version_item")
                        .clickable {
                            val now = System.currentTimeMillis()
                            if (now - lastTapMillis > 5000L) {
                                buildTapCount = 1
                            } else {
                                buildTapCount += 1
                            }
                            lastTapMillis = now

                            if (buildTapCount >= 6) {
                                buildTapCount = 0
                                if (adminSession != null) {
                                    onOpenAdminDashboard()
                                } else {
                                    onSixTapBuildTrigger()
                                }
                            } else if (buildTapCount >= 4) {
                                val left = 6 - buildTapCount
                                onShowMessage("До панели входа осталось нажатий: $left")
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = "Версия сборки",
                            tint = TextMuted
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.build_version_label),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = buildVersionValue,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}
