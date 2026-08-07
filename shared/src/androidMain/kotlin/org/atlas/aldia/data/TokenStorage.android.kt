package org.atlas.aldia.data

import android.content.Context
import android.content.SharedPreferences

/** MainActivity debe setear esto en onCreate, antes de que cualquier
 * pantalla intente leer la sesión guardada. Ver androidApp/MainActivity.kt. */
object AndroidAppContext {
    lateinit var appContext: Context
}

private const val PREFS_NAME = "aldia_session"
private const val KEY_TOKEN = "token"

actual class TokenStorage actual constructor() {
    private val prefs: SharedPreferences
        get() = AndroidAppContext.appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    actual fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    actual fun setToken(token: String?) {
        prefs.edit().apply {
            if (token == null) remove(KEY_TOKEN) else putString(KEY_TOKEN, token)
        }.apply()
    }
}
