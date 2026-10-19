package com.tonny.messages.ui.components

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue

fun initials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.firstOrNull()?.isLetter() == true }
    return when {
        parts.isEmpty() -> ""
        parts.size == 1 -> parts[0].take(1).uppercase()
        else -> (parts.first().take(1) + parts.last().take(1)).uppercase()
    }
}

@Composable
fun rememberPhoto(uri: String?): ImageBitmap? {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, uri) {
        value = if (uri == null) null else withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(uri))?.use {
                    BitmapFactory.decodeStream(it)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
    return bitmap
}

/** iOS monogram: soft gray gradient disc with white initials. */
@Composable
fun Avatar(
    name: String,
    photoUri: String?,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    val photo = rememberPhoto(photoUri)
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(Color(0xFFA5ABB8), Color(0xFF858994)))),
        contentAlignment = Alignment.Center,
    ) {
        val letters = initials(name)
        when {
            photo != null -> Image(photo, null, Modifier.size(size), contentScale = ContentScale.Crop)
            letters.isNotEmpty() -> Text(
                letters,
                color = Color.White,
                fontSize = (size.value * 0.4f).sp,
                fontWeight = FontWeight.SemiBold,
            )
            else -> Icon(Icons.Rounded.Person, null, tint = Color.White, modifier = Modifier.size(size * 0.62f))
        }
    }
}
