package com.example.bestplannner.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.bestplannner.MainActivity
import com.example.bestplannner.R

/**
 * 提醒管理器
 * 负责处理对话系统中的提醒功能
 */
class ReminderManager(private val context: Context) {

    companion object {
        private const val CHANNEL_ID = "conversation_reminder_channel"
        private const val CHANNEL_NAME = "对话提醒"
        private const val CHANNEL_DESCRIPTION = "当自动对话系统有新信息时的提醒"
        private const val BASE_NOTIFICATION_ID = 2000
        
        @Volatile
        private var INSTANCE: ReminderManager? = null

        fun getInstance(context: Context): ReminderManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ReminderManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    init {
        createNotificationChannel()
    }

    /**
     * 创建通知渠道（Android 8.0及以上版本需要）
     * 注意：Android 8.0及以上版本中，通知渠道一旦创建就无法修改，
     * 所以当设置改变时需要删除旧渠道并创建新渠道
     */
    fun createNotificationChannel() {
        // 获取通知设置
        val plannerPreferences = context.getSharedPreferences("planner_settings", Context.MODE_PRIVATE)
        val notificationSoundEnabled = plannerPreferences.getBoolean("notification_sound_enabled", true)
        val notificationVibrationEnabled = plannerPreferences.getBoolean("notification_vibration_enabled", true)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // 删除已存在的渠道（如果存在）
            val existingChannel = notificationManager.getNotificationChannel(CHANNEL_ID)
            if (existingChannel != null) {
                notificationManager.deleteNotificationChannel(CHANNEL_ID)
            }
            
            // 创建新的通知渠道
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = CHANNEL_DESCRIPTION
                
                // 设置通知声音
                if (notificationSoundEnabled) {
                    // 使用系统默认通知声音
                    setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), null)
                } else {
                    // 禁用声音
                    setSound(null, null)
                }
                
                // 设置震动模式
                if (notificationVibrationEnabled) {
                    // 设置震动模式：长-短-长 (1秒震动，0.5秒暂停，1秒震动)
                    vibrationPattern = longArrayOf(0, 1000, 500, 1000)
                    enableVibration(true)
                } else {
                    enableVibration(false)
                }
                
                // 确保锁屏显示通知
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * 显示提醒通知
     * 当自动对话系统有新信息时调用此方法
     *
     * @param conversationId 对话ID
     * @param title 通知标题
     * @param content 通知内容
     */
    fun showReminder(conversationId: Long, title: String, content: String) {
        // 创建点击通知后要跳转到对话空间的意图
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("open_chat_with_conversation", conversationId)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context, 
            (BASE_NOTIFICATION_ID + conversationId).toInt(), 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // 获取通知设置
        val plannerPreferences = context.getSharedPreferences("planner_settings", Context.MODE_PRIVATE)
        val notificationSoundEnabled = plannerPreferences.getBoolean("notification_sound_enabled", true)
        val notificationVibrationEnabled = plannerPreferences.getBoolean("notification_vibration_enabled", true)

        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.star_hexagon)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)  // 点击后自动取消通知
            
        // 在Android 8.0以下版本中设置声音和震动
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            // 设置通知声音
            if (notificationSoundEnabled) {
                // 使用系统默认通知声音
                notificationBuilder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            }
            
            // 设置震动
            if (notificationVibrationEnabled) {
                // 设置震动模式：长-短-长 (1秒震动，0.5秒暂停，1秒震动)
                notificationBuilder.setVibrate(longArrayOf(0, 1000, 500, 1000))
            }
        }

        val notification = notificationBuilder.build()
        val notificationManager = NotificationManagerCompat.from(context)
        
        // 检查通知权限后再发送通知
        if (notificationManager.areNotificationsEnabled()) {
            try {
                // 使用唯一的ID确保通知正确显示和取消
                notificationManager.notify((BASE_NOTIFICATION_ID + conversationId).toInt(), notification)
            } catch (securityException: SecurityException) {
                // 处理权限被拒绝的情况
                android.util.Log.w("ReminderManager", "通知权限被拒绝", securityException)
            }
        } else {
            android.util.Log.w("ReminderManager", "通知未启用，无法显示通知")
        }
    }

    /**
     * 取消提醒
     */
    fun cancelReminder(conversationId: Long) {
        val notificationManager = NotificationManagerCompat.from(context)
        if (notificationManager.areNotificationsEnabled()) {
            try {
                notificationManager.cancel((BASE_NOTIFICATION_ID + conversationId).toInt())
            } catch (securityException: SecurityException) {
                android.util.Log.w("ReminderManager", "通知权限被拒绝", securityException)
            }
        }
    }
}