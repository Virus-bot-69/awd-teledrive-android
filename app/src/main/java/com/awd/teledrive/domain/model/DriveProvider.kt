package com.awd.teledrive.domain.model

/**
 * A storage backend that can be mounted inside the app's "Multi Drive" tab.
 */
enum class DriveProvider(
    val key: String,
    val displayName: String,
    val authMethod: AuthMethod,
    /** Short one-liner shown under the provider name. */
    val tagline: String,
    /** What is technically required to make this provider work. */
    val requirement: String
) {
    TELEGRAM(
        key = "telegram",
        displayName = "Telegram",
        authMethod = AuthMethod.PHONE_OTP,
        tagline = "Built-in · unlimited cloud storage",
        requirement = "Already integrated in this app (TDLib). Chats / channels are mounted as folders."
    ),
    ONEDRIVE(
        key = "onedrive",
        displayName = "OneDrive",
        authMethod = AuthMethod.OAUTH_MICROSOFT,
        tagline = "Microsoft account · sign in with email",
        requirement = "Microsoft Graph API + MSAL. Standard OAuth: the email/password is typed on Microsoft's own page, " +
            "the app only receives a token. Needs an Azure app registration (client id). Easiest of the new providers."
    ),
    GOOGLE_DRIVE(
        key = "gdrive",
        displayName = "Google Drive",
        authMethod = AuthMethod.OAUTH_GOOGLE,
        tagline = "Google account · full Drive browsing",
        requirement = "Google Drive API with the drive.readonly RESTRICTED scope. Works immediately for your own " +
            "test users, but publishing it to the public needs Google OAuth verification + a CASA security assessment. " +
            "Needs a Google Cloud project and an OAuth client id."
    ),
    GOOGLE_PHOTOS(
        key = "gphotos",
        displayName = "Google Photos",
        authMethod = AuthMethod.OAUTH_GOOGLE,
        tagline = "Google account · photo library",
        requirement = "Google locked this down: since 31 March 2025 the Photos Library API only returns media the app " +
            "itself uploaded (photoslibrary.readonly.appcreateddata). Browsing the user's whole library is no longer " +
            "possible for new third-party apps, so this tab can only ever show app-uploaded media."
    ),
    MEGA(
        key = "mega",
        displayName = "MEGA",
        authMethod = AuthMethod.EMAIL_PASSWORD,
        tagline = "MEGA account · email + password",
        requirement = "MEGA has no plain REST API: everything is end-to-end encrypted, so the client must do the crypto " +
            "itself (AES-CCM, RSA handshake, node key decryption). Options: the official MEGA SDK (C++ via JNI) or a " +
            "Kotlin/Java port. Biggest effort of the four, but technically possible."
    ),
    WHATSAPP(
        key = "whatsapp",
        displayName = "WhatsApp",
        authMethod = AuthMethod.NONE,
        tagline = "Not achievable with an official API",
        requirement = "There is NO official API that logs in with a phone number and lists someone's chats. " +
            "WhatsApp's Cloud API only serves your own Business number. The unofficial route (re-implementing the " +
            "multi-device companion protocol) violates WhatsApp's Terms of Service, needs a always-on server, and gets " +
            "the account banned — so it will not be built. Realistic alternative: import an exported chat " +
            "(Settings → Chats → Export chat) and/or let the user pick the WhatsApp media folder to back up."
    );

    enum class AuthMethod { PHONE_OTP, OAUTH_GOOGLE, OAUTH_MICROSOFT, EMAIL_PASSWORD, NONE }
}

/** Connection state of a provider inside the Multi Drive tab. */
enum class ProviderState { CONNECTED, NOT_CONNECTED, UNAVAILABLE }

data class ProviderStatus(
    val provider: DriveProvider,
    val state: ProviderState,
    /** e.g. the signed-in account, shown when CONNECTED. */
    val accountLabel: String? = null,
    val itemCount: Int? = null
)
