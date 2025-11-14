package com.example.bestplannner.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.ExistingPeriodicWorkPolicy
import com.example.bestplannner.components.chat.GlobalConversationManager
import com.example.bestplannner.notification.ReminderManager
import com.example.bestplannner.data.PlanItem
import org.threeten.bp.DayOfWeek
import org.threeten.bp.LocalDateTime
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.*

/**
 * 自动对话生成Worker，专门负责生成AI对话消息
 * 与通知系统完全解耦，独立运行在单独的进程中
 */
class AutoConversationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    companion object {
        private const val TAG = "AutoConversationWorker"
        // 用于生成唯一的任务ID
        private val uniqueIdGenerator = AtomicLong(System.currentTimeMillis())

        /**
         * 安排自动对话生成任务
         */
        fun scheduleAutoConversation(context: Context, plan: com.example.bestplannner.data.PlanItem, conversationId: Long, status: String) {
            val data = Data.Builder()
                .putInt("plan_id", plan.id)
                .putLong("conversationId", conversationId)
                .putString("status", status)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<AutoConversationWorker>()
                .setInputData(data)
                .setInitialDelay(1, TimeUnit.SECONDS) // 立即执行
                .addTag("auto_conversation_${plan.id}")
                .build()

            WorkManager.getInstance(context).enqueue(workRequest)
        }

        /**
         * 根据计划状态安排自动对话生成任务
         */
        fun scheduleAutoConversationByPlanStatus(context: Context, plan: com.example.bestplannner.data.PlanItem, conversationId: Long) {
            // 获取当前计划状态
            val prefs = context.getSharedPreferences("plan_status", Context.MODE_PRIVATE)
            val currentStatus = prefs.getString("plan_${plan.id}_status", "not_started") ?: "not_started"

            val data = Data.Builder()
                .putInt("plan_id", plan.id)
                .putLong("conversationId", conversationId)
                .putString("status", currentStatus)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<AutoConversationWorker>()
                .setInputData(data)
                .setInitialDelay(1, TimeUnit.SECONDS) // 立即执行
                .addTag("auto_conversation_${plan.id}_${uniqueIdGenerator.incrementAndGet()}")
                .build()

            WorkManager.getInstance(context).enqueue(workRequest)
        }

        /**
         * 为单个计划安排周期性检查任务
         * 每个计划使用独立的周期性任务，确保互不干扰
         */
        fun schedulePeriodicPlanCheckForPlan(context: Context, plan: PlanItem) {
            // 获取用户设置的检查频率，默认为1分钟
            val settingsPrefs = context.getSharedPreferences("planner_settings", Context.MODE_PRIVATE)
            val checkInterval = settingsPrefs.getInt("periodic_check_interval", 1) // 默认1分钟
            
            // 为每个计划创建独立的周期性任务，使用计划ID作为任务名的一部分
            val workRequest = PeriodicWorkRequestBuilder<AutoConversationWorker>(
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
                    ExistingPeriodicWorkPolicy.KEEP,
                    workRequest
                )
        }

        /**
         * 安排周期性检查计划状态并生成对话
         * 周期根据设置中的频率进行调整，并考虑对话状态
         */
        fun schedulePeriodicPlanCheck(context: Context) {
            // 获取用户设置的检查频率，默认为1分钟
            val settingsPrefs = context.getSharedPreferences("planner_settings", Context.MODE_PRIVATE)
            val checkInterval = settingsPrefs.getInt("periodic_check_interval", 1) // 默认1分钟
            
            // 保留原有的全局周期性检查任务，用于检查所有计划
            val workRequest = PeriodicWorkRequestBuilder<AutoConversationWorker>(
                checkInterval.toLong(), TimeUnit.MINUTES
            )
                .setInitialDelay(1, TimeUnit.MINUTES)
                .addTag("periodic_plan_check")
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    "periodic_plan_check",
                    ExistingPeriodicWorkPolicy.KEEP,
                    workRequest
                )
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val planId = inputData.getInt("plan_id", -1)
            val conversationId = inputData.getLong("conversationId", -1)
            val status = inputData.getString("status") ?: "not_started"

