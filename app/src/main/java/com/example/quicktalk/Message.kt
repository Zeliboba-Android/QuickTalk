package com.example.quicktalk

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName

data class Message(
    val text: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    @get:PropertyName("timestamp")
    @set:PropertyName("timestamp")
    var timestamp: Timestamp? = null,
    val type: String = "TEXT", // TEXT, IMAGE, VIDEO, AUDIO, DOCUMENT
    var fileUrl: String? = null,
    val fileName: String? = null,
    var progress: Int = -1 // -1 = not uploading, 0-100 = upload progress
)