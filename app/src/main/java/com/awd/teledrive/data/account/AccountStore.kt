package com.awd.teledrive.data.account

import com.awd.teledrive.data.secure.SecureSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class AccountEntry(
    val id: String,
    /** "TELEGRAM" or "GOOGLE" */
    val type: String,
    val label: String,
    /** phone number for Telegram, email for Google */
    val detail: String = "",
    /**
     * TDLib working directory name for Telegram accounts. The very first account keeps
     * the legacy "tdlib" folder so existing installs keep their session after the update.
     */
    val dbDir: String = "",
    val createdAt: Long = 0L
) {
    val isTelegram: Boolean get() = type == TYPE_TELEGRAM
    val isGoogle: Boolean get() = type == TYPE_GOOGLE

    companion object {
        const val TYPE_TELEGRAM = "TELEGRAM"
        const val TYPE_GOOGLE = "GOOGLE"
    }
}

/**
 * Stores every signed-in account (Telegram and Google) plus which one is active.
 *
 * Switching the *active* account is what the UI reacts to: Telegram reads
 * [activeTelegramDbDir] when it (re)creates its TDLib client, and Google reads the
 * token bucket that belongs to the active Google account.
 */
@Singleton
class AccountStore @Inject constructor(
    private val secureSettings: SecureSettings
) {
    companion object {
        private const val KEY_LIST = "accounts_json"
        private const val KEY_ACTIVE_TG = "active_telegram_account"
        private const val KEY_ACTIVE_GOOGLE = "active_google_account"
        private const val LEGACY_TDLIB_DIR = "tdlib"
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _accounts = MutableStateFlow<List<AccountEntry>>(emptyList())
    val accounts: StateFlow<List<AccountEntry>> = _accounts.asStateFlow()

    private val _activeTelegramId = MutableStateFlow<String?>(null)
    val activeTelegramId: StateFlow<String?> = _activeTelegramId.asStateFlow()

    private val _activeGoogleId = MutableStateFlow<String?>(null)
    val activeGoogleId: StateFlow<String?> = _activeGoogleId.asStateFlow()

    init {
        load()
        // A fresh install always starts with the legacy Telegram slot so that updates
        // over an existing session never lose data.
        if (_accounts.value.none { it.isTelegram }) {
            val first = AccountEntry(
                id = UUID.randomUUID().toString(),
                type = AccountEntry.TYPE_TELEGRAM,
                label = "Telegram",
                detail = "",
                dbDir = LEGACY_TDLIB_DIR,
                createdAt = System.currentTimeMillis()
            )
            _accounts.value = _accounts.value + first
            _activeTelegramId.value = first.id
            persist()
        } else if (_activeTelegramId.value == null) {
            _activeTelegramId.value = _accounts.value.first { it.isTelegram }.id
            persistActive()
        }
    }

    private fun load() {
        val raw = secureSettings.getString(KEY_LIST, "") ?: ""
        _accounts.value = if (raw.isBlank()) emptyList() else runCatching {
            json.decodeFromString<List<AccountEntry>>(raw)
        }.getOrDefault(emptyList())
        _activeTelegramId.value = secureSettings.getString(KEY_ACTIVE_TG, "")?.ifBlank { null }
        _activeGoogleId.value = secureSettings.getString(KEY_ACTIVE_GOOGLE, "")?.ifBlank { null }
    }

    private fun persist() {
        secureSettings.saveString(KEY_LIST, json.encodeToString(_accounts.value))
        persistActive()
    }

    private fun persistActive() {
        secureSettings.saveString(KEY_ACTIVE_TG, _activeTelegramId.value ?: "")
        secureSettings.saveString(KEY_ACTIVE_GOOGLE, _activeGoogleId.value ?: "")
    }

    fun telegramAccounts(): List<AccountEntry> = _accounts.value.filter { it.isTelegram }
    fun googleAccounts(): List<AccountEntry> = _accounts.value.filter { it.isGoogle }

    fun activeTelegramAccount(): AccountEntry? =
        telegramAccounts().firstOrNull { it.id == _activeTelegramId.value } ?: telegramAccounts().firstOrNull()

    fun activeGoogleAccount(): AccountEntry? =
        googleAccounts().firstOrNull { it.id == _activeGoogleId.value }

    /** Name of the TDLib database directory for the currently active Telegram account. */
    fun activeTelegramDbDir(): String = activeTelegramAccount()?.dbDir?.ifBlank { LEGACY_TDLIB_DIR } ?: LEGACY_TDLIB_DIR

    /** Adds a Telegram slot. The first one uses the legacy folder, later ones get their own. */
    fun addTelegramAccount(label: String = "", detail: String = ""): AccountEntry {
        val index = telegramAccounts().size
        val entry = AccountEntry(
            id = UUID.randomUUID().toString(),
            type = AccountEntry.TYPE_TELEGRAM,
            label = label.ifBlank { "Account ${index + 1}" },
            detail = detail,
            dbDir = if (index == 0) LEGACY_TDLIB_DIR else "tdlib_$index",
            createdAt = System.currentTimeMillis()
        )
        _accounts.value = _accounts.value + entry
        _activeTelegramId.value = entry.id
        persist()
        return entry
    }

    fun addGoogleAccount(email: String): AccountEntry {
        val entry = AccountEntry(
            id = UUID.randomUUID().toString(),
            type = AccountEntry.TYPE_GOOGLE,
            label = email.ifBlank { "Google account" },
            detail = email,
            createdAt = System.currentTimeMillis()
        )
        _accounts.value = _accounts.value + entry
        _activeGoogleId.value = entry.id
        persist()
        return entry
    }

    fun setActive(entry: AccountEntry) {
        if (entry.isTelegram) _activeTelegramId.value = entry.id else _activeGoogleId.value = entry.id
        persistActive()
    }

    fun remove(entry: AccountEntry): Boolean {
        // Never let the user delete the last Telegram account: TDLib always needs one slot.
        if (entry.isTelegram && telegramAccounts().size <= 1) return false
        _accounts.value = _accounts.value.filterNot { it.id == entry.id }
        if (_activeTelegramId.value == entry.id) {
            _activeTelegramId.value = telegramAccounts().firstOrNull()?.id
        }
        if (_activeGoogleId.value == entry.id) {
            _activeGoogleId.value = googleAccounts().firstOrNull()?.id
        }
        persist()
        return true
    }
}
