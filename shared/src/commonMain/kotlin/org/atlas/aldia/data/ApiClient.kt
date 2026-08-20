package org.atlas.aldia.data

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

// Mismo backend que usan la web y la app de escritorio (server/ desplegado en
// Vercel, datos en Turso) — este dashboard es un cliente más de la misma API,
// no un sistema aparte.
const val DEFAULT_BASE_URL = "https://al-dia-pos-api.vercel.app"

private fun HttpClientConfig<*>.applyDefaults(json: Json) {
    install(ContentNegotiation) { json(json) }
    install(Logging) { level = LogLevel.INFO }
    // Los errores HTTP se traducen a ApiResult.Failure más abajo. Dejar que Ktor
    // lance excepciones mezclaría "el servidor respondió que no" con "no hubo
    // servidor", que es justo la distinción que este cliente existe para hacer.
    expectSuccess = false
}

/**
 * @param engine  se inyecta solo en tests (MockEngine). En producción va nulo y
 *                cada plataforma aporta el suyo: OkHttp en Android, Darwin en iOS.
 * @param baseUrl override para tests; por defecto, el backend real.
 */
class ApiClient(
    private val tokenStorage: SessionStore,
    engine: HttpClientEngine? = null,
    private val baseUrl: String = DEFAULT_BASE_URL,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client =
        if (engine != null) HttpClient(engine) { applyDefaults(json) }
        else HttpClient { applyDefaults(json) }

    private suspend inline fun <reified T> request(
        method: HttpMethod,
        path: String,
        body: Any? = null,
        auth: Boolean = true,
    ): ApiResult<T> {
        val response: HttpResponse = try {
            client.request(baseUrl + path) {
                this.method = method
                contentType(ContentType.Application.Json)
                if (auth) {
                    tokenStorage.getToken()?.let { header(HttpHeaders.Authorization, "Bearer $it") }
                }
                if (body != null) setBody(body)
            }
        } catch (e: Exception) {
            return ApiResult.Failure(e.message ?: "Sin conexión — revisa tu Internet")
        }

        return if (response.status.isSuccess()) {
            try {
                ApiResult.Success(response.body<T>())
            } catch (e: Exception) {
                ApiResult.Failure("Respuesta inesperada del servidor: ${e.message}")
            }
        } else {
            val err = runCatching { response.body<ApiErrorBody>() }.getOrNull()
            ApiResult.Failure(err?.error ?: "Error del servidor (${response.status.value})", response.status.value)
        }
    }

    suspend fun login(email: String, password: String): ApiResult<AuthResponse> =
        request(HttpMethod.Post, "/api/auth/login", LoginRequest(email, password), auth = false)

    suspend fun me(): ApiResult<MeResponse> =
        request(HttpMethod.Get, "/api/auth/me")

    suspend fun getDashboard(): ApiResult<DashboardResponse> =
        request(HttpMethod.Get, "/api/dashboard")

    suspend fun getSale(id: Long): ApiResult<SaleDetail> =
        request(HttpMethod.Get, "/api/sales/$id")

    suspend fun updateSettings(transferLimit: Double?, usdRate: Double?): ApiResult<MeResponse> =
        request(HttpMethod.Put, "/api/auth/settings", SettingsUpdateRequest(transferLimit, usdRate))
}
