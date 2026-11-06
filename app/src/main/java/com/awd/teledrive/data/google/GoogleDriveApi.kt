package com.awd.teledrive.data.google

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/** One entry of a Google Drive folder listing. */
data class GoogleDriveItem(
    val id: String,
    val name: String,
    val isFolder: Boolean,
    val mimeType: String,
    val sizeBytes: Long,
    val modifiedTime: String?,
    val thumbnailLink: String?
)

data class GoogleQuota(val usage: Long, val limit: Long) {
    val limitIsUnknown: Boolean get() = limit <= 0
}

/**
 * Thin hand-written Google Drive v3 client (REST + JSON).
 * Only what the app needs: browse, quota, download, upload.
 */
@Singleton
class GoogleDriveApi @Inject constructor(
    private val oauth: GoogleOAuth
) {
    companion object {
        private const val TAG = "GoogleDriveApi"
        private const val BASE = "https://www.googleapis.com/drive/v3"
        private const val FOLDER_MIME = "application/vnd.google-apps.folder"
        // Google Docs editors' files have no binary content; they must be exported instead.
        private val EXPORT_MAP = mapOf(
            "application/vnd.google-apps.document" to "application/pdf",
            "application/vnd.google-apps.spreadsheet" to "application/pdf",
            "application/vnd.google-apps.presentation" to "application/pdf"
        )
    }

    /** Root folder id is the literal string "root". */
    suspend fun listChildren(parentId: String = "root"): List<GoogleDriveItem> = withContext(Dispatchers.IO) {
        val token = oauth.validAccessToken() ?: return@withContext emptyList()
        val q = URLEncoder.encode("'$parentId' in parents and trashed = false", "UTF-8")
        val fields = URLEncoder.encode("files(id,name,mimeType,size,modifiedTime,thumbnailLink),nextPageToken", "UTF-8")
        val url = "$BASE/files?q=$q&fields=$fields&pageSize=200&orderBy=folder,name_natural&includeItemsFromAllDrives=true&supportsAllDrives=true"
        val json = get(token, url) ?: return@withContext emptyList()
        val arr = json.optJSONArray("files") ?: return@withContext emptyList()
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val mime = o.optString("mimeType", "")
                add(
                    GoogleDriveItem(
                        id = o.optString("id"),
                        name = o.optString("name", "Untitled"),
                        isFolder = mime == FOLDER_MIME,
                        mimeType = mime,
                        sizeBytes = o.optString("size", "0").toLongOrNull() ?: 0L,
                        modifiedTime = o.optString("modifiedTime", null),
                        thumbnailLink = o.optString("thumbnailLink", null)
                    )
                )
            }
        }
    }

    suspend fun quota(): GoogleQuota? = withContext(Dispatchers.IO) {
        val token = oauth.validAccessToken() ?: return@withContext null
        val json = get(token, "$BASE/about?fields=storageQuota") ?: return@withContext null
        val q = json.optJSONObject("storageQuota") ?: return@withContext null
        GoogleQuota(
            usage = q.optString("usage", "0").toLongOrNull() ?: 0L,
            limit = q.optString("limit", "0").toLongOrNull() ?: 0L
        )
    }

    /**
     * Downloads a file into [destDir]. Google Docs/Sheets/Slides are exported as PDF.
     * Returns the created file, or null on failure.
     */
    suspend fun download(item: GoogleDriveItem, destDir: File): File? = withContext(Dispatchers.IO) {
        val token = oauth.validAccessToken() ?: return@withContext null
        val exportMime = EXPORT_MAP[item.mimeType]
        val url = if (exportMime != null) {
            "$BASE/files/${item.id}/export?mimeType=" + URLEncoder.encode(exportMime, "UTF-8")
        } else {
            "$BASE/files/${item.id}?alt=media"
        }
        var name = item.name
        if (exportMime != null && !name.endsWith(".pdf", true)) name += ".pdf"
        val out = File(destDir, name.replace(Regex("[/\\\\:*?\"<>|]"), "_"))
        try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.connectTimeout = 20000
            conn.readTimeout = 60000
            conn.instanceFollowRedirects = true
            if (conn.responseCode !in 200..299) {
                Log.e(TAG, "download HTTP ${conn.responseCode} for ${item.name}")
                conn.disconnect()
                return@withContext null
            }
            conn.inputStream.use { input -> out.outputStream().use { input.copyTo(it) } }
            conn.disconnect()
            out
        } catch (e: Exception) {
            Log.e(TAG, "download failed", e)
            if (out.exists()) out.delete()
            null
        }
    }

    /** Uploads a local file into the given parent folder. Multipart, simple upload. */
    suspend fun upload(local: File, parentId: String = "root", mimeType: String = "application/octet-stream"): GoogleDriveItem? =
        withContext(Dispatchers.IO) {
            val token = oauth.validAccessToken() ?: return@withContext null
            val boundary = "tdrive" + System.currentTimeMillis()
            val metadata = JSONObject().apply {
                put("name", local.name)
                put("parents", org.json.JSONArray().put(parentId))
            }.toString()

            val conn = URL("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&supportsAllDrives=true")
                .openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Authorization", "Bearer $token")
                conn.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
                conn.connectTimeout = 20000
                conn.readTimeout = 120000
                conn.outputStream.buffered().use { out ->
                    fun write(s: String) = out.write(s.toByteArray())
                    write("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metadata\r\n")
                    write("--$boundary\r\nContent-Type: $mimeType\r\n\r\n")
                    local.inputStream().use { it.copyTo(out) }
                    write("\r\n--$boundary--\r\n")
                }
                val code = conn.responseCode
                val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.readText() ?: ""
                if (code !in 200..299) {
                    Log.e(TAG, "upload HTTP $code: $text")
                    return@withContext null
                }
                val o = JSONObject(text)
                GoogleDriveItem(
                    id = o.optString("id"),
                    name = o.optString("name", local.name),
                    isFolder = false,
                    mimeType = mimeType,
                    sizeBytes = local.length(),
                    modifiedTime = null,
                    thumbnailLink = null
                )
            } catch (e: Exception) {
                Log.e(TAG, "upload failed", e)
                null
            } finally {
                conn.disconnect()
            }
        }

    suspend fun createFolder(name: String, parentId: String = "root"): GoogleDriveItem? = withContext(Dispatchers.IO) {
        val token = oauth.validAccessToken() ?: return@withContext null
        val conn = URL("$BASE/files?supportsAllDrives=true").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Content-Type", "application/json")
            val body = JSONObject().apply {
                put("name", name)
                put("mimeType", FOLDER_MIME)
                put("parents", org.json.JSONArray().put(parentId))
            }.toString()
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.readText() ?: ""
            if (code !in 200..299) {
                Log.e(TAG, "createFolder HTTP $code: $text")
                return@withContext null
            }
            val o = JSONObject(text)
            GoogleDriveItem(o.optString("id"), name, true, FOLDER_MIME, 0L, null, null)
        } catch (e: Exception) {
            Log.e(TAG, "createFolder failed", e)
            null
        } finally {
            conn.disconnect()
        }
    }

    suspend fun delete(itemId: String): Boolean = withContext(Dispatchers.IO) {
        val token = oauth.validAccessToken() ?: return@withContext false
        try {
            val conn = URL("$BASE/files/$itemId?supportsAllDrives=true").openConnection() as HttpURLConnection
            conn.requestMethod = "DELETE"
            conn.setRequestProperty("Authorization", "Bearer $token")
            val ok = conn.responseCode in 200..299
            conn.disconnect()
            ok
        } catch (e: Exception) {
            Log.e(TAG, "delete failed", e)
            false
        }
    }

    private fun get(token: String, url: String): JSONObject? {
        var conn: HttpURLConnection? = null
        return try {
            conn = URL(url).openConnection() as HttpURLConnection
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.connectTimeout = 20000
            conn.readTimeout = 30000
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.readText() ?: ""
            if (code !in 200..299) {
                Log.e(TAG, "GET $url -> HTTP $code: $text")
                null
            } else JSONObject(text)
        } catch (e: Exception) {
            Log.e(TAG, "GET failed: ${e.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }
}
