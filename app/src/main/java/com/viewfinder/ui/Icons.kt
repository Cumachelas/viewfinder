package com.viewfinder.ui

import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Thin line icons on a 24 grid (Lucide-style geometry), tinted at the call site. */
object Icons {
    private fun icon(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach {
                addPath(
                    pathData = addPathNodes(it),
                    stroke = SolidColor(androidx.compose.ui.graphics.Color.Black),
                    strokeLineWidth = 1.6f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    val Camera = icon(
        "camera",
        "M4 6h10a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2z",
        "M22 8l-6 4 6 4V8z",
    )

    val Aperture = icon(
        "aperture",
        "M2 12a10 10 0 1 0 20 0a10 10 0 1 0-20 0",
        "M14.31 8l5.74 9.94",
        "M9.69 8h11.48",
        "M7.38 12l5.74-9.94",
        "M9.69 16L3.95 6.06",
        "M14.31 16H2.83",
        "M16.62 12l-5.74 9.94",
    )

    val Clapper = icon(
        "clapper",
        "M20.2 6L3 11l-0.9-2.4c-0.3-1.1 0.3-2.2 1.3-2.5l13.5-4c1.1-0.3 2.2 0.3 2.5 1.3z",
        "M6.2 5.3l3.1 3.9",
        "M12.4 3.4l3.1 4",
        "M3 11h18v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z",
    )

    val Sliders = icon(
        "sliders",
        "M21 4h-7", "M10 4H3", "M21 12h-9", "M8 12H3", "M21 20h-5", "M12 20H3",
        "M14 2v4", "M8 10v4", "M16 18v4",
    )

    val Frame = icon("frame", "M22 6H2", "M22 18H2", "M6 2v20", "M18 2v20")

    val Squeeze = icon("squeeze", "M18 8l4 4-4 4", "M2 12h20", "M6 8l-4 4 4 4")

    val Grid = icon(
        "grid",
        "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z",
        "M3 9h18", "M3 15h18", "M9 3v18", "M15 3v18",
    )

    val Crop = icon("crop", "M6 2v14a2 2 0 0 0 2 2h14", "M18 22V8a2 2 0 0 0-2-2H2")

    val Folder = icon(
        "folder",
        "M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-0.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2z",
    )

    val Image = icon(
        "image",
        "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z",
        "M7 9a2 2 0 1 0 4 0a2 2 0 1 0-4 0",
        "M21 15l-3.09-3.09a2 2 0 0 0-2.82 0L6 21",
    )

    val Minus = icon("minus", "M5 12h14")
    val Plus = icon("plus", "M5 12h14", "M12 5v14")
    val Check = icon("check", "M20 6L9 17l-5-5")
    val Close = icon("close", "M18 6L6 18", "M6 6l12 12")
}
