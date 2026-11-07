package io.github.aedev.flow.utils

/** [bytes] gzipped, as `java.util.zip.GZIPOutputStream` writes them (zlib on iOS). */
expect fun gzip(bytes: ByteArray): ByteArray
