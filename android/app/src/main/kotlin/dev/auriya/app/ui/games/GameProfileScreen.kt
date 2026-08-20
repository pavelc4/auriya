package dev.auriya.app.ui.games

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.auriya.app.data.stats.AutoRecordPrefs
import dev.auriya.app.ui.components.AuriyaDragHandle
import dev.auriya.app.ui.components.ExpressiveList
import dev.auriya.app.ui.components.StatusBadge
import dev.auriya.app.ui.components.StatusTone
import dev.auriya.app.ui.theme.AuriyaTokens
import dev.auriya.shared.model.GameProfile

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
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
    val autoRecordPrefs = remember { AutoRecordPrefs(context) }
    var autoRecord by remember(game.packageName) {
        mutableStateOf(autoRecordPrefs.isAutoRecordEnabled(game.packageName))
    }
    val appLabel =
        remember(game.packageName) {
            runCatching {
                pm.getApplicationLabel(pm.getApplicationInfo(game.packageName, 0)).toString()
            }.getOrDefault(game.packageName.substringAfterLast('.'))
        }
    val iconBitmap = rememberAppIcon(game.packageName)

    val initialGov = game.cpuGovernor.ifEmpty {
        governorOptions.firstOrNull() ?: "schedutil"
    }
    var selectedGov by remember(game.packageName, initialGov) { mutableStateOf(initialGov) }
    var targetFps by remember(game.packageName, game.targetFps) { mutableStateOf(game.targetFps?.toFloat() ?: 60f) }
    var refreshRate by remember(game.packageName, game.refreshRate) { mutableStateOf(game.refreshRate?.toFloat() ?: 0f) }
    var enableDnd by remember(game.packageName, game.enableDnd) { mutableStateOf(game.enableDnd) }
    var selectedCeiling by remember(game.packageName, game.ceiling) { mutableStateOf(game.ceiling ?: "default") }

    val ceilingOptions = remember { listOf("default", "low", "balance", "high") }
    val effectiveGovOptions = remember(governorOptions, selectedGov) {
        if (selectedGov.isNotBlank() && selectedGov !in governorOptions) {
            listOf(selectedGov) + governorOptions
        } else {
            governorOptions.ifEmpty { listOf("schedutil", "performance", "powersave") }
        }
    }

    var showGovSheet by remember { mutableStateOf(false) }
    var showCeilingSheet by remember { mutableStateOf(false) }
    var showActionsSheet by remember { mutableStateOf(false) }

    val govSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val ceilingSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val actionsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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

    if (showGovSheet) {
        GovernorSelectionBottomSheet(
            selectedGov = selectedGov,
            options = effectiveGovOptions,
            onSelect = { updateAndSave(gov = it) },
            onDismiss = { showGovSheet = false },
            sheetState = govSheetState,
        )
    }

    if (showCeilingSheet) {
        CeilingSelectionBottomSheet(
            selectedCeiling = selectedCeiling,
            options = ceilingOptions,
            onSelect = { updateAndSave(ceiling = it) },
            onDismiss = { showCeilingSheet = false },
            sheetState = ceilingSheetState,
        )
    }

    if (showActionsSheet) {
        ProfileActionsBottomSheet(
            appLabel = appLabel,
            isExistingProfile = isExistingProfile,
            onReset = {
                val defaultGov = governorOptions.firstOrNull() ?: game.cpuGovernor
                updateAndSave(
                    gov = defaultGov,
                    ceiling = "default",
                    fps = 60f,
                    refresh = 0f,
                    dnd = true
                )
            },
            onRemove = if (isExistingProfile && onRemove != null) {
                { pendingDelete = true }
            } else null,
            onDismiss = { showActionsSheet = false },
            sheetState = actionsSheetState,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
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

            FilledIconButton(
                onClick = { showActionsSheet = true },
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.size(42.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "Profile Actions",
                    modifier = Modifier.size(20.dp)
                )
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
                                onClick = { showGovSheet = true },
                            )
                            1 -> CeilingRow(
                                selected = selectedCeiling,
                                onClick = { showCeilingSheet = true },
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
                    ExpressiveList(count = 2) { index ->
                        when (index) {
                            0 -> SwitchRow(
                                title = "Do Not Disturb",
                                subtitle = "Priority notifications on launch",
                                checked = enableDnd,
                                onCheck = { updateAndSave(dnd = it) },
                            )
                            1 -> SwitchRow(
                                title = "Auto Record FPS",
                                subtitle = "Record benchmark session automatically on game launch",
                                checked = autoRecord,
                                onCheck = {
                                    autoRecord = it
                                    autoRecordPrefs.setAutoRecordEnabled(game.packageName, it)
                                },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileActionsBottomSheet(
    appLabel: String,
    isExistingProfile: Boolean,
    onReset: () -> Unit,
    onRemove: (() -> Unit)?,
    onDismiss: () -> Unit,
    sheetState: SheetState,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { AuriyaDragHandle() },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 48.dp)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Profile Actions",
                        style = dev.auriya.app.ui.theme.ExpTitleTypography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 28.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Manage configurations for $appLabel",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Card 1: Reset to Defaults
            item {
                Surface(
                    onClick = {
                        onReset()
                        onDismiss()
                    },
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.Refresh,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Reset to Defaults",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "Restore default governor, 60 FPS cap, and system defaults.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Card 2: Delete / Remove Profile
            if (isExistingProfile && onRemove != null) {
                item {
                    Surface(
                        onClick = {
                            onDismiss()
                            onRemove()
                        },
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Outlined.DeleteOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onError,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Remove Profile",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "Delete saved tuning profile and revert to global policy.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GovernorSelectionBottomSheet(
    selectedGov: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { AuriyaDragHandle() },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 48.dp)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "CPU Governor",
                        style = dev.auriya.app.ui.theme.ExpTitleTypography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 28.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Select scheduling frequency policy for this app",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            items(options) { opt ->
                val isSelected = opt.equals(selectedGov, ignoreCase = true)
                val (icon, subtitle) = getGovernorInfo(opt)

                Surface(
                    onClick = {
                        onSelect(opt)
                        onDismiss()
                    },
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = opt.uppercase(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurface
                                )
                                if (isSelected) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                    ) {
                                        Text(
                                            text = "ACTIVE",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CeilingSelectionBottomSheet(
    selectedCeiling: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { AuriyaDragHandle() },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 48.dp)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "CPU Ceiling",
                        style = dev.auriya.app.ui.theme.ExpTitleTypography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 28.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Set max power cap and thermal throttling threshold",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            items(options) { opt ->
                val isSelected = opt.equals(selectedCeiling, ignoreCase = true)
                val (icon, subtitle) = getCeilingInfo(opt)

                Surface(
                    onClick = {
                        onSelect(opt)
                        onDismiss()
                    },
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.tertiaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (isSelected) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.onTertiary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = opt.uppercase(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.onTertiaryContainer
                                    else MaterialTheme.colorScheme.onSurface
                                )
                                if (isSelected) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.tertiary,
                                    ) {
                                        Text(
                                            text = "ACTIVE",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onTertiary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isSelected) MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroHeader(
    label: String,
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
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
                        modifier = Modifier.size(30.dp),
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CompactMetricChip(
                        icon = Icons.Outlined.Speed,
                        text = "$targetFps FPS",
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )

                    CompactMetricChip(
                        icon = Icons.Outlined.Tune,
                        text = gov.uppercase(),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    if (ceiling != "default") {
                        CompactMetricChip(
                            icon = Icons.Outlined.LocalFireDepartment,
                            text = ceiling.uppercase(),
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }

                    if (dnd) {
                        CompactMetricChip(
                            icon = Icons.Outlined.DoNotDisturbOn,
                            text = "DnD",
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactMetricChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(11.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = contentColor,
                fontSize = 10.sp
            )
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
    onClick: () -> Unit,
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
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(AuriyaTokens.rounding.full),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
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
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun CeilingRow(
    selected: String,
    onClick: () -> Unit,
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
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(AuriyaTokens.rounding.full),
            color = MaterialTheme.colorScheme.tertiaryContainer,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
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
                    modifier = Modifier.size(20.dp),
                )
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
        Switch(
            checked = checked,
            onCheckedChange = onCheck,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
            )
        )
    }
}

private fun getGovernorInfo(gov: String): Pair<androidx.compose.ui.graphics.vector.ImageVector, String> {
    val name = gov.lowercase().trim()
    return when (name) {
        "performance" -> Icons.Outlined.Speed to "Locks CPU clusters to maximum operating frequencies for maximum throughput."
        "powersave" -> Icons.Outlined.BatterySaver to "Locks CPU to lowest frequencies to conserve battery and minimize thermals."
        "schedutil" -> Icons.Outlined.Tune to "Energy-Aware Scheduling governor scaling frequencies dynamically via task load."
        "walt" -> Icons.Outlined.Analytics to "Qualcomm Window-Assisted Load Tracking predicting workload demand history."
        "conservative" -> Icons.AutoMirrored.Filled.TrendingDown to "Gradual step-by-step frequency scaling prioritizing battery longevity."
        "ondemand" -> Icons.Outlined.Bolt to "Rapidly jumps to maximum frequency on CPU load spikes, then steps down."
        "interactive" -> Icons.Outlined.TouchApp to "Responsive scaling tailored for low latency and smooth UI touch response."
        "userspace" -> Icons.Outlined.Tune to "Allows manual frequency control by userspace daemons and external tools."
        "blu_schedutil", "blu_active" -> Icons.Outlined.Tune to "Tuned EAS governor by eng.stk balancing smooth frametimes and efficiency."
        "helix_schedutil" -> Icons.Outlined.Tune to "Energy-Aware Scheduling governor tuned for responsive UI and reduced power spikes."
        "electroutil" -> Icons.Outlined.EnergySavingsLeaf to "Schedutil tuning designed for battery efficiency and low frametime jitter."
        "pwrutilx", "pwrutil" -> Icons.Outlined.BatterySaver to "Power-focused schedutil variant designed for extended battery endurance."
        "elementalx" -> Icons.Outlined.Speed to "ElementalX custom governor balancing rapid touch response with battery preservation."
        "alucard" -> Icons.Outlined.Speed to "Custom governor with aggressive frequency ramp-up on high computational loads."
        "darkness", "nightmare" -> Icons.Outlined.Speed to "Aggressive scaling governor prioritizing rapid task completion and fast idle."
        "impulse" -> Icons.Outlined.Bolt to "Tuned interactive governor delivering instant frequency bursts for responsiveness."
        "ironactive" -> Icons.Outlined.EnergySavingsLeaf to "Interactive variant modified for aggressive power conservation and smooth scaling."
        "zzmoove" -> Icons.Outlined.Tune to "Dynamic multi-profile governor that adapts frequency scaling based on load patterns."
        "smartmax", "smartmax_eps" -> Icons.Outlined.EnergySavingsLeaf to "Custom governor tuned for UI smoothness with strict battery caps."
        "wheatley" -> Icons.Outlined.EnergySavingsLeaf to "Governor designed to maximize CPU C-state sleep duration during active sessions."
        "pegasusq" -> Icons.Outlined.Tune to "Multi-core aware governor that dynamically manages core hotplugging and scaling."
        "cultivation", "cultivation_schedutil" -> Icons.Outlined.SportsEsports to "Gaming-focused governor optimized for stable 3D framerates and frametime pacing."
        "bioshock" -> Icons.Outlined.Speed to "Custom multi-tiered governor designed for snappy responsiveness under gaming loads."
        "yankactive", "yankbattery" -> Icons.Outlined.BatterySaver to "Battery-centric governor tuned for endurance and conservative frequency bumps."
        "smartass", "smartassv2" -> Icons.Outlined.TouchApp to "Smart interactive governor with dedicated screen-on and deep-sleep states."
        else -> when {
            "sched" in name || "util" in name -> Icons.Outlined.Tune to "Scheduler-based frequency scaling policy."
            "save" in name || "eco" in name || "pwr" in name || "batt" in name -> Icons.Outlined.BatterySaver to "Power-saving frequency policy designed to minimize battery drain."
            "perf" in name || "boost" in name || "turbo" in name || "max" in name -> Icons.Outlined.Speed to "High-performance frequency scaling policy."
            "active" in name || "interact" in name || "touch" in name -> Icons.Outlined.TouchApp to "Interactive scaling policy responsive to UI and input events."
            "game" in name || "gaming" in name -> Icons.Outlined.SportsEsports to "Gaming-tuned governor policy optimized for steady frametimes."
            "chill" in name || "cool" in name || "thermal" in name -> Icons.Outlined.EnergySavingsLeaf to "Thermal-focused frequency policy prioritizing cool operation."
            else -> Icons.Outlined.Tune to "Kernel CPU governor scheduling policy."
        }
    }
}

private fun getCeilingInfo(ceiling: String): Pair<androidx.compose.ui.graphics.vector.ImageVector, String> {
    val name = ceiling.lowercase().trim()
    return when (name) {
        "default" -> Icons.Outlined.Tune to "Vendor kernel default power capping and thermal limits."
        "low" -> Icons.Outlined.EnergySavingsLeaf to "Strict thermal cap for maximum battery conservation and cool thermals."
        "balance" -> Icons.Outlined.Balance to "Balanced power limit balancing sustained FPS and thermal dissipation."
        "high" -> Icons.Outlined.LocalFireDepartment to "High burst power ceiling allowing sustained peak graphical performance."
        else -> Icons.Outlined.Tune to "Custom cluster frequency ceiling cap."
    }
}
