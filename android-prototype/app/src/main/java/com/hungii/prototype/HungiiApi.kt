package com.hungii.prototype

import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

class ApiFailure(
    val code: String,
    override val message: String,
) : Exception(message)

private const val URL_SAFE = Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
private const val API_PATH = "/functions/v1/hungii-api?forceFunctionRegion=ap-south-1"
private const val REFRESH_PATH = "/functions/v1/hungii-api/auth/refresh?forceFunctionRegion=ap-south-1"
private const val LOGOUT_PREFIX = "https://api.workos.com/user_management/sessions/logout?"
private val JSON_TYPE = "application/json".toMediaType()

/**
 * Talks to the Hungii backend with WorkOS sessions, or to the Simulator gateway.
 * Refresh tokens are kept encrypted by [SecureSession]; access tokens stay in memory.
 */
class HungiiApi(
    private val secure: SecureSession,
) : Backend {
    private val http =
        OkHttpClient
            .Builder()
            .callTimeout(70, TimeUnit.SECONDS)
            .readTimeout(70, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    private val sessionLock = Mutex()
    private var access: String? = null
    private var expires = 0L
    private val redirectUri get() = "${BuildConfig.APPLICATION_ID}://auth-return"

    init {
        // Sessions from the earlier Supabase-auth preview cannot be refreshed by WorkOS.
        if (secure.read("refresh") != null && secure.read("provider") != "workos") secure.eraseAuth()
    }

    val configured
        get() =
            BuildConfig.LOCAL_DEMO ||
                (BuildConfig.SUPABASE_URL.startsWith("https://") && BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank())
    val signedIn get() = BuildConfig.LOCAL_DEMO || secure.read("refresh") != null
    val userId get() = if (BuildConfig.LOCAL_DEMO) "local-demo" else secure.read("user")

    private fun random() = Base64.encodeToString(ByteArray(32).apply { SecureRandom().nextBytes(this) }, URL_SAFE)

    private suspend fun request(
        path: String,
        body: JSONObject?,
        token: String? = null,
        workos: Boolean = false,
    ): JSONObject =
        withContext(Dispatchers.IO) {
            if (!configured) throw ApiFailure("HUNGII_SETUP_REQUIRED", "The Swiggy connection is being set up. Your tracker works offline.")
            val base = if (workos) "https://api.workos.com" else BuildConfig.SUPABASE_URL.trimEnd('/')
            val builder = Request.Builder().url(base + path)
            if (!workos) builder.header("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            if (token != null) builder.header("Authorization", "Bearer $token")
            if (body != null) builder.post(body.toString().toRequestBody(JSON_TYPE))
            val response =
                try {
                    http.newCall(builder.build()).execute()
                } catch (_: IOException) {
                    throw ApiFailure("HUNGII_OFFLINE", "Could not connect. Check your internet connection and try again.")
                }
            response.use {
                val data =
                    try {
                        JSONObject(it.body.string().ifEmpty { "{}" })
                    } catch (_: Exception) {
                        JSONObject()
                    }
                if (!it.isSuccessful) {
                    val error = data.optJSONObject("error")
                    throw ApiFailure(
                        error?.optString("code") ?: "HUNGII_REQUEST_FAILED",
                        error?.optString("message")?.takeIf(String::isNotBlank) ?: "Could not complete this request. Please try again.",
                    )
                }
                data
            }
        }

    /** Starts hosted AuthKit sign-in with PKCE and a one-time state. */
    fun signInUrl(): String {
        if (!configured || !BuildConfig.WORKOS_AUTH_READY || !BuildConfig.WORKOS_CLIENT_ID.startsWith("client_")) {
            throw ApiFailure("HUNGII_SETUP_REQUIRED", "Email sign-in is being set up. Your tracker works offline.")
        }
        val verifier = random()
        val flow = random()
        secure.put("pkce", verifier)
        secure.put("loginFlow", flow)
        secure.put("loginStarted", System.currentTimeMillis().toString())
        val challenge = Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()), URL_SAFE)
        return "https://api.workos.com/user_management/authorize"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("provider", "authkit")
            .addQueryParameter("response_type", "code")
            .addQueryParameter("client_id", BuildConfig.WORKOS_CLIENT_ID)
            .addQueryParameter("redirect_uri", redirectUri)
            .addQueryParameter("state", flow)
            .addQueryParameter("code_challenge", challenge)
            .addQueryParameter("code_challenge_method", "S256")
            .build()
            .toString()
    }

    suspend fun callback(uri: Uri) {
        if (uri.scheme != BuildConfig.APPLICATION_ID || uri.host != "auth-return") {
            throw ApiFailure("HUNGII_LOGIN_EXPIRED", "Invalid sign-in link.")
        }
        val verifier = secure.read("pkce") ?: throw ApiFailure("HUNGII_LOGIN_EXPIRED", "Start signing in again.")
        val expected = secure.read("loginFlow")
        val started = secure.read("loginStarted")?.toLongOrNull() ?: 0
        try {
            val age = System.currentTimeMillis() - started
            if (expected == null || expected != uri.getQueryParameter("state") || started <= 0 || age !in 0..10 * 60_000L) {
                throw ApiFailure("HUNGII_LOGIN_EXPIRED", "This sign-in link is invalid or expired. Start again.")
            }
            if (uri.getQueryParameter("error") != null) throw ApiFailure("HUNGII_LOGIN_CANCELLED", "Sign-in was cancelled.")
            val code = uri.getQueryParameter("code") ?: throw ApiFailure("HUNGII_LOGIN_CANCELLED", "Sign-in was cancelled.")
            val grant =
                JSONObject()
                    .put("grant_type", "authorization_code")
                    .put("client_id", BuildConfig.WORKOS_CLIENT_ID)
                    .put("code", code)
                    .put("code_verifier", verifier)
            sessionLock.withLock { save(request("/user_management/authenticate", grant, workos = true)) }
        } finally {
            secure.remove("pkce")
            secure.remove("loginFlow")
            secure.remove("loginStarted")
        }
    }

    private fun save(response: JSONObject) {
        val access = response.optString("access_token")
        val refresh = response.optString("refresh_token")
        val user = response.optJSONObject("user")?.optString("id")
        if (access.isBlank() || refresh.isBlank() || user.isNullOrBlank() || !user.startsWith("user_")) {
            throw ApiFailure("HUNGII_LOGIN_FAILED", "Please sign in again.")
        }
        // Decoding sets local refresh timing only; the backend verifies the signature,
        // issuer, client and live session before authorizing any data access.
        val claims =
            try {
                JSONObject(String(Base64.decode(access.split('.')[1], URL_SAFE)))
            } catch (_: Exception) {
                throw ApiFailure("HUNGII_LOGIN_FAILED", "Invalid account session.")
            }
        val expired = claims.optLong("exp") <= System.currentTimeMillis() / 1000
        if (claims.optString("sub") != user || claims.optString("client_id") != BuildConfig.WORKOS_CLIENT_ID || expired) {
            throw ApiFailure("HUNGII_LOGIN_FAILED", "Invalid account session.")
        }
        secure.put("refresh", refresh)
        secure.put("user", user)
        secure.put("provider", "workos")
        this.access = access
        expires = claims.getLong("exp")
        secure.remove("access")
        secure.remove("expires")
    }

    private suspend fun accessToken(): String =
        sessionLock.withLock {
            if (access == null || expires <= System.currentTimeMillis() / 1000 + 60) {
                val refresh = secure.read("refresh") ?: throw ApiFailure("HUNGII_LOGIN_REQUIRED", "Sign in to Hungii first.")
                save(request(REFRESH_PATH, JSONObject().put("refresh_token", refresh)))
            }
            access ?: throw ApiFailure("HUNGII_LOGIN_REQUIRED", "Sign in to Hungii first.")
        }

    override suspend fun action(
        action: String,
        args: JSONObject,
    ): JSONObject {
        args.put("action", action)
        if (BuildConfig.LOCAL_DEMO) {
            return withContext(Dispatchers.IO) {
                val demoSession = secure.read("demoSession") ?: random().also { secure.put("demoSession", it) }
                val request =
                    Request
                        .Builder()
                        .url(BuildConfig.DEMO_API_URL)
                        .header("X-Hungii-Demo-Session", demoSession)
                        .post(args.toString().toRequestBody(JSON_TYPE))
                        .build()
                SimulatorTransport(http).execute(request)
            }
        }
        return request(API_PATH, args, accessToken())
    }

    fun clearSession() {
        access = null
        expires = 0
        secure.eraseAuth()
    }

    /** Revokes the session remotely when possible; local credentials are always cleared. */
    suspend fun signOut(): String? {
        try {
            if (!signedIn || BuildConfig.LOCAL_DEMO) return null
            return action("sign_out", JSONObject()).optString("logoutUrl").takeIf { it.startsWith(LOGOUT_PREFIX) }
        } finally {
            clearSession()
        }
    }
}
