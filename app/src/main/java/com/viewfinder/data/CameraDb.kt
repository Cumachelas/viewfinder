package com.viewfinder.data

import kotlin.math.hypot

/** Recorded sensor area of one camera mode, in mm (horizontal x vertical, before desqueeze). */
data class SensorMode(val name: String, val widthMm: Double, val heightMm: Double) {
    val diagonalMm get() = hypot(widthMm, heightMm)
}

data class CineCamera(val brand: String, val model: String, val modes: List<SensorMode>) {
    val id get() = "$brand $model"
    val displayName get() = "$brand $model"
}

/** Mode from photosite count and pitch in µm - keeps all modes of one sensor consistent. */
private fun px(name: String, w: Int, h: Int, pitchUm: Double) =
    SensorMode(name, w * pitchUm / 1000.0, h * pitchUm / 1000.0)

private fun mm(name: String, w: Double, h: Double) = SensorMode(name, w, h)

/**
 * Sources (Oct 2026): ARRI technical data (ALEXA 35, Mini, Mini LF, Classic), Sony spec sheets and
 * brochures (VENICE 2 imager table, FX5, FX6/FX3 readout notes), RED (V-RAPTOR [X] 8K VV
 * 40.96 x 21.60 mm), Canon (C300 III, C400/C80 effective areas), Kinefinity (MAVO S35 6K).
 * Pitches derived from published width / photosites.
 */
object CameraDb {
    private const val ALEV4 = 6.075
    private const val ALEV3 = 8.25
    private const val SONY12 = 35.6 / 4240 * 1000 // FX3 / FX6 / a7S III
    private const val SONY86 = 35.9 / 8640 * 1000 // VENICE 2 8.6K / BURANO
    private const val SONY6K = 36.0 / 6048 * 1000 // VENICE 6K
    private const val FX9 = 35.7 / 6008 * 1000
    private const val CANON6K = 36.0 / 6000 * 1000 // C400 / C80
    private const val KOMODO = 4.4
    private const val RAPTOR = 5.0

    private val venice86 = listOf(
        px("8.6K 3:2 OG", 8640, 5760, SONY86),
        px("8.6K 17:9", 8640, 4556, SONY86),
        px("8.2K 17:9", 8192, 4320, SONY86),
        px("8.2K 2.39:1", 8192, 3432, SONY86),
        px("7.6K 16:9", 7680, 4320, SONY86),
        px("5.8K S35 6:5", 5792, 4854, SONY86),
        px("5.8K S35 17:9", 5792, 3056, SONY86),
    )

    private val raptorVV = listOf(
        px("8K VV 17:9", 8192, 4320, RAPTOR),
        px("8K VV 2.4:1", 8192, 3456, RAPTOR),
        px("7K VV 17:9", 7168, 3780, RAPTOR),
        px("6K S35 17:9", 6144, 3240, RAPTOR),
    )

    private val komodo = listOf(
        px("6K 17:9", 6144, 3240, KOMODO),
        px("6K 2.4:1", 6144, 2560, KOMODO),
        px("5K 17:9", 5120, 2700, KOMODO),
        px("4K 17:9", 4096, 2160, KOMODO),
    )

    private val canon6k = listOf(
        px("FF 6K 17:9", 6000, 3164, CANON6K),
        mm("FF UHD", 33.8, 19.0),
        px("S35 4.3K 17:9", 4368, 2304, CANON6K),
        mm("S35 UHD", 24.6, 13.8),
    )

