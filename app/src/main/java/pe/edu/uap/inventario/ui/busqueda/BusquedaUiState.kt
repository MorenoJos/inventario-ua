package pe.edu.uap.inventario.ui.busqueda

data class EquipoFila(
    val estado: String,
    val categoria: String,
    val marca: String,
    val modelo: String,
    val serie: String,
    val codigo: String,
    // Datos propios de cada categoría para el detalle (condición del monitor, horas del proyector)
    val extras: List<Pair<String, String>> = emptyList()
)

sealed interface BusquedaUiState {
    data object Idle : BusquedaUiState
    data object Loading : BusquedaUiState
    // El servidor respondió bien, pero ese código/serie no existe en el inventario.
    // Es distinto de un Error (red, sesión): aquí corresponde ofrecer "Agregar".
    data class NoEncontrado(val identificador: String) : BusquedaUiState
    data class Error(val message: String) : BusquedaUiState
}