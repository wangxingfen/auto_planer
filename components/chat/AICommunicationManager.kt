package com.example.bestplannner.components.chat

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.bestplannner.data.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.SocketException
import java.util.concurrent.TimeUnit

/**
 * AI通信管理器，负责与AI服务进行通信
 */
class AICommunicationManager(private val context: Context) {
    
    /**
     * 发送消息到AI并获取响应
     */
    suspend fun sendToAI(messages: List<Message>, conversationId: Long? = null): String {
        return withContext(Dispatchers.IO) {
            try {
                val sharedPreferences = context.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)

                // 获取当前对话的特定设置
                val settingsKeyPrefix = if (conversationId != null) "conversation_${conversationId}_" else ""

                val baseUrl = sharedPreferences.getString("${settingsKeyPrefix}base_url", "https://api.siliconflow.cn/v1") ?: "https://api.siliconflow.cn/v1"
                val apiKey = sharedPreferences.getString("${settingsKeyPrefix}api_key", "") ?: ""
                val modelName = sharedPreferences.getString("${settingsKeyPrefix}model_name", "THUDM/glm-4-9b-chat") ?: "THUDM/glm-4-9b-chat"
                val systemPrompt = sharedPreferences.getString("${settingsKeyPrefix}system_prompt", "你是一个乐于助人的AI助手") ?: "你是一个乐于助人的AI助手"
                val temperature = sharedPreferences.getFloat("${settingsKeyPrefix}temperature", 0.7f)
                val maxTokens = sharedPreferences.getInt("${settingsKeyPrefix}max_tokens", 2048)
                val conversationMemory = sharedPreferences.getInt("${settingsKeyPrefix}conversation_memory", 5)

                // 检查必要参数
                if (baseUrl.isBlank()) {
                    return@withContext "错误：基础URL未设置，请在设置中配置"
                }

                if (apiKey.isBlank()) {
                    return@withContext "错误：API密钥未设置，请在设置中配置"
                }

                if (modelName.isBlank()) {
                    return@withContext "错误：模型名称未设置，请在设置中配置"
                }

                // 构建符合OpenAI标准的请求体
                val jsonBody = JSONObject().apply {
                    put("model", modelName)
                    put("temperature", temperature)
                    put("max_tokens", maxTokens)
                    put("stream", false)
                    val messagesArray = JSONArray()

                    // 添加系统提示词
                    val systemMessage = JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    }
                    messagesArray.put(systemMessage)

                    // 只添加用户发送的消息，过滤掉AI的回复以节省对话空间
                    val userMessages = messages.filter { it.isUser }
                    
                    // 添加对话历史记录，最多保留指定轮数的用户消息
                    val messageCount = userMessages.size
                    val startIndex = if (messageCount > conversationMemory) {
                        messageCount - conversationMemory
                    } else {
                        0
                    }

                    for (i in startIndex until messageCount) {
                        val message = userMessages[i]
                        val messageObj = JSONObject().apply {
                            put("role", if (message.isUser) "user" else "assistant")
                            put("content", message.text)
                        }
                        messagesArray.put(messageObj)
                    }

                    put("messages", messagesArray)
                }

                // 创建HTTP客户端
                val client = OkHttpClient.Builder()
                    .connectTimeout(300, TimeUnit.SECONDS)
                    .readTimeout(300, TimeUnit.SECONDS)
                    .writeTimeout(300, TimeUnit.SECONDS)
                    .build()

                // 创建请求
                val requestBuilder = Request.Builder()
                    .url("${baseUrl.trimEnd('/')}/chat/completions")
                    .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")

                val request = requestBuilder.build()

                // 发送请求并处理响应
                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string()

                    if (!response.isSuccessful) {
                        return@withContext "错误: HTTP ${response.code} - ${responseBody ?: "未知错误"}"
                    }

                    // 解析响应
                    try {
                        val jsonResponse = JSONObject(responseBody ?: "")
                        val choices = jsonResponse.getJSONArray("choices")
                        if (choices.length() > 0) {
                            val firstChoice = choices.getJSONObject(0)
                            val messageObj = firstChoice.getJSONObject("message")
                            val content = messageObj.getString("content")
                            return@withContext content
                        } else {
                            return@withContext "错误: 响应中没有内容"
                        }
                    } catch (e: JSONException) {
                        Log.e("AICommunicationManager", "解析响应时出错", e)
                        return@withContext "错误: 解析响应失败 - ${e.message}"
                    }
                }
            } catch (e: Exception) {
                Log.e("AICommunicationManager", "发送消息到AI时出现异常", e)
                return@withContext handleException(e)
            }
        }
    }

    /**
     * 处理通信异常
     */
    private fun handleException(e: Exception): String {
        return when (e) {
            is SocketTimeoutException -> "请求超时，请稍后重试"
            is JSONException -> "响应解析错误，请稍后重试"
            is UnknownHostException -> "网络连接错误，请检查网络设置"
            is SocketException -> "网络连接异常，请检查网络设置"
            is IOException -> "网络IO错误，请检查网络连接"
            else -> "发送消息时出错: ${e.message ?: "未知错误"}"
        }
    }
}