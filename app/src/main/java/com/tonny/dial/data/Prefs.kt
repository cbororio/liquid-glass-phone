package com.tonny.dial.data

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

/** What sits behind a screen: nothing, one of the built-in gradients, or a saved photo. */
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

enum class AnswerStyle { Slider, Buttons }

data class Settings(
    val theme: ThemeMode = ThemeMode.System,
    val answerStyle: AnswerStyle = AnswerStyle.Buttons,
    val callWallpaper: Wallpaper = Wallpaper.None,
    /** 0..1, how much the call-screen photo is blurred. */
    val callBlur: Float = 0.35f,
    /** 0..1, how much the call-screen wallpaper is darkened. */
    val callDim: Float = 0.3f,
    val appWallpaper: Wallpaper = Wallpaper.None,
    /** 0..1, how strongly the app wallpaper is washed out behind lists for legibility. */
    val appDim: Float = 0.55f,
)

enum class WallpaperTarget(val key: String) { Call("call"), App("app") }

/** App settings backed by SharedPreferences and exposed as a StateFlow. */
object Prefs {
    private lateinit var sp: SharedPreferences
    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    fun init(context: Context) {
        if (::sp.isInitialized) return
        sp = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
        _settings.value = Settings(
            theme = ThemeMode.entries.getOrElse(sp.getInt("theme", 0)) { ThemeMode.System },
            answerStyle = AnswerStyle.entries.getOrElse(sp.getInt("answer", AnswerStyle.Buttons.ordinal)) { AnswerStyle.Buttons },
            callWallpaper = Wallpaper.decode(sp.getString("callWall", null)),
            callBlur = sp.getFloat("callBlur", 0.35f),
            callDim = sp.getFloat("callDim", 0.3f),
            appWallpaper = Wallpaper.decode(sp.getString("appWall", null)),
            appDim = sp.getFloat("appDim", 0.55f),
        )
    }

    fun update(transform: (Settings) -> Settings) {
        val old = _settings.value
        val new = transform(old)
        _settings.value = new
        sp.edit {
            putInt("theme", new.theme.ordinal)
            putInt("answer", new.answerStyle.ordinal)
            putString("callWall", new.callWallpaper.encode())
            putFloat("callBlur", new.callBlur)
            putFloat("callDim", new.callDim)
            putString("appWall", new.appWallpaper.encode())
            putFloat("appDim", new.appDim)
        }
        // Clean up photos that nothing points at any more.
        listOf(old.callWallpaper, old.appWallpaper).filterIsInstance<Wallpaper.Photo>().forEach { p ->
            if (p != new.callWallpaper && p != new.appWallpaper) File(p.path).delete()
        }
    }

    /**
     * Copies a picked image into app storage, downscaled and with EXIF rotation applied, and
     * returns it as a [Wallpaper.Photo]. Runs blocking I/O, so call it off the main thread.
     */
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
        // A fresh name per import so anything caching the old bitmap reloads.
        val file = File(dir, "${target.key}_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        return Wallpaper.Photo(file.absolutePath)
    }

    private const val MAX_SIDE = 1600
}
