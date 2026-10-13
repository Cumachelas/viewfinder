package com.viewfinder.data

import java.util.Locale

/** A frame guide aspect ratio with its conventional name. */
data class AspectFormat(val label: String, val ratio: Double, val note: String)

object Formats {
    val guides = listOf(
        AspectFormat("4:3", 4.0 / 3, "TV / silent"),
        AspectFormat("1.37:1", 1.375, "Academy"),
        AspectFormat("1.43:1", 1.43, "IMAX 70 mm"),
        AspectFormat("3:2", 3.0 / 2, "Full frame"),
        AspectFormat("1.66:1", 1.66, "European flat"),
        AspectFormat("16:9", 16.0 / 9, "HD / UHD"),
        AspectFormat("1.85:1", 1.85, "Flat"),
        AspectFormat("1.90:1", 256.0 / 135, "DCI / IMAX digital"),
        AspectFormat("2:1", 2.0, "Univisium"),
        AspectFormat("2.20:1", 2.2, "70 mm"),
        AspectFormat("2.35:1", 2.35, "Scope (classic)"),
        AspectFormat("2.39:1", 2.39, "Scope (DCI)"),
        AspectFormat("2.40:1", 2.4, "Scope (home video)"),
        AspectFormat("2.76:1", 2.76, "Ultra Panavision 70"),
    )

    /** Conventional name of a ratio ("16:9", "2.39:1"), or the plain number for anything else. */
    fun label(ratio: Double): String =
        guides.firstOrNull { kotlin.math.abs(it.ratio - ratio) < 1e-6 }?.label
            ?: String.format(Locale.US, "%.2f:1", ratio)
}
