package dev.antinormies.gtm

import android.content.Context
import dev.antinormies.gtm.data.ApiClient
import dev.antinormies.gtm.data.GtmRepository
import dev.antinormies.gtm.data.TokenStore
import dev.antinormies.gtm.voice.TtsPlayer

class AppContainer(context: Context) {
    val tokenStore = TokenStore(context)
    val api = ApiClient().apply {
        baseUrl = tokenStore.serverUrl
        token = tokenStore.token
    }
    val repository = GtmRepository(api, tokenStore)
    val tts = TtsPlayer(context)
}
