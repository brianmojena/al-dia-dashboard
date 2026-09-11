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

// --- Arqueos ---------------------------------------------------------------
// La pregunta que el dueño hace desde lejos no es solo "¿cuánto se vendió?"
// sino "¿cuadró todo?". El backend responde las dos dentro de /api/dashboard
// (ver server/routes/dashboard.js) para que esta pantalla se resuelva con una
// sola petición: en una conexión cubana cada llamada extra es otra oportunidad
// de fallar.

// account_email es nullable a propósito: las operaciones anteriores a que
// existiera la atribución no tienen responsable, y decir "sin identificar" es
// más honesto que atribuírselas a alguien.
// closed_at_label / counted_at_label vienen ya formateados en HORA DE LA
// TIENDA desde el servidor (ver server/lib/businessDay.js shopLocalLabel).
// No se formatea acá a propósito: la zona relevante es la del negocio, no la
// del teléfono — el dueño de viaje quiere leer la hora de su tienda, no la del
// país donde esté parado.
@Serializable
data class CashCloseLite(
    val id: Long,
    val closed_at: String,
    val closed_at_label: String? = null,
    val difference: Double,
    val expected_cash: Double = 0.0,
    val counted_cash: Double = 0.0,
    val sales_count: Int = 0,
    val account_email: String? = null,
)

@Serializable
data class InventoryCountLite(
    val id: Long,
    val counted_at: String,
    val counted_at_label: String? = null,
    val lines_count: Int = 0,
    val units_missing: Int = 0,
    val units_extra: Int = 0,
    val value_missing: Double = 0.0,
    val account_email: String? = null,
)

/** Saldo acumulado de descuadres por cuenta. El backend lo manda vacío cuando
 * hay un solo cajero: ahí el "patrón" sería la misma información que el último
 * cierre, y ocuparía espacio sin decir nada nuevo. */
@Serializable
data class CashierSummary(
    val account_id: Long? = null,
    val account_email: String? = null,
    val closes: Int = 0,
    val total_difference: Double = 0.0,
    val times_short: Int = 0,
)

@Serializable
data class AuditSummary(
    val lastCashClose: CashCloseLite? = null,
    val lastInventoryCount: InventoryCountLite? = null,
    val cashierSummary: List<CashierSummary> = emptyList(),
)

@Serializable
data class DashboardResponse(
    val today: TodayStats,
    val lowStock: List<ProductLite> = emptyList(),
    val recentSales: List<SaleLite> = emptyList(),
    // Con default: un servidor todavía sin desplegar la función no rompe la app.
    val audit: AuditSummary = AuditSummary(),
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
