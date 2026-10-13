package com.viewfinder.capture

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.provider.MediaStore
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Export caption. Top row (large): camera | location, scene, shot | lens and focal length.
 * Bottom row (small): project, director, DP | format details and date. Notes in italics below.
 */
data class Caption(
    val camera: String,
    val slate: String,
    val lens: String,
    val credits: String,
    val details: String,
    val notes: String,
)

/** A frame guide as a centered fraction (w, h) of the exported frame. */
data class StillGuide(val label: String, val fracW: Double, val fracH: Double, val color: Int)

object Still {
    private const val MAX_WIDTH = 4096.0

    /**
     * Crops the centered frame from src and sets it in a mat with the caption below.
     * fracW/fracH: frame size as a fraction of src (true field of view; may exceed 1 when the
     * phone sees less than the frame - that part is hatched). stretch: horizontal desqueeze
     * mismatch (see Optics.stretch). circle: lens image circle diameter as a fraction of the
     * frame (w, h), drawn as a vignette; null = none.
     */
    fun compose(
        src: Bitmap,
        fracW: Double,
        fracH: Double,
        stretch: Double,
        guides: List<StillGuide>,
        circle: Pair<Double, Double>?,
        caption: Caption,
        font: Typeface?,
        dark: Boolean,
    ): Bitmap {
        val frameW = src.width * fracW * stretch
        val frameH = src.height * fracH
        val scale = min(1.0, MAX_WIDTH / frameW)
        val imgW = (frameW * scale).roundToInt().coerceAtLeast(1)
        val imgH = (frameH * scale).roundToInt().coerceAtLeast(1)
        val mat = (imgW * 0.03).roundToInt()
        val baseBand = max(imgH * 0.13, imgW * 0.055)
        val band = (if (caption.notes.isNotBlank()) baseBand * 1.3 else baseBand).roundToInt()

        val matColor = if (dark) 0xFF0B0B0B.toInt() else 0xFFFFFFFF.toInt()
        val ink = if (dark) 0xFFF2F2F2.toInt() else 0xFF0A0A0A.toInt()
        val muted = if (dark) 0xFF8C8C8C.toInt() else 0xFF6E6E6E.toInt()

        val out = Bitmap.createBitmap(imgW + 2 * mat, imgH + mat + band, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(matColor)

        c.save()
        c.translate(mat.toFloat(), mat.toFloat())
        c.clipRect(0, 0, imgW, imgH)

        // Where the phone image lands inside the frame; the rest is hatched like in the app.
        val srcW = (src.width * scale * stretch).toFloat()
        val srcH = (src.height * scale).toFloat()
        val srcRect = RectF((imgW - srcW) / 2, (imgH - srcH) / 2, (imgW + srcW) / 2, (imgH + srcH) / 2)
        if (srcRect.left > 0.5f || srcRect.top > 0.5f) hatch(c, imgW, imgH, srcRect, dark)
        c.drawBitmap(src, null, srcRect, Paint(Paint.FILTER_BITMAP_FLAG))

        circle?.let { vignette(c, imgW, imgH, it) }
        drawGuides(c, imgW, imgH, guides, font)
        c.restore()

        val left = mat.toFloat()
        val right = (mat + imgW).toFloat()
        val gap = baseBand.toFloat() * 0.4f
        val big = textPaint(baseBand.toFloat() * 0.24f, font, 600, ink, 0f)
        val small = textPaint(baseBand.toFloat() * 0.17f, font, 400, muted, 0.01f)

        // Top row: three parts in one size, shrunk together until they fit side by side.
        val parts = listOf(caption.camera, caption.slate, caption.lens).filter { it.isNotBlank() }
        while (big.textSize > 8f && parts.sumOf { big.measureText(it).toDouble() } + gap * (parts.size - 1) > imgW) {
            big.textSize *= 0.95f
        }
        val top = (imgH + mat).toFloat()
        val y1 = top + baseBand.toFloat() * 0.42f
        val y2 = top + baseBand.toFloat() * 0.70f
        c.drawText(caption.camera, left, y1, big)
        c.drawText(caption.lens, right, y1, Paint(big).apply { textAlign = Paint.Align.RIGHT })
        if (caption.slate.isNotBlank()) {
            // Centered between camera and lens, so it never collides with either.
            val from = left + big.measureText(caption.camera) + gap
            val to = right - big.measureText(caption.lens) - gap
            val half = big.measureText(caption.slate) / 2
            val mid = (from + to) / 2
            val x = if (from + half <= to - half) mid.coerceIn(from + half, to - half) else mid
            c.drawText(caption.slate, x, y1, Paint(big).apply { textAlign = Paint.Align.CENTER })
        }

        val smallR = Paint(small).apply { textAlign = Paint.Align.RIGHT }
        fit(smallR, caption.details, imgW * 0.6f)
        fit(small, caption.credits, imgW - smallR.measureText(caption.details) - gap)
        c.drawText(caption.credits, left, y2, small)
        c.drawText(caption.details, right, y2, smallR)

        if (caption.notes.isNotBlank()) {
            val note = textPaint(baseBand.toFloat() * 0.15f, font, 400, muted, 0.01f).apply { textSkewX = -0.22f }
            val text = caption.notes.replace('\n', ' ')
            fit(note, text, imgW.toFloat())
            c.drawText(text, left, top + baseBand.toFloat() * 1.0f, note)
        }
        return out
    }

    fun save(context: Context, bmp: Bitmap, baseName: String): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$baseName.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/viewfinder")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        val written = try {
            resolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) } == true
        } catch (e: Exception) {
            false
        }
        if (!written) {
            // Don't leave a half-written, pending entry behind.
            try {
                resolver.delete(uri, null, null)
            } catch (e: Exception) {
                // Nothing more to do.
            }
            return null
        }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }

    private fun hatch(c: Canvas, w: Int, h: Int, seen: RectF, dark: Boolean) {
        val unit = max(w, h) / 1000f
        c.save()
        val area = Path().apply {
            fillType = Path.FillType.EVEN_ODD
            addRect(0f, 0f, w.toFloat(), h.toFloat(), Path.Direction.CW)
            addRect(seen, Path.Direction.CW)
        }
        c.clipPath(area)
        c.drawColor(if (dark) 0xFF161616.toInt() else 0xFFEDEDED.toInt())
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (dark) 0xFF3A3A3A.toInt() else 0xFFBDBDBD.toInt()
            strokeWidth = max(1f, unit * 1.2f)
        }
        val step = unit * 12
        var x = -h.toFloat()
        while (x < w) {
            c.drawLine(x, h.toFloat(), x + h, 0f, line)
            x += step
        }
        c.restore()
    }

    private fun vignette(c: Canvas, w: Int, h: Int, circle: Pair<Double, Double>) {
        val rx = (w * circle.first / 2).toFloat()
        val ry = (h * circle.second / 2).toFloat()
        val shader = RadialGradient(
            0f, 0f, 1f,
            intArrayOf(0x00000000, 0x00000000, 0xEB000000.toInt()),
            floatArrayOf(0f, 0.86f, 1f),
            Shader.TileMode.CLAMP,
        ).apply {
            setLocalMatrix(Matrix().apply {
                setScale(rx, ry)
                postTranslate(w / 2f, h / 2f)
            })
        }
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply { this.shader = shader })
    }

    private fun drawGuides(c: Canvas, w: Int, h: Int, guides: List<StillGuide>, font: Typeface?) {
        if (guides.isEmpty()) return
        val unit = max(w, h) / 1000f
        guides.forEach { g ->
            val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = max(2f, unit * 1.6f)
                color = g.color
                pathEffect = DashPathEffect(floatArrayOf(unit * 14, unit * 8), 0f)
            }
            val label = textPaint(max(12f, unit * 14), font, 600, g.color, 0.02f)
            val gw = (w * g.fracW).toFloat()
            val gh = (h * g.fracH).toFloat()
            val left = (w - gw) / 2
            val top = (h - gh) / 2
            // Only edges inside the frame; edges on the frame border add nothing.
            if (left > line.strokeWidth) {
                c.drawLine(left, top, left, top + gh, line)
                c.drawLine(left + gw, top, left + gw, top + gh, line)
            }
            if (top > line.strokeWidth) {
                c.drawLine(left, top, left + gw, top, line)
                c.drawLine(left, top + gh, left + gw, top + gh, line)
            }
            c.drawText(g.label, left + unit * 10, top + unit * 10 - label.ascent(), label)
        }
    }

    /** Text paint in the bundled variable font at the given weight (falls back to system sans). */
    private fun textPaint(size: Float, font: Typeface?, weight: Int, color: Int, spacing: Float) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            typeface = font ?: Typeface.SANS_SERIF
            if (font != null) fontVariationSettings = "'wght' $weight"
            this.color = color
            letterSpacing = spacing
        }

    private fun fit(p: Paint, text: String, maxWidth: Float) {
        while (p.textSize > 8f && p.measureText(text) > maxWidth) p.textSize *= 0.95f
    }
}
