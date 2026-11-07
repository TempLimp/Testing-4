package io.github.aedev.flow.utils

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.UShortVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.cstr
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.posix.RTLD_NOW
import platform.posix.dlopen
import platform.posix.dlsym

// The system's ICU (libicucore), the engine Android's Character.UnicodeScript and java.text.BreakIterator run on.
// Its headers are not in the iOS SDK, so its C API is looked up at run time, as ICU's headers declare it (Apple
// builds it unrenamed); a cinterop would also stop the iOS klibs compiling on the Windows dev machine.

private const val UBRK_WORD = 1
private const val UBRK_DONE = -1

@OptIn(ExperimentalForeignApi::class)
private object Icu {
    private val library: COpaquePointer =
        checkNotNull(dlopen("/usr/lib/libicucore.A.dylib", RTLD_NOW)) { "libicucore is missing" }

    private fun symbol(name: String): COpaquePointer = checkNotNull(dlsym(library, name)) { "libicucore has no $name" }

    // UScriptCode uscript_getScript(UChar32 codepoint, UErrorCode *err)
    val getScript = symbol("uscript_getScript").reinterpret<CFunction<(Int, CPointer<IntVar>?) -> Int>>()

    // UBreakIterator *ubrk_open(UBreakIteratorType type, const char *locale, const UChar *text, int32_t textLength, UErrorCode *status)
    val open =
        symbol("ubrk_open").reinterpret<CFunction<(Int, CPointer<ByteVar>?, CPointer<UShortVar>?, Int, CPointer<IntVar>?) -> COpaquePointer?>>()

    // int32_t ubrk_first(UBreakIterator *bi), ubrk_next(UBreakIterator *bi); void ubrk_close(UBreakIterator *bi)
    val first = symbol("ubrk_first").reinterpret<CFunction<(COpaquePointer?) -> Int>>()
    val next = symbol("ubrk_next").reinterpret<CFunction<(COpaquePointer?) -> Int>>()
    val close = symbol("ubrk_close").reinterpret<CFunction<(COpaquePointer?) -> Unit>>()
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun icuScriptCode(codePoint: Int): Int =
    memScoped {
        val status = alloc<IntVar>()
        val code = Icu.getScript(codePoint, status.ptr)
        // U_FAILURE: an error code above zero.
        if (status.value > 0) -1 else code
    }

@OptIn(ExperimentalForeignApi::class)
internal actual fun icuWordBreaks(text: String): List<String> {
    if (text.isEmpty()) return emptyList()
    val chars = text.toCharArray()
    return chars.usePinned { pinned ->
        memScoped {
            val status = alloc<IntVar>()
            val breaks = Icu.open(UBRK_WORD, "".cstr.ptr, pinned.addressOf(0).reinterpret(), chars.size, status.ptr)
            check(breaks != null && status.value <= 0) { "ubrk_open failed: ${status.value}" }
            try {
                val pieces = ArrayList<String>()
                var start = Icu.first(breaks)
                var end = Icu.next(breaks)
                while (end != UBRK_DONE) {
                    pieces += text.substring(start, end)
                    start = end
                    end = Icu.next(breaks)
                }
                pieces
            } finally {
                Icu.close(breaks)
            }
        }
    }
}
