package com.tonny.messages.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.tonny.messages.ui.theme.Ios
import com.tonny.messages.ui.theme.IosType
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.sin

/** The backdrop behind the current scrolling content, for glass that lives inside a list. */
val LocalBackgroundBackdrop = androidx.compose.runtime.compositionLocalOf<Backdrop?> { null }

/** Spring used everywhere for that soft, slightly bouncy iOS motion. */
fun <T> iosSpring(bouncy: Boolean = false) =
    spring<T>(dampingRatio = if (bouncy) 0.8f else 1f, stiffness = 340f)

/**
 * Liquid Glass: refracts, blurs and saturates whatever [backdrop] captured behind this element,
 * then lays a faint tint and a specular rim on top.
 */
fun Modifier.liquidGlass(
    backdrop: Backdrop,
    shape: Shape,
    tint: Color,
    blur: Dp = 2.dp,
    refractionHeight: Dp = 12.dp,
    refractionAmount: Dp = 24.dp,
    scale: () -> Float = { 1f },
    shadow: Boolean = true,
): Modifier = drawBackdrop(
    backdrop = backdrop,
    shape = { shape },
    effects = {
        vibrancy()
        blur(blur.toPx())
        lens(refractionHeight.toPx(), refractionAmount.toPx())
    },
    highlight = { Highlight.Default },
    shadow = if (shadow) ({ Shadow(radius = 18.dp, color = Color.Black.copy(alpha = 0.12f)) }) else null,
    layerBlock = {
        val s = scale()
        scaleX = s
        scaleY = s
    },
    onDrawSurface = { drawRect(tint) },
)

/** A round (or any-shaped) glass button that swells under the finger like iOS 26 controls. */
@Composable
fun GlassButton(
    backdrop: Backdrop,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    shape: Shape = CircleShape,
    tint: Color = Ios.colors.glassTint,
    contentDescription: String? = null,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 1.12f else 1f, iosSpring(bouncy = true), label = "glassScale")
    val glow by animateFloatAsState(if (pressed) 1f else 0f, tween(140), label = "glow")
    Box(
        modifier
            .size(size)
            .liquidGlass(backdrop, shape, tint, scale = { scale })
            .then(if (glow > 0f) Modifier.background(Color.White.copy(alpha = 0.18f * glow), shape) else Modifier)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button) {
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                onClick()
            }
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

data class GlassTab(val title: String, val icon: ImageVector, val selectedIcon: ImageVector = icon)

/**
 * iOS 26 style floating tab bar. The selection is a liquid droplet that springs between tabs,
 * swells while it travels, and can be dragged with a finger.
 */
@Composable
fun LiquidTabBar(
    backdrop: Backdrop,
    tabs: List<GlassTab>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ios = Ios.colors
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier
            .height(72.dp)
            .liquidGlass(backdrop, Capsule(), ios.glassTint, blur = 4.dp),
    ) {
        val pad = 4.dp
        val itemW = (maxWidth - pad * 2) / tabs.size
        val itemPx = with(density) { itemW.toPx() }
        val x = remember { Animatable(selected * itemPx) }
        val lift = remember { Animatable(0f) }
        var dragging by remember { mutableStateOf(false) }

        LaunchedEffect(selected, itemPx) {
            if (dragging) return@LaunchedEffect
            launch { lift.animateTo(1f, tween(120)) }
            x.animateTo(selected * itemPx, iosSpring(bouncy = true))
            lift.animateTo(0f, iosSpring())
        }

        // Droplet
        val l = lift.value
        Box(
            Modifier
                .offset { IntOffset((x.value + with(density) { pad.toPx() }).roundToInt(), 0) }
                .width(itemW)
                .fillMaxHeight()
                .padding(vertical = pad)
                .graphicsLayer {
                    val s = lerp(1f, 1.28f, l)
                    scaleX = s; scaleY = lerp(1f, 1.18f, l)
                }
                .then(
                    if (l > 0.01f) Modifier.liquidGlass(
                        backdrop, Capsule(), Color.White.copy(alpha = 0.08f * l),
                        blur = 0.dp, refractionHeight = 14.dp, refractionAmount = 32.dp, shadow = false,
                    ) else Modifier
                )
                .background(ios.fill.copy(alpha = ios.fill.alpha * (1f - l)), Capsule()),
        )

        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = pad)
                .pointerInput(itemPx, tabs.size) {
                    detectHorizontalDragGestures(
                        onDragStart = { start ->
                            dragging = true
                            scope.launch { lift.animateTo(1f, iosSpring()) }
                            scope.launch { x.animateTo((start.x - itemPx / 2).coerceIn(0f, itemPx * (tabs.size - 1)), iosSpring()) }
                        },
                        onDragEnd = {
                            val target = (x.value / itemPx).roundToInt().coerceIn(0, tabs.lastIndex)
                            dragging = false
                            scope.launch {
                                x.animateTo(target * itemPx, iosSpring(bouncy = true))
                                lift.animateTo(0f, iosSpring())
                            }
                            if (target != selected) {
                                view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
                                onSelect(target)
                            }
                        },
                        onDragCancel = { dragging = false; scope.launch { lift.animateTo(0f) } },
                    ) { change, dx ->
                        change.consume()
                        scope.launch { x.snapTo((x.value + dx).coerceIn(0f, itemPx * (tabs.size - 1))) }
                    }
                },
        ) {
            tabs.forEachIndexed { i, tab ->
                // Color follows the droplet, not just the selected index, so it feels continuous.
                val nearness = (1f - kotlin.math.abs(x.value / itemPx - i)).coerceIn(0f, 1f)
                val color = androidx.compose.ui.graphics.lerp(ios.label, ios.blue, nearness)
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .semantics { this.selected = i == selected; role = Role.Tab }
                        .pointerInput(i) {
                            detectTapGestures {
                                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                onSelect(i)
                            }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    Icon(if (i == selected) tab.selectedIcon else tab.icon, null, tint = color, modifier = Modifier.size(27.dp))
                    Spacer(Modifier.height(3.dp))
                    Text(tab.title, color = color, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
    }
}

/** iOS 26 segmented control: a glass capsule whose glass thumb springs between segments. */
@Composable
fun SegmentedControl(
    backdrop: Backdrop,
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ios = Ios.colors
    val view = LocalView.current
    BoxWithConstraints(modifier.height(40.dp).liquidGlass(backdrop, Capsule(), ios.glassTint, blur = 4.dp).padding(3.dp)) {
        val segW = maxWidth / options.size
        val x by animateFloatAsState(selected.toFloat(), iosSpring(bouncy = true), label = "seg")
        Box(
            Modifier
                .offset { IntOffset((segW.toPx() * x).roundToInt(), 0) }
                .width(segW)
                .fillMaxHeight()
                .background(ios.fill, Capsule()),
        )
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, o ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(remember { MutableInteractionSource() }, null) {
                            view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
                            onSelect(i)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(o, style = IosType.subhead.copy(fontWeight = FontWeight.SemiBold), color = if (i == selected) ios.label else ios.secondaryLabel)
                }
            }
        }
    }
}

