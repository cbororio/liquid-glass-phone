package com.tonny.messages.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import com.tonny.messages.data.Prefs
import com.tonny.messages.data.ThemeMode
import com.tonny.messages.data.Wallpaper
import com.tonny.messages.data.WallpaperTarget
import com.tonny.messages.ui.components.Hairline
import com.tonny.messages.ui.components.LocalBackgroundBackdrop
import com.tonny.messages.ui.components.PresetWallpapers
import com.tonny.messages.ui.components.SegmentedControl
import com.tonny.messages.ui.components.WallpaperView
import com.tonny.messages.ui.components.iosSpring
import com.tonny.messages.ui.components.liquidGlass
import com.tonny.messages.ui.theme.Ios
import com.tonny.messages.ui.theme.IosType
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(bottomInset: Dp, onPickPhoto: (WallpaperTarget) -> Unit, onBack: (() -> Unit)? = null) {
    val ios = Ios.colors
    val s by Prefs.settings.collectAsStateWithLifecycle()

    LargeTitleList(
        title = "Settings",
        bottomInset = bottomInset,
        topLeading = if (onBack != null) ({
            Text("Back", style = IosType.body, color = ios.blue, modifier = Modifier.clickable(androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null, onClick = onBack).padding(8.dp))
        }) else null,
    ) {
        item(key = "lookHeader") { SectionHeader("Appearance") }
        item(key = "lookGroup") {
            Group {
                LabeledRow("Theme") {
                    SegmentedControl(
                        LocalBackgroundBackdrop.current ?: rememberLayerBackdrop(),
                        listOf("Auto", "Light", "Dark"),
                        s.theme.ordinal,
                        { i -> Prefs.update { it.copy(theme = ThemeMode.entries[i]) } },
                        Modifier.width(200.dp),
                    )
                }
            }
        }

        item(key = "appHeader") { SectionHeader("App Background") }
        item(key = "appGroup") {
            Group {
                WallpaperPicker(s.appWallpaper, { w -> Prefs.update { it.copy(appWallpaper = w) } }, { onPickPhoto(WallpaperTarget.App) }, "Plain")
                if (s.appWallpaper != Wallpaper.None) {
                    Hairline(Modifier.padding(start = 16.dp))
                    SliderRow("Readability", s.appDim) { v -> Prefs.update { it.copy(appDim = v) } }
                }
            }
        }
        item(key = "appFooter") { Footer("Shown behind your conversations. Raise readability if text gets hard to see.") }

        item(key = "chatHeader") { SectionHeader("Chat Background") }
        item(key = "chatGroup") {
            Group {
                WallpaperPicker(s.chatWallpaper, { w -> Prefs.update { it.copy(chatWallpaper = w) } }, { onPickPhoto(WallpaperTarget.Chat) }, "Default")
                if (s.chatWallpaper != Wallpaper.None) {
                    Hairline(Modifier.padding(start = 16.dp))
                    SliderRow("Dim", s.chatDim) { v -> Prefs.update { it.copy(chatDim = v) } }
                }
            }
        }
        item(key = "chatFooter") { Footer("Shown behind message bubbles inside a conversation.") }

        item(key = "spamHeader") { SectionHeader("Spam") }
        item(key = "spamGroup") {
            Group {
                LabeledRow("Filter betting & promos") {
                    IosSwitch(s.autoSpamEnabled) { on -> Prefs.update { it.copy(autoSpamEnabled = on) } }
                }
            }
        }
        item(key = "spamFooter") { Footer("Betting and promo texts from senders you haven't saved go to Spam, without a notification. Move a sender out of Spam and they stay in your inbox.") }
        item(key = "version") { Footer("Messages 1.0") }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text.uppercase(), style = IosType.footnote, color = Ios.colors.secondaryLabel, modifier = Modifier.padding(start = 34.dp, top = 22.dp, bottom = 6.dp))
}

@Composable
private fun Footer(text: String) {
    Text(text, style = IosType.footnote, color = Ios.colors.secondaryLabel, modifier = Modifier.padding(horizontal = 34.dp, vertical = 6.dp))
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    val ios = Ios.colors
    val backdrop = LocalBackgroundBackdrop.current
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).then(
            // Translucent rather than blurred glass: live blur behind every card cost too much GPU on low-end phones.
            if (backdrop != null) Modifier.background((if (ios.dark) ios.card else Color(0xFFF2F2F7)).copy(alpha = 0.78f), RoundedRectangle(22.dp))
            else Modifier.background(ios.card, RoundedRectangle(22.dp)),
        ),
    ) { content() }
}

