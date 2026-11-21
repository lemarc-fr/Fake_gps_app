package com.lemarc.fake_gps.ui

import android.os.Bundle
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.lemarc.fake_gps.ui.theme.FakeGpsTheme

/** Point d'entrée : affiche [MainScreen]. */
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // La carte reste claire même en thème sombre : icônes de barre d'état toujours foncées.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT))
        super.onCreate(savedInstanceState)

        setContent {
            FakeGpsTheme {
                MainScreen(viewModel)
            }
        }
    }
}
