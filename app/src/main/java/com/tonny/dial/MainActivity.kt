package com.tonny.dial

import android.Manifest
import android.annotation.SuppressLint
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.activity.SystemBarStyle
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.lifecycleScope
import com.tonny.dial.data.Prefs
import com.tonny.dial.data.ThemeMode
import com.tonny.dial.data.WallpaperTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.graphics.Color as AndroidColor
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tonny.dial.data.DialViewModel
import com.tonny.dial.ui.screens.Home
import com.tonny.dial.ui.screens.ListActions
import com.tonny.dial.ui.screens.Onboarding
import com.tonny.dial.ui.screens.SimOption
import com.tonny.dial.ui.screens.SimPrompt
import com.tonny.dial.ui.screens.callSims
import com.tonny.dial.ui.theme.DialTheme

class MainActivity : ComponentActivity() {

    private val vm: DialViewModel by viewModels()
    private var pendingNumber by mutableStateOf<String?>(null)
    private var isDefault by mutableStateOf(false)
    private var skipped by mutableStateOf(false)
    private var simPrompt by mutableStateOf<SimPrompt?>(null)

    private val roleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refreshRole()
        requestPermissions()
    }

    private var photoTarget = WallpaperTarget.Call

    private val photoPicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@registerForActivityResult
        val target = photoTarget
        lifecycleScope.launch {
            val photo = withContext(Dispatchers.IO) { runCatching { Prefs.importPhoto(this@MainActivity, uri, target) }.getOrNull() }
            if (photo == null) {
                Toast.makeText(this@MainActivity, "Couldn't use that photo", Toast.LENGTH_SHORT).show()
            } else {
                Prefs.update { if (target == WallpaperTarget.Call) it.copy(callWallpaper = photo) else it.copy(appWallpaper = photo) }
            }
        }
    }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        skipped = prefs.getBoolean(KEY_SKIPPED, false)
        refreshRole()
        handleIntent(intent)
        if (isDefault || skipped) requestPermissions()

        val actions = ListActions(
            call = ::placeCall,
            message = { number -> launch(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null))) },
            addContact = { number ->
                launch(
                    Intent(Intent.ACTION_INSERT)
                        .setType(ContactsContract.Contacts.CONTENT_TYPE)
                        .putExtra(ContactsContract.Intents.Insert.PHONE, number)
                )
            },
            openContact = { c ->
                launch(Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, c.id)))
            },
            showCard = {},
            toggleStar = vm::toggleStar,
            toggleBlock = vm::toggleBlock,
            openSettings = {},
        )

        setContent {
            val settings by Prefs.settings.collectAsStateWithLifecycle()
            val dark = when (settings.theme) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            // Keep status/nav bar icons readable when the in-app theme differs from the system's.
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                enableEdgeToEdge(style, style)
            }
            DialTheme(dark = dark) {
                if (!isDefault && !skipped) {
                    Onboarding(
                        onMakeDefault = ::requestRole,
                        onSkip = {
                            skipped = true
                            prefs.edit { putBoolean(KEY_SKIPPED, true) }
                            requestPermissions()
                        },
                    )
                } else {
                    val contacts by vm.contacts.collectAsStateWithLifecycle()
                    val recents by vm.recents.collectAsStateWithLifecycle()
                    val blocked by vm.blocked.collectAsStateWithLifecycle()
                    val blockedNumbers by vm.blockedNumbers.collectAsStateWithLifecycle()
                    Home(
                        contacts = contacts,
                        recents = recents,
                        blocked = blocked,
                        blockedNumbers = blockedNumbers,
                        actions = actions,
                        pendingNumber = pendingNumber,
                        onPendingConsumed = { pendingNumber = null },
                        onRecentsShown = ::clearMissedNotification,
                        isDefaultDialer = isDefault,
                        onMakeDefault = ::requestRole,
                        onPickPhoto = { target ->
                            photoTarget = target
                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        simPrompt = simPrompt,
                        onSimPicked = { sim ->
                            val p = simPrompt
                            simPrompt = null
                            if (p != null && sim != null) dial(p.number, sim)
                        },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshRole()
        vm.refresh()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_DIAL || action == Intent.ACTION_VIEW) {
            pendingNumber = intent.data?.takeIf { it.scheme == "tel" }?.schemeSpecificPart.orEmpty()
        }
    }

    private val prefs get() = getSharedPreferences("dial", MODE_PRIVATE)

    private fun refreshRole() {
        isDefault = getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_DIALER)
    }

    private fun requestRole() {
        val rm = getSystemService(RoleManager::class.java)
        if (rm.isRoleAvailable(RoleManager.ROLE_DIALER) && !rm.isRoleHeld(RoleManager.ROLE_DIALER)) {
            roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_DIALER))
        } else {
            refreshRole()
        }
    }

    private fun requestPermissions() {
        val wanted = buildList {
            add(Manifest.permission.CALL_PHONE)
            add(Manifest.permission.READ_CONTACTS)
            add(Manifest.permission.WRITE_CONTACTS)
            add(Manifest.permission.READ_CALL_LOG)
            add(Manifest.permission.WRITE_CALL_LOG)
            add(Manifest.permission.READ_PHONE_STATE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (wanted.isNotEmpty()) permissionLauncher.launch(wanted.toTypedArray()) else vm.refresh()
    }

    /** With two or more SIMs, asks which one to use first. */
    private fun placeCall(number: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(arrayOf(Manifest.permission.CALL_PHONE))
            return
        }
        val sims = callSims(this)
        if (sims.size > 1) simPrompt = SimPrompt(number, sims) else dial(number, null)
    }

    @SuppressLint("MissingPermission")
    private fun dial(number: String, sim: SimOption?) {
        val extras = Bundle()
        if (sim != null) extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, sim.handle)
        runCatching {
            getSystemService(TelecomManager::class.java).placeCall(Uri.fromParts("tel", number, null), extras)
        }.onFailure { Toast.makeText(this, "Couldn't place call", Toast.LENGTH_SHORT).show() }
    }

    @SuppressLint("MissingPermission")
    private fun clearMissedNotification() {
        if (isDefault) runCatching { getSystemService(TelecomManager::class.java).cancelMissedCallsNotification() }
        com.tonny.dial.telecom.MissedCallReceiver.cancel(this)
    }

    private fun launch(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "No app can handle that", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val KEY_SKIPPED = "skipped_default"
    }
}
