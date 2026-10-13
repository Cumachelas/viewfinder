package com.viewfinder

import com.viewfinder.camera.PhoneLens
import com.viewfinder.camera.planLens
import com.viewfinder.data.CameraDb
import com.viewfinder.data.Formats
import com.viewfinder.data.LensDb
import com.viewfinder.data.SensorMode
import com.viewfinder.optics.Optics
import com.viewfinder.optics.Tangents
import com.viewfinder.ui.nextShot
import com.viewfinder.ui.roundFocal
import com.viewfinder.ui.stepFocal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraDataTest {
    private fun mode(camera: String, name: String): SensorMode =
        CameraDb.find(camera)!!.modes.first { it.name == name }

    private fun assertSize(m: SensorMode, w: Double, h: Double, tol: Double = 0.02) {
        assertEquals("${m.name} width", w, m.widthMm, tol)
        assertEquals("${m.name} height", h, m.heightMm, tol)
    }

    @Test fun publishedSensorSizes() {
        // Manufacturer-published active areas.
        assertSize(mode("ARRI Alexa 35", "4.6K 3:2 OG"), 27.99, 19.22)
        assertSize(mode("ARRI Alexa 35", "4K 16:9"), 24.88, 14.00)
        assertSize(mode("ARRI Alexa 35", "3.3K 6:5"), 20.22, 16.95)
        assertSize(mode("ARRI Alexa Mini", "3.4K OG 3:2"), 28.25, 18.17)
        assertSize(mode("ARRI Alexa Mini", "2.8K 4:3"), 23.76, 17.82)
        assertSize(mode("ARRI Alexa Mini", "2.8K / HD 16:9"), 23.76, 13.37)
        assertSize(mode("ARRI Alexa Mini LF", "4.5K LF 3:2 OG"), 36.70, 25.54)
        assertSize(mode("ARRI Alexa Mini LF", "4.5K LF 2.39:1"), 36.70, 15.31)
        assertSize(mode("ARRI Alexa Mini LF", "3.8K LF 16:9 UHD"), 31.68, 17.82)
        assertSize(mode("RED V-Raptor 8K VV", "8K VV 17:9"), 40.96, 21.60)
        assertSize(mode("RED Komodo 6K", "6K 17:9"), 27.03, 14.26)
        assertSize(mode("Sony Venice 2 8.6K", "8.6K 3:2 OG"), 35.9, 24.0, tol = 0.1)
        assertSize(mode("Sony Venice 2 8.6K", "8.2K 17:9"), 34.1, 18.0, tol = 0.1)
        assertSize(mode("Sony Venice 2 8.6K", "5.8K S35 6:5"), 24.1, 20.2, tol = 0.1)
        assertSize(mode("Sony Venice / Venice 2 6K", "6K 3:2 OG"), 36.0, 24.0, tol = 0.05)
        assertSize(mode("Canon C400", "FF 6K 17:9"), 36.0, 19.0, tol = 0.05)
        assertSize(mode("Canon C80", "S35 4.3K 17:9"), 26.2, 13.8, tol = 0.05)
        assertSize(mode("Canon C300 Mk III", "4K DCI"), 26.2, 13.8)
        assertSize(mode("Sony FX6", "FF UHD (≤ 60p)"), 35.6, 20.03)
    }

    @Test fun camerasAreConsistent() {
        assertEquals("unique camera ids", CameraDb.cameras.size, CameraDb.cameras.map { it.id }.toSet().size)
        CameraDb.cameras.forEach { cam ->
            assertTrue("${cam.id} has modes", cam.modes.isNotEmpty())
            assertEquals("${cam.id} unique mode names", cam.modes.size, cam.modes.map { it.name }.toSet().size)
            cam.modes.forEach { m ->
                assertTrue("${cam.id} ${m.name} plausible size", m.widthMm in 5.0..60.0 && m.heightMm in 5.0..60.0)
                assertFalse("${cam.id} ${m.name} says Open Gate", m.name.contains("Open Gate"))
            }
        }
    }
}

class LensDataTest {
    @Test fun lensesAreConsistent() {
        assertEquals("unique lens ids", LensDb.series.size, LensDb.series.map { it.id }.toSet().size)
        LensDb.series.forEach { l ->
            if (l.isPrimeSet) {
                assertEquals("${l.id} primes sorted and unique", l.primes.sorted().distinct(), l.primes)
                assertTrue("${l.id} circle overrides are real focal lengths", l.circles.keys.all { it in l.primes })
                assertTrue("${l.id} stop overrides are real focal lengths", l.stops.keys.all { it in l.primes })
            } else {
                assertTrue("${l.id} zoom range", l.zoomMin > 0 && l.zoomMax > l.zoomMin)
            }
            assertTrue("${l.id} squeeze", l.squeeze in 1.0..2.0)
            (l.circles.values + listOfNotNull(l.imageCircleMm)).forEach { assertTrue("${l.id} circle $it", it in 20.0..70.0) }
            (l.stops.values + listOfNotNull(l.maxStop)).forEach { assertTrue("${l.id} stop $it", it in 1.0..4.0) }
        }
    }

    @Test fun knownApertures() {
        val sig = LensDb.find("ARRI Signature Prime")!!
        assertEquals("T1.8", sig.stopLabel(35.0))
        assertEquals("T2.5", sig.stopLabel(200.0))
        assertEquals("T2.8", sig.stopLabel(280.0))
        assertEquals("T1.8–T2.8", sig.stopRange)
        assertEquals("f/1.8", LensDb.find("Sigma 18-35 Art")!!.stopRange)
        assertEquals(null, LensDb.FREE.stopRange)
    }