            // 如果是周期性检查任务，检查所有计划
            if (planId == -1 && conversationId == -1L) {
                processPeriodicCheck()
                return Result.success()
            }

            // 如果是指定计划的周期性检查任务
            if (planId != -1 && conversationId == -1L) {
                processSinglePlan(planId)
                return Result.success()
            }

            if (planId != -1 && conversationId != -1L) {
                // 加载计划信息
                val plan = loadPlanById(planId)
                if (plan != null) {
                    // 检查计划是否在活动时间范围内
                    val isPlanActive = isPlanTimeActive(plan)

                    // 检查计划是否已完成
                    val isPlanCompleted = isPlanCompleted(applicationContext, plan)

                    // 只有当计划在活动时间范围内且未完成时才处理对话生成
                    if (isPlanActive && !isPlanCompleted) {
                        // 使用全局对话管理器安排对话生成任务
                        GlobalConversationManager.getInstance(applicationContext)
                            .scheduleConversationGeneration(plan, conversationId, status)
                             
                        // 对话生成完成后立即触发通知
                        triggerNotificationIfNeeded(plan, conversationId, status)
                    } else {
                        Log.d(TAG, "计划未在活动时间范围内或已完成: ${plan.title}, 活动状态: $isPlanActive, 完成状态: $isPlanCompleted")
                    }
                } else {
                    Log.w(TAG, "找不到计划ID: $planId")
                }

                // 任务完成，返回成功
                return Result.success()
            } else {
                // 参数不完整，直接返回成功
                return Result.success()
            }
        } catch (e: Exception) {
            Log.e(TAG, "自动对话任务发生严重错误: ${e.message}", e)
            // 即使出错也返回成功，避免Worker重复执行
            return Result.success()
        }
    }
    
    /**
     * 根据计划状态和对话内容触发通知（立即发送通知）
     */
    private fun triggerNotificationIfNeeded(plan: PlanItem, conversationId: Long, status: String) {
        try {
            // 检查是否启用了通知
            val settingsPrefs = applicationContext.getSharedPreferences("planner_settings", Context.MODE_PRIVATE)
            val notificationsEnabled = settingsPrefs.getBoolean("notifications_enabled", true)
            
            // 只有启用了通知才发送
            if (notificationsEnabled) {
                val autoReminderManager = ReminderManager.getInstance(applicationContext)
                
                // 加载对话以获取最新消息
                val conversationManager = com.example.bestplannner.components.chat.ConversationManager(applicationContext)
                val conversation = conversationManager.loadConversationById(conversationId)
                
                if (conversation != null && conversation.messages.isNotEmpty()) {
                    // 获取最后一条AI消息
                    val lastAiMessage = conversation.messages.lastOrNull { !it.isUser }
                    if (lastAiMessage != null) {
                        // 立即发送通知
                        autoReminderManager.showReminder(
                            conversationId,
                            conversation.title,
                            lastAiMessage.text.trim()
                        )
                        Log.d(TAG, "已发送通知: ${lastAiMessage.text.trim()}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "触发通知时发生错误: ${e.message}", e)
        }
    }
    
    /**
     * 获取对话最后查看时间
     */
    private fun getLastOpenedTime(conversationId: Long): Long {
        val prefs = applicationContext.getSharedPreferences("notification_tracker", Context.MODE_PRIVATE)
        return prefs.getLong("conversation_${conversationId}_last_opened", 0L)
    }

    /**
     * 处理周期性检查任务
     * 只处理符合条件的计划（在活动时间范围内且状态为未完成）
     */
    private suspend fun processPeriodicCheck() {
        try {
            val planPrefs = applicationContext.getSharedPreferences("plans", Context.MODE_PRIVATE)
            val planCount = planPrefs.getInt("plan_count", 0)

            // 创建一个协程作用域用于并发处理多个计划
            val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            val jobs = mutableListOf<Job>()

            for (i in 0 until planCount) {
                val id = planPrefs.getInt("plan_${i}_id", i)
                val title = planPrefs.getString("plan_${i}_title", "") ?: ""
                val description = planPrefs.getString("plan_${i}_description", "") ?: ""
                val dayValue = planPrefs.getInt("plan_${i}_day", 1)
                val startTime = planPrefs.getString("plan_${i}_startTime", "09:00") ?: "09:00"
                val endTime = planPrefs.getString("plan_${i}_endTime", startTime) ?: startTime
                val isDaily = planPrefs.getBoolean("plan_${i}_isDaily", false)

                val dayOfWeek = DayOfWeek.of(dayValue)
                val plan = com.example.bestplannner.data.PlanItem(id, dayOfWeek, title, description, startTime, endTime, isDaily)

                // 为每个计划创建一个协程任务，实现并发处理
                val job = scope.launch {
                    processSinglePlan(plan.id)
                }
                jobs.add(job)
            }

            // 等待所有计划处理完成
            jobs.joinAll()
        } catch (e: Exception) {
            Log.e(TAG, "处理周期性检查时发生错误: ${e.message}", e)
        }
    }

    /**
     * 处理单个计划（用于周期性任务）
     * 只有符合条件的计划才会生成对话
     */
    private suspend fun processSinglePlan(planId: Int) {
        try {
            // 加载计划信息
            val plan = loadPlanById(planId)
            if (plan != null) {
                // 查找或创建对话
                val conversationManager = com.example.bestplannner.components.chat.ConversationManager(applicationContext)
                val conversationId = conversationManager.findOrCreateConversationForPlan(plan)
                if (conversationId != -1L) {
                    // 获取当前计划状态
                    val currentStatus = getCurrentPlanStatus(applicationContext, plan)

                    // 检查计划是否在活动时间范围内
                    val isPlanActive = isPlanTimeActive(plan)

                    // 检查计划是否已完成
                    val isPlanCompleted = isPlanCompleted(applicationContext, plan)

                    // 只有当计划在活动时间范围内且未完成时才安排对话
                    if (isPlanActive && !isPlanCompleted) {
                        // 使用全局对话管理器安排对话生成任务
                        GlobalConversationManager.getInstance(applicationContext)
                            .scheduleConversationGeneration(plan, conversationId, currentStatus)

                        Log.d(TAG, "为计划安排自动对话生成: ${plan.title}, 状态: $currentStatus")
                    } else {
                        Log.d(TAG, "跳过计划: ${plan.title}, 活动状态: $isPlanActive, 完成状态: $isPlanCompleted")
                    }
                } else {
                    Log.w(TAG, "无法为计划创建或查找对话: ${plan.title}")
                }
            } else {
                Log.w(TAG, "找不到计划ID: $planId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理单个计划时发生错误: $planId, 错误: ${e.message}", e)
        }
    }

    /**
     * 处理单个计划
     */
    private suspend fun processPlan(plan: com.example.bestplannner.data.PlanItem) {
        try {
            // 查找或创建对话
            val conversationManager = com.example.bestplannner.components.chat.ConversationManager(applicationContext)
            val conversationId = conversationManager.findOrCreateConversationForPlan(plan)
            if (conversationId != -1L) {
                // 获取当前计划状态
                val currentStatus = getCurrentPlanStatus(applicationContext, plan)

                // 检查计划是否在活动时间范围内
                val isPlanActive = isPlanTimeActive(plan)

                // 检查计划是否已完成
                val isPlanCompleted = isPlanCompleted(applicationContext, plan)

                // 只有当计划在活动时间范围内且未完成时才安排对话
                if (isPlanActive && !isPlanCompleted) {
                    // 使用全局对话管理器安排对话生成任务
                    GlobalConversationManager.getInstance(applicationContext)
                        .scheduleConversationGeneration(plan, conversationId, currentStatus)

                    Log.d(TAG, "为计划安排自动对话生成: ${plan.title}, 状态: $currentStatus")
                } else {
                    Log.d(TAG, "跳过计划: ${plan.title}, 活动状态: $isPlanActive, 完成状态: $isPlanCompleted")
                }
            } else {
                Log.w(TAG, "无法为计划创建或查找对话: ${plan.title}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理计划时发生错误: ${plan.title}, 错误: ${e.message}", e)
        }
    }

    /**
     * 获取当前计划状态
     */
    private fun getCurrentPlanStatus(context: Context, plan: com.example.bestplannner.data.PlanItem): String {
        // 直接从plan_status SharedPreferences获取计划状态
        val prefs = context.getSharedPreferences("plan_status", Context.MODE_PRIVATE)
        return prefs.getString("plan_${plan.id}_status", "not_started") ?: "not_started"
    }

    /**
     * 检查计划是否已完成
     */
    private fun isPlanCompleted(context: Context, plan: com.example.bestplannner.data.PlanItem): Boolean {
        // 检查计划是否已完成
        val planStatusPrefs = context.getSharedPreferences("plan_status", Context.MODE_PRIVATE)
        return planStatusPrefs.getBoolean("plan_${plan.id}_completed", false)
    }

    /**
     * 检查计划是否在活动时间范围内
     */
    private fun isPlanTimeActive(plan: com.example.bestplannner.data.PlanItem): Boolean {
        val now = LocalDateTime.now()
        val currentTime = now.toLocalTime()
        val currentDay = now.dayOfWeek

        // 检查是否是当天的计划
        if (plan.dayOfWeek == currentDay) {
            try {
                val planStartTime = org.threeten.bp.LocalTime.parse(plan.startTime)
                val planEndTime = org.threeten.bp.LocalTime.parse(plan.endTime)

                // 检查当前时间是否在计划时间范围内
                return isTimeInRange(currentTime, planStartTime, planEndTime)
            } catch (e: Exception) {
                Log.e(TAG, "解析计划时间时出错: ${e.message}")
                return false
            }
        }

        // 如果是每日计划，也返回true
        if (plan.isDaily) {
            try {
                val planStartTime = org.threeten.bp.LocalTime.parse(plan.startTime)
                val planEndTime = org.threeten.bp.LocalTime.parse(plan.endTime)

                // 检查当前时间是否在计划时间范围内
                return isTimeInRange(currentTime, planStartTime, planEndTime)
            } catch (e: Exception) {
                Log.e(TAG, "解析每日计划时间时出错: ${e.message}")
                return false
            }
        }

        return false
    }

    /**
     * 检查给定时间是否在时间范围内
     */
    private fun isTimeInRange(currentTime: org.threeten.bp.LocalTime, startTime: org.threeten.bp.LocalTime, endTime: org.threeten.bp.LocalTime): Boolean {
        return if (endTime.isAfter(startTime) || endTime == startTime) {
            // 正常情况：结束时间晚于开始时间（或相等）
            !currentTime.isBefore(startTime) && !currentTime.isAfter(endTime)
        } else {
            // 跨日期情况：结束时间早于开始时间（例如从晚上10点到早上6点）
            !currentTime.isBefore(startTime) || !currentTime.isAfter(endTime)
        }
    }

    /**
     * 根据计划ID加载计划项
     */
    private fun loadPlanById(planId: Int): com.example.bestplannner.data.PlanItem? {
        val planPrefs = applicationContext.getSharedPreferences("plans", Context.MODE_PRIVATE)
        val planCount = planPrefs.getInt("plan_count", 0)

        for (i in 0 until planCount) {
            val id = planPrefs.getInt("plan_${i}_id", i)
            if (id == planId) {
                val title = planPrefs.getString("plan_${i}_title", "") ?: ""
                val description = planPrefs.getString("plan_${i}_description", "") ?: ""
                val dayValue = planPrefs.getInt("plan_${i}_day", 1)
                val startTime = planPrefs.getString("plan_${i}_startTime", "09:00") ?: "09:00"
                val endTime = planPrefs.getString("plan_${i}_endTime", startTime) ?: startTime
                val isDaily = planPrefs.getBoolean("plan_${i}_isDaily", false)

                val dayOfWeek = DayOfWeek.of(dayValue)

                return com.example.bestplannner.data.PlanItem(id, dayOfWeek, title, description, startTime, endTime, isDaily)
            }
        }

        return null
    }
}