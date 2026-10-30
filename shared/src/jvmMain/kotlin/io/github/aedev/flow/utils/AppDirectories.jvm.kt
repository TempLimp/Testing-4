package io.github.aedev.flow.utils

import kotlinx.io.files.Path

actual fun appFilesDir(): Path = Path(System.getProperty("java.io.tmpdir"), "flow-files")
