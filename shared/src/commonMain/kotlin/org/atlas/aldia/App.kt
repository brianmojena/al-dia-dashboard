package org.atlas.aldia

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import org.atlas.aldia.data.ApiClient
import org.atlas.aldia.data.TokenStorage
import org.atlas.aldia.ui.DashboardScreen
import org.atlas.aldia.ui.LoginScreen
import org.atlas.aldia.viewmodel.AppViewModel
import org.atlas.aldia.viewmodel.AuthState

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun App() {
    val viewModel: AppViewModel = viewModel {
        val tokenStorage = TokenStorage()
        AppViewModel(ApiClient(tokenStorage), tokenStorage)
    }

    val authState by viewModel.authState.collectAsState()
    val loginState by viewModel.loginState.collectAsState()
    val dashboardState by viewModel.dashboardState.collectAsState()

    MaterialExpressiveTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            when (val state = authState) {
                is AuthState.CheckingSession -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LoadingIndicator()
                    }
                }

                is AuthState.LoggedOut -> {
                    LoginScreen(
                        state = loginState,
                        onEmailChange = viewModel::onEmailChange,
                        onPasswordChange = viewModel::onPasswordChange,
                        onSubmit = viewModel::login,
                    )
                }

                is AuthState.LoggedIn -> {
                    DashboardScreen(
                        storeName = state.user.store_name,
                        state = dashboardState,
                        onRefresh = viewModel::refreshDashboard,
                        onLogout = viewModel::logout,
                        onTransferLimitChange = viewModel::onTransferLimitInputChange,
                        onUsdRateChange = viewModel::onUsdRateInputChange,
                        onSaveSettings = viewModel::saveSettings,
                        onToggleSaleDetail = viewModel::toggleSaleDetail,
                    )
                }
            }
        }
    }
}
