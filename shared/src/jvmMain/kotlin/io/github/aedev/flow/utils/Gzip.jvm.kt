package io.github.aedev.flow.utils

import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

actual fun gzip(bytes: ByteArray): ByteArray = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(bytes) } }.toByteArray()
