package com.tonny.dial.telecom

import android.os.Build
import android.util.Log
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.VideoProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Immutable snapshot of a telecom [Call], safe to hand to Compose. */
data class CallUi(
    val call: Call,
    val state: Int,
    val number: String,
    val name: String?,
    val photoUri: String?,
    val connectTimeMillis: Long,
    val canHold: Boolean,
    val isConference: Boolean,
) {
    val title: String get() = name ?: number.ifBlank { "Unknown" }
    val isRinging: Boolean get() = state == Call.STATE_RINGING
    val isActive: Boolean get() = state == Call.STATE_ACTIVE
    val isHolding: Boolean get() = state == Call.STATE_HOLDING
    val isDialing: Boolean
        get() = state == Call.STATE_DIALING || state == Call.STATE_CONNECTING ||
            state == Call.STATE_PULLING_CALL || state == Call.STATE_SELECT_PHONE_ACCOUNT
    /** Telecom is waiting for us to choose a SIM (another app started the call, no default SIM). */
    val needsSim: Boolean get() = state == Call.STATE_SELECT_PHONE_ACCOUNT
    val isEnded: Boolean
        get() = state == Call.STATE_DISCONNECTED || state == Call.STATE_DISCONNECTING
}

data class AudioUi(
    val muted: Boolean = false,
    val route: Int = CallAudioState.ROUTE_EARPIECE,
    val supportedRoutes: Int = CallAudioState.ROUTE_EARPIECE or CallAudioState.ROUTE_SPEAKER,
) {
    val speaker: Boolean get() = route == CallAudioState.ROUTE_SPEAKER
    val bluetooth: Boolean get() = route == CallAudioState.ROUTE_BLUETOOTH
    val hasBluetooth: Boolean get() = supportedRoutes and CallAudioState.ROUTE_BLUETOOTH != 0
}

data class ContactInfo(val name: String?, val photoUri: String?)

@Suppress("DEPRECATION")
fun Call.stateCompat(): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) details.state else state

/**
 * Process-wide bridge between the system's [InCallService] and the UI.
 * Telecom calls are mutable objects with callbacks; this flattens them into StateFlows.
 */
object CallManager {
    private const val TAG = "DialCall"
    private val _calls = MutableStateFlow<List<CallUi>>(emptyList())
    val calls: StateFlow<List<CallUi>> = _calls.asStateFlow()

    private val _uiVisible = MutableStateFlow(false)
    /** True while the full-screen call UI is showing; the banner is redundant then. */
    val uiVisible: StateFlow<Boolean> = _uiVisible.asStateFlow()

    fun setUiVisible(visible: Boolean) {
        _uiVisible.value = visible
    }

    private val _audio = MutableStateFlow(AudioUi())
    val audio: StateFlow<AudioUi> = _audio.asStateFlow()

    internal var service: InCallService? = null
    private val tracked = mutableListOf<Call>()
    private val contactCache = mutableMapOf<String, ContactInfo>()

    var contactResolver: ((String) -> ContactInfo)? = null

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) = publish()
        override fun onDetailsChanged(call: Call, details: Call.Details) = publish()
        override fun onConferenceableCallsChanged(call: Call, calls: MutableList<Call>) = publish()
    }

    internal fun add(call: Call) {
        if (call in tracked) return
        tracked += call
        call.registerCallback(callback)
        publish()
    }

    internal fun remove(call: Call) {
        call.unregisterCallback(callback)
        tracked -= call
        publish()
    }

    internal fun updateAudio(state: CallAudioState) {
        _audio.value = AudioUi(state.isMuted, state.route, state.supportedRouteMask)
    }

    private fun publish() {
        _calls.value = tracked.map { it.toUi() }
    }

    private fun Call.toUi(): CallUi {
        val number = details.handle?.schemeSpecificPart.orEmpty()
        val info = contactCache.getOrPut(number) {
            if (number.isBlank()) ContactInfo(null, null)
            else runCatching { contactResolver?.invoke(number) }.getOrNull() ?: ContactInfo(null, null)
        }
        val caps = details.callCapabilities
        return CallUi(
            call = this,
            state = stateCompat(),
            number = number,
            name = info.name ?: details.callerDisplayName?.takeIf { it.isNotBlank() },
            photoUri = info.photoUri,
            connectTimeMillis = details.connectTimeMillis,
            canHold = caps and Call.Details.CAPABILITY_HOLD != 0,
            isConference = details.hasProperty(Call.Details.PROPERTY_CONFERENCE),
        )
    }

    /** The call the UI should focus on: ringing beats live beats held. */
    fun primary(calls: List<CallUi>): CallUi? =
        calls.firstOrNull { it.isRinging }
            ?: calls.firstOrNull { it.isDialing }
            ?: calls.firstOrNull { it.isActive }
            ?: calls.firstOrNull { it.isHolding }
            ?: calls.firstOrNull()

    fun answer(call: Call) {
        Log.i(TAG, "answer state=${call.stateCompat()}")
        call.answer(VideoProfile.STATE_AUDIO_ONLY)
    }

    fun decline(call: Call) {
        Log.i(TAG, "decline state=${call.stateCompat()}")
        call.reject(false, null)
    }

    /** Declines and has the default SMS app text [message] back to the caller. */
    fun declineWithMessage(call: Call, message: String) {
        Log.i(TAG, "decline with message")
        call.reject(true, message)
    }

    fun hangup(call: Call) {
        if (call.stateCompat() == Call.STATE_RINGING) decline(call) else call.disconnect()
    }

    fun selectSim(call: Call, handle: android.telecom.PhoneAccountHandle) {
        Log.i(TAG, "sim selected")
        call.phoneAccountSelected(handle, false)
    }

    fun toggleHold(call: Call) {
        if (call.stateCompat() == Call.STATE_HOLDING) call.unhold() else call.hold()
    }

    fun setMuted(muted: Boolean) {
        service?.setMuted(muted)
    }

    @Suppress("DEPRECATION")
    fun setRoute(route: Int) {
        service?.setAudioRoute(route)
    }

    /** Cycle earpiece -> speaker -> bluetooth (when available) -> earpiece. */
    fun cycleRoute() {
        val a = _audio.value
        val next = when {
            a.route == CallAudioState.ROUTE_SPEAKER && a.hasBluetooth -> CallAudioState.ROUTE_BLUETOOTH
            a.route == CallAudioState.ROUTE_SPEAKER || a.route == CallAudioState.ROUTE_BLUETOOTH ->
                if (a.supportedRoutes and CallAudioState.ROUTE_WIRED_HEADSET != 0) CallAudioState.ROUTE_WIRED_HEADSET
                else CallAudioState.ROUTE_EARPIECE
            else -> CallAudioState.ROUTE_SPEAKER
        }
        setRoute(next)
    }

    fun dtmf(call: Call, digit: Char) {
        call.playDtmfTone(digit)
        call.stopDtmfTone()
    }

    fun clearCache() = contactCache.clear()
}
