package com.tonny.messages.data

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import kotlin.math.max

sealed interface Wallpaper {
    data object None : Wallpaper
    data class Preset(val index: Int) : Wallpaper
    data class Photo(val path: String) : Wallpaper

    fun encode(): String = when (this) {
        None -> "none"
        is Preset -> "preset:$index"
        is Photo -> "photo:$path"
    }

    companion object {
        fun decode(s: String?): Wallpaper = when {
            s == null || s == "none" -> None
            s.startsWith("preset:") -> s.removePrefix("preset:").toIntOrNull()?.let(::Preset) ?: None
            s.startsWith("photo:") -> s.removePrefix("photo:").takeIf { File(it).exists() }?.let(::Photo) ?: None
            else -> None
        }
    }
}

enum class ThemeMode { System, Light, Dark }

enum class WallpaperTarget(val key: String) { App("app"), Chat("chat") }

data class Settings(
    val theme: ThemeMode = ThemeMode.System,
    val appWallpaper: Wallpaper = Wallpaper.None,
    val appDim: Float = 0.55f,
    val chatWallpaper: Wallpaper = Wallpaper.None,
    val chatDim: Float = 0.4f,
    /** Addresses the user marked as spam. */
    val spam: Set<String> = emptySet(),
    /** Addresses the user archived. */
    val archived: Set<String> = emptySet(),
    /** Move betting/promo texts from unknown senders to Spam automatically. */
    val autoSpamEnabled: Boolean = true,
    /** Senders the filter flagged (their latest text looked like a promo). */
    val autoSpam: Set<String> = emptySet(),
    /** Senders you moved out of Spam; the filter leaves them alone. */
    val notSpam: Set<String> = emptySet(),
)

object Prefs {
    private lateinit var sp: SharedPreferences
    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    fun init(context: Context) {
        if (::sp.isInitialized) return
        sp = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
        _settings.value = Settings(
            theme = ThemeMode.entries.getOrElse(sp.getInt("theme", 0)) { ThemeMode.System },
            appWallpaper = Wallpaper.decode(sp.getString("appWall", null)),
            appDim = sp.getFloat("appDim", 0.55f),
            chatWallpaper = Wallpaper.decode(sp.getString("chatWall", null)),
            chatDim = sp.getFloat("chatDim", 0.4f),
            spam = sp.getStringSet("spam", emptySet())?.toSet() ?: emptySet(),
            archived = sp.getStringSet("archived", emptySet())?.toSet() ?: emptySet(),
            autoSpamEnabled = sp.getBoolean("autoSpamOn", true),
            autoSpam = sp.getStringSet("autoSpam", emptySet())?.toSet() ?: emptySet(),
            notSpam = sp.getStringSet("notSpam", emptySet())?.toSet() ?: emptySet(),
        )
    }

    fun update(transform: (Settings) -> Settings) {
        val old = _settings.value
        val new = transform(old)
        _settings.value = new
        sp.edit {
            putInt("theme", new.theme.ordinal)
            putString("appWall", new.appWallpaper.encode())
            putFloat("appDim", new.appDim)
            putString("chatWall", new.chatWallpaper.encode())
            putFloat("chatDim", new.chatDim)
            putStringSet("spam", new.spam)
            putStringSet("archived", new.archived)
            putBoolean("autoSpamOn", new.autoSpamEnabled)
            putStringSet("autoSpam", new.autoSpam)
            putStringSet("notSpam", new.notSpam)
        }
        listOf(old.appWallpaper, old.chatWallpaper).filterIsInstance<Wallpaper.Photo>().forEach { p ->
            if (p != new.appWallpaper && p != new.chatWallpaper) File(p.path).delete()
        }
    }

    /** True if an address is flagged spam (compared on the last 9 digits, or the raw sender id). */
    fun isSpam(address: String): Boolean {
        val key = spamKey(address)
        val s = _settings.value
        return key in s.spam || (s.autoSpamEnabled && key in s.autoSpam && key !in s.notSpam)
    }

    fun setSpam(address: String, spam: Boolean) {
        val key = spamKey(address)
        update {
            if (spam) it.copy(spam = it.spam + key, notSpam = it.notSpam - key)
            else it.copy(spam = it.spam - key, notSpam = it.notSpam + key)
        }
    }

    /** Records the filter's verdict on a sender's latest text. */
    fun setAutoSpam(address: String, promo: Boolean) {
        val key = spamKey(address)
        if ((key in _settings.value.autoSpam) == promo) return
        update { it.copy(autoSpam = if (promo) it.autoSpam + key else it.autoSpam - key) }
    }

    /** One-time pass over existing messages; [verdicts] maps sender address to "latest text is a promo". */
    fun seedAutoSpam(verdicts: Map<String, Boolean>) {
        update { s -> s.copy(autoSpam = s.autoSpam + verdicts.filterValues { it }.keys.map(::spamKey)) }
        sp.edit { putBoolean("autoSpamSeeded", true) }
    }

    val autoSpamSeeded: Boolean get() = sp.getBoolean("autoSpamSeeded", false)

    /** The SIM you last chose for this person, if you have more than one. */
    fun simFor(address: String): Int? = sp.getInt("sim_" + spamKey(address), -1).takeIf { it >= 0 }

    fun setSim(address: String, subId: Int) = sp.edit { putInt("sim_" + spamKey(address), subId) }

    fun isArchived(address: String): Boolean = _settings.value.archived.contains(spamKey(address))

    fun setArchived(address: String, archived: Boolean) {
        val key = spamKey(address)
        update { it.copy(archived = if (archived) it.archived + key else it.archived - key) }
    }

    private fun spamKey(address: String): String {
        val digits = address.filter { it.isDigit() }
        return if (digits.length >= 6) digits.takeLast(9) else address.lowercase()
    }

    fun importPhoto(context: Context, uri: Uri, target: WallpaperTarget): Wallpaper.Photo {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val longest = max(info.size.width, info.size.height)
            if (longest > MAX_SIDE) {
                val scale = MAX_SIDE.toFloat() / longest
                decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val dir = File(context.filesDir, "wallpapers").apply { mkdirs() }
        val file = File(dir, "${target.key}_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        return Wallpaper.Photo(file.absolutePath)
    }

    private const val MAX_SIDE = 1600
}
