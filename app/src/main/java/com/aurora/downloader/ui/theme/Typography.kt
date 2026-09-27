package com.aurora.downloader.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val family = FontFamily.Default

val AuroraTypography = Typography(
    displayMedium = TextStyle(
        fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 44.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleLarge = TextStyle(
        fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 19.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 15.sp,
        lineHeight = 20.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    bodySmall = TextStyle(
        fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 11.sp,
        lineHeight = 14.sp
    ),
    labelLarge = TextStyle(
        fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
        letterSpacing = 0.6.sp
    ),
    labelMedium = TextStyle(
        fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 11.sp,
        letterSpacing = 0.4.sp
    ),
    labelSmall = TextStyle(
        fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 10.sp,
        letterSpacing = 0.3.sp
    )
)
