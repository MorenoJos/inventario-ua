package pe.edu.uap.inventario.data.repository

import android.util.Log
import io.github.jan.supabase.functions.functions
import io.ktor.client.call.body
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import pe.edu.uap.inventario.data.model.BusquedaResultado
import pe.edu.uap.inventario.data.model.CpuData
import pe.edu.uap.inventario.data.model.MonitorData
import pe.edu.uap.inventario.data.model.NuevoEquipo
import pe.edu.uap.inventario.data.model.ProyectorData
import pe.edu.uap.inventario.data.model.RaeData
import pe.edu.uap.inventario.data.model.RegistroResultado
import pe.edu.uap.inventario.data.remote.SupabaseClientProvider

interface AssetRepository {
    suspend fun buscar(identificador: String): Result<BusquedaResultado>
    suspend fun registrar(nuevo: NuevoEquipo): Result<RegistroResultado>
}

// Error ya "traducido" para mostrar al usuario. Su mensaje es seguro: nunca contiene
// URL, cabeceras ni tokens.
class ErrorServidor(mensaje: String, val codigo: String? = null) : Exception(mensaje)

private const val TAG = "AssetRepository"
private val jsonError = Json { ignoreUnknownKeys = true }

// La excepción de supabase-kt trae en su mensaje el cuerpo de la respuesta, seguido de la URL
// y TODAS las cabeceras de la petición (incluido el token de sesión). Mostrarla tal cual
// filtra el token en pantalla, en capturas y en logs. Aquí se usa solo la primera línea
// (el cuerpo) y se convierte en un mensaje claro.
private fun traducirError(e: Exception): Exception {
    if (e is java.io.IOException) {
        return ErrorServidor("No hay conexión con el servidor. Revisa tu internet.", "SIN_RED")
    }

    val primeraLinea = e.message?.lineSequence()?.firstOrNull()?.trim().orEmpty()
    // Al log solo va la clase del error y esa primera línea, nunca el mensaje completo
    Log.e(TAG, "Fallo al llamar a la función: ${e::class.simpleName}: $primeraLinea")

    val obj = runCatching { jsonError.parseToJsonElement(primeraLinea).jsonObject }.getOrNull()
    val codigo = obj?.get("codigo")?.jsonPrimitive?.contentOrNull
    val textoError = obj?.get("error")?.jsonPrimitive?.contentOrNull
        ?: obj?.get("message")?.jsonPrimitive?.contentOrNull

    return when {
        obj != null && codigo == "DUPLICADO" -> ErrorServidor(mensajeDuplicado(obj), codigo)
        textoError == "No autorizado" ||
                textoError?.contains("JWT", ignoreCase = true) == true ||
                textoError?.contains("authorization", ignoreCase = true) == true ->
            ErrorServidor("Tu sesión venció. Cierra sesión y vuelve a entrar.", "SESION")
        // Mensajes que escribe nuestra propia función (ya están en español y son claros)
        textoError != null -> ErrorServidor(textoError, codigo)
        else -> ErrorServidor("No se pudo completar la operación. Inténtalo de nuevo.")
    }
}

private fun mensajeDuplicado(obj: JsonObject): String {
    val existente = obj["existente"]?.jsonObject
    val descripcion = listOfNotNull(
        existente?.get("marca")?.jsonPrimitive?.contentOrNull,
        existente?.get("modelo")?.jsonPrimitive?.contentOrNull
    ).filter { it.isNotBlank() }.joinToString(" ")
    val identificador = existente?.get("serie")?.jsonPrimitive?.contentOrNull
        ?: existente?.get("codigoPatrimonial")?.jsonPrimitive?.contentOrNull
    val enRae = obj["estadoGeneral"]?.jsonPrimitive?.contentOrNull == "RAE"

    return buildString {
        append("Ese equipo ya existe en el inventario")
        if (descripcion.isNotEmpty()) append(": $descripcion")
        if (identificador != null) append(" ($identificador)")
        append(".")
        if (enRae) append(" Está dado de baja (RAE).")
    }
}

class AssetRepositoryImpl : AssetRepository {

    private val functions = SupabaseClientProvider.client.functions

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override suspend fun buscar(identificador: String): Result<BusquedaResultado> {
        return try {
            val response = functions.invoke(
                function = "buscar-equipo",
                body = buildJsonObject {
                    put("identificador", identificador.trim())
                },
                headers = Headers.build {
                    append(HttpHeaders.ContentType, "application/json")
                }
            )
            val resultado = response.body<BusquedaResultado>()
            Result.success(resultado)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(traducirError(e))
        }
    }

    // Da de alta un equipo. Quién lo registra NO se envía: el servidor lo saca
    // del token de sesión, así nadie puede falsificarlo en el Kardex.
    override suspend fun registrar(nuevo: NuevoEquipo): Result<RegistroResultado> {
        return try {
            val response = functions.invoke(
                function = "registrar-equipo",
                body = buildJsonObject {
                    put("categoria", nuevo.categoria.name)
                    put("marca", nuevo.marca.trim())
                    put("modelo", nuevo.modelo.trim())
                    put("serie", nuevo.serie.trim())
                    put("codigoPatrimonial", nuevo.codigoPatrimonial.trim())
                    put("ubicacion", nuevo.ubicacion.trim())
                    put("observaciones", nuevo.observaciones.trim())
                    put("metodoCaptura", nuevo.metodoCaptura.name)
                    // Estos dos solo se envían si la categoría los usa
                    nuevo.estado?.let { put("estado", it) }
                    nuevo.horasUso?.let { put("horasUso", it) }
                },
                headers = Headers.build {
                    append(HttpHeaders.ContentType, "application/json")
                }
            )
            Result.success(response.body<RegistroResultado>())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(traducirError(e))
        }
    }

    // Helpers para cuando ya tengas el BusquedaResultado y quieras tipar `datos`
    fun parseCpu(datos: JsonObject): CpuData =
        json.decodeFromJsonElement(CpuData.serializer(), datos)

    fun parseMonitor(datos: JsonObject): MonitorData =
        json.decodeFromJsonElement(MonitorData.serializer(), datos)

    fun parseProyector(datos: JsonObject): ProyectorData =
        json.decodeFromJsonElement(ProyectorData.serializer(), datos)

    fun parseRae(datos: JsonObject): RaeData =
        json.decodeFromJsonElement(RaeData.serializer(), datos)
}