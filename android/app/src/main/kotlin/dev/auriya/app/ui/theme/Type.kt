package dev.auriya.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.auriya.app.R
import androidx.compose.ui.text.googlefonts.Font as GoogleFontRes

private val montserrat = GoogleFont("Montserrat")
private val provider =
    GoogleFont.Provider(
        providerAuthority = "com.google.android.gms.fonts",
        providerPackage = "com.google.android.gms",
        certificates = R.array.com_google_android_gms_fonts_certs,
    )

val MontserratFamily =
    FontFamily(
        GoogleFontRes(googleFont = montserrat, fontProvider = provider, weight = FontWeight.Black),
        GoogleFontRes(googleFont = montserrat, fontProvider = provider, weight = FontWeight.ExtraBold),
        GoogleFontRes(googleFont = montserrat, fontProvider = provider, weight = FontWeight.Bold),
        GoogleFontRes(googleFont = montserrat, fontProvider = provider, weight = FontWeight.SemiBold),
        GoogleFontRes(googleFont = montserrat, fontProvider = provider, weight = FontWeight.Medium),
        GoogleFontRes(googleFont = montserrat, fontProvider = provider, weight = FontWeight.Normal),
        GoogleFontRes(googleFont = montserrat, fontProvider = provider, weight = FontWeight.Light),
    )

internal val AuriyaFontFamily = MontserratFamily

private const val GoogleSansFlexRond = 100f

@OptIn(ExperimentalTextApi::class)
val GoogleSansRounded =
    FontFamily(
        Font(
            resId = R.font.gflex_variable,
            weight = FontWeight.Light,
            variationSettings =
                FontVariation.Settings(
                    FontVariation.weight(FontWeight.Light.weight),
                    FontVariation.Setting("ROND", GoogleSansFlexRond),
                ),
        ),
        Font(
            resId = R.font.gflex_variable,
            weight = FontWeight.Normal,
            variationSettings =
                FontVariation.Settings(
                    FontVariation.weight(FontWeight.Normal.weight),
                    FontVariation.Setting("ROND", GoogleSansFlexRond),
                ),
        ),
        Font(
            resId = R.font.gflex_variable,
            weight = FontWeight.Medium,
            variationSettings =
                FontVariation.Settings(
                    FontVariation.weight(FontWeight.Medium.weight),
                    FontVariation.Setting("ROND", GoogleSansFlexRond),
                ),
        ),
        Font(
            resId = R.font.gflex_variable,
            weight = FontWeight.SemiBold,
            variationSettings =
                FontVariation.Settings(
                    FontVariation.weight(FontWeight.SemiBold.weight),
                    FontVariation.Setting("ROND", GoogleSansFlexRond),
                ),
        ),
        Font(
            resId = R.font.gflex_variable,
            weight = FontWeight.Bold,
            variationSettings =
                FontVariation.Settings(
                    FontVariation.weight(FontWeight.Bold.weight),
                    FontVariation.Setting("ROND", GoogleSansFlexRond),
                ),
        ),
    )

val ExpTitleTypography =
    Typography(
        displayLarge =
            TextStyle(
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 36.sp,
                textGeometricTransform = TextGeometricTransform(scaleX = 1.15f),
                letterSpacing = (-0.02).em,
                lineHeight = 1.0.em,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
            ),
        displayMedium =
            TextStyle(
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 30.sp,
                textGeometricTransform = TextGeometricTransform(scaleX = 1.12f),
                letterSpacing = (-0.02).em,
                lineHeight = 1.0.em,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
            ),
        titleLarge =
            TextStyle(
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                textGeometricTransform = TextGeometricTransform(scaleX = 1.10f),
                letterSpacing = (-0.02).em,
                lineHeight = 1.0.em,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
            ),
        titleMedium =
            TextStyle(
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                textGeometricTransform = TextGeometricTransform(scaleX = 1.10f),
                letterSpacing = (-0.02).em,
                lineHeight = 1.0.em,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
            ),
    )

internal val AuriyaTypography =
    Typography(
        displayLarge =
            TextStyle(
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Bold,
                fontSize = 48.sp,
                lineHeight = 56.sp,
                letterSpacing = 0.sp,
            ),
        displayMedium =
            TextStyle(
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Bold,
                fontSize = 36.sp,
                lineHeight = 44.sp,
                letterSpacing = 0.sp,
            ),
        displaySmall =
            TextStyle(
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Normal,
                fontSize = 30.sp,
                lineHeight = 38.sp,
                letterSpacing = 0.sp,
            ),
        headlineLarge =
            TextStyle(
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.SemiBold,
                fontSize = 32.sp,
                lineHeight = 40.sp,
                letterSpacing = 0.sp,
            ),
        headlineMedium =
            TextStyle(
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.SemiBold,
                fontSize = 28.sp,
                lineHeight = 36.sp,
                letterSpacing = 0.sp,
            ),
        headlineSmall =
            TextStyle(
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp,
                lineHeight = 32.sp,
                letterSpacing = 0.sp,
            ),
        titleLarge =
            TextStyle(
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Normal,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                letterSpacing = 0.sp,
            ),
        titleMedium =
            TextStyle(
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Medium,
                fontSize = 18.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.15.sp,
            ),
        titleSmall =
            TextStyle(
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.1.sp,
            ),
        bodyLarge =
            TextStyle(
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.5.sp,
            ),
        bodyMedium =
            TextStyle(
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.25.sp,
            ),
        bodySmall =
            TextStyle(
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.4.sp,
            ),
        labelLarge =
            TextStyle(
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.1.sp,
            ),
        labelMedium =
            TextStyle(
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.5.sp,
            ),
        labelSmall =
            TextStyle(
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.5.sp,
            ),
    )
