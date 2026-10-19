package com.tonny.dial.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.tonny.dial.ui.components.liquidGlass
import com.tonny.dial.ui.theme.Ios
import com.tonny.dial.ui.theme.IosType

/** A SIM that can place calls. */
data class SimOption(val handle: PhoneAccountHandle, val slot: Int, val label: String)

/** A call waiting for the user to pick which SIM to use. */
data class SimPrompt(val number: String, val sims: List<SimOption>)

/** Call-capable SIMs, in slot order. Empty without READ_PHONE_STATE. */
@SuppressLint("MissingPermission")
fun callSims(context: Context): List<SimOption> = runCatching {
    val telecom = context.getSystemService(TelecomManager::class.java)
    val subs = context.getSystemService(SubscriptionManager::class.java).activeSubscriptionInfoList.orEmpty()
    val telephony = context.getSystemService(TelephonyManager::class.java)
    telecom.callCapablePhoneAccounts.mapIndexed { i, handle ->
        val subId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) telephony.getSubscriptionId(handle)
        else handle.id.toIntOrNull() ?: SubscriptionManager.INVALID_SUBSCRIPTION_ID
        val info = subs.firstOrNull { it.subscriptionId == subId || it.iccId == handle.id }
        val name = info?.displayName?.toString()?.takeIf { it.isNotBlank() }
            ?: telecom.getPhoneAccount(handle)?.label?.toString().orEmpty()
        SimOption(handle, info?.simSlotIndex ?: i, name)
    }.sortedBy { it.slot }
}.getOrDefault(emptyList())

/** Glass card asking which SIM to call from. */
@Composable
fun BoxScope.SimPicker(backdrop: Backdrop, prompt: SimPrompt, onPick: (SimOption) -> Unit, onDismiss: () -> Unit) {
    val ios = Ios.colors
    Box(
        Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.3f))
            .clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss),
    )
    Column(
        Modifier.align(Alignment.Center).padding(horizontal = 32.dp).fillMaxWidth()
            .liquidGlass(backdrop, com.kyant.shapes.RoundedRectangle(26.dp), ios.background.copy(alpha = 0.7f), blur = 16.dp, refractionHeight = 14.dp, refractionAmount = 24.dp)
            .clickable(remember { MutableInteractionSource() }, null) {}
            .padding(vertical = 18.dp),
    ) {
        Text("Call with", style = IosType.headline, color = ios.label, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(2.dp))
        Text(prompt.number, style = IosType.caption, color = ios.secondaryLabel, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        prompt.sims.forEach { sim ->
            Row(
                Modifier.fillMaxWidth().clickable { onPick(sim) }.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(ios.green), contentAlignment = Alignment.Center) {
                    Text("${sim.slot + 1}", style = IosType.headline, color = Color.White)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("SIM ${sim.slot + 1}", style = IosType.body, color = ios.label)
                    if (sim.label.isNotBlank()) Text(sim.label, style = IosType.caption, color = ios.secondaryLabel)
                }
                Icon(Icons.Rounded.SimCard, null, tint = ios.secondaryLabel, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Cancel", style = IosType.headline, color = ios.blue, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss).padding(vertical = 10.dp),
        )
    }
}
