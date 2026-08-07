package org.atlas.aldia.data

// Persiste la sesión (JWT + datos básicos del usuario) entre reinicios de la
// app, para no pedirle al dueño que inicie sesión cada vez que la abre.
// El JWT vive 30 días (server/routes/auth.js) — mismo comportamiento que la
// web/escritorio: se sigue confiando en la sesión guardada hasta que el
// servidor la rechace con 401.
expect class TokenStorage() {
    fun getToken(): String?
    fun setToken(token: String?)
}
