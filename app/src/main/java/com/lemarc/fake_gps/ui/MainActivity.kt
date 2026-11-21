package com.lemarc.fake_gps.ui

import android.os.Bundle
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.lemarc.fake_gps.ui.theme.FakeGpsTheme
import org.osmdroid.config.Configuration

/** Point d'entrée : configure osmdroid puis affiche [MainScreen]. */
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // La carte reste claire même en thème sombre : icônes de barre d'état toujours foncées.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        // osmdroid lit sa configuration (cache des tuiles, user agent) avant la création de la carte.
        Configuration.getInstance().load(this, getSharedPreferences(OSMDROID_PREFS, MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName

        setContent {
            FakeGpsTheme {
                MainScreen(viewModel)
            }
        }
    }

    private companion object {
        const val OSMDROID_PREFS = "osmdroid"
    }
}
