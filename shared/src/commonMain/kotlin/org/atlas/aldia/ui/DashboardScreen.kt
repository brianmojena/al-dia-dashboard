package org.atlas.aldia.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Euro
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.atlas.aldia.data.CashCloseLite
import org.atlas.aldia.data.CashierSummary
import org.atlas.aldia.data.InventoryCountLite
import org.atlas.aldia.data.ProductLite
import org.atlas.aldia.data.SaleItem
import org.atlas.aldia.data.SaleLite
import org.atlas.aldia.viewmodel.DashboardUiState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DashboardScreen(
    storeName: String,
    state: DashboardUiState,
    onRefresh: () -> Unit,
    onAutoRefresh: () -> Unit,
    onLogout: () -> Unit,
    onUsdRateChange: (String) -> Unit,
    onEurRateChange: (String) -> Unit,
    onSaveSettings: () -> Unit,
    onToggleSaleDetail: (Long) -> Unit,
) {
    var showSoldOut by remember { mutableStateOf(false) }
    val soldOut = state.lowStock.filter { it.stock <= 0 }
    val running = state.lowStock.filter { it.stock > 0 }

    // "Tiempo real" = sondeo cada 30 s mientras la pantalla está abierta.
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            onAutoRefresh()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(storeName, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Actualizar")
                    }
                },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Cerrar sesión")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        }
    ) { padding ->
        if (state.loading) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                LoadingIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.error != null) {
                item { ErrorBanner(state.error) }
            }

            item { Text("Hoy", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Filled.AttachMoney,
                        label = "Ventas",
                        value = formatCUP(state.today.sales),
                        container = MaterialTheme.colorScheme.primaryContainer,
                        onContainer = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.AutoMirrored.Filled.TrendingUp,
                        label = "Ganancia",
                        value = formatCUP(state.today.profit),
                        container = MaterialTheme.colorScheme.tertiaryContainer,
                        onContainer = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Filled.Receipt,
                        label = "Ventas",
                        value = state.today.count.toString(),
                        container = MaterialTheme.colorScheme.secondaryContainer,
                        onContainer = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }

            // La otra pregunta del dueño que no está en la tienda. Va antes del
            // stock bajo a propósito: un descuadre es más urgente que un
            // producto por acabarse.
            item { Text("¿Cuadró todo?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            item { CashCloseCard(state.audit.lastCashClose) }
            item { InventoryCountCard(state.audit.lastInventoryCount) }

            if (state.audit.cashierSummary.isNotEmpty()) {
                item {
                    Text(
                        "Descuadres por cajero",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                items(state.audit.cashierSummary, key = { "cashier-${it.account_id}-${it.account_email}" }) {
                    CashierSummaryRow(it)
                }
                item {
                    Text(
                        "Saldo acumulado de sus cierres. Un faltante aislado suele ser un vuelto mal " +
                            "dado; lo que dice algo es el patrón.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item { SoldOutButton(count = soldOut.size, onClick = { showSoldOut = true }) }

            item { Text("Últimas ventas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            if (state.recentSales.isEmpty()) {
                item {
                    Text(
                        "Sin ventas registradas hoy",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                items(state.recentSales, key = { "sale-${it.id}" }) { sale ->
                    val expanded = state.expandedSaleId == sale.id
                    SaleRow(
                        sale = sale,
                        expanded = expanded,
                        loadingDetail = expanded && state.expandedSaleLoading,
                        items = state.saleItemsCache[sale.id],
                        onClick = { onToggleSaleDetail(sale.id) },
                    )
                }
            }

            item {
                SettingsCard(
                    state = state,
                    onUsdRateChange = onUsdRateChange,
                    onEurRateChange = onEurRateChange,
                    onSave = onSaveSettings,
                )
            }

            item { Text("Dinero en tienda", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        StatCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Filled.Payments,
                            label = "Efectivo",
                            value = formatCUP(state.holdings.cash),
                            container = MaterialTheme.colorScheme.primaryContainer,
                            onContainer = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        StatCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Filled.CreditCard,
                            label = "Transferencia",
                            value = formatCUP(state.holdings.transfer),
                            container = MaterialTheme.colorScheme.secondaryContainer,
                            onContainer = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        StatCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Filled.AttachMoney,
                            label = "Dólares",
                            value = formatForeign("US$", state.holdings.usd),
                            container = MaterialTheme.colorScheme.tertiaryContainer,
                            onContainer = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        StatCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Filled.Euro,
                            label = "Euros",
                            value = formatForeign("€", state.holdings.eur),
                            container = MaterialTheme.colorScheme.tertiaryContainer,
                            onContainer = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                    StatCard(
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Filled.Inventory2,
                        label = "Importe del inventario a precio de venta · ${state.inventory.units} unidades",
                        value = formatCUP(state.inventory.retail),
                        container = MaterialTheme.colorScheme.surfaceContainerHigh,
                        onContainer = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }

    if (showSoldOut) {
        ModalBottomSheet(
            onDismissRequest = { showSoldOut = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            SoldOutList(soldOut = soldOut, running = running)
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    container: Color,
    onContainer: Color,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Icon(icon, contentDescription = null, tint = onContainer, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = onContainer)
            Text(label, style = MaterialTheme.typography.labelSmall, color = onContainer)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsCard(
    state: DashboardUiState,
    onUsdRateChange: (String) -> Unit,
    onEurRateChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Configuración del negocio", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            OutlinedTextField(
                value = state.usdRateInput,
                onValueChange = onUsdRateChange,
                label = { Text("Cambio del dólar (CUP por 1 USD)") },
                placeholder = { Text("Sin definir") },
                leadingIcon = { Icon(Icons.Filled.Payments, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.eurRateInput,
                onValueChange = onEurRateChange,
                label = { Text("Cambio del euro (CUP por 1 EUR)") },
                placeholder = { Text("Sin definir") },
                leadingIcon = { Icon(Icons.Filled.Euro, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            if (state.settingsError != null) {
                Text(state.settingsError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (state.settingsSaved) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        "Guardado",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Button(
                onClick = onSave,
                enabled = !state.savingSettings,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.savingSettings) {
                    LoadingIndicator(modifier = Modifier.size(18.dp))
                } else {
                    Text("Guardar")
                }
            }
        }
    }
}

/**
 * Fila de estado de un arqueo. El color carga el mensaje: rojo si falta, y en
 * este negocio "falta" es la única palabra que el dueño necesita ver desde
 * lejos. Sobrante en tono terciario porque también es un descuadre —cobrar de
 * más también es un problema— pero no de la misma urgencia.
 */
@Composable
private fun AuditRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    detail: String,
    amount: String?,
    tone: AuditTone,
) {
    val container = when (tone) {
        AuditTone.SHORT -> MaterialTheme.colorScheme.errorContainer
        AuditTone.EXTRA -> MaterialTheme.colorScheme.tertiaryContainer
        AuditTone.OK -> MaterialTheme.colorScheme.surfaceContainer
    }
    val onContainer = when (tone) {
        AuditTone.SHORT -> MaterialTheme.colorScheme.onErrorContainer
        AuditTone.EXTRA -> MaterialTheme.colorScheme.onTertiaryContainer
        AuditTone.OK -> MaterialTheme.colorScheme.onSurface
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                Icon(icon, contentDescription = null, tint = onContainer, modifier = Modifier.size(22.dp))
                Column {
                    Text(
                        title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = onContainer,
                    )
                    Text(detail, style = MaterialTheme.typography.labelMedium, color = onContainer)
                }
            }
            if (amount != null) {
                Text(
                    amount,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = onContainer,
                )
            }
        }
    }
}

private enum class AuditTone { OK, SHORT, EXTRA }

// Redondeo antes de comparar: expected y counted son REAL y una suma de
// decimales puede dejar 0.0000001, que no es un descuadre de verdad.
private fun isSquare(value: Double): Boolean = kotlin.math.round(value).toLong() == 0L

@Composable
private fun CashCloseCard(close: CashCloseLite?) {
    if (close == null) {
        AuditRow(
            icon = Icons.Filled.Payments,
            title = "Caja",
            detail = "Todavía sin cierres",
            amount = null,
            tone = AuditTone.OK,
        )
        return
    }

    val quien = accountLabel(close.account_email) ?: "sin identificar"
    val cuando = close.closed_at_label ?: ""
    val detalle = listOf(cuando, quien).filter { it.isNotBlank() }.joinToString(" · ")

    when {
        isSquare(close.difference) -> AuditRow(
            icon = Icons.Filled.CheckCircle,
            title = "Caja cuadró",
            detail = detalle,
            amount = null,
            tone = AuditTone.OK,
        )
        close.difference < 0 -> AuditRow(
            icon = Icons.Filled.Warning,
            title = "Faltó en caja",
            detail = detalle,
            amount = formatCUP(kotlin.math.abs(close.difference)),
            tone = AuditTone.SHORT,
        )
        else -> AuditRow(
            icon = Icons.Filled.Warning,
            title = "Sobró en caja",
            detail = detalle,
            amount = formatCUP(close.difference),
            tone = AuditTone.EXTRA,
        )
    }
}

@Composable
private fun InventoryCountCard(count: InventoryCountLite?) {
    if (count == null) {
        AuditRow(
            icon = Icons.Filled.Inventory2,
            title = "Inventario",
            detail = "Todavía sin arqueos",
            amount = null,
            tone = AuditTone.OK,
        )
        return
    }

    val quien = accountLabel(count.account_email) ?: "sin identificar"
    val cuando = count.counted_at_label ?: ""
    val base = listOf(cuando, quien).filter { it.isNotBlank() }.joinToString(" · ")

    when {
        count.units_missing > 0 -> AuditRow(
            icon = Icons.Filled.Warning,
            title = "Falta mercancía",
            // El valor grande va a precio de venta: es el dinero que debió
            // entrar a la caja por lo que salió del estante.
            detail = "${count.units_missing} uds · $base",
            amount = formatCUP(count.value_missing),
            tone = AuditTone.SHORT,
        )
        count.units_extra > 0 -> AuditRow(
            icon = Icons.Filled.Inventory2,
            title = "Sobra mercancía",
            detail = "${count.units_extra} uds · $base",
            amount = null,
            tone = AuditTone.EXTRA,
        )
        else -> AuditRow(
            icon = Icons.Filled.CheckCircle,
            title = "Inventario cuadró",
            detail = "${count.lines_count} productos · $base",
            amount = null,
            tone = AuditTone.OK,
        )
    }
}

@Composable
private fun CashierSummaryRow(summary: CashierSummary) {
    val falta = kotlin.math.round(summary.total_difference).toLong() < 0L
    val tone = when {
        falta -> AuditTone.SHORT
        isSquare(summary.total_difference) -> AuditTone.OK
        else -> AuditTone.EXTRA
    }
    val cierres = if (summary.closes == 1) "1 cierre" else "${summary.closes} cierres"
    val detalle = if (summary.times_short > 0) "$cierres · ${summary.times_short} con faltante" else cierres

    AuditRow(
        icon = Icons.Filled.Person,
        title = accountLabel(summary.account_email) ?: "Sin identificar",
        detail = detalle,
        amount = if (isSquare(summary.total_difference)) formatCUP(0.0)
        else (if (falta) "-" else "+") + formatCUP(kotlin.math.abs(summary.total_difference)),
        tone = tone,
    )
}

@Composable
private fun SaleRow(
    sale: SaleLite,
    expanded: Boolean,
    loadingDetail: Boolean,
    items: List<SaleItem>?,
    onClick: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Venta #${sale.id}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (sale.payment_method == "transferencia") "Transferencia" else "Efectivo",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(formatCUP(sale.total), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "Ocultar detalle" else "Ver qué se vendió",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            if (expanded) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                when {
                    loadingDetail -> Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        LoadingIndicator(modifier = Modifier.size(18.dp))
                    }
                    items.isNullOrEmpty() -> Text(
                        "No se pudo cargar el detalle",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items.forEach { line ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    "${line.quantity}× ${line.product_name}",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    formatCUP(line.unit_price * line.quantity),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            message,
            modifier = Modifier.padding(14.dp),
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
private fun SoldOutButton(count: Int, onClick: () -> Unit) {
    val hasAny = count > 0
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (hasAny) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        val color = if (hasAny) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(
                    if (hasAny) Icons.Filled.Warning else Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(24.dp),
                )
                Column {
                    Text("Productos agotados", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = color)
                    Text(
                        when (count) {
                            0 -> "No hay ninguno agotado"
                            1 -> "1 producto sin existencias"
                            else -> "$count productos sin existencias"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = color,
                    )
                }
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = "Ver lista", tint = color)
        }
    }
}

/** Lista a pantalla completa de lo que se acabó, grande y sin ruido. Debajo, lo
 * que está por acabarse, para que el jefe compre antes de quedarse sin él. */
@Composable
private fun SoldOutList(soldOut: List<ProductLite>, running: List<ProductLite>) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                "Productos agotados",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        if (soldOut.isEmpty()) {
            item {
                Text(
                    "Todo tiene existencias.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        } else {
            item {
                Text(
                    if (soldOut.size == 1) "1 producto sin existencias" else "${soldOut.size} productos sin existencias",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(soldOut, key = { "soldout-${it.id}" }) { product ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            product.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "AGOTADO",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
        }

        if (running.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                Text("Por acabarse", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            items(running, key = { "running-${it.id}" }) { product ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(product.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(
                        if (product.stock == 1) "queda 1" else "quedan ${product.stock}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
