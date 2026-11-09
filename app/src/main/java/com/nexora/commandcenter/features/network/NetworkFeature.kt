package com.nexora.commandcenter.features.network

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexora.commandcenter.core.common.NetworkMonitor
import com.nexora.commandcenter.core.database.ActivityLogDao
import com.nexora.commandcenter.core.database.ActivityLogEntity
import com.nexora.commandcenter.core.datastore.NexoraPreferencesDataStore
import com.nexora.commandcenter.core.designsystem.LatencySparklineChart
import com.nexora.commandcenter.core.designsystem.NexoraButton
import com.nexora.commandcenter.core.designsystem.NexoraCard
import com.nexora.commandcenter.core.designsystem.NexoraColors
import com.nexora.commandcenter.core.designsystem.NexoraErrorState
import com.nexora.commandcenter.core.designsystem.NexoraGlyph
import com.nexora.commandcenter.core.designsystem.NexoraLoading
import com.nexora.commandcenter.core.designsystem.NexoraSectionHeader
import com.nexora.commandcenter.core.designsystem.NexoraTextField
import com.nexora.commandcenter.core.network.DnsLookupResult
import com.nexora.commandcenter.core.network.LocalNetworkSnapshot
import com.nexora.commandcenter.core.network.NetworkDiagnosticsEngine
import com.nexora.commandcenter.core.network.PingResult
import com.nexora.commandcenter.core.network.PublicIpLookupApi
import com.nexora.commandcenter.core.network.PublicIpResponseDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NetworkViewModel @Inject constructor(
    private val diagnosticsEngine: NetworkDiagnosticsEngine,
    private val publicIpLookupApi: PublicIpLookupApi,
    private val activityLogDao: ActivityLogDao,
    preferencesDataStore: NexoraPreferencesDataStore,
    networkMonitor: NetworkMonitor
) : ViewModel() {

    val isOnlineFlow: Flow<Boolean> = networkMonitor.isOnline

    val prefs = preferencesDataStore.userPreferencesFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        NexoraPreferencesDataStore.UserPreferences()
    )

    private val _localSnapshot = MutableStateFlow(diagnosticsEngine.inspectLocalInterfaces())
    val localSnapshot: StateFlow<LocalNetworkSnapshot> = _localSnapshot.asStateFlow()

    private val _pingHistory = MutableStateFlow<List<PingResult>>(emptyList())
    val pingHistory: StateFlow<List<PingResult>> = _pingHistory.asStateFlow()

    private val _dnsResult = MutableStateFlow<DnsLookupResult?>(null)
    val dnsResult: StateFlow<DnsLookupResult?> = _dnsResult.asStateFlow()

    private val _publicIp = MutableStateFlow<PublicIpResponseDto?>(null)
    val publicIp: StateFlow<PublicIpResponseDto?> = _publicIp.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _errorText = MutableStateFlow<String?>(null)
    val errorText: StateFlow<String?> = _errorText.asStateFlow()

    fun refreshLocalOfflineDiagnostics() {
        _localSnapshot.value = diagnosticsEngine.inspectLocalInterfaces()
    }

    fun executePing(host: String, burstCount: Int = 1) {
        viewModelScope.launch {
            _isBusy.value = true
            _errorText.value = null
            repeat(burstCount.coerceIn(1, 4)) {
                val result = diagnosticsEngine.pingHost(host)
                _pingHistory.value = (_pingHistory.value + result).takeLast(16)
            }
            val latest = _pingHistory.value.lastOrNull()
            if (latest != null) {
                activityLogDao.insertActivity(
                    ActivityLogEntity(
                        module = "NETWORK",
                        actionTitle = "Ping Diagnostics (${latest.host})",
                        detail = "${latest.rttMs} ms via ${latest.method}"
                    )
                )
            }
            _isBusy.value = false
        }
    }

    fun executeDnsLookup(hostname: String) {
        viewModelScope.launch {
            _isBusy.value = true
            _errorText.value = null
            runCatching { diagnosticsEngine.resolveDns(hostname) }
                .onSuccess { dns ->
                    _dnsResult.value = dns
                    activityLogDao.insertActivity(
                        ActivityLogEntity(
                            module = "NETWORK",
                            actionTitle = "DNS Lookup (${dns.hostname})",
                            detail = "${dns.ipv4Records.size} A records in ${dns.lookupTimeMs} ms"
                        )
                    )
                }
                .onFailure { err ->
                    _errorText.value = "DNS Resolution Error: ${err.localizedMessage}"
                }
            _isBusy.value = false
        }
    }

    fun fetchPublicIp() {
        viewModelScope.launch {
            _isBusy.value = true
            _errorText.value = null
            runCatching { publicIpLookupApi.lookupPublicIp() }
                .onSuccess { ipDto ->
                    _publicIp.value = ipDto
                    activityLogDao.insertActivity(
                        ActivityLogEntity(
                            module = "NETWORK",
                            actionTitle = "Public IP Telemetry",
                            detail = "${ipDto.city}, ${ipDto.country} (${ipDto.isp})"
                        )
                    )
                }
                .onFailure { err ->
                    _errorText.value = "Public IP Lookup Failed: ${err.localizedMessage}"
                }
            _isBusy.value = false
        }
    }
}

