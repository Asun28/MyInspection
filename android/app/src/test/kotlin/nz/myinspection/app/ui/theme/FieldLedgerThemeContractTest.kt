package nz.myinspection.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FieldLedgerThemeContractTest {
    @Test
    fun `light scheme matches the approved field ledger palette`() {
        with(fieldLedgerLightColorScheme) {
            assertEquals(Color(0xFF0B5D52), primary)
            assertEquals(Color.White, onPrimary)
            assertEquals(Color(0xFFC9ECE5), primaryContainer)
            assertEquals(Color(0xFF073B35), onPrimaryContainer)
            assertEquals(Color(0xFF3E5B67), secondary)
            assertEquals(Color.White, onSecondary)
            assertEquals(Color(0xFFD9EAF1), secondaryContainer)
            assertEquals(Color(0xFF183842), onSecondaryContainer)
            assertEquals(Color(0xFF8B5C00), tertiary)
            assertEquals(Color.White, onTertiary)
            assertEquals(Color(0xFFFFDEA8), tertiaryContainer)
            assertEquals(Color(0xFF352000), onTertiaryContainer)
            assertEquals(Color(0xFFF7F9F7), background)
            assertEquals(Color(0xFF17201D), onBackground)
            assertEquals(Color(0xFFF7F9F7), surface)
            assertEquals(Color(0xFFFFFFFF), surfaceContainerLow)
            assertEquals(Color(0xFFEEF2EF), surfaceContainer)
            assertEquals(Color(0xFFE2E8E4), surfaceContainerHigh)
            assertEquals(Color(0xFF17201D), onSurface)
            assertEquals(Color(0xFF44504B), onSurfaceVariant)
            assertEquals(Color(0xFF6F7C76), outline)
            assertEquals(Color(0xFFC3CCC7), outlineVariant)
            assertEquals(Color(0xFFB3261E), error)
            assertEquals(Color.White, onError)
            assertEquals(Color(0xFFFFDAD5), errorContainer)
            assertEquals(Color(0xFF410002), onErrorContainer)
        }
    }

    @Test
    fun `dark scheme fixes stable semantic pairs with AA contrast`() {
        with(fieldLedgerDarkColorScheme) {
            assertEquals(Color(0xFF88D0C5), primary)
            assertEquals(Color(0xFF003731), onPrimary)
            assertEquals(Color(0xFF064F46), primaryContainer)
            assertEquals(Color(0xFFA5F2E8), onPrimaryContainer)
            assertEquals(Color(0xFFB8CAD2), secondary)
            assertEquals(Color(0xFF23343B), onSecondary)
            assertEquals(Color(0xFF354B54), secondaryContainer)
            assertEquals(Color(0xFFD4E6EE), onSecondaryContainer)
            assertEquals(Color(0xFFF0BD69), tertiary)
            assertEquals(Color(0xFF422C00), onTertiary)
            assertEquals(Color(0xFF604200), tertiaryContainer)
            assertEquals(Color(0xFFFFDEA8), onTertiaryContainer)
            assertEquals(Color(0xFF0F1513), background)
            assertEquals(Color(0xFFE0E4E1), onBackground)
            assertEquals(Color(0xFF0F1513), surface)
            assertEquals(Color(0xFF171D1B), surfaceContainerLow)
            assertEquals(Color(0xFF1B211F), surfaceContainer)
            assertEquals(Color(0xFF252B29), surfaceContainerHigh)
            assertEquals(Color(0xFFE0E4E1), onSurface)
            assertEquals(Color(0xFFC3CAC6), onSurfaceVariant)
            assertEquals(Color(0xFF8D9691), outline)
            assertEquals(Color(0xFF414946), outlineVariant)
            assertEquals(Color(0xFFFFB4AB), error)
            assertEquals(Color(0xFF690005), onError)
            assertEquals(Color(0xFF93000A), errorContainer)
            assertEquals(Color(0xFFFFDAD6), onErrorContainer)

            listOf(
                primary to onPrimary,
                primaryContainer to onPrimaryContainer,
                secondary to onSecondary,
                secondaryContainer to onSecondaryContainer,
                tertiary to onTertiary,
                tertiaryContainer to onTertiaryContainer,
                background to onBackground,
                surface to onSurface,
                surfaceContainerLow to onSurface,
                surfaceContainer to onSurface,
                surfaceContainerHigh to onSurfaceVariant,
                error to onError,
                errorContainer to onErrorContainer,
            ).forEach { (background, foreground) ->
                assertTrue(contrastRatio(background, foreground) >= 4.5)
            }
        }
    }

    @Test
    fun `five semantic roles remain distinct in light and dark palettes`() {
        assertSchemeRoles(fieldLedgerLightColorScheme, fieldLedgerLightStatusColors)
        assertSchemeRoles(fieldLedgerDarkColorScheme, fieldLedgerDarkStatusColors)
        assertEquals(
            FieldLedgerStatusColor(Color(0xFFEADDFF), Color(0xFF241047), Color(0xFF60458E)),
            fieldLedgerLightStatusColors.privacy,
        )
        assertEquals(
            FieldLedgerStatusColor(Color(0xFF51347F), Color(0xFFEADDFF), Color(0xFFD3BCFD)),
            fieldLedgerDarkStatusColors.privacy,
        )
        assertTrue(fieldLedgerLightStatusColors.privacy != fieldLedgerLightStatusColors.critical)
        assertTrue(fieldLedgerDarkStatusColors.privacy != fieldLedgerDarkStatusColors.critical)
        listOf(fieldLedgerLightStatusColors, fieldLedgerDarkStatusColors)
            .flatMap { colors ->
                listOf(colors.ok, colors.attention, colors.critical, colors.notApplicable, colors.privacy)
            }
            .forEach { color ->
                assertTrue(contrastRatio(color.container, color.content) >= 4.5)
            }
    }

    @Test
    fun `typography and shapes preserve field readability tokens`() {
        val sans = FontFamily.SansSerif
        val condensed = FontFamily(Font(DeviceFontFamilyName("sans-serif-condensed")))
        with(fieldLedgerTypography) {
            assertEquals(style(condensed, FontWeight.Bold, 32.sp, 38.sp, (-0.01).em), displayMedium)
            assertEquals(style(sans, FontWeight.Bold, 28.sp, 34.sp, (-0.01).em), headlineLarge)
            assertEquals(style(sans, FontWeight.Bold, 24.sp, 30.sp), headlineMedium)
            assertEquals(style(sans, FontWeight.Bold, 20.sp, 26.sp), titleLarge)
            assertEquals(style(sans, FontWeight.SemiBold, 17.sp, 24.sp), titleMedium)
            assertEquals(style(sans, FontWeight.Normal, 18.sp, 27.sp), bodyLarge)
            assertEquals(style(sans, FontWeight.Normal, 16.sp, 24.sp), bodyMedium)
            assertEquals(style(sans, FontWeight.Normal, 14.sp, 20.sp), bodySmall)
            assertEquals(style(sans, FontWeight.Bold, 16.sp, 20.sp, 0.01.em), labelLarge)
            assertEquals(style(condensed, FontWeight.Bold, 13.sp, 18.sp, 0.04.em), labelMedium)
            assertEquals(style(sans, FontWeight.SemiBold, 12.sp, 16.sp, 0.02.em), labelSmall)
        }
        assertEquals(
            style(condensed, FontWeight.Bold, 28.sp, 32.sp, (-0.01).em),
            fieldLedgerDataLargeTextStyle,
        )
        assertEquals(RoundedCornerShape(4.dp), fieldLedgerShapes.extraSmall)
        assertEquals(RoundedCornerShape(8.dp), fieldLedgerShapes.small)
        assertEquals(RoundedCornerShape(12.dp), fieldLedgerShapes.medium)
        assertEquals(RoundedCornerShape(16.dp), fieldLedgerShapes.large)
        assertEquals(RoundedCornerShape(16.dp), fieldLedgerShapes.extraLarge)
    }

    private fun assertSchemeRoles(
        scheme: androidx.compose.material3.ColorScheme,
        roles: FieldLedgerStatusColors,
    ) {
        assertEquals(FieldLedgerStatusColor(scheme.primaryContainer, scheme.onPrimaryContainer, scheme.primary), roles.ok)
        assertEquals(FieldLedgerStatusColor(scheme.tertiaryContainer, scheme.onTertiaryContainer, scheme.tertiary), roles.attention)
        assertEquals(FieldLedgerStatusColor(scheme.errorContainer, scheme.onErrorContainer, scheme.error), roles.critical)
        assertEquals(
            FieldLedgerStatusColor(scheme.surfaceContainerHigh, scheme.onSurfaceVariant, scheme.outline),
            roles.notApplicable,
        )
    }

    private fun style(
        family: FontFamily,
        weight: FontWeight,
        size: androidx.compose.ui.unit.TextUnit,
        lineHeight: androidx.compose.ui.unit.TextUnit,
        letterSpacing: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    ) = TextStyle(
        fontFamily = family,
        fontWeight = weight,
        fontSize = size,
        lineHeight = lineHeight,
        letterSpacing = letterSpacing,
    )

    private fun contrastRatio(background: Color, foreground: Color): Double {
        val backgroundLuminance = relativeLuminance(background)
        val foregroundLuminance = relativeLuminance(foreground)
        return (max(backgroundLuminance, foregroundLuminance) + 0.05) /
            (min(backgroundLuminance, foregroundLuminance) + 0.05)
    }

    private fun relativeLuminance(color: Color): Double {
        fun linear(component: Float): Double =
            if (component <= 0.04045f) component / 12.92 else
                ((component + 0.055) / 1.055).pow(2.4)

        return 0.2126 * linear(color.red) +
            0.7152 * linear(color.green) +
            0.0722 * linear(color.blue)
    }
}
