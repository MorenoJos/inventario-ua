package pe.edu.uap.inventario.ui.escaneo

import android.Manifest
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CamaraPreview(modifier: Modifier = Modifier) {
    val permiso = rememberPermissionState(Manifest.permission.CAMERA)

    // Pide el permiso solo al abrir la pantalla; si ya está concedido, no hace nada
    LaunchedEffect(Unit) {
        if (!permiso.status.isGranted) permiso.launchPermissionRequest()
    }

    Box(
        modifier = modifier.clipToBounds(),
        contentAlignment = Alignment.Center
    ) {
        if (permiso.status.isGranted) {
            VistaCamara(Modifier.matchParentSize())
        } else {
            Button(onClick = { permiso.launchPermissionRequest() }) {
                Text("Permitir cámara")
            }
        }
    }
}

@Composable
private fun VistaCamara(modifier: Modifier) {
    val lifecycleOwner = LocalContext.current as LifecycleOwner

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                // COMPATIBLE respeta el recorte del recuadro (el modo por defecto lo ignora)
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener({
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview
                )
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        }
    )
}