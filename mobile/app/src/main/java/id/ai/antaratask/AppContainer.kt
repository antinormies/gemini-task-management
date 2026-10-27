package id.ai.antaratask

import android.content.Context
import id.ai.antaratask.data.ApiClient
import id.ai.antaratask.data.GtmRepository
import id.ai.antaratask.data.TokenStore
import id.ai.antaratask.voice.TtsPlayer

class AppContainer(context: Context) {
    val tokenStore = TokenStore(context)
    val api = ApiClient().apply {
        baseUrl = tokenStore.serverUrl
        token = tokenStore.token
    }
    val repository = GtmRepository(api, tokenStore)
    val tts = TtsPlayer(context)
}
