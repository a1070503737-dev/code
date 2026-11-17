package com.zhuangxiu.ledger.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// 奶油白与松绿：用明晰的层级区分预算、支出和提醒。

private val LightColors = lightColorScheme(
    primary = Color(0xFF2E6655),            // 松绿
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDDECE3),
    onPrimaryContainer = Color(0xFF163C30),
    secondary = Color(0xFF936348),          // 暖木色
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF3E3D5),
    onSecondaryContainer = Color(0xFF432A1B),
    tertiary = Color(0xFF526C48),           // 橄榄绿，做少量点缀
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE0EDD8),
    onTertiaryContainer = Color(0xFF20331B),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFCDAD5),
    onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFF7F8F2),         // 奶油白
    onBackground = Color(0xFF202D27),
    surface = Color(0xFFF7F8F2),
    onSurface = Color(0xFF202D27),
    surfaceVariant = Color(0xFFE8ECE3),
    onSurfaceVariant = Color(0xFF626E65),
    outline = Color(0xFF89958C),
    outlineVariant = Color(0xFFDFE5DC),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFEDF1E9),
    surfaceContainerHigh = Color(0xFFE5EBE0),
    surfaceContainerHighest = Color(0xFFDEE6D9),
)

/** 深色模式：深松绿底色与柔和的浅绿色。 */
private val DarkColors = darkColorScheme(
    primary = Color(0xFFA6D5BC),
    onPrimary = Color(0xFF0B3828),
    primaryContainer = Color(0xFF24503D),
    onPrimaryContainer = Color(0xFFDDECE3),
    secondary = Color(0xFFE5BFA5),
    onSecondary = Color(0xFF452C1C),
    secondaryContainer = Color(0xFF60422F),
    onSecondaryContainer = Color(0xFFF8DDC8),
    tertiary = Color(0xFFBED5AB),
    onTertiary = Color(0xFF293C20),
    tertiaryContainer = Color(0xFF405636),
    onTertiaryContainer = Color(0xFFE0EDD8),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF151C18),
    onBackground = Color(0xFFE2EAE0),
    surface = Color(0xFF151C18),
    onSurface = Color(0xFFE2EAE0),
    surfaceVariant = Color(0xFF414D43),
    onSurfaceVariant = Color(0xFFC0CDC1),
    outline = Color(0xFF8B9B8E),
    outlineVariant = Color(0xFF3C493F),
    surfaceContainerLowest = Color(0xFF101612),
    surfaceContainerLow = Color(0xFF1D2720),
    surfaceContainer = Color(0xFF233027),
    surfaceContainerHigh = Color(0xFF2D3A30),
    surfaceContainerHighest = Color(0xFF37453A),
)

/** 圆角统一偏大，营造柔和的家居感 */
private val LedgerShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val LedgerTypography = Typography(
    displaySmall = TextStyle(fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 21.sp, lineHeight = 29.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

/** Material 配色之外的补充颜色：预警色和图表色板 */
data class ExtraColors(
    val warning: Color,
    val success: Color,
    val chart: List<Color>,
)

private val LightExtra = ExtraColors(
    warning = Color(0xFFB7791F),
    success = Color(0xFF5E7D4A),
    chart = listOf(
        Color(0xFF2E6655), Color(0xFFD9A05B), Color(0xFF936348), Color(0xFF7D8F5C), Color(0xFFB98A8A),
        Color(0xFF5F8A8B), Color(0xFFE0B8A0), Color(0xFF9C7BA8), Color(0xFFA8A17A), Color(0xFF6E5A4E),
    ),
)

private val DarkExtra = ExtraColors(
    warning = Color(0xFFF2C46D),
    success = Color(0xFFA9C993),
    chart = listOf(
        Color(0xFFA6D5BC), Color(0xFFE8C07F), Color(0xFFC4A283), Color(0xFFA9BC87), Color(0xFFD9A9A9),
        Color(0xFF8DB8B9), Color(0xFFF0CDB8), Color(0xFFC0A3CB), Color(0xFFC9C39C), Color(0xFFA08B7E),
    ),
)

private val LocalExtraColors = staticCompositionLocalOf { LightExtra }

/** 取补充颜色：`LedgerTheme.extra.warning` */
object LedgerTheme {
    val extra: ExtraColors
        @Composable @ReadOnlyComposable get() = LocalExtraColors.current
}

@Composable
fun RenovationLedgerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalExtraColors provides if (darkTheme) DarkExtra else LightExtra) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            shapes = LedgerShapes,
            typography = LedgerTypography,
            content = content,
        )
    }
}
