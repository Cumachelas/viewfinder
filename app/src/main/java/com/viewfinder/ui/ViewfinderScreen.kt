package com.viewfinder.ui

import android.view.Surface
import android.widget.Toast
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.viewfinder.R
import com.viewfinder.Settings
import com.viewfinder.camera.CameraRig
import com.viewfinder.camera.planLens
import com.viewfinder.capture.Caption
import com.viewfinder.capture.Still
import com.viewfinder.capture.StillGuide
import com.viewfinder.data.Formats
import com.viewfinder.data.LensDb
import com.viewfinder.data.LensSeries
import com.viewfinder.data.Project
import com.viewfinder.data.ProjectStore
import com.viewfinder.data.Shot
import com.viewfinder.optics.Optics
import com.viewfinder.optics.Tangents
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Panel { None, Camera, Lens, Project, Projects, Gallery, Setup, Focal }

// Mat layout: lens tools on the left, camera and shutter on the right, a thin label lane above and
// below the frame. The shown frame is fitted into and centered in the area left free between them.
private val EdgePadding = 10.dp
private val LeftWidth = 100.dp
private val RightWidth = 84.dp
private val ColumnGap = 8.dp
private val LaneHeight = 20.dp

@Composable
fun ViewfinderScreen(s: Settings, store: ProjectStore) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val rig = remember { CameraRig(context) }
    val previewView = remember {
        PreviewView(context).apply {
            // The whole stream stays on screen; the mat hides what lies outside the frame.
            scaleType = PreviewView.ScaleType.FIT_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var panel by remember { mutableStateOf(Panel.None) }
    var busy by remember { mutableStateOf(false) }
    var flash by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            rig.start()
        } catch (e: Exception) {
            rig.error = e.message
        }
    }

    val camera = s.camera
    val mode = s.mode
    val lens = s.lens
    // A stored focal length can lie outside the current lens range (e.g. after a data update).
    LaunchedEffect(lens.id, s.focal) {
        val valid = lens.snap(s.focal)
        if (valid != s.focal) s.focal = valid
    }
    val stretch = Optics.stretch(lens.squeeze, s.desqueeze)
    val sensor = Optics.recordedFrame(mode, s.focal, lens.squeeze).stretchH(stretch)
    val allGuides = s.guides.mapIndexed { i, r -> Guide(Formats.label(r), Optics.guideFrame(sensor, r), guideColor(i)) }
    val cropGuide = if (s.crop) allGuides.firstOrNull() else null
    val frame = cropGuide?.frame ?: sensor
    val guides = if (cropGuide != null) allGuides.drop(1) else allGuides
    val trueFrame = Tangents(frame.h / stretch, frame.v)

    val imageCircle = lens.imageCircle(s.focal)
    val coverage = Optics.coverage(mode, imageCircle)
    val coverageIssue = coverage != null && coverage < 1.0
    val circle = imageCircle?.takeIf { s.coverage && coverageIssue }?.let { d -> d / mode.widthMm to d / mode.heightMm }

    val calib = s.calib.toMap()
    val density = LocalDensity.current
    val insets = WindowInsets.safeDrawing
    val layoutDirection = LocalLayoutDirection.current
    val fitW: Double
    val fitH: Double
    val frameOffset: Offset
    with(density) {
        // Free area between the columns and lanes; the preview is scaled to fill it and moved to its center.
        val vw = viewSize.width.toFloat()
        val vh = viewSize.height.toFloat()
        val left = insets.getLeft(density, layoutDirection) + (EdgePadding + LeftWidth + ColumnGap).toPx()
        val right = vw - insets.getRight(density, layoutDirection) - (EdgePadding + RightWidth + ColumnGap).toPx()
        val top = insets.getTop(density) + LaneHeight.toPx()
        val bottom = vh - insets.getBottom(density) - LaneHeight.toPx()
        if (vw > 0 && vh > 0) {
            fitW = ((right - left) / vw).toDouble().coerceIn(0.3, 1.0)
            fitH = ((bottom - top) / vh).toDouble().coerceIn(0.3, 1.0)
            frameOffset = Offset((left + right) / 2 - vw / 2, (top + bottom) / 2 - vh / 2)
        } else {
            fitW = 0.7
            fitH = 0.9
            frameOffset = Offset.Zero
        }
    }

    val plan = remember(rig.lenses, trueFrame, rig.streamAspect, s.pinnedPhoneCam, calib) {
        planLens(rig.lenses, trueFrame, rig.streamAspect, { calib[it] ?: 1.0 }, s.pinnedPhoneCam)
    }

    LaunchedEffect(plan?.lens?.id) {
        val p = plan ?: return@LaunchedEffect
        rig.bind(owner, p.lens, previewView)
        repeat(30) {
            rig.refreshStreamAspect()
            delay(100)
        }
    }
    // Whenever the app comes back to the foreground, make sure the camera is really still bound.
    val currentPlan by rememberUpdatedState(plan)
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) currentPlan?.let { rig.bind(owner, it.lens, previewView) }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(plan?.zoom, rig.boundLens?.id, rig.bindCount) {
        val p = plan ?: return@LaunchedEffect
        if (rig.boundLens?.id == p.lens.id) rig.applyZoom(p.zoom)
    }
    LaunchedEffect(flash) {
        if (flash) {
            delay(120)
            flash = false
        }
    }

    val bound = rig.boundLens
    val shown = bound?.tangents(rig.streamAspect, rig.zoom.toDouble(), calib[bound.id] ?: 1.0)
    val scale = if (shown == null) 1f else previewScale(
        viewSize.width.toFloat(), viewSize.height.toFloat(), fitW, fitH, rig.streamAspect, shown, frame,
    )

    fun shoot() {
        val l = bound ?: return
        if (busy) return
        busy = true
        flash = true
        val rotation = previewView.display?.rotation ?: Surface.ROTATION_90
        val zoomAtShot = rig.zoom.toDouble()
        val k = calib[l.id] ?: 1.0
        val target = trueFrame
        val stillStretch = stretch
        val stillGuides = guides.map { StillGuide(it.label, it.frame.h / frame.h, it.frame.v / frame.v, it.color.toArgb()) }
        val stillCircle = circle?.let { (cw, ch) -> cw * sensor.h / frame.h to ch * sensor.v / frame.v }
        val project = store.current
        val caption = buildCaption(s, project, frame.aspect, cropGuide != null, stretch, guides)
        val name = fileName(project)
        val dark = s.dark
        val font = ResourcesCompat.getFont(context, R.font.inter)
        scope.launch {
            try {
                val bmp = rig.takePicture(rotation)
                val uri = withContext(Dispatchers.Default) {
                    try {
                        val aspect = max(bmp.width, bmp.height).toDouble() / min(bmp.width, bmp.height)
                        val t = l.tangents(aspect, zoomAtShot, k)
                        val out = Still.compose(
                            bmp, target.h / t.h, target.v / t.v, stillStretch, stillGuides, stillCircle, caption, font, dark,
                        )
                        try {
                            withContext(Dispatchers.IO) { Still.save(context, out, name) }
                        } finally {
                            out.recycle()
                        }
                    } catch (e: OutOfMemoryError) {
                        null
                    } finally {
                        bmp.recycle()
                    }
                }
                if (uri != null) {
                    Toast.makeText(context, "Saved to Pictures/viewfinder", Toast.LENGTH_SHORT).show()
                    store.addShot(project.id, Shot(uri.toString(), project.scene, project.shot, System.currentTimeMillis()))
                    // Increment on the stored project, in case it was edited while the still was rendering.
                    store.projects.firstOrNull { it.id == project.id }?.let { now ->
                        if (now.autoShot) store.update(now.copy(shot = nextShot(now.shot)))
                    }
                } else {
                    Toast.makeText(context, "Saving failed", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Capture failed: ${e.message}", Toast.LENGTH_LONG).show()
                currentPlan?.let { rig.bind(owner, it.lens, previewView) }
            } finally {
                busy = false
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Mat)) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { viewSize = it }
                .graphicsLayer {
                    scaleX = scale * stretch.toFloat()
                    scaleY = scale
                    translationX = frameOffset.x
                    translationY = frameOffset.y
                },
        )

        if (shown != null) {
            val modeLabel = mode.name.uppercase()
            FrameOverlay(
                frame = frame,
                sensor = sensor,
                guides = guides,
                cropGuide = cropGuide,
                phone = shown,
                stretch = stretch,
                streamAspect = rig.streamAspect,
                scale = scale,
                circle = circle,
                topLeftLabel = if (cropGuide != null) "$modeLabel   CROP ${cropGuide.label}" else "$modeLabel   ${Formats.label(sensor.aspect)}",
                topRightLabel = "H ${fmt(trueFrame.hFovDeg, 1)}°   V ${fmt(trueFrame.vFovDeg, 1)}°",
                warning = rig.error ?: if (coverageIssue) "Potential sensor coverage issue" else null,
                offset = frameOffset,
            )
        } else {
            Box(Modifier.fillMaxSize().background(Mat))
        }
        if (flash) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.6f)))

        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = EdgePadding, vertical = 8.dp)
        ) {
            // Left: lens, image format tools and focal length
            Column(
                Modifier.align(Alignment.CenterStart).width(LeftWidth).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val title = if (lens.isGeneric) "${fmtMm(s.focal)} mm" else lens.name
                val sub = if (lens.isGeneric) lens.name
                else listOfNotNull("${fmtMm(s.focal)} mm", lens.stopLabel(s.focal)).joinToString("  ·  ")
                SideItem(Icons.Aperture, lens.brand.ifEmpty { "Generic" }, title, sub) { panel = Panel.Lens }
                Spacer(Modifier.height(2.dp))
                ToolRow(
                    Icons.Crop,
                    "Crop",
                    active = cropGuide != null,
                    enabled = s.guides.isNotEmpty(),
                ) { s.crop = !s.crop }
                GuidesTool(s.guides) { s.guides = it }
                SqueezeTool(s.desqueeze) { s.desqueeze = it }
                Spacer(Modifier.height(2.dp))
                FocalSelector(
                    lens, s.focal,
                    Modifier.weight(1f).fillMaxWidth(),
                    onFocal = { s.focal = it },
                    onEdit = { panel = Panel.Focal },
                )
            }

            // Right: camera and project on top, shutter pinned to the vertical center, buttons at the bottom
            Box(Modifier.align(Alignment.CenterEnd).width(RightWidth).fillMaxHeight()) {
                Column(Modifier.align(Alignment.TopCenter), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SideItem(Icons.Camera, camera.brand, camera.model, mode.name) { panel = Panel.Camera }
                    SideItem(Icons.Clapper, "Project", store.current.title, "") { panel = Panel.Project }
                }
                CaptureButton(enabled = !busy && bound != null, modifier = Modifier.align(Alignment.Center)) { shoot() }
                Column(Modifier.align(Alignment.BottomCenter), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ToolRow(Icons.Folder, "Projects", active = false) { panel = Panel.Projects }
                    ToolRow(Icons.Image, "Gallery", active = false) { panel = Panel.Gallery }
                    ToolRow(Icons.Sliders, "Settings", active = false) { panel = Panel.Setup }
                }
            }
        }

        when (panel) {
            Panel.Camera -> CameraPanel(s) { panel = Panel.None }
            Panel.Lens -> LensPanel(s) { panel = Panel.None }
            Panel.Project -> ProjectPanel(store) { panel = Panel.None }
            Panel.Projects -> ProjectsPanel(store, onOpenProject = { panel = Panel.Project }) { panel = Panel.None }
            Panel.Gallery -> GalleryScreen(store) { panel = Panel.None }
            Panel.Setup -> SetupPanel(s, rig.lenses, bound, rig.zoom, rig.streamAspect) { panel = Panel.None }
            Panel.Focal -> FocalPanel(s) { panel = Panel.None }
            Panel.None -> Unit
        }
    }
}

