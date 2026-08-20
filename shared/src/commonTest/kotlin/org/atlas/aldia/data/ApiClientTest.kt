package org.atlas.aldia.data

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * El valor de ApiClient no está en hacer peticiones — está en que traduce tres
 * situaciones muy distintas a un mismo tipo de resultado sin confundirlas:
 *
 *   1. no hubo red            → Failure sin statusCode
 *   2. hubo respuesta ilegible → Failure sin statusCode
 *   3. el servidor dijo que no → Failure CON statusCode
 *
 * Confundir la 1 con la 3 es lo que hace que una app muestre "credenciales
 * inválidas" cuando en realidad se cayó el wifi. Estos tests fijan esa frontera.
 */
class ApiClientTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun clientWith(engine: MockEngine, token: String? = null) =
        ApiClient(
            tokenStorage = FakeTokenStorage(token),
            engine = engine,
            baseUrl = "https://test.local",
        )

    // --- 1. Sin red -------------------------------------------------------

    @Test
    fun sin_red_devuelve_Failure_sin_codigo_de_estado() = runTest {
        val engine = MockEngine { throw IOException("Network unreachable") }

        when (val result = clientWith(engine).getDashboard()) {
            is ApiResult.Failure -> {
                assertNull(result.statusCode, "un fallo de red no tiene status HTTP")
                assertTrue(result.message.isNotBlank(), "debe traer un mensaje mostrable")
            }
            is ApiResult.Success -> fail("no debería haber tenido éxito sin red")
        }
    }

    // --- 2. Respuesta ilegible -------------------------------------------

    @Test
    fun respuesta_que_no_parsea_devuelve_Failure_no_Success() = runTest {
        // 200 OK pero el cuerpo no es el DashboardResponse esperado. Es el caso
        // de un proxy o portal cautivo devolviendo HTML con status 200.
        val engine = MockEngine {
            respond("<html>hola</html>", HttpStatusCode.OK, jsonHeaders)
        }

        when (val result = clientWith(engine).getDashboard()) {
            is ApiResult.Failure ->
                assertNull(result.statusCode, "el status era 200; el fallo es de parseo, no del servidor")
            is ApiResult.Success -> fail("un cuerpo ilegible no puede reportarse como éxito")
        }
    }

    // --- 3. Error del servidor -------------------------------------------

    @Test
    fun error_del_servidor_conserva_codigo_y_mensaje_del_backend() = runTest {
        // Forma real de error del backend: {"error": "..."} — ver server/middleware/auth.js
        val engine = MockEngine {
            respond("""{"error":"Sesión inválida o expirada"}""", HttpStatusCode.Unauthorized, jsonHeaders)
        }

        when (val result = clientWith(engine).me()) {
            is ApiResult.Failure -> {
                assertEquals(401, result.statusCode)
                assertEquals("Sesión inválida o expirada", result.message)
            }
            is ApiResult.Success -> fail("un 401 no es éxito")
        }
    }

    @Test
    fun error_sin_cuerpo_legible_cae_a_mensaje_generico_pero_mantiene_el_codigo() = runTest {
        val engine = MockEngine { respondError(HttpStatusCode.InternalServerError) }

        when (val result = clientWith(engine).getDashboard()) {
            is ApiResult.Failure -> {
                assertEquals(500, result.statusCode, "el código debe sobrevivir aunque el cuerpo no se pueda leer")
                assertTrue(result.message.isNotBlank())
            }
            is ApiResult.Success -> fail("un 500 no es éxito")
        }
    }

    // --- Camino feliz -----------------------------------------------------

    @Test
    fun respuesta_valida_se_deserializa_a_Success() = runTest {
        val engine = MockEngine {
            respond(
                """{"today":{"sales":1500.0,"profit":400.0,"count":3},
                    "lowStock":[{"id":1,"name":"Arroz","stock":2,"sale_price":100.0}],
                    "recentSales":[]}""",
                HttpStatusCode.OK,
                jsonHeaders,
            )
        }

        when (val result = clientWith(engine).getDashboard()) {
            is ApiResult.Success -> {
                assertEquals(3, result.data.today.count)
                assertEquals(1500.0, result.data.today.sales)
                assertEquals(1, result.data.lowStock.size)
                assertEquals("Arroz", result.data.lowStock[0].name)
            }
            is ApiResult.Failure -> fail("respuesta válida reportada como fallo: ${result.message}")
        }
    }

    @Test
    fun campos_desconocidos_del_backend_no_rompen_la_deserializacion() = runTest {
        // El backend manda más campos de los que el dashboard usa. Si esto
        // fallara, cualquier campo nuevo en el servidor tumbaría la app móvil.
        val engine = MockEngine {
            respond(
                """{"today":{"sales":10.0,"profit":2.0,"count":1,"campo_nuevo":"x"},
                    "lowStock":[],"recentSales":[],"otra_cosa":123}""",
                HttpStatusCode.OK,
                jsonHeaders,
            )
        }

        val result = clientWith(engine).getDashboard()
        assertTrue(result is ApiResult.Success, "ignoreUnknownKeys debe absorber campos nuevos")
    }

    // --- Autenticación ----------------------------------------------------

    @Test
    fun adjunta_el_token_guardado_como_Bearer() = runTest {
        var authHeader: String? = null
        val engine = MockEngine { request ->
            authHeader = request.headers[HttpHeaders.Authorization]
            respond("""{"user":{"id":1,"email":"a@b.c","store_name":"T","plan":"dev","created_at":"2026-01-01"}}""",
                HttpStatusCode.OK, jsonHeaders)
        }

        clientWith(engine, token = "jwt-de-prueba").me()

        assertEquals("Bearer jwt-de-prueba", authHeader)
    }

    @Test
    fun login_no_manda_Authorization() = runTest {
        // Mandar un token viejo al login puede hacer que el servidor responda
        // por la sesión anterior en vez de por las credenciales enviadas.
        var authHeader: String? = null
        val engine = MockEngine { request ->
            authHeader = request.headers[HttpHeaders.Authorization]
            respond("""{"token":"nuevo","user":{"id":1,"email":"a@b.c","store_name":"T","plan":"dev","created_at":"2026-01-01"}}""",
                HttpStatusCode.OK, jsonHeaders)
        }

        clientWith(engine, token = "token-viejo").login("a@b.c", "secreta")

        assertNull(authHeader, "el login es la única llamada que debe ir sin credenciales previas")
    }
}
