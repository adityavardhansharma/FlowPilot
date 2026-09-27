package dev.flowpilot.app.ui.pairing

import dev.flowpilot.core.sync.catching

import android.annotation.SuppressLint
import android.util.Log
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Full-bleed camera preview that reports every QR code it decodes. Nothing in here may crash the app: a camera
 * or scanner that fails to start is reported through [onError] so the person can type the address instead.
 */
@SuppressLint("UnsafeOptInUsageError")
@Composable
fun QrScanner(onCode: (String) -> Unit, onError: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val latest = rememberUpdatedState(onCode)
    val latestError = rememberUpdatedState(onError)
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
        val worker = Executors.newSingleThreadExecutor()
        // CameraX may still post a frame after we let go; drop it instead of throwing on a shut-down executor.
        val analysisExecutor = Executor { task ->
            if (disposed.get()) return@Executor
            try { worker.execute(task) } catch (_: RejectedExecutionException) {}
        }
        var scanner: BarcodeScanner? = null
        var provider: ProcessCameraProvider? = null
        val usePreview = Preview.Builder().build()
        val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()

        fun fail(e: Throwable) {
            Log.w("FlowPilot", "camera failed", e)
            if (!disposed.get()) latestError.value("The camera didn't start. Enter the address instead.")
        }

        try {
            val s = BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
            scanner = s
            usePreview.surfaceProvider = preview.surfaceProvider
            analysis.setAnalyzer(analysisExecutor) { proxy ->
                val media = proxy.image
                if (media == null || disposed.get() || !busy.compareAndSet(false, true)) { proxy.close(); return@setAnalyzer }
                try {
                    val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                    s.process(image)
                        .addOnSuccessListener(main) { codes ->
                            if (disposed.get()) return@addOnSuccessListener
                            codes.firstNotNullOfOrNull { it.rawValue }?.let { value -> latest.value(value) }
                        }
                        .addOnCompleteListener(main) { proxy.close(); busy.set(false) }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Log.w("FlowPilot", "frame skipped", e)
                    proxy.close()
                    busy.set(false)
                }
            }
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                if (disposed.get()) return@addListener
                try {
                    val p = future.get()
                    val selector = when {
                        p.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
                        p.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                        else -> { latestError.value("This phone has no camera. Enter the address instead."); return@addListener }
                    }
                    p.unbind(usePreview, analysis)
                    p.bindToLifecycle(owner, selector, usePreview, analysis)
                    provider = p
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    fail(e)
                }
            }, main)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            fail(e)
        }

        onDispose {
            disposed.set(true)
            catching { analysis.clearAnalyzer() }
            catching { provider?.unbind(usePreview, analysis) }
            catching { scanner?.close() }
            worker.shutdown()
        }
    }

    // Detach first in case a previous AndroidView still holds the remembered view.
    AndroidView(factory = { preview.also { (it.parent as? ViewGroup)?.removeView(it) } }, modifier = modifier)
}
