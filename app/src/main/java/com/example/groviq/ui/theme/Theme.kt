package com.example.groviq.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.groviq.R

val clashFont = FontFamily(
    Font(R.font.clashsemi, weight = FontWeight.Normal),
    Font(R.font.clashmedium, weight = FontWeight.Medium),
)

val SfProDisplay = FontFamily(
    Font(R.font.regular, weight = FontWeight.Normal),
    Font(R.font.light, weight = FontWeight.Light),
    Font(R.font.medium, weight = FontWeight.Medium),
    Font(R.font.bold, weight = FontWeight.Bold),
    Font(R.font.blackitalic, weight = FontWeight.Black, style = FontStyle.Italic),
    Font(R.font.heavy_italic, weight = FontWeight.ExtraBold, style = FontStyle.Italic),
    Font(R.font.light_italic, weight = FontWeight.Light, style = FontStyle.Italic),
    Font(R.font.semibold_italic, weight = FontWeight.SemiBold, style = FontStyle.Italic),
    Font(R.font.regular, weight = FontWeight.Thin, style = FontStyle.Italic),
    Font(R.font.ultra_light_italic, weight = FontWeight.ExtraLight, style = FontStyle.Italic)
)


// главный цвет темы
private val MainColor = Color(
    218,
    105,
    105,
    255
)

// Тёмная тема
private val DarkColorScheme = darkColorScheme(
    primary       = MainColor,
    onPrimary     = Color.White,
    secondary     = MainColor,
    onSecondary   = Color.White,
    tertiary      = Color(14, 14, 14),
    onTertiary    = Color.White,
    background    = Color(14, 14, 14),
    onBackground  = Color.White,
    surface       = Color(14, 14, 14),
    onSurface     = Color.White,
    error         = Color(255, 69, 58),
    onError       = Color.White,
    surfaceVariant = Color(40, 40, 40),
    onSurfaceVariant = Color.White.copy(alpha = 0.7f),
    outline       = Color(100, 100, 100),
    inverseOnSurface = Color.Black,
    inverseSurface  = Color.White,
    inversePrimary  = Color(133, 98, 225)
)

// Светлая тема
private val LightColorScheme = lightColorScheme(
    primary       = MainColor,
    onPrimary     = Color.White,
    secondary     = MainColor,
    onSecondary   = Color.White,
    tertiary      = Color(238, 238, 238),
    onTertiary    = Color(20, 20, 20),
    background    = Color(236, 236, 236),
    onBackground  = Color(15, 15, 15),
    surface       = Color(242, 242, 242),
    onSurface     = Color(15, 15, 15),
    error         = Color(255, 69, 58),
    onError       = Color.White,
    surfaceVariant = Color(220, 220, 220),
    onSurfaceVariant = Color.Black.copy(alpha = 0.7f),
    outline       = Color(160, 160, 160),
    inverseOnSurface = Color.White,
    inverseSurface  = Color(30, 30, 30),
    inversePrimary  = Color(133, 98, 225)
)


@Composable
fun GroviqTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(
            bodyLarge = TextStyle(
                fontFamily = SfProDisplay,
                fontWeight = FontWeight.Normal,
                fontSize = 18.sp,
                letterSpacing = 0.55.sp
            ),
            bodyMedium = TextStyle(
                fontFamily = SfProDisplay,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                letterSpacing = 0.55.sp
            ),
            bodySmall = TextStyle(
                fontFamily = SfProDisplay,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                letterSpacing = 0.55.sp
            ),
            titleLarge = TextStyle(
                fontFamily = SfProDisplay,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                letterSpacing = 0.55.sp
            ),
            titleMedium = TextStyle(
                fontFamily = SfProDisplay,
                fontWeight = FontWeight.Medium,
                fontSize = 20.sp,
                letterSpacing = 0.55.sp
            ),
            labelLarge = TextStyle(
                fontFamily = SfProDisplay,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                letterSpacing = 0.55.sp
            )
        ),
        content = content
    )
}