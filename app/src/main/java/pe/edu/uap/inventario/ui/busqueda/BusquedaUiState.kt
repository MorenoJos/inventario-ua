package pe.edu.uap.inventario.ui.busqueda

data class EquipoFila(
    val estado: String,
    val categoria: String,
    val marca: String,
    val modelo: String,
    val serie: String,
    val codigo: String
)

sealed interface BusquedaUiState {
    data object Idle : BusquedaUiState
    data object Loading : BusquedaUiState
    data class Error(val message: String) : BusquedaUiState
}