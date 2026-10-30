package io.github.aedev.flow.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt

private const val TONE_STEP = 4.0
private const val LIGHTEST_TONE = 98.0
private const val DARKEST_TONE = 2.0
private const val MID_TONE = 50.0

internal fun Color.tone(): Double {
    val lab = DoubleArray(3)
    ColorUtils.colorToLAB(toArgb(), lab)
    return lab[0]
}

internal fun Color.withTone(tone: Double): Color {
    val lab = DoubleArray(3)
    ColorUtils.colorToLAB(toArgb(), lab)
    return Color(ColorUtils.LABToColor(tone.coerceIn(0.0, 100.0), lab[1], lab[2]))
}

/** [withTone], with the colourfulness held to at most [maxChroma] (CIELAB a*b* distance). */
internal fun Color.withTone(
    tone: Double,
    maxChroma: Double,
): Color {
    val lab = DoubleArray(3)
    ColorUtils.colorToLAB(toArgb(), lab)
    val chroma = hypot(lab[1], lab[2])
    val scale = if (chroma > maxChroma) maxChroma / chroma else 1.0
    return Color(ColorUtils.LABToColor(tone.coerceIn(0.0, 100.0), lab[1] * scale, lab[2] * scale))
}

/** WCAG contrast ratio between two opaque colors. */
internal fun contrastRatio(
    a: Color,
    b: Color,
): Float {
    val la = a.luminance() + 0.05f
    val lb = b.luminance() + 0.05f
    return maxOf(la, lb) / minOf(la, lb)
}

/** Moves [color] away from [surface] in tone until it clears [minRatio]. */
internal fun ensureContrastOn(
    color: Color,
    surface: Color,
    minRatio: Float,
): Color {
    val lighten = surface.tone() < MID_TONE
    var candidate = color
    var tone = candidate.tone()
    while (contrastRatio(candidate, surface) < minRatio && if (lighten) tone < LIGHTEST_TONE else tone > DARKEST_TONE) {
        tone += if (lighten) TONE_STEP else -TONE_STEP
        candidate = candidate.withTone(tone)
    }
    return candidate
}

/**
 * The CIELAB half of `androidx.core.graphics.ColorUtils`, which is Android-only. Same constants and
 * arithmetic as androidx core's ColorUtils.java, so tones match upstream exactly.
 */
@Suppress("FunctionName", "LocalVariableName")
private object ColorUtils {
    private const val XYZ_WHITE_REFERENCE_X = 95.047
    private const val XYZ_WHITE_REFERENCE_Y = 100.0
    private const val XYZ_WHITE_REFERENCE_Z = 108.883
    private const val XYZ_EPSILON = 0.008856
    private const val XYZ_KAPPA = 903.3

    fun colorToLAB(
        color: Int,
        outLab: DoubleArray,
    ) {
        val xyz = DoubleArray(3)
        RGBToXYZ((color shr 16) and 0xFF, (color shr 8) and 0xFF, color and 0xFF, xyz)
        XYZToLAB(xyz[0], xyz[1], xyz[2], outLab)
    }

    fun LABToColor(
        l: Double,
        a: Double,
        b: Double,
    ): Int {
        val xyz = DoubleArray(3)
        LABToXYZ(l, a, b, xyz)
        return XYZToColor(xyz[0], xyz[1], xyz[2])
    }

    private fun RGBToXYZ(
        r: Int,
        g: Int,
        b: Int,
        outXyz: DoubleArray,
    ) {
        val sr = linearize(r / 255.0)
        val sg = linearize(g / 255.0)
        val sb = linearize(b / 255.0)
        outXyz[0] = 100 * (sr * 0.4124 + sg * 0.3576 + sb * 0.1805)
        outXyz[1] = 100 * (sr * 0.2126 + sg * 0.7152 + sb * 0.0722)
        outXyz[2] = 100 * (sr * 0.0193 + sg * 0.1192 + sb * 0.9505)
    }

    private fun linearize(s: Double): Double = if (s < 0.04045) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)

    private fun XYZToLAB(
        x: Double,
        y: Double,
        z: Double,
        outLab: DoubleArray,
    ) {
        val px = pivotXyzComponent(x / XYZ_WHITE_REFERENCE_X)
        val py = pivotXyzComponent(y / XYZ_WHITE_REFERENCE_Y)
        val pz = pivotXyzComponent(z / XYZ_WHITE_REFERENCE_Z)
        outLab[0] = maxOf(0.0, 116 * py - 16)
        outLab[1] = 500 * (px - py)
        outLab[2] = 200 * (py - pz)
    }

    private fun LABToXYZ(
        l: Double,
        a: Double,
        b: Double,
        outXyz: DoubleArray,
    ) {
        val fy = (l + 16) / 116
        val fx = a / 500 + fy
        val fz = fy - b / 200
        var tmp = fx.pow(3)
        val xr = if (tmp > XYZ_EPSILON) tmp else (116 * fx - 16) / XYZ_KAPPA
        val yr = if (l > XYZ_KAPPA * XYZ_EPSILON) fy.pow(3) else l / XYZ_KAPPA
        tmp = fz.pow(3)
        val zr = if (tmp > XYZ_EPSILON) tmp else (116 * fz - 16) / XYZ_KAPPA
        outXyz[0] = xr * XYZ_WHITE_REFERENCE_X
        outXyz[1] = yr * XYZ_WHITE_REFERENCE_Y
        outXyz[2] = zr * XYZ_WHITE_REFERENCE_Z
    }

    private fun XYZToColor(
        x: Double,
        y: Double,
        z: Double,
    ): Int {
        val r = delinearize((x * 3.2406 + y * -1.5372 + z * -0.4986) / 100)
        val g = delinearize((x * -0.9689 + y * 1.8758 + z * 0.0415) / 100)
        val b = delinearize((x * 0.0557 + y * -0.2040 + z * 1.0570) / 100)
        // Color.rgb: opaque, each channel rounded like Java's Math.round and clamped.
        return (0xFF shl 24) or (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
    }

    private fun delinearize(c: Double): Double = if (c > 0.0031308) 1.055 * c.pow(1 / 2.4) - 0.055 else 12.92 * c

    private fun channel(c: Double): Int = (c * 255).roundToInt().coerceIn(0, 255)

    private fun pivotXyzComponent(component: Double): Double =
        if (component > XYZ_EPSILON) component.pow(1 / 3.0) else (XYZ_KAPPA * component + 16) / 116
}
