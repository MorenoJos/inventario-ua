package pe.edu.uap.inventario.ui.alta

import pe.edu.uap.inventario.data.model.CategoriaEquipo
import pe.edu.uap.inventario.data.model.RegistroResultado

// Todo lo que la pantalla de alta necesita saber para dibujarse.
// La pantalla NO guarda nada por su cuenta: solo muestra este estado y avisa
// al ViewModel cuando el usuario cambia algo.
data class AltaUiState(
    val categoria: CategoriaEquipo = CategoriaEquipo.MONITORES,
    val marca: String = "",
    val modelo: String = "",
    val serie: String = "",
    val codigoPatrimonial: String = "",
    val ubicacion: String = "",
    val observaciones: String = "",
    val estadoMonitor: String = "",     // "NUEVO" / "USADO" (solo monitores)
    val horasUso: String = "",          // texto, porque el campo se escribe a mano (solo proyectores)

    val intentoGuardar: Boolean = false, // los errores se muestran solo después del primer intento
    val guardando: Boolean = false,
    val error: String? = null,           // fallo de red/servidor
    val registrado: RegistroResultado? = null // si no es null, el alta salió bien
) {
    // "S/N" escrito a mano cuenta como "sin serie", igual que en el servidor.
    private val sinSerie: Boolean
        get() = serie.isBlank() || serie.trim().equals("S/N", ignoreCase = true)

    private val sinCodigo: Boolean
        get() = codigoPatrimonial.isBlank()

    // Reglas espejo de las del servidor (la validación real sigue siendo la del servidor;
    // esto solo evita viajes de red por errores evidentes).
    val errorObservaciones: String?
        get() = if (sinSerie && sinCodigo && observaciones.isBlank())
            "Sin serie ni código, explica el motivo (ej. etiqueta ilegible)"
        else null

    val errorEstado: String?
        get() = if (categoria == CategoriaEquipo.MONITORES && estadoMonitor.isBlank())
            "Elige NUEVO o USADO"
        else null

    val formularioValido: Boolean
        get() = errorObservaciones == null && errorEstado == null
}