/** Multi-select of frame guide aspect ratios; the menu stays open while toggling. */
@Composable
private fun GuidesTool(guides: List<Double>, onChange: (List<Double>) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        val label = when (guides.size) {
            0 -> "Aspect"
            1 -> Formats.label(guides.first())
            else -> "${guides.size} aspects"
        }
        ToolRow(Icons.Frame, label, active = guides.isNotEmpty()) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = Mat) {
            MenuRow("Native", null, guides.isEmpty()) { onChange(emptyList()); open = false }
            Formats.guides.forEach { format ->
                val a = format.ratio
                val index = guides.indexOfFirst { abs(it - a) < 1e-6 }
                val on = index >= 0
                MenuRow(format.label, if (on) guideColor(index) else null, on, format.note) {
                    onChange(if (on) guides.filterIndexed { i, _ -> i != index } else guides + a)
                }
            }
        }
    }
}

@Composable
private fun SqueezeTool(desqueeze: Double, onSelect: (Double) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        ToolRow(Icons.Squeeze, squeezeLabel(desqueeze), active = desqueeze != 1.0) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = Mat) {
            LensDb.squeezeFactors.forEach { f ->
                MenuRow(squeezeLabel(f), null, f == desqueeze) { onSelect(f); open = false }
            }
        }
    }
}

