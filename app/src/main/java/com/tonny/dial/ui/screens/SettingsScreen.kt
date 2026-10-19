package com.tonny.dial.ui.screens

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material3.Icon
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import com.tonny.dial.ui.components.GlassButton
import com.tonny.dial.data.BlockedRepository
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.BackHandler
import com.tonny.dial.data.Contact
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import com.tonny.dial.data.AnswerStyle
import com.tonny.dial.data.Prefs
import com.tonny.dial.data.Settings
import com.tonny.dial.data.ThemeMode
import com.tonny.dial.data.Wallpaper
import com.tonny.dial.data.WallpaperTarget
import com.tonny.dial.ui.components.Hairline
import com.tonny.dial.ui.components.LocalBackgroundBackdrop
import com.tonny.dial.ui.components.PresetWallpapers
import com.tonny.dial.ui.components.SegmentedControl
import com.tonny.dial.ui.components.WallpaperView
import com.tonny.dial.ui.components.iosSpring
import com.tonny.dial.ui.components.liquidGlass
import com.tonny.dial.ui.theme.Ios
import com.tonny.dial.ui.theme.IosType
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    bottomInset: Dp,
    isDefaultDialer: Boolean,
    onMakeDefault: () -> Unit,
    onPickPhoto: (WallpaperTarget) -> Unit,
    blockedNumbers: List<String>,
    contacts: List<Contact>,
    onUnblock: (String) -> Unit,
    onBack: () -> Unit,
) {
    val ios = Ios.colors
    val s by Prefs.settings.collectAsStateWithLifecycle()
    var showBlocked by rememberSaveable { mutableStateOf(false) }
    if (showBlocked) {
        BackHandler { showBlocked = false }
        BlockedNumbersScreen(bottomInset, blockedNumbers, contacts, isDefaultDialer, onUnblock, onBack = { showBlocked = false })
        return
    }

    LargeTitleList(
        title = "Settings",
        bottomInset = bottomInset,
        topLeading = { bd ->
            GlassButton(bd, onClick = onBack, size = 44.dp, contentDescription = "Back") {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack, null,
                    tint = ios.label, modifier = Modifier.size(22.dp),
                )
            }
        },
    ) {
        // ---------------------------------------------------------- call screen
        item(key = "callHeader") { SectionHeader("Call Screen") }
        item(key = "callPreview") {
            Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                CallPreview(s)
            }
        }
        item(key = "callGroup") {
            Group {
                WallpaperPicker(
                    current = s.callWallpaper,
                    onPick = { w -> Prefs.update { it.copy(callWallpaper = w) } },
                    onPhoto = { onPickPhoto(WallpaperTarget.Call) },
                    noneLabel = "Black",
                    noneColor = Color.Black,
                )
                Hairline(Modifier.padding(start = 16.dp))
                if (s.callWallpaper is Wallpaper.Photo) {
                    SliderRow("Blur", s.callBlur) { v -> Prefs.update { it.copy(callBlur = v) } }
                    Hairline(Modifier.padding(start = 16.dp))
                }
                if (s.callWallpaper != Wallpaper.None) {
                    SliderRow("Dim", s.callDim) { v -> Prefs.update { it.copy(callDim = v) } }
                    Hairline(Modifier.padding(start = 16.dp))
                }
                LabeledRow("Answer with") {
                    SegmentedControl(
                        LocalBackgroundBackdrop.current ?: rememberLayerBackdrop(),
                        listOf("Slider", "Buttons"),
                        s.answerStyle.ordinal,
                        { i -> Prefs.update { it.copy(answerStyle = AnswerStyle.entries[i]) } },
                        Modifier.width(180.dp),
                    )
                }
            }
        }
        item(key = "callFooter") {
            Footer("The slider works like iPhone's “slide to answer”. Press a volume key to silence the ringer, or decline from the notification shade.")
        }

        // ---------------------------------------------------------- app background
        item(key = "appHeader") { SectionHeader("App Background") }
        item(key = "appGroup") {
            Group {
                WallpaperPicker(
                    current = s.appWallpaper,
                    onPick = { w -> Prefs.update { it.copy(appWallpaper = w) } },
                    onPhoto = { onPickPhoto(WallpaperTarget.App) },
                    noneLabel = "Plain",
                )
                if (s.appWallpaper != Wallpaper.None) {
                    Hairline(Modifier.padding(start = 16.dp))
                    SliderRow("Readability", s.appDim) { v -> Prefs.update { it.copy(appDim = v) } }
                }
            }
        }
        item(key = "appFooter") { Footer("Shown behind Favorites, Recents, Contacts and the keypad. Raise readability if text gets hard to see.") }

        // ---------------------------------------------------------- appearance
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

        // ---------------------------------------------------------- phone
        item(key = "phoneHeader") { SectionHeader("Phone") }
        item(key = "phoneGroup") {
            Group {
                LabeledRow("Default phone app") {
                    if (isDefaultDialer) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Check, null, tint = ios.green, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("On", style = IosType.body, color = ios.secondaryLabel)
                        }
                    } else {
                        Text(
                            "Set", style = IosType.body, color = ios.blue,
                            modifier = Modifier.clickable(remember { MutableInteractionSource() }, null, onClick = onMakeDefault),
                        )
                    }
                }
            }
        }
        // ---------------------------------------------------------- blocked
        item(key = "blockedHeader") { SectionHeader("Privacy") }
        item(key = "blockedGroup") {
            Group {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clickable { showBlocked = true }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Blocked Numbers", style = IosType.body, color = ios.label, modifier = Modifier.weight(1f))
                    Text(blockedNumbers.size.toString(), style = IosType.body, color = ios.secondaryLabel)
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = ios.tertiaryLabel, modifier = Modifier.size(22.dp))
                }
            }
        }

        item(key = "version") { Footer("Dial 1.0") }
    }
}

