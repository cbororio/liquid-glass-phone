package com.tonny.dial.ui.screens

import android.content.Intent
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.SwapCalls
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.shapes.Capsule
import com.tonny.dial.MainActivity
import com.tonny.dial.data.AnswerStyle
import com.tonny.dial.data.Prefs
import com.tonny.dial.data.Wallpaper
import com.tonny.dial.ui.components.WallpaperView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tonny.dial.telecom.AudioUi
import com.tonny.dial.telecom.CallManager
import com.tonny.dial.telecom.CallUi
import com.tonny.dial.ui.components.GlassButton
import com.tonny.dial.ui.components.Keypad
import com.tonny.dial.ui.components.iosSpring
import com.tonny.dial.ui.components.liquidGlass
import com.tonny.dial.ui.theme.DarkIos
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val Glass = Color.White.copy(alpha = 0.14f)

@Composable
fun InCallScreen(primary: CallUi, others: List<CallUi>, audio: AudioUi, ended: Boolean) {
    var showKeypad by rememberSaveable { mutableStateOf(false) }
    var dtmf by rememberSaveable { mutableStateOf("") }
    val backdrop = rememberLayerBackdrop()
    val keypad = showKeypad && !primary.isRinging && !ended
    val settings by Prefs.settings.collectAsStateWithLifecycle()
    var replies by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Box(Modifier.fillMaxSize().background(Color.Black).layerBackdrop(backdrop)) {
            WallpaperView(settings.callWallpaper, blur = (settings.callBlur * 40).dp)
            if (settings.callWallpaper != Wallpaper.None) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = settings.callDim)))
            }
        }

        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            others.firstOrNull { it.call != primary.call }?.let { OtherCallChip(backdrop, it) }
            Spacer(Modifier.height(if (keypad) 12.dp else 36.dp))
            Header(primary, ended, compact = keypad, dtmf = dtmf)

            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                AnimatedContent(
                    keypad,
                    transitionSpec = {
                        (fadeIn(tween(240)) + scaleIn(iosSpring(), 0.92f)) togetherWith (fadeOut(tween(160)) + scaleOut(targetScale = 0.92f))
                    },
                    label = "middle",
                ) { k ->
                    when {
                        k -> Keypad(
                            backdrop = backdrop,
                            onKey = { dtmf += it; CallManager.dtmf(primary.call, it) },
                            tint = Glass,
                            contentColor = Color.White,
                        )
                        // No avatar: the call screen stays clean, just name and controls.
                        else -> Box(Modifier.fillMaxSize())
                    }
                }
            }

            // Like iPhone: "Message" above the answer controls declines with a quick text.
            if (primary.isRinging && !ended && primary.number.isNotBlank()) {
                MessageButton(backdrop, onClick = { replies = true }, modifier = Modifier.padding(bottom = 22.dp))
            }

            when {
                // Like iPhone: just the slider. Declining is still possible from the notification.
                primary.isRinging && !ended && settings.answerStyle == AnswerStyle.Slider -> SlideToAnswer(
                    backdrop,
                    onAnswer = { CallManager.answer(primary.call) },
                    onDecline = { CallManager.decline(primary.call) },
                    modifier = Modifier.padding(bottom = 56.dp),
                )
                primary.isRinging && !ended -> AnswerButtons(backdrop, primary)
                keypad -> Row(Modifier.fillMaxWidth().padding(bottom = 34.dp), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.weight(1f))
                    EndButton(backdrop, enabled = true) { CallManager.hangup(primary.call) }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            "Hide", color = Color.White, fontSize = 17.sp,
                            modifier = Modifier.clickable(remember { MutableInteractionSource() }, null) { showKeypad = false }.padding(12.dp),
                        )
                    }
                }
                else -> Controls(backdrop, primary, others, audio, enabled = !ended, onKeypad = { showKeypad = true })
            }
        }

        if (replies && primary.isRinging && !ended) {
            QuickReplies(
                backdrop,
                onPick = { text -> replies = false; CallManager.declineWithMessage(primary.call, text) },
                onCustom = {
                    replies = false
                    CallManager.decline(primary.call)
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_SENDTO, android.net.Uri.fromParts("smsto", primary.number, null))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                },
                onDismiss = { replies = false },
            )
        }

        // Another app started this call and there's no default SIM: ask which one, like the keypad does.
        if (primary.needsSim && !ended) {
            val sims = remember(primary.call) { callSims(context) }
            if (sims.isNotEmpty()) {
                SimPicker(
                    backdrop, SimPrompt(primary.title, sims),
                    onPick = { CallManager.selectSim(primary.call, it.handle) },
                    onDismiss = { primary.call.disconnect() },
                )
            }
        }
    }
}

private val QuickReplyTexts = listOf(
    "Sorry, I can't talk right now.",
    "I'll call you right back.",
    "I'll call you later.",
    "Can't talk now. Call me later?",
)

