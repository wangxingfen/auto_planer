package com.example.bestplannner.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.material3.RadioButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.navigation.NavController
import androidx.work.WorkManager
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONException
import org.json.JSONObject
import java.io.*
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.UUID
import java.util.concurrent.TimeUnit
import com.example.bestplannner.data.PlanItem
import org.threeten.bp.DayOfWeek

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController, conversationId: Long? = null) {
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)
    val plannerPreferences = context.getSharedPreferences("planner_settings", Context.MODE_PRIVATE)
    val chatPreferences = context.getSharedPreferences("chat_settings", Context.MODE_PRIVATE)

    // 统一使用全局设置
    val settingsKeyPrefix = ""
    // 初始化并保存默认值到SharedPreferences
    LaunchedEffect(Unit) {
        var shouldSave = false
        val editor = sharedPreferences.edit()
        
        if (!sharedPreferences.contains("${settingsKeyPrefix}base_url")) {
            editor.putString("${settingsKeyPrefix}base_url", "https://api.siliconflow.cn/v1")
            shouldSave = true
        }
        
        if (!sharedPreferences.contains("${settingsKeyPrefix}api_key")) {
            editor.putString("${settingsKeyPrefix}api_key", "sk-dplqbsomzrruqpclshnwysqlmhpdbylvfxbnvtvwygvsvfaz")
            shouldSave = true
        }
        
        if (!sharedPreferences.contains("${settingsKeyPrefix}model_name")) {
            editor.putString("${settingsKeyPrefix}model_name", "THUDM/glm-4-9b-chat")
            shouldSave = true
        }
        
        if (!sharedPreferences.contains("${settingsKeyPrefix}system_prompt")) {
            editor.putString("${settingsKeyPrefix}system_prompt", "请认真扮演一位帮助用户达成计划的占星师")
            shouldSave = true
        }
        
        if (!sharedPreferences.contains("${settingsKeyPrefix}temperature")) {
            editor.putFloat("${settingsKeyPrefix}temperature", 0.7f)
            shouldSave = true
        }
        
        if (!sharedPreferences.contains("${settingsKeyPrefix}max_tokens")) {
            editor.putInt("${settingsKeyPrefix}max_tokens", 2048)
            shouldSave = true
        }
        
        if (!sharedPreferences.contains("${settingsKeyPrefix}conversation_memory")) {
            editor.putInt("${settingsKeyPrefix}conversation_memory", 5)
            shouldSave = true
        }
        
        // 保存设置如果需要的话
        if (shouldSave) {
            editor.apply()
        }
        
        // 同样处理通知设置
        var shouldSavePlanner = false
        val plannerEditor = plannerPreferences.edit()
        
        if (!plannerPreferences.contains("periodic_check_interval")) {
            plannerEditor.putInt("periodic_check_interval", 5) // 默认5分钟
            shouldSavePlanner = true
        }
        
        // 添加默认通知设置
        if (!plannerPreferences.contains("notifications_enabled")) {
            plannerEditor.putBoolean("notifications_enabled", true)
            shouldSavePlanner = true
        }
        
        if (shouldSavePlanner) {
            plannerEditor.apply()
        }
        
        // 同样处理聊天设置
        var shouldSaveChat = false
        val chatEditor = chatPreferences.edit()
        
        if (!chatPreferences.contains("chat_bubble_font_size")) {
            chatEditor.putFloat("chat_bubble_font_size", 12f) // 默认12sp(最小)
            shouldSaveChat = true
        }
        
        if (shouldSaveChat) {
            chatEditor.apply()
        }
    }

    var baseUrl by remember {
        mutableStateOf(sharedPreferences.getString("${settingsKeyPrefix}base_url", "https://api.siliconflow.cn/v1") ?: "https://api.siliconflow.cn/v1")
    }

    var apiKey by remember {
        mutableStateOf(sharedPreferences.getString("${settingsKeyPrefix}api_key", "sk-dplqbsomzrruqpclshnwysqlmhpdbylvfxbnvtvwygvsvfaz") ?: "sk-dplqbsomzrruqpclshnwysqlmhpdbylvfxbnvtvwygvsvfaz")
    }

    var modelName by remember {
        mutableStateOf(sharedPreferences.getString("${settingsKeyPrefix}model_name", "THUDM/glm-4-9b-chat") ?: "THUDM/glm-4-9b-chat")
    }

    var systemPrompt by remember {
        mutableStateOf(sharedPreferences.getString("${settingsKeyPrefix}system_prompt", "请认真扮演一位帮助用户达成计划的占星师") ?: "请认真扮演一位帮助用户达成计划的占星师")
    }

    var temperature by remember {
        mutableStateOf(sharedPreferences.getFloat("${settingsKeyPrefix}temperature", 0.7f))
    }

    var maxTokens by remember {
        mutableStateOf(sharedPreferences.getInt("${settingsKeyPrefix}max_tokens", 2048))
    }

    var conversationMemory by remember {
        mutableStateOf(sharedPreferences.getInt("${settingsKeyPrefix}conversation_memory", 5))
    }

    // 添加周期性检查间隔
    var periodicCheckInterval by remember {
        mutableStateOf<Int>(
            plannerPreferences.getInt("periodic_check_interval", 5)
        )
    }
    
    // 添加通知开关状态
    var notificationsEnabled by remember {
        mutableStateOf<Boolean>(
            plannerPreferences.getBoolean("notifications_enabled", true)
        )
    }
    
    // 聊天界面字体大小
    var chatBubbleFontSize by remember {
        mutableStateOf<Float>(
            chatPreferences.getFloat("chat_bubble_font_size", 12f)
        )
    }
    
    val aiSettings = context.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)

    var isFetchingModels by remember { mutableStateOf(false) }
    var availableModels by remember { mutableStateOf<List<String>>(emptyList()) }

    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .size(20.dp)
                                .padding(end = 8.dp)
                        )
                        Text(
                            text = "星轨设置",
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Base URL 设置
            SettingSection(title = "基础设置") {
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("基础URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API密钥") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Text(
                    text = "请点击此处跳转然后新建API密钥" +
                            "或者浏览器输入网址https://cloud.siliconflow.cn/i/ByXrxmTh",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://cloud.siliconflow.cn/i/ByXrxmTh"))
                            context.startActivity(intent)
                        }
                )

                Spacer(Modifier.height(8.dp))

                // 模型选择区域
                var expanded by remember { mutableStateOf(false) }
                
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { newExpanded ->
                        if (newExpanded && availableModels.isEmpty()) {
                            // 首次展开时获取模型列表
                            fetchModelList(baseUrl, apiKey, context) { models ->
                                availableModels = models
                                isFetchingModels = false
                                expanded = true // 确保获取模型后菜单仍然展开
                                
                                // 如果只有一个模型，自动选择它
                                if (models.size == 1) {
                                    modelName = models[0]
                                }
                            }
                            isFetchingModels = true
                        } else {
                            expanded = newExpanded
                        }
                    }
                ) {
                    OutlinedTextField(
                        value = modelName,
                        onValueChange = { modelName = it },
                        label = { Text("模型名称") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        singleLine = true,
                        readOnly = true,
                        trailingIcon = {
                            if (isFetchingModels) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                            }
                        }
                    )
                    
                    // 下拉菜单
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.heightIn(max = 300.dp) // 限制最大高度
                    ) {
                        if (availableModels.isEmpty() && !isFetchingModels) {
                            DropdownMenuItem(
                                text = { Text("无可用模型，请点击重新获取") },
                                onClick = {
                                    expanded = false
                                    fetchModelList(baseUrl, apiKey, context) { models ->
                                        availableModels = models
                                        isFetchingModels = false
                                        expanded = true // 获取模型后重新展开菜单
                                        
                                        // 如果只有一个模型，自动选择它
                                        if (models.size == 1) {
                                            modelName = models[0]
                                        }
                                    }
                                    isFetchingModels = true
                                }
                            )
                        } else if (isFetchingModels) {
                            DropdownMenuItem(
                                text = { 
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text("正在加载模型列表...")
                                    }
                                },
                                onClick = { }
                            )
                        } else {
                            availableModels.forEach { model ->
                                DropdownMenuItem(
                                    text = { Text(model) },
                                    onClick = {
                                        modelName = model
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                
                Text(
                    text = "点击模型名称获取可用模型列表",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    label = { Text("系统提示词") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    maxLines = 3
                )

                Spacer(Modifier.height(8.dp))

                // Temperature 设置
                Text("Temperature (0.0 - 2.0):")
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Slider(
                        value = temperature,
                        onValueChange = { temperature = it },
                        valueRange = 0f..2f,
                        steps = 19,
                        modifier = Modifier.weight(1f)
                    )

                    Text(
                        text = "%.1f".format(temperature),
                        modifier = Modifier.width(40.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Max Tokens 设置
                Text("最大输出字符数:")
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Slider(
                        value = maxTokens.toFloat(),
                        onValueChange = { maxTokens = it.toInt() },
                        valueRange = 100f..4096f,
                        steps = 3996,
                        modifier = Modifier.weight(1f)
                    )

                    Text(
                        text = "$maxTokens",
                        modifier = Modifier.width(60.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(Modifier.height(8.dp))

                // 对话记忆设置
                Text("对话记忆轮数:")
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Slider(
                        value = conversationMemory.toFloat(),
                        onValueChange = { conversationMemory = it.toInt() },
                        valueRange = 0f..20f,
                        steps = 19,
                        modifier = Modifier.weight(1f)
                    )

                    Text(
                        text = "$conversationMemory",
                        modifier = Modifier.width(40.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // 通知设置
            SettingSection(title = "通知设置") {
                // 添加全局通知开关
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("启用通知")
                    Switch(
                        checked = notificationsEnabled,
                        onCheckedChange = { notificationsEnabled = it }
                    )
                }
                
                Spacer(Modifier.height(8.dp))
                
                // 添加跳转到系统通知设置的按钮
                Button(
                    onClick = {
                        try {
                            val intent = Intent().apply {
                                action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                                
                                // 兼容不同Android版本的设置页面跳转
                                when {
                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    }
                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP -> {
                                        putExtra("app_package", context.packageName)
                                        putExtra("app_uid", context.applicationInfo.uid)
                                    }
                                    else -> {
                                        putExtra(Settings.EXTRA_CHANNEL_ID, context.packageName)
                                    }
                                }
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            // 如果无法跳转到特定应用的通知设置，则跳转到通用通知设置
                            try {
                                val intent = Intent(Settings.ACTION_SETTINGS)
                                context.startActivity(intent)
                            } catch (fallbackException: Exception) {
                                Toast.makeText(context, "无法打开通知设置页面", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("自定义通知设置")
                }
                
                Spacer(Modifier.height(8.dp))
                
                Text(
                    text = "开启后，系统将在适当时候发送通知提醒您查看新的对话内容。点击\"自定义通知设置\"按钮可以配置通知铃声、震动等选项。",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // 自动对话设置
            SettingSection(title = "自动对话设置") {
                Text("自动对话系统检查频率（分钟）:")

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Slider(
                        value = periodicCheckInterval.toFloat(),
                        onValueChange = { periodicCheckInterval = it.toInt().coerceAtLeast(1) },
                        valueRange = 1f..60f,
                        steps = 59,
                        modifier = Modifier.weight(1f)
                    )

                    Text(
                        text = "$periodicCheckInterval",
                        modifier = Modifier.width(40.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Text(
                    text = "每隔${periodicCheckInterval}分钟检查一次计划状态并生成对话",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "注意：检查频率最小为1分钟。",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // 聊天界面设置
            SettingSection(title = "聊天界面设置") {
                // 聊天气泡字体大小设置
                Text("聊天气泡字体大小:")
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Slider(
                        value = chatBubbleFontSize,
                        onValueChange = { 
                            chatBubbleFontSize = it
                            // 立即保存设置
                            chatPreferences.edit().putFloat("chat_bubble_font_size", it).apply()
                        },
                        valueRange = 12f..24f,
                        steps = 11,
                        modifier = Modifier.weight(1f)
                    )

                    Text(
                        text = "${chatBubbleFontSize.toInt()}sp",
                        modifier = Modifier.width(50.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 预览区域
                Text("预览效果:", style = MaterialTheme.typography.bodyMedium)
                
                // 用户消息预览
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Card(
                        modifier = Modifier.widthIn(max = 250.dp),
                        shape = RoundedCornerShape(8.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                    ) {
                        Text(
                            text = "这是一条用户消息示例",
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = TextStyle(
                                fontSize = chatBubbleFontSize.sp
                            )
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // AI消息预览
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    Card(
                        modifier = Modifier.widthIn(max = 250.dp),
                        shape = RoundedCornerShape(8.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                        )
                    ) {
                        Text(
                            text = "这是一条AI回复消息示例",
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            style = TextStyle(
                                fontSize = chatBubbleFontSize.sp
                            )
                        )
                    }
                }
            }

            // 聊天背景设置
            SettingSection(title = "聊天背景设置") {
                var backgroundImageUri by remember {
                    mutableStateOf(
                        chatPreferences.getString("chat_background_uri", "") ?: ""
                    )
                }

                var backgroundImageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

                // 如果有背景图片URI，加载图片
                LaunchedEffect(backgroundImageUri) {
                    if (backgroundImageUri.isNotEmpty()) {
                        try {
                            // 先尝试从内部存储加载
                            val internalFile = File(context.filesDir, backgroundImageUri)
                            if (internalFile.exists()) {
                                val bitmap = BitmapFactory.decodeFile(internalFile.absolutePath)
                                backgroundImageBitmap = bitmap.asImageBitmap()
                            } else {
                                // 否则尝试从URI加载
                                val uri = Uri.parse(backgroundImageUri)
                                val inputStream = context.contentResolver.openInputStream(uri)
                                inputStream?.use { stream ->
                                    val bitmap = BitmapFactory.decodeStream(stream)
                                    backgroundImageBitmap = bitmap.asImageBitmap()
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("SettingsScreen", "加载背景图片失败", e)
                        }
                    } else {
                        backgroundImageBitmap = null
                    }
                }

                // 图片选择器
                val imagePickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri: Uri? ->
                    uri?.let {
                        // 将图片保存到应用内部存储
                        try {
                            val inputStream = context.contentResolver.openInputStream(uri)
                            inputStream?.use { stream ->
                                // 生成唯一文件名
                                val fileName = "chat_background_${UUID.randomUUID()}.jpg"
                                val outputFile = File(context.filesDir, fileName)
                                
                                // 复制文件到内部存储
                                FileOutputStream(outputFile).use { outputStream ->
                                    stream.copyTo(outputStream)
                                }
                                
                                // 删除旧的背景图片文件
                                val oldFileName = chatPreferences.getString("chat_background_uri", "")
                                if (!oldFileName.isNullOrEmpty() && oldFileName != fileName) {
                                    val oldFile = File(context.filesDir, oldFileName)
                                    if (oldFile.exists()) {
                                        oldFile.delete()
                                    }
                                }
                                
                                // 保存文件名到SharedPreferences
                                chatPreferences.edit().putString("chat_background_uri", fileName).apply()
                                backgroundImageUri = fileName
                                Toast.makeText(context, "背景图片已设置并保存", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Log.e("SettingsScreen", "保存背景图片失败", e)
                            Toast.makeText(context, "保存背景图片失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 显示当前背景图片预览
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(9f / 16f) // 9:16 比例更符合手机竖屏
                            .clip(RoundedCornerShape(8.dp))
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline,
                                shape = RoundedCornerShape(8.dp)
                            )
                    ) {
                        if (backgroundImageBitmap != null) {
                            Image(
                                bitmap = backgroundImageBitmap!!,
                                contentDescription = "聊天背景预览",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "未设置背景图片",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 选择图片按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                imagePickerLauncher.launch("image/*")
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("选择图片")
                        }

                        // 清除背景按钮
                        if (backgroundImageUri.isNotEmpty()) {
                            OutlinedButton(
                                onClick = {
                                    // 删除背景图片文件
                                    val file = File(context.filesDir, backgroundImageUri)
                                    if (file.exists()) {
                                        file.delete()
                                    }
                                    
                                    chatPreferences.edit().remove("chat_background_uri").apply()
                                    backgroundImageUri = ""
                                    Toast.makeText(context, "背景图片已清除", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("清除背景")
                            }
                        }
                    }
                }
            }



            // 保存按钮
            Button(
                onClick = {
                    val editor = sharedPreferences.edit()
                    editor.putString("${settingsKeyPrefix}base_url", baseUrl)
                    editor.putString("${settingsKeyPrefix}api_key", apiKey)
                    editor.putString("${settingsKeyPrefix}model_name", modelName)
                    editor.putString("${settingsKeyPrefix}system_prompt", systemPrompt)
                    editor.putFloat("${settingsKeyPrefix}temperature", temperature)
                    editor.putInt("${settingsKeyPrefix}max_tokens", maxTokens)
                    editor.putInt("${settingsKeyPrefix}conversation_memory", conversationMemory)
                    editor.apply()

                    val plannerEditor = plannerPreferences.edit()
                    val oldCheckInterval = plannerPreferences.getInt("periodic_check_interval", 5)
                    plannerEditor.putInt("periodic_check_interval", periodicCheckInterval) // 保存周期性检查间隔
                    plannerEditor.putBoolean("notifications_enabled", notificationsEnabled) // 保存通知开关状态
                    plannerEditor.apply()
                    
                    // 如果检查间隔发生了变化，需要更新所有计划的周期性任务
                    if (oldCheckInterval != periodicCheckInterval) {
                        updatePeriodicChecksForAllPlans(context, periodicCheckInterval)
                    }
                    
                    // 更新通知渠道以应用新的设置
                    val reminderManager = com.example.bestplannner.notification.ReminderManager.getInstance(context)
                    reminderManager.createNotificationChannel()
                    
                    // 显示保存成功的消息
                    Toast.makeText(context, "设置已保存", Toast.LENGTH_SHORT).show()
                }
            ) {
                Text("保存设置")
            }
        }
    }
}

/**
 * 更新所有计划的周期性检查任务
 */
fun updatePeriodicChecksForAllPlans(context: Context, checkInterval: Int) {
    // 取消现有的所有计划周期性任务
    WorkManager.getInstance(context).cancelAllWorkByTag("periodic_plan_check")
    
    // 重新安排所有计划的周期性检查任务
    val plans = loadAllPlans(context)
    plans.forEach { plan ->
        // 为每个计划安排新的周期性检查任务
        val workRequest = PeriodicWorkRequestBuilder<com.example.bestplannner.worker.AutoConversationWorker>(
            checkInterval.toLong(), TimeUnit.MINUTES
        )
            .setInputData(
                Data.Builder()
                    .putInt("plan_id", plan.id)
                    .build()
            )
            .setInitialDelay(1, TimeUnit.MINUTES)
            .addTag("periodic_plan_check_${plan.id}")
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                "periodic_plan_check_${plan.id}",
                ExistingPeriodicWorkPolicy.REPLACE,
                workRequest
            )
    }
}

/**
 * 加载所有计划项
 */
fun loadAllPlans(context: Context): List<com.example.bestplannner.data.PlanItem> {
    val plans = mutableListOf<com.example.bestplannner.data.PlanItem>()
    val planPrefs = context.getSharedPreferences("plans", Context.MODE_PRIVATE)
    val planCount = planPrefs.getInt("plan_count", 0)

    for (i in 0 until planCount) {
        val id = planPrefs.getInt("plan_${i}_id", i)
        val title = planPrefs.getString("plan_${i}_title", "") ?: ""
        val description = planPrefs.getString("plan_${i}_description", "") ?: ""
        val dayValue = planPrefs.getInt("plan_${i}_day", 1)
        val startTime = planPrefs.getString("plan_${i}_startTime", "09:00") ?: "09:00"
        val endTime = planPrefs.getString("plan_${i}_endTime", startTime) ?: startTime
        val isDaily = planPrefs.getBoolean("plan_${i}_isDaily", false)

        val dayOfWeek = DayOfWeek.of(dayValue)

        plans.add(com.example.bestplannner.data.PlanItem(id, dayOfWeek, title, description, startTime, endTime, isDaily))
    }

    return plans
}

@Composable
fun SettingSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                content()
            }
        }
    }
}

fun fetchModelList(baseUrl: String, apiKey: String, context: Context, callback: (List<String>) -> Unit) {
    // 检查baseUrl是否为空
    if (baseUrl.isBlank()) {
        Toast.makeText(context, "错误：基础URL不能为空", Toast.LENGTH_LONG).show()
        callback(emptyList())
        return
    }

    val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    val requestBuilder = Request.Builder()
        .url("${baseUrl.trimEnd('/')}/models")
        .get()

    // 如果有API密钥，则添加到请求头
    if (apiKey.isNotEmpty()) {
        requestBuilder.addHeader("Authorization", "Bearer $apiKey")
    } else {
        // SiliconFlow 平台也接受直接使用 API-Key 头
        requestBuilder.addHeader("Accept", "application/json")
    }

    // 添加用户代理
    requestBuilder.addHeader("User-Agent", "BestPlanner/1.0")
    requestBuilder.addHeader("Content-Type", "application/json")

    val request = requestBuilder.build()

    CoroutineScope(Dispatchers.IO).launch {
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = try {
                        response.body?.string()
                    } catch (e: Exception) {
                        Log.e("SettingsScreen", "读取错误响应体时出错", e)
                        null
                    }

                    val errorMessage = when (response.code) {
                        400 -> "请求参数不正确，请检查设置参数是否符合要求"
                        401 -> "认证失败，请检查API密钥。请确保已在 SiliconFlow 平台 (https://cloud.siliconflow.cn/i/ByXrxmTh) 获取正确的API密钥"
                        403 -> "访问被拒绝，请检查API密钥和权限"
                        404 -> "请求的资源未找到"
                        429 -> "请求过于频繁，请稍后再试"
                        500 -> "服务器内部错误，请稍后再试"
                        503 -> "服务暂时不可用，请稍后再试"
                        else -> "获取模型失败: ${response.code}" +
                            if (!errorBody.isNullOrEmpty()) " ($errorBody)" else ""
                    }

                    CoroutineScope(Dispatchers.Main).launch {
                        Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                    }
                    callback(emptyList())
                    return@launch
                }

                val responseBody = try {
                    response.body?.string()
                } catch (e: Exception) {
                    Log.e("SettingsScreen", "读取响应体时出错", e)
                    null
                }

                if (responseBody.isNullOrEmpty()) {
                    CoroutineScope(Dispatchers.Main).launch {
                        Toast.makeText(context, "响应为空", Toast.LENGTH_LONG).show()
                    }
                    callback(emptyList())
                    return@launch
                }

                try {
                    val jsonObject = JSONObject(responseBody)
                    val dataArray = jsonObject.optJSONArray("data")
                    val models = mutableListOf<String>()

                    if (dataArray != null) {
                        for (i in 0 until dataArray.length()) {
                            val modelObject = dataArray.optJSONObject(i)
                            if (modelObject != null) {
                                val modelName = modelObject.optString("id", "")
                                if (modelName.isNotEmpty()) {
                                    models.add(modelName)
                                }
                            }
                        }
                    }

                    CoroutineScope(Dispatchers.Main).launch {
                        if (models.isEmpty()) {
                            Toast.makeText(context, "未找到可用模型", Toast.LENGTH_LONG).show()
                        }
                        callback(models)
                    }
                } catch (e: JSONException) {
                    Log.e("SettingsScreen", "解析响应时出错", e)
                    CoroutineScope(Dispatchers.Main).launch {
                        Toast.makeText(context, "解析响应失败: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                    callback(emptyList())
                }
            }
        } catch (e: UnknownHostException) {
            Log.e("SettingsScreen", "网络连接错误", e)
            CoroutineScope(Dispatchers.Main).launch {
                Toast.makeText(context, "网络连接错误，请检查网络设置", Toast.LENGTH_LONG).show()
            }
            callback(emptyList())
        } catch (e: SocketTimeoutException) {
            Log.e("SettingsScreen", "请求超时", e)
            CoroutineScope(Dispatchers.Main).launch {
                Toast.makeText(context, "请求超时，请稍后重试", Toast.LENGTH_LONG).show()
            }
            callback(emptyList())
        } catch (e: java.net.SocketException) {
            Log.e("SettingsScreen", "网络连接异常", e)
            CoroutineScope(Dispatchers.Main).launch {
                Toast.makeText(context, "网络连接异常，请检查网络设置", Toast.LENGTH_LONG).show()
            }
            callback(emptyList())
        } catch (e: IOException) {
            Log.e("SettingsScreen", "网络IO错误", e)
            CoroutineScope(Dispatchers.Main).launch {
                Toast.makeText(context, "网络错误: ${e.message}", Toast.LENGTH_LONG).show()
            }
            callback(emptyList())
        } catch (e: Exception) {
            Log.e("SettingsScreen", "获取模型列表时出现未处理的异常", e)
            CoroutineScope(Dispatchers.Main).launch {
                Toast.makeText(context, "获取模型失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
            callback(emptyList())
        }
    }
}