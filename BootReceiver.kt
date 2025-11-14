package com.example.bestplannner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.work.WorkManager
import com.example.bestplannner.notification.ReminderManager
import com.example.bestplannner.data.PlanItem
import com.example.bestplannner.worker.AutoConversationWorker
import com.example.bestplannner.components.chat.ConversationManager
import org.threeten.bp.DayOfWeek
import org.threeten.bp.LocalDateTime
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED == intent.action ||
            "android.intent.action.QUICKBOOT_POWERON" == intent.action ||
            Intent.ACTION_MY_PACKAGE_REPLACED == intent.action) {

            // 检查是否启用了通知
            val settingsPrefs = context.getSharedPreferences("planner_settings", Context.MODE_PRIVATE)
            if (settingsPrefs.getBoolean("notifications_enabled", true)) {
                // 应用安装、更新或设备重启时启动自动对话系统（但不发送通知）
                android.util.Log.d("BootReceiver", "自动对话系统已启动")

                // 启动周期性计划检查（使用用户设置的频率）
                AutoConversationWorker.schedulePeriodicPlanCheck(context)
                android.util.Log.d("BootReceiver", "周期性计划检查已启动")

                // 为每个计划启动独立的周期性检查任务
                schedulePeriodicChecksForAllPlans(context)
                android.util.Log.d("BootReceiver", "各计划独立周期性检查已启动")

                // 检查当前是否在计划作用范围内并启动自动对话
                checkAndStartAutoConversation(context)
                android.util.Log.d("BootReceiver", "开机自动对话检查已完成")
            } else {
                android.util.Log.d("BootReceiver", "通知被禁用，跳过启动对话系统")
            }
        }
    }

    /**
     * 为所有计划安排独立的周期性检查任务
     */
    private fun schedulePeriodicChecksForAllPlans(context: Context) {
        val plans = loadAllPlans(context)
        plans.forEach { plan ->
            AutoConversationWorker.schedulePeriodicPlanCheckForPlan(context, plan)
            android.util.Log.d("BootReceiver", "已为计划安排独立周期性检查: ${plan.title}")
        }
    }

    /**
     * 加载所有计划项
     */
    private fun loadAllPlans(context: Context): List<PlanItem> {
        val plans = mutableListOf<PlanItem>()
        val planPrefs = context.getSharedPreferences("plans", Context.MODE_PRIVATE)
        val planCount = planPrefs.getInt("plan_count", 0)

        for (i in 0 until planCount) {
            val id = planPrefs.getInt("plan_${i}_id", i)
            val title = planPrefs.getString("plan_${i}_title", "") ?: ""
            val description = planPrefs.getString("plan_${i}_description", "") ?: ""
            val dayValue = planPrefs.getInt("plan_${i}_day", 1)  // 默认值改为1（星期一）
            val startTime = planPrefs.getString("plan_${i}_startTime", "09:00") ?: "09:00"
            val endTime = planPrefs.getString("plan_${i}_endTime", startTime) ?: startTime
            val isDaily = planPrefs.getBoolean("plan_${i}_isDaily", false)

            val dayOfWeek = DayOfWeek.of(dayValue)

            plans.add(com.example.bestplannner.data.PlanItem(id, dayOfWeek, title, description, startTime, endTime, isDaily))
        }

        return plans
    }

    /**
     * 检查当前是否在计划作用范围内并启动自动对话
     */
    private fun checkAndStartAutoConversation(context: Context) {
        val conversationManager = ConversationManager(context)
        val plans = loadAllPlans(context)
        val now = org.threeten.bp.LocalDateTime.now()

        // 检查所有计划中是否有当前正在活动的
        plans.forEachIndexed { index, plan ->
            // 查找或创建与计划相关的对话
            val conversationId = conversationManager.findOrCreateConversationForPlan(plan)
            if (conversationId != -1L) {
                // 添加随机延迟避免扎堆
                try {
                    Thread.sleep((1000..5000).random().toLong()) // 1-5秒随机延迟
                } catch (e: InterruptedException) {
                    // 忽略中断异常
                }
                // 直接安排自动对话生成
                AutoConversationWorker.scheduleAutoConversationByPlanStatus(context, plan, conversationId)
                android.util.Log.d("BootReceiver", "已为当前活动计划安排自动对话: ${plan.title}")
            }
        }
    }
}