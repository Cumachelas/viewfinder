@file:OptIn(ExperimentalCamera2Interop::class)

package com.viewfinder.camera

import android.hardware.camera2.CameraCharacteristics
import android.os.Build
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraInfo
import com.viewfinder.optics.Tangents
import kotlin.math.max
import kotlin.math.min

/**
 * One back-facing phone camera as reported by Camera2. Sizes are in sensor orientation (landscape).
 * activeWidthMm/activeHeightMm is the physical extent of the active pixel array, which is what
 * zoom ratio 1.0 shows.
 */
data class PhoneLens(
    val id: String,
    val focalMm: Double,
    val activeWidthMm: Double,
    val activeHeightMm: Double,
    val minZoom: Float,
    val maxZoom: Float,
) {
    /**
     * Field of view of a stream with the given aspect at the given zoom ratio.
     * The camera crops the active array to the stream aspect around its center, then zoom divides it.
     * calib corrects devices whose reported focal length or sensor size is slightly off.
     */
    fun tangents(streamAspect: Double, zoom: Double, calib: Double): Tangents {
        val activeAspect = activeWidthMm / activeHeightMm
        val w: Double
        val h: Double
        if (streamAspect < activeAspect) {
            h = activeHeightMm; w = h * streamAspect
        } else {
            w = activeWidthMm; h = w / streamAspect
        }
        val k = calib / (2 * focalMm * zoom)
        return Tangents(w * k, h * k)
    }

    companion object {
        @androidx.annotation.OptIn(markerClass = [ExperimentalCamera2Interop::class])
        fun from(info: CameraInfo): PhoneLens? {
            val c = Camera2CameraInfo.from(info)
            val physical = c.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) ?: return null
            val pixels = c.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE) ?: return null
            val active = c.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: return null
            val focal = c.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                ?.minOrNull()?.toDouble() ?: return null
            if (focal <= 0 || pixels.width <= 0 || pixels.height <= 0) return null

            var minZoom = 1f
            var maxZoom = c.getCameraCharacteristic(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f
            if (Build.VERSION.SDK_INT >= 30) {
                c.getCameraCharacteristic(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)?.let {
                    minZoom = it.lower; maxZoom = it.upper
                }
            }

            val w = physical.width.toDouble() * active.width() / pixels.width
            val h = physical.height.toDouble() * active.height() / pixels.height
            return PhoneLens(c.cameraId, focal, max(w, h), min(w, h), minZoom, maxZoom)
        }
    }
}

data class LensPlan(val lens: PhoneLens, val zoom: Float, val tooWide: Boolean)

/**
 * Picks the phone camera and zoom at which the target frame just fits inside the full preview
 * stream - the highest-resolution way to capture it. The screen then scales the preview so the
 * frame fills the free area, independent of this choice.
 * Prefers the narrowest camera that can still see the whole frame.
 */
fun planLens(
    lenses: List<PhoneLens>,
    target: Tangents,
    streamAspect: Double,
    calib: (String) -> Double,
    pinnedId: String,
): LensPlan? {
    val candidates = lenses.filter { it.id == pinnedId }.ifEmpty { lenses }
    if (candidates.isEmpty()) return null

    fun base(l: PhoneLens) = l.tangents(streamAspect, 1.0, calib(l.id))
    fun requiredZoom(l: PhoneLens): Double {
        val t = base(l)
        return min(t.h / target.h, t.v / target.v)
    }

    for (l in candidates.sortedBy { base(it).h }) {
        val z = requiredZoom(l)
        if (z >= l.minZoom - 1e-3) {
            return LensPlan(l, z.coerceIn(l.minZoom.toDouble(), l.maxZoom.toDouble()).toFloat(), false)
        }
    }
    val widest = candidates.maxBy { base(it).h / it.minZoom }
    return LensPlan(widest, widest.minZoom, true)
}
