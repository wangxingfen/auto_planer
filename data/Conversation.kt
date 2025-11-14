package com.example.bestplannner.data

import org.threeten.bp.LocalDateTime
import com.example.bestplannner.data.Message

data class Conversation(
    val id: Long,
    val title: String,
    val messages: List<Message>,
    val timestamp: LocalDateTime
)