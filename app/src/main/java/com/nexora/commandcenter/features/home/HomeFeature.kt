package com.nexora.commandcenter.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexora.commandcenter.core.common.NetworkMonitor
import com.nexora.commandcenter.core.database.ActivityLogDao
import com.nexora.commandcenter.core.database.ActivityLogEntity
import com.nexora.commandcenter.core.database.ChatDao
import com.nexora.commandcenter.core.database.NoteDao
import com.nexora.commandcenter.core.database.NoteEntity
import com.nexora.commandcenter.core.database.TaskDao
import com.nexora.commandcenter.core.database.TaskEntity
import com.nexora.commandcenter.core.database.ToolUsageDao
import com.nexora.commandcenter.core.database.ToolUsageEntity
import com.nexora.commandcenter.core.designsystem.NexoraAiOrb
import com.nexora.commandcenter.core.designsystem.NexoraButton
import com.nexora.commandcenter.core.designsystem.NexoraCard
import com.nexora.commandcenter.core.designsystem.NexoraColors
import com.nexora.commandcenter.core.designsystem.NexoraEmptyState
import com.nexora.commandcenter.core.designsystem.NexoraGlyph
import com.nexora.commandcenter.core.designsystem.NexoraIcon
import com.nexora.commandcenter.core.designsystem.NexoraRadialGauge
import com.nexora.commandcenter.core.designsystem.NexoraSectionHeader
import com.nexora.commandcenter.core.designsystem.NexoraShapes
import com.nexora.commandcenter.core.designsystem.NexoraSplineAreaChart
import com.nexora.commandcenter.core.designsystem.NexoraTextField
import com.nexora.commandcenter.core.network.LocalNetworkSnapshot
import com.nexora.commandcenter.core.network.NetworkDiagnosticsEngine
import com.nexora.commandcenter.navigation.NexoraDestination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class QuickToolShortcut(
    val id: String,
    val titleEn: String,
    val titleFa: String,
    val subtitleEn: String,
    val subtitleFa: String,
    val glyph: NexoraGlyph
)

