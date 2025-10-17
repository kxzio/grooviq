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


private val DarkColorScheme = darkColorScheme(
    primary       = Color(
        132,
        169,
        201,
        255
    ),
    onPrimary     = Color.White,
    secondary     = Color(
        132,
        169,
        201,
        255
    ),
    onSecondary   = Color.White,
    tertiary      = Color(14, 14, 14),
    onTertiary    = Color.White,
    background    = Color(14, 14, 14),
    onBackground  = Color.White,
    surface       = Color(14, 14, 14),
    onSurface     = Color.White,
    error         = Color(255, 69, 58), // красный для ошибок
    onError       = Color.White,
    surfaceVariant = Color(40, 40, 40),
    onSurfaceVariant = Color.White.copy(alpha = 0.7f),
    outline       = Color(100, 100, 100),
    inverseOnSurface = Color.Black,
    inverseSurface  = Color.White,
    inversePrimary  = Color(133, 98, 225)
)


private val LightColorScheme =
    lightColorScheme(
        primary = Purple40,
        secondary = PurpleGrey40,
        tertiary = Pink40

        /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */

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