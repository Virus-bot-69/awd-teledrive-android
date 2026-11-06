package com.awd.teledrive.ui.screens.admin

import androidx.lifecycle.ViewModel
import com.awd.teledrive.data.admin.AdminStore
import com.awd.teledrive.data.admin.AuditEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AdminViewModel @Inject constructor(
    private val adminStore: AdminStore
) : ViewModel() {

    val disabledProviders = adminStore.disabledProviders
    val notice = adminStore.notice
    val noticeEnabled = adminStore.noticeEnabled
    val consent = adminStore.consentToSupport
    val audit = adminStore.audit

    fun logAdminLogin() = adminStore.log("admin", "Admin panel unlocked")

    fun setProviderEnabled(key: String, enabled: Boolean) =
        adminStore.setProviderEnabled(key, enabled)

    fun setNotice(text: String, enabled: Boolean) =
        adminStore.setNotice(text, enabled)

    fun setConsent(value: Boolean) = adminStore.setConsent(value)

    fun clearAudit() = adminStore.clearAudit()

    fun format(entry: AuditEntry): String = adminStore.formatted(entry)
}
