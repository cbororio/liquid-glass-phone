package com.tonny.messages.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.tonny.messages.mms.MmsSender
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import com.tonny.messages.ui.components.GlassMenu
import com.tonny.messages.ui.components.GlassMenuItem
import com.tonny.messages.mms.OutgoingAttachment
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.style.TextAlign
import java.util.Calendar
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.platform.LocalView
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.view.HapticFeedbackConstants
import android.widget.Toast
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.shapes.Capsule
import com.tonny.messages.data.Message
import com.tonny.messages.data.SmsRepository
import com.tonny.messages.sms.MessageNotifier
import com.tonny.messages.sms.MessagesEvents
import com.tonny.messages.sms.SmsSender
import com.tonny.messages.ui.components.Avatar
import com.tonny.messages.ui.components.iosSpring
import com.tonny.messages.ui.components.liquidGlass
import com.tonny.messages.ui.theme.Ios
import com.tonny.messages.ui.theme.IosType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ThreadScreen(
    threadId: Long,
    address: String,
    title: String,
    photoUri: String?,
    onBack: () -> Unit,
) {
    val ios = Ios.colors
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val version by MessagesEvents.version.collectAsStateWithLifecycle()
    var messages by remember { mutableStateOf<List<Message>>(emptyList()) }
    val listState = rememberLazyListState()
    val backdrop = rememberLayerBackdrop()

    var firstLoad by remember(threadId) { mutableStateOf(true) }
    var searching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    // Open glass menus: on a message (with its bounds on screen) or on the + button.
    var messageMenu by remember { mutableStateOf<Pair<Message, Rect>?>(null) }
    var attachMenu by remember { mutableStateOf<Rect?>(null) }
    var simMenu by remember { mutableStateOf<Rect?>(null) }
    var selectFor by remember { mutableStateOf<Message?>(null) }
    // SIM picker only appears with two or more active SIMs.
    val sims = remember { activeSims(context) }
    var sim by remember(address) { mutableStateOf(com.tonny.messages.data.Prefs.simFor(address)?.takeIf { s -> sims.any { it.subId == s } } ?: sims.firstOrNull()?.subId) }

    // ---- attachments (sent as a picture message)
    val isGroup = address.contains(',')
    val recipients = remember(address) { address.split(',').map { it.trim() }.filter { it.isNotEmpty() } }
    var pending by remember { mutableStateOf<OutgoingAttachment?>(null) }
    var recorder by remember { mutableStateOf<VoiceRecorder?>(null) }
    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
    fun budget() = MmsSender.attachmentBudget(context, sim)
    fun prepareImage(uri: android.net.Uri) {
        scope.launch {
            val a = withContext(Dispatchers.IO) { MmsSender.compressImage(context, uri, budget()) }
            if (a == null) toast("Couldn't use that photo") else pending = a
        }
    }
    fun prepareFile(uri: android.net.Uri) {
        scope.launch {
            val (a, size) = withContext(Dispatchers.IO) { runCatching { MmsSender.readFile(context, uri, budget()) }.getOrDefault(null to -1L) }
            when {
                a != null -> pending = a
                size > 0 -> toast("That file is ${size / 1024} KB. Picture messages on your network are limited to ${budget() / 1024} KB.")
                else -> toast("Couldn't read that file")
            }
        }
    }
    fun stopRecording() {
        val r = recorder ?: return
        recorder = null
        val f = r.finish()
        if (f != null) { pending = OutgoingAttachment(f.readBytes(), "audio/amr", f.name); f.delete() }
    }
    fun startRecording() {
        recorder = runCatching { VoiceRecorder(context, budget().toLong()) { stopRecording() } }
            .onFailure { toast("Couldn't start recording") }.getOrNull()
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(::prepareImage) }
    var cameraUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) cameraUri?.let(::prepareImage) }
    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(::prepareFile) }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording() else toast("Allow the microphone to record voice notes")
    }
    fun attach(kind: AttachKind) {
        when (kind) {
            AttachKind.Photo -> photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            AttachKind.Camera -> {
                val file = java.io.File(java.io.File(context.cacheDir, "mms").apply { mkdirs() }, "camera_${System.currentTimeMillis()}.jpg")
                val uri = androidx.core.content.FileProvider.getUriForFile(context, "com.tonny.messages.mmsfiles", file)
                cameraUri = uri
                runCatching { camera.launch(uri) }.onFailure { toast("No camera app found") }
            }
            AttachKind.Voice -> {
                val granted = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (granted) startRecording() else micPermission.launch(android.Manifest.permission.RECORD_AUDIO)
            }
            AttachKind.Document -> documentPicker.launch(arrayOf("*/*"))
        }
    }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { recorder?.cancel() } }

    fun copy(m: Message) {
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Message", m.body))
        // Android 13+ shows its own "Copied" confirmation.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
    }
    fun delete(m: Message) {
        messages = messages - m
        scope.launch { withContext(Dispatchers.IO) { SmsRepository.deleteMessage(context, m) }; MessagesEvents.bump() }
    }
    fun resend(m: Message) {
        scope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    if (m.id >= SmsRepository.MMS_ID_OFFSET) MmsSender.resend(context, m.id - SmsRepository.MMS_ID_OFFSET, sim)
                    else { SmsRepository.deleteMessage(context, m); SmsSender.send(context, address, m.body, sim) }
                }
            }
            MessagesEvents.bump()
        }
        // A failed text is replaced by the fresh send; a picture message is resent in place.
        if (m.id < SmsRepository.MMS_ID_OFFSET) messages = messages - m
    }

    LaunchedEffect(threadId, version) {
        if (threadId > 0) {
            messages = withContext(Dispatchers.IO) { SmsRepository.messages(context, threadId) }
            withContext(Dispatchers.IO) { SmsRepository.markRead(context, threadId) }
            MessageNotifier.clear(context, threadId)
        }
    }
    // Open directly at the latest message (no scroll-through). Later arrivals animate into view.
    LaunchedEffect(messages.size) {
        if (messages.isEmpty() || searching) return@LaunchedEffect
        if (firstLoad) { listState.scrollToItem(messages.lastIndex); firstLoad = false }
        else listState.animateScrollToItem(messages.lastIndex)
    }

    val settings by com.tonny.messages.data.Prefs.settings.collectAsStateWithLifecycle()
    val replyable = com.tonny.messages.data.isReplyable(address)
    val displayed = remember(messages, searching, searchQuery) {
        if (searching && searchQuery.isNotBlank()) messages.filter { it.body.contains(searchQuery, ignoreCase = true) }
        else messages
    }
    // Like iMessage: "Delivered" only under your latest text, once its report is back.
    val deliveredId = remember(displayed) { displayed.lastOrNull { !it.incoming }?.takeIf { it.delivered }?.id }

    Box(Modifier.fillMaxSize().background(ios.background)) {
        // Wallpaper and messages form the layer the glass bars and menus blur.
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            if (settings.chatWallpaper != com.tonny.messages.data.Wallpaper.None) {
                com.tonny.messages.ui.components.WallpaperView(settings.chatWallpaper)
                Box(Modifier.fillMaxSize().background(ios.background.copy(alpha = settings.chatDim)))
            }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().imePadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = if (searching) 150.dp else 108.dp, bottom = 84.dp),
        ) {
            itemsIndexed(displayed, key = { _, m -> m.id }) { i, m ->
                val prev = displayed.getOrNull(i - 1)
                val newSection = prev == null || startsNewSection(prev.date, m.date)
                if (newSection) TimeHeader(m.date)
                val grouped = !newSection && prev != null && prev.incoming == m.incoming && m.date - prev.date < 60_000
                Bubble(m, grouped, onOpenMenu = { rect -> messageMenu = m to rect })
                if (m.id == deliveredId) {
                    Text(
                        "Delivered", style = IosType.caption, color = ios.secondaryLabel, textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth().padding(end = 18.dp, top = 1.dp, bottom = 2.dp),
                    )
                }
            }
        }
        }

        // ---- top glass bar
        TopBar(backdrop, title, photoUri, onBack, searching, searchQuery, onSearchToggle = { searching = it; if (!it) searchQuery = "" }, onSearchChange = { searchQuery = it })

        // ---- composer, or a note for senders you can't reply to
        if (replyable || isGroup) {
            Composer(
                backdrop,
                onSend = { text ->
                    val att = pending
                    pending = null
                    scope.launch {
                        val sent = withContext(Dispatchers.IO) {
                            runCatching {
                                // Attachments and group chats go as a picture message (MMS); plain texts as SMS.
                                if (att != null || isGroup) MmsSender.send(context, recipients, text, att, sim)
                                else SmsSender.send(context, address, text, sim)
                            }.isSuccess
                        }
                        if (!sent) toast("Couldn't send that message")
                        MessagesEvents.bump()
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter),
                sims = sims,
                selectedSim = sim,
                onSelectSim = { id -> sim = id; com.tonny.messages.data.Prefs.setSim(address, id) },
                onSimMenu = { rect -> simMenu = rect },
                attachment = pending,
                onRemoveAttachment = { pending = null },
                onAttachMenu = { rect -> attachMenu = rect },
                recording = recorder?.let { r -> { RecordingBar(r.startedAt, onCancel = { recorder = null; r.cancel() }, onStop = ::stopRecording) } },
            )
        } else {
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)
                    .liquidGlass(backdrop, Capsule(), ios.background.copy(alpha = 0.6f), blur = 10.dp, refractionHeight = 8.dp, refractionAmount = 12.dp, shadow = false)
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "You can't reply to this sender.",
                    style = IosType.subhead, color = ios.secondaryLabel,
                )
            }
        }

        // ---- glass menus and the Select sheet, drawn in-app so they blur the chat behind them
        messageMenu?.let { (m, rect) ->
            GlassMenu(backdrop, rect, onDismiss = { messageMenu = null }, width = 210.dp, alignEnd = !m.incoming) {
                if (m.failed && (replyable || isGroup)) GlassMenuItem("Resend", Icons.Rounded.Refresh) { messageMenu = null; resend(m) }
                if (m.body.isNotBlank()) {
                    GlassMenuItem("Copy", Icons.Rounded.ContentCopy) { messageMenu = null; copy(m) }
                    GlassMenuItem("Select", Icons.Rounded.TextFields) { messageMenu = null; selectFor = m }
                }
                GlassMenuItem("Delete", Icons.Rounded.Delete, destructive = true) { messageMenu = null; delete(m) }
            }
        }
        attachMenu?.let { rect ->
            GlassMenu(backdrop, rect, onDismiss = { attachMenu = null }, width = 210.dp, alignEnd = false) {
                GlassMenuItem("Photo", Icons.Rounded.Image) { attachMenu = null; attach(AttachKind.Photo) }
                GlassMenuItem("Camera", Icons.Rounded.PhotoCamera) { attachMenu = null; attach(AttachKind.Camera) }
                GlassMenuItem("Voice note", Icons.Rounded.Mic) { attachMenu = null; attach(AttachKind.Voice) }
                GlassMenuItem("Document", Icons.Rounded.Description) { attachMenu = null; attach(AttachKind.Document) }
            }
        }
        simMenu?.let { rect ->
            SimMenu(backdrop, rect, sims, sim, onPick = { id ->
                simMenu = null; sim = id; com.tonny.messages.data.Prefs.setSim(address, id)
            }, onDismiss = { simMenu = null })
        }
        selectFor?.let { m -> SelectTextSheet(backdrop, m.body, onDone = { selectFor = null }) }
    }
    androidx.activity.compose.BackHandler(messageMenu != null || attachMenu != null || simMenu != null || selectFor != null) {
        messageMenu = null; attachMenu = null; simMenu = null; selectFor = null
    }
}

