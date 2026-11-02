package io.github.aedev.flow.player.sabr.core

import org.brotli.dec.BrotliInputStream
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream

internal actual fun gunzip(data: ByteArray): ByteArray = GZIPInputStream(ByteArrayInputStream(data)).use { it.readBytes() }

internal actual fun unbrotli(data: ByteArray): ByteArray = BrotliInputStream(ByteArrayInputStream(data)).use { it.readBytes() }
