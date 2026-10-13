package pe.edu.uap.inventario

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import pe.edu.uap.inventario.ui.login.LoginScreen
import pe.edu.uap.inventario.ui.login.LoginViewModel
import pe.edu.uap.inventario.ui.theme.UAInventarioTheme

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import pe.edu.uap.inventario.ui.busqueda.BusquedaUiState
import pe.edu.uap.inventario.ui.busqueda.BusquedaViewModel

import pe.edu.uap.inventario.data.model.EquipoDetalle
import pe.edu.uap.inventario.data.model.toDetalle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import pe.edu.uap.inventario.ui.busqueda.EquipoFila

import pe.edu.uap.inventario.ui.escaneo.CamaraPreview

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UAInventarioTheme {
                MainApp()
            }
        }
    }
}

@Composable
fun MainApp(viewModel: LoginViewModel = viewModel()) {
    val sessionReady by viewModel.sessionReady.collectAsState()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()

    if (!sessionReady) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else if (isLoggedIn) {
        HomeScreen(
            userName = viewModel.getCurrentUserName() ?: "Usuario",
            onLogout = {
                viewModel.logout()
            }
        )
    } else {
        LoginScreen(
            onLoginSuccess = {
                // El ViewModel ya actualiza isLoggedIn automáticamente
            },
            viewModel = viewModel
        )
    }
}

@Composable
fun HomeScreen(
    userName: String,
    onLogout: () -> Unit,
    busquedaViewModel: BusquedaViewModel = viewModel()
) {
    var identificador by remember { mutableStateOf("") }
    val uiState by busquedaViewModel.uiState.collectAsState()
    val items by busquedaViewModel.items.collectAsState()
    val isLoading = uiState is BusquedaUiState.Loading

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    BasicText(
                        text = "¡Bienvenido, $userName!",
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        maxLines = 1,
                        autoSize = TextAutoSize.StepBased(
                            minFontSize = 14.sp,
                            maxFontSize = MaterialTheme.typography.titleLarge.fontSize
                        )
                    )
                    Text(
                        "Sesión activa en Almacén 5C",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onLogout) {
                    Text("Cerrar sesión")
                }
            }

            Spacer(Modifier.height(12.dp))

            // Reserva para cámara (Paso 8.2)
            CamaraPreview(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline),
                onCodigoDetectado = { codigo ->
                    identificador = codigo
                    busquedaViewModel.buscar(codigo)
                }
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = identificador,
                onValueChange = { identificador = it },
                label = { Text("Código o serie") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        busquedaViewModel.buscar(identificador)
                        identificador = ""
                    }
                )
            )

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { busquedaViewModel.limpiarLista() },
                    enabled = items.isNotEmpty() && !isLoading
                ) {
                    Text("Limpiar")
                }
                Button(
                    onClick = {
                        busquedaViewModel.buscar(identificador)
                        identificador = ""
                    },
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (isLoading) "Buscando..." else "Buscar")
                }
            }

            if (uiState is BusquedaUiState.Error) {
                Spacer(Modifier.height(8.dp))
                Text(
                    (uiState as BusquedaUiState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                "Equipos (${items.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))

            val hScroll = rememberScrollState()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(hScroll)
                    .verticalScroll(rememberScrollState())
                    .border(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                TablaHeader()
                items.forEach { fila ->
                    TablaRow(fila)
                }
                if (items.isEmpty()) {
                    Text(
                        "Aún no hay equipos. Busca un código o serie.",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun TablaHeader() {
    Row(
        Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .height(40.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Celda("Estado", true)
        Celda("Categoría", true)
        Celda("Marca", true)
        Celda("Modelo", true)
        Celda("Serie", true)
        Celda("Cód. patr.", true)
    }
}

@Composable
private fun TablaRow(fila: EquipoFila) {
    val bg = if (fila.estado == "RAE")
        MaterialTheme.colorScheme.errorContainer
    else
        Color.Transparent

    Row(
        Modifier
            .background(bg)
            .height(40.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Celda(fila.estado)
        Celda(fila.categoria)
        Celda(fila.marca)
        Celda(fila.modelo)
        Celda(fila.serie)
        Celda(fila.codigo)
    }
}

@Composable
private fun RowScope.Celda(texto: String, header: Boolean = false) {
    Text(
        text = texto,
        modifier = Modifier
            .width(110.dp)
            .padding(horizontal = 6.dp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        fontWeight = if (header) FontWeight.Bold else FontWeight.Normal,
        fontSize = 12.sp
    )
}