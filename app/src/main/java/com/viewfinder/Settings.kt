package com.viewfinder

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import com.viewfinder.data.CameraDb
import com.viewfinder.data.CineCamera
import com.viewfinder.data.LensDb
import com.viewfinder.data.SensorMode
import kotlin.math.abs
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** Persistent, observable app state (SharedPreferences backed Compose state). */
class Settings(context: Context) {
    private val p = context.getSharedPreferences("viewfinder", Context.MODE_PRIVATE)

    private inner class Pref<T>(
        private val key: String,
        read: (String) -> T,
        private val write: (String, T) -> Unit,
    ) : ReadWriteProperty<Any?, T> {
        private val state: MutableState<T> = mutableStateOf(read(key))
        override fun getValue(thisRef: Any?, property: KProperty<*>) = state.value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            state.value = value
            write(key, value)
        }
    }

    private fun str(key: String, def: String) =
        Pref(key, { p.getString(it, def) ?: def }, { k, v -> p.edit().putString(k, v).apply() })

    private fun dbl(key: String, def: Double) =
        Pref(key, { p.getString(it, null)?.toDoubleOrNull() ?: def }, { k, v -> p.edit().putString(k, v.toString()).apply() })

    private fun int(key: String, def: Int) =
        Pref(key, { p.getInt(it, def) }, { k, v -> p.edit().putInt(k, v).apply() })

    private fun bool(key: String, def: Boolean) =
        Pref(key, { p.getBoolean(it, def) }, { k, v -> p.edit().putBoolean(k, v).apply() })

    var cameraId by str("camera", "ARRI Alexa 35")
    var modeIndex by int("mode", 0)
    var lensId by str("lens", LensDb.FREE.id)
    var focal by dbl("focal", 35.0)

    /** Desqueeze applied to the image; set to the lens squeeze on lens change, overridable. */
    var desqueeze by dbl("squeeze", 1.0)

    private var guidesRaw by str("guides", "")

    /** Frame guide aspect ratios in the order they were added (first one is the crop target). */
    var guides: List<Double>
        get() = guidesRaw.split(',').mapNotNull { it.toDoubleOrNull() }
        set(value) {
            val unique = value.fold(emptyList<Double>()) { acc, r -> if (acc.any { abs(it - r) < 1e-6 }) acc else acc + r }
            guidesRaw = unique.joinToString(",")
        }

    /** Show only the first frame guide, filling the screen (not sensor-true). */
    var crop by bool("crop", false)
    var dark by bool("dark", false)
    var coverage by bool("coverage", true)
    var pinnedPhoneCam by str("phone_cam", "")

    // Legacy single-project fields; only read once to seed the first project (see ProjectStore).
    var project by str("project", "")
    var director by str("director", "")
    var dop by str("dop", "")
    var location by str("location", "")
    var scene by str("scene", "1")
    var shot by str("shot", "1")
    var notes by str("notes", "")
    var autoShot by bool("auto_shot", true)

    /** Per phone camera FOV correction factor, keyed by Camera2 id. */
    val calib = mutableStateMapOf<String, Double>().apply {
        p.all.forEach { (k, v) ->
            if (k.startsWith("calib_") && v is String) v.toDoubleOrNull()?.let { put(k.removePrefix("calib_"), it.coerceIn(0.8, 1.2)) }
        }
    }

    /** Calibration is a small correction; limited to ±20 % so it can never invert or explode the geometry. */
    fun setCalib(id: String, value: Double) {
        val v = Math.round(value.coerceIn(0.8, 1.2) * 1000) / 1000.0
        calib[id] = v
        p.edit().putString("calib_$id", v.toString()).apply()
    }

    val camera: CineCamera get() = CameraDb.find(cameraId) ?: CameraDb.cameras.first()

    val mode: SensorMode get() = camera.modes.let { it[modeIndex.coerceIn(0, it.lastIndex)] }

    val lens get() = LensDb.find(lensId) ?: LensDb.FREE
}
