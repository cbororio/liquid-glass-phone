package com.tonny.messages.ui.screens

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.shapes.Capsule
import com.tonny.messages.data.ContactMatch
import com.tonny.messages.data.ContactSearch
import com.tonny.messages.sms.MessagesEvents
import com.tonny.messages.sms.SmsSender
import com.tonny.messages.ui.components.Avatar
import com.tonny.messages.ui.components.Hairline
import com.tonny.messages.ui.theme.Ios
import com.tonny.messages.ui.theme.IosType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewMessageScreen(onBack: () -> Unit, onSent: (Long, String) -> Unit) {
    val ios = Ios.colors
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val backdrop = rememberLayerBackdrop()
    var query by remember { mutableStateOf("") }
    var recipients by remember { mutableStateOf<List<ContactMatch>>(emptyList()) }
    var results by remember { mutableStateOf<List<ContactMatch>>(emptyList()) }
    val focus = remember { FocusRequester() }
    // SIM picker only appears with two or more active SIMs.
    val sims = remember { activeSims(context) }
    var sim by remember { mutableStateOf(sims.firstOrNull()?.subId) }
    var simMenu by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    LaunchedEffect(query) {
        if (query.isBlank()) { results = emptyList(); return@LaunchedEffect }
        delay(150)
        results = withContext(Dispatchers.IO) { runCatching { ContactSearch.search(context, query) }.getOrDefault(emptyList()) }
    }

    // Numbers to send to: the chosen contacts, plus a typed number if one is sitting in the field.
    fun targets(): List<String> {
        val typed = query.trim().takeIf { it.isNotEmpty() && it.any(Char::isDigit) }
        return (recipients.map { it.number } + listOfNotNull(typed)).distinct()
    }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.matchParentSize().background(ios.background).layerBackdrop(backdrop))
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Box(Modifier.fillMaxWidth().height(52.dp), contentAlignment = Alignment.Center) {
                Text("New Message", style = IosType.headline, color = ios.label)
                Text(
                    "Cancel", style = IosType.body, color = ios.blue,
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 16.dp)
                        .clickable(remember { MutableInteractionSource() }, null, onClick = onBack),
                )
            }

            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.Top) {
                Text("To:", style = IosType.body, color = ios.secondaryLabel, modifier = Modifier.padding(top = 6.dp))
                Spacer(Modifier.width(10.dp))
                FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    recipients.forEach { r ->
                        RecipientChip(r) { recipients = recipients - r }
                    }
                    Box(Modifier.widthIn(min = 120.dp).padding(top = 4.dp)) {
                        if (query.isEmpty() && recipients.isEmpty()) Text("Name or number", style = IosType.body, color = ios.tertiaryLabel)
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = IosType.body.copy(color = ios.label),
                            cursorBrush = SolidColor(ios.blue),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        )
                    }
                }
            }
            Hairline()

            LazyColumn(Modifier.weight(1f)) {
                items(results, key = { it.number }) { match ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            if (recipients.none { it.number == match.number }) recipients = recipients + match
                            query = ""
                        }.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(match.name, match.photoUri, size = 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(match.name, style = IosType.headline, color = ios.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${match.label}  ${match.number}", style = IosType.subhead, color = ios.secondaryLabel, maxLines = 1)
                        }
                    }
                }
            }

            Composer(
                backdrop,
                enabled = targets().isNotEmpty(),
                onSend = { text ->
                    val numbers = targets()
                    if (numbers.isNotEmpty()) {
                        scope.launch {
                            // Group SMS sends an individual message to each recipient.
                            val firstThread = withContext(Dispatchers.IO) {
                                numbers.map { n -> sim?.let { com.tonny.messages.data.Prefs.setSim(n, it) }; SmsSender.send(context, n, text, sim) }.first()
                            }
                            MessagesEvents.bump()
                            onSent(firstThread, numbers.first())
                        }
                    }
                },
                sims = sims,
                selectedSim = sim,
                onSelectSim = { sim = it },
                onSimMenu = { simMenu = it },
            )
        }
        simMenu?.let { rect -> SimMenu(backdrop, rect, sims, sim, onPick = { sim = it; simMenu = null }, onDismiss = { simMenu = null }) }
    }
    androidx.activity.compose.BackHandler(simMenu != null) { simMenu = null }
}

@Composable
private fun RecipientChip(match: ContactMatch, onRemove: () -> Unit) {
    val ios = Ios.colors
    Row(
        Modifier.clip(Capsule()).background(ios.blue.copy(alpha = 0.16f)).padding(start = 6.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Avatar(match.name, match.photoUri, size = 26.dp)
        Text(match.name, style = IosType.subhead.copy(fontWeight = FontWeight.Medium), color = ios.blue, maxLines = 1)
        Icon(
            Icons.Rounded.Close, "Remove", tint = ios.blue,
            modifier = Modifier.size(22.dp).clip(CircleShape).clickable(remember { MutableInteractionSource() }, null, onClick = onRemove).padding(3.dp),
        )
    }
}
