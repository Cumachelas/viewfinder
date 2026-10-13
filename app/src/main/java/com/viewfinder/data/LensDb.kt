package com.viewfinder.data

import java.util.Locale

/**
 * A prime set (primes) or a zoom / free range (zoomMin..zoomMax). squeeze is the lens's own
 * anamorphic factor. imageCircleMm is the covered image circle for the whole series, circles
 * overrides it per focal length; both null = unknown (no coverage check). maxStop is the widest
 * aperture (T-stop, or f-stop for stills lenses), stops overrides it per focal length.
 */
data class LensSeries(
    val brand: String,
    val name: String,
    val primes: List<Double> = emptyList(),
    val zoomMin: Double = 0.0,
    val zoomMax: Double = 0.0,
    val squeeze: Double = 1.0,
    val imageCircleMm: Double? = null,
    val circles: Map<Double, Double> = emptyMap(),
    val maxStop: Double? = null,
    val stops: Map<Double, Double> = emptyMap(),
    val fStop: Boolean = false,
) {
    val id get() = "$brand $name"
    val isPrimeSet get() = primes.isNotEmpty()
    val isGeneric get() = brand.isEmpty()
    val minFocal get() = if (isPrimeSet) primes.first() else zoomMin
    val maxFocal get() = if (isPrimeSet) primes.last() else zoomMax

    fun imageCircle(focal: Double): Double? = circles[focal] ?: imageCircleMm

    fun stop(focal: Double): Double? = stops[focal] ?: maxStop

    /** "T1.8" for one focal length, null if unknown. */
    fun stopLabel(focal: Double): String? = stop(focal)?.let(::formatStop)

    /** "T1.8", or "T1.4–T2.4" when the set's widest apertures differ; null if unknown. */
    val stopRange: String?
        get() {
            val all = (if (isPrimeSet) primes.mapNotNull(::stop) else listOfNotNull(maxStop)).distinct().sorted()
            return when {
                all.isEmpty() -> null
                all.size == 1 -> formatStop(all.first())
                else -> "${formatStop(all.first())}–${formatStop(all.last())}"
            }
        }

    private fun formatStop(v: Double): String {
        val n = String.format(Locale.US, "%.1f", v).removeSuffix(".0")
        return if (fStop) "f/$n" else "T$n"
    }

    /** Nearest valid focal length of this series. */
    fun snap(focal: Double): Double =
        if (isPrimeSet) primes.minBy { kotlin.math.abs(it - focal) } else focal.coerceIn(zoomMin, zoomMax)
}

private fun primes(
    brand: String, name: String, vararg f: Number,
    circle: Double? = null, squeeze: Double = 1.0, circles: Map<Double, Double> = emptyMap(),
    stop: Double? = null, stops: Map<Double, Double> = emptyMap(),
) = LensSeries(
    brand, name, primes = f.map { it.toDouble() }, squeeze = squeeze,
    imageCircleMm = circle, circles = circles, maxStop = stop, stops = stops,
)

private fun zoom(
    brand: String, name: String, min: Number, max: Number, circle: Double?,
    stop: Double?, squeeze: Double = 1.0, fStop: Boolean = false,
) = LensSeries(
    brand, name, zoomMin = min.toDouble(), zoomMax = max.toDouble(), squeeze = squeeze,
    imageCircleMm = circle, maxStop = stop, fStop = fStop,
)

private fun generic(name: String, squeeze: Double) =
    LensSeries("", name, zoomMin = 8.0, zoomMax = 300.0, squeeze = squeeze)

/**
 * Image circles and widest apertures (Oct 2026) from manufacturer specs where available, otherwise
 * rental/retail spec sheets (Keslow, Panavision, VMI, Adorama, Cine Lens Wiki). Where sources
 * disagreed the more conservative (slower) value was used: Cooke Panchro/i Classic FF 100/135/152,
 * DZOFILM Arles 135.
 */
object LensDb {
    val FREE = generic("Spherical", 1.0)

    private val superSpeedCircles = mapOf(18.0 to 32.5, 25.0 to 36.5, 35.0 to 37.0, 50.0 to 41.0, 65.0 to 41.0, 85.0 to 43.0)

