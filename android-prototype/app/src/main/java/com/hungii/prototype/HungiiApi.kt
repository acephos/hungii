package com.hungii.prototype

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

class ApiFailure(val code: String, override val message: String) : Exception(message)

class HungiiApi(private val secure: SecureSession) {
    private val http = OkHttpClient.Builder().callTimeout(45, TimeUnit.SECONDS).retryOnConnectionFailure(false).build()
    val configured get() = BuildConfig.LOCAL_DEMO || BuildConfig.SUPABASE_URL.startsWith("https://") && BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank()
    val signedIn get() = BuildConfig.LOCAL_DEMO || secure.read("refresh") != null
    val userId get() = if(BuildConfig.LOCAL_DEMO) "local-demo" else secure.read("user")
    private fun random() = Base64.encodeToString(ByteArray(32).apply { SecureRandom().nextBytes(this) }, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    private suspend fun request(path: String, body: JSONObject?, token: String? = null): JSONObject = withContext(Dispatchers.IO) {
        if (!configured) throw ApiFailure("HUNGII_SETUP_REQUIRED", "The Swiggy connection is being set up. Your tracker works offline.")
        val builder = Request.Builder().url(BuildConfig.SUPABASE_URL.trimEnd('/') + path)
            .header("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
        if (token != null) builder.header("Authorization", "Bearer $token")
        if (body != null) builder.post(body.toString().toRequestBody("application/json".toMediaType()))
        val response = try { http.newCall(builder.build()).execute() } catch (_: java.io.IOException) {
            throw ApiFailure("HUNGII_OFFLINE", "Could not connect. Check your internet connection and try again.")
        }
        response.use {
            val data = try { JSONObject(it.body?.string() ?: "{}") } catch (_: Exception) { JSONObject() }
            if (!it.isSuccessful) {
                val error = data.optJSONObject("error")
                throw ApiFailure(error?.optString("code") ?: "HUNGII_REQUEST_FAILED", error?.optString("message")?.takeIf(String::isNotBlank) ?: "Could not complete this request. Please try again.")
            }
            data
        }
    }
    fun signInUrl(): String {
        if (!configured) throw ApiFailure("HUNGII_SETUP_REQUIRED", "The account service is being set up. Your tracker works offline.")
        val verifier = random(); val flow = random()
        secure.put("pkce", verifier); secure.put("loginFlow", flow); secure.put("loginStarted", System.currentTimeMillis().toString())
        val challenge = Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        return (BuildConfig.SUPABASE_URL.trimEnd('/') + "/auth/v1/authorize").toHttpUrl().newBuilder()
            .addQueryParameter("provider", "google").addQueryParameter("scopes", "openid email profile")
            .addQueryParameter("redirect_to", "hungii://auth-return?flow=$flow")
            .addQueryParameter("code_challenge", challenge).addQueryParameter("code_challenge_method", "s256").build().toString()
    }
    suspend fun callback(uri: Uri) {
        if (uri.scheme != "hungii" || uri.host != "auth-return") return
        val verifier = secure.read("pkce") ?: throw ApiFailure("HUNGII_LOGIN_EXPIRED", "Start signing in again.")
        val expected = secure.read("loginFlow")
        val started = secure.read("loginStarted")?.toLongOrNull() ?: 0
        if (expected != uri.getQueryParameter("flow") || System.currentTimeMillis() - started > 10 * 60_000) throw ApiFailure("HUNGII_LOGIN_EXPIRED", "This sign-in link is invalid or expired. Start again.")
        try {
            val code = uri.getQueryParameter("code") ?: throw ApiFailure("HUNGII_LOGIN_CANCELLED", "Sign-in was cancelled.")
            save(request("/auth/v1/token?grant_type=pkce", JSONObject().put("auth_code", code).put("code_verifier", verifier)))
        } finally { secure.remove("pkce"); secure.remove("loginFlow"); secure.remove("loginStarted") }
    }
    private fun save(response: JSONObject) {
        val access = response.optString("access_token"); val refresh = response.optString("refresh_token"); val user = response.optJSONObject("user")?.optString("id")
        if (access.isBlank() || refresh.isBlank() || user.isNullOrBlank()) throw ApiFailure("HUNGII_LOGIN_FAILED", "Please sign in again.")
        secure.put("access", access); secure.put("refresh", refresh); secure.put("user", user)
        secure.put("expires", response.optLong("expires_at", System.currentTimeMillis()/1000 + response.optLong("expires_in", 3600)).toString())
    }
    private suspend fun accessToken(): String {
        val expires = secure.read("expires")?.toLongOrNull() ?: 0
        if (expires <= System.currentTimeMillis()/1000 + 60) {
            val refresh = secure.read("refresh") ?: throw ApiFailure("HUNGII_LOGIN_REQUIRED", "Sign in to Hungii first.")
            save(request("/auth/v1/token?grant_type=refresh_token", JSONObject().put("refresh_token", refresh)))
        }
        return secure.read("access") ?: throw ApiFailure("HUNGII_LOGIN_REQUIRED", "Sign in to Hungii first.")
    }
    suspend fun action(action: String, args: JSONObject = JSONObject()): JSONObject {
        args.put("action",action)
        if(BuildConfig.LOCAL_DEMO) return withContext(Dispatchers.IO) {
            val request=Request.Builder().url("http://10.0.2.2:8788/api").post(args.toString().toRequestBody("application/json".toMediaType())).build()
            http.newCall(request).execute().use { response ->
                val data=JSONObject(response.body?.string() ?: "{}")
                if(!response.isSuccessful) throw ApiFailure(data.optJSONObject("error")?.optString("code") ?: "HUNGII_DEMO",data.optJSONObject("error")?.optString("message") ?: "Start the local demo server.")
                data
            }
        }
        return request("/functions/v1/hungii-api?forceFunctionRegion=ap-south-1",args,accessToken())
    }
    fun clearSession() {secure.eraseAuth()}
    suspend fun signOut() {
        try { if (signedIn&&!BuildConfig.LOCAL_DEMO) request("/auth/v1/logout?scope=local", JSONObject(), accessToken()) }
        finally { clearSession() }
    }
}
