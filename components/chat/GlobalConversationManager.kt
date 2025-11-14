package com.example.bestplannner.components.chat

import android.content.Context
import android.util.Log
import com.example.bestplannner.data.Conversation
import com.example.bestplannner.data.Message
import com.example.bestplannner.data.PlanItem
import com.example.bestplannner.notification.ReminderManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.threeten.bp.LocalDateTime
import java.util.concurrent.TimeUnit

class GlobalConversationManager(private val context: Context) {
    companion object {
        private const val TAG = "GlobalConvManager"

        @Volatile
        private var INSTANCE: GlobalConversationManager? = null

        fun getInstance(context: Context): GlobalConversationManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: GlobalConversationManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    /**
     * 生成对话消息
     */
    private suspend fun generateConversationMessage(conversation: Conversation, status: String, plan: PlanItem): Message {
        // 获取AI设置
        val settingsPreferences = context.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)
        val settings = getAISettingsForConversation(settingsPreferences, conversation.id)

        val baseSystemPrompt = settings[0] as String
        val baseUrl = settings[1] as String
        val apiKey = settings[2] as String
        val modelName = settings[3] as String
        val temperature = settings[4] as Float
        val maxTokens = settings[5] as Int
        val conversationMemory = settings[6] as Int

        // 构造发送给AI的完整上下文信息，将设置中的提示词与对话信息结合
        val currentTime = org.threeten.bp.LocalTime.now().withSecond(0).withNano(0) // 精确到分钟
        val currentDay = org.threeten.bp.LocalDate.now().dayOfWeek

        // 获取对话历史记录用于AI记忆，只获取用户发送的消息
        val conversationHistory = getConversationHistory(conversation.id, conversationMemory)

        val statusText = when (status) {
            "not_started" -> "计划尚未开始，请做好准备"
            "working" -> "正在进行中，请保持专注"
            else -> "计划状态更新"
        }

        val systemPrompt = """
            $baseSystemPrompt
            请扮演好这个角色。

            ${if (conversationHistory.isNotEmpty()) "以下是之前的对话历史记录，用于保持上下文一致性：\n$conversationHistory" else ""}
            当前时间: $currentTime
            当前日期: $currentDay
            对话状态: $statusText
            你需要帮助用户完成以下计划：
            计划信息:
            - 计划标题: ${plan.title}
            - 计划描述: ${plan.description}
            - 计划时间: ${plan.startTime} 到 ${plan.endTime}
        """.trimIndent()

        // 调用AI API生成对话内容
        val aiResponse = callAIAPISync(systemPrompt, baseUrl, apiKey, modelName, temperature, maxTokens)

        // 使用兼容API 21的日期时间处理
        val timestamp = LocalDateTime.now()

        return Message(
            id = System.currentTimeMillis(),
            text = aiResponse,
            isUser = false,
            timestamp = timestamp
        )
    }

    /**
     * 获取对话的AI设置
     */
    private fun getAISettingsForConversation(settingsPreferences: android.content.SharedPreferences, conversationId: Long): Array<out Any> {
        // 优先使用对话特定设置，其次使用全局设置
        val settingsKeyPrefix = "conversation_${conversationId}_"

        return arrayOf(
            settingsPreferences.getString("${settingsKeyPrefix}system_prompt",
                settingsPreferences.getString("system_prompt", "你是一个乐于助人的AI助手")) ?: "你是一个乐于助人的AI助手",
            settingsPreferences.getString("${settingsKeyPrefix}base_url",
                settingsPreferences.getString("base_url", "https://api.siliconflow.cn/v1")) ?: "https://api.siliconflow.cn/v1",
            settingsPreferences.getString("${settingsKeyPrefix}api_key",
                settingsPreferences.getString("api_key", "sk-dplqbsomzrruqpclshnwysqlmhpdbylvfxbnvtvwygvsvfaz")) ?: "sk-dplqbsomzrruqpclshnwysqlmhpdbylvfxbnvtvwygvsvfaz",
            settingsPreferences.getString("${settingsKeyPrefix}model_name",
                settingsPreferences.getString("model_name", "THUDM/glm-4-9b-chat")) ?: "THUDM/glm-4-9b-chat",
            settingsPreferences.getFloat("${settingsKeyPrefix}temperature",
                settingsPreferences.getFloat("temperature", 0.7f)),
            settingsPreferences.getInt("${settingsKeyPrefix}max_tokens",
                settingsPreferences.getInt("max_tokens", 512)),
            settingsPreferences.getInt("${settingsKeyPrefix}conversation_memory",
                settingsPreferences.getInt("conversation_memory", 5))
        )
    }

