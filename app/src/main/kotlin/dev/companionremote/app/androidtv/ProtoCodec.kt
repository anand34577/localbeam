package dev.companionremote.app.androidtv

import java.io.ByteArrayOutputStream

/** Minimal protobuf wire codec for the Android TV Remote v2 messages. */
internal class ProtoWriter {
    private val out = ByteArrayOutputStream()

    fun varint(field: Int, value: Long) {
        rawVarint((field shl 3).toLong())
        rawVarint(value)
    }

    fun bytes(field: Int, value: ByteArray) {
        rawVarint(((field shl 3) or 2).toLong())
        rawVarint(value.size.toLong())
        out.write(value)
    }

    fun string(field: Int, value: String) = bytes(field, value.toByteArray(Charsets.UTF_8))

    fun message(field: Int, value: ByteArray) = bytes(field, value)

    fun toByteArray(): ByteArray = out.toByteArray()

    private fun rawVarint(input: Long) {
        var value = input
        while (value and 0x7f.inv().toLong() != 0L) {
            out.write(((value.toInt() and 0x7f) or 0x80))
            value = value ushr 7
        }
        out.write(value.toInt() and 0x7f)
    }

    companion object {
        fun frame(message: ByteArray): ByteArray = ProtoWriter().apply {
            rawVarint(message.size.toLong())
            out.write(message)
        }.toByteArray()
    }
}

internal data class ProtoField(val number: Int, val wireType: Int, val value: Any)

internal class ProtoReader(private val data: ByteArray) {
    private var offset = 0

    fun readFields(): List<ProtoField> {
        val result = mutableListOf<ProtoField>()
        while (offset < data.size) {
            val tag = readVarint().toInt()
            val number = tag ushr 3
            val wireType = tag and 7
            val value: Any = when (wireType) {
                0 -> readVarint()
                1 -> readRaw(8)
                2 -> readRaw(readVarint().toInt())
                5 -> readRaw(4)
                else -> throw IllegalArgumentException("Unsupported protobuf wire type $wireType")
            }
            result += ProtoField(number, wireType, value)
        }
        return result
    }

    private fun readVarint(): Long {
        var result = 0L
        var shift = 0
        while (shift < 64) {
            if (offset >= data.size) throw IllegalArgumentException("Truncated protobuf varint")
            val byte = data[offset++].toInt() and 0xff
            result = result or ((byte and 0x7f).toLong() shl shift)
            if (byte and 0x80 == 0) return result
            shift += 7
        }
        throw IllegalArgumentException("Invalid protobuf varint")
    }

    private fun readRaw(length: Int): ByteArray {
        require(length >= 0 && length <= data.size - offset) { "Truncated protobuf field" }
        return data.copyOfRange(offset, offset + length).also { offset += length }
    }
}

internal fun ProtoField.longValue(): Long? = value as? Long
internal fun ProtoField.bytesValue(): ByteArray? = value as? ByteArray
internal fun ProtoField.stringValue(): String? = bytesValue()?.toString(Charsets.UTF_8)

internal object AndroidTvPairingCodec {
    private const val STATUS_OK = 200L

    fun request(serviceName: String, clientName: String): ByteArray {
        val body = ProtoWriter().apply {
            string(1, serviceName)
            string(2, clientName)
        }.toByteArray()
        return outer(10, body)
    }

    fun option(): ByteArray {
        val encoding = ProtoWriter().apply {
            varint(1, 3) // ENCODING_TYPE_HEXADECIMAL
            varint(2, 6)
        }.toByteArray()
        val options = ProtoWriter().apply {
            message(1, encoding)
            varint(3, 1) // ROLE_TYPE_INPUT
        }.toByteArray()
        return outer(20, options)
    }

    fun configuration(): ByteArray {
        val encoding = ProtoWriter().apply {
            varint(1, 3)
            varint(2, 6)
        }.toByteArray()
        val configuration = ProtoWriter().apply {
            message(1, encoding)
            varint(2, 1) // ROLE_TYPE_INPUT
        }.toByteArray()
        return outer(30, configuration)
    }

