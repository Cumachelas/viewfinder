package com.viewfinder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.viewfinder.Settings
import com.viewfinder.camera.PhoneLens
import com.viewfinder.data.CameraDb
import com.viewfinder.data.LensDb
import com.viewfinder.data.Project
import com.viewfinder.data.ProjectStore

private val listPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp)

private fun parseNum(s: String) = s.trim().replace(',', '.').toDoubleOrNull()

@Composable
fun CameraPanel(s: Settings, onClose: () -> Unit) {
    SidePanel("Camera", onClose) {
        var open by remember { mutableStateOf(s.cameraId) }
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = listPadding) {
            CameraDb.cameras.groupBy { it.brand }.forEach { (brand, cams) ->
                item(key = "h_$brand") { SectionHeader(brand) }
                cams.forEach { cam ->
                    item(key = cam.id) {
                        ListRow(
                            title = cam.model,
                            subtitle = if (cam.id == s.cameraId) s.mode.name else "${cam.modes.size} formats",
                            trailing = if (open == cam.id) "–" else "+",
                        ) { open = if (open == cam.id) "" else cam.id }
                    }
                    if (open == cam.id) {
                        cam.modes.forEachIndexed { i, m ->
                            item(key = "${cam.id}#$i") {
                                Column(Modifier.padding(start = 14.dp)) {
                                    ListRow(
                                        title = m.name,
                                        subtitle = "${fmt(m.widthMm, 2)} × ${fmt(m.heightMm, 2)} mm  ·  Ø ${fmt(m.diagonalMm, 2)} mm  ·  ${aspectLabel(m.widthMm / m.heightMm)}",
                                        selected = cam.id == s.cameraId && i == s.modeIndex,
                                    ) {
                                        s.cameraId = cam.id
                                        s.modeIndex = i
                                        onClose()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LensPanel(s: Settings, onClose: () -> Unit) {
    SidePanel("Lens", onClose) {
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = listPadding) {
            LensDb.series.groupBy { it.brand.ifEmpty { "Generic" } }.forEach { (brand, list) ->
                item(key = "h_$brand") { SectionHeader(brand) }
                items(list, key = { it.id }) { l ->
                    val range = when {
                        l.isPrimeSet -> "${fmtMm(l.minFocal)}–${fmtMm(l.maxFocal)} mm  ·  ${l.primes.size} focal lengths"
                        else -> "${fmtMm(l.zoomMin)}–${fmtMm(l.zoomMax)} mm"
                    }
                    val known = (l.circles.values + listOfNotNull(l.imageCircleMm)).distinct().sorted()
                    val circle = when {
                        known.isEmpty() -> ""
                        known.size == 1 -> "  ·  Ø ${fmtNum(known.first())} mm"
                        else -> "  ·  Ø ${fmtNum(known.first())}–${fmtNum(known.last())} mm"
                    }
                    val stop = l.stopRange?.let { "  ·  $it" } ?: ""
                    ListRow(l.name, range + stop + circle, selected = l.id == s.lensId) {
                        s.lensId = l.id
                        s.desqueeze = l.squeeze
                        s.focal = l.snap(s.focal)
                        onClose()
                    }
                }
            }
        }
    }
}

/** Edits the current project; every keystroke is saved immediately. */
@Composable
fun ProjectPanel(store: ProjectStore, onClose: () -> Unit) {
    val p = store.current
    fun set(change: Project.() -> Project) = store.update(store.current.change())
    var confirmDelete by remember(p.id) { mutableStateOf(false) }

    SidePanel("Project", onClose) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Field("Project", p.name, Modifier.fillMaxWidth()) { v -> set { copy(name = v) } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Director", p.director, Modifier.weight(1f)) { v -> set { copy(director = v) } }
                Field("DP", p.dp, Modifier.weight(1f)) { v -> set { copy(dp = v) } }
            }
            Field("Location", p.location, Modifier.fillMaxWidth()) { v -> set { copy(location = v) } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Scene", p.scene, Modifier.weight(1f)) { v -> set { copy(scene = v) } }
                Field("Shot", p.shot, Modifier.weight(1f), KeyboardType.Number) { v -> set { copy(shot = v.filter(Char::isDigit)) } }
            }
            OutlinedTextField(
                p.notes, { v -> set { copy(notes = v) } }, Modifier.fillMaxWidth(),
                label = { Text("Notes (printed in italics on the export)") },
                minLines = 2, maxLines = 4, shape = Square,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = p.autoShot,
                    onCheckedChange = { v -> set { copy(autoShot = v) } },
                    colors = CheckboxDefaults.colors(checkedColor = Ink, checkmarkColor = Mat, uncheckedColor = Ink),
                )
                Text("Increment shot", fontSize = 14.sp, color = Ink)
            }
            Text("Saved automatically.", color = Muted, fontSize = 11.sp)
            Spacer(Modifier.height(12.dp))
            Pill(
                if (confirmDelete) "Tap again to delete “${p.title}”" else "Delete project",
                selected = confirmDelete,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (confirmDelete) {
                    store.delete(p.id)
                    onClose()
                } else {
                    confirmDelete = true
                }
            }
            Text("Images stay in the phone gallery under Pictures/viewfinder.", color = Muted, fontSize = 11.sp)
        }
    }
}

/** All projects, newest first; tap to switch, or start a new one. */
@Composable
fun ProjectsPanel(store: ProjectStore, onOpenProject: () -> Unit, onClose: () -> Unit) {
    val dateFormat = remember { java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.UK) }
    SidePanel("Projects", onClose) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Pill("New project", selected = true, modifier = Modifier.fillMaxWidth()) {
                store.create()
                onOpenProject()
            }
        }
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = listPadding) {
            items(store.projects, key = { it.id }) { proj ->
                val count = store.shotsOf(proj.id).size
                ListRow(
                    title = proj.title,
                    subtitle = "${dateFormat.format(java.util.Date(proj.created))}  ·  $count ${if (count == 1) "shot" else "shots"}",
                    selected = proj.id == store.currentId,
                ) {
                    store.select(proj.id)
                    onClose()
                }
            }
        }
    }
}

@Composable
fun FocalPanel(s: Settings, onClose: () -> Unit) {
    var text by remember { mutableStateOf(fmtMm(s.focal)) }
    val lens = s.lens
    fun apply() {
        parseNum(text)?.takeIf { it > 0 }?.let { s.focal = lens.snap(it) }
        onClose()
    }
    SidePanel("Focal length", onClose) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Field("mm (${fmtMm(lens.minFocal)}–${fmtMm(lens.maxFocal)})", text, Modifier.fillMaxWidth(), KeyboardType.Decimal) { text = it }
            Pill("Apply", selected = true, modifier = Modifier.fillMaxWidth(), onClick = ::apply)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SetupPanel(
    s: Settings,
    lenses: List<PhoneLens>,
    current: PhoneLens?,
    zoom: Float,
    streamAspect: Double,
    onClose: () -> Unit,
) {
    SidePanel("Settings", onClose) {
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = listPadding) {
            item {
                SectionHeader("Display")
                Toggle("Dark mode", s.dark) {
                    s.dark = it
                    Palette.dark = it
                }
                Toggle("Lens coverage vignette", s.coverage) { s.coverage = it }
            }
            item {
                SectionHeader("Phone camera")
                Text(
                    current?.let { "Current: Cam ${it.id}  ·  ${fmt(it.focalMm, 2)} mm  ·  ${fmt(zoom.toDouble(), 2)}×" } ?: "Current: –",
                    color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Automatic", selected = s.pinnedPhoneCam.isEmpty()) { s.pinnedPhoneCam = "" }
                    lenses.forEach { l ->
                        Pill("Cam ${l.id}  ·  ${fmt(l.focalMm, 2)} mm", selected = s.pinnedPhoneCam == l.id) { s.pinnedPhoneCam = l.id }
                    }
                }
            }
            item {
                SectionHeader("Calibration")
                Text(
                    "Only needed if a device reports slightly wrong values. Aim at something of known width W " +
                        "from a known distance D. With Generic / Full Frame the frame width matches at focal " +
                        "length f = 36 × D / W. Adjust the factor until the object fills the frame exactly.",
                    color = Muted, fontSize = 11.sp,
                )
            }
            items(lenses, key = { it.id }) { l ->
                val k = s.calib[l.id] ?: 1.0
                val t = l.tangents(streamAspect, 1.0, k)
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Cam ${l.id}  ·  ${fmt(l.focalMm, 2)} mm", fontSize = 13.sp, color = Ink, fontWeight = FontWeight.Medium)
                        Text(
                            "${fmt(l.activeWidthMm, 2)} × ${fmt(l.activeHeightMm, 2)} mm  ·  H ${fmt(t.hFovDeg, 1)}°",
                            color = Muted, fontSize = 11.sp,
                        )
                    }
                    IconPill(Icons.Minus) { s.setCalib(l.id, k - 0.002) }
                    Text(
                        "${if (k >= 1) "+" else ""}${fmt((k - 1) * 100, 1)} %",
                        fontSize = 12.sp, color = Ink, fontWeight = FontWeight.Medium, modifier = Modifier.width(52.dp),
                    )
                    IconPill(Icons.Plus) { s.setCalib(l.id, k + 0.002) }
                    Pill("Reset", selected = false) { s.setCalib(l.id, 1.0) }
                }
            }
        }
    }
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, color = Ink, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Mat, checkedTrackColor = Ink,
                uncheckedThumbColor = Ink, uncheckedTrackColor = Mat, uncheckedBorderColor = Ink,
            ),
        )
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    modifier: Modifier,
    keyboard: KeyboardType = KeyboardType.Text,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value, onChange, modifier,
        label = { Text(label) },
        singleLine = true,
        shape = Square,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
    )
}
