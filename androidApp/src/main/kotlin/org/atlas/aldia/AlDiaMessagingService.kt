package org.atlas.aldia

import com.google.firebase.messaging.FirebaseMessagingService
import org.atlas.aldia.data.PushRegistrar

/** FCM renueva el token de vez en cuando; el ViewModel lo vuelve a registrar. */
class AlDiaMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        PushRegistrar.onToken("android", token)
    }
}
