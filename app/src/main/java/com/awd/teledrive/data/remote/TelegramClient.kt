package com.awd.teledrive.data.remote

import android.util.Log
import com.awd.teledrive.data.account.AccountStore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the TDLib client(s) of the app.
 *
 * Multi-account support: the tdlibx wrapper (com.github.tdlibx:td) has no close() method,
 * so a client cannot be shut down once created. Instead we keep ONE CLIENT PER DATABASE
 * DIRECTORY and reuse it:
 *
 *  - switching to an account that was used before just re-points [current] to its old client
 *    (TDLib keeps that session alive, so switching back is instant),
 *  - a brand new account gets a new client with its own database directory,
 *  - every client is tagged with a generation number; updates from a client that is no
 *    longer the current one are dropped, so screens never show another account's data.
 */
@Singleton
class TelegramClient @Inject constructor(
    private val accountStore: AccountStore
) {
    companion object {
        private const val TAG = "TelegramClient"
        private const val LEGACY_DIR = "tdlib"
    }

    private val clients = HashMap<String, Client>()
    private val generation = AtomicInteger(0)

    @Volatile
    private var current: Client? = null

    /** Folder name (inside filesDir) the active client is using. */
    @Volatile
    var databaseDirectoryName: String = runCatching { accountStore.activeTelegramDbDir() }
        .getOrDefault(LEGACY_DIR)
        private set

    private val _updates = MutableSharedFlow<TdApi.Object>(extraBufferCapacity = 500)
    val updates = _updates.asSharedFlow()

    val fileUpdates = updates.filterIsInstance<TdApi.UpdateFile>()

    init {
        activate(databaseDirectoryName)
    }

    @Synchronized
    private fun activate(dirName: String) {
        databaseDirectoryName = dirName
        val gen = generation.incrementAndGet()
        val existing = clients[dirName]
        if (existing != null) {
            Log.d(TAG, "activate(): reusing existing client for $dirName")
            current = existing
            return
        }
        try {
            System.loadLibrary("tdjni")
            Log.d(TAG, "activate(): creating client for $dirName")
            val client = Client.create(
                { update -> if (gen == generation.get()) _updates.tryEmit(update) },
                null,
                null,
            )
            clients[dirName] = client
            current = client
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load TDLib native library", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating TDLib client", e)
        }
    }

    /**
     * Switches the active TDLib client to [dirName] (default: the active account's folder).
     * Safe to call from a background thread.
     */
    fun restart(dirName: String = accountStore.activeTelegramDbDir()) {
        Log.d(TAG, "restart() -> $dirName")
        activate(dirName)
    }

    val isReady: Boolean get() = current != null

    fun send(function: TdApi.Function<out TdApi.Object>, resultHandler: Client.ResultHandler = Client.ResultHandler { }) {
        val client = current
        if (client != null) {
            client.send(function, resultHandler)
        } else {
            Log.e(TAG, "Cannot send request: client is null (initialization failed?)")
            resultHandler.onResult(TdApi.Error(500, "TDLib client not initialized"))
        }
    }
}
