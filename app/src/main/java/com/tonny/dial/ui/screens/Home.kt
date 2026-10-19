package com.tonny.dial.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.rounded.AccessTimeFilled
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.outlined.Settings
import com.tonny.dial.data.WallpaperTarget
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.shapes.Capsule
import com.tonny.dial.data.Contact
import com.tonny.dial.data.RecentCall
import com.tonny.dial.ui.components.AppBackground
import com.tonny.dial.ui.components.GlassButton
import com.tonny.dial.ui.components.GlassTab
import com.tonny.dial.ui.components.LiquidTabBar
import com.tonny.dial.ui.components.iosSpring
import com.tonny.dial.ui.components.liquidGlass
import com.tonny.dial.ui.theme.Ios
import com.tonny.dial.ui.theme.IosType
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val Tabs = listOf(
    GlassTab("Recents", Icons.Outlined.AccessTime, Icons.Rounded.AccessTimeFilled),
    GlassTab("Contacts", Icons.Outlined.AccountCircle, Icons.Rounded.AccountCircle),
    GlassTab("Keypad", Icons.Outlined.Dialpad, Icons.Rounded.Dialpad),
)

private const val RECENTS = 0
private const val KEYPAD = 2
private const val SEARCH = -1
private const val SETTINGS = -2

@Composable
fun Home(
    contacts: List<Contact>,
    recents: List<RecentCall>,
    blocked: Set<String>,
    blockedNumbers: List<String>,
    actions: ListActions,
    pendingNumber: String?,
    onPendingConsumed: () -> Unit,
    onRecentsShown: () -> Unit,
    isDefaultDialer: Boolean,
    onMakeDefault: () -> Unit,
    onPickPhoto: (WallpaperTarget) -> Unit,
    simPrompt: SimPrompt? = null,
    onSimPicked: (SimOption?) -> Unit = {},
) {
    val ios = Ios.colors
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    var tab by rememberSaveable { mutableIntStateOf(RECENTS) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var keypadSeed by remember { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var card by remember { mutableStateOf<CardTarget?>(null) }
    val sheet = remember { Animatable(0f) }
    val backdrop = rememberLayerBackdrop()

    fun openCard(t: CardTarget) {
        card = t
        focus.clearFocus()
        scope.launch { sheet.animateTo(1f, spring(dampingRatio = 0.86f, stiffness = 320f)) }
    }
    fun closeCard() {
        scope.launch {
            sheet.animateTo(0f, spring(dampingRatio = 1f, stiffness = 420f))
            card = null
        }
    }

    LaunchedEffect(pendingNumber) {
        if (pendingNumber != null) {
            tab = KEYPAD; searching = false; settingsOpen = false
            keypadSeed = pendingNumber
            onPendingConsumed()
        }
    }
    LaunchedEffect(tab) { if (tab == RECENTS) onRecentsShown() }
    BackHandler(card != null) { closeCard() }
    BackHandler(card == null && settingsOpen) { settingsOpen = false }
    BackHandler(card == null && searching) { searching = false; query = ""; focus.clearFocus() }

    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomInset = navInset + 104.dp
    // Remembered so screens below don't see a new instance (and recompose) on every Home recomposition.
    val wrapped = remember(actions) {
        ListActions(
            call = actions.call, message = actions.message, addContact = actions.addContact,
            openContact = actions.openContact, showCard = { openCard(it) },
            toggleStar = actions.toggleStar, toggleBlock = actions.toggleBlock,
            openSettings = { searching = false; query = ""; focus.clearFocus(); settingsOpen = true },
        )
    }
    // The sheet position is only read in layout/draw lambdas, so the animation doesn't recompose Home each frame.
    val sheetUp by remember { derivedStateOf { sheet.value > 0f } }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val heightPx = with(LocalDensity.current) { maxHeight.toPx() }

        // ---- the page, which recedes when a sheet is up
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = sheet.value
                    val s = lerp(1f, 0.92f, p)
                    scaleX = s; scaleY = s
                    translationY = p * 14.dp.toPx()
                    shape = RoundedCornerShape(lerp(0f, 28f, p).dp)
                    clip = p > 0f
                }
                .background(ios.background),
        ) {
            Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                AnimatedContent(
                    when { settingsOpen -> SETTINGS; searching -> SEARCH; else -> tab },
                    transitionSpec = {
                        when {
                            targetState == SETTINGS ->
                                slideInHorizontally(iosSpring()) { it } togetherWith slideOutHorizontally(iosSpring()) { -it / 4 }
                            initialState == SETTINGS ->
                                slideInHorizontally(iosSpring()) { -it / 4 } togetherWith slideOutHorizontally(iosSpring()) { it }
                            else -> fadeIn(tween(180)) togetherWith fadeOut(tween(120))
                        }
                    },
                    label = "tab",
                ) { t ->
                    when (t) {
                        SEARCH -> SearchResults(query, contacts, wrapped, bottomInset)
                        SETTINGS -> SettingsScreen(
                            bottomInset, isDefaultDialer, onMakeDefault, onPickPhoto,
                            blockedNumbers = blockedNumbers,
                            contacts = contacts,
                            onUnblock = { actions.toggleBlock(listOf(it)) },
                            onBack = { settingsOpen = false },
                        )
                        RECENTS -> RecentsScreen(recents, contacts, wrapped, bottomInset)
                        1 -> ContactsScreen(contacts, wrapped, bottomInset)
                        else -> KeypadScreen(keypadSeed, { keypadSeed = null }, contacts, actions.call, actions.addContact, bottomInset)
                    }
                }
            }

            if (!settingsOpen) BottomControls(
                backdrop = backdrop,
                tab = tab,
                onTab = { tab = it },
                searching = searching,
                onSearching = { searching = it; if (!it) { query = ""; focus.clearFocus() } },
                query = query,
                onQuery = { query = it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            )
        }

        // ---- dim + sheet
        if (sheetUp) {
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind { drawRect(Color.Black, alpha = 0.3f * sheet.value) }
                    .clickable(remember { MutableInteractionSource() }, null) { closeCard() },
            )
        }
        card?.let { target ->
            val sheetBackdrop = rememberLayerBackdrop()
            Box(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(top = 10.dp)
                    .offset { IntOffset(0, ((1f - sheet.value) * heightPx).roundToInt()) }
                    .clip(RoundedCornerShape(topStart = 38.dp, topEnd = 38.dp))
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { d -> scope.launch { sheet.snapTo((sheet.value - d / heightPx).coerceIn(0f, 1f)) } },
                        onDragStopped = { v ->
                            if (sheet.value < 0.8f || v > 1400f) closeCard()
                            else sheet.animateTo(1f, iosSpring(bouncy = true))
                        },
                    ),
            ) {
                // Same wallpaper as the rest of the app; glass in the card bends it.
                AppBackground(Modifier.layerBackdrop(sheetBackdrop), base = ios.groupedBackground)
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Grabber()
                    ContactCard(target, contacts, recents, blocked, wrapped, sheetBackdrop)
                }
            }
        }

        // ---- which SIM to call from (two or more SIMs)
        simPrompt?.let { p -> SimPicker(backdrop, p, onPick = { onSimPicked(it) }, onDismiss = { onSimPicked(null) }) }
    }
    BackHandler(simPrompt != null) { onSimPicked(null) }
}

