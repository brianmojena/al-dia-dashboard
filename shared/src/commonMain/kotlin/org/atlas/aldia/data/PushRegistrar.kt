package org.atlas.aldia.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Teléfono identificado por el sistema de avisos de su plataforma. */
data class PushDevice(val platform: String, val token: String)

/**
 * Puente entre el código nativo y el ViewModel. iOS (AppDelegate) y Android
 * (servicio de FCM) reciben el token del sistema operativo y lo dejan acá con
 * onToken; el ViewModel lo registra en el backend cuando hay sesión iniciada.
 * Sirve igual si el token llega antes o después del login.
 */
object PushRegistrar {
    private val _device = MutableStateFlow<PushDevice?>(null)
    val device: StateFlow<PushDevice?> = _device.asStateFlow()

    /** platform: "ios" o "android" (lo que espera /api/push/register). */
    fun onToken(platform: String, token: String) {
        _device.value = PushDevice(platform, token)
    }
}
