package de.laurik.openalarm

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.laurik.openalarm.ui.theme.bounce
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.platform.LocalContext

@Composable
fun TimerScreen(
    viewModel: DashboardViewModel,
    timerPresets: List<Int> = listOf(10, 15, 30),
    triggerStartTimer: Long = 0L,
    onNumpadChange: ((@Composable () -> Unit)?) -> Unit
) {
    var hour by remember { mutableIntStateOf(0) }
    var minute by remember { mutableIntStateOf(15) }
    var second by remember { mutableIntStateOf(0) }
    var snapNext by remember { mutableStateOf(true) }
    var updateTrigger by remember { mutableLongStateOf(0L) }
    var keyboardTrigger by remember { mutableLongStateOf(0L) }
    
    val activeTimers = viewModel.activeTimers
    val currentTime by viewModel.currentTime.collectAsStateWithLifecycle()
    val isRunning = activeTimers.isNotEmpty()

    // FAB Trigger for Start Timer
    LaunchedEffect(triggerStartTimer) {
        if (triggerStartTimer > 0 && activeTimers.isEmpty()) {
            viewModel.startTimer((hour * 3600) + (minute * 60) + second)
        }
    }

    AnimatedContent(
        targetState = isRunning,
        transitionSpec = {
            if (targetState) {
                // Setup → Running: fade + scale up
                (fadeIn(tween(300)) + scaleIn(tween(300), initialScale = 0.92f))
                    .togetherWith(fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.95f))
            } else {
                // Running → Setup: fade + scale down
                (fadeIn(tween(300)) + scaleIn(tween(300), initialScale = 1.05f))
                    .togetherWith(fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 1.05f))
            }
        },
        label = "timer_state"
    ) { running ->
        if (!running) {
            // TIMER SETUP VIEW
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                TimerSetupView(
                    hour = hour,
                    minute = minute,
                    second = second,
                    updateTrigger = updateTrigger,
                    snapNext = snapNext,
                    keyboardTrigger = keyboardTrigger,
                    timerPresets = timerPresets,
                    onNumpadChange = onNumpadChange,
                    onTimeChange = { h, m, s ->
                        hour = h
                        minute = m
                        second = s
                        snapNext = true
                        updateTrigger++
                    },
                    onOpenKeyboard = { keyboardTrigger++ },
                    onStart = {
                        val totalSec = (hour * 3600) + (minute * 60) + second
                        if (totalSec > 0) viewModel.startTimer(totalSec)
                    }
                )
            }
        } else {
            // TIMER RUNNING VIEW
            val currentTimer = activeTimers.firstOrNull()
            var lastTimer by remember { mutableStateOf<TimerItem?>(null) }
            
            if (currentTimer != null) {
                lastTimer = currentTimer
            }
            
            lastTimer?.let { t ->
                TimerRunningView(
                    timer = t,
                    currentTime = currentTime,
                    onStop = { viewModel.stopTimer(t.id) },
                    onPause = { viewModel.pauseTimer(t) },
                    onResume = { viewModel.resumeTimer(t) }
                )
            }
        }
    }
}

