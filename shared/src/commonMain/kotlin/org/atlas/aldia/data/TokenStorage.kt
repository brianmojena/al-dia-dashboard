package org.atlas.aldia.data

/**
 * Contrato de persistencia de sesión, separado de la implementación concreta
 * para que ApiClient y AppViewModel dependan de la capacidad ("guardar y leer
 * un token") y no del almacén real de cada plataforma. Es lo que permite
 * probarlos en commonTest sin Android ni iOS de por medio.
 */
interface SessionStore {
    fun getToken(): String?
    fun setToken(token: String?)
}

// Persiste la sesión (JWT + datos básicos del usuario) entre reinicios de la
// app, para no pedirle al dueño que inicie sesión cada vez que la abre.
// El JWT vive 30 días (server/routes/auth.js) — mismo comportamiento que la
// web/escritorio: se sigue confiando en la sesión guardada hasta que el
// servidor la rechace con 401.
//
// El token es una credencial, no una preferencia: va cifrado en ambas
// plataformas (Keystore en Android, Keychain en iOS). Ver los `actual`.
expect class TokenStorage() : SessionStore
