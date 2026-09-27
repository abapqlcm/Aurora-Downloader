package com.aurora.downloader.ui.screens.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Brief branded launch screen: black canvas, gold Aurora wordmark that fades
 * and scales in. Not a functional screen — hands off to the main nav.
 */
@Composable
fun AuroraSplash() {
    val scale = remember { Animatable(0.92f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        delay(80)
        alpha.animateTo(1f, tween(420, easing = LinearEasing))
        scale.animateTo(1f, tween(520, easing = LinearEasing))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "AURORA",
            fontWeight = FontWeight.Black,
            fontSize = 34.sp,
            letterSpacing = 6.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .scale(scale.value)
                .alpha(alpha.value)
        )
    }
}
