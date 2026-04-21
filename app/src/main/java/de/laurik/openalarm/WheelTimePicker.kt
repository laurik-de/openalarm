package de.laurik.openalarm

import androidx.compose.animation.core.EaseOutQuart
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

enum class TimeColumn { NONE, HOUR, MINUTE, SECOND }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WheelTimePicker(
    hour: Int,
    minute: Int,
    seconds: Int? = null,
    activeColumn: TimeColumn,
    inputBuffer: String,
    updateTrigger: Long,
    snapImmediately: Boolean,
    onTimeChange: (Int, Int, Int) -> Unit,
    onColumnClick: (TimeColumn) -> Unit
) {
    val pageCount = 2400
    val startPage = pageCount / 2

    val initialHourPage = remember { startPage + (hour - (startPage % 24)) }
    val initialMinutePage = remember { startPage + (minute - (startPage % 60)) }
    val initialSecondPage = remember { if (seconds != null) startPage + (seconds - (startPage % 60)) else 0 }

    val hourState = rememberPagerState(initialPage = initialHourPage) { pageCount }
    val minuteState = rememberPagerState(initialPage = initialMinutePage) { pageCount }
    val secondState = rememberPagerState(initialPage = initialSecondPage) { pageCount }

    var isProgrammaticScroll by remember { mutableStateOf(false) }

    // 1. SCROLL LISTENER
    LaunchedEffect(hourState.currentPage, minuteState.currentPage, secondState.currentPage) {
        if (isProgrammaticScroll) return@LaunchedEffect
        
        val h = hourState.currentPage % 24
        val m = minuteState.currentPage % 60
        val s = if (seconds != null) secondState.currentPage % 60 else 0

        if (h != hour || m != minute || (seconds != null && s != seconds)) {
            onTimeChange(h, m, s)
        }
    }

    // 2. COMMAND LISTENER (Presets/Numpad)
    LaunchedEffect(updateTrigger) {
        isProgrammaticScroll = true
        try {
            suspend fun syncWheel(state: androidx.compose.foundation.pager.PagerState, targetVal: Int, range: Int) {
                if (state.isScrollInProgress) return

                val currentVal = state.currentPage % range
                if (currentVal != targetVal) {
                    val diff = (targetVal - currentVal + range) % range
                    val targetPage = state.currentPage + diff

                    if (snapImmediately) {
                        state.scrollToPage(targetPage)
                    } else {
                        state.animateScrollToPage(
                            targetPage,
                            animationSpec = tween(durationMillis = 500, easing = EaseOutQuart)
                        )
                    }
                }
            }
            
            // Run all syncs in parallel and wait for them
            kotlinx.coroutines.coroutineScope {
                launch { syncWheel(hourState, hour, 24) }
                launch { syncWheel(minuteState, minute, 60) }
                if (seconds != null) {
                    launch { syncWheel(secondState, seconds, 60) }
                }
            }
        } finally {
            // Small delay to ensure any last-frame scroll events are ignored
            kotlinx.coroutines.delay(50)
            isProgrammaticScroll = false
        }
    }

    val isEditing = activeColumn != TimeColumn.NONE
    val topSpacerHeight by animateDpAsState(targetValue = if (isEditing) 100.dp else 0.dp, label = "LayoutShift")

    Column {
        Spacer(modifier = Modifier.height(topSpacerHeight))

        Box(
            modifier = Modifier
                .height(220.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            if (!isEditing) {
                Box(modifier = Modifier.fillMaxWidth(0.9f).height(64.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest))
            }

            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val labelStyle = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                )

                // HOURS
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        WheelColumn(hourState, 24, (activeColumn == TimeColumn.HOUR || !isEditing), isEditing, if (activeColumn == TimeColumn.HOUR) inputBuffer else null, { onColumnClick(TimeColumn.HOUR) }, Modifier.fillMaxWidth())
                    }
                    if (isEditing) {
                        Text(androidx.compose.ui.res.stringResource(R.string.label_hour_short), style = labelStyle)
                        Spacer(Modifier.height(12.dp))
                    }
                }

                // COLON 1
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(":", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(bottom = 6.dp))
                    }
                    if (isEditing) {
                        Spacer(Modifier.height(28.dp)) // Height of label + Spacer
                    }
                }

                // MINUTES
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        WheelColumn(minuteState, 60, (activeColumn == TimeColumn.MINUTE || !isEditing), isEditing, if (activeColumn == TimeColumn.MINUTE) inputBuffer else null, { onColumnClick(TimeColumn.MINUTE) }, Modifier.fillMaxWidth())
                    }
                    if (isEditing) {
                        Text(androidx.compose.ui.res.stringResource(R.string.label_minute_short), style = labelStyle)
                        Spacer(Modifier.height(12.dp))
                    }
                }

                if (seconds != null) {
                    // COLON 2
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Text(":", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(bottom = 6.dp))
                        }
                        if (isEditing) {
                            Spacer(Modifier.height(28.dp))
                        }
                    }

                    // SECONDS
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            WheelColumn(secondState, 60, (activeColumn == TimeColumn.SECOND || !isEditing), isEditing, if (activeColumn == TimeColumn.SECOND) inputBuffer else null, { onColumnClick(TimeColumn.SECOND) }, Modifier.fillMaxWidth())
                        }
                        if (isEditing) {
                            Text(androidx.compose.ui.res.stringResource(R.string.label_second_short), style = labelStyle)
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WheelColumn(
    state: androidx.compose.foundation.pager.PagerState,
    range: Int,
    isFocused: Boolean,
    isCompact: Boolean,
    editDisplay: String?,
    onClick: () -> Unit,
    modifier: Modifier
) {
    val flingBehavior = PagerDefaults.flingBehavior(
        state = state,
        pagerSnapDistance = PagerSnapDistance.atMost(26)
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        VerticalPager(
            state = state,
            modifier = Modifier.fillMaxSize(),
            pageSize = PageSize.Fixed(64.dp),
            contentPadding = PaddingValues(vertical = 78.dp),
            // OPTIMIZATION 1: Reduce offscreen load to keep frame rate high
            beyondViewportPageCount = 1,
            flingBehavior = flingBehavior,
            userScrollEnabled = !isCompact,
            // OPTIMIZATION 2: Use Keys to help Compose recycle views (01 is always 01)
            key = { page -> page % range }
        ) { page ->
            val number = page % range

            // OPTIMIZATION 3: Perform math inside graphicsLayer to skip Recomposition
            // We just create the Box, and let the GPU handle the scaling frame-by-frame
            Box(
                modifier = Modifier
                    .height(64.dp)
                    .fillMaxWidth()
                    .graphicsLayer {
                        // Calculate offset from the PagerState directly inside the draw phase
                        val pageOffset = ((state.currentPage - page) + state.currentPageOffsetFraction).absoluteValue

                        // Simpler Curve for speed
                        val scale = lerp(0.7f, 1f, 1f - pageOffset.coerceIn(0f, 1f))

                        val baseAlpha = if (isCompact) {
                            if (pageOffset < 0.5f) 1f else 0f
                        } else {
                            lerp(0.3f, 1f, 1f - pageOffset.coerceIn(0f, 1f))
                        }

                        this.scaleX = scale
                        this.scaleY = scale
                        this.alpha = if (isFocused) baseAlpha else baseAlpha * 0.3f
                    },
                contentAlignment = Alignment.Center
            ) {
                // If editing this specific cell, show cursor content
                // Note: We access state.currentPage here, which causes recomposition only when page changes,
                // not on every pixel scroll (unlike pageOffset).
                if (isCompact && isFocused && page == state.currentPage) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val color = MaterialTheme.colorScheme.primary
                        
                        val char1: String
                        val char2: String
                        val char1Color: androidx.compose.ui.graphics.Color
                        val char2Color: androidx.compose.ui.graphics.Color
                        
                        if (editDisplay.isNullOrEmpty()) {
                            char1 = "_"
                            char2 = "_"
                            char1Color = color
                            char2Color = color
                        } else if (editDisplay.length == 1) {
                            char1 = "_"
                            char2 = editDisplay
                            char1Color = color
                            char2Color = color
                        } else {
                            char1 = editDisplay[0].toString()
                            char2 = editDisplay[1].toString()
                            char1Color = color
                            char2Color = color
                        }

                        Text(char1, fontSize = 48.sp, fontWeight = FontWeight.Bold, color = char1Color)
                        Spacer(Modifier.width(2.dp))
                        Text(char2, fontSize = 48.sp, fontWeight = FontWeight.Bold, color = char2Color)
                    }
                } else {
                    // Standard Number
                    val color = if (isCompact && isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    Text(
                        text = String.format("%02d", number),
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                }
            }
        }
    }
}