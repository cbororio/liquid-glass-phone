package com.tonny.dial.ui.screens

import android.media.AudioManager
import android.media.ToneGenerator
import android.provider.Settings
import android.telephony.PhoneNumberUtils
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.tonny.dial.data.Contact
import com.tonny.dial.data.ContactsRepository
import androidx.compose.foundation.background
import com.tonny.dial.ui.components.AppBackground
import com.tonny.dial.ui.components.GlassButton
import com.tonny.dial.ui.components.Keypad
import com.tonny.dial.ui.components.iosSpring
import com.tonny.dial.ui.theme.Ios
import com.tonny.dial.ui.theme.IosType
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KeypadScreen(
    seed: String?,
    onSeedConsumed: () -> Unit,
    contacts: List<Contact>,
    onCall: (String) -> Unit,
    onAddContact: (String) -> Unit,
    bottomInset: androidx.compose.ui.unit.Dp,
) {
    val ios = Ios.colors
    val view = LocalView.current
    var number by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(seed) {
        if (seed != null) {
            number = seed; onSeedConsumed()
        }
    }
    val tones = rememberDtmfTones()
    val match = remember(number, contacts) { bestMatch(number, contacts) }
    val backdrop = rememberLayerBackdrop()

    Box(Modifier.fillMaxSize()) {
        AppBackground(Modifier.layerBackdrop(backdrop))

        Column(
            Modifier.fillMaxSize().statusBarsPadding().padding(bottom = bottomInset),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))

            NumberDisplay(format(number))
            Box(Modifier.height(30.dp), contentAlignment = Alignment.Center) {
                when {
                    match != null -> Text(match.name, style = IosType.subhead, color = ios.secondaryLabel)
                    number.isNotEmpty() -> Text(
                        "Add Number",
                        style = IosType.body,
                        color = ios.blue,
                        modifier = Modifier.combinedClickable(remember { MutableInteractionSource() }, null) { onAddContact(number) },
                    )
                }
            }
            Spacer(Modifier.height(18.dp))

            Keypad(
                backdrop = backdrop,
                onKey = { number += it; tones(it) },
                onLongKey = { if (it == '0') { number += '+'; true } else false },
            )

            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(25.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.size(78.dp))
                GlassButton(
                    backdrop,
                    onClick = {
                        if (number.isNotBlank()) onCall(number)
                        else view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                    },
                    size = 78.dp,
                    tint = ios.green,
                    contentDescription = "Call",
                ) {
                    Icon(Icons.Rounded.Call, null, tint = Color.White, modifier = Modifier.size(36.dp))
                }
                Box(Modifier.size(78.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.animation.AnimatedVisibility(number.isNotEmpty(), enter = fadeIn() + scaleIn(iosSpring(true), 0.6f), exit = fadeOut() + scaleOut(targetScale = 0.6f)) {
                        Icon(
                            Icons.AutoMirrored.Outlined.Backspace,
                            "Delete",
                            tint = ios.label.copy(alpha = 0.55f),
                            modifier = Modifier
                                .size(56.dp)
                                .combinedClickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { number = number.dropLast(1) },
                                    onLongClick = {
                                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                        number = ""
                                    },
                                )
                                .padding(14.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Each digit drops in with a little spring, like the iPhone number field. */
@Composable
private fun NumberDisplay(text: String) {
    val size = when {
        text.length > 17 -> 26.sp
        text.length > 13 -> 32.sp
        else -> 38.sp
    }
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 24.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        text.forEachIndexed { i, c ->
            androidx.compose.runtime.key(i) { PopChar(c, size) }
        }
    }
}

@Composable
private fun PopChar(c: Char, size: androidx.compose.ui.unit.TextUnit) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(Unit) { anim.animateTo(1f, iosSpring(bouncy = true)) }
    Text(
        c.toString(),
        fontSize = size,
        fontWeight = FontWeight.Normal,
        color = Ios.colors.label,
        modifier = Modifier.graphicsLayer {
            val p = anim.value
            alpha = p.coerceIn(0f, 1f)
            translationY = (1f - p) * 14.dp.toPx()
            scaleX = 0.7f + 0.3f * p; scaleY = scaleX
        },
    )
}

private fun format(number: String): String =
    if (number.any { it == '*' || it == '#' } || number.length < 4) number
    else PhoneNumberUtils.formatNumber(number, Locale.getDefault().country) ?: number

private fun bestMatch(query: String, contacts: List<Contact>): Contact? {
    val digits = query.filter { it.isDigit() }
    if (digits.length < 3) return null
    return contacts.firstOrNull { c ->
        c.numbers.any { ContactsRepository.normalize(it.number).endsWith(digits.takeLast(9)) }
    } ?: contacts.firstOrNull { c -> c.numbers.any { ContactsRepository.normalize(it.number).contains(digits) } }
}

/** Short local DTMF tone when the system "dial pad tones" setting is on. */
@Composable
private fun rememberDtmfTones(): (Char) -> Unit {
    val context = LocalContext.current
    val generator = remember {
        val enabled = Settings.System.getInt(context.contentResolver, Settings.System.DTMF_TONE_WHEN_DIALING, 1) == 1
        if (enabled) runCatching { ToneGenerator(AudioManager.STREAM_DTMF, 70) }.getOrNull() else null
    }
    DisposableEffect(Unit) { onDispose { generator?.release() } }
    return remember(generator) {
        { key: Char ->
            val tone = when (key) {
                in '0'..'9' -> ToneGenerator.TONE_DTMF_0 + (key - '0')
                '*' -> ToneGenerator.TONE_DTMF_S
                '#' -> ToneGenerator.TONE_DTMF_P
                else -> null
            }
            if (tone != null) generator?.startTone(tone, 120)
        }
    }
}
