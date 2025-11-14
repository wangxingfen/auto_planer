package com.example.bestplannner.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.example.bestplannner.worker.AutoConversationWorker
import android.util.Log
import android.os.Build

/**
 * 电源管理模式接收器
 * 当设备退出省电模式时重新安排自动对话任务
 */
class PowerSaveModeReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "PowerSaveModeReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && 
            "android.os.action.POWER_SAVE_MODE_CHANGED" == intent.action) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val isInPowerSaveMode = powerManager.isPowerSaveMode
            
            if (!isInPowerSaveMode) {
                // 设备退出省电模式，重新安排周期性任务
                AutoConversationWorker.schedulePeriodicPlanCheck(context)
                Log.d(TAG, "设备退出省电模式，重新安排自动对话任务")
            } else {
                Log.d(TAG, "设备进入省电模式")
            }
        }
    }
}