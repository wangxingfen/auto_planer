package com.example.bestplannner.components.chat

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import com.example.bestplannner.data.Message
import java.io.File
import org.threeten.bp.LocalDateTime
import org.threeten.bp.format.DateTimeFormatter

@Composable
fun ChatMessageItem(
    message: Message,
    onWorkingClicked: (() -> Unit)? = null,
    onNotStartedClicked: (() -> Unit)? = null,
    onCompletedClicked: (() -> Unit)? = null,
    taskStatus: String? = null
) {
    val context = LocalContext.current
    val chatPreferences = context.getSharedPreferences("chat_settings", Context.MODE_PRIVATE)
    var chatBubbleFontSize by remember { mutableStateOf(chatPreferences.getFloat("chat_bubble_font_size", 16f)) }
    var backgroundImageBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    
    // 加载背景图片
    androidx.compose.runtime.DisposableEffect(Unit) {
        val fileName = chatPreferences.getString("chat_background_uri", "")
        if (!fileName.isNullOrEmpty()) {
            try {
                val file = File(context.filesDir, fileName)
                if (file.exists()) {
                    val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                    backgroundImageBitmap = bitmap.asImageBitmap()
                }
            } catch (e: Exception) {
                android.util.Log.e("ChatMessageItem", "加载背景图片失败", e)
            }
        }
        
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "chat_bubble_font_size") {
                chatBubbleFontSize = chatPreferences.getFloat("chat_bubble_font_size", 16f)
            } else if (key == "chat_background_uri") {
                // 重新加载背景图片
                val fileName = chatPreferences.getString("chat_background_uri", "")
                if (!fileName.isNullOrEmpty()) {
                    try {
                        val file = File(context.filesDir, fileName)
                        if (file.exists()) {
                            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                            backgroundImageBitmap = bitmap.asImageBitmap()
                        } else {
                            backgroundImageBitmap = null
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("ChatMessageItem", "加载背景图片失败", e)
                        backgroundImageBitmap = null
                    }
                } else {
                    backgroundImageBitmap = null
                }
            }
        }
        chatPreferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            chatPreferences.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 300.dp),
            shape = RoundedCornerShape(8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            colors = if (message.isUser) {
                // 用户消息使用主色调，添加透明度确保在背景上可见
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                )
            } else {
                // AI消息使用次要色调，添加透明度确保在背景上可见
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                )
            }
        ) {
            Box {
                // 背景图片
                if (backgroundImageBitmap != null && !message.isUser) {
                    Image(
                        bitmap = backgroundImageBitmap!!,
                        contentDescription = null,
                        modifier = Modifier
                            .matchParentSize(),
                        contentScale = ContentScale.Crop,
                        alpha = 0.2f
                    )
                }
                
                Column {
                    SelectionContainer {
                        Text(
                            text = message.text.trim(),
                            modifier = Modifier.padding(12.dp),
                            color = if (message.isUser) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            },
                            style = TextStyle(
                                fontSize = chatBubbleFontSize.sp
                            )
                        )
                    }

                    // 只在AI消息中显示按钮
                    if (!message.isUser && (onWorkingClicked != null || onNotStartedClicked != null || onCompletedClicked != null)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 未开始按钮
                            Button(
                                onClick = { onNotStartedClicked?.invoke() },
                                modifier = Modifier.weight(1f),
                                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                                colors = if (taskStatus == "not_started") {
                                    // 高亮显示
                                    ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    )
                                } else {
                                    // 默认状态
                                    ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            ) {
                                Text("未开始")
                            }

                            // 正在努力按钮
                            Button(
                                onClick = { onWorkingClicked?.invoke() },
                                modifier = Modifier.weight(1f),
                                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                                colors = if (taskStatus == "working") {
                                    // 高亮显示
                                    ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                } else {
                                    // 默认状态
                                    ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            ) {
                                Text("正在努力")
                            }

                            // 已完成按钮
                            Button(
                                onClick = { onCompletedClicked?.invoke() },
                                modifier = Modifier.weight(1f),
                                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                                colors = if (taskStatus == "completed") {
                                    // 高亮显示
                                    ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                } else {
                                    // 默认状态
                                    ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            ) {
                                Text("已完成")
                            }
                        }
                    }

                    Text(
                        text = message.timestamp.format(DateTimeFormatter.ofPattern("HH:mm")),
                        modifier = Modifier
                            .align(if (message.isUser) Alignment.End else Alignment.Start)
                            .padding(end = 12.dp, start = 12.dp, bottom = 8.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (message.isUser) {
                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SystemMessageItem(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 300.dp),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
        ) {
            Column {
                Text(
                    text = message,
                    modifier = Modifier
                        .padding(12.dp)
                        .fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}