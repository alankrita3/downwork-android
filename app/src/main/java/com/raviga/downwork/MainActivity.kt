package com.raviga.downwork

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController
import com.raviga.downwork.ui.LocalAppContainer
import com.raviga.downwork.ui.app.AppNavHost
import com.raviga.downwork.ui.theme.DownWorkTheme

class MainActivity : ComponentActivity() {

    private var pendingProject by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        pendingProject = projectFrom(intent)
        val container = (application as DownWorkApp).container
        setContent {
            DownWorkTheme {
                CompositionLocalProvider(LocalAppContainer provides container) {
                    val nav = rememberNavController()
                    AppNavHost(
                        nav = nav,
                        pendingDeepLinkProject = pendingProject,
                        onDeepLinkConsumed = { pendingProject = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        projectFrom(intent)?.let { pendingProject = it }
    }

    /** downwork://project/{id} from a notification or the delivery email. */
    private fun projectFrom(intent: Intent?): String? {
        val data = intent?.data ?: return null
        if (data.scheme != "downwork" || data.host != "project") return null
        return data.pathSegments.firstOrNull()?.takeIf { it.isNotBlank() }
    }
}