    val cameras: List<CineCamera> = listOf(
        CineCamera("ARRI", "Alexa 35", listOf(
            px("4.6K 3:2 OG", 4608, 3164, ALEV4),
            px("4.6K 16:9", 4608, 2592, ALEV4),
            px("4K 16:9", 4096, 2304, ALEV4),
            px("4K 2:1", 4096, 2048, ALEV4),
            px("3.8K 16:9 UHD", 3840, 2160, ALEV4),
            px("3.3K 6:5", 3328, 2790, ALEV4),
            px("3K 1:1", 3072, 3072, ALEV4),
            px("2.7K 8:9", 2743, 3086, ALEV4),
            px("2K 16:9 S16", 2048, 1152, ALEV4),
        )),
        CineCamera("ARRI", "Alexa Mini", listOf(
            px("3.4K OG 3:2", 3424, 2202, ALEV3),
            px("3.2K 16:9", 3200, 1800, ALEV3),
            px("2.8K 4:3", 2880, 2160, ALEV3),
            px("2.8K / HD 16:9", 2880, 1620, ALEV3),
            px("S16 HD", 1600, 900, ALEV3),
        )),
        CineCamera("ARRI", "Alexa Classic", listOf(
            px("2.8K / HD 16:9", 2880, 1620, ALEV3),
        )),
        CineCamera("ARRI", "Alexa Mini LF", listOf(
            px("4.5K LF 3:2 OG", 4448, 3096, ALEV3),
            px("4.5K LF 2.39:1", 4448, 1856, ALEV3),
            px("3.8K LF 16:9 UHD", 3840, 2160, ALEV3),
            px("2.8K LF 1:1", 2880, 2880, ALEV3),
        )),
        CineCamera("Sony", "a7 IV", listOf(
            mm("FF 4K 16:9 (7K oversampled)", 35.9, 20.19),
            mm("S35 4K 16:9 (1.5× crop)", 23.93, 13.46),
        )),
        CineCamera("Sony", "FX3", listOf(
            px("FF UHD (≤ 60p)", 4240, 2385, SONY12),
            px("FF UHD (120p, 1.1× crop)", 3840, 2160, SONY12),
            px("S35 HD", 2827, 1590, SONY12),
        )),
        CineCamera("Sony", "FX6", listOf(
            px("FF UHD (≤ 60p)", 4240, 2385, SONY12),
            px("FF 4K DCI", 4096, 2160, SONY12),
            px("FF UHD (> 60p)", 3840, 2160, SONY12),
            px("S35 HD", 2827, 1590, SONY12),
        )),
        CineCamera("Sony", "FX5", listOf(
            mm("5K 3:2 OG", 35.9, 24.0),
            mm("FF 4K 16:9", 35.9, 20.19),
        )),
        CineCamera("Sony", "FX9", listOf(
            px("FF 6K 17:9", 6008, 3168, FX9),
            px("FF 6K 16:9", 5632, 3168, FX9),
            mm("FF crop 5K (1.25×)", 28.56, 15.06),
            px("S35 4K 17:9", 4096, 2160, FX9),
        )),
        CineCamera("Sony", "Venice 2 8.6K", venice86),
        CineCamera("Sony", "Burano", listOf(
            px("8.6K 16:9", 8632, 4856, SONY86),
            px("8.6K 17:9", 8632, 4552, SONY86),
            px("5.8K S35 16:9", 5760, 3240, SONY86),
            px("5.8K S35 17:9", 5760, 3036, SONY86),
        )),
        CineCamera("Sony", "Venice / Venice 2 6K", listOf(
            px("6K 3:2 OG", 6048, 4032, SONY6K),
            px("6K 17:9", 6054, 3192, SONY6K),
            px("6K 2.39:1", 6048, 2534, SONY6K),
            px("5.7K 16:9", 5674, 3192, SONY6K),
            px("4K S35 6:5", 4096, 3432, SONY6K),
            px("4K S35 4:3", 4096, 3024, SONY6K),
            px("4K S35 17:9", 4096, 2160, SONY6K),
        )),
        CineCamera("RED", "Komodo 6K", komodo),
        CineCamera("RED", "Komodo-X", komodo),
        CineCamera("RED", "V-Raptor 8K VV", raptorVV),
        CineCamera("RED", "V-Raptor XL 8K VV", raptorVV),
        CineCamera("Kinefinity", "Mavo 6K (mk1)", listOf(
            mm("6K 3:2 OG", 24.0, 16.0),
            mm("6K 16:9", 24.0, 13.5),
            mm("6K 17:9", 24.0, 12.66),
        )),
        CineCamera("Canon", "C300 Mk III", listOf(
            mm("4K DCI", 26.2, 13.8),
            mm("UHD", 24.6, 13.8),
        )),
        CineCamera("Canon", "C400", canon6k + px("S16 2K (crop)", 2048, 1080, CANON6K)),
        CineCamera("Canon", "C80", canon6k),
        CineCamera("Generic", "Sensor formats", listOf(
            mm("Full Frame 3:2", 36.0, 24.0),
            mm("Super 35 4:3", 24.89, 18.66),
            mm("Super 35 16:9", 24.89, 14.0),
            mm("APS-C", 23.5, 15.6),
            mm("Micro Four Thirds", 17.3, 13.0),
            mm("Super 16", 12.52, 7.41),
        )),
    )

    fun find(id: String) = cameras.firstOrNull { it.id == id }
}
