package ee.schimke.rcsp

/** One frame of Shokz's observed vendor transport for OpenSwim Pro. */
data class ShokzOpenSwimEnvelope(
  /** `1` for captured app requests and `0x101` for headset responses. */
  val type: UInt,
  val payload: ByteArray,
  internal val checksumSeed: Int,
)

/**
 * Codec for the OpenSwim Pro `A5 5A` RFCOMM envelope.
 *
 * This is deliberately distinct from [RcspCodec]. Captures on firmware R_24_202520250815 show a
 * four-byte type, and a payload CRC-16/Modbus. Captured media-control requests use seed `0x86F7`;
 * Local Audio state queries use `0x7649`. Their response classes currently use `0x7649` and
 * `0xA492`, respectively.
 */
object ShokzOpenSwimCodec {
  private const val requestType = 1u
  private const val responseType = 0x101u
  private const val requestChecksumSeed = 0x86F7
  private const val equalizerRequestChecksumSeed = 0x742D
  private const val mediaResponseChecksumSeed = 0x7649
  private const val localAudioStateResponseChecksumSeed = 0xA492
  private const val headerSize = 12

  fun isRequest(envelope: ShokzOpenSwimEnvelope): Boolean = envelope.type == requestType

  fun isResponse(envelope: ShokzOpenSwimEnvelope): Boolean = envelope.type == responseType

  fun encode(envelope: ShokzOpenSwimEnvelope): ByteArray {
    require(envelope.payload.size <= UShort.MAX_VALUE.toInt() - 4) {
      "OpenSwim payload is too large"
    }
    val payloadLength = envelope.payload.size
    return ByteArray(headerSize + payloadLength).also { frame ->
      frame[0] = 0xA5.toByte()
      frame[1] = 0x5A
      frame.writeUShort(2, payloadLength + 4)
      frame.writeUInt(4, envelope.type)
      frame.writeUShort(8, payloadLength)
      frame.writeUShort(10, crc16Modbus(envelope.payload, envelope.checksumSeed))
      envelope.payload.copyInto(frame, destinationOffset = headerSize)
    }
  }

  fun decode(frame: ByteArray): ShokzOpenSwimEnvelope {
    require(frame.size >= headerSize) { "OpenSwim frame is too short" }
    require(frame[0] == 0xA5.toByte() && frame[1] == 0x5A.toByte()) { "Missing OpenSwim frame tag" }
    val frameLength = frame.readUShort(2)
    val payloadLength = frame.readUShort(8)
    require(frameLength == payloadLength + 4) { "OpenSwim frame length does not match payload" }
    require(frame.size == headerSize + payloadLength) { "OpenSwim frame is truncated" }
    val payload = frame.copyOfRange(headerSize, frame.size)
    val type = frame.readUInt(4)
    val checksumSeed = seedFor(type, payload, frame.readUShort(10))
    require(checksumSeed != null) { "OpenSwim payload checksum failed" }
    return ShokzOpenSwimEnvelope(type = type, payload = payload, checksumSeed = checksumSeed)
  }

  private fun seedFor(type: UInt, payload: ByteArray, checksum: Int): Int? =
    when (type) {
      requestType ->
        listOf(requestChecksumSeed, equalizerRequestChecksumSeed, mediaResponseChecksumSeed)
          .firstOrNull { crc16Modbus(payload, it) == checksum }
      responseType ->
        listOf(
            mediaResponseChecksumSeed,
            localAudioStateResponseChecksumSeed,
            equalizerRequestChecksumSeed,
          )
          .firstOrNull { crc16Modbus(payload, it) == checksum }
      else -> null
    }

  private fun crc16Modbus(bytes: ByteArray, seed: Int): Int {
    var crc = seed
    for (byte in bytes) {
      crc = crc xor byte.toInt().and(0xFF)
      repeat(8) { crc = if (crc and 1 != 0) (crc ushr 1) xor 0xA001 else crc ushr 1 }
    }
    return crc and 0xFFFF
  }

  private fun ByteArray.readUShort(offset: Int): Int =
    this[offset].toInt().and(0xFF) or (this[offset + 1].toInt().and(0xFF) shl 8)

  private fun ByteArray.readUInt(offset: Int): UInt =
    readUShort(offset).toUInt() or (readUShort(offset + 2).toUInt() shl 16)

  private fun ByteArray.writeUShort(offset: Int, value: Int) {
    this[offset] = value.toByte()
    this[offset + 1] = (value ushr 8).toByte()
  }

  private fun ByteArray.writeUInt(offset: Int, value: UInt) {
    writeUShort(offset, value.toInt())
    writeUShort(offset + 2, (value shr 16).toInt())
  }
}

/** Captured Local Audio media requests, verified against the Shokz app's RFCOMM traffic. */
object ShokzOpenSwimCommandEncoder {
  private const val requestType = 1u

