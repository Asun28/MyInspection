package nz.myinspection.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

val fieldLedgerLightColorScheme = lightColorScheme(
    primary = Color(0xFF0B5D52),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC9ECE5),
    onPrimaryContainer = Color(0xFF073B35),
    secondary = Color(0xFF3E5B67),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD9EAF1),
    onSecondaryContainer = Color(0xFF183842),
    tertiary = Color(0xFF8B5C00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDEA8),
    onTertiaryContainer = Color(0xFF352000),
    inversePrimary = Color(0xFF88D0C5),
    primaryFixed = Color(0xFFC9ECE5),
    primaryFixedDim = Color(0xFF88D0C5),
    onPrimaryFixed = Color(0xFF073B35),
    onPrimaryFixedVariant = Color(0xFF0B5D52),
    secondaryFixed = Color(0xFFD9EAF1),
    secondaryFixedDim = Color(0xFFB8CAD2),
    onSecondaryFixed = Color(0xFF183842),
    onSecondaryFixedVariant = Color(0xFF3E5B67),
    tertiaryFixed = Color(0xFFFFDEA8),
    tertiaryFixedDim = Color(0xFFF0BD69),
    onTertiaryFixed = Color(0xFF352000),
    onTertiaryFixedVariant = Color(0xFF8B5C00),
    background = Color(0xFFF7F9F7),
    onBackground = Color(0xFF17201D),
    surface = Color(0xFFF7F9F7),
    surfaceVariant = Color(0xFFE2E8E4),
    inverseSurface = Color(0xFF0F1513),
    inverseOnSurface = Color(0xFFE0E4E1),
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFE2E8E4),
    surfaceContainerLow = Color.White,
    surfaceContainerLowest = Color.White,
    surfaceContainer = Color(0xFFEEF2EF),
    surfaceContainerHigh = Color(0xFFE2E8E4),
    surfaceContainerHighest = Color(0xFFE2E8E4),
    onSurface = Color(0xFF17201D),
    onSurfaceVariant = Color(0xFF44504B),
    outline = Color(0xFF6F7C76),
    outlineVariant = Color(0xFFC3CCC7),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD5),
    onErrorContainer = Color(0xFF410002),
)

val fieldLedgerDarkColorScheme = darkColorScheme(
    primary = Color(0xFF88D0C5),
    onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF064F46),
    onPrimaryContainer = Color(0xFFA5F2E8),
    secondary = Color(0xFFB8CAD2),
    onSecondary = Color(0xFF23343B),
    secondaryContainer = Color(0xFF354B54),
    onSecondaryContainer = Color(0xFFD4E6EE),
    tertiary = Color(0xFFF0BD69),
    onTertiary = Color(0xFF422C00),
    tertiaryContainer = Color(0xFF604200),
    onTertiaryContainer = Color(0xFFFFDEA8),
    inversePrimary = Color(0xFF0B5D52),
    primaryFixed = Color(0xFFC9ECE5),
    primaryFixedDim = Color(0xFF88D0C5),
    onPrimaryFixed = Color(0xFF073B35),
    onPrimaryFixedVariant = Color(0xFF0B5D52),
    secondaryFixed = Color(0xFFD9EAF1),
    secondaryFixedDim = Color(0xFFB8CAD2),
    onSecondaryFixed = Color(0xFF183842),
    onSecondaryFixedVariant = Color(0xFF3E5B67),
    tertiaryFixed = Color(0xFFFFDEA8),
    tertiaryFixedDim = Color(0xFFF0BD69),
    onTertiaryFixed = Color(0xFF352000),
    onTertiaryFixedVariant = Color(0xFF8B5C00),
    background = Color(0xFF0F1513),
    onBackground = Color(0xFFE0E4E1),
    surface = Color(0xFF0F1513),
    surfaceVariant = Color(0xFF252B29),
    inverseSurface = Color(0xFFF7F9F7),
    inverseOnSurface = Color(0xFF17201D),
    surfaceBright = Color(0xFF252B29),
    surfaceDim = Color(0xFF0F1513),
    surfaceContainerLow = Color(0xFF171D1B),
    surfaceContainerLowest = Color(0xFF0F1513),
    surfaceContainer = Color(0xFF1B211F),
    surfaceContainerHigh = Color(0xFF252B29),
    surfaceContainerHighest = Color(0xFF252B29),
    onSurface = Color(0xFFE0E4E1),
    onSurfaceVariant = Color(0xFFC3CAC6),
    outline = Color(0xFF8D9691),
    outlineVariant = Color(0xFF414946),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

data class FieldLedgerStatusColor(
    val container: Color,
    val content: Color,
    val rail: Color,
)

data class FieldLedgerStatusColors(
    val ok: FieldLedgerStatusColor,
    val attention: FieldLedgerStatusColor,
    val critical: FieldLedgerStatusColor,
    val notApplicable: FieldLedgerStatusColor,
    val privacy: FieldLedgerStatusColor,
)

val fieldLedgerLightStatusColors = FieldLedgerStatusColors(
    ok = FieldLedgerStatusColor(Color(0xFFC9ECE5), Color(0xFF073B35), Color(0xFF0B5D52)),
    attention = FieldLedgerStatusColor(Color(0xFFFFDEA8), Color(0xFF352000), Color(0xFF8B5C00)),
    critical = FieldLedgerStatusColor(Color(0xFFFFDAD5), Color(0xFF410002), Color(0xFFB3261E)),
    notApplicable = FieldLedgerStatusColor(Color(0xFFE2E8E4), Color(0xFF44504B), Color(0xFF6F7C76)),
    privacy = FieldLedgerStatusColor(Color(0xFFEADDFF), Color(0xFF241047), Color(0xFF60458E)),
)

val fieldLedgerDarkStatusColors = FieldLedgerStatusColors(
    ok = FieldLedgerStatusColor(Color(0xFF064F46), Color(0xFFA5F2E8), Color(0xFF88D0C5)),
    attention = FieldLedgerStatusColor(Color(0xFF604200), Color(0xFFFFDEA8), Color(0xFFF0BD69)),
    critical = FieldLedgerStatusColor(Color(0xFF93000A), Color(0xFFFFDAD6), Color(0xFFFFB4AB)),
    notApplicable = FieldLedgerStatusColor(Color(0xFF252B29), Color(0xFFC3CAC6), Color(0xFF8D9691)),
    privacy = FieldLedgerStatusColor(Color(0xFF51347F), Color(0xFFEADDFF), Color(0xFFD3BCFD)),
)

private val CondensedSans = FontFamily(Font(DeviceFontFamilyName("sans-serif-condensed")))

val fieldLedgerTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = CondensedSans,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.01).em,
    ),
    displayMedium = TextStyle(
        fontFamily = CondensedSans,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.01).em,
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.01).em,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.01).em,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 24.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 24.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 27.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.01.em,
    ),
    labelMedium = TextStyle(
        fontFamily = CondensedSans,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.04.em,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.02.em,
    ),
)

val fieldLedgerDataLargeTextStyle = TextStyle(
    fontFamily = CondensedSans,
    fontWeight = FontWeight.Bold,
    fontSize = 28.sp,
    lineHeight = 32.sp,
    letterSpacing = (-0.01).em,
)

val fieldLedgerShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(16.dp),
)
