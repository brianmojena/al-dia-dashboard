package org.atlas.aldia.data

/**
 * Doble en memoria de SessionStore. Existe para que los tests de ApiClient
 * puedan correr en commonTest: TokenStorage real es una expect class y sus
 * implementaciones dependen del Keystore de Android y del Keychain de iOS,
 * ninguno de los cuales existe en una JVM de test.
 */
class FakeTokenStorage(private var token: String? = null) : SessionStore {
    override fun getToken(): String? = token
    override fun setToken(token: String?) {
        this.token = token
    }
}
