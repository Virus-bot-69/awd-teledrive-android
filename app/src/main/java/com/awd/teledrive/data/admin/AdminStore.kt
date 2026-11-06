package com.awd.teledrive.data.admin

import com.awd.teledrive.data.secure.SecureSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class AuditEntry(
    val timestamp: Long,
    val actor: String,
    val action: String
)

/**
 * Local admin/owner settings for this device.
 *
 * What this deliberately does NOT do: read another user's files behind their back.
 * It only controls what THIS device shows (enabled providers, notice banner), keeps a
 * visible audit trail and stores the user's explicit consent flag. Anything that needs
 * to reach other devices (remote config, notices pushed to users) has to go through a
 * backend you own (e.g. Firebase Remote Config) and must be visible in the UI.
 */
@Singleton
class AdminStore @Inject constructor(
    private val secureSettings: SecureSettings
) {
    companion object {
        private const val KEY_DISABLED = "admin_disabled_providers"
        private const val KEY_NOTICE = "admin_notice"
        private const val KEY_NOTICE_ON = "admin_notice_enabled"
        private const val KEY_AUDIT = "admin_audit_log"
        private const val KEY_CONSENT = "admin_consent_share"
        private const val MAX_AUDIT = 200
    }

    private val json = Json { ignoreUnknownKeys = true }

    private val _disabledProviders = MutableStateFlow(loadDisabled())
    val disabledProviders: StateFlow<Set<String>> = _disabledProviders.asStateFlow()

    private val _notice = MutableStateFlow(secureSettings.getString(KEY_NOTICE, "") ?: "")
    val notice: StateFlow<String> = _notice.asStateFlow()

    private val _noticeEnabled = MutableStateFlow(secureSettings.getBoolean(KEY_NOTICE_ON, false))
    val noticeEnabled: StateFlow<Boolean> = _noticeEnabled.asStateFlow()

    private val _consent = MutableStateFlow(secureSettings.getBoolean(KEY_CONSENT, false))
    val consentToSupport: StateFlow<Boolean> = _consent.asStateFlow()

    private val _audit = MutableStateFlow(loadAudit())
    val audit: StateFlow<List<AuditEntry>> = _audit.asStateFlow()

    private fun loadDisabled(): Set<String> {
        val raw = secureSettings.getString(KEY_DISABLED, "") ?: ""
        return if (raw.isBlank()) emptySet() else raw.split(',').filter { it.isNotBlank() }.toSet()
    }

    private fun loadAudit(): List<AuditEntry> {
        val raw = secureSettings.getString(KEY_AUDIT, "") ?: ""
        return if (raw.isBlank()) emptyList() else runCatching {
            json.decodeFromString<List<AuditEntry>>(raw)
        }.getOrDefault(emptyList())
    }

    fun isProviderEnabled(key: String): Boolean = key !in _disabledProviders.value

    fun setProviderEnabled(key: String, enabled: Boolean, actor: String = "admin") {
        val next = if (enabled) _disabledProviders.value - key else _disabledProviders.value + key
        _disabledProviders.value = next
        secureSettings.saveString(KEY_DISABLED, next.joinToString(","))
        log(actor, if (enabled) "Enabled provider: $key" else "Disabled provider: $key")
    }

    fun setNotice(text: String, enabled: Boolean, actor: String = "admin") {
        _notice.value = text
        _noticeEnabled.value = enabled
        secureSettings.saveString(KEY_NOTICE, text)
        secureSettings.saveBoolean(KEY_NOTICE_ON, enabled)
        log(actor, if (enabled) "Updated notice" else "Notice hidden")
    }

    fun setConsent(value: Boolean, actor: String = "user") {
        _consent.value = value
        secureSettings.saveBoolean(KEY_CONSENT, value)
        log(actor, if (value) "Consented to support access" else "Withdrew support access consent")
    }

    fun log(actor: String, action: String) {
        val entry = AuditEntry(System.currentTimeMillis(), actor, action)
        val next = (listOf(entry) + _audit.value).take(MAX_AUDIT)
        _audit.value = next
        secureSettings.saveString(KEY_AUDIT, json.encodeToString(next))
    }

    fun clearAudit() {
        _audit.value = emptyList()
        secureSettings.saveString(KEY_AUDIT, "")
    }

    fun formatted(entry: AuditEntry): String =
        SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(entry.timestamp))
}
