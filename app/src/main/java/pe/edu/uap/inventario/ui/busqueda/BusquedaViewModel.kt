package pe.edu.uap.inventario.ui.busqueda

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.edu.uap.inventario.data.model.EquipoDetalle
import pe.edu.uap.inventario.data.model.toDetalle
import pe.edu.uap.inventario.data.repository.AssetRepository
import pe.edu.uap.inventario.data.repository.AssetRepositoryImpl

class BusquedaViewModel(
    private val assetRepository: AssetRepository = AssetRepositoryImpl()
) : ViewModel() {

    private val _uiState = MutableStateFlow<BusquedaUiState>(BusquedaUiState.Idle)
    val uiState: StateFlow<BusquedaUiState> = _uiState.asStateFlow()

    private val _items = MutableStateFlow<List<EquipoFila>>(emptyList())
    val items: StateFlow<List<EquipoFila>> = _items.asStateFlow()

    fun buscar(identificador: String) {
        val id = identificador.trim()
        if (id.isEmpty()) {
            _uiState.value = BusquedaUiState.Error("Ingresa un código o serie")
            return
        }

        _uiState.value = BusquedaUiState.Loading
        viewModelScope.launch {
            val result = assetRepository.buscar(id)
            result.fold(
                onSuccess = { r ->
                    if (!r.encontrado) {
                        _uiState.value = BusquedaUiState.Error("No encontrado: $id")
                        return@fold
                    }
                    val detalle = r.toDetalle()
                    val fila = when (detalle) {
                        is EquipoDetalle.Cpu -> EquipoFila(
                            estado = r.estadoGeneral ?: "—",
                            categoria = detalle.data.equipo,
                            marca = detalle.data.marca,
                            modelo = detalle.data.modelo ?: "—",
                            serie = detalle.data.serie ?: "—",
                            codigo = detalle.data.codigoPatrimonial ?: "—"
                        )
                        is EquipoDetalle.Monitor -> EquipoFila(
                            estado = r.estadoGeneral ?: "—",
                            categoria = detalle.data.equipo,
                            marca = detalle.data.marca,
                            modelo = detalle.data.modelo ?: "—",
                            serie = detalle.data.serie ?: "—",
                            codigo = detalle.data.codigoPatrimonial ?: "—"
                        )
                        is EquipoDetalle.Proyector -> EquipoFila(
                            estado = r.estadoGeneral ?: "—",
                            categoria = detalle.data.equipo,
                            marca = detalle.data.marca,
                            modelo = detalle.data.modelo ?: "—",
                            serie = detalle.data.serie ?: "—",
                            codigo = detalle.data.codigoPatrimonial ?: "—"
                        )
                        is EquipoDetalle.Rae -> EquipoFila(
                            estado = "RAE",
                            categoria = detalle.data.equipo,
                            marca = detalle.data.marca,
                            modelo = detalle.data.modelo ?: "—",
                            serie = detalle.data.serie ?: "—",
                            codigo = detalle.data.codigoPatrimonial ?: "—"
                        )
                        null -> EquipoFila(
                            estado = r.estadoGeneral ?: "—",
                            categoria = r.categoria ?: "—",
                            marca = "—",
                            modelo = "—",
                            serie = "—",
                            codigo = id
                        )
                    }
                    // Evita duplicar el mismo código patrimonial
                    _items.update { lista ->
                        if (lista.any { it.codigo == fila.codigo && fila.codigo != "—" }) lista
                        else lista + fila
                    }
                    _uiState.value = BusquedaUiState.Idle
                },
                onFailure = {
                    _uiState.value = BusquedaUiState.Error(it.message ?: "Error al buscar")
                }
            )
        }
    }

    fun limpiarLista() {
        _items.value = emptyList()
        _uiState.value = BusquedaUiState.Idle
    }
}