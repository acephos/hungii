package com.hungii.prototype

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

/** HTTP boundary for the synthetic gateway; provider authentication is separate. */
internal class SimulatorTransport(private val http: OkHttpClient) {
    fun execute(request: Request): JSONObject = try { http.newCall(request).execute().use { response ->
        val data = JSONObject(response.body?.string() ?: "{}")
        if (!response.isSuccessful) throw ApiFailure(
            data.optJSONObject("error")?.optString("code") ?: "HUNGII_DEMO",
            data.optJSONObject("error")?.optString("message") ?: "Start the local demo server."
        )
        data
    } } catch (_: IOException) {
        throw ApiFailure("HUNGII_DEMO_OFFLINE",
            "Cannot reach the simulator at ${request.url.host}:${request.url.port}. " +
                "Keep the simulator running on your computer. On your phone, connect Tailscale, then tap Refresh connection.")
    }
}