/** A new time label after an hour's gap or on a new day. */
private fun startsNewSection(prev: Long, next: Long): Boolean {
    if (next - prev > 60 * 60_000L) return true
    val a = Calendar.getInstance().apply { timeInMillis = prev }
    val b = Calendar.getInstance().apply { timeInMillis = next }
    return a.get(Calendar.DAY_OF_YEAR) != b.get(Calendar.DAY_OF_YEAR) || a.get(Calendar.YEAR) != b.get(Calendar.YEAR)
}

/** "Today 14:05", "Yesterday 09:12", "Saturday 18:30", "Sat, 3 Oct 18:30", "3 Oct 2025 18:30". */
@Composable
private fun TimeHeader(millis: Long) {
    val context = LocalContext.current
    val label = remember(millis) {
        val time = android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(millis))
        val now = Calendar.getInstance()
        val then = Calendar.getInstance().apply { timeInMillis = millis }
        val sameYear = now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
        val days = ((startOfDay(now) - startOfDay(then)) / 86_400_000L).toInt()
        val fmt = { p: String -> java.text.SimpleDateFormat(p, java.util.Locale.getDefault()).format(millis) }
        when {
            days == 0 -> "Today $time"
            days == 1 -> "Yesterday $time"
            days in 2..6 -> "${fmt("EEEE")} $time"
            sameYear -> "${fmt("EEE, d MMM")} $time"
            else -> "${fmt("d MMM yyyy")} $time"
        }
    }
    Text(
        label, style = IosType.caption, color = Ios.colors.secondaryLabel, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp),
    )
}

