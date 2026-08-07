package org.atlas.aldia.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.atlas.aldia.data.ApiClient
import org.atlas.aldia.data.ApiResult
import org.atlas.aldia.data.ProductLite
import org.atlas.aldia.data.SaleItem
import org.atlas.aldia.data.SaleLite
import org.atlas.aldia.data.TodayStats
import org.atlas.aldia.data.TokenStorage
import org.atlas.aldia.data.User

sealed class AuthState {
    data object CheckingSession : AuthState()
    data object LoggedOut : AuthState()
    data class LoggedIn(val user: User) : AuthState()
}

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val error: String? = null,
)

data class DashboardUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val today: TodayStats = TodayStats(),
    val lowStock: List<ProductLite> = emptyList(),
    val recentSales: List<SaleLite> = emptyList(),
    // Texto libre, no Double — así el campo no "pelea" con lo que el dueño está
    // escribiendo (un punto decimal a medio escribir, borrar para reemplazar, etc.).
    val transferLimitInput: String = "",
    val usdRateInput: String = "",
    val savingSettings: Boolean = false,
    val settingsSaved: Boolean = false,
    val settingsError: String? = null,
    // Detalle de "qué se vendió" en Últimas ventas: una sola venta expandida a
    // la vez, con caché por id para no re-pedir el detalle si se colapsa y
    // se vuelve a abrir la misma venta.
    val expandedSaleId: Long? = null,
    val expandedSaleLoading: Boolean = false,
    val saleItemsCache: Map<Long, List<SaleItem>> = emptyMap(),
)