    /**
     * 获取对话历史记录，只保留用户发送的消息以节省空间
     */
    private fun getConversationHistory(conversationId: Long, memoryRounds: Int): String {
        if (memoryRounds <= 0) return ""

        val conversationPreferences = context.getSharedPreferences("conversations", Context.MODE_PRIVATE)
        val conversationCount = conversationPreferences.getInt("conversation_count", 0)

        for (i in 0 until conversationCount) {
            val id = conversationPreferences.getLong("conversation_${i}_id", i.toLong())
            if (id == conversationId) {
                val messageCount = conversationPreferences.getInt("conversation_${i}_message_count", 0)
                val history = StringBuilder()

                // 收集用户发送的消息
                val userMessages = mutableListOf<String>()
                for (j in 0 until messageCount) {
                    val text = conversationPreferences.getString("conversation_${i}_message_${j}_text", "") ?: ""
                    val isUser = conversationPreferences.getBoolean("conversation_${i}_message_${j}_isUser", false)
                    // 只记录用户输入的内容
                    if (isUser) {
                        userMessages.add(text)
                    }
                }

                // 只获取最近几条用户消息作为历史记录
                val startIdx = if (userMessages.size > memoryRounds) userMessages.size - memoryRounds else 0
                for (k in startIdx until userMessages.size) {
                    history.append("用户: ${userMessages[k]}\n")
                }

                return history.toString().trim()
            }
        }

        return ""
    }

    /**
     * 同步调用AI API生成对话内容
     */
    private suspend fun callAIAPISync(
        systemPrompt: String,
        baseUrl: String,
        apiKey: String,
        modelName: String,
        temperature: Float,
        maxTokens: Int
    ): String = withContext(Dispatchers.IO) {
        // 检查必要参数是否存在
        if (apiKey.isEmpty()) {
            Log.e(TAG, "API密钥未设置")
            return@withContext "[计划尚未开始，请做好准备] ${systemPrompt.split("\n").lastOrNull() ?: "计划"}"
        }

        if (baseUrl.isEmpty()) {
            Log.e(TAG, "API基础URL未设置")
            return@withContext "[计划尚未开始，请做好准备] ${systemPrompt.split("\n").lastOrNull() ?: "计划"}"
        }

        if (modelName.isEmpty()) {
            Log.e(TAG, "模型名称未设置")
            return@withContext "[计划尚未开始，请做好准备] ${systemPrompt.split("\n").lastOrNull() ?: "计划"}"
        }

        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()

            // 构建请求体
            val jsonBody = JSONObject().apply {
                put("model", modelName)
                put("temperature", temperature)
                put("max_tokens", maxTokens)
                put("stream", false)
                val messagesArray = JSONArray()

                // 添加系统提示词作为系统消息
                val systemMessage = JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                }
                messagesArray.put(systemMessage)

                // 添加一个空的用户消息，确保请求格式正确
                val userMessage = JSONObject().apply {
                    put("role", "user")
                    put("content", "请根据系统提示生成对话内容")
                }
                messagesArray.put(userMessage)

                put("messages", messagesArray)
            }

            Log.d(TAG, "发送请求到: $baseUrl")
            Log.d(TAG, "使用模型: $modelName")
            Log.d(TAG, "请求体: $jsonBody")

            // 创建请求
            val request = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/chat/completions")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .build()

            // 发送请求并获取响应
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                Log.d(TAG, "响应码: ${response.code}")
                Log.d(TAG, "响应体: $responseBody")

