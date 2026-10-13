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
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

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

// Bookkeeping del analizador de barcode: evita disparar la misma busqueda en frames seguidos
private class AntiRebote {
    var ultimoCodigo: String? = null
    var ultimoMillis: Long = 0L
}

// Controla cada cuanto se intenta OCR (es mas pesado que el barcode) y permite forzar el proximo intento
private class OcrThrottle(private val intervaloMs: Long = 1000L) {
    @Volatile private var ultimoIntentoMillis = 0L
    @Volatile private var forzado = false

    fun debeIntentar(): Boolean =
        forzado || (System.currentTimeMillis() - ultimoIntentoMillis) >= intervaloMs

    fun marcarIntento() {
        ultimoIntentoMillis = System.currentTimeMillis()
        forzado = false
    }

    fun forzarProximoIntento() {
        forzado = true
    }
}

@Composable
private fun VistaCamara(modifier: Modifier, onCodigoDetectado: (String) -> Unit) {
    val lifecycleOwner = LocalContext.current as LifecycleOwner
    val barcodeScanner = remember { BarcodeScanning.getClient() }
    val textRecognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val antiRebote = remember { AntiRebote() }
    val ocrThrottle = remember { OcrThrottle() }

    DisposableEffect(Unit) {
        onDispose {
            analysisExecutor.shutdown()
            barcodeScanner.close()
            textRecognizer.close()
        }
    }

    fun codigoDetectado(valor: String) {
        val limpio = valor.trim()
        if (limpio.isEmpty()) return
        val ahora = System.currentTimeMillis()
        val esNuevo = limpio != antiRebote.ultimoCodigo ||
                (ahora - antiRebote.ultimoMillis) > 3000
        if (esNuevo) {
            antiRebote.ultimoCodigo = limpio
            antiRebote.ultimoMillis = ahora
            onCodigoDetectado(limpio)
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.matchParentSize(),
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
                        procesarFrame(
                            imageProxy = imageProxy,
                            barcodeScanner = barcodeScanner,
                            textRecognizer = textRecognizer,
                            ocrThrottle = ocrThrottle,
                            onBarcodeDetectado = ::codigoDetectado,
                            onTextoDetectado = ::codigoDetectado
                        )
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

        Button(
            onClick = { ocrThrottle.forzarProximoIntento() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
            )
        ) {
            Text("Leer texto")
        }
    }
}

@SuppressLint("UnsafeOptInUsageError")
private fun procesarFrame(
    imageProxy: ImageProxy,
    barcodeScanner: BarcodeScanner,
    textRecognizer: TextRecognizer,
    ocrThrottle: OcrThrottle,
    onBarcodeDetectado: (String) -> Unit,
    onTextoDetectado: (String) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        imageProxy.close()
        return
    }
    val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    val intentarOcr = ocrThrottle.debeIntentar()
    val tareasPendientes = AtomicInteger(if (intentarOcr) 2 else 1)

    fun cerrarSiTerminado() {
        if (tareasPendientes.decrementAndGet() == 0) {
            imageProxy.close()
        }
    }

    barcodeScanner.process(inputImage)
        .addOnSuccessListener { barcodes ->
            val valor = barcodes.firstOrNull()?.rawValue
            if (!valor.isNullOrBlank()) onBarcodeDetectado(valor)
        }
        .addOnCompleteListener { cerrarSiTerminado() }

    if (intentarOcr) {
        ocrThrottle.marcarIntento()
        textRecognizer.process(inputImage)
            .addOnSuccessListener { texto ->
                val candidato = texto.textBlocks
                    .flatMap { it.lines }
                    .map { it.text.replace(" ", "").trim() }
                    .firstOrNull { esIdentificadorValido(it) }
                if (candidato != null) onTextoDetectado(candidato)
            }
            .addOnCompleteListener { cerrarSiTerminado() }
    }
}

private val REGEX_CODIGO_PATRIMONIAL = Regex("^\\d{8}$")
private val REGEX_SERIE = Regex("^(?=.*\\d)(?=.*[A-Za-z])[A-Za-z0-9-]{6,20}$")

private fun esIdentificadorValido(texto: String): Boolean =
    REGEX_CODIGO_PATRIMONIAL.matches(texto) || REGEX_SERIE.matches(texto)