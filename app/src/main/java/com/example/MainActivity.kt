package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.DynamicFeed
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.notifications.NinjagoNotificationHelper
import com.example.data.prefs.AppPreferences
import com.example.data.repository.HubRepository
import com.example.ui.screens.AccessKeyScreen
import com.example.ui.screens.AdminDashboardScreen
import com.example.ui.screens.AdminLoginDialog
import com.example.ui.screens.FeedScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.NinjagoHubTheme
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.OnEmeraldContainer
import com.example.ui.theme.TextMuted
import com.example.ui.viewmodel.HubViewModel
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        NinjagoNotificationHelper.ensureChannel(applicationContext)

        val initialPostId = intent?.getStringExtra(NinjagoNotificationHelper.EXTRA_POST_ID)

        setContent {
            NinjagoHubTheme {
                NinjagoHubApp(initialOpenPostId = initialPostId)
            }
        }
    }
}

@Composable
fun NinjagoHubApp(initialOpenPostId: String?) {
    val viewModel: HubViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                val repo = HubRepository(db)
                val prefs = AppPreferences(app.applicationContext)
                HubViewModel(repo, prefs, app.applicationContext)
            }
        }
    )

    val onboardingCompleted by viewModel.onboardingCompleted.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMessage) {
        val msg = snackbarMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    when (onboardingCompleted) {
        null -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = EmeraldPrimary)
            }
        }
        false -> {
            Scaffold(
                containerColor = ObsidianBg,
                snackbarHost = { SnackbarHost(snackbarHostState) }
            ) { innerPadding ->
                Box(modifier = Modifier.padding(innerPadding)) {
                    OnboardingScreen(
                        onContinue = { viewModel.completeOnboarding() },
                        onShowMessage = { viewModel.showMessage(it) }
                    )
                }
            }
        }
        true -> {
            MainContentShell(
                viewModel = viewModel,
                snackbarHostState = snackbarHostState,
                initialOpenPostId = initialOpenPostId
            )
        }
    }
}

