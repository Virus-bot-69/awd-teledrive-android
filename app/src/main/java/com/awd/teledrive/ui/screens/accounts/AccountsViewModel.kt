package com.awd.teledrive.ui.screens.accounts

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.awd.teledrive.MainActivity
import com.awd.teledrive.data.account.AccountEntry
import com.awd.teledrive.data.account.AccountStore
import com.awd.teledrive.data.google.GoogleOAuth
import com.awd.teledrive.data.remote.TelegramClient
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val accountStore: AccountStore,
    private val telegramClient: TelegramClient,
    private val googleOAuth: GoogleOAuth,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    val accounts = accountStore.accounts
    val activeTelegramId = accountStore.activeTelegramId
    val activeGoogleId = accountStore.activeGoogleId

    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    fun clearMessage() { _message.value = null }

    fun googleSignedInEmail(): String? = googleOAuth.signedInEmail()

    fun googleSignInUrl(): String? = googleOAuth.buildAuthorizationUrl()

    /**
     * Switches the active Telegram account: TDLib is restarted against that account's
     * database folder and the app is relaunched so every ViewModel reloads cleanly.
     */
    fun switchAccount(entry: AccountEntry, onDone: () -> Unit = {}) {
        if (_busy.value) return
        _busy.value = true
        accountStore.setActive(entry)
        viewModelScope.launch {
            withContext(Dispatchers.IO) { telegramClient.restart() }
            _busy.value = false
            relaunchApp()
            onDone()
        }
    }

    /** Adds an empty Telegram slot and makes it active, then restarts the app into the login flow. */
    fun addTelegramAccount(label: String) {
        if (_busy.value) return
        _busy.value = true
        accountStore.addTelegramAccount(label = label)
        viewModelScope.launch {
            withContext(Dispatchers.IO) { telegramClient.restart() }
            _busy.value = false
            relaunchApp()
        }
    }

    fun removeAccount(entry: AccountEntry) {
        val wasActive = (entry.isTelegram && accountStore.activeTelegramId.value == entry.id) ||
            (entry.isGoogle && accountStore.activeGoogleId.value == entry.id)
        val removed = accountStore.remove(entry)
        if (!removed) {
            _message.value = "You need at least one Telegram account."
            return
        }
        if (entry.isGoogle) googleOAuth.signOut()
        if (entry.isTelegram && entry.dbDir.isNotBlank()) {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { File(context.filesDir, entry.dbDir).deleteRecursively() }
            }
        }
        if (wasActive && entry.isTelegram) {
            viewModelScope.launch {
                withContext(Dispatchers.IO) { telegramClient.restart() }
                relaunchApp()
            }
        }
    }

    fun googleSignOut() {
        googleOAuth.signOut()
        accountStore.googleAccounts().forEach { accountStore.remove(it) }
        _message.value = "Signed out of Google"
    }

    fun labelFor(entry: AccountEntry): String {
        if (entry.isTelegram) {
            val index = accountStore.telegramAccounts().indexOfFirst { it.id == entry.id }
            return if (entry.label.isNotBlank()) entry.label else "Telegram account ${index + 1}"
        }
        return entry.detail.ifBlank { entry.label }
    }

    private val relaunching = AtomicBoolean(false)

    private fun relaunchApp() {
        if (!relaunching.compareAndSet(false, true)) return
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        context.startActivity(intent)
        (context as? Activity)?.finish()
    }
}
