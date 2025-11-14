package com.example.bestplannner.data

import org.threeten.bp.LocalDateTime

data class ConversationMetadata(
    val id: Long,
    val title: String,
    val timestamp: LocalDateTime,
    val messageCount: Int
)