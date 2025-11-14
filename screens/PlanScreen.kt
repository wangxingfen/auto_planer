package com.example.bestplannner.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.navigation.NavController
import com.example.bestplannner.worker.AutoConversationWorker
import org.threeten.bp.DayOfWeek
import java.util.*
import java.util.concurrent.TimeUnit

// 自定义滚动时间选择器对话框组件
@Composable
fun ScrollableTimePickerDialog(
    onCancel: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit,
    initialHour: Int,
    initialMinute: Int
) {
    // 使用rememberSaveable确保配置更改时状态保持
    var selectedHour by rememberSaveable { mutableStateOf(initialHour) }
    var selectedMinute by rememberSaveable { mutableStateOf(initialMinute) }

    // 创建小时和分钟的列表状态，用于滚动到指定位置
    // 为了实现循环效果，我们将索引设置为中间位置
    val hourListState = rememberLazyListState()
    val minuteListState = rememberLazyListState()

    // 小时列表数据 (0-23) - 扩展以支持循环滚动
    val hours = (0 until 24 * 5).map { it % 24 }.toList() // 5个循环周期
    // 分钟列表数据 (0-59) - 扩展以支持循环滚动
    val minutes = (0 until 60 * 5).map { it % 60 }.toList() // 5个循环周期

    // 初始化滚动位置到中间区域
    LaunchedEffect(hourListState) {
        hourListState.scrollToItem(24 * 2 + initialHour)
    }

    LaunchedEffect(minuteListState) {
        minuteListState.scrollToItem(60 * 2 + initialMinute)
    }

    // 监听滚动位置变化，更新选中的时间
    // 计算小时列表中视觉上居中的项
    LaunchedEffect(hourListState) {
        snapshotFlow { hourListState.layoutInfo }
        .collect { layoutInfo ->
            if (layoutInfo.visibleItemsInfo.isNotEmpty()) {
                // 计算垂直中心位置
                val centerPosition = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
                val center = layoutInfo.viewportStartOffset + centerPosition / 2

                // 查找中心位置对应的项
                val centerItem = layoutInfo.visibleItemsInfo.find { itemInfo ->
                    itemInfo.offset <= center && itemInfo.offset + itemInfo.size >= center
                }

                centerItem?.let { item ->
                    selectedHour = hours[item.index]
                }
            }
        }
    }

    // 计算分钟列表中视觉上居中的项
    LaunchedEffect(minuteListState) {
        snapshotFlow { minuteListState.layoutInfo }
        .collect { layoutInfo ->
            if (layoutInfo.visibleItemsInfo.isNotEmpty()) {
                // 计算垂直中心位置
                val centerPosition = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
                val center = layoutInfo.viewportStartOffset + centerPosition / 2

                // 查找中心位置对应的项
                val centerItem = layoutInfo.visibleItemsInfo.find { itemInfo ->
                    itemInfo.offset <= center && itemInfo.offset + itemInfo.size >= center
                }

                centerItem?.let { item ->
                    selectedMinute = minutes[item.index]
                }
            }
        }
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("选择时间") },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // 小时选择器
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("小时", style = MaterialTheme.typography.labelMedium)
                        Box(
                            modifier = Modifier
                                .height(200.dp)
                                .fillMaxWidth()
                        ) {
                            // 背景指示器（选中区域）
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .height(40.dp)
                                    .fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                        RoundedCornerShape(8.dp)
                                    )
                            )

                            LazyColumn(
                                state = hourListState,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(hours.size) { index ->
                                    val hour = hours[index]
                                    // 判断是否为中心项（视觉上）
                                    val isCentered = hour == selectedHour
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(40.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = String.format("%02d", hour),
                                            style = MaterialTheme.typography.headlineSmall.copy(
                                                color = if (isCentered) MaterialTheme.colorScheme.primary
                                                       else MaterialTheme.colorScheme.onSurface,
                                                fontSize = if (isCentered) MaterialTheme.typography.headlineMedium.fontSize
                                                         else MaterialTheme.typography.headlineSmall.fontSize
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 分隔符
                    Text(
                        text = ":",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    // 分钟选择器
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("分钟", style = MaterialTheme.typography.labelMedium)
                        Box(
                            modifier = Modifier
                                .height(200.dp)
                                .fillMaxWidth()
                        ) {
                            // 背景指示器（选中区域）
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .height(40.dp)
                                    .fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                        RoundedCornerShape(8.dp)
                                    )
                            )

                            LazyColumn(
                                state = minuteListState,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(minutes.size) { index ->
                                    val minute = minutes[index]
                                    // 判断是否为中心项（视觉上）
                                    val isCentered = minute == selectedMinute
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(40.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = String.format("%02d", minute),
                                            style = MaterialTheme.typography.headlineSmall.copy(
                                                color = if (isCentered) MaterialTheme.colorScheme.primary
                                                       else MaterialTheme.colorScheme.onSurface,
                                                fontSize = if (isCentered) MaterialTheme.typography.headlineMedium.fontSize
                                                         else MaterialTheme.typography.headlineSmall.fontSize
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 当前选中时间显示
                Text(
                    text = String.format("选中时间: %02d:%02d", selectedHour, selectedMinute),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedHour, selectedMinute) }
            ) {
                Text("确认")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("取消")
            }
        }
    )
}

@Composable
fun PlanDialog(
    title: String,
    planTitle: String,
    planDescription: String,
    selectedDay: DayOfWeek,
    startTime: String,
    endTime: String,
    isDaily: Boolean,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onDayChange: (DayOfWeek) -> Unit,
    onStartTimeChange: (String) -> Unit,
    onEndTimeChange: (String) -> Unit,
    onDailyChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = planTitle,
                    onValueChange = onTitleChange,
                    label = { Text("命运安排标题") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = planDescription,
                    onValueChange = onDescriptionChange,
                    label = { Text("详细描述") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                // 使用时间范围选择器
                TimeRangePickerField(
                    startTime = startTime,
                    endTime = endTime,
                    onStartTimeChange = onStartTimeChange,
                    onEndTimeChange = onEndTimeChange,
                )

                Spacer(Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isDaily,
                        onCheckedChange = onDailyChange
                    )
                    Text("每日星象循环")
                }

                Spacer(Modifier.height(8.dp))

                Text("选择星曜日:")
                Spacer(Modifier.height(4.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(DayOfWeek.values().toList()) { day ->
                        FilterChip(
                            selected = (day == selectedDay),
                            onClick = { onDayChange(day) },
                            label = { Text(getDayOfWeekDisplayName(day, "SHORT")) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
fun TimeRangePickerField(
    startTime: String,
    endTime: String,
    onStartTimeChange: (String) -> Unit,
    onEndTimeChange: (String) -> Unit
) {
    val startTimeParts = startTime.split(":")
    val initialStartHour = startTimeParts.getOrNull(0)?.toIntOrNull() ?: 9
    val initialStartMinute = startTimeParts.getOrNull(1)?.toIntOrNull() ?: 0

    val endTimeParts = endTime.split(":")
    val initialEndHour = endTimeParts.getOrNull(0)?.toIntOrNull() ?: 9
    val initialEndMinute = endTimeParts.getOrNull(1)?.toIntOrNull() ?: 0

    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    // 使用remember保存开始和结束时间的当前值
    var currentStartTime by remember { mutableStateOf(startTime) }
    var currentEndTime by remember { mutableStateOf(endTime) }

    // 当外部startTime变化时更新内部状态
    LaunchedEffect(startTime) {
        currentStartTime = startTime
    }

    // 当外部endTime变化时更新内部状态
    LaunchedEffect(endTime) {
        currentEndTime = endTime
    }

    Column {
        Text("命运时间范围:")

        // 开始时间选择
        Text("开始时间:")
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = { showStartPicker = true }) {
                Text(currentStartTime)
            }

            if (showStartPicker) {
                val parts = currentStartTime.split(":")
                val hour = parts.getOrNull(0)?.toIntOrNull() ?: initialStartHour
                val minute = parts.getOrNull(1)?.toIntOrNull() ?: initialStartMinute

                ScrollableTimePickerDialog(
                    onCancel = { showStartPicker = false },
                    onConfirm = { hour, minute ->
                        val newTime = String.format("%02d:%02d", hour, minute)
                        currentStartTime = newTime
                        onStartTimeChange(newTime)
                        showStartPicker = false
                    },
                    initialHour = hour,
                    initialMinute = minute
                )
            }
        }

        // 结束时间选择
        Text("结束时间:")
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = { showEndPicker = true }) {
                Text(currentEndTime)
            }

            if (showEndPicker) {
                val parts = currentEndTime.split(":")
                val hour = parts.getOrNull(0)?.toIntOrNull() ?: initialEndHour
                val minute = parts.getOrNull(1)?.toIntOrNull() ?: initialEndMinute

                ScrollableTimePickerDialog(
                    onCancel = { showEndPicker = false },
                    onConfirm = { hour, minute ->
                        val newTime = String.format("%02d:%02d", hour, minute)
                        currentEndTime = newTime
                        onEndTimeChange(newTime)
                        showEndPicker = false
                    },
                    initialHour = hour,
                    initialMinute = minute
                )
            }
        }
    }
}

@Composable
fun EmptyPlanScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "尚未制定命运规划",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = "点击右下角的按钮添加你的第一项命运安排",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun PlanList(planItems: List<com.example.bestplannner.data.PlanItem>, onEditPlan: (com.example.bestplannner.data.PlanItem) -> Unit, onDeletePlan: (com.example.bestplannner.data.PlanItem) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Group plans by day and include daily plans in each day
        val plansByDay = mutableMapOf<DayOfWeek, MutableList<com.example.bestplannner.data.PlanItem>>()
        
        // Initialize map with empty lists for each day
        DayOfWeek.values().forEach { day ->
            plansByDay[day] = mutableListOf()
        }
        
        // Add plans to their respective days
        planItems.forEach { plan ->
            if (plan.isDaily) {
                // Add daily plans to all days
                DayOfWeek.values().forEach { day ->
                    plansByDay[day]?.add(plan)
                }
            } else {
                // Add regular plans to their specific day
                plansByDay[plan.dayOfWeek]?.add(plan)
            }
        }

        // 显示一周七天的计划
        DayOfWeek.values().forEach { day ->
            val dayPlans = plansByDay[day] ?: emptyList<com.example.bestplannner.data.PlanItem>()

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = getDayOfWeekDisplayName(day, "FULL"),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )

                        if (dayPlans.isEmpty()) {
                            Text(
                                text = "今日无命运安排",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        } else {
                            dayPlans.forEach { plan ->
                                PlanItemCard(
                                    plan = plan,
                                    onEditPlan = onEditPlan,
                                    onDeletePlan = onDeletePlan
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlanItemCard(plan: com.example.bestplannner.data.PlanItem, onEditPlan: (com.example.bestplannner.data.PlanItem) -> Unit, onDeletePlan: (com.example.bestplannner.data.PlanItem) -> Unit) {
    val context = LocalContext.current
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    // 删除确认对话框
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("确认删除") },
            text = { Text("确定要删除计划 \"${plan.title}\" 吗？此操作无法撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        // 转换为screens.PlanItem以匹配函数参数类型
                        val screensPlan = com.example.bestplannner.screens.PlanItem(
                            id = plan.id,
                            dayOfWeek = plan.dayOfWeek,
                            title = plan.title,
                            description = plan.description,
                            startTime = plan.startTime,
                            endTime = plan.endTime,
                            isDaily = plan.isDaily,
                            isCompleted = plan.isCompleted
                        )
                        // 重置通知记录
                        com.example.bestplannner.screens.PlanItem.resetNotificationForPlan(context, screensPlan)
                        // 删除计划
                        onDeletePlan(plan)
                        showDeleteConfirmation = false
                    }
                ) {
                    Text("确认")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmation = false }
                ) {
                    Text("取消")
                }
            }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        // 主要内容
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = plan.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row {
                    Text(
                        text = "${plan.startTime}-${plan.endTime}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    IconButton(onClick = { onEditPlan(plan) }) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "编辑计划",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // 添加删除按钮
                    IconButton(onClick = {
                        showDeleteConfirmation = true
                    }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "删除计划",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            if (plan.description.isNotEmpty()) {
                Text(
                    text = plan.description,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // 显示重复标记
            if (plan.isDaily) {
                Text(
                    text = "每天重复",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

// 保存计划到共享偏好设置
fun savePlans(context: Context, plans: List<com.example.bestplannner.data.PlanItem>) {
    val sharedPref = context.getSharedPreferences("plans", Context.MODE_PRIVATE)
    val editor = sharedPref.edit()

    // 清除旧的计划
    editor.clear()

    // 保存新的计划
    plans.forEachIndexed { index, plan ->
        editor.putString("plan_${index}_title", plan.title)
        editor.putString("plan_${index}_description", plan.description)
        editor.putInt("plan_${index}_day", plan.dayOfWeek.value) // 使用value替代ordinal
        editor.putString("plan_${index}_startTime", plan.startTime)
        editor.putString("plan_${index}_endTime", plan.endTime)
        editor.putBoolean("plan_${index}_isDaily", plan.isDaily)
        editor.putInt("plan_${index}_id", plan.id)
    }

    // 保存计划总数
    editor.putInt("plan_count", plans.size)

    editor.apply()
}

// 保存单个计划
fun savePlan(context: Context, plan: com.example.bestplannner.data.PlanItem) {
    val plans = loadPlans(context).toMutableList()

    // 查找是否已存在具有相同ID的计划
    val existingIndex = plans.indexOfFirst { it.id == plan.id }

    if (existingIndex >= 0) {
        // 更新现有计划
        plans[existingIndex] = plan
    } else {
        // 添加新计划
        plans.add(plan)
    }

    // 保存所有计划
    savePlans(context, plans)

    // 重新调度通知
    scheduleNotifications(context, plans)
}

// 删除计划
fun deletePlan(context: Context, plan: com.example.bestplannner.data.PlanItem) {
    val plans = loadPlans(context).toMutableList()

    // 查找并删除计划
    plans.removeAll { it.id == plan.id }

    // 保存所有计划
    savePlans(context, plans)

    // 删除与该计划关联的对话
    deleteConversationByPlanId(context, plan.id)

    // 重新调度通知
    scheduleNotifications(context, plans)
}

/**
 * 根据计划ID删除关联的对话
 */
fun deleteConversationByPlanId(context: Context, planId: Int) {
    val conversationPreferences = context.getSharedPreferences("conversations", Context.MODE_PRIVATE)
    try {
        val editor = conversationPreferences.edit()

        // 查找要删除的对话索引
        var deleteIndex = -1
        val conversationCount = conversationPreferences.getInt("conversation_count", 0)
        for (i in 0 until conversationCount) {
            val storedPlanId = conversationPreferences.getLong("conversation_${i}_plan_id", -1)
            if (storedPlanId == planId.toLong()) {
                deleteIndex = i
                break
            }
        }

        // 如果找到了要删除的对话
        if (deleteIndex != -1) {
            // 删除该对话的所有数据
            editor.remove("conversation_${deleteIndex}_id")
            editor.remove("conversation_${deleteIndex}_plan_id")
            editor.remove("conversation_${deleteIndex}_title")
            editor.remove("conversation_${deleteIndex}_timestamp")

            val messageCount = conversationPreferences.getInt("conversation_${deleteIndex}_message_count", 0)
            for (j in 0 until messageCount) {
                editor.remove("conversation_${deleteIndex}_message_${j}_id")
                editor.remove("conversation_${deleteIndex}_message_${j}_text")
                editor.remove("conversation_${deleteIndex}_message_${j}_isUser")
                editor.remove("conversation_${deleteIndex}_message_${j}_timestamp")
            }
            editor.remove("conversation_${deleteIndex}_message_count")

            // 将后面的对话索引前移
            for (i in (deleteIndex + 1) until conversationCount) {
                // 移动对话数据
                val nextIndex = i - 1
                editor.putLong("conversation_${nextIndex}_id", conversationPreferences.getLong("conversation_${i}_id", i.toLong()))
                editor.putLong("conversation_${nextIndex}_plan_id", conversationPreferences.getLong("conversation_${i}_plan_id", -1))
                editor.putString("conversation_${nextIndex}_title", conversationPreferences.getString("conversation_${i}_title", "对话") ?: "对话")
                editor.putLong("conversation_${nextIndex}_timestamp", conversationPreferences.getLong("conversation_${i}_timestamp", System.currentTimeMillis()))

                // 移动消息数据
                val messageCount = conversationPreferences.getInt("conversation_${i}_message_count", 0)
                editor.putInt("conversation_${nextIndex}_message_count", messageCount)
                for (j in 0 until messageCount) {
                    editor.putLong("conversation_${nextIndex}_message_${j}_id", conversationPreferences.getLong("conversation_${i}_message_${j}_id", j.toLong()))
                    editor.putString("conversation_${nextIndex}_message_${j}_text", conversationPreferences.getString("conversation_${i}_message_${j}_text", "") ?: "")
                    editor.putBoolean("conversation_${nextIndex}_message_${j}_isUser", conversationPreferences.getBoolean("conversation_${i}_message_${j}_isUser", false))
                    editor.putLong("conversation_${nextIndex}_message_${j}_timestamp", conversationPreferences.getLong("conversation_${i}_message_${j}_timestamp", System.currentTimeMillis()))
                }

                // 删除旧索引的数据
                editor.remove("conversation_${i}_id")
                editor.remove("conversation_${i}_plan_id")
                editor.remove("conversation_${i}_title")
                editor.remove("conversation_${i}_timestamp")
                editor.remove("conversation_${i}_message_count")
                for (j in 0 until messageCount) {
                    editor.remove("conversation_${i}_message_${j}_id")
                    editor.remove("conversation_${i}_message_${j}_text")
                    editor.remove("conversation_${i}_message_${j}_isUser")
                    editor.remove("conversation_${i}_message_${j}_timestamp")
                }
            }

            // 更新对话总数
            editor.putInt("conversation_count", conversationCount - 1)
            editor.apply()
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

// 从共享偏好设置加载计划
fun loadPlans(context: Context): List<com.example.bestplannner.data.PlanItem> {
    val sharedPref = context.getSharedPreferences("plans", Context.MODE_PRIVATE)
    val planCount = sharedPref.getInt("plan_count", 0)

    val plans = mutableListOf<com.example.bestplannner.data.PlanItem>()

    for (i in 0 until planCount) {
        val id = sharedPref.getInt("plan_${i}_id", i)
        val title = sharedPref.getString("plan_${i}_title", "") ?: ""
        val description = sharedPref.getString("plan_${i}_description", "") ?: ""
        val dayValue = sharedPref.getInt("plan_${i}_day", 1)  // 默认值改为1（星期一）
         val startTime = sharedPref.getString("plan_${i}_startTime", "09:00") ?: "09:00"
        val endTime = sharedPref.getString("plan_${i}_endTime", startTime) ?: startTime
        val isDaily = sharedPref.getBoolean("plan_${i}_isDaily", false)

        // 使用of方法替代ordinal
        val dayOfWeek = org.threeten.bp.DayOfWeek.of(dayValue)

        plans.add(com.example.bestplannner.data.PlanItem(id, dayOfWeek, title, description, startTime, endTime, isDaily))
    }

    return plans
}

// 调度通知
fun scheduleNotifications(context: Context, plans: List<com.example.bestplannner.data.PlanItem>) {
    // 计划面板不应该接入通知系统这样会搞得很复杂
    // 移除与通知系统相关的代码
}

@Composable
fun PlanScreen(navController: NavController) {
    val context = LocalContext.current
    var planItems by remember { mutableStateOf<List<com.example.bestplannner.data.PlanItem>>(loadPlans(context)) }
    var showPlanDialog by remember { mutableStateOf(false) }
    var editingPlan by remember { mutableStateOf<com.example.bestplannner.data.PlanItem?>(null) }
    var newPlanId by remember { mutableStateOf(System.currentTimeMillis().toInt()) }
    var newPlanTitle by remember { mutableStateOf("") }
    var newPlanDescription by remember { mutableStateOf("") }
    var selectedDay by remember { mutableStateOf(DayOfWeek.MONDAY) }
    var newStartTime by remember { mutableStateOf("09:00") }
    var newEndTime by remember { mutableStateOf("10:00") }
    var isDaily by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .size(20.dp)
                                .padding(end = 8.dp)
                        )
                        Text("命运规划") 
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "返回星语"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    // Reset form for adding new plan
                    editingPlan = null
                    newPlanId = System.currentTimeMillis().toInt()
                    newPlanTitle = ""
                    newPlanDescription = ""
                    selectedDay = DayOfWeek.MONDAY
                    newStartTime = "09:00"
                    newEndTime = "10:00"
                    isDaily = false
                    showPlanDialog = true
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = "添加命运安排")
            }
        }
    ) { padding ->
        if (planItems.isEmpty()) {
            EmptyPlanScreen()
        } else {
            PlanList(
                planItems = planItems,
                onEditPlan = { plan ->
                    editingPlan = plan
                    newPlanTitle = plan.title
                    newPlanDescription = plan.description
                    selectedDay = org.threeten.bp.DayOfWeek.of(plan.dayOfWeek.value)
                    newStartTime = plan.startTime
                    newEndTime = plan.endTime
                    isDaily = plan.isDaily
                    showPlanDialog = true
                },
                onDeletePlan = { plan ->
                    // 更新planItems状态以立即刷新界面
                    planItems = planItems.filter { it.id != plan.id }
                    // 转换为screens.PlanItem以匹配函数参数类型
                    val screensPlan = com.example.bestplannner.screens.PlanItem(
                        id = plan.id,
                        dayOfWeek = plan.dayOfWeek,
                        title = plan.title,
                        description = plan.description,
                        startTime = plan.startTime,
                        endTime = plan.endTime,
                        isDaily = plan.isDaily,
                        isCompleted = plan.isCompleted
                    )
                    // 重置通知记录
                    com.example.bestplannner.screens.PlanItem.resetNotificationForPlan(context, screensPlan)
                    // 删除计划
                    deletePlan(context, plan)
                },
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            )
        }
    }

    if (showPlanDialog) {
        PlanDialog(
            title = if (editingPlan == null) "添加命运安排" else "编辑命运安排",
            planTitle = newPlanTitle,
            planDescription = newPlanDescription,
            selectedDay = selectedDay,
            startTime = newStartTime,
            endTime = newEndTime,
            isDaily = isDaily,
            onTitleChange = { newPlanTitle = it },
            onDescriptionChange = { newPlanDescription = it },
            onDayChange = { selectedDay = it },
            onStartTimeChange = { newStartTime = it },
            onEndTimeChange = { newEndTime = it },
            onDailyChange = { isDaily = it },
            onDismiss = { showPlanDialog = false },
            onConfirm = {
                // 验证输入
                if (newPlanTitle.isBlank()) {
                    // 可以显示错误提示
                    return@PlanDialog
                }

                // 创建或更新计划项
                val plan = com.example.bestplannner.data.PlanItem(
                    id = editingPlan?.id ?: newPlanId,
                    dayOfWeek = org.threeten.bp.DayOfWeek.of(selectedDay.value),
                    title = newPlanTitle,
                    description = newPlanDescription,
                    startTime = newStartTime,
                    endTime = newEndTime,
                    isDaily = isDaily,
                    isCompleted = editingPlan?.isCompleted ?: false
                )

                // 如果是编辑现有计划，重置通知记录
                if (editingPlan != null) {
                    // 转换为screens.PlanItem以匹配函数参数类型
                    val screensPlan = com.example.bestplannner.screens.PlanItem(
                        id = plan.id,
                        dayOfWeek = plan.dayOfWeek,
                        title = plan.title,
                        description = plan.description,
                        startTime = plan.startTime,
                        endTime = plan.endTime,
                        isDaily = plan.isDaily,
                        isCompleted = plan.isCompleted
                    )
                    com.example.bestplannner.screens.PlanItem.resetNotificationForPlan(context, screensPlan)
                }

                // 保存计划
                savePlan(context, plan)

                // 计划面板不应该接入通知系统这样会搞得很复杂
                // 移除与通知系统相关的代码
                val conversationManager = com.example.bestplannner.components.chat.ConversationManager(context)
                val conversationId = conversationManager.findOrCreateConversationForPlan(plan)
                if (conversationId != -1L) {
                    try {
                        // 获取当前计划状态
                        val currentStatus = getCurrentPlanStatus(context, plan)
                        // 只是安排对话生成任务，而不是立即执行
                        AutoConversationWorker.scheduleAutoConversationByPlanStatus(context, plan, conversationId)
                        // 确保周期性计划检查已经启动
                        AutoConversationWorker.schedulePeriodicPlanCheck(context)
                    } catch (e: Exception) {
                        android.util.Log.e("PlanScreen", "将计划添加到自动对话系统检测序列失败: ${e.message}", e)
                    }
                }

                // 更新planItems状态以立即刷新界面
                if (editingPlan != null) {
                    // 更新现有计划
                    planItems = planItems.map { if (it.id == plan.id) plan else it }
                } else {
                    // 添加新计划
                    planItems = planItems + plan
                }

                // 重置表单状态
                editingPlan = null
                newPlanId = (0..Int.MAX_VALUE).random()
                newPlanTitle = ""
                newPlanDescription = ""
                selectedDay = DayOfWeek.MONDAY
                newStartTime = "09:00"
                newEndTime = "10:00"
                isDaily = false

                // 关闭对话框
                showPlanDialog = false
            }
        )
    }
}

// 辅助函数用于获取星期几的显示名称（兼容API级别）
fun getDayOfWeekDisplayName(day: DayOfWeek, style: String): String {
    return when (style) {
        "FULL" -> when (day.value) {
            1 -> "星期一"
            2 -> "星期二"
            3 -> "星期三"
            4 -> "星期四"
            5 -> "星期五"
            6 -> "星期六"
            7 -> "星期日"
            else -> "" // 添加默认情况以满足编译要求
        }
        "SHORT" -> when (day.value) {
            1 -> "周一"
            2 -> "周二"
            3 -> "周三"
            4 -> "周四"
            5 -> "周五"
            6 -> "周六"
            7 -> "周日"
            else -> "" // 添加默认情况以满足编译要求
        }
        else -> day.toString()
    }
}

// 添加获取计划状态的辅助函数
fun getCurrentPlanStatus(context: android.content.Context, plan: com.example.bestplannner.data.PlanItem): String {
    val prefs = context.getSharedPreferences("plan_status", android.content.Context.MODE_PRIVATE)
    return prefs.getString("plan_${plan.id}_status", "not_started") ?: "not_started"
}