@Composable
private fun BlockedNumbersScreen(
    bottomInset: Dp,
    blockedNumbers: List<String>,
    contacts: List<Contact>,
    isDefaultDialer: Boolean,
    onUnblock: (String) -> Unit,
    onBack: () -> Unit,
) {
    val ios = Ios.colors
    val names = remember(contacts) {
        contacts.flatMap { c -> c.numbers.map { BlockedRepository.key(it.number) to c.name } }
            .filter { it.first.isNotEmpty() }.toMap()
    }
    LargeTitleList(
        title = "Blocked",
        bottomInset = bottomInset,
        topLeading = { bd ->
            GlassButton(bd, onClick = onBack, size = 44.dp, contentDescription = "Back") {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = ios.label, modifier = Modifier.size(22.dp))
            }
        },
    ) {
        if (blockedNumbers.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    "No Blocked Numbers",
                    if (isDefaultDialer) "Block someone from their contact page." else "Set Phone as your default phone app to manage blocked numbers.",
                )
            }
        }
        items(blockedNumbers, key = { it }) { number ->
            val name = BlockedRepository.key(number).takeIf { it.isNotEmpty() }?.let { names[it] }
            Column {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(name ?: number, style = IosType.body, color = ios.label)
                        if (name != null) Text(number, style = IosType.subhead, color = ios.secondaryLabel)
                    }
                    Text(
                        "Unblock", style = IosType.body, color = ios.blue,
                        modifier = Modifier
                            .clickable(remember { MutableInteractionSource() }, null) { onUnblock(number) }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                    )
                }
                Hairline(Modifier.padding(start = 18.dp))
            }
        }
        if (blockedNumbers.isNotEmpty()) {
            item(key = "footer") { Footer("Calls from these numbers are rejected without ringing.") }
        }
    }
}

// ============================================================== building blocks

@Composable
private fun SectionHeader(text: String) {
    Text(
        text.uppercase(),
        style = IosType.footnote,
        color = Ios.colors.secondaryLabel,
        modifier = Modifier.padding(start = 34.dp, top = 22.dp, bottom = 6.dp),
    )
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
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .then(
                // Translucent rather than blurred glass: live blur behind every card cost too much GPU on low-end phones.
                if (backdrop != null) Modifier.background(
                    (if (ios.dark) ios.card else Color(0xFFF2F2F7)).copy(alpha = 0.78f), RoundedRectangle(22.dp),
                ) else Modifier.background(ios.card, RoundedRectangle(22.dp)),
            ),
    ) { content() }
}

@Composable
private fun LabeledRow(label: String, trailing: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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

/** iOS slider: thin track, blue fill, white knob that swells while dragged. */
@Composable
fun IosSlider(value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val ios = Ios.colors
    var dragging by remember { mutableStateOf(false) }
    val knobScale by animateFloatAsState(if (dragging) 1.25f else 1f, iosSpring(bouncy = true), label = "knob")
    BoxWithConstraints(modifier.height(36.dp), contentAlignment = Alignment.CenterStart) {
        val knob = 26.dp
        val widthPx = constraints.maxWidth.toFloat()
        val knobPx = with(androidx.compose.ui.platform.LocalDensity.current) { knob.toPx() }
        fun valueAt(x: Float) = ((x - knobPx / 2) / (widthPx - knobPx)).coerceIn(0f, 1f)

        Box(Modifier.fillMaxWidth().height(4.dp).background(ios.fill, Capsule()))
        Box(Modifier.fillMaxWidth(value.coerceIn(0.02f, 1f)).height(4.dp).background(ios.blue, Capsule()))
        Box(
            Modifier
                .offset { IntOffset(((widthPx - knobPx) * value).roundToInt(), 0) }
                .size(knob)
                .graphicsLayer {
                    scaleX = knobScale; scaleY = knobScale
                    shadowElevation = 4.dp.toPx(); shape = CircleShape; clip = true
                }
                .background(Color.White, CircleShape),
        )
        Box(
            Modifier
                .matchParentSize()
                .pointerInput(widthPx) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragging = true; onChange(valueAt(it.x)) },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                    ) { change, _ -> change.consume(); onChange(valueAt(change.position.x)) }
                }
                .pointerInput(widthPx) { detectTapGestures { onChange(valueAt(it.x)) } },
        )
    }
}

