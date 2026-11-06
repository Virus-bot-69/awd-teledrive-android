package com.awd.teledrive.data.google

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.awd.teledrive.core.Config
import com.awd.teledrive.data.secure.SecureSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Minimal OAuth 2.0 + PKCE client for Google (no Play Services, no AppAuth dependency).
 *
 * Flow:
 *  1. [buildAuthorizationUrl] -> open in any browser
 *  2. Google redirects to Config.googleRedirectScheme:/oauth2redirect?code=...
 *     (caught by the intent-filter in AndroidManifest.xml -> MainActivity)
 *  3. [exchangeCode] -> access token + refresh token, stored in EncryptedSharedPreferences
 *  4. [validAccessToken] -> refreshes automatically when expired
 */
@Singleton
class GoogleOAuth @Inject constructor(
    private val secureSettings: SecureSettings,
    @param:ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "GoogleOAuth"
        private const val AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
        private const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
        private const val K_ACCESS = "google_access_token"
        private const val K_REFRESH = "google_refresh_token"
        private const val K_EXPIRES = "google_expires_at"
        private const val K_EMAIL = "google_email"
        private const val K_VERIFIER = "google_code_verifier"
        private const val K_STATE = "google_oauth_state"
    }

    private fun redirectUri(): String =
        Config.googleRedirectScheme + ":" + Config.GOOGLE_REDIRECT_PATH

    /** Step 1 — returns the URL to open in a browser. Suspending: touches prefs only. */
    fun buildAuthorizationUrl(loginHint: String? = null): String? {
        if (!Config.isGoogleConfigured) return null
        val verifier = randomUrlSafe(64)
        val challenge = sha256Base64Url(verifier)
        val state = randomUrlSafe(24)
        secureSettings.saveString(K_VERIFIER, verifier)
        secureSettings.saveString(K_STATE, state)

        return Uri.parse(AUTH_ENDPOINT).buildUpon()
            .appendQueryParameter("client_id", Config.GOOGLE_CLIENT_ID)
            .appendQueryParameter("redirect_uri", redirectUri())
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", Config.GOOGLE_EMAIL_SCOPE + " " + Config.GOOGLE_DRIVE_SCOPE)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("access_type", "offline")
            .appendQueryParameter("prompt", "consent")
            .appendQueryParameter("state", state)
            .apply { if (!loginHint.isNullOrBlank()) appendQueryParameter("login_hint", loginHint) }
            .build()
            .toString()
    }

    /** Step 3 — call with the redirect Uri your Activity received. Returns true on success. */
    suspend fun exchangeCode(redirect: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val code = redirect.getQueryParameter("code") ?: return@withContext false
            val state = redirect.getQueryParameter("state")
            val expectedState = secureSettings.getString(K_STATE, "")
            if (!expectedState.isNullOrEmpty() && state != null && state != expectedState) {
                Log.w(TAG, "OAuth state mismatch — ignoring response")
                return@withContext false
            }
            val verifier = secureSettings.getString(K_VERIFIER, "") ?: return@withContext false

            val body = formEncode(
                mapOf(
                    "client_id" to Config.GOOGLE_CLIENT_ID,
                    "code" to code,
                    "code_verifier" to verifier,
                    "grant_type" to "authorization_code",
                    "redirect_uri" to redirectUri()
                )
            )
            val json = postForm(TOKEN_ENDPOINT, body) ?: return@withContext false
            storeTokenResponse(json)
            refreshEmail()
            true
        } catch (e: Exception) {
            Log.e(TAG, "exchangeCode failed", e)
            false
        }
    }

    /** Returns a valid access token, refreshing it if needed. Null when not signed in. */
    suspend fun validAccessToken(): String? = withContext(Dispatchers.IO) {
        val access = secureSettings.getString(K_ACCESS, "") ?: ""
        val expiresAt = secureSettings.getLong("google_expires_at_ms", 0L)
        if (access.isNotEmpty() && System.currentTimeMillis() < expiresAt - 60_000L) return@withContext access

        val refresh = secureSettings.getString(K_REFRESH, "") ?: ""
        if (refresh.isEmpty()) return@withContext null

        val body = formEncode(
            mapOf(
                "client_id" to Config.GOOGLE_CLIENT_ID,
                "refresh_token" to refresh,
                "grant_type" to "refresh_token"
            )
        )
        val json = postForm(TOKEN_ENDPOINT, body) ?: return@withContext null
        storeTokenResponse(json, keepRefresh = true)
        secureSettings.getString(K_ACCESS, "")
    }

    fun signedInEmail(): String? = secureSettings.getString(K_EMAIL, "")?.ifBlank { null }

    fun isSignedIn(): Boolean =
        !(secureSettings.getString(K_REFRESH, "") ?: "").isNullOrEmpty()

    fun signOut() {
        listOf(K_ACCESS, K_REFRESH, K_EXPIRES, K_EMAIL, K_VERIFIER, K_STATE).forEach {
            secureSettings.saveString(it, "")
        }
        secureSettings.saveLong("google_expires_at_ms", 0L)
    }

    // ------------------------------------------------------------------ helpers

    private suspend fun refreshEmail() {
        val token = validAccessToken() ?: return
        try {
            val conn = URL("https://www.googleapis.com/oauth2/v3/userinfo").openConnection() as HttpURLConnection
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            if (conn.responseCode == 200) {
                val json = JSONObject(conn.inputStream.bufferedReader().readText())
                secureSettings.saveString(K_EMAIL, json.optString("email", ""))
            }
            conn.disconnect()
        } catch (e: Exception) {
            Log.w(TAG, "refreshEmail failed: ${e.message}")
        }
    }

    private fun storeTokenResponse(json: JSONObject, keepRefresh: Boolean = false) {
        val access = json.optString("access_token", "")
        if (access.isNotEmpty()) secureSettings.saveString(K_ACCESS, access)
        val refresh = json.optString("refresh_token", "")
        if (refresh.isNotEmpty()) secureSettings.saveString(K_REFRESH, refresh)
        val expiresIn = json.optLong("expires_in", 3600L)
        secureSettings.saveLong("google_expires_at_ms", System.currentTimeMillis() + expiresIn * 1000L)
        if (keepRefresh) secureSettings.saveString(K_EXPIRES, "")
    }

    private fun postForm(url: String, body: String): JSONObject? {
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.connectTimeout = 20000
            conn.readTimeout = 20000
            OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(body) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.readText() ?: ""
            if (code !in 200..299) {
                Log.e(TAG, "token endpoint HTTP $code: $text")
                null
            } else JSONObject(text)
        } catch (e: Exception) {
            Log.e(TAG, "postForm failed", e)
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun formEncode(params: Map<String, String>): String =
        params.entries.joinToString("&") { (k, v) -> "$k=" + Uri.encode(v) }

    private fun randomUrlSafe(bytes: Int): String {
        val buf = ByteArray(bytes)
        SecureRandom().nextBytes(buf)
        return Base64.encodeToString(buf, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun sha256Base64Url(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }
}
