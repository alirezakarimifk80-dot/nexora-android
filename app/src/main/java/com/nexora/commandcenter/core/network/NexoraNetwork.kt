package com.nexora.commandcenter.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.nexora.commandcenter.BuildConfig
import com.nexora.commandcenter.core.datastore.NexoraPreferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Streaming
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.Collections
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlin.system.measureTimeMillis

@Serializable
data class ChatHistoryItemDto(
    val role: String,
    val content: String
)

@Serializable
data class MemoryContextItemDto(
    val title: String,
    val factContent: String,
    val category: String = "General"
)

@Serializable
data class TokenUsageDto(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0
)

@Serializable
data class ChatRequestDto(
    val message: String,
    val conversationId: String? = null,
    val history: List<ChatHistoryItemDto> = emptyList(),
    val memories: List<MemoryContextItemDto> = emptyList(),
    val language: String = "en",
    val mode: String = "chat", // "chat" or "summarize"
    val provider: String? = "cloudflare-workers-ai",
    val model: String? = "@cf/meta/llama-3.3-70b-instruct-fp8-fast",
    val temperature: Double = 0.4,
    @SerialName("max_tokens") val maxTokens: Int = 1024
)

@Serializable
data class ChatResponseDto(
    val reply: String,
    val provider: String = "cloudflare-workers-ai",
    val model: String = "@cf/meta/llama-3.3-70b-instruct-fp8-fast",
    val latencyMs: Long = 0L,
    val usage: TokenUsageDto = TokenUsageDto(),
    val usedMemoriesCount: Int = 0,
    val conversationId: String = "",
    val d1Persisted: Boolean = false,
    val r2ObjectKey: String? = null
)

@Serializable
data class StreamChunkDto(
    val text: String = "",
    val chunk: String = "",
    val done: Boolean = false,
    val error: String? = null,
    val provider: String = "cloudflare-workers-ai",
    val model: String = "@cf/meta/llama-3.3-70b-instruct-fp8-fast"
) {
    val resolvedText: String
        get() = text.ifEmpty { chunk }
}

@Serializable
data class ApiErrorEnvelopeDto(
    val detail: String = "",
    val error: String = "",
    val message: String = "",
    val code: String = ""
) {
    val readableMessage: String
        get() = detail.ifBlank { error.ifBlank { message } }
}

@Serializable
data class ImageAnalysisRequestDto(
    val prompt: String,
    val imageBase64: String,
    val mimeType: String = "image/jpeg",
    val language: String = "en",
    val provider: String? = "cloudflare-workers-ai",
    val model: String? = "@cf/meta/llama-3.2-11b-vision-instruct",
    val storeInR2: Boolean = false
)

@Serializable
data class BackendHealthDto(
    val status: String = "ok",
    val service: String = "nexora-cloudflare-worker-ai",
    val version: String = "4.0.0",
    val infrastructure: String = "Cloudflare Workers + Workers AI + D1 + R2",
    val provider: String = "cloudflare-workers-ai",
    val activeProvider: String = "cloudflare-workers-ai",
    val model: String = "@cf/meta/llama-3.3-70b-instruct-fp8-fast",
    val activeModel: String = "@cf/meta/llama-3.3-70b-instruct-fp8-fast",
    val visionModel: String = "@cf/meta/llama-3.2-11b-vision-instruct",
    val fallbackModels: List<String> = listOf(
        "@cf/meta/llama-3.3-70b-instruct-fp8-fast",
        "@cf/google/gemma-3-12b-it",
        "@cf/meta/llama-3.1-8b-instruct"
    ),
    val configured: Boolean = true,
    val apiKeyPresent: Boolean = true,
    val configuredProviders: List<String> = listOf("cloudflare-workers-ai"),
    val streamingSupported: Boolean = true,
    val visionSupported: Boolean = true,
    val rateLimitPerMinute: Int = 40,
    val timeoutSeconds: Double = 30.0,
    val configurationNotice: String = "Cloudflare Workers AI ready",
    val timestampMs: Long = 0L
)

@Serializable
data class ProviderInfoDto(
    val activeProvider: String = "cloudflare-workers-ai",
    val activeModel: String = "@cf/meta/llama-3.3-70b-instruct-fp8-fast",
    val activeVisionModel: String = "@cf/meta/llama-3.2-11b-vision-instruct",
    val fallbackModels: List<String> = listOf(
        "@cf/meta/llama-3.3-70b-instruct-fp8-fast",
        "@cf/google/gemma-3-12b-it",
        "@cf/meta/llama-3.1-8b-instruct"
    ),
    val configured: Boolean = true,
    val apiKeyPresent: Boolean = true,
    val configuredProviders: List<String> = listOf("cloudflare-workers-ai"),
    val streamingSupported: Boolean = true,
    val visionSupported: Boolean = true,
    val maxImageSizeBytes: Int = 4194304,
    val supportedImageMimeTypes: List<String> = listOf("image/jpeg", "image/png", "image/webp"),
    val rateLimitPerMinute: Int = 40,
    val timeoutSeconds: Double = 30.0,
    val configurationNotice: String = "Cloudflare Workers AI ready"
)