    @Test fun genericRangeAndSnap() {
        assertEquals(8.0, LensDb.FREE.snap(4.0), 0.0)
        assertEquals(300.0, LensDb.FREE.snap(1000.0), 0.0)
        assertEquals(35.0, LensDb.find("ARRI Signature Prime")!!.snap(33.0), 0.0)
    }
}

class FormatsTest {
    @Test fun guideFormats() {
        assertEquals(Formats.guides.size, Formats.guides.map { it.label }.toSet().size)
        assertEquals(Formats.guides.map { it.ratio }.sorted(), Formats.guides.map { it.ratio })
        assertEquals("16:9", Formats.label(16.0 / 9))
        assertEquals("2.39:1", Formats.label(2.39))
        assertEquals("1.46:1", Formats.label(1.4564))
    }
}

class OpticsTest {
    private val ff = SensorMode("FF", 36.0, 24.0)

    @Test fun textbookFieldOfView() {
        val t = Optics.recordedFrame(ff, 50.0, 1.0)
        assertEquals(39.60, t.hFovDeg, 0.01)
        assertEquals(26.99, t.vFovDeg, 0.01)
    }

    @Test fun anamorphicDoublesHorizontalOnly() {
        val s = Optics.recordedFrame(ff, 50.0, 1.0)
        val a = Optics.recordedFrame(ff, 50.0, 2.0)
        assertEquals(2 * s.h, a.h, 1e-12)
        assertEquals(s.v, a.v, 1e-12)
        assertEquals(1.0, Optics.stretch(2.0, 2.0), 0.0)
        assertEquals(0.5, Optics.stretch(2.0, 1.0), 0.0)
        assertEquals(2.0, Optics.stretch(1.0, 2.0), 0.0)
    }

    @Test fun guidesFitInsideFrame() {
        val frame = Optics.recordedFrame(ff, 35.0, 1.0)
        Formats.guides.forEach { g ->
            val r = Optics.guideFrame(frame, g.ratio)
            assertEquals(g.label, g.ratio, r.aspect, 1e-9)
            assertTrue(g.label, r.h <= frame.h + 1e-12 && r.v <= frame.v + 1e-12)
            assertTrue(g.label, r.h == frame.h || r.v == frame.v)
        }
    }

    @Test fun coverage() {
        assertTrue(Optics.coverage(ff, 43.3)!! >= 1.0)
        assertTrue(Optics.coverage(ff, 28.4)!! < 1.0)
        assertEquals(null, Optics.coverage(ff, null))
    }
}

class PlannerTest {
    // Roughly a modern phone: main 5.4 mm on a 7.2 x 5.4 mm active area, ultrawide 1.74 mm on 4.0 x 3.0 mm.
    private val main = PhoneLens("0", 5.4, 7.2, 5.4, 1f, 8f)
    private val ultra = PhoneLens("2", 1.74, 4.0, 3.0, 1f, 4f)
    private val all = listOf(main, ultra)
    private val ff = SensorMode("FF", 36.0, 24.0)

    @Test fun phoneFieldOfView() {
        // 7.2 mm / (2 * 5.4 mm) = 0.6667
        assertEquals(0.6667, main.tangents(4.0 / 3, 1.0, 1.0).h, 1e-4)
        assertEquals(0.3333, main.tangents(4.0 / 3, 2.0, 1.0).h, 1e-4)
    }

    @Test fun picksNarrowestLensThatSeesEverything() {
        val tele = Optics.recordedFrame(ff, 85.0, 1.0)
        val p1 = planLens(all, tele, 4.0 / 3, { 1.0 }, "")!!
        assertEquals("0", p1.lens.id)
        assertFalse(p1.tooWide)

        val wide = Optics.recordedFrame(ff, 18.0, 1.0)
        val p2 = planLens(all, wide, 4.0 / 3, { 1.0 }, "")!!
        assertEquals("2", p2.lens.id)
        // At the chosen zoom the frame fits inside the stream.
        val seen = p2.lens.tangents(4.0 / 3, p2.zoom.toDouble(), 1.0)
        assertTrue(wide.h <= seen.h + 1e-6 && wide.v <= seen.v + 1e-6)

        val tooWide = Optics.recordedFrame(ff, 8.0, 1.0)
        assertTrue(planLens(all, tooWide, 4.0 / 3, { 1.0 }, "")!!.tooWide)
    }

    @Test fun pinnedLensIsRespected() {
        val tele = Optics.recordedFrame(ff, 85.0, 1.0)
        assertEquals("2", planLens(all, tele, 4.0 / 3, { 1.0 }, "2")!!.lens.id)
        assertNotNull(planLens(all, tele, 4.0 / 3, { 1.0 }, "missing"))
    }
}

class FormatHelpersTest {
    @Test fun shotNumbers() {
        assertEquals("4", nextShot("3"))
        assertEquals("13B", nextShot("12B"))
        assertEquals("", nextShot(""))
    }

    @Test fun focalSteps() {
        assertEquals(36.0, stepFocal(35.0, 1), 1e-9)
        assertEquals(34.0, stepFocal(35.0, -1), 1e-9)
        assertEquals(19.5, stepFocal(20.0, -1), 1e-9)
        assertEquals(105.0, stepFocal(100.0, 1), 1e-9)
        assertEquals(18.5, roundFocal(18.4), 1e-9)
    }
}
