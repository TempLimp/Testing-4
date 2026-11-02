@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.aedev.flow.player.sabr.core

import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import kotlinx.io.Buffer
import kotlinx.io.IOException
import kotlinx.io.readByteArray
import platform.posix.RTLD_NOW
import platform.posix.dlopen
import platform.posix.dlsym
import platform.zlib.MAX_WBITS
import platform.zlib.Z_NO_FLUSH
import platform.zlib.Z_OK
import platform.zlib.Z_STREAM_END
import platform.zlib.inflate
import platform.zlib.inflateEnd
import platform.zlib.inflateInit2_
import platform.zlib.z_stream
import platform.zlib.zlibVersion

// zlib reads the gzip member's header and trailer itself when windowBits asks for gzip (16 + MAX_WBITS).
internal actual fun gunzip(data: ByteArray): ByteArray =
    memScoped {
        val stream = alloc<z_stream>()
        if (inflateInit2_(stream.ptr, 16 + MAX_WBITS, zlibVersion(), sizeOf<z_stream>().convert()) != Z_OK) {
            throw IOException("zlib refused to start a gzip inflate")
        }
        try {
            val output = Buffer()
            val chunk = ByteArray(1 shl 16)
            if (data.isEmpty()) throw IOException("Empty gzip member")
            data.usePinned { source ->
                chunk.usePinned { target ->
                    stream.next_in = source.addressOf(0).reinterpret()
                    stream.avail_in = data.size.convert()
                    while (true) {
                        stream.next_out = target.addressOf(0).reinterpret()
                        stream.avail_out = chunk.size.convert()
                        val status = inflate(stream.ptr, Z_NO_FLUSH)
                        val produced = chunk.size - stream.avail_out.toInt()
                        output.write(chunk, 0, produced)
                        when {
                            status == Z_STREAM_END -> break
                            status != Z_OK -> throw IOException("Corrupt gzip SABR media (zlib $status)")
                            produced == 0 && stream.avail_in.toInt() == 0 -> throw IOException("Truncated gzip SABR media")
                        }
                    }
                }
            }
            output.readByteArray()
        } finally {
            inflateEnd(stream.ptr)
        }
    }

private typealias CompressionDecodeBuffer = CFunction<(CPointer<UByteVar>?, ULong, CPointer<UByteVar>?, ULong, COpaquePointer?, Int) -> ULong>

// Apple's libcompression decodes brotli (COMPRESSION_BROTLI, iOS 15+), but Kotlin/Native ships no binding for it, so
// its one-shot decoder is looked up in the system library at runtime.
private val compressionDecodeBuffer: CPointer<CompressionDecodeBuffer>? by lazy {
    dlopen("/usr/lib/libcompression.dylib", RTLD_NOW)?.let { dlsym(it, "compression_decode_buffer") }?.reinterpret()
}

private const val COMPRESSION_BROTLI = 0xB02

internal actual fun unbrotli(data: ByteArray): ByteArray {
    val decode = compressionDecodeBuffer ?: throw IOException("libcompression is unavailable")
    if (data.isEmpty()) throw IOException("Empty brotli SABR media")
    // The one-shot decoder fills at most the buffer it is given, so a full buffer is retried at twice the size.
    var capacity = maxOf(data.size * 4, 1 shl 16)
    while (true) {
        val output = ByteArray(capacity)
        val written =
            data.usePinned { source ->
                output.usePinned { target ->
                    decode(target.addressOf(0).reinterpret(), capacity.convert(), source.addressOf(0).reinterpret(), data.size.convert(), null, COMPRESSION_BROTLI).toInt()
                }
            }
        if (written == 0) throw IOException("Corrupt brotli SABR media")
        if (written < capacity) return output.copyOf(written)
        if (capacity >= 1 shl 28) throw IOException("Brotli SABR media decodes past 256 MiB")
        capacity *= 2
    }
}
