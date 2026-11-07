package io.github.aedev.flow.utils

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import platform.zlib.Z_DEFAULT_COMPRESSION
import platform.zlib.Z_DEFAULT_STRATEGY
import platform.zlib.Z_DEFLATED
import platform.zlib.Z_FINISH
import platform.zlib.Z_OK
import platform.zlib.Z_STREAM_END
import platform.zlib.ZLIB_VERSION
import platform.zlib.deflate
import platform.zlib.deflateBound
import platform.zlib.deflateEnd
import platform.zlib.deflateInit2_
import platform.zlib.z_stream

// zlib's deflate with a gzip wrapper (window bits 15 + 16), at GZIPOutputStream's default level.
@OptIn(ExperimentalForeignApi::class)
actual fun gzip(bytes: ByteArray): ByteArray =
    memScoped {
        val stream = alloc<z_stream>()
        val init = deflateInit2_(stream.ptr, Z_DEFAULT_COMPRESSION, Z_DEFLATED, 15 + 16, 8, Z_DEFAULT_STRATEGY, ZLIB_VERSION, sizeOf<z_stream>().convert())
        check(init == Z_OK) { "deflateInit2 failed: $init" }
        try {
            // The gzip header and trailer add 18 bytes to deflate's own bound.
            val out = ByteArray((deflateBound(stream.ptr, bytes.size.convert()) + 18u).toInt())
            val input = if (bytes.isEmpty()) ByteArray(1) else bytes
            input.usePinned { src ->
                out.usePinned { dst ->
                    stream.next_in = src.addressOf(0).reinterpret<UByteVar>()
                    stream.avail_in = bytes.size.convert()
                    stream.next_out = dst.addressOf(0).reinterpret<UByteVar>()
                    stream.avail_out = out.size.convert()
                    val result = deflate(stream.ptr, Z_FINISH)
                    check(result == Z_STREAM_END) { "deflate failed: $result" }
                }
            }
            out.copyOf(stream.total_out.toInt())
        } finally {
            deflateEnd(stream.ptr)
        }
    }