/**
 * Tab bar plus a round search button. Tapping search morphs: the tab bar shrinks to a single
 * glass disc showing the current tab while the search button stretches into a field.
 */
@Composable
private fun BottomControls(
    backdrop: Backdrop,
    tab: Int,
    onTab: (Int) -> Unit,
    searching: Boolean,
    onSearching: (Boolean) -> Unit,
    query: String,
    onQuery: (String) -> Unit,
    modifier: Modifier,
) {
    val ios = Ios.colors
    val morph = remember { Animatable(if (searching) 1f else 0f) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(searching) {
        morph.animateTo(if (searching) 1f else 0f, spring(dampingRatio = 0.78f, stiffness = 360f))
        if (searching) runCatching { focusRequester.requestFocus() }
    }
    var widthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    Box(modifier.fillMaxWidth().height(72.dp).onSizeChanged { widthPx = it.width }) {
        if (widthPx == 0) return@Box
        val full = with(density) { widthPx.toDp() }
        val gap = 10.dp
        val disc = 72.dp
        val m = morph.value
        val barW = lerp((full - disc - gap).value, disc.value, m).dp
        val searchW = lerp(disc.value, (full - disc - gap).value, m).dp

        // Left: tab bar, or a single disc while searching.
        if (m < 0.5f) {
            LiquidTabBar(
                backdrop, Tabs, tab, onTab,
                modifier = Modifier.width(barW).graphicsLayer { alpha = 1f - m * 2f },
            )
        } else {
            GlassButton(
                backdrop, onClick = { onSearching(false) }, size = disc,
                modifier = Modifier.graphicsLayer { alpha = (m - 0.5f) * 2f }, contentDescription = "Close search",
            ) {
                Icon(Tabs[tab].selectedIcon, null, tint = ios.label, modifier = Modifier.size(26.dp))
            }
        }

        // Right: search disc that grows into a field.
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .width(searchW)
                .height(disc)
                .liquidGlass(backdrop, Capsule(), ios.glassTint, blur = 4.dp)
                .clickable(remember { MutableInteractionSource() }, null) { if (!searching) onSearching(true) },
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Search, "Search", tint = if (m > 0.5f) ios.secondaryLabel else ios.label, modifier = Modifier.size(24.dp))
                if (m > 0.3f) {
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f).graphicsLayer { alpha = ((m - 0.3f) / 0.7f).coerceIn(0f, 1f) }) {
                        if (query.isEmpty()) Text("Search", style = IosType.body, color = ios.secondaryLabel)
                        BasicTextField(
                            value = query,
                            onValueChange = onQuery,
                            singleLine = true,
                            textStyle = IosType.body.copy(color = ios.label),
                            cursorBrush = SolidColor(ios.blue),
                            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                        )
                    }
                    if (query.isNotEmpty()) {
                        Icon(
                            Icons.Rounded.Cancel, "Clear", tint = ios.tertiaryLabel,
                            modifier = Modifier.size(20.dp).clickable(remember { MutableInteractionSource() }, null) { onQuery("") },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun Onboarding(onMakeDefault: () -> Unit, onSkip: () -> Unit) {
    val ios = Ios.colors
    val backdrop = rememberLayerBackdrop()
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(ios.background).layerBackdrop(backdrop))
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            GlassButton(backdrop, onClick = {}, size = 108.dp, shape = com.kyant.shapes.RoundedRectangle(30.dp)) {
                Icon(Icons.Rounded.Dialpad, null, tint = ios.label, modifier = Modifier.size(48.dp))
            }
            Spacer(Modifier.height(28.dp))
            Text("Welcome to Phone", style = IosType.largeTitle, color = ios.label)
            Spacer(Modifier.height(10.dp))
            Text(
                "Make it your default phone app to place and answer calls, see your recents and reach your contacts.",
                style = IosType.body, color = ios.secondaryLabel,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.weight(1f))
            GlassButton(
                backdrop, onClick = onMakeDefault, size = 56.dp, shape = Capsule(), tint = ios.blue,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Continue", style = IosType.headline, color = Color.White)
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "Not Now", style = IosType.body, color = ios.blue,
                modifier = Modifier.clickable(remember { MutableInteractionSource() }, null, onClick = onSkip).padding(10.dp),
            )
        }
    }
}
