package dev.auriya.app.ui.oobe

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.auriya.app.data.DarkThemeMode
import dev.auriya.app.data.NavMode
import dev.auriya.app.data.NavType
import dev.auriya.app.data.ThemePrefs
import dev.auriya.app.ui.theme.AuriyaTokens
import dev.auriya.app.ui.theme.RobotoSerifFontFamily
import dev.auriya.app.ui.theme.RobotoFlexFontFamily
import dev.auriya.app.viewmodel.ThemeViewModel
import dev.auriya.app.viewmodel.UiViewModel
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

// ─── Custom Dynamic Palette Items (Screenshot 2) ───────────────────────────
private data class PaletteItem(
    val seed: Int,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val neutral: Color
)

private val PALETTE_ITEMS = listOf(
    // Greenish theme (Monet template 1)
    PaletteItem(
        seed = 0xFFA7E0A2.toInt(),
        primary = Color(0xFF388E3C),
        secondary = Color(0xFF81C784),
        tertiary = Color(0xFFC8E6C9),
        neutral = Color(0xFFE8F5E9)
    ),
    // Blueish/gray theme (Monet template 2)
    PaletteItem(
        seed = 0xFF9ECAFF.toInt(),
        primary = Color(0xFF1976D2),
        secondary = Color(0xFF64B5F6),
        tertiary = Color(0xFFBBDEFB),
        neutral = Color(0xFFE3F2FD)
    ),
    // Purple theme (Monet template 3)
    PaletteItem(
        seed = 0xFFD0BCFF.toInt(),
        primary = Color(0xFF7B1FA2),
        secondary = Color(0xFFBA68C8),
        tertiary = Color(0xFFE1BEE7),
        neutral = Color(0xFFF3E5F5)
    ),
    // Pinkish/orange theme (Monet template 4)
    PaletteItem(
        seed = 0xFFFFB68E.toInt(),
        primary = Color(0xFFE64A19),
        secondary = Color(0xFFFF8A65),
        tertiary = Color(0xFFFFCCBC),
        neutral = Color(0xFFFBE9E7)
    ),
    // Yellow/sand theme (Monet template 5)
    PaletteItem(
        seed = 0xFFFFD188.toInt(),
        primary = Color(0xFFFBC02D),
        secondary = Color(0xFFFFF176),
        tertiary = Color(0xFFFFF9C4),
        neutral = Color(0xFFFFFDE7)
    )
)

// Helper to determine if we should render light or dark text based on theme preferences
@Composable
private fun isThemeDark(prefs: ThemePrefs?): Boolean {
    if (prefs == null) return true // default to dark OOBE theme
    return when (prefs.darkThemeMode) {
        DarkThemeMode.DARK -> true
        DarkThemeMode.LIGHT -> false
        DarkThemeMode.FOLLOW_SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
    }
}

// ─── Main Screen ───
@Composable
fun OobeScreen(
    viewModel: UiViewModel,
    themeViewModel: ThemeViewModel,
    onFinished: () -> Unit,
) {
    var step by remember { mutableStateOf(1) }
    val hasRoot by viewModel.hasRoot.collectAsState()

    val prefs by themeViewModel.prefs.collectAsState()
    val currentPrefs = prefs
    val isDark = isThemeDark(currentPrefs)

    // Root polling only on step 2
    LaunchedEffect(step, hasRoot) {
        if (step == 2 && !hasRoot) {
            while (!hasRoot) {
                viewModel.checkRoot()
                delay(1500)
            }
        }
    }

    val bg = MaterialTheme.colorScheme.background

    Box(modifier = Modifier.fillMaxSize().background(bg)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Crossfade(
                targetState = step,
                animationSpec = tween(400),
                modifier = Modifier.fillMaxSize(),
                label = "OobeStep"
            ) { s ->
                when (s) {
                    1 -> WelcomeContent(
                        isDark = isDark,
                        themeViewModel = themeViewModel,
                        onNext = { step = 2 }
                    )
                    2 -> RootCheckContent(
                        isDark = isDark,
                        viewModel = viewModel,
                        hasRoot = hasRoot,
                        onBack = { step = 1 },
                        onNext = { step = 3 }
                    )
                    3 -> ColoringContent(
                        isDark = isDark,
                        themeViewModel = themeViewModel,
                        onBack = { step = 2 },
                        onNext = { step = 4 }
                    )
                    4 -> NavbarContent(
                        isDark = isDark,
                        themeViewModel = themeViewModel,
                        onBack = { step = 3 },
                        onNext = { step = 5 }
                    )
                    5 -> CorneringContent(
                        isDark = isDark,
                        themeViewModel = themeViewModel,
                        onBack = { step = 4 },
                        onNext = { step = 6 }
                    )
                    6 -> DoneContent(
                        isDark = isDark,
                        themeViewModel = themeViewModel,
                        onBack = { step = 5 },
                        onFinished = onFinished
                    )
                }
            }
        }
    }
}

