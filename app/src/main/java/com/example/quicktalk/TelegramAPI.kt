package com.example.quicktalk

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface TelegramApiService {
    @Multipart
    @POST("sendDocument")
    suspend fun uploadDocument(
        @Part("chat_id") chatId: RequestBody,
        @Part document: MultipartBody.Part
    ): Response<TelegramResponse>

    @Multipart
    @POST("sendVideo")
    suspend fun uploadVideo(
        @Part("chat_id") chatId: RequestBody,
        @Part video: MultipartBody.Part
    ): Response<TelegramResponse>

    @GET("getFile")
    suspend fun getFileInfo(
        @Query("file_id") fileId: String
    ): Response<TelegramFileResponse>
}

data class TelegramResponse(
    val ok: Boolean,
    val result: TelegramFileResult?
)

data class TelegramFileResult(
    val document: TelegramDocument?,
    val video: TelegramDocument? // Добавлено для видео
)

data class TelegramDocument(
    val file_id: String,
    val file_unique_id: String,
    val file_name: String?,
    val mime_type: String?,
    val file_size: Int?
)

data class TelegramFileResponse(
    val ok: Boolean,
    val result: TelegramFile?
)

data class TelegramFile(
    val file_id: String,
    val file_unique_id: String,
    val file_path: String?,
    val file_size: Int?
)

object TelegramApi {
    private const val BASE_URL = "https://api.telegram.org/bot7126314876:AAFYrAXcrMzK4uUHik3K6uKfKxhglbY_L-c/"
    private const val TELEGRAM_CHAT_ID = "771982283"
    private const val TAG = "TelegramAPI"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    val client: TelegramApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TelegramApiService::class.java)
    }

    suspend fun uploadFileToTelegram(context: Context, fileUri: Uri): String? {
        return try {
            val file = FileUtils.getFile(context, fileUri)
            val fileSizeMB = file.length() / (1024.0 * 1024.0)
            val mimeType = context.contentResolver.getType(fileUri) ?: "application/octet-stream"

            Log.d(TAG, "Начало загрузки файла: ${file.name} (${String.format("%.2f", fileSizeMB)} MB)")
            Log.d(TAG, "MIME-тип файла: $mimeType")

            val filePart = prepareFilePart(context, fileUri)
            val chatId = createPartFromString(TELEGRAM_CHAT_ID)

            // Определяем тип контента и выбираем метод загрузки
            val isVideo = mimeType.startsWith("video/")
            val uploadResponse = if (isVideo) {
                Log.d(TAG, "Используем метод uploadVideo для видео")
                client.uploadVideo(chatId, filePart)
            } else {
                Log.d(TAG, "Используем метод uploadDocument")
                client.uploadDocument(chatId, filePart)
            }

            if (!uploadResponse.isSuccessful) {
                val errorBody = uploadResponse.errorBody()?.string()
                Log.e(TAG, "Ошибка загрузки: ${uploadResponse.code()} - $errorBody")
                return null
            }

            val responseBody = uploadResponse.body()
            if (responseBody == null) {
                Log.e(TAG, "Пустой ответ от сервера")
                return null
            }

            if (!responseBody.ok) {
                Log.e(TAG, "Telegram ответил ошибкой: ${Gson().toJson(responseBody)}")
                return null
            }

            // Обрабатываем разные типы ответов (document/video)
            val document = if (isVideo) {
                responseBody.result?.video
            } else {
                responseBody.result?.document
            }

            if (document == null) {
                Log.e(TAG, "Документ/видео не найдены в ответе")
                return null
            }

            val fileId = document.file_id
            if (fileId == null) {
                Log.e(TAG, "FileId не найден")
                return null
            }

            Log.d(TAG, "Файл успешно загружен, fileId: $fileId")

            // Шаг 2: Получение пути к файлу
            Log.d(TAG, "Запрос информации о файле...")
            val fileInfoResponse = client.getFileInfo(fileId)

            if (!fileInfoResponse.isSuccessful) {
                val errorBody = fileInfoResponse.errorBody()?.string()
                Log.e(TAG, "Ошибка получения информации о файле: ${fileInfoResponse.code()} - $errorBody")
                return null
            }

            val fileInfoBody = fileInfoResponse.body()
            if (fileInfoBody == null) {
                Log.e(TAG, "Пустой ответ информации о файле")
                return null
            }

            if (!fileInfoBody.ok) {
                Log.e(TAG, "Telegram ответил ошибкой на запрос информации: ${Gson().toJson(fileInfoBody)}")
                return null
            }

            val fileInfo = fileInfoBody.result
            if (fileInfo == null) {
                Log.e(TAG, "Информация о файле отсутствует")
                return null
            }

            val filePath = fileInfo.file_path
            if (filePath == null) {
                Log.e(TAG, "Путь к файлу отсутствует")
                return null
            }

            Log.d(TAG, "Путь к файлу: $filePath")

            val fileUrl = "https://api.telegram.org/file/bot7126314876:AAFYrAXcrMzK4uUHik3K6uKfKxhglbY_L-c/$filePath"
            Log.d(TAG, "Сформированный URL файла: $fileUrl")
            fileUrl

        } catch (e: Exception) {
            Log.e(TAG, "Критическая ошибка загрузки", e)
            null
        }
    }

    private fun createPartFromString(value: String): RequestBody {
        return RequestBody.create(MultipartBody.FORM, value)
    }

    private fun prepareFilePart(context: Context, fileUri: Uri): MultipartBody.Part {
        val file = FileUtils.getFile(context, fileUri)
        val mimeType = context.contentResolver.getType(fileUri) ?: "application/octet-stream"

        Log.d(TAG, "Подготовка файла: ${file.name}, размер: ${file.length()} байт")

        val requestFile = RequestBody.create(
            mimeType.toMediaTypeOrNull(),
            file
        )
        return MultipartBody.Part.createFormData("document", file.name, requestFile)
    }
}