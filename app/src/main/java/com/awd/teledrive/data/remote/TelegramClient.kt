package com.awd.teledrive.data.remote

import android.util.Log
import com.awd.teledrive.data.account.AccountStore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the single TDLib client of the app.
 *
 * Multi-account support: every Telegram account has its own TDLib database folder, so
 * switching accounts means closing the current client and creating a new one pointed at
 * the other folder. [restart] does exactly that; the caller is responsible for refreshing
 * the UI afterwards.
 */
@Singleton
class TelegramClient @Inject constructor(
    private val accountStore: AccountStore
) {
    companion object {
        private const val TAG = "TelegramClient"
    }

    private var client: Client? = null

    /** Folder name (inside filesDir) TDLib is currently using. */
    @Volatile
    var databaseDirectoryName: String = accountStore.activeTelegramDbDir()
        private set

    private val _updates = MutableSharedFlow<TdApi.Object>(extraBufferCapacity = 500)
    val updates = _updates.asSharedFlow()

    val fileUpdates = updates.filterIsInstance<TdApi.UpdateFile>()

    init {
        createClient(accountStore.activeTelegramDbDir())
    }

    private fun createClient(dirName: String) {
        try {
            System.loadLibrary("tdjni")
            Log.d(TAG, "TDLib native library loaded, database dir = $dirName")
            databaseDirectoryName = dirName
            client = Client.create(
                { update -> _updates.tryEmit(update) },
                null,
                null,
            )
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load TDLib native library", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating TDLib client", e)
        }
    }

    /**
     * Closes the current client and opens the database of [dirName].
     * Call from a background thread: TDLib's close() blocks until it flushed its state.
     */
    @Synchronized
    fun restart(dirName: String = accountStore.activeTelegramDbDir()) {
        Log.d(TAG, "restart() -> $dirName")
        try {
            client?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing previous client", e)
        }
        client = null
        createClient(dirName)
    }

    val isReady: Boolean get() = client != null

    fun send(function: TdApi.Function<out TdApi.Object>, resultHandler: Client.ResultHandler = Client.ResultHandler { }) {
        val currentClient = client
        if (currentClient != null) {
            currentClient.send(function, resultHandler)
        } else {
            Log.e(TAG, "Cannot send request: client is null (initialization failed?)")
            resultHandler.onResult(TdApi.Error(500, "TDLib client not initialized"))
        }
    }
}
