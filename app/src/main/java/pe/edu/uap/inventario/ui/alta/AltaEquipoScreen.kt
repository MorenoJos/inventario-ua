package pe.edu.uap.inventario.ui.alta

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import pe.edu.uap.inventario.data.model.CategoriaEquipo

// Pantalla completa de "Agregar equipo".
// No guarda estado propio: lee el AltaUiState del ViewModel y le avisa de cada cambio.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AltaEquipoScreen(
    viewModel: AltaViewModel,
    onCerrar: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    // Los errores de campo solo se muestran después del primer intento de guardar
    val errorEstado = if (state.intentoGuardar) state.errorEstado else null
    val errorObservaciones = if (state.intentoGuardar) state.errorObservaciones else null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Agregar equipo") },
                navigationIcon = {
                    IconButton(onClick = onCerrar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Categoría ───────────────────────────────────────────────────
            Text("Categoría", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CategoriaEquipo.entries.forEach { categoria ->
                    FilterChip(
                        selected = state.categoria == categoria,
                        onClick = { viewModel.onCategoria(categoria) },
                        label = { Text(categoria.etiqueta) }
                    )
                }
            }

            // ── Datos del equipo ────────────────────────────────────────────
            Campo("Marca", state.marca, viewModel::onMarca, mayusculas = true)
            Campo("Modelo", state.modelo, viewModel::onModelo, mayusculas = true)
            Campo("Serie", state.serie, viewModel::onSerie, mayusculas = true)
            Campo(
                etiqueta = "Código patrimonial",
                valor = state.codigoPatrimonial,
                onCambio = viewModel::onCodigo,
                teclado = KeyboardType.Number,
                ayuda = "Solo dígitos. Se completa con ceros hasta 8."
            )
            Campo("Ubicación", state.ubicacion, viewModel::onUbicacion, ayuda = "Ej. Lab 3, Almacén 5C")

            // ── Campos propios de cada categoría ────────────────────────────
            if (state.categoria == CategoriaEquipo.MONITORES) {
                Text("Estado del monitor", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("NUEVO", "USADO").forEach { opcion ->
                        FilterChip(
                            selected = state.estadoMonitor == opcion,
                            onClick = { viewModel.onEstadoMonitor(opcion) },
                            label = { Text(opcion) }
                        )
                    }
                }
                if (errorEstado != null) {
                    Text(errorEstado, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (state.categoria == CategoriaEquipo.PROYECTORES) {
                Campo(
                    etiqueta = "Horas de uso",
                    valor = state.horasUso,
                    onCambio = viewModel::onHorasUso,
                    teclado = KeyboardType.Number,
                    ayuda = "Opcional"
                )
            }

            Campo(
                etiqueta = "Observaciones",
                valor = state.observaciones,
                onCambio = viewModel::onObservaciones,
                error = errorObservaciones,
                minLineas = 2
            )

            // ── Error del servidor / red ────────────────────────────────────
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            // ── Guardar ─────────────────────────────────────────────────────
            Button(
                onClick = viewModel::guardar,
                enabled = !state.guardando,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.guardando) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Guardar equipo")
                }
            }
        }
    }

    // ── Confirmación de alta exitosa ────────────────────────────────────────
    if (state.registrado != null) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Equipo registrado") },
            text = { Text("Se guardó en el inventario y quedó anotado en el Kardex.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.reiniciar()
                    onCerrar()
                }) { Text("Listo") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::reiniciar) { Text("Agregar otro") }
            }
        )
    }
}

// Campo de texto reutilizable: evita repetir la misma configuración 6 veces.
@Composable
private fun Campo(
    etiqueta: String,
    valor: String,
    onCambio: (String) -> Unit,
    teclado: KeyboardType = KeyboardType.Text,
    mayusculas: Boolean = false,
    ayuda: String? = null,
    error: String? = null,
    minLineas: Int = 1
) {
    val textoApoyo = error ?: ayuda
    OutlinedTextField(
        value = valor,
        onValueChange = onCambio,
        label = { Text(etiqueta) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = minLineas == 1,
        minLines = minLineas,
        isError = error != null,
        supportingText = if (textoApoyo != null) {
            { Text(textoApoyo) }
        } else null,
        keyboardOptions = KeyboardOptions(
            keyboardType = teclado,
            capitalization = if (mayusculas) KeyboardCapitalization.Characters else KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Next
        )
    )
}