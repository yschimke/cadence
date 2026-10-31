package ee.schimke.rcsp

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RcspCodecTest {
  @Test
  fun `encodes and decodes a response-required command`() {
    val packet =
      RcspPacket(
        type = RcspPacket.Type.Command,
        opcode = 0x07u,
        responseRequired = true,
        payload = byteArrayOf(0x01),
      )

    val encoded = RcspCodec.encode(packet)

    assertContentEquals(
      byteArrayOf(
        0xFE.toByte(),
        0xDC.toByte(),
        0xBA.toByte(),
        0x47,
        0x00,
        0x01,
        0x00,
        0x01,
        0xEF.toByte(),
      ),
      encoded,
    )
    val decoded = RcspCodec.decode(encoded)
    assertTrue(decoded.type == RcspPacket.Type.Command)
    assertTrue(decoded.responseRequired)
    assertTrue(decoded.opcode == 0x07u.toUByte())
    assertContentEquals(byteArrayOf(0x01), decoded.payload)
  }

  @Test
  fun `capabilities are exact command checks`() {
    val capabilities = RcspCapabilities().withSupported(RcspCommandId.Play, RcspCommandId.Pause)

    assertTrue(capabilities.isSupported(RcspCommand.Play))
    assertTrue(capabilities.isSupported(RcspCommand.Pause))
    assertFalse(capabilities.isSupported(RcspCommand.SetVolume(50u)))
    assertFalse(capabilities.isSupported(RcspCommand.SeekRelative(SeekDirection.Forward, 5_000u)))
  }

  @Test
  fun `openswim profile stays conservative until its envelope is implemented`() {
    assertFalse(OpenSwimProProfile.isSupported(RcspCommand.SetVolume(50u)))
    assertFalse(
      OpenSwimProProfile.isSupported(RcspCommand.SeekRelative(SeekDirection.Forward, 5_000u))
    )
  }

  @Test
  fun `public Jieli encoder encodes verified system-info commands`() {
    assertContentEquals(
      byteArrayOf(
        0xFE.toByte(),
        0xDC.toByte(),
        0xBA.toByte(),
        0x47,
        0x00,
        0x01,
        0x00,
        0x01,
        0xEF.toByte(),
      ),
      RcspCodec.encode(PublicJieliCommandEncoder.encode(RcspCommand.QueryVolume)!!),
    )
    assertContentEquals(
      byteArrayOf(
        0xFE.toByte(),
        0xDC.toByte(),
        0xBA.toByte(),
        0x48,
        0x00,
        0x02,
        0x00,
        0x01,
        0x64,
        0xEF.toByte(),
      ),
      RcspCodec.encode(PublicJieliCommandEncoder.encode(RcspCommand.SetVolume(100u))!!),
    )
    assertNull(
      PublicJieliCommandEncoder.encode(RcspCommand.SeekRelative(SeekDirection.Forward, 1u))
    )
  }
}
