package de.laurik.openalarm

import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import de.laurik.openalarm.ui.theme.bounce
import de.laurik.openalarm.ui.theme.bounceClickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun AlarmConfigSection(
    label: String,
    onLabelChange: (String) -> Unit,
    vibration: Boolean,
    onVibrationChange: (Boolean) -> Unit,
    ringtoneUri: String?,
    onRingtoneChange: (String?) -> Unit,
    customVolume: Float?,
    onVolumeChange: (Float?) -> Unit,
    fadeInSeconds: Int,
    onFadeInChange: (Int) -> Unit,
    ttsMode: TtsMode,
    onTtsModeChange: (TtsMode) -> Unit,
    ttsText: String,
    onTtsTextChange: (String) -> Unit,
    isSingleUse: Boolean,
    onSingleUseChange: (Boolean) -> Unit,
    isSelfDestroying: Boolean,
    onSelfDestroyingChange: (Boolean) -> Unit,
    isSnoozeEnabled: Boolean,
    onSnoozeEnabledChange: (Boolean) -> Unit,
    snoozeDuration: Int?, // null = global
    onSnoozeDurationChange: (Int?) -> Unit,
    maxSnoozes: Int?, // null = unlimited
    onMaxSnoozesChange: (Int?) -> Unit,
    autoStopDuration: Int?, // null = global
    onAutoStopDurationChange: (Int?) -> Unit,
    directSnooze: Boolean = false,
    onDirectSnoozeChange: (Boolean) -> Unit,
    snoozePresets: List<Int>?,
    onSnoozePresetsChange: (List<Int>?) -> Unit,
    daysOfWeek: List<Int>,
    onDaysOfWeekChange: (List<Int>) -> Unit,
    ringingMode: RingingScreenMode,
    onRingingModeChange: (RingingScreenMode) -> Unit,
    backgroundType: String,
    onBackgroundTypeChange: (String) -> Unit,
    backgroundValue: String,
    onBackgroundValueChange: (String) -> Unit,
    hurdleEnabled: Boolean,
    onHurdleEnabledChange: (Boolean) -> Unit,
    selectedHurdles: List<HurdleType>,
    onSelectedHurdlesChange: (List<HurdleType>) -> Unit,

    betaHurdlesEnabled: Boolean,

    showRingingMode: Boolean = true,
    showDefaultRingingMode: Boolean = false,
    
    // Help labels for "use default" cases
    globalSnooze: Int = 10,
    globalAutoStop: Int = 10
) {
    var testingHurdle by remember { mutableStateOf<HurdleType?>(null) }
    val context = LocalContext.current
    val ringtoneTitle = remember(ringtoneUri) { RingtoneUtils.getRingtoneTitle(context, ringtoneUri) }
    val ringtoneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == android.app.Activity.RESULT_OK) {
            onRingtoneChange(res.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)?.toString())
        }
    }

    var showSourceSelector by remember { mutableStateOf(false) }
    var showCustomManager by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        // 1. LABEL
        OutlinedTextField(
            value = label,
            onValueChange = onLabelChange,
            label = { Text(stringResource(R.string.hint_label)) },
            placeholder = { Text(stringResource(R.string.default_alarm_title)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(16.dp))

        SwipeableDaySelector(
            selectedDays = daysOfWeek,
            onSelectionChanged = { 
                onDaysOfWeekChange(it)
                if (it.isEmpty()) onSingleUseChange(true)
            }
        )
        Spacer(Modifier.height(8.dp))

        ListItem(
            headlineContent = { Text(stringResource(R.string.setting_single_use)) },
            trailingContent = { 
                val isIS = remember { MutableInteractionSource() }
                Box(modifier = Modifier.bounce(isIS)) {
                    Switch(checked = isSingleUse, onCheckedChange = onSingleUseChange, interactionSource = isIS) 
                }
            }
        )
        AnimatedVisibility(visible = isSingleUse) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.setting_self_destroy)) },
                trailingContent = { 
                    val sdIS = remember { MutableInteractionSource() }
                    Box(modifier = Modifier.bounce(sdIS)) {
                        Switch(checked = isSelfDestroying, onCheckedChange = onSelfDestroyingChange, interactionSource = sdIS) 
                    }
                }
            )
        }
        HorizontalDivider()

        // VIBRATION
        ListItem(
            headlineContent = { Text(stringResource(R.string.label_vibration)) },
            trailingContent = { 
                val vibIS = remember { MutableInteractionSource() }
                Box(modifier = Modifier.bounce(vibIS)) {
                    Switch(checked = vibration, onCheckedChange = onVibrationChange, interactionSource = vibIS) 
                }
            }
        )
        HorizontalDivider()

        // 3. AUDIO
        Text(
            stringResource(R.string.label_audio),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 12.dp)
        )

        val selectToneTitle = stringResource(R.string.title_select_tone)
        ListItem(
            headlineContent = { Text(stringResource(R.string.label_sound)) },
            supportingContent = { Text(ringtoneTitle) },
            trailingContent = { Text(">") },
            modifier = Modifier.bounceClickable(indication = LocalIndication.current) {
                showSourceSelector = true
            }
        )

        if (showSourceSelector) {
            AlertDialog(
                onDismissRequest = { showSourceSelector = false },
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                title = { Text(stringResource(R.string.dialog_title_select_manager)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(
                            onClick = {
                                showSourceSelector = false
                                val i = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, selectToneTitle)
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, ringtoneUri?.let { Uri.parse(it) })
                                }
                                ringtoneLauncher.launch(i)
                            },
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.option_system_manager)) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                        }
                        
                        Surface(
                            onClick = {
                                showSourceSelector = false
                                showCustomManager = true
                            },
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.option_custom_manager)) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                        }
                    }
                },
                confirmButton = {}
            )
        }

        if (showCustomManager) {
            CustomRingtoneManagerDialog(
                onDismiss = { showCustomManager = false },
                onRingtoneSelected = { 
                    onRingtoneChange(it)
                    showCustomManager = false
                }
            )
        }

        // Volume Slider
        Column(modifier = Modifier.padding(top = 12.dp)) {
            Row {
                Text(stringResource(R.string.label_volume_override))
                Spacer(Modifier.weight(1f))
                Text(if (customVolume != null) stringResource(R.string.label_volume_percent, (customVolume * 100).toInt()) else stringResource(R.string.label_system))
            }
            Slider(value = customVolume ?: 0.5f, onValueChange = onVolumeChange, valueRange = 0f..1f)
            if (customVolume != null) {
                TextButton(onClick = { onVolumeChange(null) }, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.action_reset))
                }
            }
        }

        // Fade In
        Column(Modifier.padding(vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.label_fade_in), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.weight(1f))
                val fiIS = remember { MutableInteractionSource() }
                Box(modifier = Modifier.bounce(fiIS)) {
                    Switch(checked = fadeInSeconds > 0, onCheckedChange = { onFadeInChange(if (it) 30 else 0) }, interactionSource = fiIS)
                }
            }
            AnimatedVisibility(visible = fadeInSeconds > 0) {
                Column {
                    Text(stringResource(R.string.fmt_duration_s, fadeInSeconds), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    Slider(value = fadeInSeconds.toFloat(), onValueChange = { onFadeInChange(it.toInt()) }, valueRange = 1f..180f)
                }
            }
        }

        // TTS
        Column(Modifier.padding(vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.label_speak_time), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.weight(1f))
                val ttsToggleIS = remember { MutableInteractionSource() }
                Box(modifier = Modifier.bounce(ttsToggleIS)) {
                    Switch(
                        checked = ttsMode != TtsMode.NONE,
                        onCheckedChange = { enabled ->
                            onTtsModeChange(if (enabled) TtsMode.ONCE else TtsMode.NONE)
                        },
                        interactionSource = ttsToggleIS
                    )
                }
            }
            
            AnimatedVisibility(visible = ttsMode != TtsMode.NONE) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.label_tts_frequency),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val onceIS = remember { MutableInteractionSource() }
                        FilterChip(
                            selected = ttsMode == TtsMode.ONCE,
                            onClick = { onTtsModeChange(TtsMode.ONCE) },
                            label = { Text(stringResource(R.string.tts_once)) },
                            modifier = Modifier.weight(1f).bounce(onceIS),
                            interactionSource = onceIS
                        )
                        val everyIS = remember { MutableInteractionSource() }
                        FilterChip(
                            selected = ttsMode == TtsMode.EVERY_MINUTE,
                            onClick = { onTtsModeChange(TtsMode.EVERY_MINUTE) },
                            label = { Text(stringResource(R.string.tts_every_min)) },
                            modifier = Modifier.weight(1f).bounce(everyIS),
                            interactionSource = everyIS
                        )
                    }
                    
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = ttsText,
                        onValueChange = onTtsTextChange,
                        label = { Text(stringResource(R.string.label_custom_tts_text)) },
                        placeholder = { Text(stringResource(R.string.hint_custom_tts_text)) },
                        supportingText = { Text(stringResource(R.string.desc_tts_variables)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        maxLines = 3
                    )
                }
            }
        }
        HorizontalDivider()

        // 4. SNOOZE & TIMEOUT
        Text(stringResource(R.string.settings_alarm_behaviors), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 12.dp))

        ListItem(
            headlineContent = { Text(stringResource(R.string.setting_allow_snooze)) },
            trailingContent = { 
                val asIS = remember { MutableInteractionSource() }
                Box(modifier = Modifier.bounce(asIS)) {
                    Switch(checked = isSnoozeEnabled, onCheckedChange = onSnoozeEnabledChange, interactionSource = asIS) 
                }
            }
        )
        AnimatedVisibility(visible = isSnoozeEnabled) {
            Column {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.setting_direct_snooze)) },
                    supportingContent = { Text(stringResource(R.string.setting_direct_snooze_subtext)) },
                    trailingContent = { 
                        val dsIS = remember { MutableInteractionSource() }
                        Box(modifier = Modifier.bounce(dsIS)) {
                            Switch(checked = directSnooze, onCheckedChange = onDirectSnoozeChange, interactionSource = dsIS) 
                        }
                    }
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.setting_snooze_duration)) },
                    supportingContent = { Text(if (snoozeDuration == null) stringResource(R.string.setting_snooze_duration_subtext_default, globalSnooze) else stringResource(R.string.setting_snooze_duration_subtext_custom, snoozeDuration)) },
                    modifier = Modifier.bounceClickable(indication = LocalIndication.current) { onSnoozeDurationChange(snoozeDuration) }
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.setting_max_snoozes)) },
                    supportingContent = { Text(if (maxSnoozes == null) stringResource(R.string.setting_max_snoozes_subtext_infinite) else stringResource(R.string.setting_max_snoozes_subtext_custom, maxSnoozes)) },
                    modifier = Modifier.bounceClickable(indication = LocalIndication.current) { onMaxSnoozesChange(maxSnoozes) }
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.setting_snooze_presets)) },
                    supportingContent = {
                        val defaultText = stringResource(R.string.setting_snooze_presets_default)
                        val fmtMinutesShort = stringResource(R.string.fmt_minutes_short)
                        Text(if (snoozePresets == null) defaultText else snoozePresets.joinToString(", ") { it.toString() + fmtMinutesShort })
                    },
                    modifier = Modifier.bounceClickable(indication = LocalIndication.current) { onSnoozePresetsChange(snoozePresets) }
                )
            }
        }
        
        ListItem(
            headlineContent = { Text(stringResource(R.string.setting_timeout)) },
            supportingContent = { Text(if (autoStopDuration == null) stringResource(R.string.setting_timeout_default, globalAutoStop) else stringResource(R.string.setting_timeout_custom, autoStopDuration)) },
            modifier = Modifier.bounceClickable(indication = LocalIndication.current) { onAutoStopDurationChange(autoStopDuration) }
        )

        HorizontalDivider()

        // 5. RINGING SCREEN STYLE
        Text(stringResource(R.string.label_ringing_experience), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 12.dp))

        if (showRingingMode) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.setting_ringing_mode)) },
                supportingContent = { Text(ringingMode.localizedName()) },
                modifier = Modifier.bounceClickable(indication = LocalIndication.current) {
                    val modes = if (showDefaultRingingMode) {
                        RingingScreenMode.entries.toList()
                    } else {
                        RingingScreenMode.entries.filter { it != RingingScreenMode.DEFAULT }
                    }
                    val currentIndex = modes.indexOf(ringingMode)
                    val next = modes.getOrElse((currentIndex + 1) % modes.size) { modes.first() }
                    onRingingModeChange(next)
                }
            )
        }

        ListItem(
            headlineContent = { Text(stringResource(R.string.setting_alarm_background)) },
            supportingContent = { Text(if (backgroundType == "COLOR") stringResource(R.string.setting_text_solid_color) else stringResource(R.string.setting_text_gradient)) },
            modifier = Modifier.bounceClickable(indication = LocalIndication.current) { onBackgroundTypeChange(backgroundType) }
        )

        if (betaHurdlesEnabled) {
            HorizontalDivider()

            // 6. HURDLES
            Text(
                stringResource(R.string.section_hurdles) + stringResource(R.string.label_hurdle_beta),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.setting_hurdle_enabled)) },
                supportingContent = { Text(stringResource(R.string.setting_hurdle_enabled_desc)) },
                trailingContent = {
                    Switch(
                        checked = hurdleEnabled,
                        onCheckedChange = onHurdleEnabledChange
                    )
                }
            )

            AnimatedVisibility(visible = hurdleEnabled) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        stringResource(R.string.label_select_hurdles),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    HurdleType.entries.filter { it != HurdleType.GAME }.forEach { type ->
                        val labelId = when (type) {
                            HurdleType.DAY_OF_WEEK -> R.string.hurdle_day_of_week
                            HurdleType.MATH_EASY -> R.string.hurdle_math_easy
                            HurdleType.MATH_MEDIUM -> R.string.hurdle_math_medium
                            HurdleType.MATH_DIFFICULT -> R.string.hurdle_math_difficult
                            HurdleType.GAME -> R.string.hurdle_game
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selectedHurdles.contains(type),
                                onCheckedChange = { checked ->
                                    val newList = if (checked) {
                                        selectedHurdles + type
                                    } else {
                                        selectedHurdles.filter { it != type }
                                    }
                                    onSelectedHurdlesChange(newList)
                                }
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(labelId), modifier = Modifier.weight(1f))
                            IconButton(onClick = { testingHurdle = type }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Test")
                            }
                        }
                    }
                }
            }
        }

        if (testingHurdle != null) {
            Dialog(
                onDismissRequest = { testingHurdle = null },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(Modifier.fillMaxSize()) {
                    HurdleSolvingScreen(
                        hurdleType = testingHurdle!!,
                        onSolved = { testingHurdle = null },
                        onCancel = { testingHurdle = null }
                    )
                }
            }
        }
    }
}