@Composable
fun TimerSetupView(
    hour: Int,
    minute: Int,
    second: Int,
    updateTrigger: Long,
    snapNext: Boolean,
    keyboardTrigger: Long,
    timerPresets: List<Int>,
    onNumpadChange: (( @Composable () -> Unit )?) -> Unit,
    onTimeChange: (Int, Int, Int) -> Unit,
    onOpenKeyboard: () -> Unit,
    onStart: () -> Unit
) {
    SmartTimePickerLayout(
        hour = hour, minute = minute, seconds = second,
        updateTrigger = updateTrigger,
        snapImmediately = snapNext,
        keyboardTrigger = keyboardTrigger,
        onTimeChange = onTimeChange
    ) { wheelContent, numpadContent, onDismissRequest ->
        
        LaunchedEffect(numpadContent) {
            onNumpadChange(numpadContent)
        }

        DisposableEffect(Unit) {
            onDispose {
                onNumpadChange(null)
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // CENTERED CONTENT — biased slightly above center
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(modifier = Modifier.offset(y = (-40).dp)) {
                        wheelContent()
                    }

                    Spacer(Modifier.height(24.dp))

                    // Keyboard Button (Low contrast)
                    if (numpadContent == null) {
                        val keyboardIS = remember { MutableInteractionSource() }
                        TextButton(
                            onClick = onOpenKeyboard,
                            modifier = Modifier.bounce(keyboardIS),
                            interactionSource = keyboardIS,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                        ) {
                            Icon(Icons.Default.Keyboard, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.action_open_keyboard))
                        }
                    }
                }

                // BOTTOM CONTENT (Presets + Start button)
                Column(
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Presets
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                    ) {
                        timerPresets.forEach { mins ->
                            val presetIS = remember { MutableInteractionSource() }
                            OutlinedButton(
                                onClick = { onTimeChange(mins / 60, mins % 60, 0) },
                                modifier = Modifier
                                    .weight(1f)
                                    .bounce(presetIS),
                                interactionSource = presetIS
                            ) {
                                Text("${mins}m")
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Huge Start Button
                    val startIS = remember { MutableInteractionSource() }
                    Button(
                        onClick = onStart,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .padding(horizontal = 16.dp)
                            .bounce(startIS),
                        interactionSource = startIS,
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(stringResource(R.string.action_start).uppercase(), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun TimerRunningView(
    timer: TimerItem,
    currentTime: Long,
    onStop: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit
) {
    val context = LocalContext.current
    val isPaused = timer.isPaused
    val diff = if (isPaused) timer.remainingMillis else timer.endTime - currentTime

    val bgColor = if (diff > 0L) Color(0xFFE65100) else Color(0xFFB71C1C)
    val finalBg = if (isPaused) bgColor.copy(alpha = 0.6f) else bgColor

    val titleText = when {
        diff <= 0L -> stringResource(R.string.title_timer_done)
        isPaused -> stringResource(R.string.notif_timer_paused)
        else -> stringResource(R.string.notif_timer_running)
    }
    
    val timeStr = if (diff > 0L) AlarmUtils.formatTimerTime(diff) else {
        val overtime = -diff
        val s = (overtime / 1000) % 60
        val m = (overtime / (1000 * 60))
        String.format(java.util.Locale.getDefault(), "+ %02d:%02d", m, s)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(finalBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(titleText, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            val baseDuration = timer.durationSeconds
            val totalSeconds = (timer.totalDuration / 1000).toInt()
            val addedSeconds = totalSeconds - baseDuration
            
            val durationText = if (addedSeconds > 0) {
                val baseStr = AlarmUtils.formatDuration(context, baseDuration)
                val addedStr = AlarmUtils.formatDuration(context, addedSeconds)
                "($baseStr + $addedStr)"
            } else {
                "(${AlarmUtils.formatDuration(context, baseDuration)})"
            }
            Text(durationText, color = Color.White.copy(alpha = 0.7f), fontSize = 20.sp)

            Spacer(Modifier.height(32.dp))

            // BIG TIME — animated size change at < 60s
            Text(
                text = timeStr,
                color = Color.White,
                fontSize = if (diff in 1..59999) 100.sp else 90.sp,
                fontWeight = FontWeight.Bold
            )

            if (isPaused) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.action_pause).uppercase(),
                    color = Color.White.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(Modifier.height(64.dp))

            // ACTION ROW
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
            ) {
                // Pause/Resume Button
                val pauseIS = remember { MutableInteractionSource() }
                val pauseIcon = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause
                val pauseAction = if (isPaused) onResume else onPause

                Button(
                    onClick = pauseAction,
                    modifier = Modifier.weight(1f).height(64.dp).bounce(pauseIS),
                    interactionSource = pauseIS,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f)),
                    shape = MaterialTheme.shapes.large
                ) {
                    Icon(pauseIcon, contentDescription = null, modifier = Modifier.size(32.dp), tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isPaused) stringResource(R.string.action_resume) else stringResource(R.string.action_pause),
                        color = Color.White,
                        fontSize = 18.sp
                    )
                }

                // Stop Button
                val stopIS = remember { MutableInteractionSource() }
                Button(
                    onClick = onStop,
                    modifier = Modifier.weight(1f).height(64.dp).bounce(stopIS),
                    interactionSource = stopIS,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    shape = MaterialTheme.shapes.large
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(32.dp), tint = Color.Red)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_stop_caps), color = Color.Red, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(32.dp))

            // ADD TIME PRESETS
            val settingsRepo = remember { SettingsRepository.getInstance(context) }
            val adjustPresets by settingsRepo.timerAdjustPresets.collectAsState(initial = listOf(60, 300))

            Text(stringResource(R.string.label_add_time), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                adjustPresets.forEach { seconds ->
                    val addIS = remember { MutableInteractionSource() }
                    OutlinedButton(
                        onClick = {
                            val addedMillis = seconds * 1000L
                            val updated = timer.copy(
                                endTime = timer.endTime + addedMillis,
                                remainingMillis = timer.remainingMillis + addedMillis,
                                totalDuration = timer.totalDuration + addedMillis,
                                isPaused = false
                            )
                            AlarmRepository.updateTimer(context, updated)
                            if (!isPaused) {
                                AlarmScheduler(context).scheduleExact(updated.endTime, updated.id, "TIMER")
                            }
                        },
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
                        modifier = Modifier.padding(horizontal = 4.dp).height(40.dp).bounce(addIS),
                        interactionSource = addIS
                    ) {
                        Text("+${seconds / 60}m", color = Color.White)
                    }
                }
            }
        }
    }
}
