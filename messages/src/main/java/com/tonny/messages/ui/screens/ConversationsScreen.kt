package com.tonny.messages.ui.screens

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MarkEmailRead
import androidx.compose.material.icons.rounded.MarkEmailUnread
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Report
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tonny.messages.data.Conversation
import com.tonny.messages.ui.components.Avatar
import com.tonny.messages.ui.components.GlassButton
import com.tonny.messages.ui.components.GlassMenu
import com.tonny.messages.ui.components.GlassMenuItem
import com.tonny.messages.ui.components.Hairline
import com.tonny.messages.ui.theme.Ios
import com.tonny.messages.ui.theme.IosType

class ConvoActions(
    val delete: (List<Conversation>) -> Unit,
    val markRead: (List<Conversation>) -> Unit,
    val markUnread: (List<Conversation>) -> Unit,
    val markAllRead: () -> Unit,
    val setSpam: (List<Conversation>, Boolean) -> Unit,
    val setArchived: (List<Conversation>, Boolean) -> Unit,
)

enum class ListMode { Inbox, Spam, Archived }

private data class RowMenu(val anchor: Rect, val convo: Conversation)

@Composable
fun ConversationsScreen(
    title: String,
    conversations: List<Conversation>,
    mode: ListMode,
    bottomInset: Dp,
    onOpen: (Conversation) -> Unit,
    actions: ConvoActions,
    editing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    onCompose: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    onOpenSpam: (() -> Unit)? = null,
    onOpenArchived: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
) {
    val ios = Ios.colors
    val listState = rememberLazyListState()
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var query by remember { mutableStateOf("") }
    var overflow by remember { mutableStateOf<Rect?>(null) }
    var rowMenu by remember { mutableStateOf<RowMenu?>(null) }
    LaunchedEffect(editing) { if (!editing) selected = emptySet() }

    // iOS-style: hide the search bar when scrolling down, reveal it when scrolling up or at top.
    var searchVisible by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        var pi = 0; var po = 0
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }.collect { (i, o) ->
            when {
                i == 0 && o < 40 -> searchVisible = true
                i > pi || (i == pi && o > po + 8) -> searchVisible = false
                i < pi || (i == pi && o < po - 8) -> searchVisible = true
            }
            pi = i; po = o
        }
    }

    val shown = remember(conversations, query) {
        if (query.isBlank()) conversations else {
            val q = query.trim().lowercase(); val digits = q.filter { it.isDigit() }
            conversations.filter {
                it.title.lowercase().contains(q) || it.snippet.lowercase().contains(q) ||
                    (digits.length >= 3 && it.address.filter { c -> c.isDigit() }.contains(digits))
            }
        }
    }
    val selectedConvos = conversations.filter { it.threadId in selected }
    val hasUnread = conversations.any { it.unread }

    Box(Modifier.fillMaxWidth()) {
        LargeTitleList(
            title = title,
            bottomInset = bottomInset + if (editing) 64.dp else 0.dp,
            state = listState,
            topLeading = if (onBack != null) ({
                Text("Back", style = IosType.body, color = ios.blue, modifier = Modifier.clickable(remember { MutableInteractionSource() }, null, onClick = onBack).padding(8.dp))
            }) else if (editing) ({
                Text("Done", style = IosType.body, color = ios.blue, modifier = Modifier.clickable(remember { MutableInteractionSource() }, null) { onEditingChange(false) }.padding(8.dp))
            }) else null,
            topTrailing = { bd ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AnchorButton(ios.fill, Icons.Rounded.MoreHoriz, "More", tintColor = ios.blue) { overflow = it }
                    if (!editing && onCompose != null) {
                        Spacer(Modifier.width(8.dp))
                        GlassButton(bd, onClick = onCompose, size = 44.dp, contentDescription = "New message") {
                            Icon(Icons.AutoMirrored.Rounded.Chat, null, tint = ios.blue, modifier = Modifier.size(22.dp))
                        }
                    }
                }
            },
            menuOverlay = { bd ->
                if (overflow != null) {
                    GlassMenu(bd, overflow, onDismiss = { overflow = null }) {
                        if (!editing) GlassMenuItem("Edit", Icons.Rounded.CheckCircle) { overflow = null; onEditingChange(true) }
                        if (hasUnread) GlassMenuItem("Mark all as read", Icons.Rounded.MarkEmailRead) { overflow = null; actions.markAllRead() }
                        onOpenSpam?.let { f -> GlassMenuItem("Spam", Icons.Rounded.Report) { overflow = null; f() } }
                        onOpenArchived?.let { f -> GlassMenuItem("Archived", Icons.Rounded.Archive) { overflow = null; f() } }
                        onOpenSettings?.let { f -> GlassMenuItem("Settings", Icons.Rounded.Settings) { overflow = null; f() } }
                    }
                }
                rowMenu?.let { rm ->
                    val one = listOf(rm.convo)
                    GlassMenu(bd, rm.anchor, onDismiss = { rowMenu = null }) {
                        if (mode == ListMode.Archived) GlassMenuItem("Unarchive", Icons.Rounded.Unarchive) { rowMenu = null; actions.setArchived(one, false) }
                        else GlassMenuItem("Archive", Icons.Rounded.Archive) { rowMenu = null; actions.setArchived(one, true) }
                        if (rm.convo.unread) GlassMenuItem("Mark as read", Icons.Rounded.MarkEmailRead) { rowMenu = null; actions.markRead(one) }
                        else GlassMenuItem("Mark as unread", Icons.Rounded.MarkEmailUnread) { rowMenu = null; actions.markUnread(one) }
                        GlassMenuItem(if (mode == ListMode.Spam) "Not spam" else "Mark as spam", Icons.Rounded.Report) { rowMenu = null; actions.setSpam(one, mode != ListMode.Spam) }
                        GlassMenuItem("Delete", Icons.Rounded.Delete, destructive = true) { rowMenu = null; actions.delete(one) }
                    }
                }
            },
        ) {
            if (!editing) {
                item(key = "__search") {
                    AnimatedVisibility(searchVisible, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        SearchField(query, { query = it })
                    }
                }
            }
            if (shown.isEmpty()) {
                item {
                    EmptyState(
                        if (query.isNotBlank()) "No Results" else when (mode) { ListMode.Spam -> "No Spam"; ListMode.Archived -> "No Archived"; else -> "No Messages" },
                        if (query.isNotBlank()) null else when (mode) {
                            ListMode.Spam -> "Threads you mark as spam move here."
                            ListMode.Archived -> "Threads you archive move here."
                            else -> "Tap the compose button to start a conversation."
                        },
                    )
                }
            }
            items(shown, key = { it.threadId }) { convo ->
                if (editing) {
                    ConversationRow(
                        convo,
                        onClick = { selected = if (convo.threadId in selected) selected - convo.threadId else selected + convo.threadId },
                        leading = {
                            val on = convo.threadId in selected
                            Icon(if (on) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null, tint = if (on) ios.blue else ios.tertiaryLabel, modifier = Modifier.size(24.dp))
                        },
                    )
                } else {
                    ConversationRow(
                        convo,
                        onClick = { onOpen(convo) },
                        trailing = { AnchorButton(Color.Transparent, Icons.Rounded.MoreHoriz, "Options", tintColor = ios.secondaryLabel, size = 40.dp) { rowMenu = RowMenu(it, convo) } },
                    )
                }
            }
        }

        AnimatedVisibility(
            editing, enter = fadeIn() + scaleIn(initialScale = 0.9f), exit = fadeOut() + scaleOut(targetScale = 0.9f),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            EditBar(
                count = selectedConvos.size, mode = mode, bottomInset = bottomInset,
                onDelete = { actions.delete(selectedConvos); onEditingChange(false) },
                onRead = { actions.markRead(selectedConvos) },
                onSpam = { actions.setSpam(selectedConvos, mode != ListMode.Spam); onEditingChange(false) },
            )
        }
    }
}

