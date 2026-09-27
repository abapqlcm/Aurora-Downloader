package com.aurora.downloader.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val family = FontFamily.Default

val AuroraTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 40.sp,
        lineHeight = 46.sp
    ),
    displayMedium = TextStyle(
        fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 32.sp,
        lineHeight = 38.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 26.sp,
        lineHeight = 32.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 21.sp,
        lineHeight = 26.sp
    ),
    titleLarge = TextStyle(
        fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 19.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 15.5.sp,
        lineHeight = 20.sp, letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 15.sp,
        lineHeight = 21.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    bodySmall = TextStyle(
        fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 11.5.sp,
        lineHeight = 15.sp
    ),
    labelLarge = TextStyle(
        fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 12.sp,
        letterSpacing = 0.8.sp
    ),
    labelMedium = TextStyle(
        fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 11.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 10.sp,
        letterSpacing = 0.4.sp
    )
)
