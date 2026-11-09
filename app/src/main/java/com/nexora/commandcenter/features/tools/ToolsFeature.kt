package com.nexora.commandcenter.features.tools

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.nexora.commandcenter.core.database.ActivityLogDao
import com.nexora.commandcenter.core.database.ActivityLogEntity
import com.nexora.commandcenter.core.database.NoteDao
import com.nexora.commandcenter.core.database.NoteEntity
import com.nexora.commandcenter.core.database.ToolUsageDao
import com.nexora.commandcenter.core.database.ToolUsageEntity
import com.nexora.commandcenter.core.designsystem.NexoraButton
import com.nexora.commandcenter.core.designsystem.NexoraCard
import com.nexora.commandcenter.core.designsystem.NexoraColors
import com.nexora.commandcenter.core.designsystem.NexoraDialog
import com.nexora.commandcenter.core.designsystem.NexoraEmptyState
import com.nexora.commandcenter.core.designsystem.NexoraGlyph
import com.nexora.commandcenter.core.designsystem.NexoraIcon
import com.nexora.commandcenter.core.designsystem.NexoraSectionHeader
import com.nexora.commandcenter.core.designsystem.NexoraShapes
import com.nexora.commandcenter.core.designsystem.NexoraSuccessBanner
import com.nexora.commandcenter.core.designsystem.NexoraTextField
import com.nexora.commandcenter.core.network.NetworkDiagnosticsEngine
import com.nexora.commandcenter.core.network.PingResult
import com.nexora.commandcenter.domain.QrGeneratorUseCase
import com.nexora.commandcenter.domain.ScientificCalculatorUseCase
import com.nexora.commandcenter.domain.TextUtilitiesUseCase
import com.nexora.commandcenter.domain.UnitCategory
import com.nexora.commandcenter.domain.UnitConverterUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

enum class SmartToolTab(
    val id: String,
    val titleEn: String,
    val titleFa: String,
    val descEn: String,
    val descFa: String,
    val glyph: NexoraGlyph
) {
    CALCULATOR(
        "calculator",
        "Calculator",
        "ماشین‌حساب",
        "Recursive-descent scientific expression solver",
        "حل‌کننده عبارات علمی و مهندسی",
        NexoraGlyph.CALCULATOR
    ),
    QR_SCANNER(
        "qr_scanner",
        "QR Scanner",
        "اسکنر QR",
        "Live CameraX + MLKit barcode detector",
        "اسکن زنده بارکد با دوربین CameraX",
        NexoraGlyph.QR
    ),
    QR_GENERATOR(
        "qr_generator",
        "QR Generator",
        "ساخت QR",
        "Offline ZXing matrix code generator",
        "تولید بارکد دوبعدی آفلاین با ZXing",
        NexoraGlyph.QR
    ),
    CONVERTER(
        "converter",
        "Unit Converter",
        "مبدل واحد",
        "5-domain engineering unit converter",
        "تبدیل دقیق واحدهای مهندسی",
        NexoraGlyph.CONVERTER
    ),
    NOTES(
        "notes",
        "Notes",
        "یادداشت‌ها",
        "Room SQLite persistent markdown notes",
        "مدیریت یادداشت‌های محلی در Room",
        NexoraGlyph.NOTES
    ),
    FILE_VAULT(
        "file_vault",
        "File Browser",
        "مرورگر فایل",
        "App sandbox file explorer & editor",
        "مدیریت و ویرایش فایل‌های داخلی",
        NexoraGlyph.FILES
    ),
    TEXT_UTILS(
        "text_utils",
        "Text Analyzer",
        "تحلیلگر متن",
        "SHA-256, MD5, Base64 & linguistic metrics",
        "هش رمزنگاری، Base64 و آمار متن",
        NexoraGlyph.TEXT
    ),
    NETWORK_DIAG(
        "network_diag",
        "Network Probe",
        "پروب شبکه",
        "Quick TCP/ICMP latency & interface probe",
        "تست سریع پینگ و رابط سخت‌افزاری شبکه",
        NexoraGlyph.NETWORK
    )
}

data class LocalVaultFile(
    val name: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val content: String
)

