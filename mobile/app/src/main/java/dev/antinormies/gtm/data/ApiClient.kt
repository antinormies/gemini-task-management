package dev.antinormies.gtm.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiException(val status: Int, message: String) : IOException(message)

class ApiClient {
    var baseUrl: String = "http://127.0.0.1:8765"
    var token: String? = null

    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    fun request(method: String, path: String, body: JSONObject? = null): JSONObject? {
        val builder = Request.Builder()
            .url(baseUrl.trimEnd('/') + path)
            .header("Accept", "application/json")
            .header("X-GTM-Request", "1")
            .header("X-GTM-Client", "android/0.1")
        token?.let { builder.header("Authorization", "Bearer " + it) }
        val payload = body?.toString()?.toRequestBody(jsonType)
        // OkHttp rejects POST/PUT/PATCH with a null body ("method POST must have
        // a request body"), which silently broke every body-less action the app
        // makes: complete, reopen, device revoke, unpair, backup, rebuild search.
        val requiresBody = method == "POST" || method == "PUT" || method == "PATCH"
        builder.method(
            method,
            if (requiresBody && payload == null) "{}".toRequestBody(jsonType) else payload
        )
        client.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = try {
                    val parsed = JSONObject(text)
                    val detail = parsed.opt("detail")
                    when (detail) {
                        is JSONObject -> detail.optString("message", detail.toString())
                        is String -> detail
                        else -> parsed.optString("message", "Request failed")
                    }
                } catch (_: Exception) {
                    "Request failed (" + response.code + ")"
                }
                throw ApiException(response.code, message)
            }
            if (text.isBlank()) return JSONObject()
            return JSONObject(text)
        }
    }
}
