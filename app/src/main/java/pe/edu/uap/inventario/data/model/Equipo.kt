package pe.edu.uap.inventario.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement

// ── ¿Qué estado general tiene el equipo? ─────────────────────────────────────
// Solo dos posibilidades en el negocio: operativo (en uso) o en baja (RAE).
// Un enum garantiza que no puedas escribir "Operativo" o "rae" por error.
enum class EstadoGeneral { OPERATIVO, RAE }

// ── Campos base que comparten TODAS las categorías ───────────────────────────
// Son nulables (?) porque en el Sheet algunos equipos pueden no tener
// número de serie o código patrimonial cargado todavía.
@Serializable
data class EquipoBase(
    val equipo: String,
    val marca: String,
    val modelo: String? = null,
    val serie: String? = null,
    @SerialName("codigoPatrimonial") val codigoPatrimonial: String? = null
)

// ── Un equipo de cada categoría agrega sus campos extra ───────────────────────
// CPU no tiene nada extra, pero lo modelamos igual para que sea consistente.
@Serializable
data class CpuData(
    val equipo: String,
    val marca: String,
    val modelo: String? = null,
    val serie: String? = null,
    @SerialName("codigoPatrimonial") val codigoPatrimonial: String? = null
)

@Serializable
data class MonitorData(
    val equipo: String,
    val marca: String,
    val modelo: String? = null,
    val serie: String? = null,
    @SerialName("codigoPatrimonial") val codigoPatrimonial: String? = null,
    val estado: String? = null          // NUEVO / USADO
)

@Serializable
data class ProyectorData(
    val equipo: String,
    val marca: String,
    val modelo: String? = null,
    val serie: String? = null,
    @SerialName("codigoPatrimonial") val codigoPatrimonial: String? = null,
    @SerialName("horasUso") val horasUso: Int? = null
)

@Serializable
data class RaeData(
    val equipo: String,
    val marca: String,
    val modelo: String? = null,
    val serie: String? = null,
    @SerialName("codigoPatrimonial") val codigoPatrimonial: String? = null
)

// ── La respuesta completa que llega desde la Edge Function ───────────────────
// JsonObject para `datos` porque puede ser CPU, Monitor, Proyector o RAE.
// Lo desempaquetamos en el repositorio cuando ya sabemos qué categoría es.
@Serializable
data class BusquedaResultado(
    val encontrado: Boolean,
    @SerialName("estadoGeneral") val estadoGeneral: String? = null,
    val categoria: String? = null,
    val datos: JsonObject? = null       // Se deserializa según la categoría
) {
    // Convierte el string que llega del JSON al enum tipado.
    // Si estadoGeneral es null (equipo no encontrado), devuelve null.
    fun estadoGeneralEnum(): EstadoGeneral? = when (estadoGeneral) {
        "OPERATIVO" -> EstadoGeneral.OPERATIVO
        "RAE"       -> EstadoGeneral.RAE
        else        -> null
    }
}

sealed class EquipoDetalle {
    data class Cpu(val data: CpuData) : EquipoDetalle()
    data class Monitor(val data: MonitorData) : EquipoDetalle()
    data class Proyector(val data: ProyectorData) : EquipoDetalle()
    data class Rae(val data: RaeData) : EquipoDetalle()
}

private val equipoJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

fun BusquedaResultado.toDetalle(): EquipoDetalle? {
    val datos = this.datos ?: return null
    return when {
        estadoGeneral == "RAE" ->
            EquipoDetalle.Rae(equipoJson.decodeFromJsonElement(RaeData.serializer(), datos))
        categoria == "CPU" ->
            EquipoDetalle.Cpu(equipoJson.decodeFromJsonElement(CpuData.serializer(), datos))
        categoria == "MONITORES" ->
            EquipoDetalle.Monitor(equipoJson.decodeFromJsonElement(MonitorData.serializer(), datos))
        categoria == "PROYECTORES" ->
            EquipoDetalle.Proyector(equipoJson.decodeFromJsonElement(ProyectorData.serializer(), datos))
        else -> null
    }
}

