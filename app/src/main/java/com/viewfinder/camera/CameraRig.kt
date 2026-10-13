@file:OptIn(ExperimentalCamera2Interop::class)

package com.viewfinder.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Size
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.suspendCancellableCoroutine

/** Owns CameraX: lists phone cameras, binds one with preview + still capture, applies zoom. */
class CameraRig(private val context: Context) {
    var lenses by mutableStateOf<List<PhoneLens>>(emptyList()); private set
    var boundLens by mutableStateOf<PhoneLens?>(null); private set
    var zoom by mutableFloatStateOf(1f); private set
    var streamAspect by mutableDoubleStateOf(4.0 / 3.0); private set
    /** Increments on every successful bind, so zoom gets re-applied to a fresh camera. */
    var bindCount by mutableIntStateOf(0); private set
    var error by mutableStateOf<String?>(null)

    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private val captureExecutor = Executors.newSingleThreadExecutor()

    // Preview and still use the same 4:3 aspect (the native shape of nearly all phone sensors),
    // so both see the full active array and share one geometry.
    private val preview = Preview.Builder()
        .setResolutionSelector(
            ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .build()
        )
        .build()

    private val capture = ImageCapture.Builder()
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
        .setResolutionSelector(
            ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .setResolutionStrategy(
                    ResolutionStrategy(Size(4032, 3024), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)
                )
                .build()
        )
        .build()

    suspend fun start() {
        val p = suspendCancellableCoroutine { cont ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                try {
                    cont.resume(future.get())
                } catch (e: Exception) {
                    cont.resumeWithException(e)
                }
            }, ContextCompat.getMainExecutor(context))
        }
        provider = p
        lenses = p.availableCameraInfos
            .filter { it.lensFacing == CameraSelector.LENS_FACING_BACK }
            .mapNotNull { PhoneLens.from(it) }
            .distinctBy { it.id }
        if (lenses.isEmpty()) error = "No rear camera found"
    }

    /**
     * Binds preview and still capture to the given phone camera. A no-op if exactly that is already
     * bound; otherwise (other lens, or use cases unbound behind our back, e.g. by another instance or
     * a lifecycle teardown) it rebinds from scratch.
     */
    @androidx.annotation.OptIn(markerClass = [ExperimentalCamera2Interop::class])
    fun bind(owner: LifecycleOwner, lens: PhoneLens, view: PreviewView) {
        val p = provider ?: return
        if (boundLens?.id == lens.id && camera != null && p.isBound(preview) && p.isBound(capture)) return
        camera?.cameraInfo?.zoomState?.removeObservers(owner)
        p.unbindAll()
        preview.setSurfaceProvider(view.surfaceProvider)
        val selector = CameraSelector.Builder()
            .addCameraFilter { infos -> infos.filter { Camera2CameraInfo.from(it).cameraId == lens.id } }
            .build()
        try {
            val cam = p.bindToLifecycle(owner, selector, preview, capture)
            camera = cam
            boundLens = lens
            cam.cameraInfo.zoomState.observe(owner) { zoom = it.zoomRatio }
            bindCount++
            error = null
        } catch (e: Exception) {
            error = "Camera ${lens.id}: ${e.message}"
        }
    }

    /** Reads the actual preview stream shape once CameraX has negotiated it. */
    fun refreshStreamAspect() {
        val r = preview.resolutionInfo?.cropRect ?: return
        if (r.width() <= 0 || r.height() <= 0) return
        streamAspect = max(r.width(), r.height()).toDouble() / min(r.width(), r.height())
    }

    fun applyZoom(z: Float) {
        val l = boundLens ?: return
        camera?.cameraControl?.setZoomRatio(z.coerceIn(l.minZoom, l.maxZoom))
    }

    /** Full still, rotated upright for the given display rotation. */
    suspend fun takePicture(displayRotation: Int): Bitmap = suspendCancellableCoroutine { cont ->
        capture.targetRotation = displayRotation
        capture.takePicture(captureExecutor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                try {
                    val bmp = image.toBitmap()
                    val rotation = image.imageInfo.rotationDegrees
                    image.close()
                    cont.resume(rotate(bmp, rotation))
                } catch (e: Throwable) {
                    image.close()
                    cont.resumeWithException(e)
                }
            }

            override fun onError(exception: ImageCaptureException) {
                cont.resumeWithException(exception)
            }
        })
    }

    private fun rotate(src: Bitmap, degrees: Int): Bitmap {
        if (degrees % 360 == 0) return src
        val m = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true).also { if (it !== src) src.recycle() }
    }
}