@Composable
fun ColorPickerGrid(
    selectedColor: String,
    onColorSelected: (String) -> Unit
) {
    val colors = listOf(
        "#F44336", "#E91E63", "#9C27B0", "#673AB7", "#3F51B5", "#2196F3",
        "#03A9F4", "#00BCD4", "#009688", "#4CAF50", "#8BC34A", "#CDDC39",
        "#FFEB3B", "#FFC107", "#FF9800", "#FF5722", "#795548", "#9E9E9E",
        "#607D8B", "#000000", "#FFFFFF"
    )

    Column {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 44.dp),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.heightIn(max = 240.dp)
        ) {
            items(colors) { colorHex ->
                val color = runCatching { Color(android.graphics.Color.parseColor(colorHex)) }.getOrDefault(Color.Gray)
                val isSelected = selectedColor.equals(colorHex, ignoreCase = true)
                
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(color)
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.5f),
                            shape = MaterialTheme.shapes.medium
                        )
                        .bounceClickable(indication = LocalIndication.current) { onColorSelected(colorHex) },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = if (color.luminance() > 0.5f) Color.Black else Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BackgroundConfigDialog(
    currentType: String,
    currentValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var type by remember { mutableStateOf(currentType) }
    var color1 by remember { mutableStateOf("") }
    var color2 by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (currentType == "COLOR") {
            color1 = currentValue
        } else if (currentType == "GRADIENT") {
            val parts = currentValue.split(",")
            color1 = parts.getOrNull(0)?.trim() ?: "#000000"
            color2 = parts.getOrNull(1)?.trim() ?: "#000000"
        } else {
            color1 = "#000000"
            color2 = "#000000"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.setting_alarm_background_dialog_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected = type == "COLOR",
                        onClick = { type = "COLOR" },
                        label = { Text(stringResource(R.string.label_color)) }
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = type == "GRADIENT",
                        onClick = { type = "GRADIENT" },
                        label = { Text(stringResource(R.string.setting_text_gradient)) }
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(if (type == "COLOR") stringResource(R.string.setting_pick_color) else stringResource(R.string.setting_pick_top_color), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                ColorPickerGrid(
                    selectedColor = color1,
                    onColorSelected = { color1 = it }
                )
                
                if (type == "GRADIENT") {
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.setting_pick_bottom_color), style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    ColorPickerGrid(
                        selectedColor = color2,
                        onColorSelected = { color2 = it }
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(type, if (type == "COLOR") color1 else "$color1,$color2") }) {
                Text(stringResource(R.string.button_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.button_cancel)) }
        }
    )
}

@Composable
fun RingingScreenMode.localizedName(): String {
    return when (this) {
        RingingScreenMode.EASY -> stringResource(R.string.mode_easy)
        RingingScreenMode.CLEAN -> stringResource(R.string.mode_clean)
        RingingScreenMode.DEFAULT -> stringResource(R.string.mode_default)
    }
}