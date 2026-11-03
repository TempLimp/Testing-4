package io.github.aedev.flow.data.local

import androidx.room.AutoMigration
import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.aedev.flow.data.local.dao.CacheDao
import io.github.aedev.flow.data.local.dao.DownloadCollectionDao
import io.github.aedev.flow.data.local.dao.DownloadDao
import io.github.aedev.flow.data.local.dao.HomeFeedCacheDao
import io.github.aedev.flow.data.local.dao.MusicGraphDao
import io.github.aedev.flow.data.local.dao.NoteDao
import io.github.aedev.flow.data.local.dao.NotificationDao
import io.github.aedev.flow.data.local.dao.PlaylistDao
import io.github.aedev.flow.data.local.dao.RecognitionHistoryDao
import io.github.aedev.flow.data.local.dao.SubscriptionGroupDao
import io.github.aedev.flow.data.local.dao.SyncLogDao
import io.github.aedev.flow.data.local.dao.SyncPeerDao
import io.github.aedev.flow.data.local.dao.VideoDao
import io.github.aedev.flow.data.local.dao.WatchHistoryDao
import io.github.aedev.flow.data.local.entity.DownloadCollectionEntity
import io.github.aedev.flow.data.local.entity.DownloadCollectionItemEntity
import io.github.aedev.flow.data.local.entity.DownloadEntity
import io.github.aedev.flow.data.local.entity.DownloadItemEntity
import io.github.aedev.flow.data.local.entity.HomeFeedCacheEntity
import io.github.aedev.flow.data.local.entity.MusicGraphAlbumEntity
import io.github.aedev.flow.data.local.entity.MusicGraphArtistEntity
import io.github.aedev.flow.data.local.entity.MusicGraphEdgeEntity
import io.github.aedev.flow.data.local.entity.MusicGraphPlaylistEntity
import io.github.aedev.flow.data.local.entity.MusicGraphTrackEntity
import io.github.aedev.flow.data.local.entity.MusicHomeCacheEntity
import io.github.aedev.flow.data.local.entity.MusicHomeChipEntity
import io.github.aedev.flow.data.local.entity.NoteEntity
import io.github.aedev.flow.data.local.entity.NotificationEntity
import io.github.aedev.flow.data.local.entity.PlaylistEntity
import io.github.aedev.flow.data.local.entity.PlaylistVideoCrossRef
import io.github.aedev.flow.data.local.entity.RecognitionHistoryEntity
import io.github.aedev.flow.data.local.entity.SubscriptionFeedEntity
import io.github.aedev.flow.data.local.entity.SubscriptionGroupEntity
import io.github.aedev.flow.data.local.entity.SyncLogEntity
import io.github.aedev.flow.data.local.entity.SyncPeerEntity
import io.github.aedev.flow.data.local.entity.VideoEntity
import io.github.aedev.flow.data.local.entity.WatchHistoryEntity
import io.github.aedev.flow.data.local.migrations.MIGRATIONS
import io.github.aedev.flow.data.local.migrations.Migration24To25
import io.github.aedev.flow.data.profile.ProfileFiles
import io.github.aedev.flow.utils.appFilesDir
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlin.concurrent.Volatile

@Database(
    entities = [
        VideoEntity::class,
        PlaylistEntity::class,
        PlaylistVideoCrossRef::class,
        NotificationEntity::class,
        SubscriptionFeedEntity::class,
        MusicHomeCacheEntity::class,
        MusicHomeChipEntity::class,
        DownloadEntity::class,
        DownloadItemEntity::class,
        WatchHistoryEntity::class,
        HomeFeedCacheEntity::class,
        SubscriptionGroupEntity::class,
        RecognitionHistoryEntity::class,
        SyncLogEntity::class,
        SyncPeerEntity::class,
        MusicGraphTrackEntity::class,
        MusicGraphArtistEntity::class,
        MusicGraphAlbumEntity::class,
        MusicGraphPlaylistEntity::class,
        MusicGraphEdgeEntity::class,
        NoteEntity::class,
        DownloadCollectionEntity::class,
        DownloadCollectionItemEntity::class,
    ],
    autoMigrations = [
        AutoMigration(from = 24, to = 25, spec = Migration24To25::class),
        AutoMigration(from = 25, to = 26),
        AutoMigration(from = 26, to = 27),
        AutoMigration(from = 27, to = 28),
        AutoMigration(from = 28, to = 29),
        AutoMigration(from = 29, to = 30),
        AutoMigration(from = 30, to = 31),
        AutoMigration(from = 31, to = 32),
        AutoMigration(from = 32, to = 33),
    ],
    version = 33,
    exportSchema = true,
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun videoDao(): VideoDao

    abstract fun playlistDao(): PlaylistDao

    abstract fun notificationDao(): NotificationDao

    abstract fun noteDao(): NoteDao

    abstract fun cacheDao(): CacheDao

    abstract fun downloadDao(): DownloadDao

    abstract fun downloadCollectionDao(): DownloadCollectionDao

    abstract fun watchHistoryDao(): WatchHistoryDao

    abstract fun homeFeedCacheDao(): HomeFeedCacheDao

    abstract fun subscriptionGroupDao(): SubscriptionGroupDao

    abstract fun recognitionHistoryDao(): RecognitionHistoryDao

    abstract fun syncLogDao(): SyncLogDao

    abstract fun syncPeerDao(): SyncPeerDao

    abstract fun musicGraphDao(): MusicGraphDao

    companion object {
        const val DEFAULT_NAME = "flow_database"

        @Volatile
        private var deviceDatabase: AppDatabase? = null

        @Volatile
        private var profileDatabase: AppDatabase? = null

        private val lock = SynchronizedObject()

        /** Default's file, which also holds the download tables every profile shares. */
        fun device(): AppDatabase =
            deviceDatabase ?: synchronized(lock) {
                deviceDatabase ?: open(DEFAULT_NAME).also { deviceDatabase = it }
            }

        /** The running profile's own file. With Default running it is [device]: Room must never open one file twice. */
        fun profile(): AppDatabase =
            profileDatabase ?: synchronized(lock) {
                profileDatabase ?: ProfileFiles
                    .database(DEFAULT_NAME)
                    .let { name -> if (name == DEFAULT_NAME) device() else open(name) }
                    .also { profileDatabase = it }
            }

        internal fun open(name: String): AppDatabase =
            Room
                .databaseBuilder<AppDatabase>(databasePath(name))
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .addMigrations(*MIGRATIONS)
                .fallbackToDestructiveMigration(false)
                .build()

        /** Android's `getDatabasePath`: a bare name lives in `databases/` beside the app's files, a path stays put. */
        internal fun databasePath(name: String): String {
            if (Path(name).isAbsolute) return name
            val dir = Path(appFilesDir(), "databases")
            SystemFileSystem.createDirectories(dir)
            return Path(dir, name).toString()
        }
    }
}

// Room generates the actual for each target (Kotlin/Native has no reflection to construct the database).
@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}