/** A row of phone-shaped thumbnails: none, every preset, and the user's own photo. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WallpaperPicker(
    current: Wallpaper,
    onPick: (Wallpaper) -> Unit,
    onPhoto: () -> Unit,
    noneLabel: String,
    noneColor: Color = Ios.colors.background,
) {
    val ios = Ios.colors
    val view = LocalView.current
    val options: List<Wallpaper?> = listOf(Wallpaper.None) + PresetWallpapers.indices.map { Wallpaper.Preset(it) } + listOf(null)
    // Start scrolled so the current choice is in view.
    val selectedIndex = when (current) {
        Wallpaper.None -> 0
        is Wallpaper.Preset -> current.index + 1
        is Wallpaper.Photo -> options.lastIndex
    }
    val rowState = rememberLazyListState(initialFirstVisibleItemIndex = (selectedIndex - 1).coerceAtLeast(0))
    LazyRow(
        state = rowState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(options) { _, option ->
            val selected = when (option) {
                null -> current is Wallpaper.Photo
                else -> option == current
            }
            val ring by animateFloatAsState(if (selected) 1f else 0f, iosSpring(), label = "ring")
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(width = 62.dp, height = 104.dp)
                        .border(2.5.dp, ios.blue.copy(alpha = ring), RoundedRectangle(16.dp))
                        .padding(4.dp)
                        .clip(RoundedRectangle(12.dp))
                        .background(if (option == Wallpaper.None) noneColor else Color.Black)
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
                            Box(
                                Modifier.size(34.dp).background(Color.Black.copy(alpha = 0.35f), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Rounded.AddPhotoAlternate, "Choose photo", tint = Color.White, modifier = Modifier.size(20.dp)) }
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
                    style = IosType.caption,
                    color = if (selected) ios.blue else ios.secondaryLabel,
                )
            }
        }
    }
}

/** A miniature of the real call screen so changes are visible immediately. */
@Composable
private fun CallPreview(s: Settings) {
    val backdrop = rememberLayerBackdrop()
    Box(
        Modifier
            .size(width = 170.dp, height = 340.dp)
            .clip(RoundedRectangle(34.dp))
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedRectangle(34.dp)),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black).layerBackdrop(backdrop)) {
            WallpaperView(s.callWallpaper, blur = (s.callBlur * 12).dp)
            if (s.callWallpaper != Wallpaper.None) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = s.callDim)))
        }
        Column(Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(22.dp))
            Text("mobile", color = Color.White.copy(0.7f), fontSize = 9.sp)
            Text("Winnie", color = Color.White, fontSize = 20.sp)
            Spacer(Modifier.weight(1f))
            if (s.answerStyle == AnswerStyle.Slider) {
                Box(
                    Modifier.fillMaxWidth().height(40.dp).liquidGlass(backdrop, Capsule(), Color.White.copy(alpha = 0.14f), blur = 4.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Box(Modifier.padding(4.dp).size(32.dp).background(Ios.colors.green, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Call, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                    Text("slide to answer", color = Color.White.copy(0.7f), fontSize = 10.sp, modifier = Modifier.align(Alignment.Center).padding(start = 28.dp))
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    MiniButton(backdrop, Icons.Rounded.CallEnd, Ios.colors.red)
                    MiniButton(backdrop, Icons.Rounded.Call, Ios.colors.green)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                MiniButton(backdrop, Icons.AutoMirrored.Rounded.VolumeUp, Color.White.copy(alpha = 0.14f), small = true)
                MiniButton(backdrop, Icons.Rounded.Pause, Color.White.copy(alpha = 0.14f), small = true)
                MiniButton(backdrop, Icons.Rounded.MicOff, Color.White.copy(alpha = 0.14f), small = true)
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun MiniButton(backdrop: Backdrop, icon: ImageVector, tint: Color, small: Boolean = false) {
    val size = if (small) 32.dp else 40.dp
    Box(
        Modifier.size(size).liquidGlass(backdrop, CircleShape, tint, refractionHeight = 6.dp, refractionAmount = 10.dp, shadow = false),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * 0.5f)) }
}