/** An icon button that reports its on-screen bounds so a glass menu can anchor to it. */
@Composable
private fun AnchorButton(bg: Color, icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, tintColor: Color, size: Dp = 44.dp, onOpen: (Rect) -> Unit) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    Box(
        Modifier.size(size).onGloballyPositioned { bounds = it.boundsInWindow() }
            .then(if (bg != Color.Transparent) Modifier.clip(CircleShape).background(bg) else Modifier)
            .clickable(remember { MutableInteractionSource() }, null) { onOpen(bounds) },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = tintColor, modifier = Modifier.size(if (size > 40.dp) 24.dp else 22.dp)) }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    val ios = Ios.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).height(40.dp).clip(CircleShape).background(ios.fill).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = ios.secondaryLabel, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text("Search", style = IosType.body, color = ios.secondaryLabel)
            BasicTextField(query, onChange, singleLine = true, textStyle = IosType.body.copy(color = ios.label), cursorBrush = SolidColor(ios.blue), modifier = Modifier.fillMaxWidth())
        }
        if (query.isNotEmpty()) {
            Icon(Icons.Rounded.Close, "Clear", tint = ios.tertiaryLabel, modifier = Modifier.size(20.dp).clip(CircleShape).clickable(remember { MutableInteractionSource() }, null) { onChange("") })
        }
    }
}