    val series: List<LensSeries> = listOf(
        FREE,
        generic("Anamorphic 1.3×", 1.3),
        generic("Anamorphic 1.5×", 1.5),
        generic("Anamorphic 1.65×", 1.65),
        generic("Anamorphic 1.8×", 1.8),
        generic("Anamorphic 2×", 2.0),

        primes(
            "ARRI", "Signature Prime", 12, 15, 18, 21, 25, 29, 35, 40, 47, 58, 75, 95, 125, 150, 200, 280,
            circle = 46.31, stop = 1.8, stops = mapOf(200.0 to 2.5, 280.0 to 2.8),
        ),

        primes("Cooke", "SP3", 18, 25, 32, 50, 75, 100, circle = 43.3, stop = 2.4),
        primes(
            "Cooke", "Panchro/i Classic FF", 18, 21, 25, 27, 32, 40, 50, 65, 75, 100, 135, 152,
            circle = 43.3, stop = 2.2, stops = mapOf(65.0 to 2.4, 100.0 to 2.6, 135.0 to 2.8, 152.0 to 3.0),
        ),

        primes(
            "ZEISS", "Supreme Prime", 15, 18, 21, 25, 29, 35, 40, 50, 65, 85, 100, 135, 150, 200,
            circle = 46.2, stop = 1.5, stops = mapOf(15.0 to 1.8, 150.0 to 1.8, 200.0 to 2.2),
        ),
        primes("ZEISS", "Super Speed", 18, 25, 35, 50, 65, 85, circles = superSpeedCircles, stop = 1.3),
        primes("ZEISS", "B-Speed", 18, 25, 35, 50, 85, circles = superSpeedCircles - 65.0, stop = 1.4),
        primes("ZEISS", "Standard Speed", 16, 20, 24, 28, 32, 40, 50, 85, 100, 135, circle = 31.1, stop = 2.1),

        primes(
            "Tribe7", "Blackwing7", 17, 20.7, 23.7, 27, 37, 47, 57, 77, 107, 137,
            circle = 55.0, stop = 1.9,
            circles = mapOf(17.0 to 52.0, 20.7 to 50.0, 23.7 to 50.0, 27.0 to 55.0, 77.0 to 60.0, 137.0 to 62.0),
        ),

        primes(
            "Atlas", "Orion", 18, 21, 25, 28, 32, 40, 50, 65, 80, 100, 135, 200,
            circle = 31.0, squeeze = 2.0, circles = mapOf(200.0 to 46.3),
            stop = 2.0, stops = mapOf(135.0 to 2.2, 200.0 to 2.2),
        ),

        primes(
            "DZOFILM", "Vespid Prime", 12, 16, 21, 25, 35, 40, 50, 75, 90, 100, 125,
            circle = 46.5, stop = 2.1, stops = mapOf(12.0 to 2.8, 16.0 to 2.8, 90.0 to 2.8),
        ),
        primes(
            "DZOFILM", "Arles Prime", 14, 18, 21, 25, 35, 40, 50, 75, 100, 135, 180,
            circle = 46.5, stop = 1.4, stops = mapOf(14.0 to 1.9, 135.0 to 1.8, 180.0 to 2.4),
        ),
        zoom("DZOFILM", "Arles Zoom 25-75", 25, 75, 46.5, 2.6),
        zoom("DZOFILM", "Arles Zoom 65-180", 65, 180, 46.5, 2.6),
        zoom("DZOFILM", "Catta Ace 18-35", 18, 35, 46.5, 2.9),
        zoom("DZOFILM", "Catta Ace 35-80", 35, 80, 46.5, 2.9),
        zoom("DZOFILM", "Catta Ace 70-135", 70, 135, 46.5, 2.9),
        zoom("DZOFILM", "Pictor 12-25", 12, 25, 31.5, 2.8),
        zoom("DZOFILM", "Pictor 14-30", 14, 30, 31.5, 2.8),
        zoom("DZOFILM", "Pictor 20-55", 20, 55, 31.5, 2.8),
        zoom("DZOFILM", "Pictor 50-125", 50, 125, 31.5, 2.8),
        primes(
            "DZOFILM", "Pavo 2× Anamorphic", 28, 32, 40, 55, 65, 75, 100, 135, 180,
            circle = 31.5, squeeze = 2.0, stop = 2.1, stops = mapOf(65.0 to 2.8, 135.0 to 2.5, 180.0 to 2.8),
        ),
        primes("DZOFILM", "Arcana 1.5× Anamorphic", 32, 45, 75, circle = 43.3, squeeze = 1.5, stop = 2.1),

        zoom("Sigma", "18-35 Art", 18, 35, 28.4, 1.8, fStop = true),
        zoom("Fujinon", "MK 18-55 (E)", 18, 55, 28.5, 2.9),
        zoom("Fujinon", "MK 50-135 (E)", 50, 135, 28.5, 2.9),
    )

    val squeezeFactors = listOf(1.0, 1.25, 1.3, 1.33, 1.5, 1.65, 1.8, 2.0)

    fun find(id: String) = series.firstOrNull { it.id == id }
}
