package com.tonny.messages

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.tonny.messages.data.Conversation
import com.tonny.messages.data.MessagesViewModel
import com.tonny.messages.data.Prefs
import com.tonny.messages.data.ThemeMode
import com.tonny.messages.data.WallpaperTarget
import com.tonny.messages.ui.screens.ConvoActions
import com.tonny.messages.ui.screens.ConversationsScreen
import com.tonny.messages.ui.screens.ListMode
import com.tonny.messages.ui.screens.NewMessageScreen
import com.tonny.messages.ui.screens.Onboarding
import com.tonny.messages.ui.screens.SettingsScreen
import com.tonny.messages.ui.screens.ThreadScreen
import com.tonny.messages.ui.theme.DialTheme
import com.tonny.messages.ui.theme.Ios
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface Overlay {
    data object New : Overlay
    data object Spam : Overlay
    data object Archived : Overlay
    data object Settings : Overlay
    data class Thread(val threadId: Long, val address: String, val title: String, val photoUri: String?) : Overlay
}

class MainActivity : ComponentActivity() {

    private val vm: MessagesViewModel by viewModels()
    private var isDefault by mutableStateOf(false)
    private var openThread by mutableStateOf<Overlay.Thread?>(null)
    private var photoTarget = WallpaperTarget.App

