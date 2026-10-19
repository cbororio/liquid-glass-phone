package com.tonny.messages.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.tonny.messages.data.Attachment
import com.tonny.messages.mms.OutgoingAttachment
import com.tonny.messages.ui.theme.Ios
import com.tonny.messages.ui.theme.IosType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Pictures, voice notes and files in a message. Long-press any of them for the message menu. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AttachmentList(items: List<Attachment>, outgoing: Boolean, onLongPress: () -> Unit = {}) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = if (outgoing) Alignment.End else Alignment.Start) {
        items.forEach { a ->
            when {
                a.isImage -> PictureView(a, onLongPress)
                a.isAudio -> VoiceNoteView(a.uri, outgoing, onLongPress)
                else -> FileChip(a, outgoing, onLongPress)
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun PictureView(a: Attachment, onLongPress: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bitmap by produceState<ImageBitmap?>(null, a.uri) { value = withContext(Dispatchers.IO) { decodeScaled(context, a.uri, 900) } }
    Box(
        Modifier
            .widthIn(max = 260.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Ios.colors.fill)
            .combinedClickable(onLongClick = onLongPress) { scope.launch { openAttachment(context, a) } },
    ) {
        val bmp = bitmap
        if (bmp != null) Image(bmp, "Picture", contentScale = ContentScale.FillWidth, modifier = Modifier.widthIn(max = 260.dp))
        else Box(Modifier.size(200.dp, 140.dp))
    }
}

/** Play/pause bar for a voice note. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun VoiceNoteView(uri: String, outgoing: Boolean, onLongPress: () -> Unit) {
    val context = LocalContext.current
    val ios = Ios.colors
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }
    var positionMs by remember { mutableIntStateOf(0) }
    var durationMs by remember { mutableIntStateOf(0) }
    DisposableEffect(uri) { onDispose { player?.release(); player = null } }
    LaunchedEffect(playing) {
        while (playing) { positionMs = player?.currentPosition ?: 0; delay(200) }
    }
    val fg = if (outgoing) Color.White else ios.label
    Row(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (outgoing) ios.blue else ios.fill)
            .combinedClickable(onLongClick = onLongPress) {
                val p = player ?: runCatching {
                    MediaPlayer().apply {
                        setDataSource(context, Uri.parse(uri)); prepare()
                        setOnCompletionListener { playing = false; positionMs = 0 }
                    }
                }.getOrNull()?.also { player = it; durationMs = it.duration }
                if (p == null) { Toast.makeText(context, "Can't play this voice note", Toast.LENGTH_SHORT).show(); return@combinedClickable }
                if (playing) p.pause() else p.start()
                playing = !playing
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pause" else "Play", tint = fg, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(8.dp))
        Text("Voice note", style = IosType.body, color = fg)
        Spacer(Modifier.width(10.dp))
        Text(
            if (durationMs > 0) "${clock(positionMs)} / ${clock(durationMs)}" else "",
            style = IosType.caption, color = fg.copy(alpha = 0.8f),
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun FileChip(a: Attachment, outgoing: Boolean, onLongPress: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ios = Ios.colors
    val fg = if (outgoing) Color.White else ios.label
    Row(
        Modifier
            .widthIn(max = 260.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (outgoing) ios.blue else ios.fill)
            .combinedClickable(onLongClick = onLongPress) { scope.launch { openAttachment(context, a) } }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Description, null, tint = fg, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(a.name ?: "Attachment", style = IosType.body, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Tap to open", style = IosType.caption, color = fg.copy(alpha = 0.75f))
        }
    }
}

/** Copies the attachment somewhere we can share, then opens it in a viewer app. */
suspend fun openAttachment(context: Context, a: Attachment) {
    val shared = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, "share").apply { mkdirs() }
            val file = File(dir, (a.name ?: "attachment").replace(Regex("[^\\w.\\-]"), "_"))
            context.contentResolver.openInputStream(Uri.parse(a.uri))?.use { input -> file.outputStream().use { input.copyTo(it) } }
            FileProvider.getUriForFile(context, "com.tonny.messages.mmsfiles", file)
        }.getOrNull()
    } ?: return
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(shared, a.type).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.onFailure { Toast.makeText(context, "No app can open this file", Toast.LENGTH_SHORT).show() }
}

