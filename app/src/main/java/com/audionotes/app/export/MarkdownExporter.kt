package com.audionotes.app.export

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * Сохранение готового конспекта в папку «Загрузки» устройства.
 */
object MarkdownExporter {

    const val MIME_MARKDOWN = "text/markdown"

    /** Возвращает Uri сохранённого файла либо ошибку. */
    fun saveToDownloads(context: Context, fileName: String, content: String): Result<Uri> =
        runCatching {
            val safeName = sanitize(fileName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveViaMediaStore(context, safeName, content)
            } else {
                saveLegacy(context, safeName, content)
            }
        }

    private fun saveViaMediaStore(context: Context, fileName: String, content: String): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, MIME_MARKDOWN)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Не удалось создать файл в Загрузках")

        resolver.openOutputStream(uri)?.use { stream ->
            stream.write(content.toByteArray(Charsets.UTF_8))
        } ?: error("Не удалось открыть файл для записи")

        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }

    private fun saveLegacy(context: Context, fileName: String, content: String): Uri {
        @Suppress("DEPRECATION")
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        dir.mkdirs()
        val file = File(dir, fileName)
        file.writeText(content, Charsets.UTF_8)
        return Uri.fromFile(file)
    }

    private fun sanitize(name: String): String {
        val cleaned = name.trim()
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), "_")
            .take(80)
        val base = cleaned.ifEmpty { "конспект" }
        return if (base.endsWith(".md", ignoreCase = true)) base else "$base.md"
    }
}
