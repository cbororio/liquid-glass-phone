package com.tonny.messages.ui.components

import android.graphics.BitmapFactory
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tonny.messages.data.Prefs
import com.tonny.messages.data.Wallpaper
import com.tonny.messages.ui.theme.Ios
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PresetWallpaper(val name: String, val colors: List<Color>, val glow: Color)

/** Built-in gradients, picked to look good under glass in both light and dark. */
val PresetWallpapers = listOf(
    PresetWallpaper("Ocean", listOf(Color(0xFF0B1E4D), Color(0xFF1C5CC8), Color(0xFF39B5E8)), Color(0xFF7FD3FF)),
    PresetWallpaper("Sunset", listOf(Color(0xFF3A0F4D), Color(0xFFC2306B), Color(0xFFFF8A3D)), Color(0xFFFFC27A)),
    PresetWallpaper("Mint", listOf(Color(0xFF06302B), Color(0xFF0E8C74), Color(0xFF7BE0B6)), Color(0xFFC6FFE3)),
    PresetWallpaper("Aurora", listOf(Color(0xFF0A0F2C), Color(0xFF2A1E7A), Color(0xFF1FA67A)), Color(0xFF7CFFB2)),
    PresetWallpaper("Rose", listOf(Color(0xFF4A1430), Color(0xFFD4527A), Color(0xFFFFC2C8)), Color(0xFFFFE3E8)),
    PresetWallpaper("Graphite", listOf(Color(0xFF111114), Color(0xFF3A3A42), Color(0xFF7A7A86)), Color(0xFFBFC0CC)),
)

/** Keeps the last decoded photo around so switching tabs doesn't re-decode it. */
private object PhotoCache {
    var path: String? = null
    var bitmap: ImageBitmap? = null
}

@Composable
fun rememberWallpaperPhoto(path: String): ImageBitmap? {
    val bitmap by produceState(if (PhotoCache.path == path) PhotoCache.bitmap else null, path) {
        if (PhotoCache.path == path && PhotoCache.bitmap != null) {
            value = PhotoCache.bitmap; return@produceState
        }
        val decoded = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(path)?.asImageBitmap() }
        PhotoCache.path = path
        PhotoCache.bitmap = decoded
        value = decoded
    }
    return bitmap
}

/** Draws a wallpaper filling its bounds. Draws nothing for [Wallpaper.None]. */
@Composable
fun WallpaperView(wallpaper: Wallpaper, modifier: Modifier = Modifier, blur: Dp = 0.dp) {
    when (wallpaper) {
        Wallpaper.None -> Unit
        is Wallpaper.Preset -> {
            val p = PresetWallpapers.getOrElse(wallpaper.index) { PresetWallpapers.first() }
            Box(
                modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(p.colors, start = Offset(0f, 0f), end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)))
                    .background(Brush.radialGradient(listOf(p.glow.copy(alpha = 0.45f), Color.Transparent), center = Offset(220f, 260f), radius = 900f)),
            )
        }
        is Wallpaper.Photo -> {
            val photo = rememberWallpaperPhoto(wallpaper.path)
            if (photo != null) {
                Image(
                    photo, null,
                    contentScale = ContentScale.Crop,
                    modifier = modifier
                        .fillMaxSize()
                        .then(
                            if (blur > 0.dp && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                                Modifier.blur(blur, BlurredEdgeTreatment.Rectangle) else Modifier
                        ),
                )
            }
        }
    }
}

/**
 * The background behind every tab: the plain system color, or the chosen app wallpaper washed
 * toward that color so lists stay readable.
 */
@Composable
fun AppBackground(modifier: Modifier = Modifier) {
    val ios = Ios.colors
    val settings by Prefs.settings.collectAsStateWithLifecycle()
    Box(modifier.fillMaxSize().background(ios.background)) {
        if (settings.appWallpaper != Wallpaper.None) {
            WallpaperView(settings.appWallpaper)
            Box(Modifier.fillMaxSize().background(ios.background.copy(alpha = settings.appDim)))
        }
    }
}
