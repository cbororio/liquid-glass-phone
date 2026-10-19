package com.tonny.dial.ui.screens

import android.provider.CallLog
import android.text.format.DateUtils
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tonny.dial.data.Contact
import com.tonny.dial.data.ContactsRepository
import com.tonny.dial.data.RecentCall
import com.tonny.dial.ui.components.Avatar
import com.tonny.dial.ui.components.Hairline
import com.tonny.dial.ui.components.GlassButton
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.tonny.dial.ui.components.AppBackground
import com.tonny.dial.ui.components.LocalBackgroundBackdrop
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Settings
import com.tonny.dial.ui.components.SegmentedControl
import com.tonny.dial.ui.components.iosSpring
import com.tonny.dial.ui.theme.Ios
import com.tonny.dial.ui.theme.IosType
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.roundToInt

class ListActions(
    val call: (String) -> Unit,
    val message: (String) -> Unit,
    val addContact: (String) -> Unit,
    val openContact: (Contact) -> Unit,
    val showCard: (CardTarget) -> Unit,
    val toggleStar: (Contact) -> Unit,
    val toggleBlock: (List<String>) -> Unit,
    val openSettings: () -> Unit,
)

/** What the contact card sheet is about: a saved contact, a bare number, or both. */
data class CardTarget(val contact: Contact?, val number: String, val title: String, val photoUri: String?)

// ================================================================ large-title scaffold

/**
 * iOS navigation layout: a big bold title that scrolls away while a compact centered title
 * fades into a soft scroll-edge bar.
 */
@Composable
fun LargeTitleList(
    title: String,
    bottomInset: Dp,
    state: LazyListState = rememberLazyListState(),
    topLeading: (@Composable (Backdrop) -> Unit)? = null,
    topCenter: (@Composable (Backdrop) -> Unit)? = null,
    topTrailing: (@Composable (Backdrop) -> Unit)? = null,
    overlay: @Composable BoxScope.() -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    val ios = Ios.colors
    val collapsed by remember {
        derivedStateOf { state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 90 }
    }
    val titleAlpha by animateFloatAsState(if (collapsed) 1f else 0f, iosSpring(), label = "title")
    // Two layers: the background (wallpaper) and the scrolling list. Glass inside the list bends
    // the background; glass in the top bar bends both.
    val backgroundBackdrop = rememberLayerBackdrop()
    val listBackdrop = rememberLayerBackdrop()
    val backdrop = rememberCombinedBackdrop(backgroundBackdrop, listBackdrop)

    Box(Modifier.fillMaxSize()) {
        AppBackground(Modifier.layerBackdrop(backgroundBackdrop))
        CompositionLocalProvider(LocalBackgroundBackdrop provides backgroundBackdrop) {
        LazyColumn(
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
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp).graphicsLayer { alpha = 1f - titleAlpha },
                    )
                }
            }
            content()
        }
        }

        // Scroll-edge bar: background fades in under the compact title.
        Column(Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(
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
                        if (topCenter != null) topCenter(backdrop)
                        else Text(title, style = IosType.headline, color = ios.label, modifier = Modifier.graphicsLayer { alpha = titleAlpha })
                        if (topLeading != null) Box(Modifier.align(Alignment.CenterStart)) { topLeading(backdrop) }
                        if (topTrailing != null) Box(Modifier.align(Alignment.CenterEnd)) { topTrailing(backdrop) }
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
        overlay()
    }
}

// ================================================================ recents

