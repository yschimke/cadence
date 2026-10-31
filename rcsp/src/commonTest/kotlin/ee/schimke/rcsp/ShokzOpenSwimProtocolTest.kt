package ee.schimke.rcsp

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ShokzOpenSwimProtocolTest {
  @Test
  fun `encodes captured media commands byte-for-byte`() {
    assertContentEquals(
      "a55a2c00010000002800b64001000000200000000200000004000000010000000300000004000000040000000400000000000000"
        .hex(),
      ShokzOpenSwimCodec.encode(ShokzOpenSwimCommandEncoder.encode(RcspCommand.Pause)!!),
    )
    assertContentEquals(
      "a55a2c00010000002800438b01000000200000000200000004000000010000000300000004000000030000000400000000000000"
        .hex(),
      ShokzOpenSwimCodec.encode(ShokzOpenSwimCommandEncoder.encode(RcspCommand.Play)!!),
    )
    assertContentEquals(
      "a55a2c00010000002800a65001000000200000000200000004000000010000000300000004000000080000000400000000000000"
        .hex(),
      ShokzOpenSwimCodec.encode(ShokzOpenSwimCommandEncoder.encode(RcspCommand.NextTrack)!!),
    )
    assertContentEquals(
      "a55a2c00010000002800b24401000000200000000200000004000000010000000300000004000000070000000400000000000000"
        .hex(),
      ShokzOpenSwimCodec.encode(ShokzOpenSwimCommandEncoder.encode(RcspCommand.PreviousTrack)!!),
    )
  }

  @Test
  fun `encodes the captured Local Audio state query byte-for-byte`() {
    assertContentEquals(
      "a55a3000010000002c005d9d0100000024000000020000000400000002000000030000000400000040000000040000000400000000000000"
        .hex(),
      ShokzOpenSwimCodec.encode(ShokzOpenSwimCommandEncoder.encode(RcspCommand.QueryMediaState)!!),
    )
  }

  @Test
  fun `decodes a captured request and rejects checksum corruption`() {
    val frame =
      "a55a2c000100000028005ab701000000200000000200000004000000020000000300000004000000020000000400000000000000"
        .hex()

    val decoded = ShokzOpenSwimCodec.decode(frame)

    assertTrue(ShokzOpenSwimCodec.isRequest(decoded))
    assertEquals(40, decoded.payload.size)
    frame[12] = (frame[12].toInt() xor 1).toByte()
    assertFailsWith<IllegalArgumentException> { ShokzOpenSwimCodec.decode(frame) }
  }

  @Test
  fun `decodes a captured headset response with its response checksum seed`() {
    val response =
      "a55a3000010100002c00ec4f0100000024000000020000000400000003000000030000000400000006000000040000000400000000000000"
        .hex()

    assertTrue(ShokzOpenSwimCodec.isResponse(ShokzOpenSwimCodec.decode(response)))
  }

  @Test
  fun `decodes a captured Local Audio state response with its query checksum seed`() {
    val response =
      "a55a4000010100003c00d211010000003400000002000000040000000200000003000000040000004080000004000000140000000100000002000000010000000400000001000000"
        .hex()

    assertTrue(ShokzOpenSwimCodec.isResponse(ShokzOpenSwimCodec.decode(response)))
  }

  @Test
  fun `OpenSwim profile exposes only verified Local Audio commands`() {
    assertTrue(OpenSwimProProfile.isSupported(RcspCommand.QueryMediaState))
    assertTrue(OpenSwimProProfile.isSupported(RcspCommand.Play))
    assertTrue(OpenSwimProProfile.isSupported(RcspCommand.Pause))
    assertTrue(OpenSwimProProfile.isSupported(RcspCommand.NextTrack))
    assertTrue(OpenSwimProProfile.isSupported(RcspCommand.PreviousTrack))
  }

  private fun String.hex(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
