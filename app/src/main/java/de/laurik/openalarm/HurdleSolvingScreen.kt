package de.laurik.openalarm

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*
import kotlin.random.Random

@Composable
fun HurdleSolvingScreen(
    hurdleType: HurdleType,
    onSolved: () -> Unit,
    onCancel: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.section_hurdles),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            when (hurdleType) {
                HurdleType.DAY_OF_WEEK -> DayOfWeekHurdle(onSolved)
                HurdleType.MATH_EASY -> MathHurdle(difficulty = 0, onSolved)
                HurdleType.MATH_MEDIUM -> MathHurdle(difficulty = 1, onSolved)
                HurdleType.MATH_DIFFICULT -> MathHurdle(difficulty = 2, onSolved)
                HurdleType.GAME -> ReactionGameHurdle(onSolved)
            }

            Spacer(Modifier.height(48.dp))

            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    }
}

@Composable
fun DayOfWeekHurdle(onSolved: () -> Unit) {
    val calendar = Calendar.getInstance()
    val correctDay = calendar.get(Calendar.DAY_OF_WEEK)
    
    val days = remember {
        listOf(
            Calendar.MONDAY to R.string.day_monday,
            Calendar.TUESDAY to R.string.day_tuesday,
            Calendar.WEDNESDAY to R.string.day_wednesday,
            Calendar.THURSDAY to R.string.day_thursday,
            Calendar.FRIDAY to R.string.day_friday,
            Calendar.SATURDAY to R.string.day_saturday,
            Calendar.SUNDAY to R.string.day_sunday
        ).shuffled()
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            stringResource(R.string.hurdle_day_prompt),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        days.chunked(2).forEach { row ->
            Row {
                row.forEach { (id, resId) ->
                    Button(
                        onClick = { if (id == correctDay) onSolved() },
                        modifier = Modifier.padding(8.dp).weight(1f)
                    ) {
                        Text(stringResource(resId))
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f).padding(8.dp))
            }
        }
    }
}

