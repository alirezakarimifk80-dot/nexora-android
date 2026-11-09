package com.nexora.commandcenter.features.tasks

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexora.commandcenter.core.database.TaskDao
import com.nexora.commandcenter.core.database.TaskEntity
import com.nexora.commandcenter.core.designsystem.GlassCard
import com.nexora.commandcenter.core.designsystem.NeonGradientButton
import com.nexora.commandcenter.data.TaskReminderWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class TasksViewModel @Inject constructor(
    private val taskDao: TaskDao
) : ViewModel() {

    val tasks: StateFlow<List<TaskEntity>> = taskDao.observeTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addTask(
        context: Context,
        title: String,
        category: String,
        priority: Int,
        dueDateEpochDay: Long,
        scheduleWorkManagerReminder: Boolean
    ) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val entity = TaskEntity(
                title = title.trim(),
                category = category,
                priority = priority,
                dueDateEpochDay = dueDateEpochDay
            )
            val id = taskDao.upsertTask(entity)
            if (scheduleWorkManagerReminder) {
                TaskReminderWorker.scheduleReminder(
                    context = context,
                    task = entity.copy(id = id),
                    delayMinutes = 15L
                )
            }
        }
    }

    fun toggleTask(task: TaskEntity) {
        viewModelScope.launch {
            taskDao.updateTask(task.copy(isCompleted = !task.isCompleted))
        }
    }

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch {
            taskDao.deleteTask(task)
        }
    }
}

@Composable
fun TasksScreen(
    isFa: Boolean,
    viewModel: TasksViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val tasks by viewModel.tasks.collectAsState()
    val today = remember { LocalDate.now() }
    val weekDays = remember { (0..6).map { today.plusDays(it.toLong()) } }

    var selectedEpochDay by remember { mutableLongStateOf(today.toEpochDay()) }
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Deep Work") }
    var priority by remember { mutableIntStateOf(2) }
    var scheduleReminder by remember { mutableStateOf(true) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (isFa) "تقویم هفتگی و برنامه‌ریز" else "Interactive Calendar & Schedule",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(weekDays) { date ->
                        val dayLabel = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
                        FilterChip(
                            selected = selectedEpochDay == date.toEpochDay(),
                            onClick = { selectedEpochDay = date.toEpochDay() },
                            label = { Text("$dayLabel ${date.dayOfMonth}") }
                        )
                    }
                }
            }
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (isFa) "ایجاد وظیفه و یادآور WorkManager" else "New Task & WorkManager Reminder",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (isFa) "عنوان وظیفه" else "Task Objective") }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(1 to "Low", 2 to "Medium", 3 to "High").forEach { (lvl, label) ->
                        FilterChip(
                            selected = priority == lvl,
                            onClick = { priority = lvl },
                            label = { Text("P$lvl · $label") }
                        )
                    }
                    FilterChip(
                        selected = scheduleReminder,
                        onClick = { scheduleReminder = !scheduleReminder },
                        label = { Text(if (isFa) "یادآور" else "15m Reminder") },
                        leadingIcon = {
                            Icon(Icons.Default.Alarm, contentDescription = null)
                        }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                NeonGradientButton(
                    text = if (isFa) "افزودن به برنامه" else "Schedule Task",
                    onClick = {
                        viewModel.addTask(
                            context = context,
                            title = title,
                            category = category,
                            priority = priority,
                            dueDateEpochDay = selectedEpochDay,
                            scheduleWorkManagerReminder = scheduleReminder
                        )
                        title = ""
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        items(tasks) { task ->
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = task.isCompleted,
                        onCheckedChange = { viewModel.toggleTask(task) }
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = task.title,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "${task.category} · Priority P${task.priority}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { viewModel.deleteTask(task) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Task")
                    }
                }
            }
        }
    }
}
