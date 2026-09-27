package com.aurora.downloader.ui.screens.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aurora.downloader.AuroraApp

/**
 * The notification-permission gate.
 *
 * Android 13+ requires POST_NOTIFICATIONS at runtime. Asking for it blind,
 * the moment the app opens, gets denied — so this shows *why* first, then
 * asks. Denial is fine: downloads keep working, and the settings screen offers
 * the re-enable path.
 */
@Composable
fun NotificationPermissionGate(
    app: AuroraApp,
    content: @Composable () -> Unit
) {
    val needsGate = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    // Gate shown once per install until the user decides.
    val prefs = app.notificationPrefs
    val decided by prefs.decided.collectAsState(initial = false)

    if (!needsGate || decided) {
        content()
        return
    }

    var showRationale by remember { mutableStateOf(true) }

    val scope = rememberCoroutineScope()

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        showRationale = false
        scope.launch { prefs.markDecided(granted) }
    }

    AnimatedVisibility(visible = showRationale, enter = fadeIn(), exit = fadeOut()) {
        RationaleScreen(
            onAllow = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) },
            onNotNow = {
                showRationale = false
                scope.launch { prefs.markDecided(granted = false) }
            }
        )
    }

    if (!showRationale) {
        content()
    }
}

@Composable
private fun RationaleScreen(
    onAllow: () -> Unit,
    onNotNow: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Notifications,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "Stay in control of your downloads",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Aurora uses notifications to show download progress, " +
                    "and to let you pause or cancel from anywhere — " +
                    "even when the app is in the background.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(32.dp))
            AuroraButton("Allow notifications", primary = true, onClick = onAllow)
            Spacer(Modifier.height(10.dp))
            AuroraButton("Not now", primary = false, onClick = onNotNow)
        }
    }
}