@Composable
private fun EditBar(count: Int, mode: ListMode, bottomInset: Dp, onDelete: () -> Unit, onRead: () -> Unit, onSpam: () -> Unit) {
    val ios = Ios.colors
    val enabled = count > 0
    Row(
        Modifier.fillMaxWidth().background(ios.card).padding(bottom = bottomInset).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BarAction("Delete", ios.red, enabled, onDelete)
        Spacer(Modifier.weight(1f))
        BarAction("Read", ios.blue, enabled, onRead)
        Spacer(Modifier.weight(1f))
        BarAction(if (mode == ListMode.Spam) "Not Spam" else "Spam", ios.orange, enabled, onSpam)
    }
}

@Composable
private fun BarAction(label: String, color: Color, enabled: Boolean, onClick: () -> Unit) {
    Text(label, style = IosType.body, color = if (enabled) color else color.copy(alpha = 0.4f), modifier = Modifier.clickable(remember { MutableInteractionSource() }, null, enabled = enabled, onClick = onClick).padding(6.dp))
}

@Composable
private fun ConversationRow(convo: Conversation, onClick: () -> Unit, leading: (@Composable () -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    val ios = Ios.colors
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading(); Spacer(Modifier.width(10.dp))
            } else {
                Box(Modifier.size(10.dp), contentAlignment = Alignment.Center) {
                    if (convo.unread) Box(Modifier.size(10.dp).clip(CircleShape).background(ios.blue))
                }
                Spacer(Modifier.width(6.dp))
            }
            Avatar(convo.title, convo.photoUri, size = 52.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        convo.title,
                        style = IosType.headline.copy(fontWeight = if (convo.unread) FontWeight.Bold else FontWeight.SemiBold),
                        color = ios.label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(relativeDate(convo.date), style = IosType.footnote, color = ios.secondaryLabel, maxLines = 1)
                }
                Spacer(Modifier.size(2.dp))
                Text(convo.snippet, style = IosType.subhead, color = ios.secondaryLabel, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (trailing != null) { Spacer(Modifier.width(2.dp)); trailing() }
        }
        Hairline(Modifier.padding(start = 82.dp))
    }
}

fun relativeDate(millis: Long): String {
    if (millis <= 0) return ""
    val now = System.currentTimeMillis()
    return when {
        isSameDay(now, millis) -> java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(millis)
        isYesterday(now, millis) -> "Yesterday"
        now - millis < 7 * DateUtils.DAY_IN_MILLIS -> java.text.SimpleDateFormat("EEE", java.util.Locale.getDefault()).format(millis)
        else -> java.text.SimpleDateFormat("d/M/yy", java.util.Locale.getDefault()).format(millis)
    }
}

private fun isSameDay(a: Long, b: Long): Boolean {
    val ca = java.util.Calendar.getInstance().apply { timeInMillis = a }
    val cb = java.util.Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(java.util.Calendar.YEAR) == cb.get(java.util.Calendar.YEAR) && ca.get(java.util.Calendar.DAY_OF_YEAR) == cb.get(java.util.Calendar.DAY_OF_YEAR)
}

private fun isYesterday(now: Long, then: Long): Boolean {
    val y = java.util.Calendar.getInstance().apply { timeInMillis = now; add(java.util.Calendar.DAY_OF_YEAR, -1) }
    val t = java.util.Calendar.getInstance().apply { timeInMillis = then }
    return y.get(java.util.Calendar.YEAR) == t.get(java.util.Calendar.YEAR) && y.get(java.util.Calendar.DAY_OF_YEAR) == t.get(java.util.Calendar.DAY_OF_YEAR)
}
