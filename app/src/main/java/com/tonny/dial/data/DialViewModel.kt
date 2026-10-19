package com.tonny.dial.data

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.provider.ContactsContract
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DialViewModel(app: Application) : AndroidViewModel(app) {

    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()

    private val _recents = MutableStateFlow<List<RecentCall>>(emptyList())
    val recents: StateFlow<List<RecentCall>> = _recents.asStateFlow()

    private val _blockedNumbers = MutableStateFlow<List<String>>(emptyList())
    /** Blocked numbers as entered, newest first. */
    val blockedNumbers: StateFlow<List<String>> = _blockedNumbers.asStateFlow()

    private val _blocked = MutableStateFlow<Set<String>>(emptySet())
    /** Match keys (last 9 digits) of blocked numbers; see [BlockedRepository.key]. */
    val blocked: StateFlow<Set<String>> = _blocked.asStateFlow()

    private fun setBlocked(numbers: List<String>) {
        _blockedNumbers.value = numbers
        _blocked.value = numbers.map(BlockedRepository::key).filter { it.isNotEmpty() }.toSet()
    }

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = refresh()
    }
    private var observingContacts = false
    private var observingLog = false

    private var loading: Job? = null
    private var reloadAfter = false

    /**
     * Reloads contacts, recents and blocked numbers. Calls that arrive mid-load (launch, resume,
     * permissions and the content observers all fire together) fold into one follow-up load.
     */
    fun refresh() {
        if (loading?.isActive == true) { reloadAfter = true; return }
        val ctx = getApplication<Application>()
        loading = viewModelScope.launch {
            do {
                reloadAfter = false
                if (granted(Manifest.permission.READ_CONTACTS)) {
                    _contacts.value = withContext(Dispatchers.IO) { runCatching { ContactsRepository.load(ctx) }.getOrDefault(emptyList()) }
                }
                if (granted(Manifest.permission.READ_CALL_LOG)) {
                    _recents.value = withContext(Dispatchers.IO) { runCatching { CallLogRepository.load(ctx) }.getOrDefault(emptyList()) }
                }
                setBlocked(withContext(Dispatchers.IO) {
                    runCatching { if (BlockedRepository.available(ctx)) BlockedRepository.load(ctx) else emptyList() }.getOrDefault(emptyList())
                })
            } while (reloadAfter)
            startObserving()
        }
    }

    private fun startObserving() {
        val resolver = getApplication<Application>().contentResolver
        if (!observingContacts && granted(Manifest.permission.READ_CONTACTS)) {
            resolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, observer)
            observingContacts = true
        }
        if (!observingLog && granted(Manifest.permission.READ_CALL_LOG)) {
            resolver.registerContentObserver(CallLog.Calls.CONTENT_URI, true, observer)
            observingLog = true
        }
    }

    fun toggleStar(contact: Contact) {
        val ctx = getApplication<Application>()
        // Optimistic update so the star flips instantly.
        _contacts.value = _contacts.value.map { if (it.id == contact.id) it.copy(starred = !it.starred) else it }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { ContactsRepository.setStarred(ctx, contact.id, !contact.starred) }
        }
    }

    /** Blocks every number given, or unblocks them all if any is already blocked. */
    fun toggleBlock(numbers: List<String>) {
        val ctx = getApplication<Application>()
        var numbers = numbers.filter { it.isNotBlank() }
        if (numbers.isEmpty()) return
        // Exact match covers sender names like "Easytext", which have no digits to key on.
        val unblock = numbers.any { it in _blockedNumbers.value || BlockedRepository.key(it) in _blocked.value }
        if (unblock) {
            // Also remove entries saved in another format (e.g. +254... vs 07...).
            val keys = numbers.map(BlockedRepository::key).filter { it.isNotEmpty() }.toSet()
            numbers = (numbers + _blockedNumbers.value.filter { BlockedRepository.key(it) in keys }).distinct()
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    check(BlockedRepository.available(ctx))
                    numbers.forEach { if (unblock) BlockedRepository.unblock(ctx, it) else BlockedRepository.block(ctx, it) }
                    BlockedRepository.load(ctx)
                }
            }
            result.onSuccess(::setBlocked)
            val ok = result.isSuccess
            val msg = when {
                !ok -> "Set Phone as your default phone app to block numbers"
                unblock -> "Caller unblocked"
                else -> "Caller blocked"
            }
            Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(getApplication(), permission) == PackageManager.PERMISSION_GRANTED

    override fun onCleared() {
        getApplication<Application>().contentResolver.unregisterContentObserver(observer)
    }
}
