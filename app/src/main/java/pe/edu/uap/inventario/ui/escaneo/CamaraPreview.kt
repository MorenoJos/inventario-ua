package pe.edu.uap.inventario.ui.escaneo

import android.Manifest
import android.annotation.SuppressLint
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CamaraPreview(
    modifier: Modifier = Modifier,
    onCodigoDetectado: (String) -> Unit = {}
) {
    val permiso = rememberPermissionState(Manifest.permission.CAMERA)

    LaunchedEffect(Unit) {
        if (!permiso.status.isGranted) permiso.launchPermissionRequest()
    }

    Box(
        modifier = modifier.clipToBounds(),
        contentAlignment = Alignment.Center
    ) {
        if (permiso.status.isGranted) {
            VistaCamara(Modifier.matchParentSize(), onCodigoDetectado)
        } else {
            Button(onClick = { permiso.launchPermissionRequest() }) {
                Text("Permitir cámara")
            }
        }
    }
}

// Guarda el ultimo codigo leido y cuando, para el anti-rebote del analizador
private class AntiRebote {
    var ultimoCodigo: String? = null
    var ultimoMillis: Long = 0L
}

@Composable
private fun VistaCamara(modifier: Modifier, onCodigoDetectado: (String) -> Unit) {
    val lifecycleOwner = LocalContext.current as LifecycleOwner
    val scanner = remember { BarcodeScanning.getClient() }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val antiRebote = remember { AntiRebote() }

    DisposableEffect(Unit) {
        onDispose {
            analysisExecutor.shutdown()
            scanner.close()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener({
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                    procesarFrame(imageProxy, scanner) { codigo ->
                        val ahora = System.currentTimeMillis()
                        val esNuevo = codigo != antiRebote.ultimoCodigo ||
                                (ahora - antiRebote.ultimoMillis) > 3000
                        if (esNuevo) {
                            antiRebote.ultimoCodigo = codigo
                            antiRebote.ultimoMillis = ahora
                            onCodigoDetectado(codigo)
                        }
                    }
                }

                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        }
    )
}

@SuppressLint("UnsafeOptInUsageError")
private fun procesarFrame(
    imageProxy: ImageProxy,
    scanner: BarcodeScanner,
    onDetectado: (String) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        imageProxy.close()
        return
    }
    val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    scanner.process(inputImage)
        .addOnSuccessListener { barcodes ->
            val valor = barcodes.firstOrNull()?.rawValue
            if (!valor.isNullOrBlank()) {
                onDetectado(valor)
            }
        }
        .addOnCompleteListener {
            imageProxy.close()
        }
}