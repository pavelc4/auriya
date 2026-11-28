package dev.auriya.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.SemanticsModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.launch

private val BounceSpring =
    spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow,
    )

/**
 * Adds a responsive spring bouncy scale effect on click / press backed by Modifier.Node
 * to avoid per-item subcomposition overhead.
 */
fun Modifier.bouncyClickable(
    enabled: Boolean = true,
    scaleDown: Float = 0.96f,
    onClick: () -> Unit,
): Modifier = this then BouncyClickableElement(enabled = enabled, scaleDown = scaleDown, onClick = onClick)

private data class BouncyClickableElement(
    val enabled: Boolean,
    val scaleDown: Float,
    val onClick: () -> Unit,
) : ModifierNodeElement<BouncyClickableNode>() {
    override fun create(): BouncyClickableNode =
        BouncyClickableNode(enabled = enabled, scaleDown = scaleDown, onClick = onClick)

    override fun update(node: BouncyClickableNode) {
        node.update(enabled = enabled, scaleDown = scaleDown, onClick = onClick)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "bouncyClickable"
        properties["enabled"] = enabled
        properties["scaleDown"] = scaleDown
    }
}

private class BouncyClickableNode(
    private var enabled: Boolean,
    private var scaleDown: Float,
    private var onClick: () -> Unit,
) : DelegatingNode(),
    DrawModifierNode,
    SemanticsModifierNode {
    override val shouldAutoInvalidate: Boolean = false

    private val scale = Animatable(1f)

    @Suppress("unused")
    private val pointerInputNode =
        delegate(
            SuspendingPointerInputModifierNode {
                detectTapGestures(
                    onPress = {
                        if (!enabled) return@detectTapGestures
                        coroutineScope.launch {
                            scale.animateTo(scaleDown, BounceSpring)
                        }
                        tryAwaitRelease()
                        coroutineScope.launch {
                            scale.animateTo(1f, BounceSpring)
                        }
                    },
                    onTap = {
                        if (enabled) onClick()
                    },
                )
            },
        )

    fun update(
        enabled: Boolean,
        scaleDown: Float,
        onClick: () -> Unit,
    ) {
        val enabledChanged = this.enabled != enabled
        this.enabled = enabled
        this.scaleDown = scaleDown
        this.onClick = onClick
        if (enabledChanged && !enabled && isAttached) {
            coroutineScope.launch { scale.animateTo(1f, BounceSpring) }
        }
    }

    override fun onReset() {
        super.onReset()
        if (isAttached) {
            coroutineScope.launch { scale.snapTo(1f) }
        }
    }

    override fun ContentDrawScope.draw() {
        val currentScale = scale.value
        if (currentScale == 1f) {
            drawContent()
        } else {
            scale(scaleX = currentScale, scaleY = currentScale, pivot = center) {
                this@draw.drawContent()
            }
        }
        invalidateDrawIfNeeded()
    }

    private fun invalidateDrawIfNeeded() {
        if (scale.isRunning) {
            invalidateDraw()
        }
    }

    override fun SemanticsPropertyReceiver.applySemantics() {
        role = Role.Button
        if (!enabled) disabled()
        onClick {
            if (enabled) {
                onClick()
                true
            } else {
                false
            }
        }
    }
}

/**
 * Animated number / text ticker that rolls up or down smoothly when text changes.
 */
@Composable
fun AnimatedTickerText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.headlineMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
    fontWeight: FontWeight = FontWeight.ExtraBold,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Ellipsis,
) {
    AnimatedContent(
        targetState = text,
        modifier = modifier,
        transitionSpec = {
            (
                slideInVertically(
                    animationSpec =
                        spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium,
                        ),
                    initialOffsetY = { fullHeight -> fullHeight / 2 },
                ) + fadeIn(animationSpec = tween(120))
            ).togetherWith(
                slideOutVertically(
                    animationSpec = tween(120),
                    targetOffsetY = { fullHeight -> -fullHeight / 2 },
                ) + fadeOut(animationSpec = tween(100)),
            )
        },
        label = "ticker-transition",
    ) { target ->
        Text(
            text = target,
            style = style,
            color = color,
            fontWeight = fontWeight,
            maxLines = maxLines,
            overflow = overflow,
        )
    }
}
