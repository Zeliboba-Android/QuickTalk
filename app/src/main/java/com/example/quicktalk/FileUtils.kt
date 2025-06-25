package com.example.quicktalk

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.*

object FileUtils {
    fun getFile(context: Context, uri: Uri): File {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Не удалось открыть файл")

        val fileName = getFileName(context, uri) ?: "file_${System.currentTimeMillis()}"
        val file = File(context.cacheDir, fileName)

        try {
            FileOutputStream(file).use { output ->
                val buffer = ByteArray(1024 * 1024) // 1MB буфер
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                }
                output.flush()
            }
        } finally {
            inputStream.close()
        }

        return file
    }

    fun getFileName(context: Context, uri: Uri): String? {
        var name: String? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) name = cursor.getString(nameIndex)
            }
        }
        return name ?: uri.path?.substringAfterLast('/')
    }
}