package dev.antinormies.gtm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import dev.antinormies.gtm.ui.GtmApp
import dev.antinormies.gtm.ui.theme.GtmTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = AppContainer(applicationContext)
        setContent {
            GtmTheme {
                val viewModel = remember { MainViewModel(container.repository, container.tts) }
                GtmApp(viewModel)
            }
        }
    }
}
