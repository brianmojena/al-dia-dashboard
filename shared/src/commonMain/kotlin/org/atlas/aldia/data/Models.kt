package org.atlas.aldia.data

import kotlinx.serialization.Serializable

// Los nombres de campo espejan exactamente el JSON que devuelve el backend
// (server/routes/auth.js publicUser() y server/routes/dashboard.js) — mismo
// snake_case que usa el resto del sistema (web y escritorio), para no tener
// que traducir nombres en ningún punto.

@Serializable
data class User(
    val id: Long,
    val email: String,
    val store_name: String,
    val plan: String,
    val created_at: String,
    val transfer_limit: Double? = null,
    val usd_rate: Double? = null,
)

@Serializable
data class AuthResponse(
    val token: String,
    val user: User,
)

@Serializable
data class MeResponse(
    val user: User,
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
)

// Ambos campos son independientes: enviar solo uno deja el otro sin tocar en
// el servidor (ver PUT /api/auth/settings). null explícito borra el valor.
@Serializable
data class SettingsUpdateRequest(
    val transfer_limit: Double? = null,
    val usd_rate: Double? = null,
)

@Serializable
data class TodayStats(
    val sales: Double = 0.0,
    val profit: Double = 0.0,
    val count: Int = 0,
)

// Solo los campos que el dashboard simplificado necesita mostrar — el backend
// manda más (purchase_price, user_id, created_at) pero el cliente Json está
// configurado con ignoreUnknownKeys, así que el resto se ignora sin romper.
@Serializable
data class ProductLite(
    val id: Long,
    val name: String,
    val stock: Int,
    val sale_price: Double,
)

@Serializable
data class SaleLite(
    val id: Long,
    val total: Double,
    val profit: Double,
    val payment_method: String,
    val created_at: String,
)

// Línea de una venta — lo que realmente se vendió. product_id puede faltar si
// el producto fue borrado después (el backend igual conserva product_name
// como snapshot histórico, así que el detalle nunca queda vacío por eso).
@Serializable
data class SaleItem(
    val product_id: Long? = null,
    val product_name: String,
    val quantity: Int,
    val unit_price: Double,
    val unit_cost: Double = 0.0,
)

// Respuesta de GET /api/sales/:id — la venta con sus ítems, para el detalle
// que se despliega al tocar una venta en "Últimas ventas".
@Serializable
data class SaleDetail(
    val id: Long,
    val total: Double,
    val profit: Double,
    val payment_method: String,
    val created_at: String,
    val items: List<SaleItem> = emptyList(),
)

@Serializable
data class DashboardResponse(
    val today: TodayStats,
    val lowStock: List<ProductLite> = emptyList(),
    val recentSales: List<SaleLite> = emptyList(),
)

@Serializable
data class ApiErrorBody(
    val error: String? = null,
)

/** Resultado uniforme de cualquier llamada al backend — evita que cada
 * pantalla tenga que lidiar con excepciones de red vs errores HTTP por separado. */
sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Failure(val message: String, val statusCode: Int? = null) : ApiResult<Nothing>()
}