@HiltViewModel
class ToolsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val noteDao: NoteDao,
    private val toolUsageDao: ToolUsageDao,
    private val activityLogDao: ActivityLogDao,
    val diagnosticsEngine: NetworkDiagnosticsEngine,
    val calculatorUseCase: ScientificCalculatorUseCase,
    val unitConverterUseCase: UnitConverterUseCase,
    val qrGeneratorUseCase: QrGeneratorUseCase,
    val textUtilitiesUseCase: TextUtilitiesUseCase
) : ViewModel() {

    val notes: StateFlow<List<NoteEntity>> = noteDao.observeNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val toolUsageList: StateFlow<List<ToolUsageEntity>> = toolUsageDao.observeRecentTools()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _quickPingResult = MutableStateFlow<PingResult?>(null)
    val quickPingResult: StateFlow<PingResult?> = _quickPingResult.asStateFlow()

    fun recordToolUsage(tab: SmartToolTab) {
        viewModelScope.launch {
            val existing = toolUsageDao.getToolUsage(tab.id)
            val count = (existing?.usageCount ?: 0) + 1
            toolUsageDao.upsertToolUsage(
                ToolUsageEntity(
                    toolId = tab.id,
                    toolName = tab.titleEn,
                    usageCount = count,
                    lastUsedAt = System.currentTimeMillis()
                )
            )
            activityLogDao.insertActivity(
                ActivityLogEntity(
                    module = "TOOLS",
                    actionTitle = "Opened ${tab.titleEn}",
                    detail = "Invocation #$count"
                )
            )
        }
    }

    fun logToolAction(action: String, detail: String) {
        viewModelScope.launch {
            activityLogDao.insertActivity(
                ActivityLogEntity(
                    module = "TOOLS",
                    actionTitle = action,
                    detail = detail.take(80)
                )
            )
        }
    }

    fun saveNote(title: String, content: String, tag: String) {
        if (title.isBlank() && content.isBlank()) return
        viewModelScope.launch {
            val safeTitle = title.ifBlank { "Untitled Note" }
            noteDao.upsertNote(
                NoteEntity(
                    title = safeTitle,
                    content = content,
                    tag = tag.ifBlank { "General" }
                )
            )
            activityLogDao.insertActivity(
                ActivityLogEntity(
                    module = "TOOLS",
                    actionTitle = "Saved Room Note",
                    detail = "$safeTitle [$tag]"
                )
            )
        }
    }

    fun togglePin(note: NoteEntity) {
        viewModelScope.launch {
            noteDao.upsertNote(note.copy(isPinned = !note.isPinned))
        }
    }

    fun deleteNote(note: NoteEntity) {
        viewModelScope.launch {
            noteDao.deleteNote(note)
        }
    }

    fun listLocalVaultFiles(): List<LocalVaultFile> {
        val dir = File(appContext.filesDir, "nexora_vault").apply { mkdirs() }
        if (dir.listFiles().isNullOrEmpty()) {
            File(dir, "nexora_architecture_spec.md").writeText(
                "# NEXORA Command Center\n- Clean Architecture + MVVM\n- Room SQLite + DataStore\n- CameraX + ZXing + Retrofit"
            )
            File(dir, "security_policy.json").writeText(
                "{\n  \"zeroHardcodedKeys\": true,\n  \"offlineFirst\": true,\n  \"tlsRequired\": true\n}"
            )
        }
        return dir.listFiles()
            ?.sortedByDescending { it.lastModified() }
            ?.map { file ->
                LocalVaultFile(
                    name = file.name,
                    sizeBytes = file.length(),
                    lastModified = file.lastModified(),
                    content = runCatching { file.readText() }.getOrDefault("")
                )
            } ?: emptyList()
    }

    fun createVaultFile(fileName: String, content: String): List<LocalVaultFile> {
        val dir = File(appContext.filesDir, "nexora_vault").apply { mkdirs() }
        val safeName = fileName.trim().ifBlank { "document_${System.currentTimeMillis()}.txt" }
        File(dir, safeName).writeText(content)
        logToolAction("Created Vault File", safeName)
        return listLocalVaultFiles()
    }

    fun deleteVaultFile(fileName: String): List<LocalVaultFile> {
        val dir = File(appContext.filesDir, "nexora_vault")
        File(dir, fileName).delete()
        logToolAction("Deleted Vault File", fileName)
        return listLocalVaultFiles()
    }

    fun runQuickPing(host: String) {
        viewModelScope.launch {
            val res = diagnosticsEngine.pingHost(host)
            _quickPingResult.value = res
            logToolAction("Quick Network Ping", "${res.host} -> ${res.rttMs} ms")
        }
    }
}

