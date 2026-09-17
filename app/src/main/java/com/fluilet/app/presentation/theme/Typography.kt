package com.fluilet.app.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.fluilet.app.R

// Requiere colocar los .ttf en res/font/: opendyslexic_regular.ttf, opendyslexic_bold.ttf
val OpenDyslexicFontFamily = FontFamily(
    Font(R.font.opendyslexic_regular, FontWeight.Normal),
    Font(R.font.opendyslexic_bold, FontWeight.Bold)
)

val FluiletTypography = Typography(
    bodyLarge = TextStyle(
        fontFamily = OpenDyslexicFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    titleLarge = TextStyle(
        fontFamily = OpenDyslexicFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = OpenDyslexicFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    )
)