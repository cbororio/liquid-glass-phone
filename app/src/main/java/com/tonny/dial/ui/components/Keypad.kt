package com.tonny.dial.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.tonny.dial.ui.theme.Ios

private val Keys = listOf(
    listOf('1' to "", '2' to "ABC", '3' to "DEF"),
    listOf('4' to "GHI", '5' to "JKL", '6' to "MNO"),
    listOf('7' to "PQRS", '8' to "TUV", '9' to "WXYZ"),
    listOf('*' to "", '0' to "+", '#' to ""),
)

/** iPhone keypad: 3x4 glass discs. */
@Composable
fun Keypad(
    backdrop: Backdrop,
    onKey: (Char) -> Unit,
    modifier: Modifier = Modifier,
    onLongKey: (Char) -> Boolean = { false },
    keySize: Dp = 78.dp,
    gap: Dp = 18.dp,
    tint: Color = Ios.colors.controlTint.copy(alpha = 0.8f),
    flashColor: Color = if (Ios.colors.dark) Color.White else Color.Black,
    contentColor: Color = Ios.colors.label,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(gap * 0.75f), horizontalAlignment = Alignment.CenterHorizontally) {
        Keys.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(gap * 1.4f)) {
                row.forEach { (digit, letters) ->
                    Key(backdrop, digit, letters, keySize, tint, flashColor, contentColor, { onKey(digit) }, { onLongKey(digit) })
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Key(
    backdrop: Backdrop,
    digit: Char,
    letters: String,
    size: Dp,
    tint: Color,
    flashColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    onLongClick: () -> Boolean,
) {
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // iOS keys flash bright on touch and fade back slowly.
    val flash by animateFloatAsState(if (pressed) 1f else 0f, tween(if (pressed) 40 else 420), label = "flash")
    val scale by animateFloatAsState(if (pressed) 1.08f else 1f, iosSpring(bouncy = true), label = "scale")

    Box(
        Modifier
            .size(size)
            .liquidGlass(backdrop, CircleShape, tint, blur = 3.dp, refractionHeight = 8.dp, refractionAmount = 16.dp, scale = { scale }, shadow = false)
            .background(flashColor.copy(alpha = 0.22f * flash), CircleShape)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onClick()
                },
                onLongClick = { if (onLongClick()) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) },
            )
            .semantics { contentDescription = digit.toString() },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                digit.toString(),
                color = contentColor,
                fontSize = if (digit == '*') 44.sp else 36.sp,
                fontWeight = FontWeight.Normal,
                modifier = if (digit == '*') Modifier.height(40.dp) else Modifier,
            )
            if (letters.isNotEmpty()) {
                Text(letters, color = contentColor, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            } else if (digit in "123456789") {
                Spacer(Modifier.height(13.dp))
            }
        }
    }
}
