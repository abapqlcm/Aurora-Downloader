package com.aurora.downloader.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * The shared primary/secondary button used across Aurora screens — drawn with
 * Modifier backgrounds rather than Button so the gold-on-black treatment stays
 * exactly right.
 */
@Composable
fun AuroraButton(
    label: String,
    primary: Boolean = true,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(24.dp)
    val container = if (primary) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surface
    val content = if (primary) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurface
    val alpha = if (enabled) 1f else 0.4f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(container.copy(alpha = alpha))
            .border(
                width = if (primary) 0.dp else 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = shape
            )
            .padding(PaddingValues(vertical = 14.dp, horizontal = 20.dp))
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                androidx.compose.material3.Icon(
                    icon, contentDescription = null,
                    tint = content.copy(alpha = alpha),
                    modifier = Modifier.width(20.dp)
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = content.copy(alpha = alpha)
            )
        }
    }
}
