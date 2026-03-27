package de.laurik.openalarm

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import de.laurik.openalarm.ui.theme.bounce
import de.laurik.openalarm.ui.theme.bounceClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.laurik.openalarm.ui.theme.spatialSpring
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.ui.draw.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.*
import androidx.compose.ui.graphics.RectangleShape

private enum class ExpansionState { Collapsed, Expanded }

private data class GroupStatus(
    val summary: String,
    val nextTime: Long?,
    val isNextSkipped: Boolean,
    val anySkippedOrAdjusted: Boolean
)

@Composable
fun GroupCard(
    modifier: Modifier = Modifier,
    group: AlarmGroup,
    onToggleGroup: (Boolean) -> Unit,
    onAdjust: () -> Unit,
    onEdit: () -> Unit,
    onSkipNextAll: () -> Unit,
    onClearSkipAll: () -> Unit,
    onSkipUntilAll: () -> Unit,
    onDelete: (Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val anyEnabled = group.alarms.any { it.isEnabled }
    val context = LocalContext.current
    var ticker by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            ticker = System.currentTimeMillis()
        }
    }

    // Colors
    val isSystemDark = isSystemInDarkTheme()
    val baseColor = Color(group.colorArgb)
    val isDefaultColor = group.colorArgb == -1

    // New Logic:
    // Light Mode: Pastel filled background (mix with white)
    // Dark/Black Mode: Surface background with colored outline
    val useOutline = isSystemDark && !isDefaultColor

    val cardColor = if (isDefaultColor) {
        if (isSystemDark) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant
    } else if (useOutline) {
        // Dark mode: Use surface color, will apply border separately
        MaterialTheme.colorScheme.surface
    } else {
        // Light mode: Pastel tint
        Color(ColorUtils.blendARGB(baseColor.toArgb(), Color.White.toArgb(), 0.3f))
    }

    val contentColor = MaterialTheme.colorScheme.onSurface

    // AnchoredDraggable State for swipe-to-expand
    val density = LocalDensity.current
    val decaySpec = rememberSplineBasedDecay<Float>()
    var contentHeight by remember { mutableStateOf(0f) }
    
    val state = remember(density, decaySpec) {
        AnchoredDraggableState<ExpansionState>(
            initialValue = if (group.isExpanded) ExpansionState.Expanded else ExpansionState.Collapsed,
            anchors = DraggableAnchors {
                ExpansionState.Collapsed at 0f
            },
            positionalThreshold = { it * 0.5f },
            velocityThreshold = { with(density) { 100.dp.toPx() } },
            snapAnimationSpec = spatialSpring(),
            decayAnimationSpec = decaySpec
        )
    }

    // Update anchors when contentHeight changes
    LaunchedEffect(contentHeight) {
        if (contentHeight > 0f) {
            state.updateAnchors(
                DraggableAnchors {
                    ExpansionState.Collapsed at 0f
                    ExpansionState.Expanded at contentHeight
                }
            )
        }
    }

    // Keep model in sync with drag state
    LaunchedEffect(state) {
        snapshotFlow { state.currentValue }
            .collect { expanded ->
                group.isExpanded = expanded == ExpansionState.Expanded
            }
    }

    // Sync state if model changes externally (e.g. from arrow click)
    LaunchedEffect(group.isExpanded) {
        val target = if (group.isExpanded) ExpansionState.Expanded else ExpansionState.Collapsed
        if (state.currentValue != target) {
            scope.launch {
                state.animateTo(target)
            }
        }
    }

    // Arrow rotation using the draggable progress
    val rotation by animateFloatAsState(
        targetValue = if (state.targetValue == ExpansionState.Expanded) 180f else 0f, 
        animationSpec = spatialSpring(),
        label = "arrow"
    )

    val emptyListText = stringResource(R.string.alarmlist_empty)
    val nextTimeTemplate = stringResource(R.string.next_time_group)

    // Reactively calculate the summary and next ringing time
    val status = remember(group.alarms.size, anyEnabled, group.offsetMinutes, group.skippedUntil, ticker) {
        val nextTimes = group.alarms.filter { it.isEnabled }.map { alarm ->
            val minTime = maxOf(ticker, alarm.skippedUntil, group.skippedUntil)
            AlarmUtils.getNextOccurrence(
                alarm.hour, alarm.minute, alarm.daysOfWeek, group.offsetMinutes,
                alarm.temporaryOverrideTime, alarm.snoozeUntil,
                minTime
            )
        }

        val normalNextTimes = group.alarms.filter { it.isEnabled }.map { alarm ->
            AlarmUtils.getNextOccurrence(
                alarm.hour, alarm.minute, alarm.daysOfWeek, group.offsetMinutes,
                alarm.temporaryOverrideTime, alarm.snoozeUntil,
                ticker
            )
        }

        val nTime = nextTimes.minOrNull()
        val normalNextTime = normalNextTimes.minOrNull()
        val nextSkipped = normalNextTime != null && nTime != null && nTime > normalNextTime

        val anySkipped = group.skippedUntil > ticker || group.alarms.any { it.isEnabled && (it.skippedUntil > ticker || it.temporaryOverrideTime != null) }

        val nextTimeStr = nTime?.let {
            if (it > 0) {
                val timeUntil = AlarmUtils.getTimeUntilString(context, it, ticker)
                String.format(nextTimeTemplate, timeUntil)
            } else null
        }

        val alarmList = if (group.alarms.isEmpty()) emptyListText
        else group.alarms.sortedBy { it.hour * 60 + it.minute }
            .joinToString(", ") { String.format("%02d:%02d", it.hour, it.minute) }

        val summary = if (nextTimeStr != null) "$nextTimeStr ($alarmList)" else alarmList
        GroupStatus(summary, nTime, nextSkipped, anySkipped)
    }

    val outlineColor = if (isDefaultColor) {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    } else {
        baseColor.copy(alpha = if (isSystemDark) 1f else 0.4f)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .border(
                width = 1.dp,
                color = outlineColor,
                shape = MaterialTheme.shapes.large
            ),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(3.dp)
    ) {
        Column {
            // HEADER
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .anchoredDraggable(state, Orientation.Vertical)
                    .bounceClickable(onClick = { 
                        scope.launch {
                            val target = if (state.currentValue == ExpansionState.Collapsed) ExpansionState.Expanded else ExpansionState.Collapsed
                            state.animateTo(target)
                        }
                    })
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Group Name & Summary
                Column(modifier = Modifier.weight(1f)) {
                    Text(group.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = contentColor)
                    if (!group.isExpanded) {
                        Text(status.summary, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = contentColor.copy(alpha = 0.7f))
                    }
                }



                // Action Group
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Skip Icon with bouncy effect
                    Box(modifier = Modifier.bounceClickable { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.AlarmOff,
                            contentDescription = stringResource(R.string.menu_skip_next),
                            tint = when {
                                status.isNextSkipped -> MaterialTheme.colorScheme.error
                                status.anySkippedOrAdjusted -> MaterialTheme.colorScheme.primary
                                else -> contentColor
                            },
                            modifier = Modifier.padding(8.dp)
                        )
                        
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_skip_next)) },
                                onClick = { showMenu = false; onSkipNextAll() }
                            )
                            if (status.anySkippedOrAdjusted) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_clear_skip)) },
                                    onClick = { showMenu = false; onClearSkipAll() }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_skip_until)) },
                                onClick = { showMenu = false; onSkipUntilAll() }
                            )
                        }
                    }

                    // Settings Icon with bouncy effect
                    Box(modifier = Modifier.bounceClickable { onEdit() }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.edit_group),
                            tint = contentColor,
                            modifier = Modifier.padding(8.dp)
                        )
                    }

                    // Switch with bouncy effect
                    val switchIS = remember { MutableInteractionSource() }
                    Box(modifier = Modifier.bounce(switchIS)) {
                        Switch(
                            checked = anyEnabled,
                            onCheckedChange = onToggleGroup,
                            interactionSource = switchIS,
                            modifier = Modifier.scale(0.85f)
                        )
                    }

                    // Expander Arrow (Right-aligned, bouncy)
                    Box(
                        modifier = Modifier
                            .bounceClickable { 
                                scope.launch {
                                    val target = if (state.currentValue == ExpansionState.Collapsed) ExpansionState.Expanded else ExpansionState.Collapsed
                                    state.animateTo(target)
                                }
                            }
                            .padding(8.dp)
                            .rotate(rotation)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.desc_expand),
                            tint = contentColor.copy(alpha = 0.7f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // EXPANDED CONTENT AREA
            // We use a box that clips based on the current drag offset
            val currentOffset = try { state.requireOffset() } catch (e: Exception) { 0f }
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(with(density) { currentOffset.coerceAtLeast(0f).toDp() })
                    .clip(RectangleShape)
            ) {
                // Inner Content (Alarms + Adjust Row)
                // We wrap it in unbounded height to measure it properly even when parent is 0dp
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(unbounded = true, align = Alignment.Top)
                        .onSizeChanged { size ->
                            // Measure natural height of EVERYTHING in the expansion area
                            if (size.height > 0) {
                                contentHeight = size.height.toFloat()
                            }
                        }
                        .padding(horizontal = 8.dp)
                        .padding(bottom = 8.dp)
                ) {
                    // Adjust Control Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        OutlinedButton(
                            onClick = onAdjust,
                            border = BorderStroke(2.dp, contentColor.copy(alpha = 0.5f))
                        ) {
                            Text(
                                stringResource(R.string.adjust_group_time),
                                style = MaterialTheme.typography.labelMedium,
                                fontSize = 12.sp,
                                color = contentColor
                            )
                        }
                    }

                    // The actual alarms list
                    content()
                }
            }
        }
    }
}