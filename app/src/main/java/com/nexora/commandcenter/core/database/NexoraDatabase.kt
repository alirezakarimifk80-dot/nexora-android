package com.nexora.commandcenter.core.database

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import javax.inject.Singleton

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val draftText: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("conversationId")]
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    val role: String, // "user" or "assistant"
    val content: String,
    val attachmentUri: String? = null,
    val mode: String = "chat", // "chat", "summarize", "vision"
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "smart_memories")
data class SmartMemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val factContent: String,
    val category: String = "Preference",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "queued_ai_requests")
data class QueuedAiRequestEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    val prompt: String,
    val mode: String = "chat",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    val tag: String = "General",
    val isPinned: Boolean = false,
    val colorHex: String = "#0E172A",
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val category: String = "Deep Work",
    val priority: Int = 2, // 1 = Low, 2 = Medium, 3 = High
    val isCompleted: Boolean = false,
    val dueDateEpochDay: Long,
    val reminderEpochMillis: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "tool_usage")
data class ToolUsageEntity(
    @PrimaryKey val toolId: String,
    val toolName: String,
    val usageCount: Int = 1,
    val lastUsedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "activity_logs")
data class ActivityLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val module: String, // "AI", "TOOLS", "TASKS", "NETWORK", "SYSTEM"
    val actionTitle: String,
    val detail: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface ChatDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun observeConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE title LIKE '%' || :query || '%' ORDER BY updatedAt DESC")
    fun searchConversations(query: String): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    suspend fun getAllConversationsSnapshot(): List<ConversationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: ConversationEntity): Long

    @Query("UPDATE conversations SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateConversationMeta(id: Long, title: String, updatedAt: Long)

    @Query("UPDATE conversations SET draftText = :draftText WHERE id = :id")
    suspend fun updateConversationDraft(id: Long, draftText: String)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversation(id: Long)

    @Query("DELETE FROM conversations")
    suspend fun clearAllConversations()

    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun observeMessages(conversationId: Long): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    suspend fun getMessagesSnapshot(conversationId: Long): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    suspend fun getAllMessagesSnapshot(): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Query("UPDATE chat_messages SET content = :content WHERE id = :messageId")
    suspend fun updateMessageContent(messageId: Long, content: String)

    @Query("DELETE FROM chat_messages WHERE id = :messageId")
    suspend fun deleteMessageById(messageId: Long)
}

@Dao
interface SmartMemoryDao {
    @Query("SELECT * FROM smart_memories ORDER BY createdAt DESC")
    fun observeMemories(): Flow<List<SmartMemoryEntity>>

    @Query("SELECT * FROM smart_memories ORDER BY createdAt DESC")
    suspend fun getMemoriesSnapshot(): List<SmartMemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: SmartMemoryEntity): Long

    @Query("DELETE FROM smart_memories WHERE id = :id")
    suspend fun deleteMemoryById(id: Long)

    @Query("DELETE FROM smart_memories")
    suspend fun clearAllMemories()
}

@Dao
interface QueuedAiRequestDao {
    @Query("SELECT * FROM queued_ai_requests ORDER BY createdAt ASC")
    fun observeQueuedRequests(): Flow<List<QueuedAiRequestEntity>>

    @Query("SELECT * FROM queued_ai_requests ORDER BY createdAt ASC")
    suspend fun getQueuedRequestsSnapshot(): List<QueuedAiRequestEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueuedRequest(request: QueuedAiRequestEntity): Long

    @Query("DELETE FROM queued_ai_requests WHERE id = :id")
    suspend fun deleteQueuedRequest(id: Long)

    @Query("DELETE FROM queued_ai_requests")
    suspend fun clearQueuedRequests()
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY isPinned DESC, updatedAt DESC")
    fun observeNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    suspend fun getAllNotesSnapshot(): List<NoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNote(note: NoteEntity): Long

    @Delete
    suspend fun deleteNote(note: NoteEntity)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY isCompleted ASC, priority DESC, dueDateEpochDay ASC")
    fun observeTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks ORDER BY createdAt DESC")
    suspend fun getAllTasksSnapshot(): List<TaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTask(task: TaskEntity): Long

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Delete
    suspend fun deleteTask(task: TaskEntity)
}

@Dao
interface ToolUsageDao {
    @Query("SELECT * FROM tool_usage ORDER BY usageCount DESC, lastUsedAt DESC")
    fun observeRecentTools(): Flow<List<ToolUsageEntity>>

    @Query("SELECT * FROM tool_usage WHERE toolId = :toolId LIMIT 1")
    suspend fun getToolUsage(toolId: String): ToolUsageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertToolUsage(entity: ToolUsageEntity)
}

@Dao
interface ActivityLogDao {
    @Query("SELECT * FROM activity_logs ORDER BY timestamp DESC LIMIT 30")
    fun observeRecentActivities(): Flow<List<ActivityLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivity(log: ActivityLogEntity): Long

    @Query("DELETE FROM activity_logs")
    suspend fun clearActivities()
}

@Database(
    entities = [
        ConversationEntity::class,
        ChatMessageEntity::class,
        SmartMemoryEntity::class,
        QueuedAiRequestEntity::class,
        NoteEntity::class,
        TaskEntity::class,
        ToolUsageEntity::class,
        ActivityLogEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class NexoraDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun smartMemoryDao(): SmartMemoryDao
    abstract fun queuedAiRequestDao(): QueuedAiRequestDao
    abstract fun noteDao(): NoteDao
    abstract fun taskDao(): TaskDao
    abstract fun toolUsageDao(): ToolUsageDao
    abstract fun activityLogDao(): ActivityLogDao
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NexoraDatabase {
        return Room.databaseBuilder(
            context,
            NexoraDatabase::class.java,
            "nexora_command_center.db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideChatDao(db: NexoraDatabase): ChatDao = db.chatDao()

    @Provides
    fun provideSmartMemoryDao(db: NexoraDatabase): SmartMemoryDao = db.smartMemoryDao()

    @Provides
    fun provideQueuedAiRequestDao(db: NexoraDatabase): QueuedAiRequestDao = db.queuedAiRequestDao()

    @Provides
    fun provideNoteDao(db: NexoraDatabase): NoteDao = db.noteDao()

    @Provides
    fun provideTaskDao(db: NexoraDatabase): TaskDao = db.taskDao()

    @Provides
    fun provideToolUsageDao(db: NexoraDatabase): ToolUsageDao = db.toolUsageDao()

    @Provides
    fun provideActivityLogDao(db: NexoraDatabase): ActivityLogDao = db.activityLogDao()
}