/** Thin iOS hairline separator. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(0.5.dp).background(Ios.colors.separator))
}

/**
 * A liquid-glass dropdown menu rendered in-tree (so it actually refracts the content behind it,
 * which a floating Popup cannot). Anchor is the trigger's bounds in window coordinates.
 */
@Composable
fun BoxScope.GlassMenu(
    backdrop: Backdrop,
    anchor: androidx.compose.ui.geometry.Rect?,
    onDismiss: () -> Unit,
    width: Dp = 236.dp,
    /** Line the menu up with the anchor's right edge (true) or left edge (false). */
    alignEnd: Boolean = true,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    if (anchor == null) return
    val density = androidx.compose.ui.platform.LocalDensity.current
    val appear = remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) { appear.animateTo(1f, iosSpring(bouncy = true)) }
    val window = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize
    var menuHeight by remember { androidx.compose.runtime.mutableIntStateOf(0) }

    // Transparent scrim catches the outside tap.
    Box(
        Modifier.matchParentSize().clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss),
    )
    val widthPx = with(density) { width.toPx() }
    val margin = with(density) { 8.dp.toPx() }
    val gap = with(density) { 6.dp.toPx() }
    val x = (if (alignEnd) anchor.right - widthPx else anchor.left).coerceIn(margin, (window.width - widthPx - margin).coerceAtLeast(margin))
    // Open upward when there isn't room below (e.g. the + button or a message near the keyboard).
    val below = anchor.bottom + gap
    val above = anchor.top - gap - menuHeight
    val openUp = menuHeight > 0 && below + menuHeight > window.height - margin * 6 && above > margin
    val y = if (openUp) above else below
    androidx.compose.foundation.layout.Column(
        Modifier
            .offset { androidx.compose.ui.unit.IntOffset(x.roundToInt(), y.roundToInt()) }
            .width(width)
            .onSizeChanged { menuHeight = it.height }
            .graphicsLayer {
                alpha = if (menuHeight == 0) 0f else appear.value
                scaleX = 0.9f + 0.1f * appear.value
                scaleY = scaleX
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(if (alignEnd) 1f else 0f, if (openUp) 1f else 0f)
            }
            .liquidGlass(
                backdrop,
                com.kyant.shapes.RoundedRectangle(20.dp),
                (if (Ios.colors.dark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.55f)),
                blur = 8.dp, refractionHeight = 14.dp, refractionAmount = 28.dp,
            )
            .padding(vertical = 6.dp),
        content = content,
    )
}

@Composable
fun GlassMenuItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, destructive: Boolean = false, onClick: () -> Unit) {
    val ios = Ios.colors
    val color = if (destructive) ios.red else ios.label
    Row(
        Modifier.fillMaxWidth().clickable(remember { MutableInteractionSource() }, null, onClick = onClick).padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = IosType.body, color = color, modifier = Modifier.weight(1f))
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
    }
}
