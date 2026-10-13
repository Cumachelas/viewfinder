package com.viewfinder.optics

import com.viewfinder.data.SensorMode
import kotlin.math.atan

/**
 * A field of view as tan(half angle), horizontal and vertical.
 * Rectilinear images map linearly in tangent space, so ratios of tangents are ratios of image size.
 */
data class Tangents(val h: Double, val v: Double) {
    val aspect get() = h / v
    val hFovDeg get() = Math.toDegrees(2 * atan(h))
    val vFovDeg get() = Math.toDegrees(2 * atan(v))
    fun stretchH(k: Double) = Tangents(h * k, v)
}

object Optics {
    /** True field of view of the recorded area. An anamorphic lens widens the horizontal FOV. */
    fun recordedFrame(mode: SensorMode, focalMm: Double, lensSqueeze: Double) = Tangents(
        h = mode.widthMm * lensSqueeze / (2 * focalMm),
        v = mode.heightMm / (2 * focalMm),
    )

    /**
     * Horizontal stretch of the shown image: 1 when the desqueeze matches the lens. A spherical
     * lens with 2x desqueeze looks stretched (2), an anamorphic 2x lens without desqueeze looks
     * squeezed (0.5). Display geometry ("display tangents") is true tangents times this factor.
     */
    fun stretch(lensSqueeze: Double, desqueeze: Double) = desqueeze / lensSqueeze

    /** Largest centered area of the given aspect ratio inside a (display) frame - a frame guide. */
    fun guideFrame(frame: Tangents, aspect: Double): Tangents =
        if (aspect > frame.aspect) Tangents(frame.h, frame.h / aspect)
        else Tangents(frame.v * aspect, frame.v)

    /** Image circle over sensor diagonal; below 1 the lens does not cover the recorded area. */
    fun coverage(mode: SensorMode, imageCircleMm: Double?): Double? =
        imageCircleMm?.let { it / mode.diagonalMm }
}