@Composable
fun ToolsScreen(
    isFa: Boolean,
    initialToolId: String = "calculator",
    onOpenNetworkScreen: () -> Unit = {},
    viewModel: ToolsViewModel = hiltViewModel()
) {
    var selectedTab by remember {
        mutableStateOf(
            SmartToolTab.entries.find { it.id == initialToolId } ?: SmartToolTab.CALCULATOR
        )
    }
    LaunchedEffect(initialToolId) {
        SmartToolTab.entries.find { it.id == initialToolId }?.let {
            selectedTab = it
        }
    }

    val notes by viewModel.notes.collectAsState()
    val usageList by viewModel.toolUsageList.collectAsState()
    val usageMap = remember(usageList) { usageList.associateBy { it.toolId } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Visually Rich Tools Selector Header & Active Tool Banner
        item {
            NexoraCard(modifier = Modifier.fillMaxWidth()) {
                NexoraSectionHeader(
                    eyebrow = if (isFa) "مجموعه ابزارهای هوشمند" else "NEXORA SMART TOOLS SUITE",
                    title = if (isFa) selectedTab.titleFa else selectedTab.titleEn
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isFa) selectedTab.descFa else selectedTab.descEn,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(SmartToolTab.entries) { tab ->
                        val count = usageMap[tab.id]?.usageCount ?: 0
                        FilterChip(
                            selected = selectedTab == tab,
                            onClick = {
                                selectedTab = tab
                                viewModel.recordToolUsage(tab)
                            },
                            leadingIcon = {
                                NexoraIcon(
                                    glyph = tab.glyph,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (selectedTab == tab) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            label = {
                                val title = if (isFa) tab.titleFa else tab.titleEn
                                Text(if (count > 0) "$title ($count)" else title)
                            }
                        )
                    }
                }
            }
        }

        // 2. Active Functional Tool Workspace
        item {
            when (selectedTab) {
                SmartToolTab.CALCULATOR -> CalculatorCard(
                    isFa = isFa,
                    calculatorUseCase = viewModel.calculatorUseCase,
                    onCalculated = { expr, res ->
                        viewModel.logToolAction("Scientific Calculation", "$expr = $res")
                    }
                )
                SmartToolTab.QR_SCANNER -> QrScannerCard(
                    isFa = isFa,
                    onScanned = { code ->
                        viewModel.logToolAction("QR Code Scanned", code)
                    }
                )
                SmartToolTab.QR_GENERATOR -> QrGeneratorCard(
                    isFa = isFa,
                    qrGeneratorUseCase = viewModel.qrGeneratorUseCase,
                    onSaveToVault = { name, payload ->
                        viewModel.createVaultFile(name, payload)
                    }
                )
                SmartToolTab.CONVERTER -> UnitConverterCard(
                    isFa = isFa,
                    converter = viewModel.unitConverterUseCase
                )
                SmartToolTab.NOTES -> NotesManagerCard(
                    isFa = isFa,
                    notes = notes,
                    onSave = viewModel::saveNote,
                    onTogglePin = viewModel::togglePin,
                    onDelete = viewModel::deleteNote
                )
                SmartToolTab.TEXT_UTILS -> TextUtilitiesCard(
                    isFa = isFa,
                    textUtilitiesUseCase = viewModel.textUtilitiesUseCase
                )
                SmartToolTab.FILE_VAULT -> FileVaultCard(
                    isFa = isFa,
                    viewModel = viewModel
                )
                SmartToolTab.NETWORK_DIAG -> QuickNetworkDiagCard(
                    isFa = isFa,
                    viewModel = viewModel,
                    onOpenFullNetwork = onOpenNetworkScreen
                )
            }
        }
    }
}

@Composable
private fun CalculatorCard(
    isFa: Boolean,
    calculatorUseCase: ScientificCalculatorUseCase,
    onCalculated: (String, String) -> Unit
) {
    val context = LocalContext.current
    var expression by remember { mutableStateOf("sqrt(144) + 2^5") }
    var resultText by remember { mutableStateOf("44.0") }
    var history by remember { mutableStateOf(listOf("sqrt(144) + 2^5 = 44.0")) }
    var copiedMsg by remember { mutableStateOf<String?>(null) }

    val keypadRows = remember {
        listOf(
            listOf("sin(", "cos(", "tan(", "sqrt("),
            listOf("log(", "ln(", "^", "π"),
            listOf("7", "8", "9", "÷"),
            listOf("4", "5", "6", "×"),
            listOf("1", "2", "3", "-"),
            listOf("0", ".", "(", ")"),
            listOf("C", "⌫", "%", "+")
        )
    }

    NexoraCard(modifier = Modifier.fillMaxWidth()) {
        NexoraSectionHeader(
            eyebrow = "OFFLINE ENGINE",
            title = if (isFa) "ماشین‌حساب مهندسی" else "Scientific Expression Calculator",
            actionLabel = if (isFa) "کپی نتیجه" else "Copy Result",
            onAction = {
                copyText(context, resultText)
                copiedMsg = if (isFa) "نتیجه کپی شد" else "Result copied: $resultText"
            }
        )
        NexoraSuccessBanner(message = copiedMsg, onDismiss = { copiedMsg = null })
        Spacer(modifier = Modifier.height(10.dp))

        NexoraTextField(
            value = expression,
            onValueChange = { expression = it },
            placeholder = "e.g., sin(30) + sqrt(256) * 2"
        )
        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(NexoraShapes.Control)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RESULT",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "= $resultText",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            keypadRows.forEach { rowTokens ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    rowTokens.forEach { token ->
                        OutlinedButton(
                            onClick = {
                                when (token) {
                                    "C" -> {
                                        expression = ""
                                        resultText = "0"
                                    }
                                    "⌫" -> {
                                        if (expression.isNotEmpty()) {
                                            expression = expression.dropLast(1)
                                        }
                                    }
                                    else -> expression += token
                                }
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 10.dp)
                        ) {
                            Text(token, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        NexoraButton(
            text = if (isFa) "محاسبه دقیق (=)" else "Evaluate Expression (=)",
            leadingGlyph = NexoraGlyph.CALCULATOR,
            onClick = {
                calculatorUseCase.evaluate(expression)
                    .onSuccess { value ->
                        val formatted = if (value % 1.0 == 0.0) value.toLong().toString() else "%.6f".format(value).trimEnd('0').trimEnd('.')
                        resultText = formatted
                        history = (listOf("$expression = $formatted") + history).take(6)
                        onCalculated(expression, formatted)
                    }
                    .onFailure { err ->
                        resultText = "Error: ${err.message}"
                    }
            },
            modifier = Modifier.fillMaxWidth()
        )

        if (history.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (isFa) "تاریخچه محاسبات" else "CALCULATION TAPE",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            history.forEach { item ->
                Text(
                    text = item,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            expression = item.substringBefore(" =")
                        }
                        .padding(vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun QrScannerCard(
    isFa: Boolean,
    onScanned: (String) -> Unit
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var scannerActive by remember { mutableStateOf(false) }
    var scannedHistory by remember { mutableStateOf(listOf<String>()) }
    var copiedBanner by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (granted) scannerActive = true
    }

    NexoraCard(modifier = Modifier.fillMaxWidth()) {
        NexoraSectionHeader(
            eyebrow = "CAMERAX + MLKIT VISION",
            title = if (isFa) "اسکنر زنده بارکد و QR" else "Live CameraX Barcode & QR Scanner"
        )
        NexoraSuccessBanner(message = copiedBanner, onDismiss = { copiedBanner = null })
        Spacer(modifier = Modifier.height(10.dp))

        NexoraButton(
            text = when {
                scannerActive -> if (isFa) "توقف دوربین اسکنر" else "Stop CameraX Stream"
                hasCameraPermission -> if (isFa) "شروع اسکن زنده با دوربین" else "Start Live CameraX Scanner"
                else -> if (isFa) "درخواست مجوز دوربین و شروع اسکن" else "Grant Camera Permission & Scan"
            },
            leadingGlyph = NexoraGlyph.QR,
            onClick = {
                if (hasCameraPermission) {
                    scannerActive = !scannerActive
                } else {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        if (scannerActive && hasCameraPermission) {
            Spacer(modifier = Modifier.height(12.dp))
            CameraXQrScannerPreview(
                onQrDetected = { rawValue ->
                    if (!scannedHistory.contains(rawValue)) {
                        scannedHistory = listOf(rawValue) + scannedHistory
                        onScanned(rawValue)
                    }
                    scannerActive = false
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        if (scannedHistory.isEmpty()) {
            NexoraEmptyState(
                title = if (isFa) "هنوز بارکدی اسکن نشده است" else "Ready to Scan QR & Barcodes",
                subtitle = if (isFa) {
                    "دوربین را به سمت هر کد QR یا بارکد بگیرید تا با MLKit رمزگشایی شود."
                } else {
                    "Point your device camera at any QR code or barcode for instant on-device MLKit decoding."
                },
                glyph = NexoraGlyph.QR
            )
        } else {
            Text(
                text = if (isFa) "بارکدهای رمزگشایی شده" else "DECODED PAYLOADS",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))
            scannedHistory.forEach { code ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = code,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            copyText(context, code)
                            copiedBanner = "Copied: $code"
                        }
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                    }
                }
            }
        }
    }
}

@Composable
private fun QrGeneratorCard(
    isFa: Boolean,
    qrGeneratorUseCase: QrGeneratorUseCase,
    onSaveToVault: (String, String) -> Unit
) {
    var qrInput by remember { mutableStateOf("https://nexora.ai/command-center") }
    var fgHex by remember { mutableStateOf("#00E5FF") }
    var savedMsg by remember { mutableStateOf<String?>(null) }

    val bitmap = remember(qrInput, fgHex) {
        qrGeneratorUseCase.generateQrBitmap(
            content = qrInput,
            sizePx = 512,
            foregroundHex = fgHex,
            backgroundHex = "#070B19"
        )
    }

    NexoraCard(modifier = Modifier.fillMaxWidth()) {
        NexoraSectionHeader(
            eyebrow = "ZXING OFFLINE MATRIX",
            title = if (isFa) "تولیدکننده بارکد QR" else "High-Contrast QR Code Generator"
        )
        NexoraSuccessBanner(message = savedMsg, onDismiss = { savedMsg = null })
        Spacer(modifier = Modifier.height(10.dp))

        NexoraTextField(
            value = qrInput,
            onValueChange = { qrInput = it },
            placeholder = if (isFa) "لینک، متن یا تنظیمات وای‌فای..." else "URL, Wi-Fi config, or text payload..."
        )
        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(
                    selected = fgHex == "#00E5FF",
                    onClick = { fgHex = "#00E5FF" },
                    label = { Text("Cyber Cyan") }
                )
            }
            item {
                FilterChip(
                    selected = fgHex == "#A855F7",
                    onClick = { fgHex = "#A855F7" },
                    label = { Text("Electric Violet") }
                )
            }
            item {
                FilterChip(
                    selected = fgHex == "#FFFFFF",
                    onClick = { fgHex = "#FFFFFF" },
                    label = { Text("Pure White") }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        bitmap?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Generated QR Code",
                modifier = Modifier
                    .size(210.dp)
                    .align(Alignment.CenterHorizontally)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        NexoraButton(
            text = if (isFa) "ذخیره مشخصات QR در صندوق فایل" else "Save QR Payload to Local File Vault",
            leadingGlyph = NexoraGlyph.FILES,
            onClick = {
                onSaveToVault("qr_payload_${System.currentTimeMillis()}.txt", qrInput)
                savedMsg = if (isFa) "در صندوق فایل ذخیره شد." else "Saved QR payload to Local File Vault."
            },
            modifier = Modifier.fillMaxWidth(),
            isPrimary = false
        )
    }
}

@Composable
@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
private fun CameraXQrScannerPreview(
    onQrDetected: (String) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val scanner = remember { BarcodeScanning.getClient() }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { imageAnalysis ->
                        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { imageProxy ->
                            val mediaImage = imageProxy.image
                            if (mediaImage != null) {
                                val image = InputImage.fromMediaImage(
                                    mediaImage,
                                    imageProxy.imageInfo.rotationDegrees
                                )
                                scanner.process(image)
                                    .addOnSuccessListener { barcodes ->
                                        barcodes.firstOrNull()?.rawValue?.let(onQrDetected)
                                    }
                                    .addOnCompleteListener { imageProxy.close() }
                            } else {
                                imageProxy.close()
                            }
                        }
                    }

                runCatching {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                    )
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
    )
}

@Composable
private fun UnitConverterCard(
    isFa: Boolean,
    converter: UnitConverterUseCase
) {
    var category by remember { mutableStateOf(UnitCategory.LENGTH) }
    var inputVal by remember { mutableStateOf("100") }
    var fromUnit by remember { mutableStateOf(category.units.first()) }
    var toUnit by remember { mutableStateOf(category.units[1]) }

    val numericVal = inputVal.toDoubleOrNull() ?: 0.0
    val converted = converter.convert(numericVal, category, fromUnit, toUnit)
    val allConversions = remember(numericVal, category, fromUnit) {
        converter.convertToAllUnits(numericVal, category, fromUnit)
    }

    NexoraCard(modifier = Modifier.fillMaxWidth()) {
        NexoraSectionHeader(
            eyebrow = "5-DOMAIN PRECISION CONVERTER",
            title = if (isFa) "مبدل واحدهای مهندسی" else "Multi-Domain Unit Converter",
            actionLabel = if (isFa) "جابجایی ⇄" else "Swap ⇄",
            onAction = {
                val temp = fromUnit
                fromUnit = toUnit
                toUnit = temp
            }
        )
        Spacer(modifier = Modifier.height(10.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(UnitCategory.entries) { cat ->
                FilterChip(
                    selected = category == cat,
                    onClick = {
                        category = cat
                        fromUnit = cat.units.first()
                        toUnit = cat.units.getOrElse(1) { cat.units.last() }
                    },
                    label = { Text(if (isFa) cat.displayNameFa else cat.displayNameEn) }
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))

        NexoraTextField(
            value = inputVal,
            onValueChange = { inputVal = it },
            label = "From: $fromUnit",
            placeholder = "Enter numeric value..."
        )
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (isFa) "واحد مبدا:" else "SOURCE UNIT",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(category.units) { u ->
                FilterChip(
                    selected = fromUnit == u,
                    onClick = { fromUnit = u },
                    label = { Text(u) }
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (isFa) "واحد مقصد:" else "TARGET UNIT",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(category.units) { u ->
                FilterChip(
                    selected = toUnit == u,
                    onClick = { toUnit = u },
                    label = { Text("→ $u") }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "%.4f %s".format(converted, toUnit),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = if (isFa) "جدول مرجع تمام واحدها" else "ALL EQUIVALENT UNITS",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        allConversions.forEach { (unitName, unitValue) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(unitName, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = "%.4f".format(unitValue),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun NotesManagerCard(
    isFa: Boolean,
    notes: List<NoteEntity>,
    onSave: (String, String, String) -> Unit,
    onTogglePin: (NoteEntity) -> Unit,
    onDelete: (NoteEntity) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var tag by remember { mutableStateOf("Architecture") }
    var searchQuery by remember { mutableStateOf("") }

    val filteredNotes = remember(notes, searchQuery) {
        if (searchQuery.isBlank()) notes
        else notes.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                it.content.contains(searchQuery, ignoreCase = true) ||
                it.tag.contains(searchQuery, ignoreCase = true)
        }
    }

    NexoraCard(modifier = Modifier.fillMaxWidth()) {
        NexoraSectionHeader(
            eyebrow = "ROOM SQLITE VAULT",
            title = if (isFa) "مدیریت یادداشت‌های محلی" else "Room Persistent Notes Manager"
        )
        Spacer(modifier = Modifier.height(10.dp))

        NexoraTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = if (isFa) "جستجو در یادداشت‌ها..." else "Search stored notes by title, content, or tag..."
        )
        Spacer(modifier = Modifier.height(10.dp))

        NexoraTextField(
            value = title,
            onValueChange = { title = it },
            label = if (isFa) "عنوان یادداشت" else "Note Title",
            placeholder = if (isFa) "عنوان..." else "Enter note title..."
        )
        Spacer(modifier = Modifier.height(6.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Architecture", "System", "AI", "Personal").forEach { t ->
                item {
                    FilterChip(
                        selected = tag == t,
                        onClick = { tag = t },
                        label = { Text("#$t") }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))

        NexoraTextField(
            value = content,
            onValueChange = { content = it },
            label = if (isFa) "متن یادداشت" else "Markdown / Content",
            placeholder = if (isFa) "محتوای یادداشت..." else "Write note content...",
            minLines = 3
        )
        Spacer(modifier = Modifier.height(10.dp))

        NexoraButton(
            text = if (isFa) "ذخیره یادداشت در Room" else "Save Note to Room Database",
            leadingGlyph = NexoraGlyph.NOTES,
            onClick = {
                onSave(title, content, tag)
                title = ""
                content = ""
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(14.dp))

        if (filteredNotes.isEmpty()) {
            NexoraEmptyState(
                title = if (isFa) "یادداشتی یافت نشد" else "No Notes Match Filter",
                subtitle = if (isFa) "یک یادداشت جدید ایجاد کنید." else "Create a note above to persist it locally in Room.",
                glyph = NexoraGlyph.NOTES
            )
        } else {
            filteredNotes.forEach { note ->
                NexoraCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    cornerRadius = 14.dp,
                    borderColor = if (note.isPinned) NexoraColors.CyberCyan.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (note.isPinned) "★ ${note.title} [${note.tag}]" else "${note.title} [${note.tag}]",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = note.content,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { onTogglePin(note) }) {
                            Icon(Icons.Default.PushPin, contentDescription = "Pin")
                        }
                        IconButton(onClick = { onDelete(note) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TextUtilitiesCard(
    isFa: Boolean,
    textUtilitiesUseCase: TextUtilitiesUseCase
) {
    val context = LocalContext.current
    var rawText by remember { mutableStateOf("NEXORA Command Center: AI & Clean Architecture.") }
    var copiedNotice by remember { mutableStateOf<String?>(null) }
    val stats = remember(rawText) { textUtilitiesUseCase.analyze(rawText) }

    NexoraCard(modifier = Modifier.fillMaxWidth()) {
        NexoraSectionHeader(
            eyebrow = "CRYPTOGRAPHY & LINGUISTICS",
            title = if (isFa) "تحلیلگر متن و هش رمزنگاری" else "Text Analyzer & Cryptographic Hash Suite"
        )
        NexoraSuccessBanner(message = copiedNotice, onDismiss = { copiedNotice = null })
        Spacer(modifier = Modifier.height(10.dp))

        NexoraTextField(
            value = rawText,
            onValueChange = { rawText = it },
            placeholder = "Enter text to hash, encode, or analyze...",
            minLines = 3
        )
        Spacer(modifier = Modifier.height(10.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                OutlinedButton(onClick = { rawText = rawText.uppercase() }) {
                    Text("UPPERCASE")
                }
            }
            item {
                OutlinedButton(onClick = { rawText = rawText.lowercase() }) {
                    Text("lowercase")
                }
            }
            item {
                OutlinedButton(
                    onClick = {
                        textUtilitiesUseCase.decodeBase64(rawText).onSuccess { rawText = it }
                    }
                ) {
                    Text("Decode Base64")
                }
            }
            item {
                OutlinedButton(
                    onClick = {
                        copyText(context, stats.sha256Hex)
                        copiedNotice = "SHA-256 copied to clipboard"
                    }
                ) {
                    Text("Copy SHA-256")
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Words: ${stats.words} · Chars: ${stats.characters} (${stats.charactersNoSpaces} no-space) · Sentences: ${stats.sentences} · Lines: ${stats.lines} · Read Time: ~${stats.estimatedReadSeconds}s",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "SHA-256: ${stats.sha256Hex}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "MD5: ${stats.md5Hex}",
            style = MaterialTheme.typography.labelMedium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Base64: ${stats.base64Encoded}",
            style = MaterialTheme.typography.labelMedium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "URL Encoded: ${stats.urlEncoded}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FileVaultCard(
    isFa: Boolean,
    viewModel: ToolsViewModel
) {
    var files by remember { mutableStateOf(viewModel.listLocalVaultFiles()) }
    var fileName by remember { mutableStateOf("diagnostics_report.txt") }
    var fileContent by remember { mutableStateOf("Generated by NEXORA File Explorer.") }
    var selectedFileForInspect by remember { mutableStateOf<LocalVaultFile?>(null) }

    selectedFileForInspect?.let { inspected ->
        NexoraDialog(
            title = inspected.name,
            onDismiss = { selectedFileForInspect = null }
        ) {
            Text(
                text = "Size: ${inspected.sizeBytes} bytes",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = inspected.content,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace
            )
        }
    }

    NexoraCard(modifier = Modifier.fillMaxWidth()) {
        NexoraSectionHeader(
            eyebrow = "APP SANDBOX STORAGE",
            title = if (isFa) "مرورگر و مدیریت فایل‌های داخلی" else "Local Sandbox File Explorer"
        )
        Spacer(modifier = Modifier.height(10.dp))

        NexoraTextField(
            value = fileName,
            onValueChange = { fileName = it },
            label = if (isFa) "نام فایل" else "File Name",
            placeholder = "example.md"
        )
        Spacer(modifier = Modifier.height(6.dp))
        NexoraTextField(
            value = fileContent,
            onValueChange = { fileContent = it },
            label = if (isFa) "محتوای فایل" else "File Contents",
            placeholder = "Write file contents...",
            minLines = 3
        )
        Spacer(modifier = Modifier.height(10.dp))
        NexoraButton(
            text = if (isFa) "ذخیره فایل در حافظه داخلی" else "Write File to Sandbox Vault",
            leadingGlyph = NexoraGlyph.FILES,
            onClick = { files = viewModel.createVaultFile(fileName, fileContent) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(14.dp))

        files.forEach { file ->
            NexoraCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                cornerRadius = 14.dp,
                onClick = { selectedFileForInspect = file }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${file.name} (${file.sizeBytes} B)",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = file.content.take(100),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { files = viewModel.deleteVaultFile(file.name) }
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete File")
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickNetworkDiagCard(
    isFa: Boolean,
    viewModel: ToolsViewModel,
    onOpenFullNetwork: () -> Unit
) {
    var target by remember { mutableStateOf("1.1.1.1") }
    val pingRes by viewModel.quickPingResult.collectAsState()
    val localSnap = remember { viewModel.diagnosticsEngine.inspectLocalInterfaces() }

    NexoraCard(modifier = Modifier.fillMaxWidth()) {
        NexoraSectionHeader(
            eyebrow = "RAPID NETWORK PROBE",
            title = if (isFa) "عیب‌یابی سریع شبکه" else "Quick Latency & Interface Probe",
            actionLabel = if (isFa) "مرکز کامل شبکه ←" else "Full Network Center →",
            onAction = onOpenFullNetwork
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text("Interface: ${localSnap.transportType} (${localSnap.interfaceName}) · IPv4: ${localSnap.localIpv4}")
        Text("Downstream: ${localSnap.downstreamKbps} Kbps · Upstream: ${localSnap.upstreamKbps} Kbps")
        Spacer(modifier = Modifier.height(10.dp))
        NexoraTextField(
            value = target,
            onValueChange = { target = it },
            label = if (isFa) "آدرس هدف پینگ" else "Ping Host Target",
            placeholder = "1.1.1.1 or cloudflare.com"
        )
        Spacer(modifier = Modifier.height(10.dp))
        NexoraButton(
            text = if (isFa) "اجرای تست پینگ سریع" else "Execute Quick Ping Probe",
            leadingGlyph = NexoraGlyph.NETWORK,
            onClick = { viewModel.runQuickPing(target) },
            modifier = Modifier.fillMaxWidth()
        )
        pingRes?.let { res ->
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "${res.host} (${res.resolvedIp}) -> ${res.rttMs} ms [${res.method}]",
                style = MaterialTheme.typography.titleMedium,
                color = if (res.reachable) NexoraColors.EmeraldSignal else NexoraColors.AmberSignal
            )
        }
    }
}

private fun copyText(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("NEXORA Smart Tool", text))
}
