package com.example.bestplannner.screens

import android.content.Context
import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.bestplannner.data.Conversation
import com.example.bestplannner.data.ConversationMetadata
import com.example.bestplannner.data.Message
import org.threeten.bp.LocalDateTime
import org.threeten.bp.Instant
import org.threeten.bp.ZoneId
import org.threeten.bp.format.DateTimeFormatter
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationHistoryScreen(navController: NavController) {
    val context = LocalContext.current
    val conversationPreferences = context.getSharedPreferences("conversations", Context.MODE_PRIVATE)
    // 只加载对话元数据，不加载完整对话内容
    var conversationsMetadata by remember { mutableStateOf<List<ConversationMetadata>>(loadConversationsMetadata(conversationPreferences)) }

    // 添加广播接收器用于监听对话更新
    val conversationUpdateReceiver = remember {
        object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
                if (intent?.action == "com.example.bestplannner.CONVERSATION_UPDATED") {
                    // 对话已更新，刷新对话列表
                    conversationsMetadata = loadConversationsMetadata(conversationPreferences)
                }
            }
        }
    }

    // 注册和注销广播接收器
    DisposableEffect(Unit) {
        val filter = android.content.IntentFilter("com.example.bestplannner.CONVERSATION_UPDATED")
        context.registerReceiver(conversationUpdateReceiver, filter)
        
        onDispose {
            context.unregisterReceiver(conversationUpdateReceiver)
        }
    }

    // 刷新对话列表的函数
    val refreshConversations = {
        conversationsMetadata = loadConversationsMetadata(conversationPreferences)
    }

    // 当界面重新进入时刷新对话列表
    LaunchedEffect(Unit) {
        refreshConversations()
    }

    // 添加对计划状态变化的监听
    val planStatusPrefs = context.getSharedPreferences("plan_status", Context.MODE_PRIVATE)
    LaunchedEffect(planStatusPrefs.all) {
        refreshConversations()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "占星轨迹",
                        fontWeight = FontWeight.Bold
                    )
                },
                // 对话历史作为主界面，不需要返回按钮
                actions = {
                    Row(
                        modifier = Modifier.padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { navController.navigate("plan") },
                            modifier = Modifier.padding(horizontal = 2.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "计划",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        OutlinedButton(
                            onClick = { 
                                // 创建新对话
                                val newConversation = Conversation(
                                    id = System.currentTimeMillis(),
                                    title = "新星轨",
                                    messages = emptyList(),
                                    timestamp = LocalDateTime.now()
                                )
                                
                                // 为新对话设置独立的普通状态
                                setConversationStatus(context, newConversation.id, "normal")
                                // 导航到新创建的对话
                                navController.navigate("chat/${newConversation.id}") {
                                    // 避免创建多个相同的对话实例
                                    launchSingleTop = true
                                }
                            },
                            modifier = Modifier.padding(horizontal = 2.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "新星轨",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        OutlinedButton(
                            onClick = { navController.navigate("settings") },
                            modifier = Modifier.padding(horizontal = 2.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "设置",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (conversationsMetadata.isEmpty()) {
            EmptyConversationHistory()
        } else {
            // 按状态分类对话
            val categorizedConversations = categorizeConversations(context, conversationPreferences, conversationsMetadata)
            
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 未开始计划对话
                if (categorizedConversations.notStarted.isNotEmpty()) {
                    item {
                        Text(
                            text = "待觉醒星座",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                        )
                    }
                    items(categorizedConversations.notStarted.sortedByDescending { it.timestamp }) { conversationMeta -> 
                        val conversation = Conversation(
                            id = conversationMeta.id,
                            title = conversationMeta.title,
                            messages = emptyList(),
                            timestamp = conversationMeta.timestamp
                        )
                        
                        ConversationItem(
                            conversation = conversation,
                            conversationMeta = conversationMeta,
                            onClick = {
                                refreshConversations()
                                try {
                                    navController.navigate("chat/${conversation.id}")
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    navController.navigate("chat")
                                }
                            },
                            onDelete = {
                                val isPlanConversation = isConversationLinkedToPlan(conversationPreferences, conversationMeta.id)
                                if (!isPlanConversation) {
                                    deleteConversationById(context, conversationMeta.id)
                                    refreshConversations()
                                } else {
                                    // 对于计划关联的对话，只清空消息内容，但保留对话和计划的关联
                                    clearConversationMessages(context, conversationMeta.id)
                                    refreshConversations()
                                }
                            },
                            isDeletable = !isConversationLinkedToPlan(conversationPreferences, conversationMeta.id)
                        )
                    }
                }
                
                // 正在努力计划对话
                if (categorizedConversations.working.isNotEmpty()) {
                    item {
                        Text(
                            text = "运行中星轨",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                        )
                    }
                    items(categorizedConversations.working.sortedByDescending { it.timestamp }) { conversationMeta ->
                        val conversation = Conversation(
                            id = conversationMeta.id,
                            title = conversationMeta.title,
                            messages = emptyList(),
                            timestamp = conversationMeta.timestamp
                        )
                        
                        ConversationItem(
                            conversation = conversation,
                            conversationMeta = conversationMeta,
                            onClick = {
                                refreshConversations()
                                try {
                                    navController.navigate("chat/${conversation.id}")
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    navController.navigate("chat")
                                }
                            },
                            onDelete = {
                                val isPlanConversation = isConversationLinkedToPlan(conversationPreferences, conversationMeta.id)
                                if (!isPlanConversation) {
                                    deleteConversationById(context, conversationMeta.id)
                                    refreshConversations()
                                } else {
                                    // 对于计划关联的对话，只清空消息内容，但保留对话和计划的关联
                                    clearConversationMessages(context, conversationMeta.id)
                                    refreshConversations()
                                }
                            },
                            isDeletable = !isConversationLinkedToPlan(conversationPreferences, conversationMeta.id)
                        )
                    }
                }
                
                // 已完成计划对话
                if (categorizedConversations.completed.isNotEmpty()) {
                    item {
                        Text(
                            text = "已完成预言",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                        )
                    }
                    items(categorizedConversations.completed.sortedByDescending { it.timestamp }) { conversationMeta ->
                        val conversation = Conversation(
                            id = conversationMeta.id,
                            title = conversationMeta.title,
                            messages = emptyList(),
                            timestamp = conversationMeta.timestamp
                        )
                        
                        ConversationItem(
                            conversation = conversation,
                            conversationMeta = conversationMeta,
                            onClick = {
                                refreshConversations()
                                try {
                                    navController.navigate("chat/${conversation.id}")
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    navController.navigate("chat")
                                }
                            },
                            onDelete = {
                                // 已完成计划对话也不能删除
                                val isPlanConversation = isConversationLinkedToPlan(conversationPreferences, conversationMeta.id)
                                if (!isPlanConversation) {
                                    deleteConversationById(context, conversationMeta.id)
                                    refreshConversations()
                                }
                            },
                            isDeletable = false // 已完成计划对话不可删除
                        )
                    }
                }
                
                // 普通对话
                if (categorizedConversations.normal.isNotEmpty()) {
                    item {
                        Text(
                            text = "自由星云",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                        )
                    }
                    items(categorizedConversations.normal.sortedByDescending { it.timestamp }) { conversationMeta ->
                        val conversation = Conversation(
                            id = conversationMeta.id,
                            title = conversationMeta.title,
                            messages = emptyList(),
                            timestamp = conversationMeta.timestamp
                        )
                        
                        ConversationItem(
                            conversation = conversation,
                            conversationMeta = conversationMeta,
                            onClick = {
                                refreshConversations()
                                try {
                                    navController.navigate("chat/${conversation.id}")
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    navController.navigate("chat")
                                }
                            },
                            onDelete = {
                                val isPlanConversation = isConversationLinkedToPlan(conversationPreferences, conversationMeta.id)
                                if (!isPlanConversation) {
                                    deleteConversationById(context, conversationMeta.id)
                                    refreshConversations()
                                } else {
                                    // 对于计划关联的对话，只清空消息内容，但保留对话和计划的关联
                                    clearConversationMessages(context, conversationMeta.id)
                                    refreshConversations()
                                }
                            },
                            isDeletable = !isConversationLinkedToPlan(conversationPreferences, conversationMeta.id)
                        )
                    }
                }
            }
        }
    }
}

// 对话分类数据类
data class CategorizedConversations(
    val notStarted: List<ConversationMetadata>,
    val working: List<ConversationMetadata>,
    val completed: List<ConversationMetadata>,
    val normal: List<ConversationMetadata>
)

// 根据计划状态对对话进行分类
fun categorizeConversations(context: Context, preferences: android.content.SharedPreferences, conversations: List<ConversationMetadata>): CategorizedConversations {
    val notStarted = mutableListOf<ConversationMetadata>()
    val working = mutableListOf<ConversationMetadata>()
    val completed = mutableListOf<ConversationMetadata>()
    val normal = mutableListOf<ConversationMetadata>()
    
    for (conversationMeta in conversations) {
        if (isConversationLinkedToPlan(preferences, conversationMeta.id)) {
            val status = getConversationPlanStatus(context, preferences, conversationMeta.id)
            when (status) {
                "not_started" -> notStarted.add(conversationMeta)
                "working" -> working.add(conversationMeta)
                "completed" -> completed.add(conversationMeta)
                else -> notStarted.add(conversationMeta) // 默认为未开始
            }
        } else {
            normal.add(conversationMeta)
        }
    }
    
    return CategorizedConversations(notStarted, working, completed, normal)
}

// 设置对话的独立状态
fun setConversationStatus(context: Context, conversationId: Long, status: String) {
    val conversationStatusPrefs = context.getSharedPreferences("conversation_status", Context.MODE_PRIVATE)
    conversationStatusPrefs.edit().putString("conversation_${conversationId}_status", status).apply()
}

// 获取对话关联计划的状态
fun getConversationPlanStatus(context: Context, preferences: android.content.SharedPreferences, conversationId: Long): String {
    val conversationCount = preferences.getInt("conversation_count", 0)
    for (i in 0 until conversationCount) {
        val id = preferences.getLong("conversation_${i}_id", i.toLong())
        if (id == conversationId) {
            // 首先检查对话是否有自己的独立状态
            val conversationStatusPrefs = context.getSharedPreferences("conversation_status", Context.MODE_PRIVATE)
            val conversationStatus = conversationStatusPrefs.getString("conversation_${conversationId}_status", null)
            if (conversationStatus != null) {
                return conversationStatus
            }
            
            val planId = preferences.getLong("conversation_${i}_plan_id", -1)
            if (planId != -1L) {
                // 直接从plan_status SharedPreferences获取计划状态
                val planStatusPrefs = context.getSharedPreferences("plan_status", Context.MODE_PRIVATE)
                val status = planStatusPrefs.getString("plan_${planId}_status", "not_started")
                val completed = planStatusPrefs.getBoolean("plan_${planId}_completed", false)
                
                // 如果计划已完成，返回completed状态
                if (completed) {
                    return "completed"
                }
                
                // 移除对消息状态的检查，只依赖计划状态
                // 这样可以避免对话状态因消息内容而意外变更
                
                return status ?: "not_started"
            }
        }
    }
    
    // 如果没有找到关联的计划，检查对话任务状态
    val conversationTaskStatusPrefs = context.getSharedPreferences("conversation_task_status", Context.MODE_PRIVATE)
    return conversationTaskStatusPrefs.getString("conversation_${conversationId}", "not_started") ?: "not_started"
}

// 根据ID加载对话
fun loadConversationById(preferences: android.content.SharedPreferences, conversationId: Long): Conversation? {
    val conversationCount = preferences.getInt("conversation_count", 0)
    for (i in 0 until conversationCount) {
        val id = preferences.getLong("conversation_${i}_id", i.toLong())
        if (id == conversationId) {
            val title = preferences.getString("conversation_${i}_title", "对话") ?: "对话"
            val timestampMillis = preferences.getLong("conversation_${i}_timestamp", System.currentTimeMillis())

            val timestamp = try {
                LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(timestampMillis),
                    ZoneId.systemDefault()
                )
            } catch (e: Exception) {
                LocalDateTime.now()
            }

            // 加载消息
            val messageCount = preferences.getInt("conversation_${i}_message_count", 0)
            val messages = mutableListOf<Message>()

            for (j in 0 until messageCount) {
                val msgId = preferences.getLong("conversation_${i}_message_${j}_id", j.toLong())
                val text = preferences.getString("conversation_${i}_message_${j}_text", "") ?: ""
                val isUser = preferences.getBoolean("conversation_${i}_message_${j}_isUser", false)
                val msgTimestampMillis = preferences.getLong("conversation_${i}_message_${j}_timestamp", System.currentTimeMillis())

                val msgTimestamp = try {
                    LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(msgTimestampMillis),
                        ZoneId.systemDefault()
                    )
                } catch (e: Exception) {
                    LocalDateTime.now()
                }

                messages.add(Message(msgId, text, isUser, msgTimestamp))
            }

            return Conversation(id, title, messages, timestamp)
        }
    }
    return null
}

// 查找对话在SharedPreferences中的索引
fun findConversationIndexById(sharedPreferences: android.content.SharedPreferences, conversationId: Long): Int {
    val conversationCount = sharedPreferences.getInt("conversation_count", 0)
    for (i in 0 until conversationCount) {
        val id = sharedPreferences.getLong("conversation_${i}_id", i.toLong())
        if (id == conversationId) {
            return i
        }
    }
    return -1
}

// 加载对话元数据
fun loadConversationsMetadata(sharedPreferences: android.content.SharedPreferences): List<ConversationMetadata> {
    return try {
        val conversationCount = sharedPreferences.getInt("conversation_count", 0)
        val conversations = mutableListOf<ConversationMetadata>()

        for (i in 0 until conversationCount) {
            val id = sharedPreferences.getLong("conversation_${i}_id", i.toLong())
            val title = sharedPreferences.getString("conversation_${i}_title", "对话") ?: "对话"
            val timestampMillis = sharedPreferences.getLong("conversation_${i}_timestamp", System.currentTimeMillis())

            val timestamp = try {
                LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(timestampMillis),
                    ZoneId.systemDefault()
                )
            } catch (e: Exception) {
                LocalDateTime.now()
            }

            // 只加载消息数量，不加载具体消息内容
            val messageCount = sharedPreferences.getInt("conversation_${i}_message_count", 0)

            conversations.add(ConversationMetadata(id, title, timestamp, messageCount))
        }

        conversations
    } catch (e: Exception) {
        e.printStackTrace()
        // 出现异常时返回空列表而不是崩溃
        emptyList()
    }
}

// 根据ID删除对话
fun deleteConversationById(context: Context, conversationId: Long) {
    val sharedPreferences = context.getSharedPreferences("conversations", Context.MODE_PRIVATE)
    try {
        val editor = sharedPreferences.edit()

        // 查找要删除的对话索引
        var deleteIndex = -1
        val conversationCount = sharedPreferences.getInt("conversation_count", 0)
        for (i in 0 until conversationCount) {
            val id = sharedPreferences.getLong("conversation_${i}_id", i.toLong())
            if (id == conversationId) {
                deleteIndex = i
                break
            }
        }

        // 如果找到了要删除的对话
        if (deleteIndex != -1) {
            // 删除该对话的所有数据
            editor.remove("conversation_${deleteIndex}_id")
            editor.remove("conversation_${deleteIndex}_title")
            editor.remove("conversation_${deleteIndex}_timestamp")

            val messageCount = sharedPreferences.getInt("conversation_${deleteIndex}_message_count", 0)
            for (j in 0 until messageCount) {
                editor.remove("conversation_${deleteIndex}_message_${j}_id")
                editor.remove("conversation_${deleteIndex}_message_${j}_text")
                editor.remove("conversation_${deleteIndex}_message_${j}_isUser")
                editor.remove("conversation_${deleteIndex}_message_${j}_timestamp")
            }
            editor.remove("conversation_${deleteIndex}_message_count")

            // 将后面的对话索引前移
            for (i in (deleteIndex + 1) until conversationCount) {
                // 移动对话数据
                val nextIndex = i - 1
                editor.putLong("conversation_${nextIndex}_id", sharedPreferences.getLong("conversation_${i}_id", i.toLong()))
                editor.putString("conversation_${nextIndex}_title", sharedPreferences.getString("conversation_${i}_title", "对话") ?: "对话")
                editor.putLong("conversation_${nextIndex}_timestamp", sharedPreferences.getLong("conversation_${i}_timestamp", System.currentTimeMillis()))

                // 移动消息数据
                val messageCount = sharedPreferences.getInt("conversation_${i}_message_count", 0)
                editor.putInt("conversation_${nextIndex}_message_count", messageCount)
                for (j in 0 until messageCount) {
                    editor.putLong("conversation_${nextIndex}_message_${j}_id", sharedPreferences.getLong("conversation_${i}_message_${j}_id", j.toLong()))
                    editor.putString("conversation_${nextIndex}_message_${j}_text", sharedPreferences.getString("conversation_${i}_message_${j}_text", "") ?: "")
                    editor.putBoolean("conversation_${nextIndex}_message_${j}_isUser", sharedPreferences.getBoolean("conversation_${i}_message_${j}_isUser", false))
                    editor.putLong("conversation_${nextIndex}_message_${j}_timestamp", sharedPreferences.getLong("conversation_${i}_message_${j}_timestamp", System.currentTimeMillis()))
                }

                // 删除旧索引的数据
                editor.remove("conversation_${i}_id")
                editor.remove("conversation_${i}_title")
                editor.remove("conversation_${i}_timestamp")
                editor.remove("conversation_${i}_message_count")
                for (j in 0 until messageCount) {
                    editor.remove("conversation_${i}_message_${j}_id")
                    editor.remove("conversation_${i}_message_${j}_text")
                    editor.remove("conversation_${i}_message_${j}_isUser")
                    editor.remove("conversation_${i}_message_${j}_timestamp")
                }
            }

            // 更新对话总数
            editor.putInt("conversation_count", conversationCount - 1)
            editor.apply()
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

// 安全加载对话历史的包装函数
fun loadConversations(sharedPreferences: android.content.SharedPreferences): List<Conversation> {
    return try {
        val conversationCount = sharedPreferences.getInt("conversation_count", 0)
        val conversations = mutableListOf<Conversation>()

        for (i in 0 until conversationCount) {
            val id = sharedPreferences.getLong("conversation_${i}_id", i.toLong())
            val title = sharedPreferences.getString("conversation_${i}_title", "对话") ?: "对话"
            val timestampMillis = sharedPreferences.getLong("conversation_${i}_timestamp", System.currentTimeMillis())

            val timestamp = try {
                LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(timestampMillis),
                    ZoneId.systemDefault()
                )
            } catch (e: Exception) {
                LocalDateTime.now()
            }

            // 加载消息
            val messageCount = sharedPreferences.getInt("conversation_${i}_message_count", 0)
            val messages = mutableListOf<Message>()

            for (j in 0 until messageCount) {
                val msgId = sharedPreferences.getLong("conversation_${i}_message_${j}_id", j.toLong())
                val text = sharedPreferences.getString("conversation_${i}_message_${j}_text", "") ?: ""
                val isUser = sharedPreferences.getBoolean("conversation_${i}_message_${j}_isUser", false)
                val msgTimestampMillis = sharedPreferences.getLong("conversation_${i}_message_${j}_timestamp", System.currentTimeMillis())

                val msgTimestamp = try {
                    LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(msgTimestampMillis),
                        ZoneId.systemDefault()
                    )
                } catch (e: Exception) {
                    LocalDateTime.now()
                }

                messages.add(Message(msgId, text, isUser, msgTimestamp))
            }

            conversations.add(Conversation(id, title, messages, timestamp))
        }

        conversations
    } catch (e: Exception) {
        e.printStackTrace()
        // 出现异常时返回空列表而不是崩溃
        emptyList()
    }
}

// 保存对话列表到SharedPreferences
fun saveConversations(context: Context, conversations: List<Conversation>) {
    val sharedPreferences = context.getSharedPreferences("conversations", Context.MODE_PRIVATE)
    val editor = sharedPreferences.edit()

    try {
        // 先清除所有现有对话数据
        val conversationCount = sharedPreferences.getInt("conversation_count", 0)
        for (i in 0 until conversationCount) {
            editor.remove("conversation_${i}_id")
            editor.remove("conversation_${i}_title")
            editor.remove("conversation_${i}_timestamp")

            val messageCount = sharedPreferences.getInt("conversation_${i}_message_count", 0)
            for (j in 0 until messageCount) {
                editor.remove("conversation_${i}_message_${j}_id")
                editor.remove("conversation_${i}_message_${j}_text")
                editor.remove("conversation_${i}_message_${j}_isUser")
                editor.remove("conversation_${i}_message_${j}_timestamp")
            }
            editor.remove("conversation_${i}_message_count")
        }

        // 保存新的对话列表
        editor.putInt("conversation_count", conversations.size)

        conversations.forEachIndexed { index, conversation ->
            editor.putLong("conversation_${index}_id", conversation.id)
            editor.putString("conversation_${index}_title", conversation.title)
            editor.putLong("conversation_${index}_timestamp",
                conversation.timestamp.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())

            editor.putInt("conversation_${index}_message_count", conversation.messages.size)

            conversation.messages.forEachIndexed { msgIndex, message ->
                editor.putLong("conversation_${index}_message_${msgIndex}_id", message.id)
                editor.putString("conversation_${index}_message_${msgIndex}_text", message.text)
                editor.putBoolean("conversation_${index}_message_${msgIndex}_isUser", message.isUser)
                editor.putLong("conversation_${index}_message_${msgIndex}_timestamp",
                    message.timestamp.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
            }
        }

        editor.apply()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

// 安全加载对话历史的包装函数
fun loadConversationsSafe(sharedPreferences: android.content.SharedPreferences): List<Conversation> {
    return try {
        loadConversations(sharedPreferences)
    } catch (e: Exception) {
        e.printStackTrace()
        // 出现异常时返回空列表而不是崩溃
        emptyList()
    }
}

@Composable
fun EmptyConversationHistory() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Star,
            contentDescription = "暂无星轨",
            modifier = Modifier
                .size(64.dp)
                .padding(bottom = 16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "暂无星轨记录",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = "在星语界面开始新的命运轨迹，或前往命运规划制定你的星象安排",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ConversationItem(
    conversation: Conversation,
    conversationMeta: ConversationMetadata,
    onClick: () -> Unit,
    onDelete: () -> Unit, // 添加删除回调
    isDeletable: Boolean = true // 添加是否可删除的标志
) {
    var offsetX by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    val context = LocalContext.current

    // 添加动画状态
    val animatedOffsetX by animateFloatAsState(
        targetValue = offsetX,
        animationSpec = tween(
            durationMillis = 300,
            easing = FastOutSlowInEasing
        ),
        label = "offset_animation"
    )

    // 检查对话是否与计划关联并获取状态
    val isPlanConversation = isConversationLinkedToPlan(context.getSharedPreferences("conversations", Context.MODE_PRIVATE), conversation.id)
    val planStatus = if (isPlanConversation) {
        getConversationPlanStatus(context, context.getSharedPreferences("conversations", Context.MODE_PRIVATE), conversation.id)
    } else {
        ""
    }

    // 根据计划状态设置背景颜色 - 已完成状态使用更鲜明的背景色
    val backgroundColor = when (planStatus) {
        "completed" -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) // 已完成计划使用更鲜明的背景色
        "working" -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f) // 正在努力计划使用特殊背景色
        "not_started" -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f) // 未开始计划使用特殊背景色
        else -> MaterialTheme.colorScheme.surface // 普通对话使用默认背景色
    }

    // 星座主题颜色
    val astrologicalColors = listOf(
        Color(0xFF9B5DE5), // 紫色 - 灵性
        Color(0xFFF15BB5), // 粉色 - 爱情
        Color(0xFF00BBF9), // 蓝色 - 智慧
        Color(0xFF00F5D4), // 青色 - 治愈
        Color(0xFFFEE440), // 黄色 - 光明
        Color(0xFFFF9E00)  // 橙色 - 创造力
    )
    
    val randomColor = astrologicalColors[(conversation.id % astrologicalColors.size).toInt()]

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .offset(x = with(density) { animatedOffsetX.toDp() })
            .draggable(
                state = rememberDraggableState { delta ->
                    // 只允许向左滑动且只有可删除的对话才能滑动
                    if (offsetX + delta < 0 && isDeletable) {
                        // 限制最大滑动距离
                        val newOffset = (offsetX + delta).coerceAtLeast(-250f)
                        offsetX = newOffset
                    }
                },
                orientation = Orientation.Horizontal,
                onDragStopped = {
                    // 根据滑动距离决定最终位置
                    if (offsetX < -100f && isDeletable) {
                        // 滑动距离超过阈值，显示删除按钮
                        offsetX = -200f
                    } else {
                        // 否则回到原位
                        offsetX = 0f
                    }
                }
            )
            .clickable(
                enabled = offsetX == 0f, // 只有在未滑动状态下才能点击
                onClick = {
                    // 点击时重置滑动状态
                    offsetX = 0f
                    onClick()
                }
            ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (offsetX != 0f) 8.dp else 2.dp,
            hoveredElevation = if (offsetX != 0f) 12.dp else 4.dp
        ),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        )
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // 删除按钮背景（仅对可删除对话显示）
            if (offsetX < -50f && isDeletable) {
                Row(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.errorContainer,
                                    MaterialTheme.colorScheme.error
                                )
                            )
                        ),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 删除选项按钮
                    var showDeleteDialog by remember { mutableStateOf(false) }

                    Surface(
                        onClick = {
                            showDeleteDialog = true
                        },
                        modifier = Modifier.padding(end = 10.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "删除选项",
                            tint = MaterialTheme.colorScheme.onError,
                            modifier = Modifier.padding(8.dp)
                        )
                    }

                    // 删除确认对话框
                    if (showDeleteDialog) {
                        AlertDialog(
                            onDismissRequest = {
                                showDeleteDialog = false
                                offsetX = 0f
                            },
                            title = {
                                Text("星轨操作确认")
                            },
                            text = {
                                Column {
                                    Text("您想要执行哪种操作？")
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("• 清空消息：仅删除星轨内容，保留星轨标题")
                                    Text("• 删除星轨：完全删除此星轨及其所有内容")
                                }
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        clearConversationMessages(context, conversation.id)
                                        showDeleteDialog = false
                                        offsetX = 0f
                                        onClick()
                                    }
                                ) {
                                    Text("清空消息")
                                }
                            },
                            dismissButton = {
                                Row {
                                    TextButton(
                                        onClick = {
                                            val conversationPreferences = context.getSharedPreferences("conversations", Context.MODE_PRIVATE)
                                            val conversations = loadConversationsSafe(conversationPreferences)
                                            val updatedConversations = conversations.filter { it.id != conversation.id }
                                            saveConversations(context, updatedConversations)
                                            showDeleteDialog = false
                                            onDelete()
                                        }
                                    ) {
                                        Text("删除星轨")
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    TextButton(
                                        onClick = {
                                            showDeleteDialog = false
                                            offsetX = 0f
                                        }
                                    ) {
                                        Text("取消")
                                    }
                                }
                            }
                        )
                    }
                }
            }

            // 主要内容
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
                    .graphicsLayer {
                        // 添加轻微的阴影效果
                        shadowElevation = if (offsetX != 0f) 8f else 2f
                    }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = randomColor,
                            modifier = Modifier
                                .size(12.dp)
                                .padding(end = 3.dp)
                        )
                        Text(
                            text = conversation.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontSize = MaterialTheme.typography.titleMedium.fontSize * 0.85f,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = try {
                            conversation.timestamp.format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
                        } catch (e: Exception) {
                            "时间未知"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = MaterialTheme.typography.bodySmall.fontSize * 0.85f,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (conversation.messages.isNotEmpty()) {
                    Text(
                        text = conversation.messages.last().text,
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = MaterialTheme.typography.bodyMedium.fontSize * 0.85f,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Text(
                    text = "${conversationMeta.messageCount} 条星语",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.85f,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )

                // 如果是计划关联的对话，显示提示信息和状态
                if (!isDeletable || isPlanConversation) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (isPlanConversation) "命运星轨" else "无法删除",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.85f,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        // 显示计划状态
                        if (isPlanConversation) {
                            val statusText = when (planStatus) {
                                "not_started" -> "待觉醒"
                                "working" -> "运行中"
                                "completed" -> "已完成预言"
                                else -> "未知状态"
                            }
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.85f,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

// 添加清空对话消息的函数
fun clearConversationMessages(context: Context, conversationId: Long) {
    val conversationPreferences = context.getSharedPreferences("conversations", Context.MODE_PRIVATE)
    val notificationPreferences = context.getSharedPreferences("notification_tracker", Context.MODE_PRIVATE)
    try {
        val editor = conversationPreferences.edit()

        // 查找要清空的对话索引
        val conversationCount = conversationPreferences.getInt("conversation_count", 0)
        for (i in 0 until conversationCount) {
            val id = conversationPreferences.getLong("conversation_${i}_id", i.toLong())
            if (id == conversationId) {
                // 清空消息数据
                val messageCount = conversationPreferences.getInt("conversation_${i}_message_count", 0)
                for (j in 0 until messageCount) {
                    editor.remove("conversation_${i}_message_${j}_id")
                    editor.remove("conversation_${i}_message_${j}_text")
                    editor.remove("conversation_${i}_message_${j}_isUser")
                    editor.remove("conversation_${i}_message_${j}_timestamp")
                }
                editor.putInt("conversation_${i}_message_count", 0)

                // 清除与该对话相关的AI记忆（通过计划ID）
                val planId = conversationPreferences.getLong("conversation_${i}_plan_id", -1)
                if (planId != -1L) {
                    val notificationEditor = notificationPreferences.edit()
                    notificationEditor.remove("plan_${planId}_last_notification")
                    notificationEditor.remove("plan_${planId}_last_user_message")
                    notificationEditor.remove("plan_${planId}_last_opened")
                    notificationEditor.apply()
                }

                editor.apply()
                break
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

// 根据计划ID查找关联的对话ID
fun findConversationIdByPlanId(context: Context, planId: Long): Long {
    val conversationPreferences = context.getSharedPreferences("conversations", Context.MODE_PRIVATE)
    val conversationCount = conversationPreferences.getInt("conversation_count", 0)

    for (i in 0 until conversationCount) {
        val storedPlanId = conversationPreferences.getLong("conversation_${i}_plan_id", -1)
        if (storedPlanId == planId) {
            return conversationPreferences.getLong("conversation_${i}_id", -1)
        }
    }

    return -1
}

// 添加检查对话是否与计划关联的函数
fun isConversationLinkedToPlan(sharedPreferences: android.content.SharedPreferences, conversationId: Long): Boolean {
    val conversationCount = sharedPreferences.getInt("conversation_count", 0)
    for (i in 0 until conversationCount) {
        val id = sharedPreferences.getLong("conversation_${i}_id", i.toLong())
        if (id == conversationId) {
            // 检查是否存在计划ID，如果存在且不为-1，则表示是计划关联的对话
            val planId = sharedPreferences.getLong("conversation_${i}_plan_id", -1)
            return planId != -1L
        }
    }
    return false
}