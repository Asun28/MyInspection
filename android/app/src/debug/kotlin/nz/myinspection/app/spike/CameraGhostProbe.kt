package nz.myinspection.app.spike

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.camera.view.TransformExperimental
import androidx.camera.view.transform.CoordinateTransform
import androidx.camera.view.transform.FileTransformFactory
import androidx.camera.view.transform.OutputTransform
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Debug probe: history is the preceding capture from this rear camera and shared viewport. */
@OptIn(markerClass = [TransformExperimental::class])
@Composable
fun CameraGhostProbe(onResult: (String) -> Unit = {}) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val latestResult by rememberUpdatedState(onResult)
    val main = remember { ContextCompat.getMainExecutor(context) }
    val worker = remember { Executors.newSingleThreadExecutor() }
    val alive = remember { AtomicBoolean(true) }
    val cameraFuture = remember { ProcessCameraProvider.getInstance(context) }
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED)
    }
    var previewSize by remember { mutableStateOf(IntSize.Zero) }
    var capture by remember { mutableStateOf<ImageCapture?>(null) }
    var streaming by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var photo by remember { mutableStateOf<GhostPhoto?>(null) }
    var showGhost by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf(if (granted) "Camera permission granted; capture a history photo."
        else "Grant camera access, then capture a history photo.") }
    fun report(value: String) { message = value; latestResult(value) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        report(if (it) "Camera permission granted; capture a history photo."
            else "Camera permission denied; overlay feasibility remains unverified.")
    }

    DisposableEffect(Unit) {
        val layout = View.OnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            previewSize = IntSize(view.width, view.height)
        }
        previewView.addOnLayoutChangeListener(layout)
        onDispose {
            alive.set(false)
            previewView.removeOnLayoutChangeListener(layout)
            worker.shutdown()
        }
    }
    DisposableEffect(photo) {
        val ownedPhoto = photo
        onDispose { ownedPhoto?.bitmap?.recycle() }
    }
    DisposableEffect(granted, previewSize, owner) {
        var disposed = false
        var provider: ProcessCameraProvider? = null
        val preview = Preview.Builder().build()
        val still = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
        val observer = Observer<PreviewView.StreamState> {
            streaming = it == PreviewView.StreamState.STREAMING
        }
        if (granted && previewSize.width > 0 && previewSize.height > 0) {
            previewView.previewStreamState.observe(owner, observer)
            cameraFuture.addListener({
                if (!disposed) try {
                    val viewport = requireNotNull(previewView.viewPort) { "Viewport is not ready" }
                    preview.targetRotation = previewView.display.rotation
                    still.targetRotation = previewView.display.rotation
                    preview.setSurfaceProvider(previewView.surfaceProvider)
                    provider = cameraFuture.get()
                    provider!!.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA,
                        UseCaseGroup.Builder().setViewPort(viewport)
                            .addUseCase(preview).addUseCase(still).build())
                    capture = still
                } catch (failure: Exception) {
                    report("Camera bind failed: ${failure.message}; feasibility unverified.")
                }
            }, main)
        }
        onDispose {
            disposed = true
            capture = null
            streaming = false
            previewView.previewStreamState.removeObserver(observer)
            provider?.unbind(preview, still)
        }
    }

    val currentPhoto = photo
    val ghost = remember(currentPhoto, streaming, previewSize) {
        if (streaming && currentPhoto != null) previewView.outputTransform?.let {
            GhostPainter(currentPhoto, it, Size(previewSize.width.toFloat(), previewSize.height.toFloat()))
        } else null
    }
    Column {
        Text("Ghost overlay · CameraX 1.5.3 · alpha 0.30")
        if (!granted) Button(onClick = { permission.launch(Manifest.permission.CAMERA) }) {
            Text("Grant camera access")
        }
        Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f).clipToBounds()) {
            AndroidView(factory = { previewView }, modifier = Modifier.matchParentSize())
            if (showGhost && ghost != null) Image(ghost, "History ghost", Modifier.matchParentSize(),
                contentScale = ContentScale.Crop, alpha = 0.30f)
        }
        Button(enabled = capture != null && streaming && !busy, onClick = {
            val camera = capture ?: return@Button
            busy = true
            try {
                val directory = File(requireNotNull(context.getExternalFilesDir(null)), "spike")
                check(directory.isDirectory || directory.mkdirs()) { "Cannot create spike directory" }
                val file = File(directory, "ghost-${System.currentTimeMillis()}.jpg")
                camera.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), main,
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                            if (!alive.get()) return
                            worker.execute {
                                val loaded = runCatching { readGhostPhoto(file) }
                                main.execute {
                                    if (!alive.get()) loaded.getOrNull()?.bitmap?.recycle()
                                    else {
                                        busy = false
                                        loaded.fold(onSuccess = {
                                            photo = it
                                            report("Saved/read ${file.absolutePath}; buffer ${it.width}x${it.height}; " +
                                                "decoded ${it.bitmap.width}x${it.bitmap.height}; preview " +
                                                "${previewSize.width}x${previewSize.height}. Visual alignment pending.")
                                        }, onFailure = { report("Photo readback failed: ${it.message}") })
                                    }
                                }
                            }
                        }
                        override fun onError(exception: ImageCaptureException) {
                            if (alive.get()) { busy = false; report("Capture failed: ${exception.message}") }
                        }
                    })
            } catch (failure: Exception) {
                busy = false
                report("Capture failed: ${failure.message}")
            }
        }) { Text(if (busy) "Saving…" else "Capture history photo") }
        if (currentPhoto != null) {
            Button(onClick = { showGhost = !showGhost }) { Text(if (showGhost) "Hide ghost" else "Show ghost") }
            Text(if (ghost == null) "Preview transform unavailable; alignment unverified." else
                "Move away and return: compare outlines with the ghost. Readback below must stay undistorted.")
            val readback = remember(currentPhoto) {
                GhostPainter(currentPhoto, currentPhoto.oriented, currentPhoto.orientedSize)
            }
            Image(readback, "Saved photo readback", Modifier.fillMaxWidth().height(180.dp),
                contentScale = ContentScale.Fit)
        }
        Text(message)
    }
}