@Composable
fun NetworkScreen(
    isFa: Boolean,
    viewModel: NetworkViewModel = hiltViewModel()
) {
    val isOnline by viewModel.isOnlineFlow.collectAsState(initial = true)
    val prefs by viewModel.prefs.collectAsState()
    val localSnapshot by viewModel.localSnapshot.collectAsState()
    val pingHistory by viewModel.pingHistory.collectAsState()
    val dnsResult by viewModel.dnsResult.collectAsState()
    val publicIp by viewModel.publicIp.collectAsState()
    val isBusy by viewModel.isBusy.collectAsState()
    val errorText by viewModel.errorText.collectAsState()

    var targetHost by remember { mutableStateOf("cloudflare.com") }
    val presetTargets = remember { listOf("1.1.1.1", "8.8.8.8", "cloudflare.com", "google.com") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // SECTION 1: OFFLINE LOCAL NETWORK INTERFACE DIAGNOSTICS
        item {
            NexoraCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = NexoraColors.EmeraldOnline.copy(alpha = 0.45f)
            ) {
                NexoraSectionHeader(
                    eyebrow = "OFFLINE HARDWARE & OS INSPECTION",
                    title = if (isFa) {
                        "۱. عیب‌یابی رابط شبکه محلی (کارکرد کامل در حالت آفلاین)"
                    } else {
                        "1. Offline Local Hardware & OS Interface Diagnostics"
                    },
                    actionLabel = if (isFa) "بروزرسانی" else "Refresh",
                    onAction = { viewModel.refreshLocalOfflineDiagnostics() }
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Connectivity Monitor: ${if (isOnline) "VALIDATED INTERNET" else "OFFLINE / LOCAL ONLY"}",
                    color = if (isOnline) NexoraColors.EmeraldSignal else NexoraColors.AmberSignal,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text("Transport: ${localSnapshot.transportType} (${localSnapshot.interfaceName})")
                Text("Local IPv4: ${if (prefs.privacyRedactIp) "***.***.***.*** (Redacted)" else localSnapshot.localIpv4}")
                Text("Local IPv6: ${if (prefs.privacyRedactIp) "****:**** (Redacted)" else localSnapshot.localIpv6}")
                Text("System DNS Servers: ${localSnapshot.dnsServers.joinToString().ifBlank { "System Default" }}")
                Text("Link Capacity: ↓ ${localSnapshot.downstreamKbps} Kbps / ↑ ${localSnapshot.upstreamKbps} Kbps")
                Text("VPN Tunnel Active: ${if (localSnapshot.isVpnActive) "Yes" else "No"} · Metered: ${if (localSnapshot.isMetered) "Yes" else "No"}")
            }
        }

        // SECTION 2: ONLINE INTERNET DIAGNOSTICS (PING, DNS, PUBLIC IP)
        item {
            NexoraCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = NexoraColors.NeonCyan.copy(alpha = 0.45f)
            ) {
                NexoraSectionHeader(
                    eyebrow = "ONLINE TELEMETRY SUITE",
                    title = if (isFa) {
                        "۲. ابزارهای آنلاین اینترنت (پینگ، DNS و آی‌پی عمومی)"
                    } else {
                        "2. Online Internet Diagnostics (Ping, DNS & Public IP)"
                    }
                )
                Spacer(modifier = Modifier.height(10.dp))

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(presetTargets) { preset ->
                        FilterChip(
                            selected = targetHost == preset,
                            onClick = { targetHost = preset },
                            label = { Text(preset) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                NexoraTextField(
                    value = targetHost,
                    onValueChange = { targetHost = it },
                    label = if (isFa) "دامنه یا آی‌پی هدف" else "Hostname or IP Target",
                    placeholder = "cloudflare.com or 1.1.1.1"
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NexoraButton(
                        text = if (isFa) "پینگ (4x Burst)" else "4x Ping Burst",
                        leadingGlyph = NexoraGlyph.NETWORK,
                        onClick = { viewModel.executePing(targetHost, burstCount = 4) },
                        modifier = Modifier.weight(1f)
                    )
                    NexoraButton(
                        text = if (isFa) "بررسی DNS" else "DNS Lookup",
                        onClick = { viewModel.executeDnsLookup(targetHost) },
                        modifier = Modifier.weight(1f),
                        isPrimary = false
                    )
                }

                if (isBusy) {
                    Spacer(modifier = Modifier.height(10.dp))
                    NexoraLoading(label = if (isFa) "در حال اجرای پروب شبکه..." else "Executing network probe...")
                }

                errorText?.let { err ->
                    Spacer(modifier = Modifier.height(10.dp))
                    NexoraErrorState(
                        title = "Network Probe Alert",
                        message = err
                    )
                }

                if (pingHistory.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    val validRtts = pingHistory.filter { it.rttMs >= 0 }.map { it.rttMs }
                    val avgRtt = if (validRtts.isEmpty()) 0L else validRtts.average().toLong()
                    val minRtt = validRtts.minOrNull() ?: 0L
                    val maxRtt = validRtts.maxOrNull() ?: 0L

                    Text(
                        text = "RTT Telemetry · Min: ${minRtt}ms · Avg: ${avgRtt}ms · Max: ${maxRtt}ms (${pingHistory.size} samples)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LatencySparklineChart(rttSamples = pingHistory.map { it.rttMs })
                    val latest = pingHistory.last()
                    Text(
                        text = "Latest: ${latest.host} (${latest.resolvedIp}) -> ${latest.rttMs} ms via ${latest.method}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                dnsResult?.let { dns ->
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "DNS Records (${dns.lookupTimeMs} ms) for ${dns.hostname}:",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text("A (IPv4): ${dns.ipv4Records.joinToString().ifBlank { "None" }}")
                    Text("AAAA (IPv6): ${dns.ipv6Records.joinToString().ifBlank { "None" }}")
                }

                Spacer(modifier = Modifier.height(14.dp))
                OutlinedButton(
                    onClick = { viewModel.fetchPublicIp() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isFa) "استعلام آی‌پی عمومی و موقعیت ISP" else "Query Public IP & ASN Telemetry")
                }

                publicIp?.let { ipInfo ->
                    Spacer(modifier = Modifier.height(8.dp))
                    val displayedIp = if (prefs.privacyRedactIp) "***.***.***.*** (Privacy Redacted)" else ipInfo.ip
                    Text(
                        text = "Public IP: $displayedIp (${ipInfo.type})",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text("Location: ${ipInfo.city}, ${ipInfo.country} · ISP: ${ipInfo.isp}")
                }
            }
        }
    }
}
