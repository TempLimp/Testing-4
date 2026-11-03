@file:OptIn(ExperimentalXmlUtilApi::class)

package org.xmlpull.v1

import nl.adaptivity.xmlutil.EventType
import nl.adaptivity.xmlutil.ExperimentalXmlUtilApi
import nl.adaptivity.xmlutil.core.KtXmlReader

/**
 * Android's `org.xmlpull.v1.XmlPullParser`, the part of it upstream calls, over xmlutil's KtXmlReader (a Kotlin
 * port of kXML2, the parser behind Android's own). Same rules as Android's `next()`: text, CDATA, entity
 * references and whitespace between tags come back as one TEXT event; comments, processing instructions and the
 * doctype are skipped. `setInput` takes the document as a String where Android takes a Reader.
 */
class XmlPullParser internal constructor(
    private val namespaceAware: Boolean,
) {
    private var reader: KtXmlReader? = null
    private var pending: EventType? = null

    var eventType: Int = START_DOCUMENT
        private set

    /** The element's name on a start or end tag (local name when namespace-aware, `prefix:local` otherwise). */
    var name: String? = null
        private set

    var text: String? = null
        private set

    var depth: Int = 0
        private set

    private var attributes: List<Attribute> = emptyList()

    private class Attribute(
        val namespace: String,
        val name: String,
        val value: String,
    )

    fun setInput(input: String) {
        reader = KtXmlReader(input, relaxed = false, expandEntities = true)
        pending = null
        eventType = START_DOCUMENT
        name = null
        text = null
        depth = 0
        attributes = emptyList()
    }

    fun next(): Int {
        val source = reader ?: throw XmlPullParserException("setInput() was not called")
        if (eventType == END_DOCUMENT) throw XmlPullParserException("END_DOCUMENT already reached")
        if (eventType == END_TAG) depth--
        name = null
        text = null
        attributes = emptyList()
        val builder = StringBuilder()
        var sawText = false
        while (true) {
            val event = pending ?: (if (source.hasNext()) source.next() else EventType.END_DOCUMENT)
            pending = null
            when (event) {
                EventType.TEXT, EventType.CDSECT, EventType.ENTITY_REF, EventType.IGNORABLE_WHITESPACE -> {
                    builder.append(source.text)
                    sawText = true
                }

                EventType.START_ELEMENT, EventType.END_ELEMENT, EventType.END_DOCUMENT -> {
                    if (sawText) {
                        pending = event
                        text = builder.toString()
                        eventType = TEXT
                        return eventType
                    }
                    return enter(source, event)
                }

                else -> Unit
            }
        }
    }

    private fun enter(
        source: KtXmlReader,
        event: EventType,
    ): Int {
        eventType =
            when (event) {
                EventType.START_ELEMENT -> START_TAG
                EventType.END_ELEMENT -> END_TAG
                else -> END_DOCUMENT
            }
        if (eventType == END_DOCUMENT) return eventType
        name = qualified(source.prefix, source.localName)
        if (eventType == START_TAG) {
            depth++
            attributes =
                List(source.attributeCount) { i ->
                    Attribute(
                        namespace = source.getAttributeNamespace(i),
                        name = qualified(source.getAttributePrefix(i), source.getAttributeLocalName(i)),
                        value = source.getAttributeValue(i),
                    )
                }
        }
        return eventType
    }

    private fun qualified(
        prefix: String,
        localName: String,
    ): String = if (namespaceAware || prefix.isEmpty()) localName else "$prefix:$localName"

    /** As kXML2: the last attribute of that name, in any namespace when [namespace] is null. */
    fun getAttributeValue(
        namespace: String?,
        name: String,
    ): String? = attributes.lastOrNull { it.name == name && (namespace == null || it.namespace == namespace) }?.value

    val attributeCount: Int get() = if (eventType == START_TAG) attributes.size else -1

    /** On a start tag, the element's text up to its end tag; an element with children is an error, as on Android. */
    fun nextText(): String {
        if (eventType != START_TAG) throw XmlPullParserException("precondition: START_TAG")
        var event = next()
        val result =
            if (event == TEXT) {
                val value = text.orEmpty()
                event = next()
                value
            } else {
                ""
            }
        if (event != END_TAG) throw XmlPullParserException("END_TAG expected")
        return result
    }

    companion object {
        const val START_DOCUMENT = 0
        const val END_DOCUMENT = 1
        const val START_TAG = 2
        const val END_TAG = 3
        const val TEXT = 4
    }
}

class XmlPullParserFactory private constructor() {
    var isNamespaceAware: Boolean = false

    fun newPullParser(): XmlPullParser = XmlPullParser(isNamespaceAware)

    companion object {
        fun newInstance(): XmlPullParserFactory = XmlPullParserFactory()
    }
}

class XmlPullParserException(
    message: String,
) : Exception(message)
