package com.tonny.dial.ui.screens

import android.provider.CallLog
import android.text.format.DateUtils
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import com.tonny.dial.data.BlockedRepository
import com.tonny.dial.data.Contact
import com.tonny.dial.data.Prefs
import com.tonny.dial.data.Wallpaper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tonny.dial.data.ContactsRepository
import com.tonny.dial.data.RecentCall
import com.tonny.dial.ui.components.Avatar
import com.tonny.dial.ui.components.Hairline
import com.tonny.dial.ui.components.iosSpring
import com.tonny.dial.ui.components.liquidGlass
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.tonny.dial.ui.theme.Ios
import com.tonny.dial.ui.theme.IosType

@Composable
fun ContactCard(
    target: CardTarget,
    contacts: List<Contact>,
    recents: List<RecentCall>,
    blocked: Set<String>,
    actions: ListActions,
    backdrop: Backdrop,
) {
    val ios = Ios.colors
    // Re-read the contact so the star reflects live state.
    val contact = target.contact?.let { t -> contacts.firstOrNull { it.id == t.id } ?: t }
    val numbers = contact?.numbers?.map { it.label.lowercase() to it.number } ?: listOf("phone" to target.number)
    val keys = numbers.map { ContactsRepository.normalize(it.second).takeLast(9) }.toSet()
    val title = contact?.name ?: target.title
    val photoUri = if (contact != null) contact.photoUri else target.photoUri
    val isBlocked = numbers.any { BlockedRepository.key(it.second) in blocked }
    // Individual calls (not merged Recents rows), newest first, grouped under a header per day.
    val history = remember(recents, keys) {
        recents.filter { ContactsRepository.normalize(it.number).takeLast(9) in keys }
            .flatMap { it.entries }
            .take(12)
            .groupBy { dayStart(it.date) }
    }

    val glassy = Prefs.settings.collectAsStateWithLifecycle().value.appWallpaper != Wallpaper.None
    CompositionLocalProvider(LocalCardGlass provides if (glassy) backdrop else null) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(28.dp))
        Avatar(title, photoUri, size = 104.dp)
        Spacer(Modifier.height(12.dp))
        Text(title, style = IosType.title1, color = ios.label, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val first = numbers.first().second
            ActionTile(backdrop, Icons.AutoMirrored.Rounded.Message, "message", Modifier.weight(1f)) { actions.message(first) }
            ActionTile(backdrop, Icons.Rounded.Call, "call", Modifier.weight(1f)) { actions.call(first) }
            if (contact != null) {
                ActionTile(backdrop, if (contact.starred) Icons.Rounded.Star else Icons.Rounded.StarBorder, if (contact.starred) "favorite" else "add favorite", Modifier.weight(1f)) {
                    actions.toggleStar(contact)
                }
                ActionTile(backdrop, Icons.Rounded.Edit, "edit", Modifier.weight(1f)) { actions.openContact(contact) }
            } else {
                ActionTile(backdrop, Icons.Rounded.PersonAdd, "add", Modifier.weight(1f)) { actions.addContact(first) }
            }
        }

        Spacer(Modifier.height(16.dp))
        Group {
            numbers.forEachIndexed { i, (label, number) ->
                Column(
                    Modifier.fillMaxWidth().clickable { actions.call(number) }.padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text(label, style = IosType.subhead, color = ios.label)
                    Text(number, style = IosType.body, color = ios.blue)
                }
                if (i < numbers.lastIndex) Hairline(Modifier.padding(start = 16.dp))
            }
        }

        if (history.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Group {
                history.forEach { (day, calls) ->
                Text(
                    dayLabel(day),
                    style = IosType.subhead.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                    color = ios.label,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                )
                calls.forEach { r ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Text(
                            java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(r.date),
                            style = IosType.subhead, color = ios.label, modifier = Modifier.width(84.dp),
                        )
                        Text(
                            when (r.type) {
                                CallLog.Calls.OUTGOING_TYPE -> "Outgoing Call"
                                CallLog.Calls.MISSED_TYPE -> "Missed Call"
                                CallLog.Calls.REJECTED_TYPE -> "Declined Call"
                                else -> "Incoming Call"
                            },
                            style = IosType.subhead,
                            color = if (r.isMissed) ios.red else ios.label,
                            modifier = Modifier.weight(1f),
                        )
                        if (r.duration > 0 && !r.isMissed) Text(DateUtils.formatElapsedTime(r.duration), style = IosType.subhead, color = ios.secondaryLabel)
                    }
                }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        Group {
            Text(
                if (isBlocked) "Unblock this Caller" else "Block this Caller",
                style = IosType.body,
                color = if (isBlocked) ios.blue else ios.red,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { actions.toggleBlock(numbers.map { it.second }) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        Spacer(Modifier.height(40.dp))
    }
    }
}

private val LocalCardGlass = staticCompositionLocalOf<Backdrop?> { null }

/** A grouped card: solid on a plain background, translucent over a wallpaper. */
private fun dayStart(millis: Long): Long = java.util.Calendar.getInstance().apply {
    timeInMillis = millis
    set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
    set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
}.timeInMillis

/** "Today", "Yesterday", "Saturday, 10 October", or with the year when it isn't this year. */
private fun dayLabel(day: Long): String {
    val today = dayStart(System.currentTimeMillis())
    val yesterday = dayStart(today - 1)
    val thisYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
    val year = java.util.Calendar.getInstance().apply { timeInMillis = day }.get(java.util.Calendar.YEAR)
    val pattern = if (year == thisYear) "EEEE, d MMMM" else "EEEE, d MMMM yyyy"
    return when (day) {
        today -> "Today"
        yesterday -> "Yesterday"
        else -> java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault()).format(day)
    }
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    val ios = Ios.colors
    val backdrop = LocalCardGlass.current
    Column(
        Modifier
            .fillMaxWidth()
            .then(
                // Translucent rather than blurred glass: live blur behind every card cost too much GPU on low-end phones.
                if (backdrop != null) Modifier.background(
                    (if (ios.dark) ios.card else Color(0xFFF2F2F7)).copy(alpha = 0.78f), RoundedRectangle(14.dp),
                ) else Modifier.background(ios.card, RoundedRectangle(14.dp)),
            ),
    ) { content() }
}

@Composable
private fun ActionTile(backdrop: Backdrop, icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    val ios = Ios.colors
    var down by remember { mutableStateOf(false) }
    val s by animateFloatAsState(if (down) 1.06f else 1f, iosSpring(bouncy = true), label = "tile")
    Column(
        modifier
            .liquidGlass(backdrop, RoundedRectangle(16.dp), ios.controlTint.copy(alpha = 0.85f), scale = { s })
            .pointerInput(onClick) {
                detectTapGestures(onPress = { down = true; tryAwaitRelease(); down = false }, onTap = { onClick() })
            }
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = ios.blue, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, style = IosType.caption, color = ios.blue, maxLines = 1)
    }
}

/** Sheet grabber. */
@Composable
fun Grabber(modifier: Modifier = Modifier) {
    Box(modifier.padding(top = 6.dp).size(36.dp, 5.dp).background(Ios.colors.tertiaryLabel, Capsule()))
}
