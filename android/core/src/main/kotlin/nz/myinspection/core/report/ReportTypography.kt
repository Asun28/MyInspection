package nz.myinspection.core.report

enum class TextFontRole { LATIN_SANS, CJK_FALLBACK }

data class TextStyleProfile(
    val fontSizePt: Double,
    val lineHeightMm: Int,
)

/** Measured evidence is inert data; composer and builder own contextual validation. */
data class TextMetricSnapshot(
    val style: TextStyle,
    val language: TextLanguage,
    val fontRole: TextFontRole,
    val fontSizePt: Double,
    val baselineOffsetPt: Double,
    val glyphTopPt: Double,
    val glyphBottomPt: Double,
)

internal fun TextMetricSnapshot.requireFitsLineBox(heightPt: Int) {
    require(fontSizePt.isFinite() && baselineOffsetPt.isFinite() && glyphTopPt.isFinite() && glyphBottomPt.isFinite()) {
        "text metrics must be finite"
    }
    require(fontSizePt > 0.0) { "text metric font size must be positive" }
    require(baselineOffsetPt >= 0.0) { "text metric baseline offset must not be negative" }
    require(glyphTopPt <= 0.0 && glyphBottomPt >= 0.0) { "text metric glyph bounds must be signed around baseline" }
    require(baselineOffsetPt + glyphTopPt >= 0.0) { "text metric glyph top escapes its line box" }
    require(baselineOffsetPt + glyphBottomPt <= heightPt) { "text metric glyph bottom escapes its line box" }
}

data class ReportTypography(
    val title: TextStyleProfile,
    val body: TextStyleProfile,
    val caption: TextStyleProfile,
) {
    fun profileFor(style: TextStyle): TextStyleProfile = when (style) {
        TextStyle.TITLE -> title
        TextStyle.BODY -> body
        TextStyle.CAPTION -> caption
    }

    fun roleFor(language: TextLanguage): TextFontRole = when (language) {
        TextLanguage.EN -> TextFontRole.LATIN_SANS
        TextLanguage.ZH, TextLanguage.ORIGINAL, TextLanguage.NEUTRAL -> TextFontRole.CJK_FALLBACK
    }

    companion object {
        val DEFAULT = ReportTypography(
            title = TextStyleProfile(fontSizePt = 12.0, lineHeightMm = 5),
            body = TextStyleProfile(fontSizePt = 11.0, lineHeightMm = 6),
            caption = TextStyleProfile(fontSizePt = 9.0, lineHeightMm = 4),
        )
    }
}
