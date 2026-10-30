package io.github.aedev.flow.utils.protobuf

import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlinx.io.write

class ProtobufWriter {
    private val buffer = Buffer()

    fun toByteArray(): ByteArray = buffer.copy().readByteArray()

    fun writeVarint(fieldNumber: Int, value: Long) {
        writeTag(fieldNumber, WIRE_VARINT)
        writeRawVarint(value)
    }

    fun writeInt32(fieldNumber: Int, value: Int) {
        writeVarint(fieldNumber, value.toLong())
    }

    fun writeInt64(fieldNumber: Int, value: Long) {
        writeVarint(fieldNumber, value)
    }

    fun writeBool(fieldNumber: Int, value: Boolean) {
        writeVarint(fieldNumber, if (value) 1L else 0L)
    }

    fun writeBytes(fieldNumber: Int, value: ByteArray) {
        writeTag(fieldNumber, WIRE_LENGTH_DELIMITED)
        writeRawVarint(value.size.toLong())
        buffer.write(value)
    }

    fun writeString(fieldNumber: Int, value: String) {
        writeBytes(fieldNumber, value.encodeToByteArray())
    }

    fun writeMessage(fieldNumber: Int, builder: ProtobufWriter.() -> Unit) {
        val nested = ProtobufWriter()
        nested.builder()
        writeBytes(fieldNumber, nested.toByteArray())
    }

    fun writeFixed32(fieldNumber: Int, value: Int) {
        writeTag(fieldNumber, WIRE_FIXED32)
        buffer.writeByte((value and 0xFF).toByte())
        buffer.writeByte(((value shr 8) and 0xFF).toByte())
        buffer.writeByte(((value shr 16) and 0xFF).toByte())
        buffer.writeByte(((value shr 24) and 0xFF).toByte())
    }

    fun writeFixed64(fieldNumber: Int, value: Long) {
        writeTag(fieldNumber, WIRE_FIXED64)
        for (i in 0 until 8) {
            buffer.writeByte(((value shr (i * 8)) and 0xFF).toByte())
        }
    }

    fun writeFloat(fieldNumber: Int, value: Float) {
        writeFixed32(fieldNumber, value.toBits())
    }

    fun writeRawBytes(data: ByteArray) {
        buffer.write(data)
    }

    private fun writeTag(fieldNumber: Int, wireType: Int) {
        writeRawVarint(((fieldNumber shl 3) or wireType).toLong())
    }

    private fun writeRawVarint(value: Long) {
        var v = value
        while (v and 0x7FL.inv() != 0L) {
            buffer.writeByte(((v.toInt() and 0x7F) or 0x80).toByte())
            v = v ushr 7
        }
        buffer.writeByte((v.toInt() and 0x7F).toByte())
    }

    companion object {
        const val WIRE_VARINT = 0
        const val WIRE_FIXED64 = 1
        const val WIRE_LENGTH_DELIMITED = 2
        const val WIRE_FIXED32 = 5

        fun encode(builder: ProtobufWriter.() -> Unit): ByteArray {
            val writer = ProtobufWriter()
            writer.builder()
            return writer.toByteArray()
        }
    }
}