private fun formatNumber(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

class AppViewModel(
    private val apiClient: ApiClient,
    private val tokenStorage: TokenStorage,
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.CheckingSession)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _loginState = MutableStateFlow(LoginUiState())
    val loginState: StateFlow<LoginUiState> = _loginState.asStateFlow()

    private val _dashboardState = MutableStateFlow(DashboardUiState())
    val dashboardState: StateFlow<DashboardUiState> = _dashboardState.asStateFlow()

    init {
        checkSession()
    }

    private fun checkSession() {
        viewModelScope.launch {
            if (tokenStorage.getToken() == null) {
                _authState.value = AuthState.LoggedOut
                return@launch
            }
            // El token cacheado podría haber expirado (30 días) o haber sido
            // revocado — /me es la forma de confirmarlo antes de mostrar nada.
            when (val result = apiClient.me()) {
                is ApiResult.Success -> {
                    _authState.value = AuthState.LoggedIn(result.data.user)
                    loadDashboard(result.data.user)
                }
                is ApiResult.Failure -> {
                    tokenStorage.setToken(null)
                    _authState.value = AuthState.LoggedOut
                }
            }
        }
    }

    fun onEmailChange(value: String) = _loginState.update { it.copy(email = value, error = null) }
    fun onPasswordChange(value: String) = _loginState.update { it.copy(password = value, error = null) }

    fun login() {
        val state = _loginState.value
        if (state.email.isBlank() || state.password.isBlank()) {
            _loginState.update { it.copy(error = "Completa correo y contraseña") }
            return
        }
        viewModelScope.launch {
            _loginState.update { it.copy(loading = true, error = null) }
            when (val result = apiClient.login(state.email.trim(), state.password)) {
                is ApiResult.Success -> {
                    tokenStorage.setToken(result.data.token)
                    _loginState.value = LoginUiState()
                    _authState.value = AuthState.LoggedIn(result.data.user)
                    loadDashboard(result.data.user)
                }
                is ApiResult.Failure -> {
                    _loginState.update { it.copy(loading = false, error = result.message) }
                }
            }
        }
    }

    fun logout() {
        tokenStorage.setToken(null)
        _authState.value = AuthState.LoggedOut
        _dashboardState.value = DashboardUiState()
    }

    // userHint: se lo pasan login()/checkSession(), que ACABAN de pedir /me y ya
    // tienen el usuario fresco a mano — evita pedirlo dos veces seguidas.
    // Sin hint (ej. desde refreshDashboard()) se vuelve a pedir /me acá mismo,
    // a propósito: es la única forma de garantizar que el límite de
    // transferencia y la tasa del dólar que se precargan en el formulario sean
    // los que de verdad están guardados ahora en el servidor, y no una copia
    // vieja del usuario que quedó en memoria desde el login.
    private fun loadDashboard(userHint: User? = null) {
        viewModelScope.launch {
            _dashboardState.update { it.copy(loading = true, error = null) }

            val currentUser = userHint ?: run {
                when (val meResult = apiClient.me()) {
                    is ApiResult.Success -> {
                        _authState.value = AuthState.LoggedIn(meResult.data.user)
                        meResult.data.user
                    }
                    is ApiResult.Failure -> (_authState.value as? AuthState.LoggedIn)?.user
                }
            }

            when (val result = apiClient.getDashboard()) {
                is ApiResult.Success -> {
                    _dashboardState.update {
                        it.copy(
                            loading = false,
                            today = result.data.today,
                            lowStock = result.data.lowStock,
                            recentSales = result.data.recentSales,
                            transferLimitInput = currentUser?.transfer_limit?.let(::formatNumber) ?: "",
                            usdRateInput = currentUser?.usd_rate?.let(::formatNumber) ?: "",
                        )
                    }
                }
                is ApiResult.Failure -> {
                    _dashboardState.update { it.copy(loading = false, error = result.message) }
                }
            }
        }
    }

    fun refreshDashboard() = loadDashboard()

    fun onTransferLimitInputChange(value: String) =
        _dashboardState.update { it.copy(transferLimitInput = value, settingsSaved = false, settingsError = null) }

    fun onUsdRateInputChange(value: String) =
        _dashboardState.update { it.copy(usdRateInput = value, settingsSaved = false, settingsError = null) }

    /** Un campo vacío se guarda como "sin definir" (null) a propósito — es
     * cómo el dueño borra un límite/tasa que ya no quiere aplicar. */
    fun saveSettings() {
        val state = _dashboardState.value
        val transferLimit = state.transferLimitInput.trim().toDoubleOrNull()
        val usdRate = state.usdRateInput.trim().toDoubleOrNull()

        if (state.transferLimitInput.isNotBlank() && transferLimit == null) {
            _dashboardState.update { it.copy(settingsError = "El límite de transferencia no es un número válido") }
            return
        }
        if (state.usdRateInput.isNotBlank() && usdRate == null) {
            _dashboardState.update { it.copy(settingsError = "La tasa del dólar no es un número válido") }
            return
        }

        viewModelScope.launch {
            _dashboardState.update { it.copy(savingSettings = true, settingsError = null) }
            when (val result = apiClient.updateSettings(transferLimit, usdRate)) {
                is ApiResult.Success -> {
                    _authState.value = AuthState.LoggedIn(result.data.user)
                    // Se resincroniza el formulario con lo que el servidor confirmó
                    // guardado (no solo con lo que se tipeó) — así el campo nunca
                    // queda desfasado de la fuente de verdad real.
                    _dashboardState.update {
                        it.copy(
                            savingSettings = false,
                            settingsSaved = true,
                            transferLimitInput = result.data.user.transfer_limit?.let(::formatNumber) ?: "",
                            usdRateInput = result.data.user.usd_rate?.let(::formatNumber) ?: "",
                        )
                    }
                }
                is ApiResult.Failure -> {
                    _dashboardState.update { it.copy(savingSettings = false, settingsError = result.message) }
                }
            }
        }
    }

    /** Toca una venta en "Últimas ventas": la despliega mostrando qué se
     * vendió, o la colapsa si ya estaba abierta. El detalle se pide una sola
     * vez por venta — las siguientes veces sale de la caché. */
    fun toggleSaleDetail(saleId: Long) {
        val state = _dashboardState.value
        if (state.expandedSaleId == saleId) {
            _dashboardState.update { it.copy(expandedSaleId = null) }
            return
        }

        _dashboardState.update { it.copy(expandedSaleId = saleId) }
        if (state.saleItemsCache.containsKey(saleId)) return

        viewModelScope.launch {
            _dashboardState.update { it.copy(expandedSaleLoading = true) }
            when (val result = apiClient.getSale(saleId)) {
                is ApiResult.Success -> {
                    _dashboardState.update {
                        it.copy(
                            expandedSaleLoading = false,
                            saleItemsCache = it.saleItemsCache + (saleId to result.data.items),
                        )
                    }
                }
                is ApiResult.Failure -> {
                    _dashboardState.update { it.copy(expandedSaleLoading = false) }
                }
            }
        }
    }
}