    fun secret(secret: ByteArray): ByteArray {
        val body = ProtoWriter().apply { bytes(1, secret) }.toByteArray()
        return outer(40, body)
    }

    fun read(message: ByteArray): PairingMessage {
        val fields = ProtoReader(message).readFields()
        val status = fields.firstOrNull { it.number == 2 }?.longValue()?.toInt()
        require(status == STATUS_OK.toInt()) { "Android TV pairing rejected the request (status=$status)" }
        val type = fields.firstOrNull { it.number in setOf(11, 20, 31, 41) }?.number
            ?: throw IllegalArgumentException("Unknown Android TV pairing message")
        return when (type) {
            11 -> PairingMessage.RequestAck
            20 -> PairingMessage.Option
            31 -> PairingMessage.ConfigurationAck
            41 -> PairingMessage.SecretAck
            else -> error("Unknown Android TV pairing message")
        }
    }

    private fun outer(type: Int, payload: ByteArray): ByteArray = ProtoWriter.frame(
        ProtoWriter().apply {
            varint(1, 2) // protocol_version
            varint(2, STATUS_OK)
            message(type, payload)
        }.toByteArray(),
    )
}

internal sealed interface PairingMessage {
    data object RequestAck : PairingMessage
    data object Option : PairingMessage
    data object ConfigurationAck : PairingMessage
    data object SecretAck : PairingMessage
}

internal object AndroidTvRemoteCodec {
    fun configure(activeFeatures: Int = 622): ByteArray {
        val info = ProtoWriter().apply {
            string(1, "LocalBeam")
            string(2, "LocalBeam")
            varint(3, 1)
            string(4, "1")
            string(5, "androidtv-remote")
            string(6, "1.0.0")
        }.toByteArray()
        val configure = ProtoWriter().apply {
            varint(1, activeFeatures.toLong())
            message(2, info)
        }.toByteArray()
        return frame(1, configure)
    }

    fun setActive(activeFeatures: Int = 622): ByteArray = frame(
        2,
        ProtoWriter().apply { varint(1, activeFeatures.toLong()) }.toByteArray(),
    )

    fun pingResponse(value: Long): ByteArray = frame(
        9,
        ProtoWriter().apply { varint(1, value) }.toByteArray(),
    )

    fun key(code: Int, direction: Int = 3): ByteArray = frame(
        10,
        ProtoWriter().apply {
            varint(1, code.toLong())
            varint(2, direction.toLong())
        }.toByteArray(),
    )

    fun text(imeCounter: Int, fieldCounter: Int, value: String): ByteArray {
        // Android TV's RemoteImeObject uses the final character index, not
        // the string length, for both selection endpoints. Sending length
        // here makes the TV reject otherwise valid edits as out of range.
        val cursor = (value.length - 1).coerceAtLeast(0)
        val status = ProtoWriter().apply {
            varint(1, cursor.toLong())
            varint(2, cursor.toLong())
            string(3, value)
        }.toByteArray()
        val edit = ProtoWriter().apply {
            varint(1, 1)
            message(2, status)
        }.toByteArray()
        val batch = ProtoWriter().apply {
            varint(1, imeCounter.toLong())
            varint(2, fieldCounter.toLong())
            message(3, edit)
        }.toByteArray()
        return frame(21, batch)
    }

    fun appLink(link: String): ByteArray = frame(
        90,
        ProtoWriter().apply { string(1, link) }.toByteArray(),
    )

    fun voiceBegin(sessionId: Int): ByteArray = frame(
        30,
        ProtoWriter().apply { varint(1, sessionId.toLong()) }.toByteArray(),
    )

    fun voicePayload(sessionId: Int, samples: ByteArray): ByteArray = frame(
        31,
        ProtoWriter().apply {
            varint(1, sessionId.toLong())
            bytes(2, samples)
        }.toByteArray(),
    )

