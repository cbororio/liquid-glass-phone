package com.tonny.dial.telecom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val call = CallManager.primary(CallManager.calls.value) ?: return
        when (intent.action) {
            ACTION_HANGUP -> CallManager.hangup(call.call)
        }
    }

    companion object {
        const val ACTION_HANGUP = "com.tonny.dial.HANGUP"
    }
}