/** Menu entry; a checked entry shows a check mark, or a dot in its guide color. */
@Composable
private fun MenuRow(text: String, swatch: Color?, checked: Boolean, note: String = "", onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text, color = Ink, fontSize = 14.sp, maxLines = 1,
                    fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = if (note.isNotEmpty()) Modifier.width(64.dp) else Modifier,
                )
                if (note.isNotEmpty()) Text(note, color = Muted, fontSize = 12.sp, maxLines = 1)
            }
        },
        leadingIcon = {
            when {
                swatch != null -> Box(Modifier.size(12.dp).clip(CircleShape).background(swatch))
                checked -> Icon(Icons.Check, null, Modifier.size(16.dp), tint = Ink)
                else -> Spacer(Modifier.width(16.dp))
            }
        },
        onClick = onClick,
    )
}

/** Vertical focal length control: a list of primes, or +/- and a drag strip for free focal lengths. */
@Composable
private fun FocalSelector(
    lens: LensSeries,
    focal: Double,
    modifier: Modifier,
    onFocal: (Double) -> Unit,
    onEdit: () -> Unit,
) {
    if (lens.isPrimeSet) {
        val state = rememberLazyListState()
        LaunchedEffect(lens.id) {
            val i = lens.primes.indexOfFirst { abs(it - focal) < 0.01 }
            if (i > 2) state.scrollToItem(i - 2)
        }
        LazyColumn(modifier, state = state, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(lens.primes) { f ->
                val stop = lens.stopLabel(f)?.let { "  $it" } ?: ""
                Pill("${fmtMm(f)}$stop", selected = abs(f - focal) < 0.01, modifier = Modifier.fillMaxWidth()) { onFocal(f) }
            }
        }
    } else {
        val current by rememberUpdatedState(focal)
        var raw by remember { mutableDoubleStateOf(focal) }
        Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            IconPill(Icons.Plus, Modifier.fillMaxWidth()) { onFocal(lens.snap(stepFocal(current, 1))) }
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(Square)
                    .background(Ink)
                    .pointerInput(lens.id) { detectTapGestures(onTap = { onEdit() }) }
                    .pointerInput(lens.id) {
                        detectVerticalDragGestures(onDragStart = { raw = current }) { change, dy ->
                            change.consume()
                            raw = (raw * exp(-dy / 220.0)).coerceIn(lens.minFocal, lens.maxFocal)
                            onFocal(lens.snap(roundFocal(raw)))
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(fmtMm(focal), color = Mat, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text("mm  ·  drag", color = Mat.copy(alpha = 0.6f), fontSize = 9.sp)
                }
            }
            IconPill(Icons.Minus, Modifier.fillMaxWidth()) { onFocal(lens.snap(stepFocal(current, -1))) }
        }
    }
}

