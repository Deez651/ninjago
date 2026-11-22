package com.example.data.download

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import androidx.core.content.FileProvider
import com.example.data.model.DownloadedRecord
import com.example.data.model.PostItem
import com.example.data.model.PostPayload
import com.example.data.security.SecurityUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object FileDownloadHelper {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun sanitizeFileName(raw: String, fallback: String = "ninjago_file.bin"): String {
        val cleaned = raw.trim()
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .take(120)
        return cleaned.ifBlank { fallback }
    }

    suspend fun downloadPostFile(
        context: Context,
        post: PostItem,
        payload: PostPayload,
        chunks: List<String>,
        onProgress: (Int) -> Unit
    ): Result<DownloadedRecord> = withContext(Dispatchers.IO) {
        runCatching {
            val safeName = sanitizeFileName(post.fileName.ifBlank { "${post.title}.txt" })
            val mimeType = post.mimeType.ifBlank { "application/octet-stream" }

            val bytes: ByteArray = when {
                !payload.embeddedBase64.isNullOrBlank() -> {
                    onProgress(40)
                    Base64.decode(payload.embeddedBase64, Base64.DEFAULT)
                }
                chunks.isNotEmpty() -> {
                    onProgress(35)
                    val joined = chunks.joinToString("")
                    Base64.decode(joined, Base64.DEFAULT)
                }
                !payload.fileUrl.isNullOrBlank() -> {
                    val url = payload.fileUrl.trim()
                    require(SecurityUtils.isSafeHttpsUrl(url)) {
                        "Небезопасная или недопустимая ссылка для скачивания (требуется HTTPS)."
                    }
                    downloadUrlToBytes(url, onProgress)
                }
                else -> error("К публикации не прикреплён файл или ссылка.")
            }

            onProgress(85)
            val appDownloadsDir = File(context.filesDir, "downloads").apply { mkdirs() }
            val localFile = File(appDownloadsDir, "${post.id.take(12)}_$safeName")
            FileOutputStream(localFile).use { it.write(bytes) }

            val publicUri = saveToPublicDownloads(context, safeName, mimeType, bytes)
            val providerUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                localFile
            )
            onProgress(100)

            DownloadedRecord(
                postId = post.id,
                title = post.title,
                fileName = safeName,
                localPath = localFile.absolutePath,
                contentUri = (publicUri ?: providerUri).toString(),
                fileSizeBytes = bytes.size.toLong(),
                mimeType = mimeType,
                downloadedAtMillis = System.currentTimeMillis()
            )
        }
    }

    private fun downloadUrlToBytes(url: String, onProgress: (Int) -> Unit): ByteArray {
        val request = Request.Builder().url(url).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Ошибка загрузки файла: HTTP ${response.code}")
            }
            val body = response.body ?: error("Пустой ответ сервера")
            val totalBytes = body.contentLength()
            val maxBytes = 25L * 1024L * 1024L
            if (totalBytes > maxBytes) {
                error("Файл превышает допустимый размер встроенного загрузчика (25 МБ).")
            }
            val buffer = ByteArray(8192)
            val out = java.io.ByteArrayOutputStream()
            var downloaded = 0L
            body.byteStream().use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    downloaded += read
                    if (downloaded > maxBytes) {
                        error("Размер файла превысил 25 МБ.")
                    }
                    out.write(buffer, 0, read)
                    if (totalBytes > 0) {
                        val pct = ((downloaded * 80L) / totalBytes).toInt().coerceIn(5, 80)
                        onProgress(pct)
                    }
                }
            }
            return out.toByteArray()
        }
    }

    private fun saveToPublicDownloads(
        context: Context,
        fileName: String,
        mimeType: String,
        bytes: ByteArray
    ): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/NinjagoHub")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        }.getOrNull()
    }

    fun buildOpenFileIntent(context: Context, record: DownloadedRecord): Intent? {
        val localFile = File(record.localPath)
        val uri: Uri = if (localFile.exists()) {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", localFile)
        } else if (!record.contentUri.isNullOrBlank()) {
            Uri.parse(record.contentUri)
        } else {
            return null
        }

        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, record.mimeType.ifBlank { "*/*" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun buildShareFileIntent(context: Context, record: DownloadedRecord): Intent? {
        val localFile = File(record.localPath)
        val uri: Uri = if (localFile.exists()) {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", localFile)
        } else if (!record.contentUri.isNullOrBlank()) {
            Uri.parse(record.contentUri)
        } else {
            return null
        }

        return Intent(Intent.ACTION_SEND).apply {
            type = record.mimeType.ifBlank { "*/*" }
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, record.fileName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0L) return "0 Б"
        return when {
            bytes < 1024L -> "$bytes Б"
            bytes < 1024L * 1024L -> String.format("%.1f КБ", bytes / 1024.0)
            else -> String.format("%.2f МБ", bytes / (1024.0 * 1024.0))
        }
    }
}
