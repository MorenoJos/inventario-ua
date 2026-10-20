package pe.edu.uap.inventario

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import pe.edu.uap.inventario.ui.busqueda.EquipoFila

import pe.edu.uap.inventario.ui.escaneo.CamaraPreview

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize

import pe.edu.uap.inventario.ui.alta.AltaEquipoScreen
import pe.edu.uap.inventario.ui.alta.AltaViewModel

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

    // ViewModel del formulario de alta. Nombre completo en la llamada porque aquí
    // el parámetro `viewModel` (el de login) taparía a la función viewModel().
    val altaViewModel = androidx.lifecycle.viewmodel.compose.viewModel<AltaViewModel>()
    // ¿Se está mostrando el formulario de alta? rememberSaveable para que sobreviva
    // a una rotación de pantalla.
    var mostrandoAlta by rememberSaveable { mutableStateOf(false) }

    // Mismo BusquedaViewModel que usa HomeScreen (ambos están ligados a la Activity).
    val busquedaViewModel = androidx.lifecycle.viewmodel.compose.viewModel<BusquedaViewModel>()
    val altaState by altaViewModel.uiState.collectAsState()

    // Cuando un alta sale bien, se vuelve a buscar el equipo recién agregado para que
    // aparezca en la lista como confirmación. Se usa el código (relleno a 8 dígitos,
    // como se guarda en el Sheet) o, si no hay código, la serie.
    LaunchedEffect(altaState.registrado) {
        if (altaState.registrado != null) {
            val serie = altaState.serie.trim()
            val id = when {
                altaState.codigoPatrimonial.isNotBlank() -> altaState.codigoPatrimonial.padStart(8, '0')
                serie.isNotBlank() && !serie.equals("S/N", ignoreCase = true) -> serie
                else -> ""
            }
            if (id.isNotEmpty()) busquedaViewModel.refrescarTrasAlta(id)
        }
    }

    if (!sessionReady) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else if (isLoggedIn) {
        if (mostrandoAlta) {
            // El botón "atrás" del teléfono vuelve a la búsqueda en vez de salir de la app
            BackHandler { mostrandoAlta = false }
            AltaEquipoScreen(
                viewModel = altaViewModel,
                onCerrar = { mostrandoAlta = false }
            )
        } else {
            HomeScreen(
                userName = viewModel.getCurrentUserName() ?: "Usuario",
                onLogout = {
                    mostrandoAlta = false
                    viewModel.logout()
                },
                onAgregarEquipo = { identificador ->
                    // Formulario siempre limpio; si había algo escrito en el buscador, se precarga
                    altaViewModel.reiniciar()
                    altaViewModel.precargar(identificador)
                    mostrandoAlta = true
                }
            )
        }
    } else {
        LoginScreen(
            onLoginSuccess = {
                // El ViewModel ya actualiza isLoggedIn automáticamente
            },
            viewModel = viewModel
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    userName: String,
    onLogout: () -> Unit,
    onAgregarEquipo: (String) -> Unit,
    busquedaViewModel: BusquedaViewModel = viewModel()
) {
    var identificador by remember { mutableStateOf("") }
    val uiState by busquedaViewModel.uiState.collectAsState()
    val items by busquedaViewModel.items.collectAsState()
    val isLoading = uiState is BusquedaUiState.Loading
    var filaSeleccionada by remember { mutableStateOf<EquipoFila?>(null) }
    val sheetState = rememberModalBottomSheetState()

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

            Spacer(Modifier.height(8.dp))

            // Abre el formulario de alta. Si hay algo escrito en el buscador, llega precargado.
            FilledTonalButton(
                onClick = { onAgregarEquipo(identificador) },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Agregar equipo")
            }

            when (val estado = uiState) {
                is BusquedaUiState.Error -> {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        estado.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                is BusquedaUiState.NoEncontrado -> {
                    // No existe en el inventario: se ofrece agregarlo con lo que se buscó ya precargado
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "No encontrado: ${estado.identificador}",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        TextButton(onClick = { onAgregarEquipo(estado.identificador) }) {
                            Text("Agregar")
                        }
                    }
                }
                else -> Unit
            }

            Spacer(Modifier.height(12.dp))

            Text(
                "Equipos (${items.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))

            if (items.isEmpty()) {
                Text(
                    "Aún no hay equipos. Busca un código o serie.",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(items) { fila ->
                        EquipoCard(
                            fila = fila,
                            seleccionada = filaSeleccionada == fila,
                            onClick = {
                                filaSeleccionada = fila
                            }
                        )
                    }
                }
            }
        }
    }

    val filaActual = filaSeleccionada
    if (filaActual != null) {
        ModalBottomSheet(
            onDismissRequest = { filaSeleccionada = null },
            sheetState = sheetState
        ) {
            EquipoDetalleContenido(filaActual)
        }
    }
}

@Composable
private fun EquipoCard(
    fila: EquipoFila,
    seleccionada: Boolean,
    onClick: () -> Unit
) {
    val esRae = fila.estado == "RAE"
    val colorFondo = if (esRae) MaterialTheme.colorScheme.errorContainer
    else MaterialTheme.colorScheme.surfaceVariant
    val colorBadge = if (esRae) MaterialTheme.colorScheme.error
    else MaterialTheme.colorScheme.primary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(colorFondo, MaterialTheme.shapes.small)
            .then(
                if (seleccionada)
                    Modifier.border(2.dp, colorBadge, MaterialTheme.shapes.small)
                else Modifier
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = fila.estado,
            color = colorBadge,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.extraSmall)
                .padding(horizontal = 6.dp, vertical = 3.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "${fila.marca} ${fila.modelo}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp
            )
            Text(
                fila.categoria,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            fila.codigo,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EquipoDetalleContenido(fila: EquipoFila) {
    val esRae = fila.estado == "RAE"
    // Verde para OPERATIVO, rojo para RAE. Usamos colores fijos (no los del tema)
    // porque el significado operativo/dado-de-baja debe verse igual sin importar
    // si el teléfono está en modo claro u oscuro.
    val colorBadge = if (esRae) Color(0xFFD32F2F) else Color(0xFF2E7D32)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp)
    ) {
        Box(
            modifier = Modifier
                .background(colorBadge, MaterialTheme.shapes.medium)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = fila.estado,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        }

        Spacer(Modifier.height(16.dp))

        Text(
            "${fila.marca} ${fila.modelo}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            fila.categoria,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(16.dp))

        CampoDetalle("Código patrimonial", fila.codigo)
        CampoDetalle("Serie", fila.serie)
        fila.extras.forEach { (etiqueta, valor) ->
            CampoDetalle(etiqueta, valor)
        }
    }
}

@Composable
private fun CampoDetalle(etiqueta: String, valor: String) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(
            etiqueta,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            valor,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}