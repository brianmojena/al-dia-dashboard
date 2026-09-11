package org.atlas.aldia.ui

import kotlin.math.roundToLong

// Mismo estilo que usa la web/escritorio: "$ 1.234" (separador de miles con
// punto, sin decimales — así se manejan los precios en CUP en todo el sistema).
// "yamila@mitienda.cu" → "yamila". El dueño conoce a su gente por el nombre,
// no por el dominio. Devuelve null cuando la operación es anterior a que
// existiera la atribución: ahí se muestra "sin identificar", que es más honesto
// que inventar un responsable.
fun accountLabel(email: String?): String? =
    email?.substringBefore('@')?.takeIf { it.isNotBlank() }

fun formatCUP(amount: Double): String {
    val rounded = amount.roundToLong()
    val negative = rounded < 0
    val digits = kotlin.math.abs(rounded).toString()
    val grouped = digits.reversed().chunked(3).joinToString(".").reversed()
    return "$ " + (if (negative) "-$grouped" else grouped)
}
