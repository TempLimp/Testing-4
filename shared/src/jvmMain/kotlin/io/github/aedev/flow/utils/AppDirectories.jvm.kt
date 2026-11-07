package io.github.aedev.flow.utils

import kotlinx.io.files.Path

actual fun appFilesDir(): Path = Path(System.getProperty("java.io.tmpdir"), "flow-files")

actual fun appCacheDir(): Path = Path(System.getProperty("java.io.tmpdir"), "flow-cache")

actual fun appNoBackupDir(): Path = Path(System.getProperty("java.io.tmpdir"), "flow-no-backup")
