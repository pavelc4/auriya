package dev.auriya.app.ui.oobe

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.auriya.app.ui.theme.RobotoFlexFontFamily
import dev.auriya.app.ui.theme.RobotoSerifFontFamily
import dev.auriya.app.viewmodel.ThemeViewModel
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun WelcomeContent(
    isDark: Boolean,
    themeViewModel: ThemeViewModel,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val prefs by themeViewModel.prefs.collectAsState()
    val currentPrefs = prefs

    val titleColor = if (isDark) Color.White else Color(0xFF1C1B1F)
    val descColor = if (isDark) Color.White.copy(alpha = 0.75f) else Color(0xFF49454F)

    // Calculate Monet colors dynamically for top and bottom shapes
    val topColor = if (currentPrefs != null) {
        if (currentPrefs.useDynamicColor) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            Color(currentPrefs.seedColor).copy(alpha = 0.9f)
        }
    } else {
        Color(0xFFADC6FF)
    }

    val bottomColor = if (currentPrefs != null) {
        if (currentPrefs.useDynamicColor) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            Color(currentPrefs.seedColor).copy(alpha = 0.65f)
        }
    } else {
        Color(0xFF8A90A5)
    }

    // Infinite loop for slow, smooth rotations
    val infiniteTransition = rememberInfiniteTransition(label = "welcomeRotations")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(28000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotationAngle"
    )

    // Multilingual Welcome cycle text
    val welcomeWords = listOf(
        "HELLO",           // English
        "BONJOUR",         // French
        "HALLO",           // German
        "HOLA",            // Spanish
        "CIAO",            // Italian
        "أَهْلًا",          // Arabic
        "你好",            // Chinese
        "こんにちは",       // Japanese
        "안녕하세요",       // Korean
        "สวัสดี",           // Thai
        "नमस्ते",           // Hindi
        "XIN CHÀO",        // Vietnamese
        "KAMUSTA",         // Filipino
        "HALO"             // Indonesian
    )
    var wordIndex by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(4500) // slower cycle: 4.5 seconds
            wordIndex = (wordIndex + 1) % welcomeWords.size
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Pixel-style Canvas rendering slowly rotating multi-lobed M3 Expressive shapes
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // 1. Top-Center Wavy M3 Shape (8-leaf Sunny) rotating clockwise
            val topBaseRad = w * 0.65f
            val topCenterX = w * 0.5f
            val topCenterY = -topBaseRad * 0.4f
            val topAmp = w * 0.055f
            val topPath = Path()
            val topPetals = 8
            for (i in 0..360) {
                val theta = (i * Math.PI.toFloat() / 180f)
                val r = topBaseRad + topAmp * sin(topPetals * theta)
                val x = topCenterX + r * cos(theta)
                val y = topCenterY + r * sin(theta)
                if (i == 0) topPath.moveTo(x, y) else topPath.lineTo(x, y)
            }
            topPath.close()

            rotate(degrees = rotationAngle, pivot = Offset(topCenterX, topCenterY)) {
                drawPath(path = topPath, color = topColor)
            }

            // 2. Bottom-Left Wavy M3 Shape (6-sided Cookie) rotating counter-clockwise
            val bottomBaseRad = w * 0.72f
            val bottomCenterX = -bottomBaseRad * 0.1f
            val bottomCenterY = h + bottomBaseRad * 0.1f
            val bottomAmp = w * 0.07f
            val bottomPath = Path()
            val bottomPetals = 6
            for (i in 0..360) {
                val theta = (i * Math.PI.toFloat() / 180f)
                val r = bottomBaseRad + bottomAmp * sin(bottomPetals * theta)
                val x = bottomCenterX + r * cos(theta)
                val y = bottomCenterY + r * sin(theta)
                if (i == 0) bottomPath.moveTo(x, y) else bottomPath.lineTo(x, y)
            }
            bottomPath.close()

            rotate(degrees = -rotationAngle * 0.65f, pivot = Offset(bottomCenterX, bottomCenterY)) {
                drawPath(path = bottomPath, color = bottomColor)
            }
        }

        // Left-aligned greeting block & action button at the bottom
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .padding(horizontal = 28.dp)
                .padding(bottom = 140.dp)
        ) {
            Crossfade(
                targetState = welcomeWords[wordIndex],
                animationSpec = tween(800),
                label = "welcomeWordCrossfade"
            ) { word ->
                Text(
                    text = word,
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontFamily = RobotoSerifFontFamily,
                        fontWeight = FontWeight.Normal,
                        fontSize = 42.sp,
                        lineHeight = 48.sp
                    ),
                    color = titleColor
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Auriya".uppercase(),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = RobotoFlexFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    letterSpacing = 3.sp
                ),
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "A Magisk/KernelSU/APatch module for Android performance optimization, written in Rust. Let's authorize root privilege and personalize your layout to begin.",
                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
                color = descColor
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomEnd)
                .padding(24.dp)
        ) {
            Button(
                onClick = onNext,
                shape = CircleShape,
                modifier = Modifier
                    .width(180.dp)
                    .height(64.dp)
                    .align(Alignment.BottomEnd),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Get started",
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}
