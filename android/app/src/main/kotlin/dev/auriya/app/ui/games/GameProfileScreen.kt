package dev.auriya.app.ui.games

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.auriya.app.ui.components.ExpressiveList
import dev.auriya.app.ui.components.StatusBadge
import dev.auriya.app.ui.components.StatusTone
import dev.auriya.app.ui.components.rememberCookie9
import dev.auriya.app.ui.theme.AuriyaTokens
import dev.auriya.shared.model.GameProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameProfileScreen(
    game: GameProfile,
    onDismiss: () -> Unit,
    onSave: (GameProfile) -> Unit,
) {
    val context = LocalContext.current
    val pm = context.packageManager
    val appLabel = remember(game.packageName) {
        runCatching {
            pm.getApplicationLabel(pm.getApplicationInfo(game.packageName, 0)).toString()
        }.getOrDefault(game.packageName.substringAfterLast('.'))
    }
    val iconPainter = rememberAppIconPainter(game.packageName)

    var selectedGov by remember { mutableStateOf(game.cpuGovernor) }
    var targetFps by remember { mutableStateOf(game.targetFps?.toFloat() ?: 60f) }
    var refreshRate by remember { mutableStateOf(game.refreshRate?.toFloat() ?: 0f) }
    var enableDnd by remember { mutableStateOf(game.enableDnd) }
    var killBackground by remember { mutableStateOf(false) }
    var autoRotate by remember { mutableStateOf(false) }
    var blockNotifications by remember { mutableStateOf(false) }

    var govDropdownExpanded by remember { mutableStateOf(false) }
    val governorOptions = listOf("performance", "schedutil", "interactive", "powersave")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile Tuning", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            StickyActions(
                onSave = {
                    onSave(
                        GameProfile(
                            packageName = game.packageName,
                            cpuGovernor = selectedGov,
                            enableDnd = enableDnd,
                            targetFps = targetFps.toInt(),
                            refreshRate = if (refreshRate.toInt() == 0) null else refreshRate.toInt(),
                        )
                    )
                },
                onReset = {
                    selectedGov = "performance"
                    targetFps = 60f
                    refreshRate = 0f
                    enableDnd = true
                    killBackground = false
                    autoRotate = false
                    blockNotifications = false
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AuriyaTokens.padding.normal),
            verticalArrangement = Arrangement.spacedBy(AuriyaTokens.padding.normal),
        ) {
            HeroHeader(
                label = appLabel,
                packageName = game.packageName,
                iconPainter = iconPainter,
                targetFps = targetFps.toInt(),
                dnd = enableDnd,
                gov = selectedGov,
            )

            SectionLabel("Performance")
            ExpressiveList(count = 3) { index ->
                when (index) {
                    0 -> GovernorRow(
                        selected = selectedGov,
                        expanded = govDropdownExpanded,
                        onExpand = { govDropdownExpanded = it },
                        onSelect = { selectedGov = it; govDropdownExpanded = false },
                        options = governorOptions,
                    )
                    1 -> SliderRow(
                        title = "Target FPS limit",
                        value = targetFps,
                        onChange = { targetFps = it },
                        range = 30f..120f,
                        steps = 5,
                        valueLabel = "${targetFps.toInt()} FPS",
                    )
                    2 -> SliderRow(
                        title = "Screen refresh rate",
                        value = refreshRate,
                        onChange = { refreshRate = it },
                        range = 0f..120f,
                        steps = 3,
                        valueLabel = if (refreshRate.toInt() == 0) "System default" else "${refreshRate.toInt()} Hz",
                    )
                }
            }

            SectionLabel("System triggers")
            ExpressiveList(count = 4) { index ->
                when (index) {
                    0 -> SwitchRow(
                        title = "Do Not Disturb",
                        subtitle = "Priority notifications on launch",
                        checked = enableDnd,
                        onCheck = { enableDnd = it },
                    )
                    1 -> SwitchRow(
                        title = "Auto-rotate lock",
                        subtitle = "Force landscape during game",
                        checked = autoRotate,
                        onCheck = { autoRotate = it },
                    )
                    2 -> SwitchRow(
                        title = "Kill background apps",
                        subtitle = "Free RAM before launch",
                        checked = killBackground,
                        onCheck = { killBackground = it },
                    )
                    3 -> SwitchRow(
                        title = "Block notifications",
                        subtitle = "Silence all incoming",
                        checked = blockNotifications,
                        onCheck = { blockNotifications = it },
                    )
                }
            }

            Spacer(Modifier.height(AuriyaTokens.padding.normal))
        }
    }
}

@Composable
private fun HeroHeader(
    label: String,
    packageName: String,
    iconPainter: androidx.compose.ui.graphics.painter.Painter?,
    targetFps: Int,
    dnd: Boolean,
    gov: String,
) {
    Surface(
        shape = RoundedCornerShape(AuriyaTokens.rounding.extraLarge),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                        )
                    )
                )
                .padding(AuriyaTokens.padding.larger),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(AuriyaTokens.padding.small)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(rememberCookie9())
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (iconPainter != null) {
                            Image(painter = iconPainter, contentDescription = null, modifier = Modifier.fillMaxSize())
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.SportsEsports,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(AuriyaTokens.iconSize.large),
                            )
                        }
                    }
                    Spacer(Modifier.width(AuriyaTokens.padding.normal))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = packageName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(AuriyaTokens.padding.smallest)) {
                    StatusBadge(label = "Active", tone = StatusTone.SUCCESS)
                    StatusBadge(label = "$targetFps FPS", tone = StatusTone.SECONDARY)
                    if (dnd) StatusBadge(label = "DnD", tone = StatusTone.WARNING)
                    StatusBadge(label = gov, tone = StatusTone.OUTLINE)
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(label: String) {
    Text(
        text = label.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = AuriyaTokens.padding.small),
    )
}

@Composable
private fun GovernorRow(
    selected: String,
    expanded: Boolean,
    onExpand: (Boolean) -> Unit,
    onSelect: (String) -> Unit,
    options: List<String>,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AuriyaTokens.padding.normal, vertical = AuriyaTokens.padding.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "CPU Governor",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Current scheduling policy",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            Surface(
                onClick = { onExpand(true) },
                shape = RoundedCornerShape(AuriyaTokens.rounding.full),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = AuriyaTokens.padding.small, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = selected.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(AuriyaTokens.iconSize.medium),
                    )
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { onExpand(false) }) {
                options.forEach { opt ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = opt.uppercase(),
                                fontWeight = if (opt == selected) FontWeight.ExtraBold else FontWeight.Normal,
                                color = if (opt == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                        },
                        onClick = { onSelect(opt) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SliderRow(
    title: String,
    value: Float,
    onChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueLabel: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AuriyaTokens.padding.normal, vertical = AuriyaTokens.padding.small),
        verticalArrangement = Arrangement.spacedBy(AuriyaTokens.padding.smallest),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            StatusBadge(label = valueLabel, tone = StatusTone.PRIMARY)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheck: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AuriyaTokens.padding.normal, vertical = AuriyaTokens.padding.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheck)
    }
}

@Composable
private fun StickyActions(onSave: () -> Unit, onReset: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AuriyaTokens.padding.normal, vertical = AuriyaTokens.padding.small),
            verticalArrangement = Arrangement.spacedBy(AuriyaTokens.padding.smaller),
        ) {
            Button(
                onClick = onSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(AuriyaTokens.rounding.full),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(AuriyaTokens.iconSize.medium))
                Spacer(Modifier.width(AuriyaTokens.padding.smaller))
                Text(
                    text = "Save & Apply",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
            TextButton(
                onClick = onReset,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Reset to defaults", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
