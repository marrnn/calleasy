package com.calleasy.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.calleasy.app.R

object Palette {
    val Background = Color(0xFF0D1B2A)
    val Card = Color(0xFFFFFFFF)
    val CardText = Color(0xFF0D1B2A)
    val Green = Color(0xFF1B7F3B)
    val GreenPressed = Color(0xFF125C2A)
    val OnDark = Color(0xFFFFFFFF)
    val Muted = Color(0xFF9AA5B1)
    val Danger = Color(0xFFB3261E)
}

@Composable
fun CallEasyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Palette.Green,
            onPrimary = Color.White,
            background = Color(0xFFF4F6F8),
            error = Palette.Danger,
        ),
        content = content,
    )
}

@Composable
fun MainScreenTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.Green,
            background = Palette.Background,
        ),
        content = content,
    )
}

/** Foto circular con borde verde; si no hay foto muestra una silueta. */
@Composable
fun ContactPhoto(photoUri: String?, size: Dp, modifier: Modifier = Modifier, borderWidth: Dp = 6.dp) {
    val shape = CircleShape
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(Color(0xFFE3E8ED))
            .border(borderWidth, Palette.Green, shape),
        contentAlignment = Alignment.Center,
    ) {
        val placeholder: @Composable () -> Unit = {
            Image(
                painter = painterResource(R.drawable.ic_person),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().padding(size / 6),
            )
        }
        if (photoUri.isNullOrBlank()) {
            placeholder()
        } else {
            SubcomposeAsyncImage(
                model = photoUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(shape),
                loading = { placeholder() },
                error = { placeholder() },
            )
        }
    }
}
