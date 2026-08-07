package org.atlas.aldia.data

import platform.Foundation.NSUserDefaults

private const val KEY_TOKEN = "aldia_token"

actual class TokenStorage actual constructor() {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual fun getToken(): String? = defaults.stringForKey(KEY_TOKEN)

    actual fun setToken(token: String?) {
        if (token == null) defaults.removeObjectForKey(KEY_TOKEN)
        else defaults.setObject(token, KEY_TOKEN)
    }
}
