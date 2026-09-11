package org.atlas.aldia.data

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSUserDefaults
import platform.Foundation.create
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlock
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

private const val SERVICE = "cu.aldia.dashboard"
private const val ACCOUNT = "session_token"

// Clave del almacenamiento anterior en claro. Se conserva solo para migrarla y
// borrarla: una versión previa dejaba el JWT en NSUserDefaults.
private const val LEGACY_KEY = "aldia_token"

// CFStrings creados una sola vez para la vida del proceso. Se crean con
// CFStringCreateWithCString en lugar de puentear un String de Kotlin porque las
// claves del Keychain tienen que ser CFStrings de verdad, no objetos puenteados.
@OptIn(ExperimentalForeignApi::class)
private val serviceCF: CFStringRef? by lazy {
    CFStringCreateWithCString(kCFAllocatorDefault, SERVICE, kCFStringEncodingUTF8)
}

@OptIn(ExperimentalForeignApi::class)
private val accountCF: CFStringRef? by lazy {
    CFStringCreateWithCString(kCFAllocatorDefault, ACCOUNT, kCFStringEncodingUTF8)
}

// String <-> NSData sin pasar por NSString: el puente String/NSString de
// Kotlin/Native no admite un cast directo, así que se convierte por bytes.

@OptIn(ExperimentalForeignApi::class)
private fun String.toNSData(): NSData {
    val bytes = encodeToByteArray()
    if (bytes.isEmpty()) return NSData()
    return bytes.usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = bytes.size.convert())
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toKotlinStringOrNull(): String? {
    val length = this.length.toInt()
    if (length == 0) return ""
    val pointer: CPointer<ByteVar> = bytes?.reinterpret() ?: return null
    return pointer.readBytes(length).decodeToString()
}

/**
 * El JWT da acceso completo a la cuenta durante 30 días. NSUserDefaults lo
 * guardaba en un plist en claro dentro del contenedor de la app — legible sin
 * esfuerzo desde un backup o un dispositivo con jailbreak. El Keychain es el
 * almacén que iOS cifra respaldado por hardware, y es donde debe vivir una
 * credencial.
 *
 * Accesibilidad: kSecAttrAccessibleAfterFirstUnlock — legible tras el primer
 * desbloqueo desde el arranque, aunque la pantalla se bloquee después. Permite
 * refrescos en segundo plano sin exponer el token en un dispositivo apagado.
 *
 * NO HAY TESTS UNITARIOS DE ESTA CLASE, y no por descuido: el binario de test
 * de Kotlin/Native no es un app bundle — no tiene bundle id ni entitlements de
 * keychain — así que toda llamada a SecItem* devuelve errSecNotAvailable
 * (-25291) antes siquiera de tocar esta lógica. Verificado ejecutando el
 * diagnóstico en el simulador. Se comprueba ejecutando la app: iniciar sesión,
 * cerrarla por completo y reabrirla; si la sesión persiste, el ciclo funciona.
 */
@OptIn(ExperimentalForeignApi::class)
actual class TokenStorage actual constructor() : SessionStore {

    /**
     * El diccionario se construye con la API de CoreFoundation en vez de con un
     * Map de Kotlin puenteado: las claves (kSecClass, kSecAttrService…) ya son
     * CFStringRef, y meterlas en un Map para puentearlo después no produce un
     * CFDictionary válido — el Keychain guarda pero luego no encuentra nada.
     *
     * Quien lo llama es responsable de CFRelease.
     */
    private fun newBaseQuery(): CFMutableDictionaryRef? {
        val dict = CFDictionaryCreateMutable(
            kCFAllocatorDefault,
            0,
            kCFTypeDictionaryKeyCallBacks.ptr,
            kCFTypeDictionaryValueCallBacks.ptr,
        ) ?: return null

        CFDictionaryAddValue(dict, kSecClass, kSecClassGenericPassword)
        CFDictionaryAddValue(dict, kSecAttrService, serviceCF)
        CFDictionaryAddValue(dict, kSecAttrAccount, accountCF)
        return dict
    }

    override fun getToken(): String? {
        migrateLegacyIfPresent()

        val query = newBaseQuery() ?: return null
        CFDictionaryAddValue(query, kSecReturnData, kCFBooleanTrue)

        return try {
            memScoped {
                val result = alloc<CFTypeRefVar>()
                val status = SecItemCopyMatching(query, result.ptr)
                if (status != errSecSuccess) return@memScoped null

                // CFBridgingRelease devuelve la propiedad a ARC: el NSData sale
                // de aquí ya gestionado, sin liberarlo a mano.
                val data = CFBridgingRelease(result.value) as? NSData ?: return@memScoped null
                data.toKotlinStringOrNull()
            }
        } finally {
            CFRelease(query)
        }
    }

    override fun setToken(token: String?) {
        // El Keychain no tiene "upsert": sin borrar antes, SecItemAdd devuelve
        // errSecDuplicateItem y se sigue leyendo el token viejo — que tras un
        // relogin significa quedarse con la sesión equivocada.
        newBaseQuery()?.let { deleteQuery ->
            try {
                SecItemDelete(deleteQuery)
            } finally {
                CFRelease(deleteQuery)
            }
        }
        if (token == null) return

        val addQuery = newBaseQuery() ?: return
        // NSData sí es un objeto Objective-C real, así que aquí el puente sí
        // aplica (a diferencia de las claves CFString de arriba).
        val cfData = CFBridgingRetain(token.toNSData()) as CFDataRef?
        try {
            CFDictionaryAddValue(addQuery, kSecValueData, cfData)
            CFDictionaryAddValue(addQuery, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlock)
            val status = SecItemAdd(addQuery, null)
            // Ignorar el OSStatus haría que un fallo de escritura se tradujera
            // en "la sesión no se guardó" sin ninguna señal: el dueño volvería
            // a ver la pantalla de login al reabrir, sin explicación posible.
            if (status != errSecSuccess) {
                println("TokenStorage: el Keychain rechazó la escritura (OSStatus $status)")
            }
        } finally {
            cfData?.let { CFRelease(it) }
            CFRelease(addQuery)
        }
    }

    /**
     * Quien ya tenía sesión con la versión anterior no debería verse obligado a
     * iniciarla de nuevo: si queda un token en claro, se mueve al Keychain y se
     * borra del plist.
     */
    private fun migrateLegacyIfPresent() {
        val defaults = NSUserDefaults.standardUserDefaults
        val legacy = defaults.stringForKey(LEGACY_KEY) ?: return
        defaults.removeObjectForKey(LEGACY_KEY)
        if (legacy.isNotBlank()) setToken(legacy)
    }
}