@OptIn(markerClass = [TransformExperimental::class])
private class GhostPhoto(val bitmap: Bitmap, val source: OutputTransform, val oriented: OutputTransform,
    val width: Int, val height: Int, val orientedSize: Size) {
    fun matrixTo(target: OutputTransform) = Matrix().apply {
        CoordinateTransform(source, target).transform(this)
        // FileTransformFactory uses full JPEG pixels; decoding samples a smaller bitmap.
        preScale(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
    }
}

@OptIn(markerClass = [TransformExperimental::class])
private fun readGhostPhoto(file: File): GhostPhoto {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Saved JPEG has no image dimensions" }
    val options = BitmapFactory.Options().apply { inSampleSize = 1 }
    while (maxOf(bounds.outWidth, bounds.outHeight) / options.inSampleSize > 1600) options.inSampleSize *= 2
    val source = FileTransformFactory().getOutputTransform(file)
    val oriented = FileTransformFactory().apply { isUsingExifOrientation = true }.getOutputTransform(file)
    val rect = RectF(0f, 0f, bounds.outWidth.toFloat(), bounds.outHeight.toFloat())
    CoordinateTransform(source, oriented).mapRect(rect)
    val bitmap = requireNotNull(BitmapFactory.decodeFile(file.absolutePath, options)) { "JPEG decode failed" }
    return GhostPhoto(bitmap, source, oriented, bounds.outWidth, bounds.outHeight, Size(rect.width(), rect.height()))
}

@OptIn(markerClass = [TransformExperimental::class])
private class GhostPainter(private val photo: GhostPhoto, target: OutputTransform,
    override val intrinsicSize: Size) : Painter() {
    private val matrix = photo.matrixTo(target)
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    override fun DrawScope.onDraw() {
        val canvas = drawContext.canvas.nativeCanvas
        val saved = canvas.save()
        canvas.scale(size.width / intrinsicSize.width, size.height / intrinsicSize.height)
        canvas.concat(matrix)
        canvas.drawBitmap(photo.bitmap, 0f, 0f, paint)
        canvas.restoreToCount(saved)
    }
}
