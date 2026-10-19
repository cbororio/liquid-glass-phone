package com.tonny.messages.data

import android.app.Application
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tonny.messages.sms.MessagesEvents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MessagesViewModel(app: Application) : AndroidViewModel(app) {

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = refresh()
    }
    private var observing = false

    init {
        // Show the list saved last time straight away; the real query replaces it moments later.
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) { ConversationSnapshot.read(getApplication()) }
            if (_conversations.value.isEmpty()) _conversations.value = saved
        }
        viewModelScope.launch {
            MessagesEvents.version.collect { refresh() }
        }
        // First run of the promo filter: sort betting blasts already in the inbox.
        if (!Prefs.autoSpamSeeded) viewModelScope.launch(Dispatchers.IO) {
            runCatching { Prefs.seedAutoSpam(SmsRepository.promoVerdicts(getApplication())) }
        }
    }

    private var loading: Job? = null
    private var reloadAfter = false

    /**
     * Reloads the list. Calls that arrive mid-load (launch, resume, permissions and the content
     * observer all fire together) are folded into a single follow-up load instead of running in parallel.
     */
    fun refresh() {
        if (loading?.isActive == true) { reloadAfter = true; return }
        val ctx = getApplication<Application>()
        loading = viewModelScope.launch {
            do {
                reloadAfter = false
                val fresh = withContext(Dispatchers.IO) {
                    runCatching { SmsRepository.conversations(ctx) }.getOrNull()?.also { ConversationSnapshot.write(ctx, it) }
                }
                if (fresh != null) _conversations.value = fresh
            } while (reloadAfter)
            if (!observing) {
                runCatching {
                    ctx.contentResolver.registerContentObserver(Telephony.MmsSms.CONTENT_URI, true, observer)
                    observing = true
                }
            }
        }
    }

    fun delete(conversation: Conversation) = deleteMany(listOf(conversation))

    fun deleteMany(items: List<Conversation>) {
        val ctx = getApplication<Application>()
        val ids = items.map { it.threadId }.toSet()
        _conversations.value = _conversations.value.filterNot { it.threadId in ids }
        viewModelScope.launch(Dispatchers.IO) { items.forEach { SmsRepository.deleteThread(ctx, it.threadId) } }
    }

    fun markReadMany(items: List<Conversation>) {
        val ctx = getApplication<Application>()
        val ids = items.map { it.threadId }.toSet()
        _conversations.value = _conversations.value.map { if (it.threadId in ids) it.copy(unread = false) else it }
        viewModelScope.launch(Dispatchers.IO) { items.forEach { SmsRepository.markRead(ctx, it.threadId) } }
    }

    fun markAllRead() {
        val ctx = getApplication<Application>()
        _conversations.value = _conversations.value.map { it.copy(unread = false) }
        viewModelScope.launch(Dispatchers.IO) { SmsRepository.markAllRead(ctx) }
    }

    fun setSpam(items: List<Conversation>, spam: Boolean) {
        items.forEach { Prefs.setSpam(it.address, spam) }
        if (spam) items.forEach { com.tonny.messages.sms.MessageNotifier.clear(getApplication(), it.threadId) }
        _conversations.value = _conversations.value.toList()
    }

    fun setArchived(items: List<Conversation>, archived: Boolean) {
        items.forEach { Prefs.setArchived(it.address, archived) }
        _conversations.value = _conversations.value.toList()
    }

    fun markUnread(items: List<Conversation>) {
        val ctx = getApplication<Application>()
        val ids = items.map { it.threadId }.toSet()
        _conversations.value = _conversations.value.map { if (it.threadId in ids) it.copy(unread = true) else it }
        viewModelScope.launch(Dispatchers.IO) { items.forEach { SmsRepository.markUnread(ctx, it.threadId) } }
    }

    override fun onCleared() {
        getApplication<Application>().contentResolver.unregisterContentObserver(observer)
    }
}
