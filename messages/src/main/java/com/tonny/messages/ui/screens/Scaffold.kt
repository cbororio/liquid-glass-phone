package com.tonny.messages.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.tonny.messages.ui.components.iosSpring
import com.tonny.messages.ui.theme.Ios
import com.tonny.messages.ui.theme.IosType
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** iOS large-title list: big title that scrolls into a compact scroll-edge bar. */
@Composable
fun LargeTitleList(
    title: String,
    bottomInset: Dp,
    state: LazyListState = rememberLazyListState(),
    topLeading: (@Composable () -> Unit)? = null,
    topTrailing: (@Composable (Backdrop) -> Unit)? = null,
    menuOverlay: (@Composable androidx.compose.foundation.layout.BoxScope.(Backdrop) -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    val ios = Ios.colors
    val collapsed by remember {
        derivedStateOf { state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 90 }
    }
    val titleAlpha by animateFloatAsState(if (collapsed) 1f else 0f, iosSpring(), label = "title")
    // Two layers: the wallpaper and the scrolling list. Top-bar glass refracts both; content glass
    // (e.g. settings cards) refracts only the wallpaper. Neither layer contains its own consumer.
    val backgroundBackdrop = rememberLayerBackdrop()
    val listBackdrop = rememberLayerBackdrop()
    val backdrop = com.kyant.backdrop.backdrops.rememberCombinedBackdrop(backgroundBackdrop, listBackdrop)

    Box(Modifier.fillMaxSize()) {
        com.tonny.messages.ui.components.AppBackground(Modifier.layerBackdrop(backgroundBackdrop))
        androidx.compose.runtime.CompositionLocalProvider(
            com.tonny.messages.ui.components.LocalBackgroundBackdrop provides backgroundBackdrop,
        ) {
        androidx.compose.foundation.lazy.LazyColumn(
            state = state,
            contentPadding = PaddingValues(bottom = bottomInset + 20.dp),
            modifier = Modifier.fillMaxSize().layerBackdrop(listBackdrop),
        ) {
            item(key = "__title") {
                Column(Modifier.statusBarsPadding().padding(top = 52.dp)) {
                    Text(
                        title,
                        style = IosType.largeTitle,
                        color = ios.label,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)
                            .graphicsLayer { alpha = 1f - titleAlpha },
                    )
                }
            }
            content()
        }
        }

        Column(Modifier.fillMaxWidth()) {
            Box(
                Modifier.fillMaxWidth().background(
                    Brush.verticalGradient(
                        0f to ios.background.copy(alpha = 0.96f * titleAlpha),
                        0.75f to ios.background.copy(alpha = 0.85f * titleAlpha),
                        1f to ios.background.copy(alpha = 0f),
                    ),
                ),
            ) {
                Column {
                    Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                    Box(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                        Text(title, style = IosType.headline, color = ios.label, modifier = Modifier.graphicsLayer { alpha = titleAlpha })
                        if (topLeading != null) Box(Modifier.align(Alignment.CenterStart)) { topLeading() }
                        if (topTrailing != null) Box(Modifier.align(Alignment.CenterEnd)) { topTrailing(backdrop) }
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
        // Glass menus render here, above the list (in the backdrop tree so they refract it).
        menuOverlay?.invoke(this, backdrop)
    }
}

/** iOS swipe-left-to-delete that commits on a long swipe. */
@Composable
fun SwipeToDelete(onDelete: () -> Unit, content: @Composable () -> Unit) {
    val ios = Ios.colors
    val view = androidx.compose.ui.platform.LocalView.current
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    var width by remember { mutableIntStateOf(1) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val action = with(density) { 88.dp.toPx() }
    var armed by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxWidth().onSizeChanged { width = it.width }) {
        val reveal = (-offset.value).coerceAtLeast(0f)
        if (reveal > 0f) {
            Box(
                Modifier.align(Alignment.CenterEnd).matchParentSize().wrapContentWidth(Alignment.End)
                    .width(with(density) { reveal.toDp() })
                    .background(ios.red)
                    .clickable { scope.launch { offset.animateTo(-width.toFloat(), iosSpring()); onDelete() } },
                contentAlignment = Alignment.Center,
            ) {
                Text("Delete", color = Color.White, style = IosType.body, textAlign = TextAlign.Center, maxLines = 1, softWrap = false)
            }
        }
        Box(
            Modifier.offset { IntOffset(offset.value.roundToInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { d ->
                        scope.launch {
                            val next = (offset.value + d).coerceAtMost(0f)
                            offset.snapTo(next)
                            val nowArmed = -next > width * 0.6f
                            if (nowArmed != armed) {
                                armed = nowArmed
                                view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_FREQUENT_TICK)
                            }
                        }
                    },
                    onDragStopped = { v ->
                        when {
                            armed -> { offset.animateTo(-width.toFloat(), iosSpring()); onDelete() }
                            offset.value < -action / 2 || v < -1200f -> offset.animateTo(-action, iosSpring(bouncy = true))
                            else -> offset.animateTo(0f, iosSpring(bouncy = true))
                        }
                        armed = false
                    },
                ),
        ) { content() }
    }
}

@Composable
fun EmptyState(title: String, subtitle: String? = null) {
    val ios = Ios.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 120.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = IosType.title2, color = ios.label, textAlign = TextAlign.Center)
        if (subtitle != null) {
            Spacer(Modifier.height(6.dp))
            Text(subtitle, style = IosType.subhead, color = ios.secondaryLabel, textAlign = TextAlign.Center)
        }
    }
}
