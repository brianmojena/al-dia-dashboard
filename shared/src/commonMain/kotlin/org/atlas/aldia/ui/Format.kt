package org.atlas.aldia.ui

import kotlin.math.roundToLong

// Mismo estilo que usa la web/escritorio: "$ 1.234" (separador de miles con
// punto, sin decimales — así se manejan los precios en CUP en todo el sistema).
fun formatCUP(amount: Double): String {
    val rounded = amount.roundToLong()
    val negative = rounded < 0
    val digits = kotlin.math.abs(rounded).toString()
    val grouped = digits.reversed().chunked(3).joinToString(".").reversed()
    return "$ " + (if (negative) "-$grouped" else grouped)
}
