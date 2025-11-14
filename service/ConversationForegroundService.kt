package com.example.bestplannner.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.bestplannner.R

class ConversationForegroundService : Service() {
    companion object {
        private const val CHANNEL_ID = "ConversationServiceChannel"
        private const val NOTIFICATION_ID = 2000
        private const val LOG_TAG = "ConversationService"
        
        fun startService(context: Context) {
            try {
                val startIntent = Intent(context, ConversationForegroundService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(startIntent)
                } else {
                    context.startService(startIntent)
                }
            } catch (e: Exception) {
                android.util.Log.e(LOG_TAG, "启动前台服务失败: ${e.message}", e)
            }
        }
        
        fun stopService(context: Context) {
            try {
                val stopIntent = Intent(context, ConversationForegroundService::class.java)
                context.stopService(stopIntent)
            } catch (e: Exception) {
                android.util.Log.e(LOG_TAG, "停止前台服务失败: ${e.message}", e)
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        try {
            createNotificationChannel()
            startForeground(NOTIFICATION_ID, createNotification())
        } catch (e: Exception) {
            android.util.Log.e(LOG_TAG, "创建前台服务失败: ${e.message}", e)
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY // 服务被杀死后会尝试重新创建
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    override fun onDestroy() {
        super.onDestroy()
        try {
            stopForeground(true)
        } catch (e: Exception) {
            android.util.Log.e(LOG_TAG, "停止前台失败: ${e.message}", e)
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "对话服务",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "用于保持自动对话系统在后台运行"
                    setShowBadge(false)
                }
                
                val manager = getSystemService(NotificationManager::class.java)
                manager.createNotificationChannel(channel)
            } catch (e: Exception) {
                android.util.Log.e(LOG_TAG, "创建通知渠道失败: ${e.message}", e)
            }
        }
    }
    
    private fun createNotification(): Notification {
        return try {
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("智能助手运行中")
                .setContentText("自动对话系统正在后台运行")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .build()
        } catch (e: Exception) {
            android.util.Log.e(LOG_TAG, "创建通知失败: ${e.message}", e)
            // 返回一个基本通知
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("服务运行中")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .build()
        }
    }
}