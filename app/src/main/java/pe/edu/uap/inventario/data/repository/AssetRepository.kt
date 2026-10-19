package pe.edu.uap.inventario.data.repository

import io.github.jan.supabase.functions.functions
import io.ktor.client.call.body
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
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
        } catch (e: Exception) {
            Result.failure(e)
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
        } catch (e: Exception) {
            Result.failure(e)
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