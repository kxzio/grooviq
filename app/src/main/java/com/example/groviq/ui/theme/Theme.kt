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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.groviq.R

val SfProDisplay = FontFamily(
    Font(R.font.sfprodisplayregular, weight = FontWeight.Normal),
    Font(R.font.sfprodisplaymedium, weight = FontWeight.Medium),
    Font(R.font.sfprodisplaybold, weight = FontWeight.Bold),
    Font(R.font.sfprodisplayblackitalic, weight = FontWeight.Black, style = FontStyle.Italic),
    Font(R.font.sfprodisplayheavyitalic, weight = FontWeight.ExtraBold, style = FontStyle.Italic),
    Font(R.font.sfprodisplaylightitalic, weight = FontWeight.Light, style = FontStyle.Italic),
    Font(R.font.sfprodisplaysemibolditalic, weight = FontWeight.SemiBold, style = FontStyle.Italic),
    Font(R.font.sfprodisplaythinitalic, weight = FontWeight.Thin, style = FontStyle.Italic),
    Font(R.font.sfprodisplayultralightitalic, weight = FontWeight.ExtraLight, style = FontStyle.Italic)
)

private val DarkColorScheme =
    darkColorScheme(
        primary = Purple80,
        secondary = PurpleGrey80,
        tertiary = Pink80
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
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context =
                    LocalContext.current
                dynamicDarkColorScheme(context)
            }

            darkTheme -> DarkColorScheme
            else -> LightColorScheme
        }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(
            bodyLarge = TextStyle(
                fontFamily = SfProDisplay,
                fontWeight = FontWeight.Normal,
                fontSize = 18.sp,
                letterSpacing = 0.25.sp
            ),
            bodyMedium = TextStyle(
                fontFamily = SfProDisplay,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                letterSpacing = 0.25.sp
            ),
            bodySmall = TextStyle(
                fontFamily = SfProDisplay,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                letterSpacing = 0.2.sp
            ),
            titleLarge = TextStyle(
                fontFamily = SfProDisplay,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                letterSpacing = 0.3.sp
            ),
            titleMedium = TextStyle(
                fontFamily = SfProDisplay,
                fontWeight = FontWeight.Medium,
                fontSize = 20.sp,
                letterSpacing = 0.25.sp
            ),
            labelLarge = TextStyle(
                fontFamily = SfProDisplay,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                letterSpacing = 0.2.sp
            )
        ),
        content = content
    )
}