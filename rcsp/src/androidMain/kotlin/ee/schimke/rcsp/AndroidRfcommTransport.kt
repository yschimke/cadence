package ee.schimke.rcsp

import android.bluetooth.BluetoothSocket
import java.io.InputStream

/**
 * Synchronous RFCOMM transport for a connected Bluetooth socket.
 *
 * Call [exchange] from a coroutine dispatcher appropriate for blocking I/O. This transport is
 * for the public RCSP frame only; it does not negotiate an OEM authentication envelope.
 */
class AndroidRfcommTransport(
  private val socket: BluetoothSocket,
  override val profile: RcspDeviceProfile,
) : RcspTransport {
  override suspend fun exchange(packet: RcspPacket): RcspPacket {
    socket.outputStream.run {
      write(RcspCodec.encode(packet))
      flush()
    }
    return RcspCodec.decode(readFrame(socket.inputStream))
  }

  private fun readFrame(input: InputStream): ByteArray {
    val prefix = byteArrayOf(0xFE.toByte(), 0xDC.toByte(), 0xBA.toByte())
    var matched = 0
    while (matched < prefix.size) {
      val next = input.readRequired()
      matched = if (next == prefix[matched]) matched + 1 else if (next == prefix[0]) 1 else 0
    }

    val header = input.readExactly(4)
    val payloadLength = header[2].toInt().and(0xFF) or (header[3].toInt().and(0xFF) shl 8)
    val payloadAndEnd = input.readExactly(payloadLength + 1)
    return prefix + header + payloadAndEnd
  }

  private fun InputStream.readExactly(size: Int): ByteArray = ByteArray(size).also { bytes ->
    var offset = 0
    while (offset < bytes.size) {
      val count = read(bytes, offset, bytes.size - offset)
      if (count < 0) throw java.io.EOFException("RFCOMM stream ended mid-frame")
      offset += count
    }
  }

  private fun InputStream.readRequired(): Byte {
    val value = read()
    if (value < 0) throw java.io.EOFException("RFCOMM stream ended before a frame")
    return value.toByte()
  }
}