@Composable
private fun MainContentShell(
    viewModel: HubViewModel,
    snackbarHostState: SnackbarHostState,
    initialOpenPostId: String?
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showAdminLoginDialog by rememberSaveable { mutableStateOf(false) }
    var showAdminDashboard by rememberSaveable { mutableStateOf(false) }
    var pendingPostId by remember { mutableStateOf(initialOpenPostId) }

    val postsState by viewModel.postsState.collectAsStateWithLifecycle()
    val filteredPostsState by viewModel.filteredPostsState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val accessFilter by viewModel.accessFilter.collectAsStateWithLifecycle()
    val activeKeyStatus by viewModel.activeKeyStatus.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val downloadedRecords by viewModel.downloadedRecords.collectAsStateWithLifecycle()
    val unreadNotifications by viewModel.unreadNotifications.collectAsStateWithLifecycle()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsStateWithLifecycle()
    val isRedeemingKey by viewModel.isRedeemingKey.collectAsStateWithLifecycle()
    val keyFeedback by viewModel.keyFeedback.collectAsStateWithLifecycle()

    val adminSession by viewModel.adminSession.collectAsStateWithLifecycle()
    val isAdminBusy by viewModel.isAdminBusy.collectAsStateWithLifecycle()
    val adminError by viewModel.adminError.collectAsStateWithLifecycle()
    val lockoutSeconds by viewModel.lockoutRemainingSeconds.collectAsStateWithLifecycle()
    val adminKeys by viewModel.adminKeysState.collectAsStateWithLifecycle()
    val adminRoster by viewModel.adminRosterState.collectAsStateWithLifecycle()
    val inspectedKeyCode by viewModel.inspectedKeyCode.collectAsStateWithLifecycle()
    val inspectedActivations by viewModel.inspectedKeyActivations.collectAsStateWithLifecycle()

    if (showAdminDashboard && adminSession != null) {
        val allPosts = (postsState as? com.example.data.model.UiState.Success)?.data ?: emptyList()
        AdminDashboardScreen(
            session = adminSession!!,
            posts = allPosts,
            keys = adminKeys,
            roster = adminRoster,
            inspectedKeyCode = inspectedKeyCode,
            inspectedActivations = inspectedActivations,
            isBusy = isAdminBusy,
            errorMessage = adminError,
            onReadFileUri = viewModel::readFileFromUri,
            onPublishPost = viewModel::publishPost,
            onDeletePost = viewModel::deletePost,
            onCreateKey = { customCode, label, scope, maxAct, dur ->
                viewModel.createAccessKey(customCode, label, scope, maxAct, dur) {}
            },
            onToggleKeyActive = viewModel::toggleKeyActive,
            onUpdateKeyLimits = viewModel::updateKeyLimits,
            onArchiveKey = viewModel::archiveKey,
            onInspectKey = viewModel::inspectKeyActivations,
            onAddAdmin = viewModel::addAdminAccount,
            onToggleAdminActive = viewModel::toggleAdminActive,
            onLogout = {
                viewModel.logoutAdmin()
                showAdminDashboard = false
            },
            onClose = { showAdminDashboard = false },
            onShowMessage = viewModel::showMessage
        )
        return
    }

    Scaffold(
        containerColor = ObsidianBg,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(containerColor = ObsidianSurface) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    modifier = Modifier.testTag("nav_feed_tab"),
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == 0) Icons.Filled.DynamicFeed else Icons.Outlined.DynamicFeed,
                            contentDescription = "Лента"
                        )
                    },
                    label = { Text("Лента") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = EmeraldPrimary,
                        selectedTextColor = OnEmeraldContainer,
                        indicatorColor = EmeraldContainer,
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    modifier = Modifier.testTag("nav_keys_tab"),
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == 1) Icons.Filled.Key else Icons.Outlined.Key,
                            contentDescription = "Ключи"
                        )
                    },
                    label = { Text("Ключи") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = EmeraldPrimary,
                        selectedTextColor = OnEmeraldContainer,
                        indicatorColor = EmeraldContainer,
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    modifier = Modifier.testTag("nav_settings_tab"),
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == 2) Icons.Filled.Settings else Icons.Outlined.Settings,
                            contentDescription = "Настройки"
                        )
                    },
                    label = { Text("Настройки") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = EmeraldPrimary,
                        selectedTextColor = OnEmeraldContainer,
                        indicatorColor = EmeraldContainer,
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedTab) {
                0 -> FeedScreen(
                    postsState = filteredPostsState,
                    searchQuery = searchQuery,
                    selectedCategory = selectedCategory,
                    accessFilter = accessFilter,
                    activeKeyStatus = activeKeyStatus,
                    downloadProgress = downloadProgress,
                    downloadedRecords = downloadedRecords,
                    unreadNotifications = unreadNotifications,
                    initialOpenPostId = pendingPostId,
                    onClearInitialPostId = { pendingPostId = null },
                    onSearchChange = viewModel::updateSearchQuery,
                    onCategorySelect = viewModel::selectCategory,
                    onAccessFilterSelect = viewModel::selectAccessFilter,
                    onDownloadPost = viewModel::downloadPostFile,
                    onRedeemKey = viewModel::redeemKey,
                    onMarkNotificationsRead = viewModel::markNotificationsRead,
                    canAccessPost = viewModel::canAccessPost,
                    onNavigateToKeys = { selectedTab = 1 }
                )
                1 -> AccessKeyScreen(
                    activeKeyStatus = activeKeyStatus,
                    isRedeeming = isRedeemingKey,
                    keyFeedback = keyFeedback,
                    downloadedRecords = downloadedRecords,
                    onRedeemKey = viewModel::redeemKey,
                    onClearKey = viewModel::clearSavedKey
                )
                2 -> SettingsScreen(
                    notificationsEnabled = notificationsEnabled,
                    adminSession = adminSession,
                    onToggleNotifications = viewModel::setNotificationsEnabled,
                    onSixTapBuildTrigger = {
                        viewModel.clearAdminError()
                        showAdminLoginDialog = true
                    },
                    onOpenAdminDashboard = { showAdminDashboard = true },
                    onLogoutAdmin = {
                        viewModel.logoutAdmin()
                        showAdminDashboard = false
                    },
                    onShowMessage = viewModel::showMessage
                )
            }
        }
    }

    if (showAdminLoginDialog) {
        AdminLoginDialog(
            isBusy = isAdminBusy,
            errorMessage = adminError,
            lockoutRemainingSeconds = lockoutSeconds,
            onDismiss = {
                showAdminLoginDialog = false
                viewModel.clearAdminError()
            },
            onClearError = viewModel::clearAdminError,
            onSubmit = { login, password ->
                viewModel.loginAdmin(login, password) {
                    showAdminLoginDialog = false
                    showAdminDashboard = true
                }
            }
        )
    }
}