// ─── Step 1: Welcome (Rotating M3 Shapes & Pixel OOBE Style) ───
@Composable
private fun WelcomeContent(
    isDark: Boolean,
    themeViewModel: ThemeViewModel,
    onNext: () -> Unit
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

    // Multilingual Welcome cycle text (14 languages: English, French, German, Spanish, Italian, Arabic, Chinese, Japanese, Korean, Thai, Hindi, Vietnamese, Filipino, Indonesian)
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
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background) // Dynamic Monet background
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

        // Left-aligned greeting block & action button at the bottom (All text locked to adaptive contrast colors)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .padding(horizontal = 28.dp)
                .padding(bottom = 140.dp)
        ) {
            // Cycling multilingual welcome phrase - LARGE main heading (Roboto Serif)
            Crossfade(
                targetState = welcomeWords[wordIndex],
                animationSpec = tween(800), // slower crossfade: 800ms
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

            // Smaller product title - uppercase subtitle branding (Roboto Flex)
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

        // Bottom action row containing the dynamic Monet primary action button
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

// ─── Step 2: Root Check (SUPERUSER) ───
@Composable
private fun RootCheckContent(
    isDark: Boolean,
    viewModel: UiViewModel,
    hasRoot: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    val titleColor = if (isDark) Color.White else Color(0xFF1C1B1F)
    val descColor = if (isDark) Color.White.copy(alpha = 0.7f) else Color(0xFF49454F)

    var isRequesting by remember { mutableStateOf(false) }
    LaunchedEffect(hasRoot) { if (hasRoot) isRequesting = false }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Spacer(modifier = Modifier.height(36.dp)) // push down content

        Icon(
            imageVector = Icons.Outlined.Shield,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "SUPERUSER",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp
            ),
            color = titleColor
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Auriya needs root authorization to optimize kernel governors, cores, and frequencies in real-time.",
            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
            color = descColor
        )

        Spacer(modifier = Modifier.height(32.dp))

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = if (hasRoot) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (hasRoot) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                            else MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (hasRoot) Icons.Outlined.CheckCircle else Icons.Outlined.Shield,
                        null,
                        tint = if (hasRoot) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        if (hasRoot) "Root verified" else "Waiting for authorization",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = titleColor
                    )
                    Text(
                        if (hasRoot) "Auriya daemon initialized successfully." else "Grant Magisk / KSU / APatch prompt.",
                        style = MaterialTheme.typography.bodySmall,
                        color = descColor
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (!hasRoot) {
            OutlinedButton(
                onClick = { isRequesting = true; viewModel.checkRoot() },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
                enabled = !isRequesting,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            ) {
                if (isRequesting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (isRequesting) "Checking root..." else "Request permission", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Back", color = MaterialTheme.colorScheme.primary)
            }

            Button(
                onClick = onNext,
                shape = CircleShape,
                enabled = hasRoot,
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("Continue", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ─── Step 3: Coloring (COLORING) ───
@Composable
private fun ColoringContent(
    isDark: Boolean,
    themeViewModel: ThemeViewModel,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    val titleColor = if (isDark) Color.White else Color(0xFF1C1B1F)
    val descColor = if (isDark) Color.White.copy(alpha = 0.7f) else Color(0xFF49454F)

    val prefs by themeViewModel.prefs.collectAsState()
    val currentPrefs = prefs ?: return

    // Swatch focus pulse loop
    val infiniteTransition = rememberInfiniteTransition(label = "pulseLoop")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Spacer(modifier = Modifier.height(36.dp)) // push down content

        Text(
            "COLORING",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp
            ),
            color = titleColor
        )
        Text(
            "Configure Monet dynamic styling and color accents.",
            style = MaterialTheme.typography.bodyMedium,
            color = descColor
        )
        Spacer(modifier = Modifier.height(20.dp))

        // Rich, animated M3 shape & widgets preview card
        ThemeColorWidgetCompositionCard(
            pulseScale = pulseScale,
            pulseAlpha = pulseAlpha
        )

        Spacer(modifier = Modifier.height(24.dp))

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Material You", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = titleColor)
                    Text("Match system theme colors", style = MaterialTheme.typography.bodySmall, color = descColor)
                }
                Switch(
                    checked = currentPrefs.useDynamicColor,
                    onCheckedChange = { themeViewModel.setUseDynamicColor(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }

            if (!currentPrefs.useDynamicColor) {
                Column {
                    Text("Custom Palette", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = titleColor)
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(PALETTE_ITEMS) { item ->
                            SwatchDot(
                                item = item,
                                selected = item.seed == currentPrefs.seedColor,
                                pulseScale = pulseScale,
                                pulseAlpha = pulseAlpha
                            ) {
                                themeViewModel.setSeedColor(item.seed)
                            }
                        }
                    }
                }
            }

            // Dark Mode Segmented Control (System, Light, Dark)
            Column {
                Text("Theme Mode", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = titleColor)
                Spacer(modifier = Modifier.height(8.dp))
                SegmentedControl(
                    items = listOf("System", "Light", "Dark"),
                    selectedIndex = when (currentPrefs.darkThemeMode) {
                        DarkThemeMode.FOLLOW_SYSTEM -> 0
                        DarkThemeMode.LIGHT -> 1
                        DarkThemeMode.DARK -> 2
                    },
                    isDark = isDark
                ) {
                    themeViewModel.setDarkThemeMode(
                        when (it) {
                            0 -> DarkThemeMode.FOLLOW_SYSTEM
                            1 -> DarkThemeMode.LIGHT
                            else -> DarkThemeMode.DARK
                        }
                    )
                }
            }

            // AMOLED Toggle switch
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Pure Black (AMOLED)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = titleColor)
                    Text("Turn background pitch black in dark mode", style = MaterialTheme.typography.bodySmall, color = descColor)
                }
                Switch(
                    checked = currentPrefs.isAmoled,
                    onCheckedChange = { themeViewModel.setAmoled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Back", color = MaterialTheme.colorScheme.primary)
            }

            Button(
                onClick = onNext,
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("Continue", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ─── Step 4: Navigation Bar Layout (NAVIGATION) ───
@Composable
private fun NavbarContent(
    isDark: Boolean,
    themeViewModel: ThemeViewModel,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    val titleColor = if (isDark) Color.White else Color(0xFF1C1B1F)
    val descColor = if (isDark) Color.White.copy(alpha = 0.7f) else Color(0xFF49454F)

    val prefs by themeViewModel.prefs.collectAsState()
    val currentPrefs = prefs ?: return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Spacer(modifier = Modifier.height(36.dp)) // push down content

        Text(
            "NAVIGATION",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp
            ),
            color = titleColor
        )
        Text(
            "Select placement styles for your system bottom navigation.",
            style = MaterialTheme.typography.bodyMedium,
            color = descColor
        )
        Spacer(modifier = Modifier.height(20.dp))

        LiveUiPreviewCard(
            seedColor = currentPrefs.seedColor,
            useDynamicColor = currentPrefs.useDynamicColor,
            navMode = currentPrefs.navMode,
            navType = currentPrefs.navType,
            cornerRadius = currentPrefs.cornerRadius
        )

        Spacer(modifier = Modifier.height(24.dp))

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column {
                Text("Navigation Layout", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = titleColor)
                Spacer(modifier = Modifier.height(8.dp))
                SegmentedControl(
                    items = listOf("Standard", "Floating"),
                    selectedIndex = if (currentPrefs.navMode == NavMode.STANDARD) 0 else 1,
                    isDark = isDark
                ) { themeViewModel.setNavMode(if (it == 0) NavMode.STANDARD else NavMode.FLOATING) }
            }

            Column {
                Text("Navigation Type", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = titleColor)
                Spacer(modifier = Modifier.height(8.dp))
                SegmentedControl(
                    items = listOf("Legacy (Dots)", "Modern (Squircles)"),
                    selectedIndex = if (currentPrefs.navType == NavType.LEGACY) 0 else 1,
                    isDark = isDark
                ) { themeViewModel.setNavType(if (it == 0) NavType.LEGACY else NavType.MODERN) }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Back", color = MaterialTheme.colorScheme.primary)
            }

            Button(
                onClick = onNext,
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("Continue", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ─── Step 5: Cornering (CORNER RADIUS) ───
@Composable
private fun CorneringContent(
    isDark: Boolean,
    themeViewModel: ThemeViewModel,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    val titleColor = if (isDark) Color.White else Color(0xFF1C1B1F)
    val descColor = if (isDark) Color.White.copy(alpha = 0.7f) else Color(0xFF49454F)

    val prefs by themeViewModel.prefs.collectAsState()
    val currentPrefs = prefs ?: return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Spacer(modifier = Modifier.height(36.dp)) // push down content

        Text(
            "CORNER RADIUS",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp
            ),
            color = titleColor
        )
        Text(
            "Drag slider to set the rounding style of bottom bar corners.",
            style = MaterialTheme.typography.bodyMedium,
            color = descColor
        )
        Spacer(modifier = Modifier.height(20.dp))

        LiveUiPreviewCard(
            seedColor = currentPrefs.seedColor,
            useDynamicColor = currentPrefs.useDynamicColor,
            navMode = currentPrefs.navMode,
            navType = currentPrefs.navType,
            cornerRadius = currentPrefs.cornerRadius
        )

        Spacer(modifier = Modifier.height(24.dp))

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Corner Radius", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = titleColor)
                    Text("${currentPrefs.cornerRadius}dp", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = currentPrefs.cornerRadius.toFloat(),
                    onValueChange = { themeViewModel.setCornerRadius(it.toInt()) },
                    valueRange = 0f..32f, steps = 32,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Back", color = MaterialTheme.colorScheme.primary)
            }

            Button(
                onClick = onNext,
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("Continue", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ─── Step 6: Completion Page (DONE) ───
@Composable
private fun DoneContent(
    isDark: Boolean,
    themeViewModel: ThemeViewModel,
    onBack: () -> Unit,
    onFinished: () -> Unit,
) {
    val titleColor = if (isDark) Color.White else Color(0xFF1C1B1F)
    val descColor = if (isDark) Color.White.copy(alpha = 0.7f) else Color(0xFF49454F)

    val prefs by themeViewModel.prefs.collectAsState()
    val currentPrefs = prefs ?: return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Spacer(modifier = Modifier.height(36.dp)) // push down content

        Icon(
            imageVector = Icons.Outlined.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "All set.",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Light, letterSpacing = 0.5.sp),
            color = descColor
        )
        Text(
            "DONE",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp
            ),
            color = titleColor
        )

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            "Your appearance preferences have been successfully configured. Auriya governor optimizations are ready.",
            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
            color = descColor
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Reworked summary layout to be dense, detailed, and compact (using checklist borders and icons)
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SummaryItem("Theme Style", if (currentPrefs.useDynamicColor) "Material You" else "Custom Palette", isDark)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                SummaryItem("Nav Layout", if (currentPrefs.navMode == NavMode.STANDARD) "Standard" else "Floating", isDark)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                SummaryItem("Nav Style", if (currentPrefs.navType == NavType.LEGACY) "Legacy (Dots)" else "Modern (Squircles)", isDark)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                SummaryItem("Bar Rounding", "${currentPrefs.cornerRadius}dp", isDark)
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Back", color = MaterialTheme.colorScheme.primary)
            }

            Button(
                onClick = onFinished,
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("Setup Done! Let's explore Auriya", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ─── Shared components ───

/**
 * Enriched ThemeColorWidgetCompositionCard:
 * - Employs animateColorAsState to smoothly transition colors over 600ms when swatches change.
 * - Employs infinite transitions to create dynamic floating and progress indicator micro-animations.
 * - Draws mathematically perfect Material 3 Expressive shapes: 8-leaf Flower and M3 Heart on a Canvas.
 * - Displays a wide array of mock M3 testing widgets: Buttons, Switches, Chips, Sliders, and shapes.
 * - Added a mock CPU Temperature line graph path & mock message bubble (richer preview elements).
 */
@Composable
private fun ThemeColorWidgetCompositionCard(
    pulseScale: Float,
    pulseAlpha: Float
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    // 600ms smooth transition color states
    val animPrimary by animateColorAsState(targetValue = primaryColor, animationSpec = tween(600), label = "animPrimary")
    val animOnPrimary by animateColorAsState(targetValue = MaterialTheme.colorScheme.onPrimary, animationSpec = tween(600), label = "animOnPrimary")
    val animSecondary by animateColorAsState(targetValue = secondaryColor, animationSpec = tween(600), label = "animSecondary")
    val animOnSecondary by animateColorAsState(targetValue = MaterialTheme.colorScheme.onSecondary, animationSpec = tween(600), label = "animOnSecondary")
    val animSecondaryContainer by animateColorAsState(targetValue = MaterialTheme.colorScheme.secondaryContainer, animationSpec = tween(600), label = "animSecondaryContainer")
    val animTertiary by animateColorAsState(targetValue = tertiaryColor, animationSpec = tween(600), label = "animTertiary")
    val animOnTertiary by animateColorAsState(targetValue = MaterialTheme.colorScheme.onTertiary, animationSpec = tween(600), label = "animOnTertiary")
    val animSurfaceContainer by animateColorAsState(targetValue = MaterialTheme.colorScheme.surfaceContainer, animationSpec = tween(600), label = "animSurfaceContainer")
    val animOutlineVariant by animateColorAsState(targetValue = MaterialTheme.colorScheme.outlineVariant, animationSpec = tween(600), label = "animOutlineVariant")

    // Micro-animations infinite transition
    val infiniteTransition = rememberInfiniteTransition(label = "microAnims")
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = SineBow),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatOffset"
    )
    val progressVal by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "progressVal"
    )

    Surface(
        modifier = Modifier.fillMaxWidth().height(210.dp),
        shape = RoundedCornerShape(24.dp),
        color = animSurfaceContainer,
        border = BorderStroke(1.dp, animOutlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Left Column: Custom polar M3 shapes (Flower & Heart) drawn mathematically on Canvas + CPU line graph
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset(y = floatOffset.dp)
                ) {
                    val w = size.width
                    val h = size.height

                    // 1. M3 Flower Shape (8-leaf clover) on the left side
                    val flowerCenterX = w * 0.28f
                    val flowerCenterY = h * 0.35f
                    val flowerBaseRad = w * 0.15f
                    val flowerAmp = w * 0.035f
                    val flowerPath = Path()
                    val numPetals = 8
                    for (i in 0..360) {
                        val theta = (i * Math.PI.toFloat() / 180f)
                        val r = flowerBaseRad + flowerAmp * sin(numPetals * theta)
                        val x = flowerCenterX + r * cos(theta)
                        val y = flowerCenterY + r * sin(theta)
                        if (i == 0) flowerPath.moveTo(x, y) else flowerPath.lineTo(x, y)
                    }
                    flowerPath.close()

                    // Pulse ring around the flower
                    if (pulseAlpha > 0f) {
                        scale(scale = pulseScale, pivot = Offset(flowerCenterX, flowerCenterY)) {
                            drawPath(
                                path = flowerPath,
                                color = animPrimary.copy(alpha = pulseAlpha),
                                style = Stroke(width = 3.dp.toPx())
                            )
                        }
                    }
                    // Fill and outline
                    drawPath(path = flowerPath, color = animPrimary.copy(alpha = 0.22f))
                    drawPath(path = flowerPath, color = animPrimary, style = Stroke(width = 2.dp.toPx()))


                    // 2. M3 Heart Shape overlapping on the right side
                    val heartCenterX = w * 0.62f
                    val heartCenterY = h * 0.38f
                    val heartSize = w * 0.26f
                    val heartPath = Path()
                    val heartScale = heartSize / 32f
                    for (i in 0..360) {
                        val t = (i * Math.PI.toFloat() / 180f)
                        val sinT = sin(t)
                        val x = heartCenterX + (16f * sinT * sinT * sinT) * heartScale
                        val y = heartCenterY - (13f * cos(t) - 5f * cos(2f * t) - 2f * cos(3f * t) - cos(4f * t)) * heartScale
                        if (i == 0) heartPath.moveTo(x, y) else heartPath.lineTo(x, y)
                    }
                    heartPath.close()

                    // Pulse ring around the heart
                    if (pulseAlpha > 0f) {
                        scale(scale = pulseScale, pivot = Offset(heartCenterX, heartCenterY)) {
                            drawPath(
                                path = heartPath,
                                color = animTertiary.copy(alpha = pulseAlpha),
                                style = Stroke(width = 3.dp.toPx())
                            )
                        }
                    }
                    // Fill and outline
                    drawPath(path = heartPath, color = animTertiary.copy(alpha = 0.18f))
                    drawPath(path = heartPath, color = animTertiary, style = Stroke(width = 2.dp.toPx()))

                    // 3. Draw a mock CPU governor performance line graph at the bottom center of the Canvas
                    val graphPath = Path()
                    val graphStart = Offset(0f, h * 0.72f)
                    graphPath.moveTo(graphStart.x, graphStart.y)
                    graphPath.quadraticTo(w * 0.25f, h * 0.60f, w * 0.5f, h * 0.75f)
                    graphPath.quadraticTo(w * 0.75f, h * 0.88f, w * 0.95f, h * 0.65f)
                    drawPath(
                        path = graphPath,
                        color = animSecondary.copy(alpha = 0.8f),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }

                // Tiny dynamic progress bar underneath shapes
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomStart)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text("Optimizing", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = animPrimary)
                        Text("${(progressVal * 100).toInt()}%", fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = animPrimary)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { progressVal },
                        modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                        color = animPrimary,
                        trackColor = animOutlineVariant.copy(alpha = 0.3f)
                    )
                }
            }

            // Right Column: A comprehensive collection of Material 3 widgets + mock chat bubble
            Column(
                modifier = Modifier.weight(1.1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.End
            ) {
                // Mock Primary Filled Button with dynamic pulse outer ring
                Box(
                    modifier = Modifier.fillMaxWidth().height(36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (pulseAlpha > 0f) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                                .scale(scaleX = pulseScale, scaleY = pulseScale),
                            shape = CircleShape,
                            color = Color.Transparent,
                            border = BorderStroke(2.dp, animPrimary.copy(alpha = pulseAlpha))
                        ) {}
                    }
                    Button(
                        onClick = {},
                        shape = CircleShape,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = animPrimary,
                            contentColor = animOnPrimary
                        )
                    ) {
                        Text("Primary Filled", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Mock Tonal Button
                Button(
                    onClick = {},
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(34.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = animSecondaryContainer,
                        contentColor = animSecondary
                    )
                ) {
                    Text("Tonal Style", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }

                // Mock Chat Bubble colored in tertiary Monet (richer element)
                Surface(
                    shape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 10.dp, bottomEnd = 2.dp),
                    color = animTertiary,
                    modifier = Modifier.fillMaxWidth().height(26.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Text("Auriya core active", color = animOnTertiary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Mock Chip & Switch Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = animSecondary.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, animSecondary.copy(alpha = 0.4f)),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                            Text("Chip", color = animSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Switch(
                        checked = true,
                        onCheckedChange = {},
                        modifier = Modifier.scale(0.75f),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = animOnPrimary,
                            checkedTrackColor = animPrimary
                        )
                    )
                }

                // Mock Slider
                Column(modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
                    Slider(
                        value = 0.65f,
                        onValueChange = {},
                        modifier = Modifier.fillMaxWidth().height(16.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = animPrimary,
                            activeTrackColor = animPrimary,
                            inactiveTrackColor = animOutlineVariant.copy(alpha = 0.3f)
                        )
                    )
                }
            }
        }
    }
}

// Sine easing transition curve for smooth bouncing/floating
private val SineBow = Easing { fraction ->
    val t = fraction * 2f * Math.PI.toFloat()
    (1f - cos(t)) / 2f
}

// Detailed, compact checklist row item for Done Screen
@Composable
private fun SummaryItem(label: String, value: String, isDark: Boolean) {
    val labelColor = if (isDark) Color.White.copy(alpha = 0.7f) else Color(0xFF49454F)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, color = labelColor)
        }
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}

// Quadrant-divided color swatch dots matching Screenshot 2
@Composable
private fun SwatchDot(
    item: PaletteItem,
    selected: Boolean,
    pulseScale: Float,
    pulseAlpha: Float,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier.size(54.dp), // handle pulsating ring size bounds
        contentAlignment = Alignment.Center
    ) {
        if (selected && pulseAlpha > 0f) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .scale(pulseScale)
                    .border(BorderStroke(2.dp, item.primary.copy(alpha = pulseAlpha)), CircleShape)
            )
        }
        Canvas(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .clickable(onClick = onClick)
        ) {
            // Draw 4 arcs representing Monet palette tone quadrants (Screenshot 2)
            drawArc(color = item.primary, startAngle = 180f, sweepAngle = 90f, useCenter = true)
            drawArc(color = item.secondary, startAngle = 270f, sweepAngle = 90f, useCenter = true)
            drawArc(color = item.tertiary, startAngle = 0f, sweepAngle = 90f, useCenter = true)
            drawArc(color = item.neutral, startAngle = 90f, sweepAngle = 90f, useCenter = true)
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(BorderStroke(1.dp, item.primary), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Check, null, tint = item.primary, modifier = Modifier.size(12.dp))
            }
        }
    }
}

@Composable
private fun SegmentedControl(
    items: List<String>,
    selectedIndex: Int,
    isDark: Boolean,
    onItemSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(4.dp)
    ) {
        items.forEachIndexed { index, title ->
            Box(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                    .background(if (selectedIndex == index) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onItemSelected(index) }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = if (selectedIndex == index) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (selectedIndex == index) MaterialTheme.colorScheme.onPrimary
                            else if (isDark) Color.White.copy(alpha = 0.7f) else Color(0xFF49454F)
                )
            }
        }
    }
}

