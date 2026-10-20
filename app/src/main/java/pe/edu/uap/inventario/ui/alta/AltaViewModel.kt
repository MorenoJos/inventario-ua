package pe.edu.uap.inventario.ui.alta

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.edu.uap.inventario.data.model.CategoriaEquipo
import pe.edu.uap.inventario.data.model.MetodoCaptura
import pe.edu.uap.inventario.data.model.NuevoEquipo
import pe.edu.uap.inventario.data.repository.AssetRepository
import pe.edu.uap.inventario.data.repository.AssetRepositoryImpl

class AltaViewModel(
    private val repository: AssetRepository = AssetRepositoryImpl()
) : ViewModel() {

    private val _uiState = MutableStateFlow(AltaUiState())
    val uiState: StateFlow<AltaUiState> = _uiState.asStateFlow()

    // ── Cambios que vienen del formulario ────────────────────────────────────
    fun onCategoria(valor: CategoriaEquipo) = _uiState.update { it.copy(categoria = valor) }
    fun onMarca(valor: String) = _uiState.update { it.copy(marca = valor) }
    fun onModelo(valor: String) = _uiState.update { it.copy(modelo = valor) }
    fun onSerie(valor: String) = _uiState.update { it.copy(serie = valor) }
    fun onUbicacion(valor: String) = _uiState.update { it.copy(ubicacion = valor) }
    fun onObservaciones(valor: String) = _uiState.update { it.copy(observaciones = valor) }
    fun onEstadoMonitor(valor: String) = _uiState.update { it.copy(estadoMonitor = valor) }

    // El código patrimonial son solo dígitos y máximo 8: se filtra al escribir.
    fun onCodigo(valor: String) =
        _uiState.update { it.copy(codigoPatrimonial = valor.filter(Char::isDigit).take(8)) }

    fun onHorasUso(valor: String) =
        _uiState.update { it.copy(horasUso = valor.filter(Char::isDigit).take(6)) }

    // ── Precarga desde la búsqueda ───────────────────────────────────────────
    // Si el usuario buscó algo que no existe y elige "Agregar", llegamos aquí con
    // lo que buscó. Misma regla que el escáner: 8 dígitos exactos = código
    // patrimonial; cualquier otra cosa = serie. Siempre se puede corregir a mano.
    fun precargar(identificador: String) {
        val id = identificador.trim()
        if (id.isEmpty()) return
        _uiState.update {
            if (Regex("^\\d{8}$").matches(id)) it.copy(codigoPatrimonial = id)
            else it.copy(serie = id.uppercase())
        }
    }

    // ── Guardar ──────────────────────────────────────────────────────────────
    fun guardar() {
        val actual = _uiState.value
        if (actual.guardando) return // evita doble toque

        // Primer intento: a partir de aquí la pantalla puede mostrar errores de campo
        _uiState.update { it.copy(intentoGuardar = true, error = null) }
        if (!actual.formularioValido) return

        _uiState.update { it.copy(guardando = true) }
        viewModelScope.launch {
            val nuevo = NuevoEquipo(
                categoria = actual.categoria,
                marca = actual.marca,
                modelo = actual.modelo,
                serie = actual.serie,
                codigoPatrimonial = actual.codigoPatrimonial,
                ubicacion = actual.ubicacion,
                observaciones = actual.observaciones,
                metodoCaptura = MetodoCaptura.MANUAL,
                // Solo se envía lo que corresponde a la categoría
                estado = if (actual.categoria == CategoriaEquipo.MONITORES) actual.estadoMonitor else null,
                horasUso = if (actual.categoria == CategoriaEquipo.PROYECTORES) actual.horasUso.toIntOrNull() else null
            )
            repository.registrar(nuevo)
                .onSuccess { resultado ->
                    _uiState.update { it.copy(guardando = false, registrado = resultado) }
                }
                .onFailure { e ->
                    // El repositorio ya entrega un mensaje claro y sin datos sensibles
                    _uiState.update {
                        it.copy(guardando = false, error = e.message ?: "No se pudo registrar el equipo")
                    }
                }
        }
    }

    fun limpiarError() = _uiState.update { it.copy(error = null) }

    // Para empezar un alta nueva después de un éxito o al cancelar
    fun reiniciar() {
        _uiState.value = AltaUiState()
    }
}