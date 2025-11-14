package com.example.bestplannner.data

import org.threeten.bp.LocalDateTime

data class Message(
    val id: Long,
    val text: String,
    val isUser: Boolean,
    val timestamp: LocalDateTime
)