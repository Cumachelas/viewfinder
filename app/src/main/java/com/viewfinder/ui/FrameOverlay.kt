package com.viewfinder.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.viewfinder.optics.Tangents

/** A frame guide in display tangents, with its label and color. */
data class Guide(val label: String, val frame: Tangents, val color: Color)

/** Size of the whole preview stream drawn FIT_CENTER into a view of size (vw, vh). */
fun streamSize(vw: Float, vh: Float, streamAspect: Double): Pair<Float, Float> {
    val sa = streamAspect.toFloat()
    return if (vw / vh >= sa) vh * sa to vh else vw to vw / sa
}

/**
 * Unscaled on-screen size of display field of view t when the whole stream shows the true field
 * of view phone. (The preview is stretched horizontally by the same factor that turns true into
 * display tangents, so the factor cancels out here.)
 */
fun screenSize(vw: Float, vh: Float, streamAspect: Double, phone: Tangents, t: Tangents): Pair<Float, Float> {
    val (sw, sh) = streamSize(vw, vh, streamAspect)
    return (sw * t.h / phone.h).toFloat() to (sh * t.v / phone.v).toFloat()
}

/**
 * Factor by which preview and overlay are scaled (around the view center) so the shown frame
 * exactly fills the free area (fitW x fitH of the view). The phone zoom only decides resolution.
 */
fun previewScale(vw: Float, vh: Float, fitW: Double, fitH: Double, streamAspect: Double, phone: Tangents, frame: Tangents): Float {
    if (vw <= 0f || vh <= 0f) return 1f
    val (w, h) = screenSize(vw, vh, streamAspect, phone, frame)
    return minOf((vw * fitW / w).toFloat(), (vh * fitH / h).toFloat())
}

/** Hatch pattern for frame areas the phone camera cannot see. */
fun DrawScope.hatch(area: Rect, fill: Color, line: Color, step: Float, width: Float) {
    drawRect(fill, area.topLeft, area.size)
    var x = area.left - area.height
    while (x < area.right) {
        drawLine(line, Offset(x, area.bottom), Offset(x + area.height, area.top), width)
        x += step
    }
}

/**
 * Draws everything over the preview: lens coverage vignette, frame guides, the mat around the
 * shown frame with its labels, and hatching where the phone sees less than the frame.
 * frame and sensor are display tangents; sensor is the full recorded area (equal to frame unless
 * cropping). circle is the lens image circle diameter as a fraction of the sensor (w, h).
 */
@Composable
fun FrameOverlay(
    frame: Tangents,
    sensor: Tangents,
    guides: List<Guide>,
    cropGuide: Guide?,
    phone: Tangents,
    stretch: Double,
    streamAspect: Double,
    scale: Float,
    circle: Pair<Double, Double>?,
    topLeftLabel: String,
    topRightLabel: String,
    warning: String?,
    offset: Offset = Offset.Zero,
) {
    val measurer = rememberTextMeasurer()
    val ink = Ink
    val mat = Mat
    val warn = Warn
    val hatchFill = HatchFill
    val hatchLine = HatchLine
    val matStyle = TextStyle(color = ink, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, fontFamily = Inter, letterSpacing = 0.8.sp)
    val warnStyle = TextStyle(color = warn, fontSize = 10.sp, fontWeight = FontWeight.Medium, fontFamily = Inter)

    Canvas(Modifier.fillMaxSize()) {
        val vw = size.width
        val vh = size.height
        if (vw <= 0f || vh <= 0f) return@Canvas

        // Optical axis on screen: the view center, moved to the middle of the free area.
        val cx = vw / 2 + offset.x
        val cy = vh / 2 + offset.y
        fun centered(w: Float, h: Float) = Rect(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)

        fun rectOf(t: Tangents): Rect {
            val (w0, h0) = screenSize(vw, vh, streamAspect, phone, t)
            return centered(w0 * scale, h0 * scale)
        }

        val f = rectOf(frame)

        // Part of the frame outside the phone's whole stream.
        val (sw0, sh0) = streamSize(vw, vh, streamAspect)
        val seenW = (sw0 * scale * stretch).toFloat()
        val seenH = sh0 * scale
        if (f.width > seenW + 1f || f.height > seenH + 1f) {
            val seen = centered(seenW, seenH)
            val missing = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(f)
                addRect(seen)
            }
            clipPath(missing) { hatch(f, hatchFill, hatchLine, 10.dp.toPx(), 1.dp.toPx()) }
        }

        // Lens coverage: darken outside the image circle (an ellipse when desqueezed).
        if (circle != null) {
            val s = rectOf(sensor)
            val rx = (s.width * circle.first / 2).toFloat()
            val ry = (s.height * circle.second / 2).toFloat()
            clipRect(f.left, f.top, f.right, f.bottom) {
                // In the x-scaled space the ellipse is a circle of radius ry; the frame is wider by ry/rx.
                val coverW = f.width * (ry / rx) + 2f
                scale(rx / ry, 1f, pivot = f.center) {
                    drawRect(
                        Brush.radialGradient(
                            0f to Color.Transparent,
                            0.86f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.92f),
                            center = f.center,
                            radius = ry,
                        ),
                        topLeft = Offset(f.center.x - coverW / 2, f.top),
                        size = Size(coverW, f.height),
                    )
                }
            }
        }

        val pad = 6.dp.toPx()
        val dash = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 6.dp.toPx()))
        clipRect(f.left, f.top, f.right, f.bottom) {
            val lw = 1.25.dp.toPx()
            guides.forEach { g ->
                val r = rectOf(g.frame)
                // Only edges that lie inside the frame; a guide edge on the frame edge adds nothing.
                if (r.left - f.left > lw) {
                    drawLine(g.color, Offset(r.left, r.top), Offset(r.left, r.bottom), lw, pathEffect = dash)
                    drawLine(g.color, Offset(r.right, r.top), Offset(r.right, r.bottom), lw, pathEffect = dash)
                }
                if (r.top - f.top > lw) {
                    drawLine(g.color, Offset(r.left, r.top), Offset(r.right, r.top), lw, pathEffect = dash)
                    drawLine(g.color, Offset(r.left, r.bottom), Offset(r.right, r.bottom), lw, pathEffect = dash)
                }
                drawText(
                    measurer, g.label, Offset(r.left + pad, r.top + pad / 2),
                    TextStyle(color = g.color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, fontFamily = Inter),
                )
            }
            cropGuide?.let { g ->
                drawText(
                    measurer, "${g.label}  CROP", Offset(f.left + pad, f.top + pad / 2),
                    TextStyle(color = g.color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, fontFamily = Inter, letterSpacing = 0.6.sp),
                )
            }
        }

        val c = f.center
        val l = 9.dp.toPx()
        val cw = 1.dp.toPx()
        drawLine(Color.White, Offset(c.x - l, c.y), Offset(c.x + l, c.y), cw)
        drawLine(Color.White, Offset(c.x, c.y - l), Offset(c.x, c.y + l), cw)

        val matPath = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(0f, 0f, vw, vh))
            addRect(f)
        }
        drawPath(matPath, mat)
        drawRect(ink, f.topLeft, f.size, style = Stroke(1.dp.toPx()))

        val gap = 3.dp.toPx()
        val left = measurer.measure(topLeftLabel, matStyle)
        drawText(left, topLeft = Offset(f.left, f.top - gap - left.size.height))
        val right = measurer.measure(topRightLabel, matStyle)
        drawText(right, topLeft = Offset(f.right - right.size.width, f.top - gap - right.size.height))
        warning?.let { drawText(measurer, it, Offset(f.left, f.bottom + gap), warnStyle) }
    }
}
