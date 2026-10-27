package id.ai.antaratask

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import id.ai.antaratask.ui.GtmApp
import id.ai.antaratask.ui.theme.GtmTheme

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
