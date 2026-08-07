package org.atlas.aldia.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
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
private const val BASE_URL = "https://server-three-orcin-11.vercel.app"

class ApiClient(private val tokenStorage: TokenStorage) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = HttpClient {
        install(ContentNegotiation) { json(json) }
        install(Logging) { level = LogLevel.INFO }
        expectSuccess = false
    }

    private suspend inline fun <reified T> request(
        method: HttpMethod,
        path: String,
        body: Any? = null,
        auth: Boolean = true,
    ): ApiResult<T> {
        val response: HttpResponse = try {
            client.request(BASE_URL + path) {
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
