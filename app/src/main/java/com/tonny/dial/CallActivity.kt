package com.tonny.dial

import android.annotation.SuppressLint
import android.content.Context
import android.telecom.TelecomManager
import android.view.KeyEvent
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.PowerManager
import android.telecom.Call
import android.telecom.CallAudioState
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tonny.dial.data.Prefs
import com.tonny.dial.telecom.CallManager
import com.tonny.dial.telecom.CallUi
import com.tonny.dial.ui.screens.InCallScreen
import com.tonny.dial.ui.theme.DialTheme
import kotlinx.coroutines.delay

class CallActivity : ComponentActivity() {

    private var proximity: PowerManager.WakeLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // Manifest flags cover stock Android; some ROMs only honour the runtime calls.
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        Prefs.init(this)
        handleIntent(intent)

        setContent {
            DialTheme(dark = true) {
                val calls by CallManager.calls.collectAsStateWithLifecycle()
                val audio by CallManager.audio.collectAsStateWithLifecycle()
                val live = calls.filterNot { it.state == Call.STATE_DISCONNECTED }
                val primary = CallManager.primary(live)

                // Keep the last call on screen briefly after it ends instead of vanishing.
                var last by remember { mutableStateOf<CallUi?>(null) }
                SideEffect { if (primary != null) last = primary }
                val shown = primary ?: last ?: calls.firstOrNull()
                val ended = primary == null

                LaunchedEffect(ended) {
                    if (ended) {
                        delay(if (shown == null) 0 else 1100)
                        finishAndRemoveTask()
                    }
                }
                LaunchedEffect(primary?.isActive, audio.route) {
                    setProximity(primary?.isActive == true && audio.route == CallAudioState.ROUTE_EARPIECE)
                }

                if (shown != null) InCallScreen(shown, live, audio, ended)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        CallManager.setUiVisible(true)
    }

    override fun onStop() {
        CallManager.setUiVisible(false)
        super.onStop()
    }

    /** Like iPhone's side buttons: a volume press silences a ringing call without declining it. */
    @SuppressLint("MissingPermission")
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val ringing = CallManager.calls.value.any { it.isRinging }
        if (ringing && (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            runCatching { getSystemService(TelecomManager::class.java).silenceRinger() }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == ACTION_ANSWER) {
            CallManager.calls.value.firstOrNull { it.isRinging }?.let { CallManager.answer(it.call) }
        }
    }

    /** Blank the screen when the phone is held to the ear. */
    private fun setProximity(on: Boolean) {
        val pm = getSystemService(PowerManager::class.java)
        if (on) {
            if (proximity == null && pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
                proximity = pm.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "dial:proximity")
                    .also { it.acquire(4 * 60 * 60 * 1000L) }
            }
        } else {
            proximity?.let { if (it.isHeld) it.release() }
            proximity = null
        }
    }

    override fun onDestroy() {
        setProximity(false)
        super.onDestroy()
    }

    companion object {
        const val ACTION_ANSWER = "com.tonny.dial.action.ANSWER"

        fun intent(context: Context): Intent =
            Intent(context, CallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
