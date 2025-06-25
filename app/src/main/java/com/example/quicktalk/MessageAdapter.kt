package com.example.quicktalk

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class MessageAdapter(private val currentUserId: String) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var messages: List<Message> = emptyList()
    private val tempMessages = mutableListOf<Message>()

    companion object {
        private const val MY_TEXT = 1
        private const val OTHER_TEXT = 2
        private const val MY_MEDIA = 3
        private const val OTHER_MEDIA = 4
    }

    inner class TextViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textViewMessage: TextView = itemView.findViewById(R.id.textViewMessage)
    }

    inner class MediaViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val mediaIcon: ImageView = itemView.findViewById(R.id.mediaIcon)
        val fileName: TextView = itemView.findViewById(R.id.fileName)
        val progressBar: ProgressBar = itemView.findViewById(R.id.progressBar)
    }

    override fun getItemViewType(position: Int): Int {
        val message = messages[position]
        return if (message.senderId == currentUserId) {
            if (message.type == "TEXT") MY_TEXT else MY_MEDIA
        } else {
            if (message.type == "TEXT") OTHER_TEXT else OTHER_MEDIA
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            MY_TEXT -> TextViewHolder(
                LayoutInflater.from(parent.context).inflate(R.layout.my_message_item, parent, false)
            )
            OTHER_TEXT -> TextViewHolder(
                LayoutInflater.from(parent.context).inflate(R.layout.other_message_item, parent, false)
            )
            MY_MEDIA -> MediaViewHolder(
                LayoutInflater.from(parent.context).inflate(R.layout.my_media_item, parent, false)
            )
            else -> MediaViewHolder(
                LayoutInflater.from(parent.context).inflate(R.layout.other_media_item, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]

        when (holder) {
            is TextViewHolder -> {
                holder.textViewMessage.text = message.text
            }
            is MediaViewHolder -> {
                holder.fileName.text = message.fileName ?: "Файл"

                val iconRes = when (message.type) {
                    "IMAGE" -> R.drawable.ic_image
                    "VIDEO" -> R.drawable.ic_video
                    "AUDIO" -> R.drawable.ic_audio
                    else -> R.drawable.ic_document
                }
                holder.mediaIcon.setImageResource(iconRes)

                if (message.progress in 0..100) {
                    holder.progressBar.visibility = View.VISIBLE
                    holder.progressBar.progress = message.progress
                } else {
                    holder.progressBar.visibility = View.GONE
                }

                holder.itemView.setOnClickListener {
                    if (message.fileUrl != null) {
                        (holder.itemView.context as? ChatActivity)?.openFile(message)
                    }
                }
            }
        }
    }

    override fun getItemCount() = messages.size

    fun setMessages(messages: List<Message>) {
        this.messages = messages.sortedBy { it.timestamp?.seconds }
        notifyDataSetChanged()
    }

    fun addTempMessage(message: Message) {
        tempMessages.add(message)
        refreshMessages()
    }

    fun updateUploadProgress(timestamp: Long, progress: Int) {
        tempMessages.find { it.timestamp?.seconds == timestamp }?.progress = progress
        refreshMessages()
    }

    fun confirmUpload(timestamp: Long, fileUrl: String) {
        val message = tempMessages.find { it.timestamp?.seconds == timestamp }
        message?.fileUrl = fileUrl
        message?.progress = -1
        refreshMessages()
    }

    fun removeTempMessage(timestamp: Long) {
        tempMessages.removeIf { it.timestamp?.seconds == timestamp }
        refreshMessages()
    }

    private fun refreshMessages() {
        val combined = messages + tempMessages
        this.messages = combined.sortedBy { it.timestamp?.seconds }
        notifyDataSetChanged()
    }
}