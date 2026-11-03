package io.github.aedev.flow.data.local

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/** Room's in-memory database over the bundled SQLite the app ships, as upstream's instrumented tests build one. */
internal fun inMemoryAppDatabase(): AppDatabase =
    Room
        .inMemoryDatabaseBuilder<AppDatabase>()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
