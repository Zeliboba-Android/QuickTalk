package com.example.quicktalk

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.quicktalk.databinding.ActivityChatBinding
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.UploadTask
import kotlinx.coroutines.launch
import java.util.Locale

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var storageRef: StorageReference
    private lateinit var adapter: MessageAdapter
    private lateinit var messageListener: ListenerRegistration

    private var currentUserId: String = ""
    private var receiverId: String = ""
    private var receiverName: String = ""
    private var receiverStatusListener: ListenerRegistration? = null
    private var uploadTask: UploadTask? = null

    private val PICK_FILE_REQUEST = 100
    private val PERMISSION_REQUEST_CODE = 200

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        storageRef = FirebaseStorage.getInstance().reference
        currentUserId = auth.currentUser?.uid ?: ""

        receiverId = intent.getStringExtra("userId") ?: run {
            Toast.makeText(this, "Ошибка: пользователь не выбран", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        receiverName = intent.getStringExtra("userName") ?: "User"

        setupUI()
        setupRecyclerView()
        loadMessages()
        setupClickListeners()
        setupReceiverStatusListener()
    }

    private fun setupReceiverStatusListener() {
        receiverStatusListener = db.collection("users").document(receiverId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("ChatActivity", "Ошибка подписки на статус получателя", error)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val online = snapshot.getBoolean("online") ?: false
                    updateOnlineStatusIndicator(online)
                }
            }
    }

    private fun updateOnlineStatusIndicator(online: Boolean) {
        val statusIndicator = binding.onlineStatus
        if (online) {
            statusIndicator.setBackgroundResource(R.drawable.circle_green)
        } else {
            statusIndicator.setBackgroundResource(R.drawable.circle_red)
        }
    }

    private fun setupUI() {
        binding.textViewTitle.text = receiverName
    }

    private fun setupRecyclerView() {
        adapter = MessageAdapter(currentUserId)
        binding.recyclerViewMessage.apply {
            layoutManager = LinearLayoutManager(this@ChatActivity).apply {
                stackFromEnd = true
            }
            adapter = this@ChatActivity.adapter
        }
    }

    private fun loadMessages() {
        val chatId = generateChatId(currentUserId, receiverId)

        messageListener = db.collection("chats")
            .document(chatId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("ChatActivity", "Ошибка загрузки сообщений: $error")
                    return@addSnapshotListener
                }

                val messages = mutableListOf<Message>()
                snapshot?.documents?.forEach { document ->
                    val message = document.toObject(Message::class.java)
                    message?.let { messages.add(it) }
                }

                adapter.setMessages(messages)
                scrollToBottom()
            }
    }

    private fun scrollToBottom() {
        if (adapter.itemCount > 0) {
            binding.recyclerViewMessage.scrollToPosition(adapter.itemCount - 1)
        }
    }

    private fun setupClickListeners() {
        binding.imageViewSend.setOnClickListener {
            sendTextMessage()
        }

        binding.buttonAttach.setOnClickListener {
            checkPermissionsAndOpenFileChooser()
        }
    }

    private fun checkPermissionsAndOpenFileChooser() {
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        } else {
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }

        val allPermissionsGranted = requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (!allPermissionsGranted) {
            // Запрашиваем недостающие разрешения
            ActivityCompat.requestPermissions(
                this,
                requiredPermissions,
                PERMISSION_REQUEST_CODE
            )
        } else {
            openFileChooser()
        }
    }

    private fun openFileChooser() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "*/*"
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        startActivityForResult(intent, PICK_FILE_REQUEST)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val allGranted = grantResults.isNotEmpty() &&
                    grantResults.all { it == PackageManager.PERMISSION_GRANTED }

            if (allGranted) {
                openFileChooser()
            } else {
                val showRationale = permissions.any {
                    ActivityCompat.shouldShowRequestPermissionRationale(this, it)
                }

                if (showRationale) {
                    // Показать объяснение, почему нужно разрешение
                    showPermissionExplanationDialog()
                } else {
                    // Пользователь выбрал "Больше не спрашивать"
                    showPermissionSettingsDialog()
                }
            }
        }
    }
    private fun showPermissionExplanationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Нужны разрешения")
            .setMessage("Для выбора файлов необходимо предоставить разрешения на доступ к хранилищу")
            .setPositiveButton("OK") { _, _ ->
                checkPermissionsAndOpenFileChooser()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
    private fun showPermissionSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Разрешения отклонены")
            .setMessage("Вы отклонили разрешения и выбрали \"Больше не спрашивать\". " +
                    "Вы можете включить их в настройках приложения.")
            .setPositiveButton("Настройки") { _, _ ->
                openAppSettings()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_FILE_REQUEST && resultCode == RESULT_OK && data != null) {
            val uri = data.data
            uri?.let { uploadFile(it) }
        }
    }

    // В методе uploadFile:
    private fun uploadFile(fileUri: Uri) {
        val fileName = getFileName(fileUri) ?: "file_${System.currentTimeMillis()}"
        val fileType = getFileType(fileName)

        val tempMessage = Message(
            type = fileType,
            fileName = fileName,
            senderId = currentUserId,
            receiverId = receiverId,
            timestamp = Timestamp.now(),
            progress = 0
        )

        adapter.addTempMessage(tempMessage)
        scrollToBottom()

        lifecycleScope.launch {
            try {
                Log.d("ChatActivityyy", "Начало загрузки файла в Telegram...")
                val fileUrl = TelegramApi.uploadFileToTelegram(this@ChatActivity, fileUri)

                if (fileUrl != null) {
                    Log.d("ChatActivityyy", "Файл успешно загружен: $fileUrl")
                    adapter.confirmUpload(tempMessage.timestamp!!.seconds, fileUrl)
                    saveMessageToFirestore(
                        type = fileType,
                        fileName = fileName,
                        fileUrl = fileUrl
                    )
                } else {
                    Log.e("ChatActivityyy", "Ошибка загрузки файла")
                    adapter.removeTempMessage(tempMessage.timestamp!!.seconds)
                    runOnUiThread {
                        Toast.makeText(
                            this@ChatActivity,
                            "Ошибка загрузки файла",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatActivityyy", "Ошибка загрузки: ${e.message}", e)
                adapter.removeTempMessage(tempMessage.timestamp!!.seconds)
                runOnUiThread {
                    Toast.makeText(
                        this@ChatActivity,
                        "Ошибка: ${e.message ?: "неизвестная ошибка"}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    fun openFile(message: Message) {
        when (message.type) {
            "IMAGE", "VIDEO" -> openMediaFile(message)
            else -> downloadFile(message)
        }
    }

    private fun openMediaFile(message: Message) {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(Uri.parse(message.fileUrl), getMimeType(message.fileName))
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Не удалось открыть файл: ${e.message}", Toast.LENGTH_SHORT).show()
            downloadFile(message)
        }
    }

    private fun downloadFile(message: Message) {
        try {
            val request = DownloadManager.Request(Uri.parse(message.fileUrl))
                .setTitle(message.fileName ?: "Файл")
                .setDescription("Загрузка файла")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, message.fileName)

            val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(request)

            Toast.makeText(this, "Файл загружается в папку Downloads", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка загрузки: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getMimeType(fileName: String?): String {
        return when (fileName?.substringAfterLast('.', "")?.lowercase(Locale.getDefault())) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "mp4" -> "video/mp4"
            "mov" -> "video/quicktime"
            "avi" -> "video/x-msvideo"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "pdf" -> "application/pdf"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "ppt", "pptx" -> "application/vnd.ms-powerpoint"
            "txt" -> "text/plain"
            "zip" -> "application/zip"
            else -> "*/*"
        }
    }

    private fun getFileType(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "").lowercase(Locale.getDefault())
        return when (extension) {
            "jpg", "jpeg", "png", "gif" -> "IMAGE"
            "mp4", "mov", "avi", "mkv" -> "VIDEO"
            "mp3", "wav", "ogg", "m4a" -> "AUDIO"
            else -> "DOCUMENT"
        }
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != -1 && cut != null) {
                result = result?.substring(cut + 1)
            }
        }
        return result
    }

    private fun sendTextMessage() {
        val messageText = binding.editTextMessage.text.toString().trim()
        if (messageText.isEmpty()) {
            Toast.makeText(this, "Введите сообщение", Toast.LENGTH_SHORT).show()
            return
        }

        val message = Message(
            text = messageText,
            senderId = currentUserId,
            receiverId = receiverId,
            timestamp = Timestamp.now()
        )

        // Исправленная строка:
        saveMessageToFirestore(message = message)
        binding.editTextMessage.text.clear()
    }

    private fun saveMessageToFirestore(
        type: String = "TEXT",
        fileName: String? = null,
        fileUrl: String? = null,
        message: Message? = null
    ) {
        val chatId = generateChatId(currentUserId, receiverId)
        val messageObj = message ?: Message(
            type = type,
            fileName = fileName,
            fileUrl = fileUrl,
            senderId = currentUserId,
            receiverId = receiverId,
            timestamp = Timestamp.now()
        )

        db.collection("chats")
            .document(chatId)
            .collection("messages")
            .add(messageObj)
            .addOnSuccessListener {
                val lastMessage = when (type) {
                    "TEXT" -> messageObj.text
                    "IMAGE" -> "Фото"
                    "VIDEO" -> "Видео"
                    "AUDIO" -> "Аудио"
                    else -> "Файл"
                }
                updateChatMetadata(chatId, lastMessage)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Ошибка отправки: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateChatMetadata(chatId: String, lastMessage: String) {
        val metadata = hashMapOf(
            "lastMessage" to lastMessage,
            "timestamp" to FieldValue.serverTimestamp(),
            "participants" to listOf(currentUserId, receiverId)
        )

        db.collection("chats")
            .document(chatId)
            .set(metadata)
    }

    private fun generateChatId(user1: String, user2: String): String {
        return if (user1 < user2) "${user1}_$user2" else "${user2}_$user1"
    }

    override fun onDestroy() {
        super.onDestroy()
        messageListener.remove()
        receiverStatusListener?.remove()
        uploadTask?.cancel()
    }
}