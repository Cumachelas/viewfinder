package com.viewfinder.ui

import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.roundToInt

fun fmt(v: Double, decimals: Int) = String.format(Locale.US, "%.${decimals}f", v)

/** 35 / 15.5 / 4.25 - no trailing zeros. */
fun fmtNum(v: Double) = fmt(v, 2).trimEnd('0').trimEnd('.')

fun fmtMm(v: Double) = fmtNum(v)

fun aspectLabel(a: Double) = "${fmt(a, 2)}:1"

fun squeezeLabel(s: Double) = "${fmtNum(s)}×"

private fun focalStep(f: Double) = when {
    f < 20 -> 0.5
    f < 100 -> 1.0
    else -> 5.0
}

/** Next focal length on the step grid in the given direction. */
fun stepFocal(f: Double, dir: Int): Double {
    val s = focalStep(if (dir < 0) f - 1e-6 else f)
    return if (dir > 0) floor(f / s + 1e-6) * s + s else ceil(f / s - 1e-6) * s - s
}

fun roundFocal(f: Double): Double {
    val s = focalStep(f)
    return (f / s).roundToInt() * s
}

/** Logarithmic slider position 0..1 for a focal range, so wide and long ends get equal room. */
fun focalToSlider(f: Double, min: Double, max: Double) = (ln(f / min) / ln(max / min)).toFloat().coerceIn(0f, 1f)

fun sliderToFocal(t: Float, min: Double, max: Double) = min * exp(t * ln(max / min))

/** Increments the last number in a shot label: "3" -> "4", "12B" -> "13B". */
fun nextShot(shot: String): String {
    val m = Regex("(\\d+)(?!.*\\d)").find(shot) ?: return shot
    val n = m.value.toInt() + 1
    return shot.replaceRange(m.range, n.toString())
}
