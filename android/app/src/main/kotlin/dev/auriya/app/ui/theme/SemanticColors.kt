package dev.auriya.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color


@Immutable
data class AuriyaSemanticColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
)

internal val LightSemanticColors = AuriyaSemanticColors(
    success = AuriyaSuccessLight,
    onSuccess = AuriyaOnSuccessLight,
    successContainer = AuriyaSuccessContainerLight,
    onSuccessContainer = AuriyaOnSuccessContainerLight,
    warning = AuriyaWarningLight,
    onWarning = AuriyaOnWarningLight,
    warningContainer = AuriyaWarningContainerLight,
    onWarningContainer = AuriyaOnWarningContainerLight,
)

internal val DarkSemanticColors = AuriyaSemanticColors(
    success = AuriyaSuccessDark,
    onSuccess = AuriyaOnSuccessDark,
    successContainer = AuriyaSuccessContainerDark,
    onSuccessContainer = AuriyaOnSuccessContainerDark,
    warning = AuriyaWarningDark,
    onWarning = AuriyaOnWarningDark,
    warningContainer = AuriyaWarningContainerDark,
    onWarningContainer = AuriyaOnWarningContainerDark,
)

val LocalAuriyaSemanticColors = compositionLocalOf { LightSemanticColors }
