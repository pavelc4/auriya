package dev.auriya.app.ui.games

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.auriya.app.ui.components.ExpressiveList
import dev.auriya.app.ui.components.StatusBadge
import dev.auriya.app.ui.components.StatusTone
import dev.auriya.app.ui.components.rememberCookie9
import dev.auriya.app.ui.theme.AuriyaTokens
import dev.auriya.shared.model.GameProfile

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GameProfileScreen(
    game: GameProfile,
    governorOptions: List<String>,
    isExistingProfile: Boolean = true,
    onDismiss: () -> Unit,
    onSave: (GameProfile) -> Unit,
    onRemove: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val pm = context.packageManager
    val appLabel =
        remember(game.packageName) {
            runCatching {
                pm.getApplicationLabel(pm.getApplicationInfo(game.packageName, 0)).toString()
            }.getOrDefault(game.packageName.substringAfterLast('.'))
        }
    val iconBitmap = rememberAppIcon(game.packageName)

    val initialGov =
        if (game.cpuGovernor in governorOptions) {
            game.cpuGovernor
        } else {
            governorOptions.firstOrNull() ?: game.cpuGovernor
        }
    var selectedGov by remember(initialGov) { mutableStateOf(initialGov) }
    var targetFps by remember { mutableStateOf(game.targetFps?.toFloat() ?: 60f) }
    var refreshRate by remember { mutableStateOf(game.refreshRate?.toFloat() ?: 0f) }
    var enableDnd by remember { mutableStateOf(game.enableDnd) }
    var selectedCeiling by remember { mutableStateOf(game.ceiling ?: "default") }

    val ceilingOptions = remember { listOf("default", "low", "balance", "high") }

    var govDropdownExpanded by remember { mutableStateOf(false) }
    var ceilingDropdownExpanded by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf(false) }

    fun updateAndSave(
        gov: String = selectedGov,
        ceiling: String = selectedCeiling,
        fps: Float = targetFps,
        refresh: Float = refreshRate,
        dnd: Boolean = enableDnd,
    ) {
        selectedGov = gov
        selectedCeiling = ceiling
        targetFps = fps
        refreshRate = refresh
        enableDnd = dnd
        onSave(
            GameProfile(
                packageName = game.packageName,
                cpuGovernor = gov,
                enableDnd = dnd,
                targetFps = fps.toInt(),
                refreshRate = if (refresh.toInt() == 0) null else refresh.toInt(),
                ceiling = if (ceiling == "default") null else ceiling,
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .statusBarsPadding()
    ) {
        // --- 1. TOP PINNED HEADER (Backdrop layer) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                FilledIconButton(
                    onClick = onDismiss,
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        text = "Profile Tuning",
                        style = dev.auriya.app.ui.theme.ExpTitleTypography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 24.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    Text(
                        text = "Per-game optimization settings",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Box {
                FilledIconButton(
                    onClick = { menuExpanded = true },
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "More options",
                        modifier = Modifier.size(20.dp)
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    shape = RoundedCornerShape(16.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    DropdownMenuItem(
                        text = { Text("Reset to defaults") },
                        leadingIcon = {
                            Icon(Icons.Outlined.Refresh, contentDescription = null)
                        },
                        onClick = {
                            menuExpanded = false
                            val defaultGov = governorOptions.firstOrNull() ?: game.cpuGovernor
                            updateAndSave(
                                gov = defaultGov,
                                ceiling = "default",
                                fps = 60f,
                                refresh = 0f,
                                dnd = true
                            )
                        }
                    )
                    if (isExistingProfile && onRemove != null) {
                        DropdownMenuItem(
                            text = { Text("Remove profile", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.DeleteOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                pendingDelete = true
                            }
                        )
                    }
                }
            }
        }

        // --- 2. FOREGROUND STACKED CARD SHEET ---
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Hero Header Card
                item {
                    HeroHeader(
                        label = appLabel,
                        packageName = game.packageName,
                        iconBitmap = iconBitmap,
                        targetFps = targetFps.toInt(),
                        dnd = enableDnd,
                        gov = selectedGov,
                        ceiling = selectedCeiling,
                    )
                }

                // Section 1: Performance
                item {
                    SectionLabel("Performance")
                }

                item {
                    ExpressiveList(count = 4) { index ->
                        when (index) {
                            0 -> GovernorRow(
                                selected = selectedGov,
                                expanded = govDropdownExpanded,
                                onExpand = { govDropdownExpanded = it },
                                onSelect = {
                                    govDropdownExpanded = false
                                    updateAndSave(gov = it)
                                },
                                options = governorOptions,
                            )
                            1 -> CeilingRow(
                                selected = selectedCeiling,
                                expanded = ceilingDropdownExpanded,
                                onExpand = { ceilingDropdownExpanded = it },
                                onSelect = {
                                    ceilingDropdownExpanded = false
                                    updateAndSave(ceiling = it)
                                },
                                options = ceilingOptions,
                            )
                            2 -> SliderRow(
                                title = "Target FPS limit",
                                value = targetFps,
                                onChange = { targetFps = it },
                                onValueChangeFinished = { updateAndSave(fps = targetFps) },
                                range = 30f..120f,
                                steps = 5,
                                valueLabel = "${targetFps.toInt()} FPS",
                            )
                            3 -> SliderRow(
                                title = "Screen refresh rate",
                                value = refreshRate,
                                onChange = { refreshRate = it },
                                onValueChangeFinished = { updateAndSave(refresh = refreshRate) },
                                range = 0f..120f,
                                steps = 3,
                                valueLabel = if (refreshRate.toInt() == 0) "System default" else "${refreshRate.toInt()} Hz",
                            )
                        }
                    }
                }

                // Section 2: System Triggers
                item {
                    SectionLabel("System triggers")
                }

                item {
                    ExpressiveList(count = 1) { index ->
                        when (index) {
                            0 -> SwitchRow(
                                title = "Do Not Disturb",
                                subtitle = "Priority notifications on launch",
                                checked = enableDnd,
                                onCheck = { updateAndSave(dnd = it) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (pendingDelete && onRemove != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = false },
            title = { Text("Remove profile?") },
            text = {
                Text("$appLabel will be removed from the active profile list. You can re-add it from the Games tab.")
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = false
                    onRemove()
                }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = false }) { Text("Cancel") }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroHeader(
    label: String,
    packageName: String,
    iconBitmap: androidx.compose.ui.graphics.ImageBitmap?,
    targetFps: Int,
    dnd: Boolean,
    gov: String,
    ceiling: String,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(rememberCookie9())
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    if (iconBitmap != null) {
                        Image(bitmap = iconBitmap, contentDescription = null, modifier = Modifier.fillMaxSize())
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.SportsEsports,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StatusBadge(label = "Active", tone = StatusTone.SUCCESS)
                StatusBadge(label = "$targetFps FPS", tone = StatusTone.SECONDARY)
                if (dnd) StatusBadge(label = "DnD", tone = StatusTone.WARNING)
                StatusBadge(label = gov.uppercase(), tone = StatusTone.PRIMARY)
                if (ceiling != "default") StatusBadge(label = ceiling.replaceFirstChar { it.uppercase() }, tone = StatusTone.PRIMARY)
            }
        }
    }
}

@Composable
private fun SectionLabel(label: String) {
    Text(
        text = label.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = AuriyaTokens.padding.small, top = 4.dp),
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
private fun CeilingRow(
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
                text = "CPU Ceiling",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Max power cap level",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            Surface(
                onClick = { onExpand(true) },
                shape = RoundedCornerShape(AuriyaTokens.rounding.full),
                color = MaterialTheme.colorScheme.tertiaryContainer,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = AuriyaTokens.padding.small, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = selected.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
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
    onValueChangeFinished: (() -> Unit)? = null,
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
            onValueChangeFinished = onValueChangeFinished,
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
