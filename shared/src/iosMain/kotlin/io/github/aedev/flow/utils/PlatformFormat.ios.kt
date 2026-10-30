package io.github.aedev.flow.utils

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.Foundation.NSDate
import platform.Foundation.NSDateComponents
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterMediumStyle
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSMakeRange
import platform.Foundation.NSRelativeDateTimeFormatter
import platform.Foundation.NSRelativeDateTimeFormatterStyle
import platform.Foundation.NSRelativeDateTimeFormatterStyleNamed
import platform.Foundation.NSRelativeDateTimeFormatterStyleNumeric
import platform.Foundation.NSRelativeDateTimeFormatterUnitsStyleFull
import platform.Foundation.NSTimeZone
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.localTimeZone
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.timeZoneWithName
import kotlin.math.roundToLong

// NSRelativeDateTimeFormatter and NSDateFormatter are Foundation's front ends to ICU, the library
// android.icu repackages, so their output follows the same CLDR data.

internal actual fun formatRelativeTime(
    value: Long,
    unit: RelativeUnit,
    future: Boolean,
    locale: Locale,
): String {
    val signed = if (future) value else -value
    val components =
        NSDateComponents().apply {
            when (unit) {
                RelativeUnit.SECONDS -> second = signed
                RelativeUnit.MINUTES -> minute = signed
                RelativeUnit.HOURS -> hour = signed
                RelativeUnit.DAYS -> day = signed
                RelativeUnit.WEEKS -> weekOfMonth = signed
                RelativeUnit.MONTHS -> month = signed
                RelativeUnit.YEARS -> year = signed
            }
        }
    return relativeFormatter(locale, NSRelativeDateTimeFormatterStyleNumeric)
        .localizedStringFromDateComponents(components)
}

internal actual fun formatRelativeNow(locale: Locale): String =
    relativeFormatter(locale, NSRelativeDateTimeFormatterStyleNamed)
        .localizedStringFromTimeInterval(0.0)

private fun relativeFormatter(
    locale: Locale,
    style: NSRelativeDateTimeFormatterStyle,
) = NSRelativeDateTimeFormatter().apply {
    this.locale = locale.ns
    unitsStyle = NSRelativeDateTimeFormatterUnitsStyleFull
    dateTimeStyle = style
}

internal actual fun bestDateTimePattern(
    locale: Locale,
    skeleton: String,
): String = NSDateFormatter.dateFormatFromTemplate(skeleton, 0u, locale.ns) ?: skeleton

internal actual fun formatDatePattern(
    epochMs: Long,
    pattern: String,
    locale: Locale,
): String =
    NSDateFormatter()
        .apply {
            dateFormat = pattern
            this.locale = locale.ns
            timeZone = NSTimeZone.localTimeZone
        }.stringFromDate(epochMs.toNSDate())

internal actual fun formatMediumDate(
    epochMs: Long,
    locale: Locale,
): String =
    NSDateFormatter()
        .apply {
            dateStyle = NSDateFormatterMediumStyle
            timeStyle = NSDateFormatterNoStyle
            this.locale = locale.ns
            timeZone = NSTimeZone.localTimeZone
        }.stringFromDate(epochMs.toNSDate())

@OptIn(ExperimentalForeignApi::class)
internal actual fun parseDatePattern(
    text: String,
    pattern: String,
    locale: Locale,
    utc: Boolean,
): Long? {
    val formatter =
        NSDateFormatter().apply {
            dateFormat = pattern
            this.locale = locale.ns
            timeZone = if (utc) NSTimeZone.timeZoneWithName("UTC")!! else NSTimeZone.localTimeZone
            lenient = false
        }
    // SimpleDateFormat.parse(String) reads from the start and ignores what follows; the range form does the same.
    return memScoped {
        val parsed = alloc<ObjCObjectVar<Any?>>()
        val range = alloc<platform.Foundation.NSRange>()
        range.location = 0u
        range.length = text.length.toULong()
        val ok = formatter.getObjectValue(parsed.ptr, forString = text, range = range.ptr, error = null)
        (parsed.value as? NSDate)?.takeIf { ok }?.let { (it.timeIntervalSince1970 * 1000).roundToLong() }
    }
}

private fun Long.toNSDate(): NSDate = NSDate.dateWithTimeIntervalSince1970(this / 1000.0)
