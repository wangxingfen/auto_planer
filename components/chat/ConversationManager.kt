package com.example.bestplannner.components.chat

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.bestplannner.data.Conversation
import com.example.bestplannner.data.ConversationMetadata
import com.example.bestplannner.data.Message
import com.example.bestplannner.notification.ReminderManager
import androidx.work.WorkManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Data
import com.example.bestplannner.worker.AutoConversationWorker
import java.util.concurrent.TimeUnit
import org.threeten.bp.LocalDateTime
import org.threeten.bp.Instant
import org.threeten.bp.ZoneId
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 对话管理器，负责对话的加载、保存和管理
 */
class ConversationManager(private val context: Context) {
    // 为对话管理创建独立的线程池，避免与其他系统组件竞争线程资源
    private val conversationExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val conversationPreferences = context.getSharedPreferences("conversations", Context.MODE_PRIVATE)
    
    /**
     * 加载对话元数据列表（只加载基本信息，不加载消息内容）
     */
    fun loadConversationsMetadata(): List<ConversationMetadata> {
        return try {
            val conversationCount = conversationPreferences.getInt("conversation_count", 0)
            val conversations = mutableListOf<ConversationMetadata>()

            for (i in 0 until conversationCount) {
                val id = conversationPreferences.getLong("conversation_${i}_id", i.toLong())
                val title = conversationPreferences.getString("conversation_${i}_title", "对话") ?: "对话"
                val timestampMillis = conversationPreferences.getLong("conversation_${i}_timestamp", System.currentTimeMillis())
                
                val timestamp = try {
                    LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(timestampMillis),
                        ZoneId.systemDefault()
                    )
                } catch (e: Exception) {
                    LocalDateTime.now()
                }

                // 只加载消息数量，不加载具体消息内容
                val messageCount = conversationPreferences.getInt("conversation_${i}_message_count", 0)

                conversations.add(ConversationMetadata(id, title, timestamp, messageCount))
            }

            conversations
        } catch (e: Exception) {
            Log.e("ConversationManager", "加载对话元数据时出错", e)
            emptyList()
        }
    }

    /**
     * 根据ID加载单个对话
     */
    fun loadConversationById(conversationId: Long): Conversation? {
        return try {
            val conversationCount = conversationPreferences.getInt("conversation_count", 0)

            for (i in 0 until conversationCount) {
                val id = conversationPreferences.getLong("conversation_${i}_id", i.toLong())
                if (id == conversationId) {
                    val title = conversationPreferences.getString("conversation_${i}_title", "对话") ?: "对话"
                    val timestampMillis = conversationPreferences.getLong("conversation_${i}_timestamp", System.currentTimeMillis())
                    
                    val timestamp = try {
                        LocalDateTime.ofInstant(
                            Instant.ofEpochMilli(timestampMillis),
                            ZoneId.systemDefault()
                        )
                    } catch (e: Exception) {
                        LocalDateTime.now()
                    }

                    // 加载消息
                    val messageCount = conversationPreferences.getInt("conversation_${i}_message_count", 0)
                    val messages = mutableListOf<Message>()

                    for (j in 0 until messageCount) {
                        val msgId = conversationPreferences.getLong("conversation_${i}_message_${j}_id", j.toLong())
                        val text = conversationPreferences.getString("conversation_${i}_message_${j}_text", "") ?: ""
                        val isUser = conversationPreferences.getBoolean("conversation_${i}_message_${j}_isUser", false)
                        val msgTimestampMillis = conversationPreferences.getLong("conversation_${i}_message_${j}_timestamp", System.currentTimeMillis())
                        
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

            null
        } catch (e: Exception) {
            Log.e("ConversationManager", "加载对话时出错", e)
            null
        }
    }

    /**
     * 保存单个对话
     */
    fun saveSingleConversation(conversation: Conversation) {
        // 在独立线程中保存对话，避免阻塞主线程
        conversationExecutor.execute {
            try {
                val editor = conversationPreferences.edit()
                
                // 查找对话索引
                var conversationIndex = -1
                val conversationCount = conversationPreferences.getInt("conversation_count", 0)
                for (i in 0 until conversationCount) {
                    val id = conversationPreferences.getLong("conversation_${i}_id", i.toLong())
                    if (id == conversation.id) {
                        conversationIndex = i
                        break
                    }
                }
                
                // 如果是新对话，使用新索引
                if (conversationIndex == -1) {
                    conversationIndex = conversationCount
                }

                // 保存对话信息
                editor.putLong("conversation_${conversationIndex}_id", conversation.id)
                editor.putString("conversation_${conversationIndex}_title", conversation.title)
                editor.putLong("conversation_${conversationIndex}_timestamp", 
                    conversation.timestamp.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())

                // 保存消息
                conversation.messages.forEachIndexed { msgIndex, message ->
                    editor.putLong("conversation_${conversationIndex}_message_${msgIndex}_id", message.id)
                    editor.putString("conversation_${conversationIndex}_message_${msgIndex}_text", message.text)
                    editor.putBoolean("conversation_${conversationIndex}_message_${msgIndex}_isUser", message.isUser)
                    editor.putLong("conversation_${conversationIndex}_message_${msgIndex}_timestamp", 
                        message.timestamp.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
                    
                    // 如果是用户发送的消息，更新对应的计划用户消息时间
                    if (message.isUser) {
                        val planId = findPlanIdByConversationTitle(conversation.title)
                        if (planId != -1) {
                            recordUserMessageSent(planId)
                        }
                    }
                }

                editor.putInt("conversation_${conversationIndex}_message_count", conversation.messages.size)
                
                // 如果是新对话，更新对话总数
                if (conversationIndex == conversationCount) {
                    editor.putInt("conversation_count", conversationCount + 1)
                }
                
                editor.apply()
                
                // 检查是否应该发送通知并安排定期通知检查
                if (conversationIndex == conversationCount) {
                    scheduleConversationNotificationCheck(conversation.id)
                } else {
                    // 对于现有对话，检查是否应该发送通知
                    checkAndScheduleGeneralNotification(conversation)
                }
            } catch (e: Exception) {
                Log.e("ConversationManager", "保存对话时出错", e)
            }
        }
    }

    /**
     * 保存单个对话（同步版本）
     */
    fun saveSingleConversationSync(conversation: Conversation) {
        try {
            val editor = conversationPreferences.edit()
            
            // 查找对话索引
            var conversationIndex = -1
            val conversationCount = conversationPreferences.getInt("conversation_count", 0)
            for (i in 0 until conversationCount) {
                val id = conversationPreferences.getLong("conversation_${i}_id", i.toLong())
                if (id == conversation.id) {
                    conversationIndex = i
                    break
                }
            }
            
            // 如果是新对话，使用新索引
            if (conversationIndex == -1) {
                conversationIndex = conversationCount
            }

            // 确定对话时间戳 - 使用最新消息的时间戳，如果没有消息则使用当前时间
            val latestTimestamp = if (conversation.messages.isNotEmpty()) {
                conversation.messages.maxByOrNull { it.timestamp }?.timestamp ?: conversation.timestamp
            } else {
                conversation.timestamp
            }

            // 保存对话信息
            editor.putLong("conversation_${conversationIndex}_id", conversation.id)
            editor.putString("conversation_${conversationIndex}_title", conversation.title)
            editor.putLong("conversation_${conversationIndex}_timestamp", 
                latestTimestamp.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())

            // 保存消息
            conversation.messages.forEachIndexed { msgIndex, message ->
                editor.putLong("conversation_${conversationIndex}_message_${msgIndex}_id", message.id)
                editor.putString("conversation_${conversationIndex}_message_${msgIndex}_text", message.text)
                editor.putBoolean("conversation_${conversationIndex}_message_${msgIndex}_isUser", message.isUser)
                editor.putLong("conversation_${conversationIndex}_message_${msgIndex}_timestamp", 
                    message.timestamp.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
                
                // 如果是用户发送的消息，更新对应的计划用户消息时间
                if (message.isUser) {
                    val planId = findPlanIdByConversationTitle(conversation.title)
                    if (planId != -1) {
                        recordUserMessageSent(planId)
                    }
                }
            }

            editor.putInt("conversation_${conversationIndex}_message_count", conversation.messages.size)
            
            // 如果是新对话，更新对话总数
            if (conversationIndex == conversationCount) {
                editor.putInt("conversation_count", conversationCount + 1)
            }
            
            editor.apply()
            
            // 检查是否应该发送通知并安排定期通知检查
            if (conversationIndex == conversationCount) {
                scheduleConversationNotificationCheck(conversation.id)
            } else {
                // 对于现有对话，检查是否应该发送通知
                checkAndScheduleGeneralNotification(conversation)
            }
            
            // 当对话有新消息时，发送提醒
            if (conversation.messages.isNotEmpty()) {
                val lastMessage = conversation.messages.last()
                // 只有当消息不是用户发送的（即AI回复）时才发送提醒
                if (!lastMessage.isUser) {
                    val reminderManager = ReminderManager.getInstance(context)
                    reminderManager.showReminder(
                        conversation.id,
                        conversation.title,
                        lastMessage.text
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("ConversationManager", "保存对话时出错", e)
        }
    }

    /**
     * 添加新消息到对话中
     */
    fun addMessageToConversation(conversationId: Long, message: Message, sendNotification: Boolean = false) {
        try {
            val conversationPreferences = context.getSharedPreferences("conversations", Context.MODE_PRIVATE)
            val conversationCount = conversationPreferences.getInt("conversation_count", 0)

            // 找到对应的对话
            var targetConversationIndex = -1
            for (i in 0 until conversationCount) {
                val id = conversationPreferences.getLong("conversation_${i}_id", i.toLong())
                if (id == conversationId) {
                    targetConversationIndex = i
                    break
                }
            }

            // 如果找到对话，添加新消息
            if (targetConversationIndex != -1) {
                val messageCount = conversationPreferences.getInt("conversation_${targetConversationIndex}_message_count", 0)

                // 保存新消息
                conversationPreferences.edit()
                    .putString("conversation_${targetConversationIndex}_message_${messageCount}_text", message.text)
                    .putBoolean("conversation_${targetConversationIndex}_message_${messageCount}_isUser", message.isUser)
                    .putLong("conversation_${targetConversationIndex}_message_${messageCount}_timestamp", message.timestamp.atZone(org.threeten.bp.ZoneId.systemDefault()).toInstant().toEpochMilli())
                    .putLong("conversation_${targetConversationIndex}_message_${messageCount}_id", message.id)
                    .putInt("conversation_${targetConversationIndex}_message_count", messageCount + 1)
                    // 同时更新对话的时间戳为最新消息的时间
                    .putLong("conversation_${targetConversationIndex}_timestamp", 
                        message.timestamp.atZone(org.threeten.bp.ZoneId.systemDefault()).toInstant().toEpochMilli())
                    .apply()

                Log.d("ConversationManager", "已保存消息到对话 $conversationId")
                 
                // 发送广播通知对话历史界面更新
                val intent = android.content.Intent("com.example.bestplannner.CONVERSATION_UPDATED")
                intent.putExtra("conversation_id", conversationId)
                intent.putExtra("message_count", messageCount + 1)
                context.sendBroadcast(intent)
                
                // 如果需要发送通知，则在后台线程中发送
                if (sendNotification) {
                    // 在后台线程中发送通知，避免阻塞当前操作
                    Thread {
                        try {
                            val autoReminderManager = com.example.bestplannner.notification.ReminderManager.getInstance(context)
                            // 加载对话以获取标题
                            val conversation = loadConversationById(conversationId)
                            if (conversation != null) {
                                autoReminderManager.showReminder(
                                    conversationId,
                                    conversation.title,
                                    message.text
                                )
                                Log.d("ConversationManager", "已为对话 $conversationId 发送通知")
                            }
                        } catch (e: Exception) {
                            Log.e("ConversationManager", "发送通知时出错", e)
                        }
                    }.start()
                }
                
                // 当对话有新消息时，发送提醒（仅对AI消息）
                if (!message.isUser) {
                    // 在后台线程中发送提醒，避免阻塞当前操作
                    Thread {
                        try {
                            val conversation = loadConversationById(conversationId)
                            if (conversation != null) {
                                val reminderManager = ReminderManager.getInstance(context)
                                reminderManager.showReminder(
                                    conversation.id,
                                    conversation.title,
                                    message.text
                                )
                                Log.d("ConversationManager", "已发送通知: ${message.text}")
                            }
                        } catch (e: Exception) {
                            Log.e("ConversationManager", "发送提醒时出错", e)
                        }
                    }.start()
                }
            } else {
                Log.e("ConversationManager", "未找到ID为 $conversationId 的对话")
            }
        } catch (e: Exception) {
            Log.e("ConversationManager", "保存消息时出错", e)
        }
    }

    /**
     * 保存消息并立即发送通知
     */
    fun saveMessageAndSendNotification(conversationId: Long, message: Message) {
        addMessageToConversation(conversationId, message, true)
    }

    /**
     * 检查并安排普通对话通知
     */
    private fun checkAndScheduleGeneralNotification(conversation: Conversation) {
        // 检查是否应该发送通知
        if (shouldSendNotificationForConversation(conversation.id)) {
            // 安排立即发送通知
            scheduleImmediateConversationNotification(conversation.id)
        }
        
        // 无论是否发送通知，都安排下一次检查
        scheduleConversationNotificationCheck(conversation.id)
    }
    
    /**
     * 检查是否应该为特定对话发送通知
     */
    private fun shouldSendNotificationForConversation(conversationId: Long): Boolean {
        // 检查全局通知设置
        val settingsPrefs = context.getSharedPreferences("planner_settings", Context.MODE_PRIVATE)
        val notificationsEnabled = settingsPrefs.getBoolean("notifications_enabled", true)
        
        // 检查特定对话的通知设置（如果存在）
        val conversationPrefs = context.getSharedPreferences("conversation_settings", Context.MODE_PRIVATE)
        val conversationNotificationsEnabled = conversationPrefs.getBoolean("conversation_${conversationId}_notifications", true)
        
        // 确保在全局通知启用的情况下才检查对话特定设置
        return notificationsEnabled && conversationNotificationsEnabled
    }

    /**
     * 为对话安排立即通知
     */
    private fun scheduleImmediateConversationNotification(conversationId: Long) {
        val data = Data.Builder()
            .putLong("conversation_id", conversationId)
            .putBoolean("is_general_notification", true) // 标记为普通对话通知
            .build()

        // 立即安排通知检查，使用较小的随机延迟避免冲突
        val randomDelay = (1..5).random() // 1到5秒的随机延迟

        // 立即安排通知检查
        val workRequest = OneTimeWorkRequestBuilder<AutoConversationWorker>()
            .setInputData(data)
            .setInitialDelay(randomDelay.toLong(), TimeUnit.SECONDS) // 使用较小的随机延迟
            .addTag("conversation_notification_$conversationId")
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
    }

    /**
     * 为对话安排通知检查
     */
    private fun scheduleConversationNotificationCheck(conversationId: Long) {
        val data = Data.Builder()
            .putLong("conversation_id", conversationId)
            .putBoolean("is_general_notification", true) // 标记为普通对话通知
            .build()

        // 根据用户设置确定检查间隔，如果没有设置则使用默认值
        val settingsPrefs = context.getSharedPreferences("planner_settings", Context.MODE_PRIVATE)
        val defaultInterval = settingsPrefs.getInt("not_started_notification_interval", 5) // 默认5分钟
        val intervalMinutes = if (defaultInterval > 0) defaultInterval else 5

        // 安排下一次检查
        val workRequest = OneTimeWorkRequestBuilder<AutoConversationWorker>()
            .setInputData(data)
            .setInitialDelay(intervalMinutes.toLong(), TimeUnit.MINUTES)
            .addTag("conversation_notification_$conversationId")
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
    }

    /**
     * 保存对话元数据
     */
    fun saveConversationMetadata(conversation: Conversation) {
        try {
            val editor = conversationPreferences.edit()
            
            // 查找对话索引
            var conversationIndex = -1
            val conversationCount = conversationPreferences.getInt("conversation_count", 0)
            for (i in 0 until conversationCount) {
                val id = conversationPreferences.getLong("conversation_${i}_id", i.toLong())
                if (id == conversation.id) {
                    conversationIndex = i
                    break
                }
            }
            
            // 如果是新对话，使用新索引
            if (conversationIndex == -1) {
                conversationIndex = conversationCount
            }

            // 确定对话时间戳 - 使用最新消息的时间戳，如果没有消息则使用当前时间
            val latestTimestamp = if (conversation.messages.isNotEmpty()) {
                conversation.messages.maxByOrNull { it.timestamp }?.timestamp ?: conversation.timestamp
            } else {
                conversation.timestamp
            }

            // 保存对话信息
            editor.putLong("conversation_${conversationIndex}_id", conversation.id)
            editor.putString("conversation_${conversationIndex}_title", conversation.title)
            editor.putLong("conversation_${conversationIndex}_timestamp", 
                latestTimestamp.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
            editor.putInt("conversation_${conversationIndex}_message_count", conversation.messages.size)
            
            // 如果是新对话，确保默认设置被保存
            if (conversationIndex == conversationCount) {
                // 初始化默认设置
                initializeDefaultConversationSettings(editor, conversation.id)
                editor.putInt("conversation_count", conversationCount + 1)
                
                // 为新对话安排定期通知检查
                scheduleConversationNotificationCheck(conversation.id)
            }
            
            editor.apply()
        } catch (e: Exception) {
            Log.e("ConversationManager", "保存对话元数据时出错", e)
        }
    }
    
    /**
     * 为新对话初始化默认设置
     */
    private fun initializeDefaultConversationSettings(editor: SharedPreferences.Editor, conversationId: Long) {
        val settingsKeyPrefix = "conversation_${conversationId}_"
        val aiSettings = context.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)
        
        // 确保新对话默认不关联任何计划
        editor.putLong("${settingsKeyPrefix}plan_id", -1)
        
        // 为新对话初始化默认设置，如果这些设置还不存在的话
        if (!aiSettings.contains("${settingsKeyPrefix}base_url")) {
            editor.putString("${settingsKeyPrefix}base_url", "https://api.siliconflow.cn/v1")
        }
        
        if (!aiSettings.contains("${settingsKeyPrefix}api_key")) {
            editor.putString("${settingsKeyPrefix}api_key", "sk-dplqbsomzrruqpclshnwysqlmhpdbylvfxbnvtvwygvsvfaz")
        }
        
        if (!aiSettings.contains("${settingsKeyPrefix}model_name")) {
            editor.putString("${settingsKeyPrefix}model_name", "THUDM/glm-4-9b-chat")
        }
        
        if (!aiSettings.contains("${settingsKeyPrefix}system_prompt")) {
            editor.putString("${settingsKeyPrefix}system_prompt", "请认真扮演一位帮助用户达成计划的占星师")
        }
        
        if (!aiSettings.contains("${settingsKeyPrefix}temperature")) {
            editor.putFloat("${settingsKeyPrefix}temperature", 0.7f)
        }
        
        if (!aiSettings.contains("${settingsKeyPrefix}max_tokens")) {
            editor.putInt("${settingsKeyPrefix}max_tokens", 2048)
        }
        
        if (!aiSettings.contains("${settingsKeyPrefix}conversation_memory")) {
            editor.putInt("${settingsKeyPrefix}conversation_memory", 5)
        }
    }

    /**
     * 查找或创建计划对应的对话空间
     */
    fun findOrCreateConversationForPlan(plan: com.example.bestplannner.data.PlanItem): Long {
        // 对于每日重复计划，使用计划ID作为对话ID，确保所有日期的副本共享同一个对话空间
        val conversationId = plan.id.toLong()
        
        // 检查对话是否已经存在
        val conversationCount = conversationPreferences.getInt("conversation_count", 0)
        var conversationExists = false
        
        for (i in 0 until conversationCount) {
            val id = conversationPreferences.getLong("conversation_${i}_id", i.toLong())
            if (id == conversationId) {
                conversationExists = true
                break
            }
        }
        
        // 如果对话不存在，则创建新的对话空间
        if (!conversationExists) {
            val editor = conversationPreferences.edit()
            // 保存新对话的信息
            editor.putLong("conversation_${conversationCount}_id", conversationId)
            editor.putLong("conversation_${conversationCount}_plan_id", plan.id.toLong())
            editor.putString("conversation_${conversationCount}_title", plan.title)
            editor.putInt("conversation_count", conversationCount + 1)
            editor.apply()
        }
        
        return conversationId
    }

    /**
     * 根据对话标题查找对应的计划ID
     */
    private fun findPlanIdByConversationTitle(conversationTitle: String): Int {
        val planPrefs = context.getSharedPreferences("plans", Context.MODE_PRIVATE)
        val planCount = planPrefs.getInt("plan_count", 0)
        
        for (i in 0 until planCount) {
            val title = planPrefs.getString("plan_${i}_title", "") ?: ""
            if (title == conversationTitle) {
                return planPrefs.getInt("plan_${i}_id", -1)
            }
        }
        
        // 尝试通过对话ID查找计划ID
        val conversationCount = conversationPreferences.getInt("conversation_count", 0)
        
        for (i in 0 until conversationCount) {
            val conversationTitleStored = conversationPreferences.getString("conversation_${i}_title", "") ?: ""
            if (conversationTitleStored == conversationTitle) {
                val planId = conversationPreferences.getLong("conversation_${i}_plan_id", -1)
                return planId.toInt()
            }
        }
        
        return -1
    }

    /**
     * 记录用户发送消息的时间
     */
    private fun recordUserMessageSent(planId: Int) {
        val notificationPrefs = context.getSharedPreferences("notification_tracker", Context.MODE_PRIVATE)
        with(notificationPrefs.edit()) {
            putLong("plan_${planId}_last_user_message", System.currentTimeMillis())
            apply()
        }
    }
    
    /**
     * 清空对话消息
     */
    fun clearConversationMessages(conversationId: Long) {
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
                    
                    // 注意：不再清除与该对话相关的AI记忆（通过计划ID）
                    // 这些记录对自动对话系统的工作非常重要，不应该在清空对话时移除
                    // 保留以下记录：
                    // - plan_${planId}_last_notification
                    // - plan_${planId}_last_user_message
                    // - plan_${planId}_last_opened
                    
                    editor.apply()
                    break
                }
            }
        } catch (e: Exception) {
            Log.e("ConversationManager", "清空对话消息时出错", e)
        }
    }
}