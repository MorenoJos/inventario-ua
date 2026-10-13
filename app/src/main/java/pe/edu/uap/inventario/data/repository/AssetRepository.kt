package pe.edu.uap.inventario.data.repository

import io.github.jan.supabase.functions.functions
import io.ktor.client.call.body
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put
import pe.edu.uap.inventario.data.model.BusquedaResultado
import pe.edu.uap.inventario.data.model.CpuData
import pe.edu.uap.inventario.data.model.MonitorData
import pe.edu.uap.inventario.data.model.ProyectorData
import pe.edu.uap.inventario.data.model.RaeData
import pe.edu.uap.inventario.data.remote.SupabaseClientProvider

interface AssetRepository {
    suspend fun buscar(identificador: String): Result<BusquedaResultado>
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