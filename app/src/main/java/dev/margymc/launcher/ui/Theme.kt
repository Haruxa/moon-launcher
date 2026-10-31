package dev.margymc.launcher.ui

import android.os.Build
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Material 3 Expressive: динамический цвет (Material You) там, где он есть,
 * экспрессивная шкала типографики и пружинная схема движения.
 */
private val Seed = Color(0xFF4C6B3C)

@Composable
fun MargyTheme(dark: Boolean = isSystemDark(), content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme(
            primary = Color(0xFFB2D39B),
            onPrimary = Color(0xFF1E370D),
            primaryContainer = Color(0xFF354E23),
            onPrimaryContainer = Color(0xFFCDEFA9),
            secondary = Color(0xFFBECBAD),
            tertiary = Color(0xFFA0CFCF),
            background = Color(0xFF12140F),
            surface = Color(0xFF12140F),
        )
        else -> lightColorScheme(
            primary = Seed,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFCDEFA9),
            onPrimaryContainer = Color(0xFF102004),
            secondary = Color(0xFF56624B),
            tertiary = Color(0xFF386666),
        )
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = MargyTypography(),
        content = content,
    )
}

@Composable
private fun isSystemDark(): Boolean =
    androidx.compose.foundation.isSystemInDarkTheme()

/** Экспрессивная типографика: крупнее и плотнее заголовки, живее подписи. */
@Composable
private fun MargyTypography(): Typography {
    val base = Typography()
    fun TextStyle.bumped(size: Float, weight: FontWeight, tracking: Float = 0f) =
        copy(
            fontSize = fontSize * size,
            fontWeight = weight,
            letterSpacing = tracking.sp,
            fontFamily = FontFamily.SansSerif,
        )
    return base.copy(
        displaySmall = base.displaySmall.bumped(1.02f, FontWeight.Bold, -0.5f),
        headlineLarge = base.headlineLarge.bumped(1.02f, FontWeight.Bold, -0.5f),
        headlineMedium = base.headlineMedium.bumped(1.0f, FontWeight.SemiBold, -0.25f),
        headlineSmall = base.headlineSmall.bumped(1.0f, FontWeight.SemiBold, -0.25f),
        titleLarge = base.titleLarge.bumped(1.0f, FontWeight.SemiBold),
        titleMedium = base.titleMedium.bumped(1.0f, FontWeight.Medium),
        labelLarge = base.labelLarge.bumped(1.0f, FontWeight.SemiBold, 0.2f),
    )
}