@Composable
fun RecentsScreen(recents: List<RecentCall>, contacts: List<Contact>, actions: ListActions, bottomInset: Dp) {
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val shown = remember(recents, filter) { if (filter == 1) recents.filter { it.isMissed } else recents }
    val byNumber = remember(contacts) {
        contacts.flatMap { c -> c.numbers.map { ContactsRepository.normalize(it.number).takeLast(9) to c } }.toMap()
    }

    val ios = Ios.colors
    LargeTitleList(
        title = "Recents",
        bottomInset = bottomInset,
        topCenter = { bd -> SegmentedControl(bd, listOf("All", "Missed"), filter, { filter = it }, Modifier.width(210.dp)) },
        topTrailing = { bd ->
            GlassButton(bd, onClick = actions.openSettings, size = 44.dp, contentDescription = "Settings") {
                Icon(Icons.Rounded.Settings, null, tint = ios.label, modifier = Modifier.size(22.dp))
            }
        },
    ) {
        if (shown.isEmpty()) {
            item { EmptyState(if (filter == 1) "No Missed Calls" else "No Recents") }
        }
        items(shown, key = { it.ids.first() }) { r ->
            val contact = byNumber[ContactsRepository.normalize(r.number).takeLast(9)]
            // Prefer the live contact over the name cached in the call log, so renames show up.
            val title = contact?.name ?: r.title
            val photo = if (contact != null) contact.photoUri else r.photoUri
            // Own render layer per row: each glass control re-draws the list behind it every frame,
            // and a layer lets the GPU skip rows that aren't under that control.
            Box(Modifier.animateItem(fadeInSpec = null, fadeOutSpec = iosSpring(), placementSpec = iosSpring()).graphicsLayer()) {
                RecentRow(
                    r, title, photo, saved = contact != null || !r.name.isNullOrBlank(),
                    onClick = { if (r.number.isNotBlank()) actions.call(r.number) },
                    onInfo = { actions.showCard(CardTarget(contact, r.number, title, photo)) },
                )
            }
        }
    }
}

@Composable
private fun RecentRow(r: RecentCall, title: String, photoUri: String?, saved: Boolean, onClick: () -> Unit, onInfo: () -> Unit) {
    val ios = Ios.colors
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, end = 6.dp, top = 9.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(title, photoUri, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    buildAnnotatedString {
                        append(title)
                        if (r.count > 1) withStyle(SpanStyle(fontWeight = FontWeight.Normal)) { append(" (${r.count})") }
                    },
                    style = IosType.headline,
                    color = if (r.isMissed) ios.red else ios.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (r.type == CallLog.Calls.OUTGOING_TYPE) {
                        Icon(Icons.AutoMirrored.Rounded.CallMade, null, tint = ios.secondaryLabel, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(3.dp))
                    }
                    Text(if (saved) "mobile" else "phone", style = IosType.subhead, color = ios.secondaryLabel)
                }
            }
            Text(remember(r.date) { shortDate(r.date) }, style = IosType.subhead, color = ios.secondaryLabel)
            Icon(
                Icons.Outlined.Info,
                "Details",
                tint = ios.blue,
                modifier = Modifier
                    .size(44.dp)
                    .clickable(remember { MutableInteractionSource() }, null, onClick = onInfo)
                    .padding(10.dp),
            )
        }
        Hairline(Modifier.padding(start = 68.dp))
    }
}

private fun shortDate(millis: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = millis }
    val sameYear = now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
    val dayDiff = now.get(Calendar.DAY_OF_YEAR) - then.get(Calendar.DAY_OF_YEAR)
    return when {
        sameYear && dayDiff == 0 -> java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(millis)
        sameYear && dayDiff == 1 -> "Yesterday"
        sameYear && dayDiff < 7 -> java.text.SimpleDateFormat("EEEE", java.util.Locale.getDefault()).format(millis)
        else -> java.text.SimpleDateFormat("d/M/yy", java.util.Locale.getDefault()).format(millis)
    }
}

// ================================================================ contacts

@Composable
fun ContactsScreen(contacts: List<Contact>, actions: ListActions, bottomInset: Dp) {
    val ios = Ios.colors
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val grouped = remember(contacts) {
        contacts.groupBy { c -> c.name.firstOrNull()?.uppercaseChar()?.takeIf { it.isLetter() } ?: '#' }.toSortedMap(compareBy { if (it == '#') '~' else it })
    }
    // Lazy index of each section header (the large title is item 0).
    val headerIndex = remember(grouped) {
        var i = 1
        grouped.map { (letter, list) -> (letter to i).also { i += list.size + 1 } }.toMap()
    }

    LargeTitleList(
        title = "Contacts",
        bottomInset = bottomInset,
        state = state,
        topTrailing = { bd ->
            GlassButton(bd, onClick = { actions.addContact("") }, size = 44.dp, contentDescription = "New contact") {
                Icon(Icons.Rounded.Add, null, tint = ios.label, modifier = Modifier.size(24.dp))
            }
        },
        overlay = {
            if (grouped.size > 4) {
                IndexScrubber(
                    letters = grouped.keys.toList(),
                    onLetter = { l -> headerIndex[l]?.let { scope.launch { state.scrollToItem(it) } } },
                    modifier = Modifier.align(Alignment.CenterEnd).padding(bottom = bottomInset / 2),
                )
            }
        },
    ) {
        if (contacts.isEmpty()) item { EmptyState("No Contacts") }
        grouped.forEach { (letter, list) ->
            item(key = "L$letter") {
                Text(
                    letter.toString(),
                    style = IosType.footnote.copy(fontWeight = FontWeight.SemiBold),
                    color = ios.secondaryLabel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 2.dp),
                )
            }
            items(list, key = { it.id }) { c ->
                ContactRow(c) { actions.showCard(CardTarget(c, c.numbers.first().number, c.name, c.photoUri)) }
            }
        }
        if (contacts.isNotEmpty()) {
            item(key = "__count") {
                Text(
                    "${contacts.size} Contacts",
                    style = IosType.subhead,
                    color = ios.secondaryLabel,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 8.dp),
                )
            }
        }
    }
}

