package id.ai.antaratask.data

import android.content.Context
import id.ai.antaratask.BuildConfig
import android.os.Build
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class TokenStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("gtm_settings", Context.MODE_PRIVATE)

    private val securePrefs by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            "gtm_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    var serverUrl: String
        get() = prefs.getString("server_url", BuildConfig.GTM_BASE_URL)
            ?.takeUnless { it.startsWith("http://127.0.0.1") || it.startsWith("http://localhost") }
            ?: BuildConfig.GTM_BASE_URL
        set(value) = prefs.edit().putString("server_url", value.trim().trimEnd('/')).apply()

    var token: String?
        get() = runCatching { securePrefs.getString("token", null) }.getOrNull()
        set(value) {
            runCatching {
                if (value == null) securePrefs.edit().remove("token").apply()
                else securePrefs.edit().putString("token", value).apply()
            }
        }

    val deviceName: String
        get() = (Build.MANUFACTURER + " " + Build.MODEL).trim()

    fun clear() {
        token = null
        prefs.edit().remove("server_url").apply()
    }
}
