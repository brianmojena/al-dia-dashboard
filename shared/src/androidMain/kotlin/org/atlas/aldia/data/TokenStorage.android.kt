package org.atlas.aldia.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** MainActivity debe setear esto en onCreate, antes de que cualquier
 * pantalla intente leer la sesión guardada. Ver androidApp/MainActivity.kt. */
object AndroidAppContext {
    lateinit var appContext: Context
}

private const val PREFS_NAME = "aldia_session"
private const val KEY_TOKEN = "token"
private const val KEYSTORE = "AndroidKeyStore"
private const val KEY_ALIAS = "aldia_session_key"
private const val GCM_TAG_BITS = 128
private const val IV_BYTES = 12

/**
 * El JWT da acceso completo a la cuenta de la tienda durante 30 días, así que
 * no puede quedar en claro: en un dispositivo rooteado, o extrayendo un backup,
 * leer un SharedPreferences plano es trivial.
 *
 * Se cifra con AES/GCM usando una clave que vive dentro del Android Keystore y
 * que nunca sale de él — la app puede pedirle que cifre y descifre, pero no
 * puede exportar el material de la clave. En disco queda solo el criptograma,
 * con su IV por delante.
 *
 * GCM además autentica: si alguien manipula el valor guardado, el descifrado
 * falla en vez de devolver basura en silencio. Ese caso se trata como "no hay
 * sesión", que hace que la app pida login otra vez.
 */
actual class TokenStorage actual constructor() : SessionStore {

    private val prefs: SharedPreferences
        get() = AndroidAppContext.appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        // Primera ejecución: la clave se genera dentro del Keystore.
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                // Sin exigir autenticación de usuario: el dueño abre la app
                // muchas veces al día y pedirle huella en cada arranque haría
                // que dejara de usarla.
                .setUserAuthenticationRequired(false)
                .build(),
        )
        return generator.generateKey()
    }

    override fun getToken(): String? {
        val stored = prefs.getString(KEY_TOKEN, null) ?: return null
        return try {
            val blob = Base64.decode(stored, Base64.NO_WRAP)
            if (blob.size <= IV_BYTES) return null
            val iv = blob.copyOfRange(0, IV_BYTES)
            val cipherText = blob.copyOfRange(IV_BYTES, blob.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.doFinal(cipherText).decodeToString()
        } catch (_: Exception) {
            // Valor manipulado, clave invalidada (por ejemplo al restaurar un
            // backup en otro dispositivo) o un token viejo guardado en claro por
            // una versión anterior: en todos los casos no hay sesión utilizable.
            // Se limpia y se pide login.
            prefs.edit().remove(KEY_TOKEN).apply()
            null
        }
    }

    override fun setToken(token: String?) {
        if (token == null) {
            prefs.edit().remove(KEY_TOKEN).apply()
            return
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val cipherText = cipher.doFinal(token.encodeToByteArray())
        // El IV lo genera el proveedor en cada cifrado y se guarda junto al
        // criptograma: reutilizarlo con la misma clave rompería GCM por completo.
        val blob = cipher.iv + cipherText
        prefs.edit().putString(KEY_TOKEN, Base64.encodeToString(blob, Base64.NO_WRAP)).apply()
    }
}
