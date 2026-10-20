package pe.edu.uap.inventario.data.model

import kotlinx.serialization.Serializable

// ── Categorías en las que se puede dar de alta un equipo ─────────────────────
// El `name` de cada valor (CPU, MONITORES, PROYECTORES) es EXACTAMENTE lo que
// espera la Edge Function y el nombre de la pestaña en el Sheet.
// `etiqueta` es solo para mostrar en pantalla.
enum class CategoriaEquipo(val etiqueta: String) {
    CPU("All in One"),
    MONITORES("Monitor"),
    PROYECTORES("Proyector")
}

// ── Cómo se capturaron los datos (queda en el Kardex) ────────────────────────
enum class MetodoCaptura { MANUAL, CAMARA, MIXTO }

// ── Lo que la app envía para dar de alta un equipo ───────────────────────────
// No es @Serializable a propósito: el repositorio arma el JSON a mano para
// controlar exactamente qué campos se mandan (con valores por defecto, la
// serialización automática los omitiría).
// Los textos pueden ir vacíos; las reglas reales (al menos serie o código,
// observaciones si no hay ninguno, estado del monitor) las valida el servidor.
data class NuevoEquipo(
    val categoria: CategoriaEquipo,
    val marca: String = "",
    val modelo: String = "",
    val serie: String = "",
    val codigoPatrimonial: String = "",
    val ubicacion: String = "",
    val observaciones: String = "",
    val metodoCaptura: MetodoCaptura = MetodoCaptura.MANUAL,
    val estado: String? = null,   // solo monitores: NUEVO / USADO
    val horasUso: Int? = null     // solo proyectores
)

// ── Respuesta de registrar-equipo cuando el alta salió bien (HTTP 201) ───────
@Serializable
data class RegistroResultado(
    val ok: Boolean = false,
    val idEquipo: String? = null,
    val idMovimiento: String? = null,
    val categoria: String? = null
)