@Composable
private fun MessageButton(backdrop: Backdrop, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        GlassButton(backdrop, onClick, size = 52.dp, tint = Glass, contentDescription = "Reply with message") {
            Icon(Icons.AutoMirrored.Rounded.Message, null, tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text("Message", color = Color.White, fontSize = 13.sp)
    }
}

/** Quick replies sheet: tapping one declines the call and texts it to the caller. */
@Composable
private fun QuickReplies(backdrop: Backdrop, onPick: (String) -> Unit, onCustom: () -> Unit, onDismiss: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .fillMaxWidth()
                .liquidGlass(backdrop, com.kyant.shapes.RoundedRectangle(24.dp), Color(0xFF1C1C1E).copy(alpha = 0.92f), blur = 10.dp)
                .clickable(remember { MutableInteractionSource() }, null) {},
        ) {
            Text(
                "Reply with message", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp,
                modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 6.dp),
            )
            (QuickReplyTexts.map { it to { onPick(it) } } + ("Custom…" to onCustom)).forEach { (label, action) ->
                Text(
                    label, color = Color.White, fontSize = 17.sp,
                    modifier = Modifier.fillMaxWidth().clickable(onClick = action).padding(horizontal = 20.dp, vertical = 14.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}


// ------------------------------------------------------------------ header

@Composable
private fun Header(call: CallUi, ended: Boolean, compact: Boolean, dtmf: String) {
    // Long names step down so they still fit on two lines without truncating.
    val fullSize = when (call.title.length) {
        in 0..12 -> 48f
        in 13..18 -> 40f
        in 19..26 -> 33f
        else -> 27f
    }
    val nameSize by animateFloatAsState(if (compact) 26f else fullSize, iosSpring(), label = "name")
    if (compact && dtmf.isNotEmpty()) {
        Text(dtmf.takeLast(16), color = Color.White, fontSize = 34.sp, textAlign = TextAlign.Center, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Text(call.title, color = Color.White.copy(0.6f), fontSize = 15.sp)
        return
    }
    Status(call, ended)
    Spacer(Modifier.height(2.dp))
    Text(
        call.title,
        color = Color.White,
        fontSize = nameSize.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = (-0.4).sp,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        lineHeight = (nameSize * 1.15f).sp,
    )
}

@Composable
private fun Status(call: CallUi, ended: Boolean) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(call.isActive) {
        while (call.isActive) {
            now = System.currentTimeMillis(); delay(500)
        }
    }
    val text = when {
        ended -> "Call Ended"
        call.isRinging -> if (call.name != null) "mobile" else "incoming call"
        call.isDialing -> "calling${if (call.name != null) " mobile" else ""}…"
        call.isHolding -> "on hold"
        call.isActive && call.connectTimeMillis > 0 -> elapsed(now - call.connectTimeMillis)
        else -> ""
    }
    AnimatedContent(
        text,
        transitionSpec = {
            if (initialState.contains(':') && targetState.contains(':')) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
            else (slideInVertically(iosSpring()) { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut())
        },
        label = "status",
    ) { t ->
        Text(t, color = Color.White.copy(alpha = 0.7f), fontSize = 17.sp, letterSpacing = if (t.contains(':')) 0.8.sp else 0.sp)
    }
}

private fun elapsed(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
}

@Composable
private fun OtherCallChip(backdrop: Backdrop, other: CallUi) {
    Box(
        Modifier
            .padding(top = 8.dp)
            .liquidGlass(backdrop, Capsule(), Glass)
            .clickable { CallManager.toggleHold(other.call) }
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.SwapCalls, null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "${other.title} — ${if (other.isHolding) "on hold · tap to swap" else "active"}",
                color = Color.White, fontSize = 15.sp,
            )
        }
    }
}

// ------------------------------------------------------------------ controls

@Composable
private fun Controls(
    backdrop: Backdrop,
    call: CallUi,
    others: List<CallUi>,
    audio: AudioUi,
    enabled: Boolean,
    onKeypad: () -> Unit,
) {
    val context = LocalContext.current
    val routeIcon = if (audio.bluetooth) Icons.Rounded.Bluetooth else Icons.AutoMirrored.Rounded.VolumeUp
    val routeLabel = if (audio.bluetooth) "Bluetooth" else "Speaker"
    val alpha by animateFloatAsState(if (enabled) 1f else 0.35f, label = "dim")

    Column(
        Modifier.fillMaxWidth().padding(bottom = 34.dp).graphicsLayer { this.alpha = alpha },
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            CallToggle(backdrop, routeIcon, routeLabel, audio.speaker || audio.bluetooth, enabled) { CallManager.cycleRoute() }
            CallToggle(
                backdrop, Icons.Rounded.Pause, if (call.isHolding) "Resume" else "Hold", call.isHolding,
                enabled && (call.canHold || call.isHolding) && others.size <= 1,
            ) { CallManager.toggleHold(call.call) }
            CallToggle(backdrop, Icons.Rounded.MicOff, "Mute", audio.muted, enabled) { CallManager.setMuted(!audio.muted) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Top) {
            CallToggle(backdrop, Icons.Rounded.Add, "Add", false, enabled) {
                // Telecom holds the current call automatically when a second one is placed.
                context.startActivity(
                    Intent(context, MainActivity::class.java).setAction(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(84.dp)) {
                EndButton(backdrop, enabled) { CallManager.hangup(call.call) }
                Spacer(Modifier.height(6.dp))
                Text("End", color = Color.White, fontSize = 13.sp)
            }
            CallToggle(backdrop, Icons.Rounded.Dialpad, "Keypad", false, enabled, onKeypad)
        }
    }
}

@Composable
private fun CallToggle(backdrop: Backdrop, icon: ImageVector, label: String, on: Boolean, enabled: Boolean, onClick: () -> Unit) {
    // "On" turns the glass into a solid white disc, like iOS.
    val fill by animateFloatAsState(if (on) 1f else 0f, tween(180), label = "on")
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(84.dp)) {
        GlassButton(backdrop, onClick, size = 78.dp, tint = Glass, enabled = enabled, contentDescription = label) {
            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = fill), CircleShape))
            Icon(icon, null, tint = lerp(Color.White, Color.Black, fill), modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color.White, fontSize = 13.sp, maxLines = 1)
    }
}

@Composable
private fun EndButton(backdrop: Backdrop, enabled: Boolean, onClick: () -> Unit) {
    GlassButton(backdrop, onClick, size = 78.dp, tint = DarkIos.red, enabled = enabled, contentDescription = "End call") {
        Icon(Icons.Rounded.CallEnd, null, tint = Color.White, modifier = Modifier.size(36.dp))
    }
}

// ------------------------------------------------------------------ incoming

@Composable
private fun AnswerButtons(backdrop: Backdrop, call: CallUi) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 48.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            GlassButton(backdrop, { CallManager.decline(call.call) }, size = 78.dp, tint = DarkIos.red, contentDescription = "Decline") {
                Icon(Icons.Rounded.CallEnd, null, tint = Color.White, modifier = Modifier.size(36.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text("Decline", color = Color.White, fontSize = 15.sp)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            GlassButton(backdrop, { CallManager.answer(call.call) }, size = 78.dp, tint = DarkIos.green, contentDescription = "Accept") {
                Icon(Icons.Rounded.Call, null, tint = Color.White, modifier = Modifier.size(36.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text("Accept", color = Color.White, fontSize = 15.sp)
        }
    }
}

/**
 * "slide to answer": a glass track with a shimmering label and a green knob. Commits past 70%
 * of the track, or on a quick flick to the right.
 */
@Composable
private fun SlideToAnswer(backdrop: Backdrop, onAnswer: () -> Unit, onDecline: () -> Unit, modifier: Modifier = Modifier) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    var done by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(88.dp)
            .liquidGlass(backdrop, Capsule(), Glass, blur = 6.dp)
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction("Answer") { onAnswer(); true },
                    CustomAccessibilityAction("Decline") { onDecline(); true },
                )
            },
    ) {
        val knob = 76.dp
        val inset = 6.dp
        val density = LocalDensity.current
        val max = with(density) { (maxWidth - knob - inset * 2).toPx() }
        val widthPx = with(density) { maxWidth.toPx() }
        val progress = (offset.value / max).coerceIn(0f, 1f)

        val t = rememberInfiniteTransition(label = "shimmer")
        val sweep by t.animateFloat(-0.2f, 1.2f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "sweep")
        val dim = Color.White.copy(0.4f)
        Text(
            "slide to answer",
            style = TextStyle(
                fontSize = 23.sp,
                brush = Brush.horizontalGradient(
                    listOf(dim, dim, Color.White, dim, dim),
                    startX = (sweep - 0.3f) * widthPx,
                    endX = (sweep + 0.3f) * widthPx,
                ),
            ),
            modifier = Modifier.align(Alignment.Center).padding(start = knob).graphicsLayer { alpha = 1f - progress * 1.6f },
        )

        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset((offset.value + inset.toPx()).roundToInt(), 0) }
                .size(knob)
                .background(DarkIos.green, CircleShape)
                .draggable(
                    orientation = Orientation.Horizontal,
                    enabled = !done,
                    startDragImmediately = true,
                    state = rememberDraggableState { d -> scope.launch { offset.snapTo((offset.value + d).coerceIn(0f, max)) } },
                    onDragStopped = { velocity ->
                        if (offset.value > max * 0.7f || (velocity > 1500f && offset.value > max * 0.3f)) {
                            done = true
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            onAnswer()
                            offset.animateTo(max, iosSpring())
                        } else {
                            offset.animateTo(0f, iosSpring(bouncy = true))
                        }
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Call, "Slide to answer", tint = Color.White, modifier = Modifier.size(36.dp))
        }
    }
}