/** iOS-style on/off switch. */
@Composable
private fun IosSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val ios = Ios.colors
    val x by androidx.compose.animation.core.animateFloatAsState(if (checked) 1f else 0f, label = "switch")
    Box(
        Modifier
            .size(width = 51.dp, height = 31.dp)
            .background(androidx.compose.ui.graphics.lerp(ios.fill, ios.green, x), com.kyant.shapes.Capsule())
            .clickable(androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null) { onChange(!checked) }
            .padding(2.dp),
    ) {
        Box(
            Modifier
                .offset { androidx.compose.ui.unit.IntOffset((20.dp.toPx() * x).toInt(), 0) }
                .size(27.dp)
                .background(Color.White, androidx.compose.foundation.shape.CircleShape),
        )
    }
}

@Composable
private fun LabeledRow(label: String, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = IosType.body, color = Ios.colors.label, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun SliderRow(label: String, value: Float, onChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = IosType.body, color = Ios.colors.label, modifier = Modifier.width(110.dp))
        IosSlider(value, onChange, Modifier.weight(1f))
    }
}

@Composable
private fun IosSlider(value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val ios = Ios.colors
    var dragging by remember { mutableStateOf(false) }
    val knobScale by animateFloatAsState(if (dragging) 1.25f else 1f, iosSpring(bouncy = true), label = "knob")
    BoxWithConstraints(modifier.height(36.dp), contentAlignment = Alignment.CenterStart) {
        val knob = 26.dp
        val widthPx = constraints.maxWidth.toFloat()
        val knobPx = with(LocalDensity.current) { knob.toPx() }
        fun valueAt(x: Float) = ((x - knobPx / 2) / (widthPx - knobPx)).coerceIn(0f, 1f)
        Box(Modifier.fillMaxWidth().height(4.dp).background(ios.fill, Capsule()))
        Box(Modifier.fillMaxWidth(value.coerceIn(0.02f, 1f)).height(4.dp).background(ios.blue, Capsule()))
        Box(
            Modifier.offset { IntOffset(((widthPx - knobPx) * value).roundToInt(), 0) }.size(knob)
                .graphicsLayer { scaleX = knobScale; scaleY = knobScale; shadowElevation = 4.dp.toPx(); shape = CircleShape; clip = true }
                .background(Color.White, CircleShape),
        )
        Box(
            Modifier.matchParentSize()
                .pointerInput(widthPx) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragging = true; onChange(valueAt(it.x)) },
                        onDragEnd = { dragging = false }, onDragCancel = { dragging = false },
                    ) { change, _ -> change.consume(); onChange(valueAt(change.position.x)) }
                }
                .pointerInput(widthPx) { detectTapGestures { onChange(valueAt(it.x)) } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WallpaperPicker(current: Wallpaper, onPick: (Wallpaper) -> Unit, onPhoto: () -> Unit, noneLabel: String) {
    val ios = Ios.colors
    val view = LocalView.current
    val options: List<Wallpaper?> = listOf(Wallpaper.None) + PresetWallpapers.indices.map { Wallpaper.Preset(it) } + listOf(null)
    val selectedIndex = when (current) {
        Wallpaper.None -> 0
        is Wallpaper.Preset -> current.index + 1
        is Wallpaper.Photo -> options.lastIndex
    }
    val rowState = rememberLazyListState(initialFirstVisibleItemIndex = (selectedIndex - 1).coerceAtLeast(0))
    LazyRow(state = rowState, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(options) { option ->
            val selected = if (option == null) current is Wallpaper.Photo else option == current
            val ring by animateFloatAsState(if (selected) 1f else 0f, iosSpring(), label = "ring")
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(width = 62.dp, height = 104.dp).border(2.5.dp, ios.blue.copy(alpha = ring), RoundedRectangle(16.dp)).padding(4.dp)
                        .clip(RoundedRectangle(12.dp))
                        .background(if (option == Wallpaper.None) ios.background else Color.Black)
                        .border(0.5.dp, ios.separator, RoundedRectangle(12.dp))
                        .combinedClickable(remember { MutableInteractionSource() }, null) {
                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            if (option == null) onPhoto() else onPick(option)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    when (option) {
                        null -> {
                            if (current is Wallpaper.Photo) WallpaperView(current)
                            Box(Modifier.size(34.dp).background(Color.Black.copy(alpha = 0.35f), CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.AddPhotoAlternate, "Choose photo", tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                        Wallpaper.None -> Unit
                        else -> WallpaperView(option)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    when (option) {
                        null -> "Photo"
                        Wallpaper.None -> noneLabel
                        is Wallpaper.Preset -> PresetWallpapers[option.index].name
                        else -> ""
                    },
                    style = IosType.caption, color = if (selected) ios.blue else ios.secondaryLabel,
                )
            }
        }
    }
}
