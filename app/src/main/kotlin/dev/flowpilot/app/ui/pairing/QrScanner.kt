package dev.flowpilot.app.ui.pairing

import dev.flowpilot.core.sync.catching

import android.annotation.SuppressLint
import android.util.Log
import android.util.Size
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.activity.compose.LocalActivity
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.delay
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** How long the preview may stay black before the person is told why, instead of staring at nothing. */
private const val NO_PICTURE_AFTER_MS = 6_000L

/** Consecutive frames the decoder may fail on before the person is told scanning is broken, not just slow. */
private const val DECODE_FAILURES_BEFORE_REPORT = 30

/**
 * `opencode pair` prints a dense QR (a long link) that is usually filmed off a monitor. CameraX's default 640x480
 * analysis frame leaves too few pixels per module to decode it, so ask for 720p or the closest size available.
 */
private val ANALYSIS_RESOLUTION = ResolutionSelector.Builder()
    .setResolutionStrategy(ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
    .build()

/**
 * Full-bleed camera preview that reports every QR code it decodes.
 *
 * CameraX's [LifecycleCameraController] owns the camera: it attaches the preview surface when the view is ready
 * and opens or closes the camera with the screen's lifecycle, which a hand-built Preview/ImageAnalysis binding
 * got wrong on some phones and left the screen black. Nothing here may crash the app, and the preview is never
 * left silently black: a camera error, or no picture after a few seconds, is reported through [onError], and
 * `onError(null)` clears it once the picture comes back.
 */
@SuppressLint("UnsafeOptInUsageError")
@Composable
fun QrScanner(onCode: (String) -> Unit, onError: (String?) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // The camera runs while the Activity is started. A navigation entry can sit below STARTED during screen
    // transitions, and CameraX then opens the camera without ever streaming: a black screen. Leaving this screen
    // still unbinds it, in onDispose below.
    val owner = (LocalActivity.current as? LifecycleOwner) ?: LocalLifecycleOwner.current
    val latest = rememberUpdatedState(onCode)
    val latestError = rememberUpdatedState(onError)
    var streaming by remember { mutableStateOf(false) }
    val preview = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            // A TextureView fades and animates with the rest of Compose; a SurfaceView punches a hole through it.
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    DisposableEffect(owner) {
        val main = ContextCompat.getMainExecutor(context)
        val disposed = AtomicBoolean(false)
        val busy = AtomicBoolean(false)
        val failures = AtomicInteger(0)
        val worker = Executors.newSingleThreadExecutor()
        // CameraX may still post a frame after we let go; drop it instead of throwing on a shut-down executor.
        val analysisExecutor = Executor { task ->
            if (disposed.get()) return@Executor
            try { worker.execute(task) } catch (_: RejectedExecutionException) {}
        }
        val scanner = catching {
            BarcodeScanning.getClient(BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                // Also returns codes found at a slant or partly out of focus, which a screen photo often is.
                .enableAllPotentialBarcodes()
                .build())
        }.onFailure { Log.w("FlowPilot", "QR scanner unavailable", it) }.getOrNull()
        val controller = LifecycleCameraController(context)

        fun report(message: String) {
            if (!disposed.get()) latestError.value(message)
        }

        val streamObserver = Observer<PreviewView.StreamState> { state ->
            streaming = state == PreviewView.StreamState.STREAMING
        }
        val cameraObserver = Observer<CameraState> { state ->
            val error = state.error ?: return@Observer
            Log.w("FlowPilot", "camera error ${error.code}", error.cause)
            report(cameraErrorMessage(error.code))
        }

        try {
            // Only preview and analysis: the default also binds photo capture, which some phones can't run alongside.
            controller.setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
            controller.imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
            controller.imageAnalysisResolutionSelector = ANALYSIS_RESOLUTION
            if (scanner != null) {
                controller.setImageAnalysisAnalyzer(analysisExecutor) { proxy ->
                    val media = proxy.image
                    if (media == null || disposed.get() || !busy.compareAndSet(false, true)) { proxy.close(); return@setImageAnalysisAnalyzer }
                    try {
                        val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                        scanner.process(image)
                            .addOnSuccessListener(main) { codes ->
                                failures.set(0)
                                if (disposed.get()) return@addOnSuccessListener
                                codes.firstNotNullOfOrNull { it.rawValue?.trim()?.takeIf(String::isNotEmpty) }?.let { value -> latest.value(value) }
                            }
                            .addOnFailureListener(main) { e ->
                                // A decoder that fails every frame used to look exactly like "no QR code in view".
                                Log.w("FlowPilot", "QR decode failed", e)
                                if (failures.incrementAndGet() == DECODE_FAILURES_BEFORE_REPORT) {
                                    report("QR scanning isn't working on this phone. Enter the address instead.")
                                }
                            }
                            .addOnCompleteListener(main) { proxy.close(); busy.set(false) }
                    } catch (e: Exception) {
                        Log.w("FlowPilot", "frame skipped", e)
                        proxy.close()
                        busy.set(false)
                    }
                }
            } else {
                report("QR scanning isn't available on this phone. Enter the address instead.")
            }
            preview.controller = controller
            preview.previewStreamState.observe(owner, streamObserver)
            controller.bindToLifecycle(owner)
            controller.initializationFuture.addListener({
                if (disposed.get()) return@addListener
                try {
                    controller.initializationFuture.get()
                    controller.cameraSelector = when {
                        controller.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
                        controller.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                        else -> { report("This phone has no camera. Enter the address instead."); return@addListener }
                    }
                    controller.cameraInfo?.cameraState?.observe(owner, cameraObserver)
                } catch (e: Exception) {
                    Log.w("FlowPilot", "camera failed to start", e)
                    report("The camera didn't start. Tap Try again, or enter the address instead.")
                }
            }, main)
        } catch (e: Exception) {
            Log.w("FlowPilot", "camera failed to start", e)
            report("The camera didn't start. Tap Try again, or enter the address instead.")
        }

        onDispose {
            disposed.set(true)
            catching { preview.previewStreamState.removeObserver(streamObserver) }
            catching { controller.cameraInfo?.cameraState?.removeObserver(cameraObserver) }
            catching { controller.clearImageAnalysisAnalyzer() }
            catching { controller.unbind() }
            catching { preview.controller = null }
            catching { scanner?.close() }
            worker.shutdown()
        }
    }

    // A camera that opened but sends no picture shows nothing but black and raises no error, so say what to check.
    LaunchedEffect(streaming) {
        if (streaming) { latestError.value(null); return@LaunchedEffect }
        delay(NO_PICTURE_AFTER_MS)
        latestError.value(
            "The camera isn't sending a picture. Check that Camera access is on in quick settings and no other app " +
                "is using the camera, then tap Try again. Or enter the address instead.",
        )
    }

    // Detach first in case a previous AndroidView still holds the remembered view.
    AndroidView(factory = { preview.also { (it.parent as? ViewGroup)?.removeView(it) } }, modifier = modifier)
}

private fun cameraErrorMessage(code: Int): String = when (code) {
    CameraState.ERROR_CAMERA_IN_USE, CameraState.ERROR_MAX_CAMERAS_IN_USE ->
        "Another app is using the camera. Close it, then tap Try again."
    CameraState.ERROR_CAMERA_DISABLED ->
        "The camera is turned off on this phone. Turn on Camera access in quick settings, then tap Try again."
    CameraState.ERROR_DO_NOT_DISTURB_MODE_ENABLED ->
        "Do Not Disturb is blocking the camera on this phone. Turn it off, then tap Try again."
    else -> "The camera stopped (error $code). Tap Try again, or enter the address instead."
}
