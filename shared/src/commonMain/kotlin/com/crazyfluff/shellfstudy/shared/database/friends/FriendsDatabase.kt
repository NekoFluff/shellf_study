package com.crazyfluff.shellfstudy.shared.database.friends

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "friend_stats")
data class FriendStatsEntity(
    @PrimaryKey val friendId: String,
    val username: String,
    val level: Int,
    val burnedCount: Int,
    val reviewAccuracy: Float,
    val avgDaysPerLevel: Float,
    val daysSinceStart: Int,
    val levelTimelineJson: String,
    val fetchedAtMillis: Long
)

@Dao
interface FriendStatsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FriendStatsEntity)

    @Query("SELECT * FROM friend_stats WHERE friendId = :id")
    suspend fun getById(id: String): FriendStatsEntity?

    @Query("SELECT * FROM friend_stats")
    fun observeAll(): Flow<List<FriendStatsEntity>>

    @Query("DELETE FROM friend_stats WHERE friendId = :id")
    suspend fun deleteById(id: String)
}

@Database(entities = [FriendStatsEntity::class], version = 1, exportSchema = true)
@ConstructedBy(FriendsDatabaseConstructor::class)
abstract class FriendsDatabase : RoomDatabase() {
    abstract fun friendStatsDao(): FriendStatsDao
}

@Suppress("KotlinNoActualForExpect")
expect object FriendsDatabaseConstructor : RoomDatabaseConstructor<FriendsDatabase> {
    override fun initialize(): FriendsDatabase
}

internal const val FRIENDS_DATABASE_FILE_NAME = "friends_cache.db"

fun buildFriendsDatabase(builder: RoomDatabase.Builder<FriendsDatabase>): FriendsDatabase =
    builder
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.Default)
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()