    private val roleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refreshRole(); requestPermissions()
    }
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.refresh()
    }
    private val photoPicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@registerForActivityResult
        val target = photoTarget
        lifecycleScope.launch {
            val photo = withContext(Dispatchers.IO) { runCatching { Prefs.importPhoto(this@MainActivity, uri, target) }.getOrNull() }
            if (photo == null) Toast.makeText(this@MainActivity, "Couldn't use that photo", Toast.LENGTH_SHORT).show()
            else Prefs.update { if (target == WallpaperTarget.App) it.copy(appWallpaper = photo) else it.copy(chatWallpaper = photo) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        refreshRole()
        handleIntent(intent)
        if (isDefault) requestPermissions()

        setContent {
            val settings by Prefs.settings.collectAsStateWithLifecycle()
            val dark = when (settings.theme) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                enableEdgeToEdge(style, style)
            }
            DialTheme(dark = dark) {
                if (!isDefault) {
                    Onboarding(onMakeDefault = ::requestRole)
                } else {
                    val conversations by vm.conversations.collectAsStateWithLifecycle()
                    MessagesApp(
                        conversations = conversations,
                        // Any change to manual or automatic spam re-sorts the lists.
                        spamSet = settings.spam + settings.autoSpam.map { "auto:$it" } + settings.notSpam.map { "not:$it" } +
                            "filter:${settings.autoSpamEnabled}",
                        archivedSet = settings.archived,
                        requestedThread = openThread,
                        onThreadConsumed = { openThread = null },
                        actions = ConvoActions(
                            delete = vm::deleteMany,
                            markRead = vm::markReadMany,
                            markUnread = vm::markUnread,
                            markAllRead = vm::markAllRead,
                            setSpam = vm::setSpam,
                            setArchived = vm::setArchived,
                        ),
                        onPickPhoto = { target ->
                            photoTarget = target
                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshRole()
        if (isDefault) vm.refresh()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val threadId = intent.getLongExtra(EXTRA_THREAD, -1L)
        val address = intent.getStringExtra(EXTRA_ADDRESS)
        if (threadId > 0 && address != null) openThread = Overlay.Thread(threadId, address, address, null)
    }

    private fun refreshRole() {
        isDefault = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_SMS)
        } else {
            android.provider.Telephony.Sms.getDefaultSmsPackage(this) == packageName
        }
    }

    private fun requestRole() {
        val rm = getSystemService(RoleManager::class.java)
        if (rm.isRoleAvailable(RoleManager.ROLE_SMS) && !rm.isRoleHeld(RoleManager.ROLE_SMS)) {
            roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_SMS))
        } else refreshRole()
    }

    private fun requestPermissions() {
        val wanted = buildList {
            add(Manifest.permission.READ_SMS); add(Manifest.permission.SEND_SMS); add(Manifest.permission.RECEIVE_SMS)
            add(Manifest.permission.READ_CONTACTS); add(Manifest.permission.READ_PHONE_STATE)
            add(Manifest.permission.READ_PHONE_NUMBERS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (wanted.isNotEmpty()) permissionLauncher.launch(wanted.toTypedArray()) else vm.refresh()
    }

    companion object {
        const val EXTRA_THREAD = "thread_id"
        const val EXTRA_ADDRESS = "address"

        fun threadIntent(context: Context, threadId: Long, address: String): Intent =
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_THREAD, threadId).putExtra(EXTRA_ADDRESS, address)
    }
}

@Composable
private fun MessagesApp(
    conversations: List<Conversation>,
    spamSet: Set<String>,
    archivedSet: Set<String>,
    requestedThread: Overlay.Thread?,
    onThreadConsumed: () -> Unit,
    actions: ConvoActions,
    onPickPhoto: (WallpaperTarget) -> Unit,
) {
    // Pages open on top of each other, so Back returns to the page you came from (e.g. Spam -> chat -> Spam).
    var stack by remember { mutableStateOf(listOf<Overlay>()) }
    var forward by remember { mutableStateOf(true) }
    val overlay = stack.lastOrNull()
    val pageState = rememberSaveableStateHolder()
    var editing by remember { mutableStateOf(false) }

    fun push(o: Overlay) { forward = true; stack = stack + o }
    fun pop() {
        val top = stack.lastOrNull() ?: return
        forward = false
        stack = stack.dropLast(1)
        if (top !in stack) pageState.removeState(top.toString())
    }
    fun replaceTop(o: Overlay) { forward = true; stack = stack.dropLast(1) + o }

    LaunchedEffect(requestedThread) {
        requestedThread?.let {
            // Tapping a notification for the chat already open shouldn't stack a second copy.
            if ((stack.lastOrNull() as? Overlay.Thread)?.threadId != it.threadId) push(it)
            onThreadConsumed()
        }
    }
    LaunchedEffect(overlay) { editing = false }
    androidx.activity.compose.BackHandler(overlay != null) { pop() }
    // Registered last so it wins: Back first leaves selection mode on whichever page is showing.
    androidx.activity.compose.BackHandler(editing) { editing = false }

    val inbox = remember(conversations, spamSet, archivedSet) {
        conversations.filter { !Prefs.isSpam(it.address) && !Prefs.isArchived(it.address) }
    }
    val spamList = remember(conversations, spamSet, archivedSet) {
        conversations.filter { Prefs.isSpam(it.address) && !Prefs.isArchived(it.address) }
    }
    val archivedList = remember(conversations, archivedSet) { conversations.filter { Prefs.isArchived(it.address) } }

    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomInset = navBottom + 16.dp
    val openThreadOf: (Conversation) -> Unit = { push(Overlay.Thread(it.threadId, it.address, it.title, it.photoUri)) }

    Box(Modifier.fillMaxSize().background(Ios.colors.background)) {
        ConversationsScreen(
            "Messages", inbox, ListMode.Inbox, bottomInset,
            onOpen = openThreadOf, actions = actions, editing = editing, onEditingChange = { editing = it },
            onCompose = { push(Overlay.New) },
            onOpenSpam = { push(Overlay.Spam) }, onOpenArchived = { push(Overlay.Archived) },
            onOpenSettings = { push(Overlay.Settings) },
        )

        // Pages slide in from the right going forward and back out to the right going back.
        AnimatedContent(
            targetState = overlay,
            transitionSpec = {
                if (forward) ((slideInHorizontally { it } + fadeIn()) togetherWith fadeOut()).apply { targetContentZIndex = 1f }
                else (fadeIn() togetherWith (slideOutHorizontally { it } + fadeOut())).apply { targetContentZIndex = -1f }
            },
            label = "overlay",
        ) { ov ->
            // Keeps each page's scroll position while another page is on top of it.
            if (ov != null) pageState.SaveableStateProvider(ov.toString()) {
            when (ov) {
                Overlay.New -> NewMessageScreen(
                    onBack = ::pop,
                    // The new chat takes the compose page's place, so Back goes to where you started.
                    onSent = { threadId, address -> replaceTop(Overlay.Thread(threadId, address, address, null)) },
                )
                is Overlay.Thread -> ThreadScreen(ov.threadId, ov.address, ov.title, ov.photoUri, onBack = ::pop)
                Overlay.Settings -> Box(Modifier.fillMaxSize().background(Ios.colors.background)) { SettingsScreen(bottomInset, onPickPhoto, onBack = ::pop) }
                Overlay.Spam -> Box(Modifier.fillMaxSize().background(Ios.colors.background)) {
                    ConversationsScreen("Spam", spamList, ListMode.Spam, bottomInset, onOpen = openThreadOf, actions = actions, editing = editing, onEditingChange = { editing = it }, onBack = ::pop)
                }
                Overlay.Archived -> Box(Modifier.fillMaxSize().background(Ios.colors.background)) {
                    ConversationsScreen("Archived", archivedList, ListMode.Archived, bottomInset, onOpen = openThreadOf, actions = actions, editing = editing, onEditingChange = { editing = it }, onBack = ::pop)
                }
            }
            }
        }
    }
}
