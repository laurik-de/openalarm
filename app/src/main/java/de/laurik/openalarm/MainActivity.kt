package de.laurik.openalarm

import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import android.app.AlarmManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.activity.compose.BackHandler
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.collections.associate
import de.laurik.openalarm.ui.theme.effectsSpring
import de.laurik.openalarm.ui.theme.bounce
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.sp
import de.laurik.openalarm.ui.theme.bounceClickable

class MainActivity : ComponentActivity() {
    private var pendingNavTarget by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        createNotificationChannels()
        checkPermissions()

        handleIntent(intent)

        setContent {
            val settingsViewModel = viewModel<SettingsViewModel>()
            val themeMode by settingsViewModel.themeMode.collectAsState()
            val isPureBlack by settingsViewModel.isPureBlack.collectAsState()
            var showSettings by remember { mutableStateOf(false) }

            // Navigation State (Sync with Pager)
            val pagerState = rememberPagerState { Screen.entries.size }
            val scope = rememberCoroutineScope()

            // Listen for external navigation requests (like from notifications)
            LaunchedEffect(pendingNavTarget) {
                pendingNavTarget?.let { target ->
                    android.util.Log.d("OpenAlarm", "Navigating to $target")
                    val targetScreen = if (target == "TIMER") Screen.TIMER else Screen.ALARM
                    pagerState.animateScrollToPage(targetScreen.ordinal)
                    pendingNavTarget = null // Clear after handling
                }
            }

            BackHandler(enabled = showSettings) {
                showSettings = false
            }

            val systemDark = isSystemInDarkTheme()
            val useDarkIcons = when (themeMode) {
                AppThemeMode.LIGHT -> true
                AppThemeMode.DARK -> false
                AppThemeMode.SYSTEM -> !systemDark
            }

            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val window = (view.context as android.app.Activity).window
                    WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = useDarkIcons
                }
            }

            de.laurik.openalarm.ui.theme.OpenAlarmTheme(themeMode = themeMode, isPureBlack = isPureBlack) {
                MainContent(settingsViewModel, pagerState)
                CheckSystemPermissions(settingsViewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val target = intent.getStringExtra("NAVIGATE_TO")
        android.util.Log.d("OpenAlarm", "handleIntent: target=$target")
        if (target != null) {
            pendingNavTarget = target
            intent.removeExtra("NAVIGATE_TO") // Clean up so it doesn't re-trigger on config change
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            AlarmRepository.ensureLoaded(this@MainActivity)
            AlarmRepository.cleanupStaleInterruptedItems(this@MainActivity)
            NotificationRenderer.refreshAll(this@MainActivity)
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            // 1. RINGING (Max Priority)
            val ringChannel = NotificationChannel(
                "ALARM_CHANNEL_ID",
                getString(R.string.channel_alarm_ringing),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setSound(null, null)
                description = getString(R.string.channel_alarm_desc)
                enableVibration(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setBypassDnd(true)
            }

            // 2. STATUS (Snooze / Next Alarm)
            // CHANGE: IMPORTANCE_LOW -> IMPORTANCE_DEFAULT
            val statusChannel = NotificationChannel(
                "STATUS_CHANNEL_ID",
                getString(R.string.channel_status),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                setShowBadge(false)
                description = getString(R.string.channel_status_desc)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setBypassDnd(true) // Ensure Snooze shows through DND
                setSound(null, null) // Silent but visible
            }

            // 3. ACTIVE TIMER
            val activeTimerChannel = NotificationChannel(
                "ACTIVE_TIMER_CHANNEL_ID",
                getString(R.string.channel_active_timer),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setSound(null, null)
                setShowBadge(false)
                description = getString(R.string.channel_active_timer_desc)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setBypassDnd(true)
            }

            manager.createNotificationChannel(activeTimerChannel)
            manager.createNotificationChannel(ringChannel)
            manager.createNotificationChannel(statusChannel)
        }
    }

    private fun checkPermissions() {
        // Notification Permission (Android 13+)
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                val launcher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {}
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

@Composable
fun CheckSystemPermissions(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val packageName = context.packageName

    var showExactAlarmDialog by remember { mutableStateOf(false) }

    // Full Screen Intent Check (Android 14+)
    var showFullScreenDialog by remember { mutableStateOf(false) }
    val fullScreenPermissionShown by viewModel.fullScreenPermissionShown.collectAsStateWithLifecycle()

    // Xiaomi Specific Guidance
    var showXiaomiDialog by remember { mutableStateOf(false) }
    val prefs = context.getSharedPreferences("openalarm_prefs", Context.MODE_PRIVATE)

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                showExactAlarmDialog = true
            }
        }
        
        // Android 14 full screen intent property
        if (Build.VERSION.SDK_INT >= 34) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!nm.canUseFullScreenIntent() && !fullScreenPermissionShown) {
                showFullScreenDialog = true
            }
        }

        // Xiaomi detection
        val manufacturer = Build.MANUFACTURER.lowercase()
        val isXiaomi = manufacturer.contains("xiaomi") || manufacturer.contains("poco") || manufacturer.contains("redmi")
        val alreadyShown = prefs.getBoolean("xiaomi_guidance_shown", false)
        if (isXiaomi && !alreadyShown) {
            showXiaomiDialog = true
        }
    }

    if (showExactAlarmDialog) {
        AlertDialog(
            onDismissRequest = { showExactAlarmDialog = false },
            title = { Text(stringResource(R.string.dialog_title_permission_required)) },
            text = { Text(stringResource(R.string.dialog_msg_permission_exact_alarm)) },
            confirmButton = {
                TextButton(onClick = {
                    showExactAlarmDialog = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                            data = Uri.parse("package:$packageName")
                        }
                        context.startActivity(intent)
                    }
                }) { Text(stringResource(R.string.action_grant)) }
            },
            dismissButton = {
                TextButton(onClick = { showExactAlarmDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    if (showFullScreenDialog) {
        AlertDialog(
            onDismissRequest = { 
                showFullScreenDialog = false 
                viewModel.setFullScreenPermissionShown(true)
            },
            title = { Text(stringResource(R.string.dialog_title_permission_required)) },
            text = { Text(stringResource(R.string.dialog_msg_permission_full_screen)) },
            confirmButton = {
                TextButton(onClick = {
                    showFullScreenDialog = false
                    viewModel.setFullScreenPermissionShown(true)
                    if (Build.VERSION.SDK_INT >= 34) {
                        val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                            data = Uri.parse("package:$packageName")
                        }
                        context.startActivity(intent)
                    }
                }) { Text(stringResource(R.string.action_grant)) }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showFullScreenDialog = false 
                    viewModel.setFullScreenPermissionShown(true)
                }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    if (showXiaomiDialog) {
        AlertDialog(
            onDismissRequest = { showXiaomiDialog = false },
            title = { Text(stringResource(R.string.dialog_title_xiaomi_fix)) },
            text = { Text(stringResource(R.string.dialog_msg_xiaomi_fix)) },
            confirmButton = {
                TextButton(onClick = {
                    showXiaomiDialog = false
                    prefs.edit().putBoolean("xiaomi_guidance_shown", true).apply()
                    try {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:$packageName")
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }) { Text(stringResource(R.string.action_open_settings)) }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showXiaomiDialog = false 
                    prefs.edit().putBoolean("xiaomi_guidance_shown", true).apply()
                }) { Text(stringResource(R.string.action_dismiss)) }
            }
        )
    }

}

enum class Screen { ALARM, TIMER, SETTINGS }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainContent(settingsViewModel: SettingsViewModel, pagerState: PagerState) {
    val currentScreen = Screen.entries[pagerState.currentPage]
    val scope = rememberCoroutineScope()
    val dashboardViewModel: DashboardViewModel = viewModel()
    
    val activeTimers = dashboardViewModel.activeTimers
    val currentTime by dashboardViewModel.currentTime.collectAsStateWithLifecycle()

    var showAlarmDialogForNew by remember { mutableStateOf(false) }
    var triggerStartTimer by remember { mutableLongStateOf(0L) }
    var globalNumpad by remember { mutableStateOf<(@Composable () -> Unit)?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                // Status Bar (Keep existing logic if any)
            },
            bottomBar = {
                Column {
                    // Small overlay bar for current timer (Keep it)
                    val activeTimers = dashboardViewModel.activeTimers
                    val currentTime by dashboardViewModel.currentTime.collectAsStateWithLifecycle()
                    
                    if (activeTimers.isNotEmpty()) {
                        val timer = activeTimers.first()
                        val diff = if (timer.isPaused) timer.remainingMillis else timer.endTime - currentTime
                        val progress = if (timer.totalDuration > 0) 1f - (diff.toFloat() / timer.totalDuration) else 0f
                        val isPaused = timer.isPaused
                        
                        // Always orange, but dimmed when paused as requested
                        val baseColor = Color(0xFFE65100)
                        val barOpacity = if (isPaused) 0.6f else 1f
                        
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .bounceClickable { scope.launch { pagerState.animateScrollToPage(Screen.TIMER.ordinal) } },
                            color = baseColor.copy(alpha = barOpacity),
                            tonalElevation = 4.dp
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                // THE TRACK (Remaining time) - dimmed version of the orange
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.3f))
                                )
                                // THE PROGRESS (Passed time) - solid pop on the dimmed track
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                                        .background(Color.White.copy(alpha = 0.35f))
                                )
                                Row(
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        stringResource(R.string.title_timer).uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White,
                                        letterSpacing = 1.2.sp
                                    )
                                    Text(
                                        if (diff > 0L) AlarmUtils.formatTimerTime(diff) else stringResource(R.string.timer_ringing),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                    
                    NavigationBar {
                        NavigationBarItem(
                            icon = { Icon(Icons.Default.Alarm, null) },
                            label = { Text(stringResource(R.string.title_alarm)) },
                            selected = currentScreen == Screen.ALARM,
                            onClick = { scope.launch { pagerState.animateScrollToPage(Screen.ALARM.ordinal) } }
                        )
                        NavigationBarItem(
                            icon = { Icon(Icons.Default.Timer, null) },
                            label = { Text(stringResource(R.string.title_timer)) },
                            selected = currentScreen == Screen.TIMER,
                            onClick = { scope.launch { pagerState.animateScrollToPage(Screen.TIMER.ordinal) } }
                        )
                        NavigationBarItem(
                            icon = { Icon(Icons.Default.Settings, null) },
                            label = { Text(stringResource(R.string.title_settings)) },
                            selected = currentScreen == Screen.SETTINGS,
                            onClick = { scope.launch { pagerState.animateScrollToPage(Screen.SETTINGS.ordinal) } }
                        )
                    }
                }
            },
            floatingActionButton = {
                // FAB moved into AlarmScreen to swipe with it
            }
        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues)) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 2,
                    userScrollEnabled = globalNumpad == null
                ) { page ->
                    when (Screen.entries[page]) {
                        Screen.ALARM -> AlarmScreen(
                            viewModel = dashboardViewModel,
                            settingsViewModel = settingsViewModel,
                            triggerCreateAlarm = showAlarmDialogForNew,
                            onAlarmDialogDismiss = { showAlarmDialogForNew = false },
                            isNumpadVisible = globalNumpad != null
                        )
                        Screen.TIMER -> TimerScreen(dashboardViewModel, settingsViewModel.timerPresets.collectAsState().value, triggerStartTimer, onNumpadChange = { globalNumpad = it })
                        Screen.SETTINGS -> SettingsScreen(settingsViewModel, onClose = { scope.launch { pagerState.animateScrollToPage(Screen.ALARM.ordinal) } })
                    }
                }
            }
        }

        // Global Numpad Overlay (Non-Popup)
        AnimatedVisibility(
            visible = globalNumpad != null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut()
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                tonalElevation = 8.dp,
                shadowElevation = 16.dp
            ) {
                Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
                    globalNumpad?.invoke()
                }
            }
        }
    }
}
@Composable
fun ReachabilityHeader(nextAlarm: NextAlarmInfo?, expansionProgress: Float) {
    val alpha = 1f
    val nextAlarmAlpha = (expansionProgress * 2f - 1f).coerceIn(0f, 1f)
    val scale = 0.75f + (0.25f * expansionProgress)
    val warp = 1f + (0.05f * (1f - expansionProgress))

    val horizontalBias = -1f + (1f * expansionProgress)
    val verticalBias = -1f + (0.7f * expansionProgress) 
    val alignment = BiasAlignment(horizontalBias, verticalBias)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(maxOf(72.dp, 240.dp * expansionProgress))
            .statusBarsPadding()
    ) {
        // App Name - Permanent
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displaySmall.copy(
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                textAlign = androidx.compose.ui.text.style.TextAlign.Start
            ),
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
            modifier = Modifier
                .align(alignment)
                .wrapContentHeight(unbounded = true) // Crucial to prevent descender clipping
                .graphicsLayer {
                    this.scaleX = scale * warp
                    this.scaleY = scale
                    this.transformOrigin = TransformOrigin(0f, 0f)
                }
        )
        
        // Next Alarm Info - Only visible when expanded
        if (expansionProgress > 0.5f) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .graphicsLayer { this.alpha = nextAlarmAlpha }
                    .padding(bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (nextAlarm == null) {
                    Text(
                        text = stringResource(R.string.no_active_alarms),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    // Top Line: Count Down (Primary)
                    Text(
                        text = stringResource(R.string.fmt_alarm_in, nextAlarm.countdownString),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Spacer(modifier = Modifier.height(2.dp))
                    
                    // Bottom Line: Contextual Info (Secondary)
                    Text(
                        text = buildString {
                            // "At/Um [Time/Date]"
                            append(nextAlarm.timeString)
                            if (!nextAlarm.label.isNullOrBlank()) {
                                append("  •  ")
                                append(nextAlarm.label)
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlarmScreen(
    viewModel: DashboardViewModel,
    settingsViewModel: SettingsViewModel,
    triggerCreateAlarm: Boolean = false,
    onAlarmDialogDismiss: () -> Unit = {},
    isNumpadVisible: Boolean = false
) {
    val context = LocalContext.current

    // Collect preset settings
    val quickAdjustPresets by settingsViewModel.quickAdjustPresets.collectAsState()

    // --- STATE ---
    var editingAlarm by remember { mutableStateOf<AlarmItem?>(null) }
    var isCreatingNew by remember { mutableStateOf(false) }
    var showDatePickerForAlarm by remember { mutableStateOf<AlarmItem?>(null) }
    var showDatePickerForGroup by remember { mutableStateOf<AlarmGroup?>(null) }

    // Reachability Expansion State
    val expansion = remember { androidx.compose.animation.core.Animatable(1f) }
    val scope = rememberCoroutineScope()
    val scrollState = rememberLazyListState()

    // Detect scroll to contract/expand
    val nestedScrollConnection = remember {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                // If scrolling down the list (available.y < 0) and header is expanded, contract it
                if (expansion.value > 0f && available.y < 0) {
                    val delta = available.y / 500f
                    scope.launch {
                        expansion.snapTo((expansion.value + delta).coerceIn(0f, 1f))
                    }
                    // Consume only if we are still contracting
                    return if (expansion.value > 0f) available.copy(x = 0f) else androidx.compose.ui.geometry.Offset.Zero
                }
                return super.onPreScroll(available, source)
            }

            override fun onPostScroll(
                consumed: androidx.compose.ui.geometry.Offset,
                available: androidx.compose.ui.geometry.Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource
            ): androidx.compose.ui.geometry.Offset {
                // If scrolling up the list (available.y > 0) and at top, expand it
                if (available.y > 0 && scrollState.firstVisibleItemIndex == 0 && scrollState.firstVisibleItemScrollOffset == 0) {
                    if (expansion.value < 1f) {
                        val delta = available.y / 500f
                        scope.launch {
                            expansion.snapTo((expansion.value + delta).coerceIn(0f, 1f))
                        }
                        return available
                    }
                }
                return super.onPostScroll(consumed, available, source)
            }

            override suspend fun onPostFling(consumed: androidx.compose.ui.unit.Velocity, available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                if (expansion.value > 0.5f) {
                    expansion.animateTo(1f, spring(stiffness = Spring.StiffnessLow))
                } else {
                    expansion.animateTo(0f, spring(stiffness = Spring.StiffnessLow))
                }
                return super.onPostFling(consumed, available)
            }
        }
    }

    // FAB Trigger Handling
    LaunchedEffect(triggerCreateAlarm) {
        if (triggerCreateAlarm) {
            val now = Calendar.getInstance()
            editingAlarm = settingsViewModel.createDefaultAlarm(
                now.get(Calendar.HOUR_OF_DAY),
                now.get(Calendar.MINUTE)
            )
            isCreatingNew = true
            onAlarmDialogDismiss()
        }
    }

    // Group Dialogs
    var groupToEdit by remember { mutableStateOf<AlarmGroup?>(null) }
    var groupToAdjust by remember { mutableStateOf<AlarmGroup?>(null) }
    var groupToDelete by remember { mutableStateOf<AlarmGroup?>(null) }

    // --- DATA FILTERING ---
    // 1. Get the default group (fallback safe)
    val defaultGroup = viewModel.groups.find { it.id == "default" }
        ?: viewModel.groups.find { it.name == "Default" }

    // 2. Split alarms: Default vs Custom Groups
    // Smart sorting: Disabled alarms last, enabled alarms by actual next occurrence time
    val flatAlarms = defaultGroup?.alarms?.sortedWith(
        compareBy<AlarmItem> { !it.isEnabled }
            .thenBy { alarm ->
                if (alarm.isEnabled) {
                    val minTime = if (alarm.skippedUntil > System.currentTimeMillis())
                        alarm.skippedUntil
                    else
                        System.currentTimeMillis()
                    AlarmUtils.getNextOccurrence(
                        alarm.hour, alarm.minute, alarm.daysOfWeek,
                        0, // group offset (0 for default group)
                        alarm.temporaryOverrideTime,
                        alarm.snoozeUntil,
                        minTime
                    )
                } else {
                    Long.MAX_VALUE // Disabled alarms go to end
                }
            }
    ) ?: emptyList()

    // Sort custom groups by their earliest alarm's next occurrence
    val customGroups = viewModel.groups
        .filter { it.id != "default" && it.id != defaultGroup?.id }
        .sortedBy { group ->
            // Find the earliest alarm in this group
            group.alarms
                .filter { it.isEnabled }
                .minOfOrNull { alarm ->
                    val minTime = maxOf(
                        System.currentTimeMillis(),
                        alarm.skippedUntil,
                        group.skippedUntil
                    )
                    AlarmUtils.getNextOccurrence(
                        alarm.hour, alarm.minute, alarm.daysOfWeek,
                        group.offsetMinutes,
                        alarm.temporaryOverrideTime,
                        alarm.snoozeUntil,
                        minTime
                    )
                } ?: Long.MAX_VALUE // Groups with no enabled alarms go last
        }

    // Trigger for FAB from MainContent (We'll use a local state for now and handle it in Scaffold in next step)
    // Actually, I'll move the FAB back into AlarmScreen for now, OR pass a lambda.
    // The request says "only the fab on the bottom right".

    Box(modifier = Modifier
        .fillMaxSize()
        .nestedScroll(nestedScrollConnection)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // REACHABILITY HEADER
            val nextAlarm = AlarmUtils.getNextAlarm(context)
            ReachabilityHeader(nextAlarm, expansion.value)

            // --- MAIN LIST ---
            LazyColumn(
                state = scrollState,
                modifier = Modifier.weight(1f)
            ) {

                // 1. UNGROUPED ALARMS
                if (flatAlarms.isNotEmpty()) {
                    items(items = flatAlarms, key = { it.id }) { alarm ->
                        // Helper to check if this is the "Next Alarm" globally
                        val nextAlarmInfo = AlarmUtils.getNextAlarm(context)
                        val isNext = nextAlarmInfo?.timestamp == AlarmUtils.getNextOccurrence(
                            alarm.hour, alarm.minute, alarm.daysOfWeek, 0,
                            alarm.temporaryOverrideTime, alarm.snoozeUntil,
                            if (alarm.skippedUntil > System.currentTimeMillis()) alarm.skippedUntil else System.currentTimeMillis()
                        )

                        AlarmCard(
                            modifier = Modifier.animateItem(),
                            alarm = alarm,
                            groupOffset = 0,
                            groupSkippedUntil = 0L,
                            isNextAlarm = isNext,
                            isSnoozed = alarm.snoozeUntil != null,
                            snoozeUntil = alarm.snoozeUntil,
                            quickAdjustPresets = quickAdjustPresets,
                            onClick = { editingAlarm = alarm; isCreatingNew = false },
                            onToggleGroup = { viewModel.toggleAlarm(alarm, it) },
                            onDelete = { viewModel.deleteAlarm(alarm) },
                            onSkipNext = {
                                val nextRaw = AlarmUtils.getNextOccurrence(
                                    alarm.hour,
                                    alarm.minute,
                                    alarm.daysOfWeek,
                                    0,
                                    alarm.temporaryOverrideTime,
                                    alarm.snoozeUntil
                                )
                                val baseNext = AlarmUtils.getNextOccurrence(
                                    alarm.hour,
                                    alarm.minute,
                                    alarm.daysOfWeek,
                                    0,
                                    null,
                                    alarm.snoozeUntil
                                )
                                val skipTarget = maxOf(nextRaw, baseNext)
                                val updated = alarm.copy(skippedUntil = skipTarget + 1000)
                                viewModel.saveAlarm(updated, false)
                            },
                            onSkipUntil = { showDatePickerForAlarm = alarm },
                            onClearSkip = {
                                val updated =
                                    alarm.copy(skippedUntil = 0L, temporaryOverrideTime = null)
                                viewModel.saveAlarm(updated, false)
                            },
                            onAdjustTime = { mins -> viewModel.adjustAlarmTime(alarm, mins) },
                            onResetTime = { viewModel.resetAlarmAdjustment(alarm) }
                        )
                    }
                } else if (customGroups.isEmpty()) {
                    item {
                        Box(
                            Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(stringResource(R.string.no_alarms_create_one), color = Color.Gray)
                        }
                    }
                }

                // 2. CUSTOM GROUPS
                items(items = customGroups, key = { "group_${it.id}" }) { group ->
                    GroupCard(
                        modifier = Modifier.animateItem(),
                        group = group,
                        onToggleGroup = { isEnabled ->
                            group.alarms.toList()
                                .forEach { a -> viewModel.toggleAlarm(a, isEnabled) }
                        },
                        onAdjust = { groupToAdjust = group },
                        onEdit = { groupToEdit = group },
                        onSkipNextAll = { viewModel.skipNextAllInGroup(group) },
                        onClearSkipAll = { viewModel.clearSkipAllInGroup(group) },
                        onSkipUntilAll = { showDatePickerForGroup = group },
                        onDelete = { groupToDelete = group },
                        content = {
                            // Smart sorting for group alarms too
                            val groupAlarms = group.alarms.sortedWith(
                                compareBy<AlarmItem> { !it.isEnabled }
                                    .thenBy { alarm ->
                                        if (alarm.isEnabled) {
                                            val minTime = maxOf(
                                                System.currentTimeMillis(),
                                                alarm.skippedUntil,
                                                group.skippedUntil
                                            )
                                            AlarmUtils.getNextOccurrence(
                                                alarm.hour, alarm.minute, alarm.daysOfWeek,
                                                group.offsetMinutes,
                                                alarm.temporaryOverrideTime,
                                                alarm.snoozeUntil,
                                                minTime
                                            )
                                        } else {
                                            Long.MAX_VALUE
                                        }
                                    }
                            )
                            groupAlarms.forEach { alarm ->
                                AlarmCard(
                                    modifier = Modifier.padding(vertical = 2.dp),
                                    alarm = alarm,
                                    groupOffset = group.offsetMinutes,
                                    groupSkippedUntil = group.skippedUntil,
                                    isNextAlarm = false,
                                    isSnoozed = alarm.snoozeUntil != null,
                                    snoozeUntil = alarm.snoozeUntil,
                                    quickAdjustPresets = quickAdjustPresets,
                                    onClick = { editingAlarm = alarm; isCreatingNew = false },
                                    onToggleGroup = { viewModel.toggleAlarm(alarm, it) },
                                    onDelete = { viewModel.deleteAlarm(alarm) },
                                    onSkipNext = {
                                        val nextRaw = AlarmUtils.getNextOccurrence(
                                            alarm.hour,
                                            alarm.minute,
                                            alarm.daysOfWeek,
                                            group.offsetMinutes,
                                            alarm.temporaryOverrideTime,
                                            alarm.snoozeUntil
                                        )
                                        val baseNext = AlarmUtils.getNextOccurrence(
                                            alarm.hour,
                                            alarm.minute,
                                            alarm.daysOfWeek,
                                            group.offsetMinutes,
                                            null,
                                            alarm.snoozeUntil
                                        )
                                        val skipTarget = maxOf(nextRaw, baseNext)
                                        val updated = alarm.copy(skippedUntil = skipTarget + 1000)
                                        viewModel.saveAlarm(updated, false)
                                    },
                                    onSkipUntil = { showDatePickerForAlarm = alarm },
                                    onClearSkip = {
                                        val updated = alarm.copy(
                                            skippedUntil = 0L,
                                            temporaryOverrideTime = null
                                        )
                                        viewModel.saveAlarm(updated, false)
                                    },
                                    onAdjustTime = { viewModel.adjustAlarmTime(alarm, it) },
                                    onResetTime = { viewModel.resetAlarmAdjustment(alarm) }
                                )
                            }
                        }
                    )

                }

                item { Spacer(Modifier.height(80.dp)) }
            }
        }

        if (!isNumpadVisible) {
            val fabIS = remember { MutableInteractionSource() }
            LargeFloatingActionButton(
                onClick = {
                    val now = Calendar.getInstance()
                    editingAlarm = settingsViewModel.createDefaultAlarm(
                        now.get(Calendar.HOUR_OF_DAY),
                        now.get(Calendar.MINUTE)
                    )
                    isCreatingNew = true
                },
                interactionSource = fabIS,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .bounce(fabIS)
            ) {
                Icon(Icons.Default.Add,
                    contentDescription = stringResource(R.string.action_add_alarm),
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }

        // --- DIALOGS ---
        if (editingAlarm != null) {
            EditAlarmDialog(
                alarm = editingAlarm!!,
                allGroups = viewModel.groups,
                onDismiss = { editingAlarm = null },
                onSave = { resultAlarm, newGroupName, newGroupColor ->
                    if (!newGroupName.isNullOrBlank()) {
                        // Use the new safe ViewModel function
                        val color = newGroupColor ?: 0xFFFFFFFF.toInt()
                        viewModel.saveAlarmWithNewGroup(
                            resultAlarm,
                            newGroupName,
                            color,
                            isCreatingNew
                        )
                    } else {
                        viewModel.saveAlarm(resultAlarm, isCreatingNew)
                    }
                    editingAlarm = null
                }
            )
        }


        // 1. Edit Group Name/Color
        if (groupToEdit != null) {
            EditGroupDialog(
                group = groupToEdit!!,
                onDismiss = { groupToEdit = null },
                onSave = { name, color ->
                    if (groupToEdit!!.id.isEmpty()) {
                        // viewModel.createGroup(name, color)
                    } else {
                        viewModel.updateGroupDetails(groupToEdit!!, name, color)
                    }
                    groupToEdit = null
                },
                onDelete = { keepAlarms ->
                    if (groupToEdit!!.id.isNotEmpty()) {
                        // keepAlarms=true: move alarms to default group
                        // keepAlarms=false: delete alarms
                        if (keepAlarms) {
                            groupToEdit!!.alarms.forEach { alarm ->
                                val updated = alarm.copy(groupId = "default")
                                viewModel.saveAlarm(updated, false)
                            }
                        }
                        viewModel.deleteGroup(groupToEdit!!, keepAlarms)
                    }
                    groupToEdit = null
                }
            )
        }

    // 2. Group Time Adjust
    if (groupToAdjust != null) {
        // Find the earliest next occurrence in the group
        val nextTime = groupToAdjust!!.alarms
            .filter { it.isEnabled }
            .minOfOrNull { alarm ->
                AlarmUtils.getNextOccurrence(
                    alarm.hour,
                    alarm.minute,
                    alarm.daysOfWeek,
                    0, // Ignore group offset
                    alarm.temporaryOverrideTime,
                    alarm.snoozeUntil,
                    System.currentTimeMillis()
                )
            } ?: System.currentTimeMillis()

        QuickAdjustDialog(
            quickAdjustPresets = quickAdjustPresets,
            overrideTitle = stringResource(R.string.dialog_title_adjust_group_time),
            currentDisplay = stringResource(
                R.string.adjust_all_count_alarms,
                groupToAdjust!!.alarms.count { it.isEnabled }),
            currentNextTime = nextTime, // Pass the calculated next time
            hasActiveOverride = groupToAdjust!!.alarms.any { it.temporaryOverrideTime != null },
            onDismiss = { groupToAdjust = null },
            onAdjust = { mins ->
                // Apply the 6-hour limit in the UI as well
                val clampedMins = mins.coerceIn(-360, 360)
                viewModel.adjustGroupAlarms(groupToAdjust!!, clampedMins)
                groupToAdjust = null
            },
            onReset = {
                viewModel.resetGroupAlarms(groupToAdjust!!)
                groupToAdjust = null
            }
        )
    }

    // Delete Group Dialog
    if (groupToDelete != null) {
        AlertDialog(
            onDismissRequest = { groupToDelete = null },
            title = { Text(stringResource(R.string.delete_group_name, groupToDelete!!.name)) },
            text = { Text(stringResource(R.string.groups_delete_alarms_too)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteGroup(groupToDelete!!, keepAlarms = false)
                    groupToDelete = null
                }) { Text(stringResource(R.string.group_delete_all), color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.deleteGroup(groupToDelete!!, keepAlarms = true)
                    groupToDelete = null
                }) { Text(stringResource(R.string.group_delete_keep_alarms)) }
            }
        )
    }

    if (showDatePickerForAlarm != null) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePickerForAlarm = null },
            confirmButton = {
                TextButton(onClick = {
                    val date = datePickerState.selectedDateMillis
                    if (date != null) {
                        val alarm = showDatePickerForAlarm!!
                        val updated = alarm.copy(skippedUntil = date)
                        viewModel.saveAlarm(updated, false)
                    }
                    showDatePickerForAlarm = null
                }) { Text(stringResource(R.string.action_save)) }
            }
        ) { DatePicker(state = datePickerState) }
    }
    if (showDatePickerForGroup != null) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePickerForGroup = null },
            confirmButton = {
                TextButton(onClick = {
                    val date = datePickerState.selectedDateMillis
                    if (date != null) {
                        viewModel.skipGroup(showDatePickerForGroup!!, date)
                    }
                    showDatePickerForGroup = null
                }) { Text(stringResource(R.string.action_save)) }
            }
        ) { DatePicker(state = datePickerState) }
    }
}