                if (!response.isSuccessful) {
                    val errorMsg = when(response.code) {
                        400 -> "请求参数错误，请检查设置"
                        401 -> "API认证失败，请检查API密钥是否正确"
                        403 -> "API访问被拒绝，请检查权限设置"
                        404 -> "请求的模型未找到，请检查模型名称"
                        429 -> "请求过于频繁，请稍后再试"
                        500 -> "服务器内部错误，请稍后重试"
                        503 -> "服务暂时不可用，请稍后再试"
                        else -> "HTTP ${response.code}: ${response.message}"
                    }
                    return@withContext "[计划尚未开始，请做好准备] ${errorMsg}"
                } else {
                    // 解析响应
                    if (responseBody.isNullOrEmpty()) {
                        return@withContext "[计划尚未开始，请做好准备] 对话生成失败: 响应内容为空"
                    } else {
                        try {
                            val jsonResponse = JSONObject(responseBody)
                            val choices = jsonResponse.getJSONArray("choices")
                            if (choices.length() > 0) {
                                val firstChoice = choices.getJSONObject(0)
                                val messageObj = firstChoice.getJSONObject("message")
                                val content = messageObj.getString("content")
                                return@withContext content.ifEmpty { "[计划尚未开始，请做好准备] 对话生成成功，但内容为空" }
                            } else {
                                return@withContext "[计划尚未开始，请做好准备] 对话生成失败: 响应中没有内容"
                            }
                        } catch (e: org.json.JSONException) {
                            Log.e(TAG, "解析响应时出错", e)
                            return@withContext "[计划尚未开始，请做好准备] 对话生成失败: 响应格式错误，请稍后重试"
                        }
                    }
                }
            }
        } catch (e: java.net.UnknownHostException) {
            Log.e(TAG, "网络连接错误", e)
            return@withContext "[计划尚未开始，请做好准备] 对话生成失败: 网络连接错误，请检查网络设置"
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "请求超时", e)
            return@withContext "[计划尚未开始，请做好准备] 对话生成失败: 请求超时，请稍后重试"
        } catch (e: java.net.SocketException) {
            Log.e(TAG, "网络连接异常", e)
            return@withContext "[计划尚未开始，请做好准备] 对话生成失败: 网络连接异常，请检查网络设置"
        } catch (e: java.io.IOException) {
            Log.e(TAG, "网络IO错误", e)
            return@withContext "[计划尚未开始，请做好准备] 对话生成失败: 网络错误，请检查网络连接"
        } catch (e: Exception) {
            Log.e(TAG, "调用AI API时发生异常", e)
            // 更详细地记录错误信息
            val errorMessage = e.message ?: "未知错误"
            val errorType = e.javaClass.simpleName
            return@withContext "[计划尚未开始，请做好准备] 对话生成失败: $errorMessage ($errorType)，请检查网络连接和API设置"
        }
    }

    /**
     * 安排对话生成任务
     */
    suspend fun scheduleConversationGeneration(plan: com.example.bestplannner.data.PlanItem, conversationId: Long, status: String) {
        try {
            // 创建一个Conversation对象用于生成消息
            val conversation = com.example.bestplannner.data.Conversation(
                id = conversationId,
                title = "计划助手 - ${plan.title}",
                messages = emptyList(),
                timestamp = LocalDateTime.now()
            )

            // 生成对话消息
            val message = generateConversationMessage(conversation, status, plan)

            // 使用ConversationManager保存消息并立即发送通知
            val conversationManager = com.example.bestplannner.components.chat.ConversationManager(context)
            conversationManager.addMessageToConversation(conversationId, message, true)
            
            // 记录生成时间
            val conversationPreferences = context.getSharedPreferences("conversations", Context.MODE_PRIVATE)
            val lastGeneratedMessageKey = "last_generated_message_${plan.id}"
            val currentTime = System.currentTimeMillis()
            conversationPreferences.edit()
                .putLong(lastGeneratedMessageKey, currentTime)
                .apply()
                
            Log.d(TAG, "已为计划 ${plan.title} 生成并保存对话消息，已发送通知")
        } catch (e: Exception) {
            Log.e(TAG, "安排对话生成任务时出错", e)
        }
    }
}