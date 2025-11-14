package com.example.bestplannner.components.chat

import android.content.Context
import com.example.bestplannner.data.Message
import com.example.bestplannner.notification.ReminderManager
import com.example.bestplannner.screens.loadPlanById
import com.example.bestplannner.screens.findPlanIdByConversation
import com.example.bestplannner.data.PlanItem

/**
 * 任务状态管理器，负责管理消息的任务状态
 */
class TaskStatusManager(private val context: Context) {

    /**
     * 获取消息的任务状态
     */
    fun getMessageTaskStatus(message: Message): String? {
        val prefs = context.getSharedPreferences("message_task_status", Context.MODE_PRIVATE)
        return prefs.getString("message_$message.id", if (message.isUser) null else "not_started")
    }

    /**
     * 设置消息的任务状态
     */
    fun setMessageTaskStatus(messageId: Long, status: String) {
        val prefs = context.getSharedPreferences("message_task_status", Context.MODE_PRIVATE)
        prefs.edit().putString("message_$messageId", status).apply()
        
        // 同时更新plan_status中的状态
        updatePlanStatusForMessage(messageId, status)
    }

    /**
     * 更新与消息关联的计划状态
     */
    private fun updatePlanStatusForMessage(messageId: Long, status: String) {
        val conversationPreferences = context.getSharedPreferences("conversations", Context.MODE_PRIVATE)
        val planStatusPrefs = context.getSharedPreferences("plan_status", Context.MODE_PRIVATE)
        
        // 通过消息ID查找关联的对话和计划
        val conversationCount = conversationPreferences.getInt("conversation_count", 0)
        for (i in 0 until conversationCount) {
            val messageCount = conversationPreferences.getInt("conversation_${i}_message_count", 0)
            for (j in 0 until messageCount) {
                val msgId = conversationPreferences.getLong("conversation_${i}_message_${j}_id", -1)
                if (msgId == messageId) {
                    val planId = conversationPreferences.getLong("conversation_${i}_plan_id", -1)
                    val conversationId = conversationPreferences.getLong("conversation_${i}_id", i.toLong())
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
                        
                        // 同时更新对话状态（用于历史记录分类）
                        val conversationStatusPrefs = context.getSharedPreferences("conversation_status", Context.MODE_PRIVATE)
                        conversationStatusPrefs.edit().putString("conversation_${conversationId}_status", status).apply()
                    }
                    return
                }
            }
        }
    }

    /**
     * 处理"未开始"状态
     */
    fun handleNotStarted(message: Message) {
        setMessageTaskStatus(message.id, "not_started")
        
        // 查找与消息关联的计划并更新状态
        val planId = findPlanIdByMessage(message)
        if (planId != -1) {
            val plan = loadPlanById(context, planId)
            if (plan != null) {
                val autoReminderManager = ReminderManager.getInstance(context)
                // 查找对话ID
                val conversationId = findConversationIdByPlanId(planId)
                if (conversationId != -1L) {
                    // 通知系统只负责对自动对话系统进行通知，不负责更新计划状态
                    autoReminderManager.showReminder(conversationId, plan.title, "计划状态更新为：未开始")
                }
            }
        }
    }

    /**
     * 处理"正在努力"状态
     */
    fun handleWorkingOnIt(message: Message) {
        setMessageTaskStatus(message.id, "working")

        // 查找与消息关联的计划并更新状态
        val planId = findPlanIdByMessage(message)
        if (planId != -1) {
            val plan = loadPlanById(context, planId)
            if (plan != null) {
                val autoReminderManager = ReminderManager.getInstance(context)
                // 查找对话ID
                val conversationId = findConversationIdByPlanId(planId)
                if (conversationId != -1L) {
                    // 通知系统只负责对自动对话系统进行通知，不负责更新计划状态
                    autoReminderManager.showReminder(conversationId, plan.title, "计划状态更新为：正在努力")
                }
            }
        }
    }

    /**
     * 处理"已完成"状态
     */
    fun handleTaskCompleted(message: Message) {
        setMessageTaskStatus(message.id, "completed")

        // 查找与消息关联的计划并更新状态
        val planId = findPlanIdByMessage(message)
        if (planId != -1) {
            val plan = loadPlanById(context, planId)
            if (plan != null) {
                val autoReminderManager = ReminderManager.getInstance(context)
                // 查找对话ID
                val conversationId = findConversationIdByPlanId(planId)
                if (conversationId != -1L) {
                    // 通知系统只负责对自动对话系统进行通知，不负责更新计划状态
                    autoReminderManager.showReminder(conversationId, plan.title, "计划状态更新为：已完成")
                }
            }
        }
    }

    /**
     * 根据消息查找关联的计划ID
     */
    private fun findPlanIdByMessage(message: Message): Int {
        // 通过SharedPreferences查找与消息列表匹配的对话
        val conversationPreferences = context.getSharedPreferences("conversations", Context.MODE_PRIVATE)
        
        // 通过对话查找计划
        val conversationCount = conversationPreferences.getInt("conversation_count", 0)
        for (i in 0 until conversationCount) {
            val messageCount = conversationPreferences.getInt("conversation_${i}_message_count", 0)
            for (j in 0 until messageCount) {
                val msgId = conversationPreferences.getLong("conversation_${i}_message_${j}_id", -1)
                if (msgId == message.id) {
                    val planId = conversationPreferences.getLong("conversation_${i}_plan_id", -1)
                    return planId.toInt()
                }
            }
        }

        return -1
    }

    /**
     * 根据计划ID查找关联的对话ID
     */
    private fun findConversationIdByPlanId(planId: Int): Long {
        val conversationPreferences = context.getSharedPreferences("conversations", Context.MODE_PRIVATE)
        val conversationCount = conversationPreferences.getInt("conversation_count", 0)

        for (i in 0 until conversationCount) {
            val storedPlanId = conversationPreferences.getLong("conversation_${i}_plan_id", -1)
            if (storedPlanId == planId.toLong()) {
                return conversationPreferences.getLong("conversation_${i}_id", -1)
            }
        }

        return -1
    }
}