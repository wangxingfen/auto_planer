package com.example.bestplannner.screens

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.bestplannner.components.chat.*
import com.example.bestplannner.components.chat.ConversationManager
import com.example.bestplannner.data.Conversation
import com.example.bestplannner.data.ConversationMetadata
import com.example.bestplannner.data.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONException
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.SocketException
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(navController: NavController, initialConversationId: Long? = null) {
    val context = LocalContext.current
    val conversationPreferences = context.getSharedPreferences("conversations", Context.MODE_PRIVATE)
    val chatPreferences = context.getSharedPreferences("chat_settings", Context.MODE_PRIVATE)
    val aiSettingsPreferences = context.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)

    // 初始化管理器
    val conversationManager = remember { ConversationManager(context) }
    val aiCommunicationManager = remember { AICommunicationManager(context) }
    val taskStatusManager = remember { TaskStatusManager(context) }

    var currentConversation by remember { mutableStateOf<Conversation?>(null) }
    // 只加载对话列表的元数据，不加载所有消息内容
    var conversationsMetadata by remember { mutableStateOf<List<ConversationMetadata>>(conversationManager.loadConversationsMetadata()) }
    var userInput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var showClearChatDialog by remember { mutableStateOf(false) }

    // 聊天背景相关状态
    var backgroundImageUri by remember {
        mutableStateOf(
            chatPreferences.getString("chat_background_uri", "") ?: ""
        )
    }
    var backgroundImageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    // AI设置状态，用于实时更新
    var baseUrl by remember { mutableStateOf(aiSettingsPreferences.getString("base_url", "https://api.siliconflow.cn/v1") ?: "https://api.siliconflow.cn/v1") }
    var apiKey by remember { mutableStateOf(aiSettingsPreferences.getString("api_key", "sk-dplqbsomzrruqpclshnwysqlmhpdbylvfxbnvtvwygvsvfaz") ?: "sk-dplqbsomzrruqpclshnwysqlmhpdbylvfxbnvtvwygvsvfaz") }
    var modelName by remember { mutableStateOf(aiSettingsPreferences.getString("model_name", "THUDM/glm-4-9b-chat") ?: "THUDM/glm-4-9b-chat") }
    var systemPrompt by remember { mutableStateOf(aiSettingsPreferences.getString("system_prompt", "你是一个乐于助人的AI助手") ?: "你是一个乐于助人的AI助手") }
    var temperature by remember { mutableStateOf(aiSettingsPreferences.getFloat("temperature", 0.7f)) }
    var maxTokens by remember { mutableStateOf(aiSettingsPreferences.getInt("max_tokens", 2048)) }
    var conversationMemory by remember { mutableStateOf(aiSettingsPreferences.getInt("conversation_memory", 5)) }

    // 监听AI设置变化
    val aiSettingsListener = remember {
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "base_url" -> baseUrl = aiSettingsPreferences.getString("base_url", "https://api.siliconflow.cn/v1") ?: "https://api.siliconflow.cn/v1"
                "api_key" -> apiKey = aiSettingsPreferences.getString("api_key", "sk-dplqbsomzrruqpclshnwysqlmhpdbylvfxbnvtvwygvsvfaz") ?: "sk-dplqbsomzrruqpclshnwysqlmhpdbylvfxbnvtvwygvsvfaz"
                "model_name" -> modelName = aiSettingsPreferences.getString("model_name", "THUDM/glm-4-9b-chat") ?: "THUDM/glm-4-9b-chat"
                "system_prompt" -> systemPrompt = aiSettingsPreferences.getString("system_prompt", "你是一个乐于助人的AI助手") ?: "你是一个乐于助人的AI助手"
                "temperature" -> temperature = aiSettingsPreferences.getFloat("temperature", 0.7f)
                "max_tokens" -> maxTokens = aiSettingsPreferences.getInt("max_tokens", 2048)
                "conversation_memory" -> conversationMemory = aiSettingsPreferences.getInt("conversation_memory", 5)
            }
        }
    }

    // 注册和注销AI设置监听器
    DisposableEffect(Unit) {
        aiSettingsPreferences.registerOnSharedPreferenceChangeListener(aiSettingsListener)
        onDispose {
            aiSettingsPreferences.unregisterOnSharedPreferenceChangeListener(aiSettingsListener)
        }
    }

    // 加载背景图片
    LaunchedEffect(backgroundImageUri) {
        if (backgroundImageUri.isNotEmpty()) {
            try {
                // 先尝试从内部存储加载
                val internalFile = File(context.filesDir, backgroundImageUri)
                if (internalFile.exists()) {
                    val bitmap = android.graphics.BitmapFactory.decodeFile(internalFile.absolutePath)
                    backgroundImageBitmap = bitmap.asImageBitmap()
                } else {
                    // 否则尝试从URI加载
                    val uri = Uri.parse(backgroundImageUri)
                    val inputStream = context.contentResolver.openInputStream(uri)
                    inputStream?.use { stream ->
                        val bitmap = android.graphics.BitmapFactory.decodeStream(stream)
                        backgroundImageBitmap = bitmap.asImageBitmap()
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatScreen", "加载背景图片失败", e)
            }
        } else {
            backgroundImageBitmap = null
        }
    }

    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // 初始化对话
    LaunchedEffect(initialConversationId, conversationsMetadata) {
        // 简化对话初始化逻辑
        if (initialConversationId != null) {
            // 从历史记录加载指定对话
            currentConversation = conversationManager.loadConversationById(initialConversationId)

            // 如果找不到指定对话，创建一个新的
            if (currentConversation == null) {
                currentConversation = Conversation(
                    id = initialConversationId,
                    title = "新星轨",
                    messages = emptyList(),
                    timestamp = org.threeten.bp.LocalDateTime.now()
                )
            }
        } else if (currentConversation == null && conversationsMetadata.isNotEmpty()) {
            // 加载最新的对话
            val latestConversationId = conversationsMetadata.maxByOrNull { it.timestamp }?.id
            if (latestConversationId != null) {
                currentConversation = conversationManager.loadConversationById(latestConversationId)
            }
        } else if (currentConversation == null) {
            // 创建第一个对话
            currentConversation = Conversation(
                id = System.currentTimeMillis(),
                title = "新星轨",
                messages = emptyList(),
                timestamp = org.threeten.bp.LocalDateTime.now()
            )
        }
        
        // 记录对话打开时间，用于控制通知发送
        currentConversation?.id?.let { conversationId ->
            val notificationPrefs = context.getSharedPreferences("notification_tracker", Context.MODE_PRIVATE)
            notificationPrefs.edit()
                .putLong("conversation_${conversationId}_last_opened", System.currentTimeMillis())
                .apply()
        }
    }

    // 自动滚动到底部
    LaunchedEffect(currentConversation?.messages?.size) {
        coroutineScope.launch {
            if (currentConversation?.messages?.isNotEmpty() == true) {
                lazyListState.animateScrollToItem(currentConversation!!.messages.size - 1)
            }
        }
    }

    // 添加消息监听器
    val messageListener = remember {
        object : SharedPreferences.OnSharedPreferenceChangeListener {
            override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
                if (key?.startsWith("conversation_") == true && key.endsWith("_message_count")) {
                    currentConversation?.id?.let { conversationId ->
                        val updatedConversation = conversationManager.loadConversationById(conversationId)
                        if (updatedConversation != null) {
                            coroutineScope.launch {
                                withContext(Dispatchers.Main) {
                                    currentConversation = updatedConversation
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 注册和注销监听器
    DisposableEffect(Unit) {
        conversationPreferences.registerOnSharedPreferenceChangeListener(messageListener)
        onDispose {
            conversationPreferences.unregisterOnSharedPreferenceChangeListener(messageListener)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { }, // 空标题，因为我们将在内容区域自定义标题
                colors = TopAppBarDefaults.smallTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                ),
                navigationIcon = {
                    IconButton(onClick = { navController.navigate("conversation_history") {
                        popUpTo("conversation_history") { inclusive = false }
                    } }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        }
    ) { padding ->
            // 清空聊天记录确认对话框
            if (showClearChatDialog) {
                AlertDialog(
                    onDismissRequest = { showClearChatDialog = false },
                    title = { Text("清空聊天记录") },
                    text = { Text("确定要清空当前对话的所有消息吗？此操作无法撤销。") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                // 清空当前对话的消息
                                currentConversation?.id?.let { conversationId ->
                                    clearConversationMessagesInChat(context, conversationId)
                                    // 更新当前对话状态
                                    currentConversation = currentConversation?.copy(messages = emptyList())
                                    // 更新元数据
                                    conversationsMetadata = conversationManager.loadConversationsMetadata()
                                }
                                showClearChatDialog = false
                            }
                        ) {
                            Text("确认")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showClearChatDialog = false }
                        ) {
                            Text("取消")
                        }
                    }
                )
            }
            // 如果有背景图片，作为全屏背景显示
            if (backgroundImageBitmap != null) {
                Image(
                    bitmap = backgroundImageBitmap!!,
                    contentDescription = "聊天背景",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
            // 标题行 - 在顶部栏下方显示
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(16.dp)
                                .padding(end = 4.dp)
                        )
                        Text(
                            text = currentConversation?.title ?: "星语助手",
                            fontWeight = FontWeight.Medium,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    // 如果有当前对话，显示消息数量
                    currentConversation?.let { conversation ->
                        if (conversation.messages.isNotEmpty()) {
                            Text(
                                text = " (${conversation.messages.size}条星语)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                    
                    // 添加清空聊天记录按钮
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = {
                            // 显示确认对话框
                            showClearChatDialog = true
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "清空星语记录",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            // 消息显示区域
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
                state = lazyListState,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(currentConversation?.messages ?: emptyList()) { message ->
                    ChatMessageItem(message = message)
                }

                if (isLoading) {
                    item {
                        ChatMessageItem(
                            message = Message(
                                id = System.currentTimeMillis(),
                                text = "正在思考...",
                                isUser = false,
                                timestamp = org.threeten.bp.LocalDateTime.now()
                            )
                        )
                    }
                }
            }

            // 状态选择器区域 - 固定在输入框上方
            var currentTaskStatus by remember(currentConversation?.id, conversationsMetadata) { 
                mutableStateOf(
                    getConversationTaskStatus(context, currentConversation?.id ?: -1) ?: "not_started"
                )
            }

            // 状态选择器
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "状态:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                // 未开始状态按钮
                FilterChip(
                    selected = currentTaskStatus == "not_started",
                    onClick = {
                        currentTaskStatus = "not_started"
                        setConversationTaskStatus(context, currentConversation?.id ?: -1, "not_started")
                        // 更新元数据列表以反映状态变化
                        conversationsMetadata = conversationManager.loadConversationsMetadata()
                    },
                    label = { Text("未开始", style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.height(32.dp)
                )

                // 正在努力状态按钮
                FilterChip(
                    selected = currentTaskStatus == "working",
                    onClick = {
                        currentTaskStatus = "working"
                        setConversationTaskStatus(context, currentConversation?.id ?: -1, "working")
                        // 更新元数据列表以反映状态变化
                        conversationsMetadata = conversationManager.loadConversationsMetadata()
                    },
                    label = { Text("正在努力", style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.height(32.dp)
                )

                // 已完成状态按钮
                FilterChip(
                    selected = currentTaskStatus == "completed",
                    onClick = {
                        currentTaskStatus = "completed"
                        setConversationTaskStatus(context, currentConversation?.id ?: -1, "completed")
                        // 更新元数据列表以反映状态变化
                        conversationsMetadata = conversationManager.loadConversationsMetadata()
                    },
                    label = { Text("已完成", style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.height(32.dp)
                )
            }

            // 输入区域
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // 传统输入区域
                ChatInputArea(
                    userInput = userInput,
                    onUserInputChanged = { userInput = it },
                    onSendMessage = {
                        if (userInput.isNotBlank() && !isLoading) {
                            // 添加用户消息
                            val userMessage = Message(
                                id = System.currentTimeMillis(),
                                text = userInput,
                                isUser = true,
                                timestamp = org.threeten.bp.LocalDateTime.now()
                            )

                            val updatedMessages = (currentConversation?.messages ?: emptyList()) + userMessage
                            val updatedConversation = currentConversation?.copy(
                                messages = updatedMessages,
                                title = if (currentConversation?.title == "新星轨" && userInput.length <= 20) {
                                    userInput
                                } else {
                                    currentConversation?.title ?: "新星轨"
                                },
                                timestamp = org.threeten.bp.LocalDateTime.now()
                            )
                            currentConversation = updatedConversation

                            // 清空输入框
                            userInput = ""
                            isLoading = true

                            // 发送消息到AI并获取响应
                            coroutineScope.launch {
                                try {
                                    val aiResponse = aiCommunicationManager.sendToAI(
                                        updatedMessages,
                                        null // 统统使用全局设置
                                    )

                                    // 添加AI响应
                                    val aiMessage = Message(
                                        id = System.currentTimeMillis(),
                                        text = aiResponse,
                                        isUser = false,
                                        timestamp = org.threeten.bp.LocalDateTime.now()
                                    )

                                    val finalMessages = updatedMessages + aiMessage
                                    val finalConversation = updatedConversation?.copy(messages = finalMessages)
                                    currentConversation = finalConversation

                                    // 保存对话（只保存当前对话）
                                    if (finalConversation != null) {
                                        conversationManager.saveSingleConversationSync(finalConversation)
                                        // 更新元数据列表
                                        conversationsMetadata = conversationManager.loadConversationsMetadata()

                                        // 通知发送已移至ConversationManager中统一处理
                                        // 发送广播通知对话历史界面更新
                                        val intent = android.content.Intent("com.example.bestplannner.CONVERSATION_UPDATED")
                                        intent.putExtra("conversation_id", finalConversation.id)
                                        intent.putExtra("message_count", finalConversation.messages.size)
                                        context.sendBroadcast(intent)
                                    }
                                } catch (e: Exception) {
                                    val errorMessageText = when (e) {
                                        is java.net.SocketTimeoutException -> "请求超时，请稍后重试"
                                        is org.json.JSONException -> "响应解析错误，请稍后重试"
                                        else -> "发送消息时出错: ${e.message ?: "未知错误"}"
                                    }

                                    val errorMessage = Message(
                                        id = System.currentTimeMillis(),
                                        text = errorMessageText,
                                        isUser = false,
                                        timestamp = org.threeten.bp.LocalDateTime.now()
                                    )
                                    val finalMessages = (currentConversation?.messages ?: emptyList()) + errorMessage
                                    val finalConversation = currentConversation?.copy(messages = finalMessages)
                                    currentConversation = finalConversation

                                    // 保存对话（只保存当前对话）
                                    if (finalConversation != null) {
                                        conversationManager.saveSingleConversationSync(finalConversation)
                                        // 更新元数据列表
                                        conversationsMetadata = conversationManager.loadConversationsMetadata()

                                        // 通知发送已移至ConversationManager中统一处理
                                    }
                                } finally {
                                    isLoading = false
                                }
                            }
                        }
                    },
                    isEnabled = !isLoading
                )
            }
        }
    }
}

// 获取对话任务状态
fun getConversationTaskStatus(context: Context, conversationId: Long): String? {
    if (conversationId == -1L) return "not_started"
    
    // 首先检查对话任务状态
    val prefs = context.getSharedPreferences("conversation_task_status", Context.MODE_PRIVATE)
    val taskStatus = prefs.getString("conversation_$conversationId", null)
    if (taskStatus != null) {
        return taskStatus
    }
    
    // 如果没有设置任务状态，尝试从计划状态获取
    val planId = findPlanIdByConversation(context, conversationId)
    if (planId != -1) {
        val planStatusPrefs = context.getSharedPreferences("plan_status", Context.MODE_PRIVATE)
        val completed = planStatusPrefs.getBoolean("plan_${planId}_completed", false)
        if (completed) {
            return "completed"
        }
        return planStatusPrefs.getString("plan_${planId}_status", "not_started") ?: "not_started"
    }
    
    return "not_started"
}

// 设置对话任务状态
fun setConversationTaskStatus(context: Context, conversationId: Long, status: String) {
    if (conversationId == -1L) return
    
    val prefs = context.getSharedPreferences("conversation_task_status", Context.MODE_PRIVATE)
    prefs.edit().putString("conversation_$conversationId", status).apply()
    
    // 同时更新计划状态（如果对话关联了计划）
    updatePlanStatusForConversation(context, conversationId, status)
    
    // 同时更新对话状态（用于历史记录分类）
    val conversationStatusPrefs = context.getSharedPreferences("conversation_status", Context.MODE_PRIVATE)
    conversationStatusPrefs.edit().putString("conversation_${conversationId}_status", status).apply()
}

// 更新对话关联计划的状态
private fun updatePlanStatusForConversation(context: Context, conversationId: Long, status: String) {
    val conversationPreferences = context.getSharedPreferences("conversations", Context.MODE_PRIVATE)
    val planStatusPrefs = context.getSharedPreferences("plan_status", Context.MODE_PRIVATE)
    
    // 查找对话关联的计划
    val conversationCount = conversationPreferences.getInt("conversation_count", 0)
    for (i in 0 until conversationCount) {
        val id = conversationPreferences.getLong("conversation_${i}_id", i.toLong())
        if (id == conversationId) {
            val planId = conversationPreferences.getLong("conversation_${i}_plan_id", -1)
            if (planId != -1L) {
                // 更新计划状态
                planStatusPrefs.edit()
                    .putString("plan_${planId}_status", status)
                    .apply()
                
                // 如果是完成状态，也更新completed标志
                if (status == "completed") {
                    planStatusPrefs.edit()
                        .putBoolean("plan_${planId}_completed", true)
                        .apply()
                }
                
                // 同时更新对话任务状态，确保状态同步
                val conversationTaskStatusPrefs = context.getSharedPreferences("conversation_task_status", Context.MODE_PRIVATE)
                conversationTaskStatusPrefs.edit().putString("conversation_${conversationId}", status).apply()
            }
            return
        }
    }
}

@Composable
fun ChatMessageItem(message: Message,
                   onWorkingClicked: (() -> Unit)? = null,
                   onNotStartedClicked: (() -> Unit)? = null,
                   onCompletedClicked: (() -> Unit)? = null,
                   taskStatus: String? = null) {
    val context = LocalContext.current
    val chatPreferences = context.getSharedPreferences("chat_settings", Context.MODE_PRIVATE)
    var chatBubbleFontSize by remember { mutableStateOf(chatPreferences.getFloat("chat_bubble_font_size", 16f)) }

    // 监听设置变化
    androidx.compose.runtime.DisposableEffect(Unit) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "chat_bubble_font_size") {
                chatBubbleFontSize = chatPreferences.getFloat("chat_bubble_font_size", 16f)
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
            colors = if (message.isUser) {
                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
            } else {
                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f))
            }
        ) {
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
                        style = androidx.compose.ui.text.TextStyle(
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
                            contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                        ) {
                            Text("未开始")
                        }

                        // 正在努力按钮
                        Button(
                            onClick = { onWorkingClicked?.invoke() },
                            modifier = Modifier.weight(1f),
                            contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                        ) {
                            Text("正在努力")
                        }

                        // 已完成按钮
                        Button(
                            onClick = { onCompletedClicked?.invoke() },
                            modifier = Modifier.weight(1f),
                            contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                        ) {
                            Text("已完成")
                        }
                    }
                }

                Text(
                    text = message.timestamp.format(org.threeten.bp.format.DateTimeFormatter.ofPattern("MM-dd HH:mm")),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatInputArea(
    userInput: String,
    onUserInputChanged: (String) -> Unit,
    onSendMessage: () -> Unit,
    isEnabled: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = userInput,
            onValueChange = onUserInputChanged,
            modifier = Modifier
                .weight(1f),
            placeholder = { Text("输入消息...") },
            enabled = isEnabled,
            singleLine = false,
            maxLines = 3
        )

        IconButton(
            onClick = onSendMessage,
            enabled = isEnabled && userInput.isNotBlank()
        ) {
            Icon(imageVector = Icons.Default.Send, contentDescription = "发送")
        }
    }
}

/**
 * 根据对话标题查找对应的计划ID
 */
fun findPlanIdByConversationTitle(context: Context, conversationTitle: String): Int {
    val planPrefs = context.getSharedPreferences("plans", Context.MODE_PRIVATE)
    val planCount = planPrefs.getInt("plan_count", 0)

    for (i in 0 until planCount) {
        val title = planPrefs.getString("plan_${i}_title", "") ?: ""
        if (title == conversationTitle) {
            return planPrefs.getInt("plan_${i}_id", -1)
        }
    }

    // 尝试通过对话ID查找计划ID
    val conversationPrefs = context.getSharedPreferences("conversations", Context.MODE_PRIVATE)
    val conversationCount = conversationPrefs.getInt("conversation_count", 0)

    for (i in 0 until conversationCount) {
        val conversationTitleStored = conversationPrefs.getString("conversation_${i}_title", "") ?: ""
        if (conversationTitleStored == conversationTitle) {
            val planId = conversationPrefs.getLong("conversation_${i}_plan_id", -1)
            return planId.toInt()
        }
    }

    return -1
}

/**
 * 记录用户发送消息的时间
 */
fun recordUserMessageSent(context: Context, planId: Int) {
    val notificationPrefs = context.getSharedPreferences("notification_tracker", Context.MODE_PRIVATE)
    with(notificationPrefs.edit()) {
        putLong("plan_${planId}_last_user_message", System.currentTimeMillis())
        apply()
    }
}

/**
 * 清空对话消息的函数（用于聊天界面）
 */
fun clearConversationMessagesInChat(context: Context, conversationId: Long) {
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
        Log.e("ChatScreen", "清空对话消息时出错", e)
    }
}

/**
 * 处理"正在努力"状态
 */
fun handleWorkingOnIt(context: Context, conversation: Conversation?, message: Message) {
    if (conversation == null) return

    // 更新消息状态为"正在努力"
    setMessageTaskStatus(context, message.id, "working")

    // 触发通知系统按照"正在努力"状态的逻辑发送提醒
    // 具体的提醒逻辑由通知控制中心处理，这里只需要设置状态
}

/**
 * 启动周期性提醒机制
 */
fun startPeriodicReminding(context: Context, planId: Int) {
    val settingsPreferences = context.getSharedPreferences("planner_settings", Context.MODE_PRIVATE)
    val notificationInterval = settingsPreferences.getInt("notification_interval", 15) // 默认15分钟

    // 如果设置为持续提醒（值为0）或设置了提醒间隔，则启动提醒
    if (notificationInterval == 0 || notificationInterval > 0) {
        // 创建一个后台任务来处理周期性提醒
        val data = Data.Builder()
            .putInt("plan_id", planId)
            .putInt("notification_interval", notificationInterval)
            .build()

        // 使用统一的ReminderManager来处理提醒
        val autoReminderManager = com.example.bestplannner.notification.ReminderManager.getInstance(context)
        // 这里可以添加启动自动提醒的逻辑
        Log.d("ChatScreen", "启动自动提醒，计划ID: $planId")
    }
}

/**
 * 处理用户点击"已完成"按钮的逻辑
 */
fun handleTaskCompleted(context: Context, conversation: Conversation?, navController: NavController, conversationManager: ConversationManager) {
    if (conversation == null) return

    // 更新消息状态为"已完成"
    val lastAiMessage = conversation.messages.lastOrNull { !it.isUser }
    if (lastAiMessage != null) {
        setMessageTaskStatus(context, lastAiMessage.id, "completed")
    }

    // 查找与对话关联的计划
    val conversationId = conversation.id
    val planId = findPlanIdByConversation(context, conversationId)

    if (planId != -1) {
        // 更新计划状态为已完成
        val planStatusPrefs = context.getSharedPreferences("plan_status", Context.MODE_PRIVATE)
        planStatusPrefs.edit()
            .putString("plan_${planId}_status", "completed")
            .putBoolean("plan_${planId}_completed", true)
            .apply()
        
        // 同时更新对话任务状态，确保状态同步
        val conversationTaskStatusPrefs = context.getSharedPreferences("conversation_task_status", Context.MODE_PRIVATE)
        conversationTaskStatusPrefs.edit().putString("conversation_${conversationId}", "completed").apply()

        // 停止该计划的自动提醒
        val autoReminderManager = com.example.bestplannner.notification.ReminderManager.getInstance(context)
        autoReminderManager.cancelReminder(conversationId)

        // 添加系统消息到对话中
        val completedMessage = Message(
            id = System.currentTimeMillis(),
            text = "恭喜！任务已完成。",
            isUser = false,
            timestamp = org.threeten.bp.LocalDateTime.now()
        )

        val updatedMessages = (conversation.messages) + completedMessage
        val updatedConversation = conversation.copy(
            messages = updatedMessages,
            timestamp = org.threeten.bp.LocalDateTime.now()
        )

        conversationManager.saveSingleConversationSync(updatedConversation)

        // 不再删除计划和对话，只更新状态
        // navController.navigate("plan") {
        //     popUpTo("chat") { inclusive = true }
        // }
    }
}

/**
 * 根据计划ID加载计划项
 */
fun loadPlanById(context: Context, planId: Int): com.example.bestplannner.data.PlanItem? {
    val planPrefs = context.getSharedPreferences("plans", Context.MODE_PRIVATE)
    val planCount = planPrefs.getInt("plan_count", 0)

    for (i in 0 until planCount) {
        val id = planPrefs.getInt("plan_${i}_id", i)
        if (id == planId) {
            val title = planPrefs.getString("plan_${i}_title", "") ?: ""
            val description = planPrefs.getString("plan_${i}_description", "") ?: ""
            val dayValue = planPrefs.getInt("plan_${i}_day", 1)  // 默认值改为1（星期一）
            val startTime = planPrefs.getString("plan_${i}_startTime", "09:00") ?: "09:00"
            val endTime = planPrefs.getString("plan_${i}_endTime", startTime) ?: startTime
            val isDaily = planPrefs.getBoolean("plan_${i}_isDaily", false)

            val dayOfWeek = org.threeten.bp.DayOfWeek.of(dayValue)

            return com.example.bestplannner.data.PlanItem(id, dayOfWeek, title, description, startTime, endTime, isDaily)
        }
    }

    return null
}

/**
 * 根据计划ID删除计划项
 */
fun deletePlanById(context: Context, planId: Int) {
    val planPrefs = context.getSharedPreferences("plans", Context.MODE_PRIVATE)
    val editor = planPrefs.edit()

    val planCount = planPrefs.getInt("plan_count", 0)
    var foundIndex = -1

    // 查找要删除的计划索引
    for (i in 0 until planCount) {
        val id = planPrefs.getInt("plan_${i}_id", i)
        if (id == planId) {
            foundIndex = i
            break
        }
    }

    // 如果找到了要删除的计划
    if (foundIndex != -1) {
        // 删除该计划的所有数据
        editor.remove("plan_${foundIndex}_id")
        editor.remove("plan_${foundIndex}_title")
        editor.remove("plan_${foundIndex}_description")
        editor.remove("plan_${foundIndex}_day")
        editor.remove("plan_${foundIndex}_startTime")
        editor.remove("plan_${foundIndex}_endTime")
        editor.remove("plan_${foundIndex}_isDaily")

        // 将后续计划前移
        for (i in foundIndex until planCount - 1) {
            val nextId = planPrefs.getInt("plan_${i + 1}_id", i + 1)
            val nextTitle = planPrefs.getString("plan_${i + 1}_title", "") ?: ""
            val nextDescription = planPrefs.getString("plan_${i + 1}_description", "") ?: ""
            val nextDay = planPrefs.getInt("plan_${i + 1}_day", 1)
            val nextStartTime = planPrefs.getString("plan_${i + 1}_startTime", "09:00") ?: "09:00"
            val nextEndTime = planPrefs.getString("plan_${i + 1}_endTime", nextStartTime) ?: nextStartTime
            val nextIsDaily = planPrefs.getBoolean("plan_${i + 1}_isDaily", false)

            editor.putInt("plan_${i}_id", nextId)
            editor.putString("plan_${i}_title", nextTitle)
            editor.putString("plan_${i}_description", nextDescription)
            editor.putInt("plan_${i}_day", nextDay)
            editor.putString("plan_${i}_startTime", nextStartTime)
            editor.putString("plan_${i}_endTime", nextEndTime)
            editor.putBoolean("plan_${i}_isDaily", nextIsDaily)
        }

        // 减少计划计数
        editor.putInt("plan_count", planCount - 1)
        editor.apply()
    }
}

/**
 * 根据对话ID查找关联的计划ID
 */
fun findPlanIdByConversation(context: Context, conversationId: Long): Int {
    if (conversationId == -1L) return -1

    val conversationPreferences = context.getSharedPreferences("conversations", Context.MODE_PRIVATE)
    try {
        val conversationCount = conversationPreferences.getInt("conversation_count", 0)

        for (i in 0 until conversationCount) {
            val id = conversationPreferences.getLong("conversation_${i}_id", i.toLong())
            if (id == conversationId) {
                return conversationPreferences.getLong("conversation_${i}_plan_id", -1).toInt()
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    return -1
}

/**
 * 获取消息的任务状态
 */
fun getMessageTaskStatus(context: Context, message: Message): String? {
    val prefs = context.getSharedPreferences("message_task_status", Context.MODE_PRIVATE)
    return prefs.getString("message_${message.id}", if (message.isUser) null else "not_started")
}

/**
 * 设置消息的任务状态
 */
fun setMessageTaskStatus(context: Context, messageId: Long, status: String) {
    val prefs = context.getSharedPreferences("message_task_status", Context.MODE_PRIVATE)
    prefs.edit().putString("message_$messageId", status).apply()
}

/**
 * 处理"未开始"状态
 */
fun handleNotStarted(context: Context, conversation: Conversation?, message: Message) {
    if (conversation == null) return

    // 更新消息状态为"未开始"
    setMessageTaskStatus(context, message.id, "not_started")

    // 触发通知系统按照"未开始"状态的逻辑发送提醒
    // 具体的提醒逻辑由通知控制中心处理，这里只需要设置状态
}

/**
 * 将消息添加到对话中
 */
private fun addMessageToConversation(context: Context, conversation: Conversation, message: Message, conversationManager: ConversationManager) {
    val updatedMessages = conversation.messages + message
    val updatedConversation = conversation.copy(
        messages = updatedMessages,
        timestamp = org.threeten.bp.LocalDateTime.now()
    )

    conversationManager.saveSingleConversationSync(updatedConversation)

    // 通知发送已移至ConversationManager中统一处理
}

/**
 * 查找当前对话ID
 */
private fun findCurrentConversationId(context: android.content.Context, messages: List<Message>): Long? {
    // 通过SharedPreferences查找与消息列表匹配的对话
    val conversationPreferences = context.getSharedPreferences("conversations", Context.MODE_PRIVATE)
    val conversationCount = conversationPreferences.getInt("conversation_count", 0)

    // 首先尝试通过消息ID匹配查找对话
    for (i in 0 until conversationCount) {
        val messageCount = conversationPreferences.getInt("conversation_${i}_message_count", 0)
        if (messageCount > 0) {
            // 检查最近的消息ID是否匹配
            val lastMessageId = conversationPreferences.getLong("conversation_${i}_message_${messageCount - 1}_id", -1)
            if (messages.isNotEmpty() && messages.last().id == lastMessageId) {
                return conversationPreferences.getLong("conversation_${i}_id", i.toLong())
            }
        }
    }

    // 如果通过消息ID无法匹配，则尝试通过当前对话对象查找
    // 这里需要在调用此函数时传入当前对话的引用，或者通过其他方式获取
    return null
}