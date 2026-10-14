package pe.edu.uap.inventario.ui.escaneo

import android.Manifest
import android.annotation.SuppressLint
import android.graphics.Rect
import androidx.camera.core.Camera
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

import android.util.Size
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy

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

// Evita disparar la misma busqueda en frames seguidos
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
    val haptico = LocalHapticFeedback.current
    val barcodeScanner = remember { BarcodeScanning.getClient() }
    val textRecognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val antiRebote = remember { AntiRebote() }
    val ocrThrottle = remember { OcrThrottle() }

    // Cámara enlazada: sirve para encender y apagar la linterna
    var camara by remember { mutableStateOf<Camera?>(null) }
    var linternaEncendida by remember { mutableStateOf(false) }
    val tieneLinterna = camara?.cameraInfo?.hasFlashUnit() == true

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
            // Vibración corta: avisa que leyó sin tener que mirar la pantalla
            haptico.performHapticFeedback(HapticFeedbackType.LongPress)
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

                    // 1920x1080: con el valor por defecto (640x480) el código y los dígitos salen borrosos
                    val seleccionResolucion = ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(
                                Size(1920, 1080),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                            )
                        )
                        .build()

                    val analysis = ImageAnalysis.Builder()
                        .setResolutionSelector(seleccionResolucion)
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
                    // Al enlazar la cámara la linterna parte apagada
                    linternaEncendida = false
                    camara = provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                    )
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            }
        )

        if (tieneLinterna) {
            Button(
                onClick = {
                    linternaEncendida = !linternaEncendida
                    camara?.cameraControl?.enableTorch(linternaEncendida)
                },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (linternaEncendida)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                )
            ) {
                Text("luz")
            }
        }

        Button(
            onClick = { ocrThrottle.forzarProximoIntento() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
            )
        ) {
            Text("txt")
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

    // Tamaño del frame ya rotado: ML Kit devuelve las posiciones en ese sistema
    val rotacion = imageProxy.imageInfo.rotationDegrees
    val girado = rotacion == 90 || rotacion == 270
    val ancho = if (girado) imageProxy.height else imageProxy.width
    val alto = if (girado) imageProxy.width else imageProxy.height

    val inputImage = InputImage.fromMediaImage(mediaImage, rotacion)
    val intentarOcr = ocrThrottle.debeIntentar()
    val tareasPendientes = AtomicInteger(if (intentarOcr) 2 else 1)
    val barcodeEncontrado = AtomicBoolean(false)

    fun cerrarSiTerminado() {
        if (tareasPendientes.decrementAndGet() == 0) {
            imageProxy.close()
        }
    }

    barcodeScanner.process(inputImage)
        .addOnSuccessListener { barcodes ->
            // De todos los códigos en cuadro, el válido más cercano al centro
            val valor = barcodes
                .filter { esCodigoValido(it.rawValue?.trim().orEmpty()) }
                .minByOrNull { distanciaAlCentro(it.boundingBox, ancho, alto) }
                ?.rawValue
                ?.trim()
            if (valor != null) {
                barcodeEncontrado.set(true)
                onBarcodeDetectado(valor)
            }
        }
        .addOnCompleteListener { cerrarSiTerminado() }

    if (intentarOcr) {
        ocrThrottle.marcarIntento()
        textRecognizer.process(inputImage)
            .addOnSuccessListener { texto ->
                // Si el barcode ya se leyó en este frame, el OCR no hace falta
                if (barcodeEncontrado.get()) return@addOnSuccessListener

                // Solo 8 dígitos exactos; de varios, el más cercano al centro
                val candidato = texto.textBlocks
                    .flatMap { it.lines }
                    .filter { REGEX_8_DIGITOS.containsMatchIn(it.text) }
                    .minByOrNull { distanciaAlCentro(it.boundingBox, ancho, alto) }
                    ?.let { REGEX_8_DIGITOS.find(it.text)?.value }
                if (candidato != null) onTextoDetectado(candidato)
            }
            .addOnCompleteListener { cerrarSiTerminado() }
    }
}

private fun distanciaAlCentro(caja: Rect?, ancho: Int, alto: Int): Float {
    if (caja == null) return Float.MAX_VALUE
    val dx = caja.exactCenterX() - ancho / 2f
    val dy = caja.exactCenterY() - alto / 2f
    return dx * dx + dy * dy
}

// Código patrimonial dentro de una línea de texto (8 dígitos sueltos, no parte de un número más largo)
private val REGEX_8_DIGITOS = Regex("(?<!\\d)\\d{8}(?!\\d)")

// Para barcode: código patrimonial exacto o serie alfanumérica (letras y números mezclados)
private val REGEX_CODIGO_8 = Regex("^\\d{8}$")
private val REGEX_SERIE = Regex("^(?=.*\\d)(?=.*[A-Za-z])[A-Za-z0-9-]{6,20}$")

private fun esCodigoValido(texto: String): Boolean =
    REGEX_CODIGO_8.matches(texto) || REGEX_SERIE.matches(texto)