package org.atlas.aldia

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import com.google.firebase.messaging.FirebaseMessaging
import org.atlas.aldia.data.PushRegistrar
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import org.atlas.aldia.data.AndroidAppContext

class MainActivity : ComponentActivity() {
    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* sin permiso, simplemente no hay avisos */ }

    // Pide permiso (Android 13+) y entrega el token de FCM al código compartido.
    // Sin google-services.json Firebase no está inicializado: se ignora en silencio.
    private fun setUpPush() {
        if (Build.VERSION.SDK_INT >= 33) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        runCatching {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { PushRegistrar.onToken("android", it) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AndroidAppContext.appContext = applicationContext

        setUpPush()

        setContent {
            App()
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}