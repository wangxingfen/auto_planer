package com.example.bestplannner.data

import org.threeten.bp.DayOfWeek

data class PlanItem(
    val id: Int,
    val dayOfWeek: DayOfWeek,
    val title: String,
    val description: String = "",
    val startTime: String,
    val endTime: String,
    val isDaily: Boolean,
    val isCompleted: Boolean = false
) {
    companion object {
        /**
         * 重置计划的通知记录
         */
        fun resetNotificationForPlan(context: android.content.Context, plan: PlanItem) {
            // 这里可以实现通知记录的重置逻辑
        }

        /**
         * 获取计划的通知状态
         */
        fun getNotificationState(context: android.content.Context, plan: PlanItem): NotificationState {
            // 这里可以实现获取通知状态的逻辑
            return NotificationState.NOTIFICATION_NOT_SET
        }

        /**
         * 从JSON字符串转换为PlanItem对象
         */
        fun fromJson(json: String): PlanItem? {
            return try {
                // 简单的JSON解析实现
                val parts = json.split(";")
                if (parts.size < 7) return null

                val id = parts[0].toInt()
                val dayOfWeek = DayOfWeek.valueOf(parts[1])
                val title = parts[2]
                val description = parts[3]
                val startTime = parts[4]
                val endTime = parts[5]
                val isDaily = parts[6].toBoolean()
                val isCompleted = if (parts.size >=8) parts[7].toBoolean() else false

                PlanItem(id, dayOfWeek, title, description, startTime, endTime, isDaily, isCompleted)
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * 通知状态枚举
     */
    enum class NotificationState {
        NOTIFICATION_SET,
        NOTIFICATION_NOT_SET
    }

    /**
     * 转换为JSON字符串
     */
    fun toJson(): String {
        // 简单的JSON序列化实现，使用分号分隔各个字段
        return "$id;${dayOfWeek.name};$title;$description;$startTime;$endTime;$isDaily;$isCompleted"
    }
    
    /**
     * 创建一个具有指定完成状态的副本
     */
    fun copyWithCompletionStatus(isCompleted: Boolean): PlanItem {
        return PlanItem(id, dayOfWeek, title, description, startTime, endTime, isDaily, isCompleted)
    }
}