private fun buildCaption(
    s: Settings,
    p: Project,
    frameAspect: Double,
    crop: Boolean,
    stretch: Double,
    guides: List<Guide>,
): Caption {
    val lens = s.lens
    val lensName = if (lens.isGeneric) (if (lens.squeeze != 1.0) lens.name else "") else "${lens.brand} ${lens.name}"
    val lensLine = listOf(lensName, "${fmtMm(s.focal)} mm").filter { it.isNotBlank() }.joinToString("  ·  ")
    val slate = listOf(
        p.location,
        if (p.scene.isNotBlank()) "Scene ${p.scene}" else "",
        if (p.shot.isNotBlank()) "Shot ${p.shot}" else "",
    ).filter { it.isNotBlank() }.joinToString("  ·  ")
    val credits = listOf(
        p.name,
        if (p.director.isNotBlank()) "Director ${p.director}" else "",
        if (p.dp.isNotBlank()) "DP ${p.dp}" else "",
    ).filter { it.isNotBlank() }.joinToString("  ·  ")
    val date = SimpleDateFormat("d MMM yyyy", Locale.UK).format(Date())
    val details = listOfNotNull(
        s.mode.name,
        Formats.label(frameAspect) + if (crop) " crop" else "",
        if (lens.squeeze != 1.0 || stretch != 1.0) "${fmtNum(lens.squeeze)}× lens / ${fmtNum(s.desqueeze)}× desqueeze" else null,
        if (guides.isNotEmpty()) "Aspect " + guides.joinToString(" / ") { it.label } else null,
        date,
    ).joinToString("  ·  ")
    return Caption(s.camera.displayName, slate, lensLine, credits, details, p.notes)
}

private fun fileName(p: Project): String {
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    // Only [a-z0-9_-] and a bounded length: always a valid, non-traversing file name.
    fun clean(v: String, max: Int) = v.replace(Regex("[^A-Za-z0-9_-]+"), "-").trim('-').take(max)
    val parts = listOf(clean(p.name, 60), "sc${clean(p.scene, 10)}", "sh${clean(p.shot, 10)}", stamp)
        .filter { it.isNotBlank() }
        .joinToString("_")
    return parts.lowercase()
}