private fun startOfDay(c: Calendar): Long = (c.clone() as Calendar).apply {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

/** Glass sheet showing the message so you can press-and-drag to select just part of it, then Copy. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.SelectTextSheet(backdrop: Backdrop, text: String, onDone: () -> Unit) {
    val ios = Ios.colors
    Box(
        Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.3f))
            .clickable(remember { MutableInteractionSource() }, null, onClick = onDone),
    )
    Box(Modifier.align(Alignment.Center).padding(horizontal = 20.dp)) {
        Column(
            Modifier.fillMaxWidth()
                .liquidGlass(backdrop, com.kyant.shapes.RoundedRectangle(26.dp), ios.background.copy(alpha = 0.62f), blur = 16.dp, refractionHeight = 14.dp, refractionAmount = 24.dp)
                .clickable(remember { MutableInteractionSource() }, null) {}
                .padding(20.dp),
        ) {
            Text("Select Text", style = IosType.headline, color = ios.label)
            Spacer(Modifier.height(4.dp))
            Text("Press and hold a word, then drag the handles.", style = IosType.caption, color = ios.secondaryLabel)
            Spacer(Modifier.height(14.dp))
            Box(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(text, style = IosType.body, color = ios.label)
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "Done", style = IosType.headline, color = ios.blue, textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth().clickable(remember { MutableInteractionSource() }, null, onClick = onDone).padding(vertical = 4.dp),
            )
        }
    }
}

/** An active SIM the user can send from. */
data class SimOption(val subId: Int, val label: String)

fun activeSims(context: android.content.Context): List<SimOption> = runCatching {
    context.getSystemService(android.telephony.SubscriptionManager::class.java)
        .activeSubscriptionInfoList.orEmpty()
        .sortedBy { it.simSlotIndex }
        .map { SimOption(it.subscriptionId, "SIM ${it.simSlotIndex + 1} · ${it.displayName}") }
}.getOrDefault(emptyList())

/** Glass list of SIMs opened from the composer's SIM chip. */
@Composable
fun androidx.compose.foundation.layout.BoxScope.SimMenu(
    backdrop: Backdrop, anchor: Rect, sims: List<SimOption>, selected: Int?, onPick: (Int) -> Unit, onDismiss: () -> Unit,
) {
    GlassMenu(backdrop, anchor, onDismiss = onDismiss, width = 240.dp, alignEnd = false) {
        sims.forEach { s ->
            GlassMenuItem(s.label, if (s.subId == selected) Icons.Rounded.Check else Icons.Rounded.SimCard) { onPick(s.subId) }
        }
    }
}



@Composable
private fun TopBar(
    backdrop: Backdrop, title: String, photoUri: String?, onBack: () -> Unit,
    searching: Boolean, searchQuery: String, onSearchToggle: (Boolean) -> Unit, onSearchChange: (String) -> Unit,
) {
    val ios = Ios.colors
    Column(
        Modifier.fillMaxWidth()
            .liquidGlass(backdrop, RoundedCornerShape(0.dp), ios.background.copy(alpha = 0.55f), blur = 14.dp, refractionHeight = 0.dp, refractionAmount = 0.dp, shadow = false)
            .statusBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBackIos, "Back", tint = ios.blue,
                modifier = Modifier.size(40.dp).clickable(remember { MutableInteractionSource() }, null, onClick = onBack).padding(start = 8.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            )
            Spacer(Modifier.width(2.dp))
            Avatar(title, photoUri, size = 34.dp)
            Spacer(Modifier.width(10.dp))
            Text(title, style = IosType.headline, color = ios.label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Icon(
                if (searching) Icons.Rounded.Close else Icons.Rounded.Search,
                if (searching) "Close search" else "Search in conversation", tint = ios.blue,
                modifier = Modifier.size(40.dp).clickable(remember { MutableInteractionSource() }, null) { onSearchToggle(!searching) }.padding(9.dp),
            )
        }
        androidx.compose.animation.AnimatedVisibility(searching) {
            val focus = remember { androidx.compose.ui.focus.FocusRequester() }
            androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 10.dp).height(38.dp).clip(Capsule()).background(ios.fill).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null, tint = ios.secondaryLabel, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (searchQuery.isEmpty()) Text("Search this conversation", style = IosType.subhead, color = ios.secondaryLabel)
                    BasicTextField(searchQuery, onSearchChange, singleLine = true, textStyle = IosType.body.copy(color = ios.label), cursorBrush = SolidColor(ios.blue), modifier = Modifier.fillMaxWidth().focusRequester(focus))
                }
            }
        }
    }
}