@Composable
fun ContactRow(c: Contact, onClick: () -> Unit) {
    val ios = Ios.colors
    // Own render layer, like recent rows, so glass controls only re-draw the rows under them.
    Column(Modifier.graphicsLayer().clickable(onClick = onClick)) {
        // iOS bolds the family name.
        val parts = c.name.trim().split(' ')
        Text(
            buildAnnotatedString {
                if (parts.size > 1) {
                    append(parts.dropLast(1).joinToString(" ") + " ")
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(parts.last()) }
                } else withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(c.name) }
            },
            style = IosType.body,
            color = ios.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 18.dp, end = 36.dp, top = 11.dp, bottom = 11.dp),
        )
        Hairline(Modifier.padding(start = 18.dp))
    }
}

/** The A–Z rail on the right edge; tap or drag to jump. */
@Composable
private fun IndexScrubber(letters: List<Char>, onLetter: (Char) -> Unit, modifier: Modifier) {
    val ios = Ios.colors
    val view = LocalView.current
    var height by remember { mutableIntStateOf(1) }
    var last by remember { mutableStateOf<Char?>(null) }
    fun pick(y: Float) {
        val i = (y / height * letters.size).toInt().coerceIn(0, letters.lastIndex)
        val l = letters[i]
        if (l != last) {
            last = l
            view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_FREQUENT_TICK)
            onLetter(l)
        }
    }
    Column(
        modifier
            .width(22.dp)
            .onSizeChanged { height = it.height }
            .pointerInput(letters) {
                detectVerticalDragGestures(
                    onDragStart = { pick(it.y) },
                    onDragEnd = { last = null },
                ) { change, _ -> change.consume(); pick(change.position.y) }
            }
            .pointerInput(letters) { detectTapGestures { pick(it.y); last = null } },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        letters.forEach { Text(it.toString(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = ios.blue, lineHeight = 14.sp) }
    }
}

// ================================================================ search results

@Composable
fun SearchResults(query: String, contacts: List<Contact>, actions: ListActions, bottomInset: Dp) {
    val ios = Ios.colors
    val q = query.trim().lowercase()
    val digits = q.filter { it.isDigit() }
    val results = remember(q, contacts) {
        if (q.isEmpty()) emptyList()
        else contacts.filter { c ->
            c.name.lowercase().contains(q) || (digits.length >= 3 && c.numbers.any { ContactsRepository.normalize(it.number).contains(digits) })
        }
    }
    Box(Modifier.fillMaxSize()) {
    AppBackground()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomInset + 20.dp),
    ) {
        item {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Text("Search", style = IosType.largeTitle, color = ios.label, modifier = Modifier.padding(start = 18.dp, top = 52.dp, bottom = 8.dp))
        }
        if (q.isEmpty()) item { EmptyState("Search", "Find people by name or number.") }
        else if (results.isEmpty()) item { EmptyState("No Results for “$query”") }
        items(results, key = { it.id }) { c ->
            Row(
                Modifier.fillMaxWidth().graphicsLayer().clickable { actions.showCard(CardTarget(c, c.numbers.first().number, c.name, c.photoUri)) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(c.name, c.photoUri, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(c.name, style = IosType.headline, color = ios.label)
                    Text(c.numbers.first().number, style = IosType.subhead, color = ios.secondaryLabel)
                }
            }
        }
    }
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
