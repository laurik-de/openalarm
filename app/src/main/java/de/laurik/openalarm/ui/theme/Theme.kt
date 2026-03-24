package de.laurik.openalarm.ui.theme

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import de.laurik.openalarm.AppThemeMode

// Material 3 Expressive Motion Specs (Manual Implementation)
fun <T> spatialSpring() = spring<T>(
    dampingRatio = 0.8f,
    stiffness = 380f
)

fun <T> effectsSpring() = spring<T>(
    dampingRatio = 1f,
    stiffness = 200f
)

/**
 * A bouncy scale modifier that monitors an InteractionSource.
 */
@Composable
fun Modifier.bounce(interactionSource: InteractionSource): Modifier {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f, // Slightly more pronounced bounce
        animationSpec = spatialSpring(),
        label = "bounce_scale"
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * A modifier that performs a horizontal shake animation triggered by a change in [trigger].
 */
@Composable
fun Modifier.shake(trigger: Long): Modifier {
    val xOffset = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger > 0) {
            xOffset.snapTo(20f)
            xOffset.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = 0.15f,
                    stiffness = 3000f
                )
            )
        }
    }
    return this.graphicsLayer {
        translationX = xOffset.value
    }
}

/**
 * A bouncy clickable modifier that scales the content down slightly when pressed.
 */
@Composable
fun Modifier.bounceClickable(
    interactionSource: MutableInteractionSource? = null,
    indication: Indication? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier {
    val actualIS = interactionSource ?: remember { MutableInteractionSource() }
    return this.bounce(actualIS)
        .clickable(
            interactionSource = actualIS,
            indication = indication,
            enabled = enabled,
            onClick = onClick
        )
}

/**
 * A bouncy combined clickable modifier that supports both click and long click.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.bounceCombinedClickable(
    interactionSource: MutableInteractionSource? = null,
    indication: Indication? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
): Modifier {
    val actualIS = interactionSource ?: remember { MutableInteractionSource() }
    return this.bounce(actualIS)
        .combinedClickable(
            interactionSource = actualIS,
            indication = indication,
            enabled = enabled,
            onClick = onClick,
            onLongClick = onLongClick
        )
}

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    secondary = SecondaryDark,
    tertiary = TertiaryDark
)

private val BlackColorScheme = darkColorScheme(
    primary = PrimaryDark,
    secondary = SecondaryDark,
    tertiary = TertiaryDark,
    background = Color.Black,
    surface = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryLight,
    secondary = SecondaryLight,
    tertiary = TertiaryLight
)

@Composable
fun OpenAlarmTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    isPureBlack: Boolean = false,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

    val colorScheme = when {
        darkTheme && isPureBlack -> BlackColorScheme
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = ExpressiveShapes,
        content = content
    )
}