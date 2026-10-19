package com.tonny.messages.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import com.tonny.messages.ui.theme.Ios
import com.tonny.messages.ui.theme.IosType

@Composable
fun Onboarding(onMakeDefault: () -> Unit) {
    val ios = Ios.colors
    Box(Modifier.fillMaxSize().background(ios.background)) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(104.dp).background(ios.green, RoundedRectangle(28.dp)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Rounded.Chat, null, tint = Color.White, modifier = Modifier.size(52.dp)) }
            Spacer(Modifier.height(26.dp))
            Text("Welcome to Messages", style = IosType.largeTitle, color = ios.label, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Text(
                "Make this your default messaging app to send and receive texts.",
                style = IosType.body, color = ios.secondaryLabel, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.fillMaxWidth().height(54.dp).background(ios.blue, Capsule())
                    .clickable(androidx.compose.runtime.remember { MutableInteractionSource() }, null, onClick = onMakeDefault),
                contentAlignment = Alignment.Center,
            ) { Text("Set as Default", style = IosType.headline, color = Color.White) }
            Spacer(Modifier.height(16.dp))
        }
    }
}
