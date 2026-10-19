package com.tonny.dial.telecom

import android.app.KeyguardManager
import android.os.PowerManager
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import com.tonny.dial.CallActivity
import com.tonny.dial.data.ContactsRepository
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class DialInCallService : InCallService() {

    private val notifier by lazy { CallNotifier(this) }

    private val scope = MainScope()

    override fun onCreate() {
        super.onCreate()
        CallManager.service = this
        CallManager.contactResolver = { number -> ContactsRepository.lookup(this, number) }
        // The notification mirrors live call state, so it can never lag behind an answer or hang-up.
        scope.launch {
            combine(CallManager.calls, CallManager.uiVisible) { calls, visible -> calls.any { it.isRinging } && !visible }
                .collectLatest { mayAlert ->
                    // Give the call screen a moment to come up before falling back to a banner.
                    // On the lock screen there's no waiting: the full-screen intent is what wakes it.
                    if (mayAlert && !isLocked()) delay(700)
                    refreshNotification()
                }
        }
    }

    override fun onDestroy() {
        CallManager.service = null
        scope.cancel()
        notifier.cancel()
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        CallManager.add(call)
        // Locked or screen off: post the full-screen notification first. Android (and MIUI's
        // "show on lock screen" gate) only reliably lets a call screen over the keyguard that way.
        if (isLocked()) refreshNotification()
        // Open the call screen straight away, ringing or not. Some ROMs (MIUI/HyperOS) hide
        // heads-up notifications for new apps, so the notification alone isn't reliable.
        runCatching { startActivity(CallActivity.intent(this)) }
    }

    override fun onCallRemoved(call: Call) {
        CallManager.remove(call)
        if (CallManager.calls.value.isEmpty()) CallManager.clearCache()
    }

    @Deprecated("Still delivered on all supported API levels")
    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        CallManager.updateAudio(audioState)
    }

    private fun refreshNotification() {
        val primary = CallManager.primary(CallManager.calls.value)
        // A call screen "started" behind the keyguard can count as visible without being seen,
        // so keep the full-screen alert while locked.
        if (primary == null || primary.isEnded) notifier.cancel()
        else notifier.show(primary, headsUp = !CallManager.uiVisible.value || isLocked())
    }

    private fun isLocked(): Boolean =
        getSystemService(KeyguardManager::class.java).isKeyguardLocked ||
            !getSystemService(PowerManager::class.java).isInteractive
}
