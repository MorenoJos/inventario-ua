package pe.edu.uap.inventario.ui.busqueda

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
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

    // Cola de búsquedas: una a la vez, en orden, sin cancelar ninguna
    private val cola = Channel<String>(Channel.UNLIMITED)

    // Todo se usa solo desde el hilo principal (UI y viewModelScope), por eso no hay locks.
    // vistos = ya consultados o en curso; noReconocidos = consultados y que no existen.
    private val vistos = mutableSetOf<String>()
    private val noReconocidos = mutableSetOf<String>()
    private var pendientes = 0

    init {
        viewModelScope.launch {
            for (id in cola) {
                procesar(id)
            }
        }
    }

    // Misma forma de comparar que el servidor: solo dígitos se rellenan a 8 con ceros
    private fun clave(id: String): String {
        val limpio = id.trim().uppercase()
        return if (limpio.all { it.isDigit() }) limpio.padStart(8, '0') else limpio
    }

    fun buscar(identificador: String) {
        val id = identificador.trim()
        if (id.isEmpty()) {
            _uiState.value = BusquedaUiState.Error("Ingresa un código o serie")
            return
        }

        val k = clave(id)
        if (k in vistos) {
            // Ya consultado: no se vuelve a llamar al servidor.
            // Si era un código inexistente, se avisa igual (útil en la búsqueda manual).
            if (k in noReconocidos && pendientes == 0) {
                _uiState.value = BusquedaUiState.Error("No encontrado: $id")
            }
            return
        }

        vistos.add(k)
        pendientes++
        _uiState.value = BusquedaUiState.Loading
        cola.trySend(id)
    }

    private suspend fun procesar(id: String) {
        val k = clave(id)
        val result = assetRepository.buscar(id)
        pendientes--

        result.fold(
            onSuccess = { r ->
                if (!r.encontrado) {
                    noReconocidos.add(k)
                    _uiState.value =
                        if (pendientes > 0) BusquedaUiState.Loading
                        else BusquedaUiState.Error("No encontrado: $id")
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

                // Evita duplicar el mismo equipo, por código patrimonial o por serie
                _items.update { lista ->
                    val repetido = lista.any {
                        (fila.codigo != "—" && it.codigo == fila.codigo) ||
                                (fila.serie != "—" && it.serie == fila.serie)
                    }
                    if (repetido) lista else lista + fila
                }
                _uiState.value =
                    if (pendientes > 0) BusquedaUiState.Loading else BusquedaUiState.Idle
            },
            onFailure = {
                // Error de red o de sesión: no se marca como visto para poder reintentar
                vistos.remove(k)
                _uiState.value =
                    if (pendientes > 0) BusquedaUiState.Loading
                    else BusquedaUiState.Error(it.message ?: "Error al buscar")
            }
        )
    }

    fun limpiarLista() {
        _items.value = emptyList()
        vistos.clear()
        noReconocidos.clear()
        _uiState.value =
            if (pendientes > 0) BusquedaUiState.Loading else BusquedaUiState.Idle
    }
}