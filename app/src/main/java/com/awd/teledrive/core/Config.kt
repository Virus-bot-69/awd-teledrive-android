package com.awd.teledrive.core

/**
 * App-wide default configuration.
 *
 * Telegram credentials below are the ones registered for this private build
 * (my.telegram.org -> API development tools). Because they are compiled into
 * the APK they are not secret: anyone who decompiles the app can read them.
 * Keep the registration dedicated to this app.
 *
 * Users can still override them in Settings -> Telegram API -> "Edit API Credentials".
 */
object Config {
    const val API_ID: Int = 36619703
    const val API_HASH: String = "febf26de7c55c24412a8d7be390a759c"

    /** True when a usable default credential pair is compiled in. */
    val isApiConfigured: Boolean
        get() = API_ID != 0 && API_HASH.isNotBlank()

    // ---------------------------------------------------------------- Google
    /**
     * OAuth client id for Google Drive / Google Photos.
     *
     * How to get it (5 minutes, free):
     *  1. console.cloud.google.com -> create a project
     *  2. APIs & Services -> Library -> enable "Google Drive API" (and "Photos Library API")
     *  3. APIs & Services -> OAuth consent screen -> External -> add yourself as a test user
     *  4. APIs & Services -> Credentials -> Create credentials -> OAuth client ID
     *     -> Application type: **Android**
     *     -> Package name: com.awd.teledrive
     *     -> SHA-1: the signing certificate fingerprint (see README-HOW-TO-BUILD.md)
     *  5. Paste the generated "<prefix>.apps.googleusercontent.com" below and rebuild.
     */
    const val GOOGLE_CLIENT_ID: String = ""

    /** True when a Google OAuth client id has been compiled in. */
    val isGoogleConfigured: Boolean
        get() = GOOGLE_CLIENT_ID.isNotBlank()

    /**
     * Redirect scheme for the OAuth flow. For a Google *Android* client this must be
     * "com.googleusercontent.apps.<the numeric prefix of the client id>".
     * Empty client id -> a neutral fallback so the manifest still builds.
     */
    val googleRedirectScheme: String
        get() = if (isGoogleConfigured) "com.googleusercontent.apps." + GOOGLE_CLIENT_ID.substringBefore(".apps.googleusercontent.com")
        else "com.awd.teledrive.oauth"

    const val GOOGLE_REDIRECT_PATH: String = "/oauth2redirect"
    const val GOOGLE_DRIVE_SCOPE: String = "https://www.googleapis.com/auth/drive.readonly"
    const val GOOGLE_EMAIL_SCOPE: String = "openid email profile"

    // ----------------------------------------------------------------- Admin
    /**
     * Local admin panel PIN. Change it before publishing. The admin panel can only
     * manage *this device's* settings (providers, notice, audit log) — it never
     * has access to other users' files.
     */
    const val ADMIN_PIN: String = "246810"
}
