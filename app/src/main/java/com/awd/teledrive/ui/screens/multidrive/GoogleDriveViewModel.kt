package com.awd.teledrive.ui.screens.multidrive

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.awd.teledrive.core.Config
import com.awd.teledrive.core.FileSharingHelper
import com.awd.teledrive.data.account.AccountStore
import com.awd.teledrive.data.google.GoogleDriveApi
import com.awd.teledrive.data.google.GoogleDriveItem
import com.awd.teledrive.data.google.GoogleOAuth
import com.awd.teledrive.data.google.GoogleQuota
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class GoogleDriveViewModel @Inject constructor(
    private val api: GoogleDriveApi,
    private val oauth: GoogleOAuth,
    private val accountStore: AccountStore,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = false,
        val items: List<GoogleDriveItem> = emptyList(),
        val stack: List<Pair<String, String>> = listOf("root" to "My Drive"),
        val quota: GoogleQuota? = null,
        val email: String? = null,
        val signedIn: Boolean = false,
        val isConfigured: Boolean = Config.isGoogleConfigured,
        val error: String? = null,
        val message: String? = null
    ) {
        val currentFolderId: String get() = stack.lastOrNull()?.first ?: "root"
        val currentFolderName: String get() = stack.lastOrNull()?.second ?: "My Drive"
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val signedIn = oauth.isSignedIn()
            _state.value = _state.value.copy(
                isLoading = true,
                signedIn = signedIn,
                email = oauth.signedInEmail(),
                error = null
            )
            if (!signedIn) {
                _state.value = _state.value.copy(isLoading = false)
                return@launch
            }
            val folderId = _state.value.currentFolderId
            val items = api.listChildren(folderId)
            val quota = api.quota()
            _state.value = _state.value.copy(
                isLoading = false,
                items = items,
                quota = quota,
                email = oauth.signedInEmail(),
                error = if (items.isEmpty() && quota == null) "Could not load Google Drive. Check your internet connection." else null
            )
        }
    }

    /** Returns the browser URL for the consent screen, or null when no client id is configured. */
    fun startSignIn(): String? = oauth.buildAuthorizationUrl()

    fun signOut() {
        oauth.signOut()
        _state.value = UiState(isConfigured = Config.isGoogleConfigured)
    }

    fun openFolder(item: GoogleDriveItem) {
        _state.value = _state.value.copy(stack = _state.value.stack + (item.id to item.name))
        refresh()
    }

    /** @return false when already at the root (caller should then finish the screen). */
    fun goUp(): Boolean {
        val stack = _state.value.stack
        if (stack.size <= 1) return false
        _state.value = _state.value.copy(stack = stack.dropLast(1))
        refresh()
        return true
    }

    fun openFile(item: GoogleDriveItem) {
        viewModelScope.launch {
            _state.value = _state.value.copy(message = "Downloading ${item.name}…")
            val dir = withContext(Dispatchers.IO) {
                File(context.cacheDir, "google_drive").apply { mkdirs() }
            }
            val file = api.download(item, dir)
            if (file == null) {
                _state.value = _state.value.copy(message = null, error = "Download failed")
                return@launch
            }
            _state.value = _state.value.copy(message = null)
            withContext(Dispatchers.Main) {
                FileSharingHelper.openFileExternally(context, file.absolutePath, item.mimeType)
            }
        }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null, error = null)
    }

    fun addToAccounts() {
        val email = oauth.signedInEmail() ?: return
        if (accountStore.googleAccounts().none { it.detail == email }) {
            accountStore.addGoogleAccount(email)
        }
    }
}