  fun encode(command: RcspCommand): ShokzOpenSwimEnvelope? =
    when (command) {
      RcspCommand.QueryMediaState -> queryRequest(query = 0x40)
      RcspCommand.Play -> mediaRequest(action = 3)
      RcspCommand.Pause -> mediaRequest(action = 4)
      RcspCommand.PreviousTrack -> mediaRequest(action = 7)
      RcspCommand.NextTrack -> mediaRequest(action = 8)
      is RcspCommand.SetVolume -> valueRequest(command = 0x0A, value = command.level.toRawVolume())
      is RcspCommand.SelectWorkingMode ->
        valueRequest(command = 0x25, value = command.mode.toOpenSwimModeValue())
      is RcspCommand.SelectEqualizerPreset ->
        equalizerRequest(command.preset.toOpenSwimEqualizerValue())
      is RcspCommand.SelectPlaybackOrder ->
        valueRequest(command = 0x26, value = command.order.toOpenSwimOrderValue())
      else -> null
    }

  /**
   * Observed Local Audio state query. The headset replies with a `0x8040` payload whose final
   * tagged values change with local playback state. Keep that payload internal until every field
   * has been independently mapped.
   */
  private fun queryRequest(query: Int): ShokzOpenSwimEnvelope {
    val values = intArrayOf(1, 36, 2, 4, 2, 3, 4, query, 4, 4, 0)
    return ShokzOpenSwimEnvelope(
      type = requestType,
      payload = values.toLittleEndianPayload(),
      checksumSeed = 0x7649,
    )
  }

  private fun mediaRequest(action: Int): ShokzOpenSwimEnvelope {
    val values = intArrayOf(1, 32, 2, 4, 1, 3, 4, action, 4, 0)
    return ShokzOpenSwimEnvelope(
      type = requestType,
      payload = values.toLittleEndianPayload(),
      checksumSeed = 0x86F7,
    )
  }

  /** Captured setters for working mode, Local Audio volume, and playback order. */
  private fun valueRequest(command: Int, value: Int): ShokzOpenSwimEnvelope {
    val values = intArrayOf(1, 36, 2, 4, 1, 3, 4, command, 4, 4, value)
    return ShokzOpenSwimEnvelope(
      type = requestType,
      payload = values.toLittleEndianPayload(),
      checksumSeed = 0x7649,
    )
  }

  /** Captured EQ setter: unlike the simple tagged setters it has an eight-byte value payload. */
  private fun equalizerRequest(value: Int): ShokzOpenSwimEnvelope {
    val values = intArrayOf(1, 40, 2, 4, 1, 3, 4, 0x0E, 4, 8, value, 0)
    return ShokzOpenSwimEnvelope(
      type = requestType,
      payload = values.toLittleEndianPayload(),
      checksumSeed = 0x742D,
    )
  }

  private fun UByte.toRawVolume(): Int = (toInt() * 16 + 50) / 100

  private fun WorkingMode.toOpenSwimModeValue(): Int =
    when (this) {
      WorkingMode.Bluetooth -> 0
      WorkingMode.LocalAudio -> 1
      else -> error("OpenSwim Pro supports only Bluetooth and Local Audio working modes")
    }

  private fun EqualizerPreset.toOpenSwimEqualizerValue(): Int =
    when (this) {
      EqualizerPreset.Standard -> 1
      EqualizerPreset.Vocal -> 2
      EqualizerPreset.Swimming -> 7
      else -> error("This OpenSwim Pro firmware has no captured value for $this")
    }

  private fun PlaybackOrder.toOpenSwimOrderValue(): Int =
    when (this) {
      PlaybackOrder.Normal -> 0
      PlaybackOrder.Shuffle -> 1
      PlaybackOrder.Repeat -> 2
    }

  private fun IntArray.toLittleEndianPayload(): ByteArray =
    ByteArray(size * Int.SIZE_BYTES).also { payload ->
      forEachIndexed { index, value ->
        val offset = index * Int.SIZE_BYTES
        payload[offset] = value.toByte()
        payload[offset + 1] = (value ushr 8).toByte()
        payload[offset + 2] = (value ushr 16).toByte()
        payload[offset + 3] = (value ushr 24).toByte()
      }
    }
}

/** Vendor transport boundary for an OpenSwim Pro connection. */
interface ShokzOpenSwimTransport {
  val profile: RcspDeviceProfile

  suspend fun exchange(request: ShokzOpenSwimEnvelope): ShokzOpenSwimEnvelope
}

/** Capability-gated controller for commands proven on the OpenSwim Pro vendor envelope. */
class ShokzOpenSwimController(private val transport: ShokzOpenSwimTransport) {
  fun isSupported(command: RcspCommand): Boolean = transport.profile.isSupported(command)

  suspend fun execute(command: RcspCommand): ShokzOpenSwimEnvelope {
    if (!isSupported(command)) throw UnsupportedRcspCommandException(command, transport.profile)
    val request =
      ShokzOpenSwimCommandEncoder.encode(command) ?: throw UnencodableRcspCommandException(command)
    return transport.exchange(request)
  }
}