    fun voiceEnd(sessionId: Int): ByteArray = frame(
        32,
        ProtoWriter().apply { varint(1, sessionId.toLong()) }.toByteArray(),
    )

    fun read(message: ByteArray): RemoteMessage {
        val outer = ProtoReader(message).readFields()
        val field = outer.firstOrNull { it.wireType == 2 }
            ?: return RemoteMessage.Unknown
        val body = field.bytesValue() ?: return RemoteMessage.Unknown
        val nested = ProtoReader(body).readFields()
        return when (field.number) {
            1 -> RemoteMessage.Configure(
                supportedFeatures = nested.firstOrNull { it.number == 1 }?.longValue()?.toInt() ?: 0,
            )
            2 -> RemoteMessage.SetActive
            8 -> RemoteMessage.Ping(nested.firstOrNull { it.number == 1 }?.longValue() ?: 0L)
            20 -> RemoteMessage.ImeKeyInject(
                packageName = readAppPackage(nested),
                status = readTextStatus(nested),
            )
            21 -> RemoteMessage.ImeBatch(
                nested.firstOrNull { it.number == 1 }?.longValue()?.toInt() ?: 0,
                nested.firstOrNull { it.number == 2 }?.longValue()?.toInt() ?: 0,
            )
            22 -> RemoteMessage.ImeShow(readTextStatus(nested))
            40 -> RemoteMessage.Start(nested.firstOrNull { it.number == 1 }?.longValue() == 1L)
            50 -> RemoteMessage.Volume(
                level = nested.firstOrNull { it.number == 7 }?.longValue()?.toInt() ?: 0,
                maximum = nested.firstOrNull { it.number == 6 }?.longValue()?.toInt() ?: 0,
                muted = nested.firstOrNull { it.number == 8 }?.longValue() == 1L,
            )
            3 -> RemoteMessage.Error
            else -> RemoteMessage.Unknown
        }
    }

    private fun readTextStatus(showFields: List<ProtoField>): TextStatus? {
        val statusBytes = showFields.firstOrNull { it.number == 2 }?.bytesValue() ?: return null
        val status = ProtoReader(statusBytes).readFields()
        return TextStatus(
            value = status.firstOrNull { it.number == 2 }?.stringValue().orEmpty(),
            start = status.firstOrNull { it.number == 3 }?.longValue()?.toInt() ?: 0,
            end = status.firstOrNull { it.number == 4 }?.longValue()?.toInt() ?: 0,
        )
    }

    private fun readAppPackage(fields: List<ProtoField>): String? {
        val appInfoBytes = fields.firstOrNull { it.number == 1 }?.bytesValue() ?: return null
        return ProtoReader(appInfoBytes)
            .readFields()
            .firstOrNull { it.number == 12 }
            ?.stringValue()
            ?.takeIf { it.isNotBlank() }
    }

    private fun frame(type: Int, body: ByteArray): ByteArray = ProtoWriter.frame(
        ProtoWriter().apply { message(type, body) }.toByteArray(),
    )
}

internal sealed interface RemoteMessage {
    data class Configure(val supportedFeatures: Int) : RemoteMessage
    data object SetActive : RemoteMessage
    data class Ping(val value: Long) : RemoteMessage
    data class ImeKeyInject(val packageName: String?, val status: TextStatus?) : RemoteMessage
    data class ImeBatch(val imeCounter: Int, val fieldCounter: Int) : RemoteMessage
    data class ImeShow(val status: TextStatus?) : RemoteMessage
    data class Start(val started: Boolean) : RemoteMessage
    data class Volume(val level: Int, val maximum: Int, val muted: Boolean) : RemoteMessage
    data object Error : RemoteMessage
    data object Unknown : RemoteMessage
}

internal data class TextStatus(val value: String, val start: Int, val end: Int)