fun decodeScaled(context: Context, uri: String, maxSide: Int): ImageBitmap? = runCatching {
    val u = Uri.parse(uri)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(u)?.use { stream -> BitmapFactory.decodeStream(stream, null, bounds); Unit }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    val bitmap: android.graphics.Bitmap? = context.contentResolver.openInputStream(u)?.use { stream -> BitmapFactory.decodeStream(stream, null, opts) }
    bitmap?.asImageBitmap()
}.getOrNull()

private fun clock(ms: Int): String { val s = ms / 1000; return "%d:%02d".format(s / 60, s % 60) }

// ---------------------------------------------------------------- composing

/** The photo / voice note / file waiting to be sent, with a remove button. */
@Composable
fun PendingAttachment(a: OutgoingAttachment, onRemove: () -> Unit) {
    val ios = Ios.colors
    val thumb = remember(a) {
        if (a.isImage) runCatching { BitmapFactory.decodeByteArray(a.bytes, 0, a.bytes.size)?.asImageBitmap() }.getOrNull() else null
    }
    Row(
        Modifier.padding(start = 14.dp, end = 14.dp, top = 6.dp).clip(RoundedCornerShape(14.dp)).background(ios.fill).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (thumb != null) Image(thumb, null, contentScale = ContentScale.Crop, modifier = Modifier.size(54.dp).clip(RoundedCornerShape(10.dp)))
        else Icon(if (a.isAudio) Icons.Rounded.Mic else Icons.Rounded.Description, null, tint = ios.blue, modifier = Modifier.size(30.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.widthIn(max = 200.dp)) {
            Text(if (a.isImage) "Photo" else if (a.isAudio) "Voice note" else a.name, style = IosType.subhead, color = ios.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${(a.bytes.size + 1023) / 1024} KB", style = IosType.caption, color = ios.secondaryLabel)
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            Icons.Rounded.Close, "Remove", tint = ios.secondaryLabel,
            modifier = Modifier.size(28.dp).clip(CircleShape).clickable(onClick = onRemove).padding(4.dp),
        )
    }
}

/** Records an AMR voice note, the audio format phones use for MMS (~1.6 KB per second). */
class VoiceRecorder(context: Context, maxBytes: Long, onLimit: () -> Unit) {
    val file = File(File(context.cacheDir, "mms").apply { mkdirs() }, "voice_${System.currentTimeMillis()}.amr")
    private val recorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()).apply {
        setAudioSource(MediaRecorder.AudioSource.MIC)
        setOutputFormat(MediaRecorder.OutputFormat.AMR_NB)
        setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
        setMaxFileSize(maxBytes)
        setOutputFile(file.absolutePath)
        setOnInfoListener { _, what, _ -> if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) onLimit() }
        prepare()
        start()
    }
    val startedAt = System.currentTimeMillis()

    /** Stops and returns the recording, or null if it was too short to keep. */
    fun finish(): File? {
        val ok = runCatching { recorder.stop() }.isSuccess
        recorder.release()
        return file.takeIf { ok && it.length() > 0 && System.currentTimeMillis() - startedAt > 600 }
    }

    fun cancel() {
        runCatching { recorder.stop() }
        recorder.release()
        file.delete()
    }
}

/** Shown in place of the text box while recording. */
@Composable
fun RecordingBar(startedAt: Long, onCancel: () -> Unit, onStop: () -> Unit, modifier: Modifier = Modifier) {
    val ios = Ios.colors
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(250) } }
    Row(
        modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp).clip(RoundedCornerShape(24.dp)).background(ios.fill).padding(horizontal = 6.dp).height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.Delete, "Cancel recording", tint = ios.red,
            modifier = Modifier.size(40.dp).clip(CircleShape).clickable(remember { MutableInteractionSource() }, null, onClick = onCancel).padding(8.dp),
        )
        Box(Modifier.size(10.dp).clip(CircleShape).background(ios.red))
        Spacer(Modifier.width(8.dp))
        Text("Recording  ${clock((now - startedAt).toInt())}", style = IosType.body, color = ios.label, modifier = Modifier.weight(1f))
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(ios.blue).clickable(remember { MutableInteractionSource() }, null, onClick = onStop),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Stop, "Stop recording", tint = Color.White, modifier = Modifier.size(22.dp)) }
    }
}