/**
 * One message. Long-press any bubble, or tap a failed one, for Resend / Copy / Delete.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bubble(m: Message, grouped: Boolean, onOpenMenu: (Rect) -> Unit) {
    val view = LocalView.current
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val open = { onOpenMenu(bounds) }
    val ios = Ios.colors
    val bubbleColor = if (m.incoming) ios.fill else if (m.failed) ios.red else ios.blue
    val textColor = if (m.incoming) ios.label else Color.White
    val shape = RoundedCornerShape(
        topStart = if (!m.incoming || !grouped) 20.dp else 7.dp,
        topEnd = if (m.incoming || !grouped) 20.dp else 7.dp,
        bottomStart = 20.dp,
        bottomEnd = 20.dp,
    )
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = if (grouped) 1.dp else 3.dp),
        horizontalArrangement = if (m.incoming) Arrangement.Start else Arrangement.End,
    ) {
        Column(
            Modifier.onGloballyPositioned { bounds = it.boundsInWindow() },
            horizontalAlignment = if (m.incoming) Alignment.Start else Alignment.End,
        ) {
            if (m.attachments.isNotEmpty()) {
                // Each attachment handles its own tap (open/play), so it reports long-press itself.
                AttachmentList(m.attachments, outgoing = !m.incoming, onLongPress = { view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); open() })
                if (m.body.isNotBlank()) Spacer(Modifier.height(3.dp))
            }
            if (m.body.isNotBlank()) {
                Box(
                    Modifier
                        .widthIn(max = 300.dp)
                        .clip(shape)
                        .background(bubbleColor)
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { if (m.failed) open() },
                            onLongClick = { view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); open() },
                        )
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(m.body, style = IosType.body, color = textColor)
                }
            }
            if (m.pending || m.failed) {
                Text(
                    if (m.failed) "Not Delivered · Tap for options" else "Sending…",
                    style = IosType.caption,
                    color = if (m.failed) ios.red else ios.secondaryLabel,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
fun Composer(
    backdrop: Backdrop,
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    sims: List<SimOption> = emptyList(),
    selectedSim: Int? = null,
    onSelectSim: (Int) -> Unit = {},
    onSimMenu: ((Rect) -> Unit)? = null,
    attachment: OutgoingAttachment? = null,
    onRemoveAttachment: () -> Unit = {},
    onAttachMenu: ((Rect) -> Unit)? = null,
    recording: (@Composable () -> Unit)? = null,
) {
    val ios = Ios.colors
    var text by remember { mutableStateOf("") }
    val canSend = enabled && (text.isNotBlank() || attachment != null)
    Column(modifier.fillMaxWidth().imePadding().navigationBarsPadding()) {
    if (attachment != null) PendingAttachment(attachment, onRemoveAttachment)
    if (recording != null) { recording(); return@Column }
    if (sims.size > 1) {
        // Which SIM this conversation sends from; tap to pick another.
        val current = sims.firstOrNull { it.subId == selectedSim } ?: sims.first()
        var chipBounds by remember { mutableStateOf(Rect.Zero) }
        Row(
            Modifier
                .padding(start = 18.dp, top = 4.dp)
                .onGloballyPositioned { chipBounds = it.boundsInWindow() }
                .clip(Capsule())
                .background(ios.fill)
                .clickable { if (onSimMenu != null) onSimMenu(chipBounds) else onSelectSim(sims[(sims.indexOf(current) + 1) % sims.size].subId) }
                .padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.SimCard, null, tint = ios.blue, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(current.label, style = IosType.caption, color = ios.blue)
            Icon(Icons.Rounded.UnfoldMore, null, tint = ios.blue, modifier = Modifier.size(14.dp))
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        if (onAttachMenu != null) { AttachButton(backdrop, onAttachMenu); Spacer(Modifier.width(8.dp)) }
        Box(
            Modifier.weight(1f).heightIn(min = 44.dp)
                .liquidGlass(backdrop, Capsule(), ios.background.copy(alpha = 0.6f), blur = 10.dp, refractionHeight = 8.dp, refractionAmount = 12.dp, shadow = false)
                .padding(horizontal = 16.dp, vertical = 11.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (text.isEmpty()) Text("Text Message", style = IosType.body, color = ios.secondaryLabel)
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                textStyle = IosType.body.copy(color = ios.label),
                cursorBrush = SolidColor(ios.blue),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Default),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.width(8.dp))
        AnimatedVisibility(canSend, enter = fadeIn() + scaleIn(iosSpring(true), 0.5f), exit = fadeOut() + scaleOut(targetScale = 0.5f)) {
            Box(
                Modifier.size(44.dp).clip(Capsule()).background(ios.blue)
                    .clickable(remember { MutableInteractionSource() }, null) {
                        val t = text.trim()
                        if (t.isNotEmpty() || attachment != null) { onSend(t); text = "" }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.ArrowUpward, "Send", tint = Color.White, modifier = Modifier.size(24.dp))
            }
        }
    }
    }
}

enum class AttachKind { Photo, Camera, Voice, Document }

/** The + button: photo, camera, voice note or document (menu shown by the chat screen). */
@Composable
private fun AttachButton(backdrop: Backdrop, onOpen: (Rect) -> Unit) {
    val ios = Ios.colors
    var bounds by remember { mutableStateOf(Rect.Zero) }
    Box(
        Modifier.size(44.dp)
            .onGloballyPositioned { bounds = it.boundsInWindow() }
            .liquidGlass(backdrop, Capsule(), ios.background.copy(alpha = 0.6f), blur = 10.dp, refractionHeight = 8.dp, refractionAmount = 12.dp, shadow = false)
            .clickable(remember { MutableInteractionSource() }, null) { onOpen(bounds) },
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Rounded.Add, "Attach", tint = ios.blue, modifier = Modifier.size(26.dp)) }
}