@Composable
fun MathHurdle(difficulty: Int, onSolved: () -> Unit) {
    val problem by remember { mutableStateOf(generateMathProblem(difficulty)) }
    var userInput by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            stringResource(R.string.hurdle_math_prompt),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            problem.question,
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 16.dp)
        )

        // Display current input
        Surface(
            modifier = Modifier
                .width(200.dp)
                .padding(bottom = 24.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = if (isError) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.error) else null
        ) {
            Text(
                text = userInput.ifEmpty { "?" },
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(16.dp),
                color = if (userInput.isEmpty()) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        IntegratedNumpad(
            onInput = { digit ->
                isError = false
                if (userInput.length < 8) {
                    userInput += digit
                    
                    // Auto-confirm logic
                    if (userInput == problem.answer.toString()) {
                        onSolved()
                    } else if (userInput.length >= problem.answer.toString().length && !problem.answer.toString().startsWith(userInput)) {
                        isError = true
                    }
                }
            },
            onDelete = {
                if (userInput.isNotEmpty()) {
                    userInput = userInput.dropLast(1)
                    isError = false
                }
            },
            onConfirm = {
                if (userInput == problem.answer.toString()) {
                    onSolved()
                } else {
                    isError = true
                }
            },
            onCancel = { /* No-op here, handled by HurdleSolvingScreen */ },
            modifier = Modifier.padding(top = 16.dp)
        )
        
        Text(
            stringResource(R.string.hurdle_math_auto_confirm),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
fun ReactionGameHurdle(onSolved: () -> Unit) {
    var score by remember { mutableIntStateOf(0) }
    var gameState by remember { mutableStateOf("START") } // START, PLAYING, HIT
    
    // Player State
    val playerY = remember { androidx.compose.animation.core.Animatable(0f) }
    val scope = rememberCoroutineScope()
    
    // Obstacle State
    var obstacleX by remember { mutableFloatStateOf(1.2f) } 
    var ballSpeed by remember { mutableFloatStateOf(0.025f) }

    LaunchedEffect(gameState) {
        if (gameState == "PLAYING") {
            delay(1000) // Lower start delay (1.5s -> 1.0s)
            obstacleX = 1.1f
            ballSpeed = Random.nextFloat() * 0.01f + 0.018f // Slower speed (0.018..0.028)
            
            while (gameState == "PLAYING") {
                delay(16)
                obstacleX -= ballSpeed
                
                // Collision Detection
                if (obstacleX in 0.12f..0.22f) {
                    if (playerY.value > -40f) { 
                        gameState = "HIT"
                        score = 0
                        break
                    }
                }

                // Reset obstacle and increment score
                if (obstacleX < -0.15f) {
                    score++
                    if (score >= 5) {
                        onSolved()
                        break
                    }
                    // Wait for next ball
                    obstacleX = -1f 
                    delay(Random.nextLong(600, 1500))
                    obstacleX = 1.1f
                    ballSpeed = Random.nextFloat() * 0.01f + 0.018f
                }
            }
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) {
                if (gameState == "PLAYING") {
                    if (!playerY.isRunning) {
                        scope.launch {
                            playerY.animateTo(
                                targetValue = -150f,
                                animationSpec = androidx.compose.animation.core.tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                            )
                            playerY.animateTo(
                                targetValue = 0f,
                                animationSpec = androidx.compose.animation.core.tween(300, easing = androidx.compose.animation.core.FastOutLinearInEasing)
                            )
                        }
                    }
                } else {
                    score = 0
                    obstacleX = 1.1f
                    gameState = "PLAYING"
                }
            }
    ) {
        Text(
            text = when(gameState) {
                "START" -> stringResource(R.string.hurdle_game_prompt)
                "HIT" -> stringResource(R.string.hurdle_game_hit)
                else -> stringResource(R.string.hurdle_game_score, score)
            },
            style = MaterialTheme.typography.titleLarge,
            color = if (gameState == "HIT") Color.Red else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Game Area (Full size)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(MaterialTheme.colorScheme.surfaceVariant, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
        ) {
            // Cannon
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(50.dp, 40.dp)
                    .background(Color.DarkGray, androidx.compose.foundation.shape.RoundedCornerShape(topStart = 8.dp))
            )

            // Ground line
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(MaterialTheme.colorScheme.outline)
            )

            // Obstacle (Cannonball)
            if (gameState == "PLAYING" || gameState == "HIT") {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .offset(x = maxWidth * obstacleX, y = (maxHeight - 25.dp))
                            .size(25.dp)
                            .clip(CircleShape)
                            .background(Color.Black)
                    )
                }
            }

            // Player
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .offset(x = maxWidth * 0.2f, y = (maxHeight - 35.dp) + playerY.value.dp)
                        .size(35.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
        
        Text(
            stringResource(R.string.hurdle_game_tap_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}

data class MathProblem(val question: String, val answer: Int)

fun generateMathProblem(difficulty: Int): MathProblem {
    return when (difficulty) {
        1 -> { // Medium: Multiplication
            val a = Random.nextInt(2, 13)
            val b = Random.nextInt(2, 13)
            MathProblem("$a \u00D7 $b", a * b)
        }
        2 -> { // Difficult: Complex
            val a = Random.nextInt(2, 10)
            val b = Random.nextInt(2, 10)
            val c = Random.nextInt(5, 20)
            MathProblem("($a \u00D7 $b) + $c", (a * b) + c)
        }
        else -> { // Easy: Addition/Subtraction
            val a = Random.nextInt(5, 50)
            val b = Random.nextInt(5, 50)
            if (Random.nextBoolean()) {
                MathProblem("$a + $b", a + b)
            } else {
                val max = maxOf(a, b)
                val min = minOf(a, b)
                MathProblem("$max - $min", max - min)
            }
        }
    }
}