data class DashboardUiState(
    val totalTasks: Int = 0,
    val completedTasks: Int = 0,
    val notesCount: Int = 0,
    val conversationsCount: Int = 0,
    val upcomingTasks: List<TaskEntity> = emptyList(),
    val recentTools: List<ToolUsageEntity> = emptyList(),
    val recentActivities: List<ActivityLogEntity> = emptyList(),
    val networkSnapshot: LocalNetworkSnapshot? = null,
    val weeklyActivityValues: List<Float> = listOf(4f, 6f, 5f, 8f, 7f, 9f, 6f)
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val taskDao: TaskDao,
    private val noteDao: NoteDao,
    private val chatDao: ChatDao,
    private val toolUsageDao: ToolUsageDao,
    private val activityLogDao: ActivityLogDao,
    private val diagnosticsEngine: NetworkDiagnosticsEngine,
    networkMonitor: NetworkMonitor
) : ViewModel() {

    val isOnlineFlow: Flow<Boolean> = networkMonitor.isOnline

    val uiState: StateFlow<DashboardUiState> = combine(
        taskDao.observeTasks(),
        noteDao.observeNotes(),
        chatDao.observeConversations(),
        toolUsageDao.observeRecentTools(),
        activityLogDao.observeRecentActivities()
    ) { tasks, notes, conversations, recentTools, activities ->
        val completed = tasks.count { it.isCompleted }
        val toolHits = recentTools.sumOf { it.usageCount }
        DashboardUiState(
            totalTasks = tasks.size,
            completedTasks = completed,
            notesCount = notes.size,
            conversationsCount = conversations.size,
            upcomingTasks = tasks.take(5),
            recentTools = recentTools,
            recentActivities = activities.take(6),
            networkSnapshot = diagnosticsEngine.inspectLocalInterfaces(),
            weeklyActivityValues = listOf(
                3f,
                5f,
                (notes.size + 2).toFloat(),
                (conversations.size + 3).toFloat(),
                (toolHits + 4).toFloat(),
                (completed + notes.size).toFloat().coerceAtLeast(4f),
                (tasks.size + conversations.size + activities.size).toFloat().coerceAtLeast(6f)
            )
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState()
    )

    init {
        viewModelScope.launch {
            if (taskDao.getAllTasksSnapshot().isEmpty()) {
                val today = LocalDate.now().toEpochDay()
                taskDao.upsertTask(
                    TaskEntity(
                        title = "Review NEXORA Architecture & Security Audit",
                        category = "Architecture",
                        priority = 3,
                        isCompleted = true,
                        dueDateEpochDay = today
                    )
                )
                taskDao.upsertTask(
                    TaskEntity(
                        title = "Run Latency & DNS Benchmark on Production Gateway",
                        category = "Network",
                        priority = 3,
                        isCompleted = false,
                        dueDateEpochDay = today
                    )
                )
                taskDao.upsertTask(
                    TaskEntity(
                        title = "Verify Offline Room Database & DataStore Sync",
                        category = "Deep Work",
                        priority = 2,
                        isCompleted = false,
                        dueDateEpochDay = today
                    )
                )
                activityLogDao.insertActivity(
                    ActivityLogEntity(
                        module = "SYSTEM",
                        actionTitle = "NEXORA Command Center Initialized",
                        detail = "Room SQLite vault & Hilt DI graph online"
                    )
                )
            }
            if (noteDao.getAllNotesSnapshot().isEmpty()) {
                noteDao.upsertNote(
                    NoteEntity(
                        title = "NEXORA Command Notes",
                        content = "Offline Room storage is active. All notes, tasks, and calculations persist locally across restarts.",
                        tag = "System",
                        isPinned = true
                    )
                )
            }
        }
    }

    fun toggleTaskCompletion(task: TaskEntity) {
        viewModelScope.launch {
            val nextCompleted = !task.isCompleted
            taskDao.updateTask(task.copy(isCompleted = nextCompleted))
            activityLogDao.insertActivity(
                ActivityLogEntity(
                    module = "TASKS",
                    actionTitle = if (nextCompleted) "Completed Directive" else "Reopened Directive",
                    detail = task.title
                )
            )
        }
    }
}

@Composable
fun HomeScreen(
    isFa: Boolean,
    onNavigateToRoute: (String) -> Unit,
    onLaunchPromptInAssistant: (String) -> Unit = { onNavigateToRoute(NexoraDestination.ASSISTANT.route) },
    onOpenToolId: (String) -> Unit = { onNavigateToRoute(NexoraDestination.TOOLS.route) },
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val isOnline by viewModel.isOnlineFlow.collectAsState(initial = true)
    var quickCommand by remember { mutableStateOf("") }

    val hour = remember { LocalTime.now().hour }
    val greeting = when {
        isFa && hour < 12 -> "صبح بخیر، فرمانده"
        isFa && hour < 18 -> "عصر بخیر، فرمانده"
        isFa -> "شب بخیر، فرمانده"
        hour < 12 -> "Good Morning, Commander"
        hour < 18 -> "Good Afternoon, Commander"
        else -> "Good Evening, Commander"
    }
    val formattedDate = remember {
        LocalDate.now().format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy"))
    }
    val completionPercent = if (state.totalTasks == 0) 0 else (state.completedTasks * 100) / state.totalTasks

    val smartSuggestions = if (isFa) {
        listOf(
            "معماری Clean Architecture پروژه را تحلیل کن",
            "وضعیت پینگ و DNS شبکه را بررسی کن",
            "خلاصه‌سازی یادداشت‌های فنی امروز"
        )
    } else {
        listOf(
            "Audit Android Clean Architecture & Hilt graph",
            "Synthesize executive summary of today's tasks",
            "Run latency & DNS diagnostics on gateway"
        )
    }

    val quickToolShortcuts = remember {
        listOf(
            QuickToolShortcut("calculator", "Calculator", "ماشین‌حساب", "Scientific Math", "محاسبات مهندسی", NexoraGlyph.CALCULATOR),
            QuickToolShortcut("qr_scanner", "QR Scanner", "اسکنر QR", "CameraX + MLKit", "دوربین زنده", NexoraGlyph.QR),
            QuickToolShortcut("qr_generator", "QR Generator", "ساخت QR", "ZXing Matrix", "تولید بارکد", NexoraGlyph.QR),
            QuickToolShortcut("converter", "Converter", "مبدل واحد", "5 Domains", "۵ دسته مهندسی", NexoraGlyph.CONVERTER),
            QuickToolShortcut("notes", "Notes Vault", "یادداشت‌ها", "Room SQLite", "ذخیره محلی", NexoraGlyph.NOTES),
            QuickToolShortcut("text_utils", "Text Crypto", "تحلیل متن", "SHA-256 & Base64", "هش و رمزنگاری", NexoraGlyph.TEXT),
            QuickToolShortcut("file_vault", "File Browser", "مدیریت فایل", "Sandbox Storage", "حافظه داخلی", NexoraGlyph.FILES),
            QuickToolShortcut("network_diag", "Net Probe", "پروب شبکه", "Latency & DNS", "تست پینگ", NexoraGlyph.NETWORK)
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // 1. HERO: Personalized Greeting, Animated AI Orb, Smart Assistant Input & Suggestions
        item {
            NexoraCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = NexoraColors.CyberCyan.copy(alpha = 0.36f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = greeting,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "$formattedDate · ${if (isOnline) "Quantum Link Active" else "Offline Room Vault Mode"}",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isOnline) NexoraColors.CyberCyan else NexoraColors.AmberSignal
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    NexoraAiOrb(isActive = isOnline)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (isFa) "هسته هوشمند نکسورا آماده دریافت فرمان است" else "NEXORA Neural Core Ready",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                NexoraTextField(
                    value = quickCommand,
                    onValueChange = { quickCommand = it },
                    placeholder = if (isFa) {
                        "فرمان یا پرسش خود را برای هوش مصنوعی نکسورا بنویسید..."
                    } else {
                        "Ask NEXORA AI, execute a command, or summarize notes..."
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                NexoraButton(
                    text = if (isFa) "اجرای فرمان در دستیار هوشمند" else "Execute in AI Command Center →",
                    leadingGlyph = NexoraGlyph.ORB,
                    onClick = {
                        val promptToRun = quickCommand.trim()
                        quickCommand = ""
                        onLaunchPromptInAssistant(promptToRun)
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = if (isFa) "پیشنهادهای هوشمند" else "SMART SUGGESTIONS",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(smartSuggestions) { suggestion ->
                        OutlinedButton(
                            onClick = { onLaunchPromptInAssistant(suggestion) }
                        ) {
                            Text(suggestion, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        // 2. NETWORK STATUS & INTERFACE TELEMETRY BAR
        item {
            val snap = state.networkSnapshot
            NexoraCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = if (isOnline) NexoraColors.EmeraldSignal.copy(alpha = 0.4f) else NexoraColors.AmberSignal.copy(alpha = 0.4f),
                onClick = { onNavigateToRoute(NexoraDestination.NETWORK.route) }
            ) {
                NexoraSectionHeader(
                    eyebrow = if (isFa) "وضعیت شبکه زنده" else "LIVE NETWORK TELEMETRY",
                    title = if (isOnline) {
                        if (isFa) "متصل به اینترنت (${snap?.transportType ?: "Wi-Fi"})" else "Connected · ${snap?.transportType ?: "Active Interface"}"
                    } else {
                        if (isFa) "حالت آفلاین فعال (Room Local Source of Truth)" else "Offline Mode · Local Room Database Active"
                    },
                    actionLabel = if (isFa) "مرکز شبکه ←" else "Diagnostics →",
                    onAction = { onNavigateToRoute(NexoraDestination.NETWORK.route) }
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricItem(
                        title = "IPv4 Interface",
                        value = snap?.localIpv4 ?: "127.0.0.1",
                        subtitle = snap?.interfaceName ?: "wlan0",
                        modifier = Modifier.weight(1f)
                    )
                    MetricItem(
                        title = if (isFa) "پهنای باند" else "Downstream",
                        value = "${snap?.downstreamKbps ?: 0} Kbps",
                        subtitle = if (snap?.isVpnActive == true) "VPN Active" else "Direct Link",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 3. PRODUCTIVITY STATISTICS & SPLINE TELEMETRY
        item {
            NexoraCard(modifier = Modifier.fillMaxWidth()) {
                NexoraSectionHeader(
                    eyebrow = if (isFa) "آمار بهره‌وری" else "PRODUCTIVITY OVERVIEW",
                    title = if (isFa) "تحلیل زنده پایگاه داده Room" else "Local Telemetry & Velocity",
                    actionLabel = if (isFa) "تحلیل کامل ←" else "Full Analytics →",
                    onAction = { onNavigateToRoute(NexoraDestination.ANALYTICS.route) }
                )
                Spacer(modifier = Modifier.height(14.dp))
                NexoraRadialGauge(
                    percentage = completionPercent,
                    label = if (isFa) "پیشرفت وظایف امروز" else "Directive Completion Velocity",
                    sublabel = "${state.completedTasks} of ${state.totalTasks} directives completed · ${state.notesCount} notes · ${state.conversationsCount} AI threads"
                )
                Spacer(modifier = Modifier.height(16.dp))
                NexoraSplineAreaChart(
                    values = state.weeklyActivityValues,
                    labels = if (isFa) {
                        listOf("ش", "ی", "د", "س", "چ", "پ", "ج")
                    } else {
                        listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                    }
                )
            }
        }

        // 4. QUICK TOOLS LAUNCHER MATRIX
        item {
            NexoraCard(modifier = Modifier.fillMaxWidth()) {
                NexoraSectionHeader(
                    eyebrow = if (isFa) "ابزارهای سریع" else "QUICK SMART TOOLS",
                    title = if (isFa) "دسترسی فوری به ۸ ابزار مهندسی" else "8 Offline-First Engineering Utilities",
                    actionLabel = if (isFa) "همه ابزارها ←" else "Open Suite →",
                    onAction = { onNavigateToRoute(NexoraDestination.TOOLS.route) }
                )
                Spacer(modifier = Modifier.height(12.dp))
                val usageMap = remember(state.recentTools) {
                    state.recentTools.associateBy { it.toolId }
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    quickToolShortcuts.chunked(2).forEach { rowPair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowPair.forEach { shortcut ->
                                val hits = usageMap[shortcut.id]?.usageCount ?: 0
                                NexoraCard(
                                    modifier = Modifier.weight(1f),
                                    cornerRadius = 16.dp,
                                    contentPadding = PaddingValues(12.dp),
                                    onClick = { onOpenToolId(shortcut.id) }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        NexoraIcon(
                                            glyph = shortcut.glyph,
                                            modifier = Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = if (isFa) shortcut.titleFa else shortcut.titleEn,
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                            Text(
                                                text = if (hits > 0) {
                                                    "${if (isFa) shortcut.subtitleFa else shortcut.subtitleEn} · ${hits}x"
                                                } else {
                                                    if (isFa) shortcut.subtitleFa else shortcut.subtitleEn
                                                },
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. PRIORITY DIRECTIVES (TASKS)
        item {
            NexoraCard(modifier = Modifier.fillMaxWidth()) {
                NexoraSectionHeader(
                    eyebrow = if (isFa) "مدیریت وظایف" else "PRIORITY DIRECTIVES",
                    title = if (isFa) "وظایف و یادآورهای فعال" else "Active Tasks & WorkManager Reminders",
                    actionLabel = if (isFa) "مدیریت وظایف ←" else "Manage Tasks →",
                    onAction = { onNavigateToRoute("tasks") }
                )
                Spacer(modifier = Modifier.height(10.dp))
                if (state.upcomingTasks.isEmpty()) {
                    NexoraEmptyState(
                        title = if (isFa) "هیچ وظیفه‌ای ثبت نشده است" else "No Directives Scheduled",
                        subtitle = if (isFa) "یک وظیفه جدید با یادآور WorkManager بسازید" else "Schedule a task with background WorkManager notifications.",
                        glyph = NexoraGlyph.TASKS,
                        actionLabel = if (isFa) "ایجاد وظیفه" else "Create Directive",
                        onAction = { onNavigateToRoute("tasks") }
                    )
                } else {
                    state.upcomingTasks.forEach { task ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = task.isCompleted,
                                onCheckedChange = { viewModel.toggleTaskCompletion(task) }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = task.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${task.category} · Priority P${task.priority}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // 6. RECENT ACTIVITIES TIMELINE
        item {
            NexoraCard(modifier = Modifier.fillMaxWidth()) {
                NexoraSectionHeader(
                    eyebrow = if (isFa) "رویدادهای اخیر" else "RECENT ACTIVITIES",
                    title = if (isFa) "گزارش زنده عملیات سیستم" else "Real-Time Command Telemetry Log"
                )
                Spacer(modifier = Modifier.height(10.dp))
                if (state.recentActivities.isEmpty()) {
                    Text(
                        text = if (isFa) "هنوز رویدادی ثبت نشده است." else "Interact with tools, tasks, or AI to populate your local telemetry log.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    val timeFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
                    state.recentActivities.forEach { act ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "[${act.module}] ${act.actionTitle}",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = act.detail,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = timeFmt.format(Date(act.timestamp)),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricItem(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(NexoraShapes.Control)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
