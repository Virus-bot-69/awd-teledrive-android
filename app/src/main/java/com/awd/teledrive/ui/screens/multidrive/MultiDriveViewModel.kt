package com.awd.teledrive.ui.screens.multidrive

import androidx.lifecycle.ViewModel
import com.awd.teledrive.core.Config
import com.awd.teledrive.data.account.AccountStore
import com.awd.teledrive.data.admin.AdminStore
import com.awd.teledrive.data.google.GoogleOAuth
import com.awd.teledrive.domain.model.DriveProvider
import com.awd.teledrive.domain.model.ProviderState
import com.awd.teledrive.domain.model.ProviderStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class MultiDriveViewModel @Inject constructor(
    private val adminStore: AdminStore,
    private val accountStore: AccountStore,
    private val googleOAuth: GoogleOAuth
) : ViewModel() {

    data class UiState(
        val providers: List<ProviderStatus> = emptyList(),
        val notice: String? = null,
        val telegramAccounts: Int = 1,
        val activeTelegramLabel: String = "Account 1"
    )

    private val _state = MutableStateFlow(build())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun refresh() {
        _state.value = build()
    }

    private fun build(): UiState {
        val disabled = adminStore.disabledProviders.value
        val googleEmail = googleOAuth.signedInEmail()

        val list = buildList {
            if (DriveProvider.TELEGRAM.key !in disabled) {
                add(
                    ProviderStatus(
                        DriveProvider.TELEGRAM,
                        ProviderState.CONNECTED,
                        accountStore.activeTelegramAccount()?.label ?: "Telegram"
                    )
                )
            }
            if (DriveProvider.GOOGLE_DRIVE.key !in disabled) {
                add(
                    ProviderStatus(
                        DriveProvider.GOOGLE_DRIVE,
                        when {
                            !Config.isGoogleConfigured -> ProviderState.UNAVAILABLE
                            googleOAuth.isSignedIn() -> ProviderState.CONNECTED
                            else -> ProviderState.NOT_CONNECTED
                        },
                        googleEmail
                    )
                )
            }
            if (DriveProvider.GOOGLE_PHOTOS.key !in disabled) {
                add(ProviderStatus(DriveProvider.GOOGLE_PHOTOS, ProviderState.UNAVAILABLE))
            }
            if (DriveProvider.ONEDRIVE.key !in disabled) {
                add(ProviderStatus(DriveProvider.ONEDRIVE, ProviderState.NOT_CONNECTED))
            }
            if (DriveProvider.MEGA.key !in disabled) {
                add(ProviderStatus(DriveProvider.MEGA, ProviderState.NOT_CONNECTED))
            }
            if (DriveProvider.WHATSAPP.key !in disabled) {
                add(ProviderStatus(DriveProvider.WHATSAPP, ProviderState.UNAVAILABLE))
            }
        }

        val tg = accountStore.telegramAccounts()
        return UiState(
            providers = list,
            notice = if (adminStore.noticeEnabled.value && adminStore.notice.value.isNotBlank())
                adminStore.notice.value else null,
            telegramAccounts = tg.size,
            activeTelegramLabel = accountStore.activeTelegramAccount()?.label ?: "Account 1"
        )
    }
}