@Serializable
data class CloudflareModelDescriptorDto(
    val id: String,
    val name: String,
    val task: String = "text-generation",
    val provider: String = "Cloudflare Workers AI",
    val persianSupport: String = "excellent",
    val visionSupported: Boolean = false,
    val recommended: Boolean = false,
    val description: String = ""
)

@Serializable
data class CloudflareModelsCatalogDto(
    val provider: String = "cloudflare-workers-ai",
    val defaultTextModel: String = "@cf/meta/llama-3.3-70b-instruct-fp8-fast",
    val defaultVisionModel: String = "@cf/meta/llama-3.2-11b-vision-instruct",
    val models: List<CloudflareModelDescriptorDto> = emptyList()
)

@Serializable
data class CloudConversationCreateDto(
    val id: String? = null,
    val title: String,
    val language: String = "en",
    val model: String = "@cf/meta/llama-3.3-70b-instruct-fp8-fast"
)

@Serializable
data class CloudConversationDto(
    val id: String,
    val userId: String = "",
    val title: String,
    val language: String = "en",
    val model: String = "@cf/meta/llama-3.3-70b-instruct-fp8-fast",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

@Serializable
data class CloudConversationsListDto(
    val conversations: List<CloudConversationDto> = emptyList(),
    val d1Connected: Boolean = true
)

@Serializable
data class UserProfileDto(
    val id: String = "",
    val email: String = "",
    val fullName: String = "",
    val avatarUrl: String = "",
    val role: String = "user",
    val emailVerified: Boolean = false,
    val accountStatus: String = "active",
    val subscriptionPlan: String = "free",
    val subscriptionStatus: String = "active",
    val subscriptionExpiresAt: Long = 0L,
    val aiRequestsToday: Int = 0,
    val dailyAiLimit: Int = 25
)

@Serializable
data class AuthRegisterRequestDto(
    val email: String,
    val password: String,
    val fullName: String,
    val deviceInfo: String = "NEXORA Android"
)

@Serializable
data class AuthLoginRequestDto(
    val email: String,
    val password: String,
    val deviceInfo: String = "NEXORA Android"
)

@Serializable
data class AuthSessionResponseDto(
    val sessionToken: String,
    val expiresAt: Long = 0L,
    val verificationTokenPreview: String = "",
    val user: UserProfileDto
)

@Serializable
data class CloudSyncItemDto(
    val entityType: String,
    val entityId: String,
    val payload: Map<String, String> = emptyMap(),
    val version: Int = 1,
    val updatedAt: Long = 0L
)

@Serializable
data class CloudSyncDeltaRequestDto(
    val items: List<CloudSyncItemDto>
)

@Serializable
data class CloudSyncDeltaResponseDto(
    val syncStatus: String = "synced",
    val syncedAt: Long = 0L,
    val appliedCount: Int = 0,
    val conflictCount: Int = 0
)

@Serializable
data class SubscriptionVerifyRequestDto(
    val planId: String,
    val purchaseToken: String
)

@Serializable
data class SubscriptionVerifyResponseDto(
    val verified: Boolean = true,
    val environment: String = "PLAY_BILLING_TEST_SANDBOX",
    val notice: String = "",
    val user: UserProfileDto
)

@Serializable
data class SubscriptionRestoreResponseDto(
    val restored: Boolean = false,
    val activePlan: String = "free",
    val environment: String = "NONE",
    val user: UserProfileDto
)

@Serializable
data class PublicIpResponseDto(
    val ip: String = "",
    val success: Boolean = true,
    val type: String = "IPv4",
    val country: String = "",
    val city: String = "",
    val isp: String = "",
    @SerialName("org") val organization: String = ""
)

interface NexoraBackendApi {
    @GET("api/health")
    suspend fun checkHealth(): BackendHealthDto

    @GET("api/ai/status")
    suspend fun getProvidersInfo(): ProviderInfoDto

    @GET("api/models")
    suspend fun getModelsCatalog(): CloudflareModelsCatalogDto

    @POST("api/ai/chat")
    suspend fun sendChatMessage(@Body request: ChatRequestDto): ChatResponseDto

    @Streaming
    @POST("api/ai/chat/stream")
    suspend fun streamChatMessage(@Body request: ChatRequestDto): ResponseBody

    @POST("api/ai/chat")
    suspend fun summarizeText(@Body request: ChatRequestDto): ChatResponseDto

    @POST("api/ai/vision")
    suspend fun analyzeImage(@Body request: ImageAnalysisRequestDto): ChatResponseDto

    @POST("api/conversations")
    suspend fun createCloudConversation(@Body request: CloudConversationCreateDto): CloudConversationDto

    @GET("api/conversations")
    suspend fun listCloudConversations(): CloudConversationsListDto

    @DELETE("api/conversations/{id}")
    suspend fun deleteCloudConversation(@Path("id") conversationId: String): Map<String, Boolean>

    @POST("api/v1/auth/register")
    suspend fun registerAccount(@Body request: AuthRegisterRequestDto): AuthSessionResponseDto

    @POST("api/v1/auth/login")
    suspend fun loginAccount(@Body request: AuthLoginRequestDto): AuthSessionResponseDto

    @POST("api/v1/auth/logout")
    suspend fun logoutAccount(@Header("Authorization") authorization: String): Map<String, Boolean>

    @POST("api/v1/sync/delta")
    suspend fun syncDelta(
        @Header("Authorization") authorization: String,
        @Body request: CloudSyncDeltaRequestDto
    ): CloudSyncDeltaResponseDto

    @POST("api/v1/subscriptions/verify")
    suspend fun verifySubscription(
        @Header("Authorization") authorization: String,
        @Body request: SubscriptionVerifyRequestDto
    ): SubscriptionVerifyResponseDto

    @POST("api/v1/subscriptions/restore")
    suspend fun restoreSubscription(
        @Header("Authorization") authorization: String
    ): SubscriptionRestoreResponseDto
}

interface PublicIpLookupApi {
    @GET("https://ipwho.is/")
    suspend fun lookupPublicIp(): PublicIpResponseDto
}

data class LocalNetworkSnapshot(
    val isConnected: Boolean,
    val transportType: String,
    val localIpv4: String,
    val localIpv6: String,
    val dnsServers: List<String>,
    val interfaceName: String,
    val downstreamKbps: Int,
    val upstreamKbps: Int,
    val isVpnActive: Boolean,
    val isMetered: Boolean
)

data class PingResult(
    val host: String,
    val resolvedIp: String,
    val reachable: Boolean,
    val rttMs: Long,
    val method: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class DnsLookupResult(
    val hostname: String,
    val ipv4Records: List<String>,
    val ipv6Records: List<String>,
    val lookupTimeMs: Long
)

@Singleton
class NetworkDiagnosticsEngine(
    private val context: Context
) {
    /**
     * Offline-capable hardware & OS network interface inspection.
     * Works completely offline without external internet access.
     */
    fun inspectLocalInterfaces(): LocalNetworkSnapshot {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetwork
        val caps = activeNetwork?.let { cm.getNetworkCapabilities(it) }
        val linkProps: LinkProperties? = activeNetwork?.let { cm.getLinkProperties(it) }

        val transport = when {
            caps == null -> "Disconnected"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi (802.11)"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular Data"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet LAN"
            else -> "Other Interface"
        }

        var ipv4 = "Unavailable"
        var ipv6 = "Unavailable"

        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue
                for (addr in Collections.list(intf.inetAddresses)) {
                    if (addr.isLoopbackAddress) continue
                    if (addr is Inet4Address && ipv4 == "Unavailable") {
                        ipv4 = addr.hostAddress ?: "Unavailable"
                    } else if (addr is Inet6Address && ipv6 == "Unavailable") {
                        ipv6 = addr.hostAddress?.substringBefore("%") ?: "Unavailable"
                    }
                }
            }
        } catch (_: Exception) {
        }

        val dnsList = linkProps?.dnsServers?.mapNotNull { it.hostAddress } ?: emptyList()

        return LocalNetworkSnapshot(
            isConnected = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true,
            transportType = transport,
            localIpv4 = ipv4,
            localIpv6 = ipv6,
            dnsServers = dnsList,
            interfaceName = linkProps?.interfaceName ?: "wlan0",
            downstreamKbps = caps?.linkDownstreamBandwidthKbps ?: 0,
            upstreamKbps = caps?.linkUpstreamBandwidthKbps ?: 0,
            isVpnActive = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true,
            isMetered = !(caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) ?: true)
        )
    }

    /**
     * Online latency test using TCP socket handshake (port 443/53) with ICMP fallback.
     */
    suspend fun pingHost(host: String, timeoutMs: Int = 3000): PingResult = withContext(Dispatchers.IO) {
        val cleanHost = host.trim().removePrefix("https://").removePrefix("http://").substringBefore("/")
        try {
            val address = InetAddress.getByName(cleanHost)
            val resolvedIp = address.hostAddress ?: cleanHost

            var tcpSuccess = false
            val rtt = measureTimeMillis {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(address, 443), timeoutMs)
                    tcpSuccess = true
                }
            }
            if (tcpSuccess) {
                return@withContext PingResult(
                    host = cleanHost,
                    resolvedIp = resolvedIp,
                    reachable = true,
                    rttMs = rtt,
                    method = "TCP/443 Handshake"
                )
            }
        } catch (_: Exception) {
        }

        try {
            val address = InetAddress.getByName(cleanHost)
            var reachable = false
            val rtt = measureTimeMillis {
                reachable = address.isReachable(timeoutMs)
            }
            PingResult(
                host = cleanHost,
                resolvedIp = address.hostAddress ?: cleanHost,
                reachable = reachable,
                rttMs = rtt,
                method = "ICMP / Echo"
            )
        } catch (e: Exception) {
            PingResult(
                host = cleanHost,
                resolvedIp = "Unresolved",
                reachable = false,
                rttMs = -1L,
                method = e.localizedMessage ?: "Unreachable"
            )
        }
    }

    /**
     * Online DNS resolution returning all A (IPv4) and AAAA (IPv6) records.
     */
    suspend fun resolveDns(hostname: String): DnsLookupResult = withContext(Dispatchers.IO) {
        val cleanHost = hostname.trim().removePrefix("https://").removePrefix("http://").substringBefore("/")
        val ipv4 = mutableListOf<String>()
        val ipv6 = mutableListOf<String>()
        val elapsed = measureTimeMillis {
            val addresses = InetAddress.getAllByName(cleanHost)
            for (addr in addresses) {
                when (addr) {
                    is Inet4Address -> addr.hostAddress?.let { ipv4.add(it) }
                    is Inet6Address -> addr.hostAddress?.substringBefore("%")?.let { ipv6.add(it) }
                }
            }
        }
        DnsLookupResult(
            hostname = cleanHost,
            ipv4Records = ipv4,
            ipv6Records = ipv6,
            lookupTimeMs = elapsed
        )
    }
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BackendRetrofit

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        preferencesDataStore: NexoraPreferencesDataStore
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        val dynamicEndpointInterceptor = Interceptor { chain ->
            val original = chain.request()
            // Avoid rewriting external IP lookup requests
            if (original.url.host.contains("ipwho.is")) {
                return@Interceptor chain.proceed(original)
            }
            val currentPrefs: NexoraPreferencesDataStore.UserPreferences? = runBlocking {
                preferencesDataStore.userPreferencesFlow.firstOrNull()
            }
            val configuredBase = currentPrefs?.backendBaseUrl?.trim().orEmpty()
            var builder = original.newBuilder()

            if (configuredBase.isNotBlank()) {
                val normalized = if (configuredBase.endsWith("/")) configuredBase else "$configuredBase/"
                val parsedBase = normalized.toHttpUrlOrNull()
                if (parsedBase != null) {
                    val newUrl = original.url.newBuilder()
                        .scheme(parsedBase.scheme)
                        .host(parsedBase.host)
                        .port(parsedBase.port)
                        .build()
                    builder = builder.url(newUrl)
                }
            }

            val token = currentPrefs?.sessionToken?.trim().orEmpty()
            if (token.isNotBlank() && original.header("Authorization") == null) {
                builder = builder.header("Authorization", "Bearer $token")
            }

            chain.proceed(builder.build())
        }
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(dynamicEndpointInterceptor)
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    fun provideNexoraBackendApi(
        okHttpClient: OkHttpClient,
        json: Json
    ): NexoraBackendApi {
        val contentType = "application/json".toMediaType()
        val baseUrl = BuildConfig.NEXORA_BACKEND_BASE_URL.let {
            if (it.endsWith("/")) it else "$it/"
        }
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(NexoraBackendApi::class.java)
    }

    @Provides
    @Singleton
    fun providePublicIpLookupApi(
        okHttpClient: OkHttpClient,
        json: Json
    ): PublicIpLookupApi {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl("https://ipwho.is/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(PublicIpLookupApi::class.java)
    }

    @Provides
    @Singleton
    fun provideNetworkDiagnosticsEngine(
        @ApplicationContext context: Context
    ): NetworkDiagnosticsEngine = NetworkDiagnosticsEngine(context)
}
