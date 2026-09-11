package org.atlas.aldia.ui

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * formatCUP agrupa los miles a mano en vez de usar el formateador de cada
 * plataforma, precisamente para que Android e iOS muestren la misma cifra sin
 * depender del locale del dispositivo. Si esto se rompe, el dueño ve totales
 * distintos en su teléfono y en la caja, que es la clase de discrepancia que
 * destruye la confianza en un sistema de ventas.
 */
class FormatTest {

    @Test
    fun agrupa_los_miles_con_punto() {
        assertEquals("$ 1.234", formatCUP(1234.0))
        assertEquals("$ 12.345", formatCUP(12345.0))
        assertEquals("$ 123.456", formatCUP(123456.0))
        assertEquals("$ 1.234.567", formatCUP(1234567.0))
    }

    @Test
    fun no_agrupa_por_debajo_de_mil() {
        assertEquals("$ 0", formatCUP(0.0))
        assertEquals("$ 7", formatCUP(7.0))
        assertEquals("$ 999", formatCUP(999.0))
    }

    @Test
    fun redondea_al_entero_mas_cercano() {
        assertEquals("$ 100", formatCUP(99.5))
        assertEquals("$ 99", formatCUP(99.4))
        assertEquals("$ 1.000", formatCUP(999.6))
    }

    @Test
    fun el_signo_va_pegado_a_la_cifra_no_al_simbolo() {
        // El caso límite real: con la resta de ganancias un total puede quedar
        // negativo, y el signo no debe acabar delante del "$".
        assertEquals("$ -1.234", formatCUP(-1234.0))
        assertEquals("$ -7", formatCUP(-7.0))
    }

    @Test
    fun el_limite_exacto_de_los_mil_agrupa_bien() {
        // Un fallo clásico en agrupaciones hechas a mano: chunked(3) sobre la
        // cadena invertida deja un grupo vacío si la longitud es múltiplo de 3.
        assertEquals("$ 1.000", formatCUP(1000.0))
        assertEquals("$ 100", formatCUP(100.0))
        assertEquals("$ 1.000.000", formatCUP(1000000.0))
    }
}
