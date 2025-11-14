package com.example.bestplannner

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.work.WorkManager
import com.example.bestplannner.navigation.AppNavigation
import com.example.bestplannner.notification.ReminderManager
import com.example.bestplannner.data.PlanItem
import com.example.bestplannner.ui.theme.PlannerTheme
import com.example.bestplannner.worker.AutoConversationWorker
import com.example.bestplannner.components.chat.ConversationManager
import com.example.bestplannner.components.chat.GlobalConversationManager
import com.jakewharton.threetenabp.AndroidThreeTen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var autoReminderManager: ReminderManager
    private var navController: NavHostController? = null
    private lateinit var ignoreBatteryOptimizationsLauncher: ActivityResultLauncher<Intent>
    private lateinit var requestMultiplePermissionsLauncher: ActivityResultLauncher<Array<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 初始化ThreeTenABP库
        AndroidThreeTen.init(this)

        // 初始化自动提醒管理器
        autoReminderManager = ReminderManager.getInstance(this)

        // 应用启动时自动启动对话系统（但不发送通知）
        lifecycleScope.launch {
            this@MainActivity.startAutoConversationSystem()
        }

        // 系统启动时自动检测用户计划并安排对话生成
        lifecycleScope.launch {
            this@MainActivity.detectAndSchedulePlans()
        }

        // 确保默认设置被保存，这样其他组件可以正确读取
        initializeDefaultSettings()

        // 初始化Activity Result Launchers
        ignoreBatteryOptimizationsLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            // 可以在这里处理结果
        }

        requestMultiplePermissionsLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            // 处理权限请求结果
            permissions.forEach { (permission, isGranted) ->
                if (!isGranted) {
                    android.util.Log.w("MainActivity", "权限被拒绝: $permission")
                }
            }
        }

        // 确保屏幕在锁屏时也能显示
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        // 如果设备有锁屏，需要解除锁屏显示通知
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            val keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        }

        // 检查是否是首次启动，避免每次打开都请求权限
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val isFirstLaunch = prefs.getBoolean("is_first_launch", true)

        if (isFirstLaunch) {
            // 首次启动时请求所有必要的权限
            requestAllPermissions()

            // 标记已启动过
            with(prefs.edit()) {
                putBoolean("is_first_launch", false)
                apply()
            }
        } else {
            // 非首次启动时也要确保有通知权限
            requestNotificationPermission()
        }

        // 设置周期性通知检查 - 使用新的分状态通知间隔
        setupPeriodicNotificationCheck()

        // 为所有现有对话安排通知检查
        scheduleAllConversationNotifications()
        
        // 启动前台服务以确保自动对话系统在后台持续运行
        com.example.bestplannner.service.ConversationForegroundService.startService(this)

        setContent {
            PlannerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    this@MainActivity.navController = navController
                    AppNavigation(navController)
                }
            }
        }

        // 检查是否有来自通知的跳转意图
        handleNotificationIntent(intent)
    }

    /**
     * 为所有现有对话安排通知检查
     */
    private fun scheduleAllConversationNotifications() {
        val conversationManager = com.example.bestplannner.components.chat.ConversationManager(this)
        val conversations = conversationManager.loadConversationsMetadata()

        conversations.forEach { conversation ->
            // 为每个对话安排定期通知检查
            val data = androidx.work.Data.Builder()
                .putLong("conversation_id", conversation.id)
                .putBoolean("is_general_notification", true) // 标记为普通对话通知
                .build()

            // 60分钟后检查对话状态
            val workRequest = androidx.work.OneTimeWorkRequestBuilder<com.example.bestplannner.worker.AutoConversationWorker>()
                .setInputData(data)
                .setInitialDelay(60, java.util.concurrent.TimeUnit.MINUTES)
                .addTag("conversation_notification_${conversation.id}")
                .build()

            androidx.work.WorkManager.getInstance(this).enqueue(workRequest)
        }
    }

    /**
     * 初始化默认设置值
     */
    private fun initializeDefaultSettings() {
        val aiSettings = getSharedPreferences("ai_settings", Context.MODE_PRIVATE)
        val editor = aiSettings.edit()

        // 确保AI设置的默认值被保存
        if (!aiSettings.contains("base_url")) {
            editor.putString("base_url", "https://api.siliconflow.cn/v1").apply()
        }
        if (!aiSettings.contains("api_key")) {
            editor.putString("api_key", "sk-dplqbsomzrruqpclshnwysqlmhpdbylvfxbnvtvwygvsvfaz").apply()
        }
        if (!aiSettings.contains("model_name")) {
            editor.putString("model_name", "THUDM/glm-4-9b-chat").apply()
        }
        if (!aiSettings.contains("system_prompt")) {
            editor.putString("system_prompt", "请认真扮演一位帮助用户达成计划的占星师").apply()
        }
        if (!aiSettings.contains("temperature")) {
            editor.putFloat("temperature", 0.7f).apply()
        }
        if (!aiSettings.contains("max_tokens")) {
            editor.putInt("max_tokens", 2048).apply()
        }
        if (!aiSettings.contains("conversation_memory")) {
            editor.putInt("conversation_memory", 5).apply()
        }

        // 确保通知设置的默认值也被保存
        val plannerPreferences = getSharedPreferences("planner_settings", Context.MODE_PRIVATE)
        val plannerEditor = plannerPreferences.edit()

        if (!plannerPreferences.contains("not_started_notification_interval")) {
            plannerEditor.putInt("not_started_notification_interval", 5).apply()
        }
        if (!plannerPreferences.contains("working_notification_interval")) {
            plannerEditor.putInt("working_notification_interval", 15).apply()
        }
        // 添加通知启用状态的默认值
        if (!plannerPreferences.contains("notifications_enabled")) {
            plannerEditor.putBoolean("notifications_enabled", true).apply()
        }
        // 添加周期性检查间隔的默认值
        if (!plannerPreferences.contains("periodic_check_interval")) {
            plannerEditor.putInt("periodic_check_interval", 1).apply()
        }
        // 添加通知声音和震动的默认值
        if (!plannerPreferences.contains("notification_sound_enabled")) {
            plannerEditor.putBoolean("notification_sound_enabled", true).apply()
        }
        if (!plannerPreferences.contains("notification_vibration_enabled")) {
            plannerEditor.putBoolean("notification_vibration_enabled", true).apply()
        }

        // 同时也为对话级别设置初始化默认值，这样就不需要用户手动点击保存设置按钮
        // 获取所有可能的对话特定设置前缀并初始化它们
        initializeConversationLevelDefaults(aiSettings, editor)
    }

    /**
     * 初始化对话级别设置的默认值
     */
    private fun initializeConversationLevelDefaults(aiSettings: android.content.SharedPreferences, editor: android.content.SharedPreferences.Editor) {
        // 这里我们不需要实际初始化每个对话的设置，因为当创建新对话时，
        // SettingsScreen会自动使用remember和默认值来显示设置
        // 我们只是确保全局设置已经正确初始化
    }

    /**
     * 检测所有计划并为活动中的计划安排对话生成
     */
    private suspend fun detectAndSchedulePlans() {
        try {
            val planPrefs = getSharedPreferences("plans", Context.MODE_PRIVATE)
            val planCount = planPrefs.getInt("plan_count", 0)

            val conversationManager = ConversationManager(this)

            for (i in 0 until planCount) {
                val id = planPrefs.getInt("plan_${i}_id", i)
                val title = planPrefs.getString("plan_${i}_title", "") ?: ""
                val description = planPrefs.getString("plan_${i}_description", "") ?: ""
                val dayValue = planPrefs.getInt("plan_${i}_day", 1)  // 默认值改为1（星期一）
                val startTime = planPrefs.getString("plan_${i}_startTime", "09:00") ?: "09:00"
                val endTime = planPrefs.getString("plan_${i}_endTime", startTime) ?: startTime
                val isDaily = planPrefs.getBoolean("plan_${i}_isDaily", false)

                val dayOfWeek = org.threeten.bp.DayOfWeek.of(dayValue)
                val plan = PlanItem(id, dayOfWeek, title, description, startTime, endTime, isDaily)

                // 查找或创建与计划相关的对话空间
                val conversationId = conversationManager.findOrCreateConversationForPlan(plan)
                if (conversationId != -1L) {
                    android.util.Log.d("MainActivity", "系统启动时检测到活动计划: ${plan.title}, 已创建对话空间")
                } else {
                    android.util.Log.w("MainActivity", "无法为活动计划创建或查找对话: ${plan.title}")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "检测用户计划时发生错误: ${e.message}", e)
        }
    }

    /**
     * 获取当前计划状态
     */
    private fun getCurrentPlanStatus(planId: Int): String {
        val prefs = getSharedPreferences("plan_status", Context.MODE_PRIVATE)
        return prefs.getString("plan_${planId}_status", "not_started") ?: "not_started"
    }

    /**
     * 处理来自通知的跳转意图
     */
    private fun handleNotificationIntent(intent: Intent?) {
        if (intent != null) {
            val conversationId = intent.getLongExtra("open_chat_with_conversation", -1L)

            if (conversationId != -1L) {
                // 点击通知后手动取消通知
                val autoReminderManager = ReminderManager.getInstance(this)
                autoReminderManager.cancelReminder(conversationId)
                
                // 立即取消通知，不等待延迟
                // 增加延迟以确保NavHost已经完全初始化完成
                Handler(Looper.getMainLooper()).postDelayed({
                    // 跳转到聊天界面并打开指定对话
                    val targetRoute = "chat/$conversationId"
                    if (navController?.currentDestination?.route != targetRoute) {
                        navController?.navigate(targetRoute)
                    }
                }, 100) // 减少延迟到100毫秒以提高响应速度
            }
        }
    }

    /**
     * 根据通知ID查找对话ID
     */
    private fun findConversationIdByNotificationId(notificationId: Long): Long {
        // 移除过时的方法，不再需要
        return -1L
    }

    /**
     * 根据计划ID查找对话ID
     */
    private fun findConversationIdForPlan(planId: Long): Long {
        // 移除过时的方法，不再需要
        return -1L
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // 处理从通知点击进入应用的情况
        handleNotificationIntent(intent)
    }

    private fun requestAllPermissions() {
        // 请求通知权限
        requestNotificationPermission()

        // 请求忽略电池优化（允许后台运行）
        requestIgnoreBatteryOptimizations()

        // 请求其他重要权限
        requestOtherEssentialPermissions()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // 使用ActivityResultContracts处理权限请求
            val requestPermissionLauncher = registerForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { isGranted: Boolean ->
                if (isGranted) {
                    // 权限被授予
                    android.util.Log.d("MainActivity", "通知权限已授予")
                } else {
                    // 权限被拒绝，可以引导用户到设置页面手动开启
                    android.util.Log.w("MainActivity", "通知权限被拒绝")
                    showPermissionSettingsDialog()
                }
            }

            // 检查是否已有权限
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                // 请求权限
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                android.util.Log.d("MainActivity", "已有通知权限")
                
                // 即使已有权限，也要重新创建通知渠道以确保设置生效
                autoReminderManager.createNotificationChannel()
            }
        } else {
            // 对于 Android 12 及更低版本，也要确保通知渠道已创建
            autoReminderManager.createNotificationChannel()
        }
    }

    // 添加请求忽略电池优化的方法（允许后台运行）
    private fun requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(POWER_SERVICE) as android.os.PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    intent.data = Uri.parse("package:$packageName")
                    ignoreBatteryOptimizationsLauncher.launch(intent)
                } catch (e: Exception) {
                    // 如果无法直接请求，则引导用户去设置页面
                    showPermissionSettingsDialog()
                }
            }
        }
    }

    // 请求其他核心权限
    private fun requestOtherEssentialPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // 存储权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        // 位置权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        // 联系人权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.READ_CONTACTS)
        }

        // 日历权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.READ_CALENDAR)
        }

        // 相机权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.CAMERA)
        }

        // 麦克风权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }

        // 电话状态权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.READ_PHONE_STATE)
        }

        // 短信权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.READ_SMS)
        }

        // 如果有需要请求的权限，则批量请求
        if (permissionsToRequest.isNotEmpty()) {
            requestMultiplePermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    // 引导用户到设置页面手动授予权限
    private fun showPermissionSettingsDialog() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri: Uri = Uri.fromParts("package", packageName, null)
        intent.data = uri
        startActivity(intent)
    }

    /**
     * 记录用户查看了通知的时间
     */
    private fun recordNotificationOpened(planId: Int) {
        val notificationPrefs = getSharedPreferences("notification_tracker", Context.MODE_PRIVATE)
        with(notificationPrefs.edit()) {
            putLong("plan_${planId}_last_opened", System.currentTimeMillis())
            apply()
        }
    }

    /**
     * 启动自动对话系统（但不发送通知）
     * 只启动周期性检查任务，不会为所有计划触发对话生成
     */
    private suspend fun startAutoConversationSystem() {
        // 启动周期性计划检查（使用用户设置的频率）
        try {
            AutoConversationWorker.schedulePeriodicPlanCheck(this@MainActivity)
            android.util.Log.d("MainActivity", "已启动周期性计划检查")
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "启动周期性计划检查失败: ${e.message}", e)
        }
        
        android.util.Log.d("MainActivity", "自动对话系统已初始化并启动")
    }

    /**
     * 加载所有计划项
     */
    private fun loadAllPlans(): List<PlanItem> {
        val plans = mutableListOf<PlanItem>()
        val planPrefs = getSharedPreferences("plans", Context.MODE_PRIVATE)
        val planCount = planPrefs.getInt("plan_count", 0)

        for (i in 0 until planCount) {
            val id = planPrefs.getInt("plan_${i}_id", i)
            val title = planPrefs.getString("plan_${i}_title", "") ?: ""
            val description = planPrefs.getString("plan_${i}_description", "") ?: ""
            val dayValue = planPrefs.getInt("plan_${i}_day", 1)  // 默认值改为1（星期一）
            val startTime = planPrefs.getString("plan_${i}_startTime", "09:00") ?: "09:00"
            val endTime = planPrefs.getString("plan_${i}_endTime", startTime) ?: startTime
            val isDaily = planPrefs.getBoolean("plan_${i}_isDaily", false)

            val dayOfWeek = org.threeten.bp.DayOfWeek.of(dayValue)

            plans.add(PlanItem(id, dayOfWeek, title, description, startTime, endTime, isDaily))
        }

        return plans
    }

    /**
     * 设置周期性通知检查
     */
    private fun setupPeriodicNotificationCheck() {
        // 取消所有旧的worker工作，但保留持续提醒标签
        val workManager = WorkManager.getInstance(this)
        workManager.cancelAllWorkByTag("plan_notification_work")
        workManager.cancelAllWorkByTag("system_notification_work")
        // 不取消持续提醒，只取消普通自动提醒
        workManager.cancelAllWorkByTag("auto_reminder")

        // 初始化自动提醒管理器
        val autoReminderManager = ReminderManager.getInstance(this)

        // 启动周期性计划检查（使用用户设置的频率）
        try {
            AutoConversationWorker.schedulePeriodicPlanCheck(this@MainActivity)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "启动周期性计划检查失败: ${e.message}", e)
        }

        android.util.Log.d("MainActivity", "自动对话系统已初始化并启动")
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    PlannerTheme {
        Text("Preview placeholder")
    }
}