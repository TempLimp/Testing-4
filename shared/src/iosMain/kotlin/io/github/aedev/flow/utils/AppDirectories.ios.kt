package io.github.aedev.flow.utils

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

actual fun appFilesDir(): Path =
    Path(NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true).first() as String)

actual fun appNoBackupDir(): Path = noBackupDir

@OptIn(ExperimentalForeignApi::class)
private val noBackupDir: Path by lazy {
    val path = Path(appFilesDir(), "no_backup")
    SystemFileSystem.createDirectories(path)
    NSURL.fileURLWithPath(path.toString(), isDirectory = true).setResourceValue(true, forKey = NSURLIsExcludedFromBackupKey, error = null)
    path
}

actual fun appCacheDir(): Path =
    Path(NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true).first() as String)