// LiveUiPreviewCard: Dynamic multi-widget previewing sharp vs rounded styling (Step 5) - 220dp Height
@Composable
private fun LiveUiPreviewCard(
    seedColor: Int, useDynamicColor: Boolean,
    navMode: NavMode, navType: NavType, cornerRadius: Int
) {
    val shapeRadius = cornerRadius.dp
    val subRadius = (cornerRadius / 2f).dp
    val miniRadius = (cornerRadius / 3f).dp

    Surface(
        modifier = Modifier.fillMaxWidth().height(220.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Top Row: Avatar icon, description lines, and mock Apply button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Profile Avatar
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(shapeRadius))
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("*", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Box(modifier = Modifier.size(width = 80.dp, height = 8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)))
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(modifier = Modifier.size(width = 50.dp, height = 5.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.13f)))
                        }
                    }

                    // Apply button
                    Surface(
                        shape = RoundedCornerShape(shapeRadius),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(width = 68.dp, height = 28.dp)
                            .clickable {}
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                "Apply",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // 2. Middle Row: 4 horizontal quick settings tiles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(4) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(28.dp)
                                .clip(RoundedCornerShape(subRadius))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                        )
                    }
                }

                // 3. Lower Middle Row: Full width settings container card (list row)
                Surface(
                    shape = RoundedCornerShape(shapeRadius),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    modifier = Modifier.fillMaxWidth().height(42.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(RoundedCornerShape(miniRadius))
                                    .background(MaterialTheme.colorScheme.primary)
                              )
                              Spacer(modifier = Modifier.width(8.dp))
                              Box(
                                  modifier = Modifier
                                      .size(width = 72.dp, height = 7.dp)
                                      .clip(RoundedCornerShape(3.dp))
                                      .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                              )
                          }
                          // Mock switch shape
                          Box(
                              modifier = Modifier
                                  .size(width = 28.dp, height = 16.dp)
                                  .clip(RoundedCornerShape(8.dp))
                                  .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                          )
                      }
                  }
              }

              // 4. System Bottom Navigation Bar
              Box(
                  modifier = Modifier
                      .fillMaxWidth()
                      .align(Alignment.BottomCenter)
                      .padding(bottom = if (navMode == NavMode.FLOATING) 6.dp else 0.dp),
                  contentAlignment = Alignment.Center
              ) {
                  val capsule = RoundedCornerShape(cornerRadius.dp / 2f)
                  Box(
                      modifier = Modifier
                          .fillMaxWidth(if (navMode == NavMode.FLOATING) 0.65f else 1f)
                          .height(24.dp)
                          .clip(capsule)
                          .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                      contentAlignment = Alignment.Center
                  ) {
                      Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                          repeat(3) { index ->
                              val sel = index == 0
                              Box(
                                  modifier = Modifier
                                      .size(if (sel) 10.dp else 6.dp)
                                      .clip(if (navType == NavType.MODERN) RoundedCornerShape(2.dp) else CircleShape)
                                      .background(
                                          if (sel) MaterialTheme.colorScheme.primary
                                          else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                      )
                              )
                          }
                      }
                  }
              }
          }
      }
  }
