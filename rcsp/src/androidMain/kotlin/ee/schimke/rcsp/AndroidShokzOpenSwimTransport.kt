package ee.schimke.rcsp

import android.bluetooth.BluetoothSocket
import java.io.EOFException
import java.io.InputStream

/** RFCOMM transport for the captured OpenSwim Pro vendor envelope. */
class AndroidShokzOpenSwimTransport(
  private val socket: BluetoothSocket,
  override val profile: RcspDeviceProfile = OpenSwimProProfile,
) : ShokzOpenSwimTransport {
  override suspend fun exchange(request: ShokzOpenSwimEnvelope): ShokzOpenSwimEnvelope {
    socket.outputStream.run {
      write(ShokzOpenSwimCodec.encode(request))
      flush()
    }
    return ShokzOpenSwimCodec.decode(readFrame(socket.inputStream))
  }

  private fun readFrame(input: InputStream): ByteArray {
    var previous = -1
    while (true) {
      val next = input.readRequired()
      if (previous == 0xA5 && next == 0x5A) break
      previous = next
    }
    val header = input.readExactly(10)
    val payloadLength = header[6].toInt().and(0xFF) or (header[7].toInt().and(0xFF) shl 8)
    return byteArrayOf(0xA5.toByte(), 0x5A) + header + input.readExactly(payloadLength)
  }

  private fun InputStream.readExactly(size: Int): ByteArray = ByteArray(size).also { bytes ->
    var offset = 0
    while (offset < bytes.size) {
      val count = read(bytes, offset, bytes.size - offset)
      if (count < 0) throw EOFException("RFCOMM stream ended mid-frame")
      offset += count
    }
  }

  private fun InputStream.readRequired(): Int {
    val value = read()
    if (value < 0) throw EOFException("RFCOMM stream ended before a frame")
    return value
  }
}
