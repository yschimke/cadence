package ee.schimke.cadence.wearcontract

import ee.schimke.rcsp.EqualizerPreset
import ee.schimke.rcsp.WorkingMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WearCompanionProtocolTest {
  @Test
  fun `round trips supported commands`() {
    assertEquals(
      CompanionCommand.SelectMode(WorkingMode.LocalAudio),
      WearCompanionProtocol.decodeCommand(
        WearCompanionProtocol.encode(CompanionCommand.SelectMode(WorkingMode.LocalAudio))
      ),
    )
    assertEquals(
      CompanionCommand.SelectEqualizerPreset(EqualizerPreset.Swimming),
      WearCompanionProtocol.decodeCommand(
        WearCompanionProtocol.encode(
          CompanionCommand.SelectEqualizerPreset(EqualizerPreset.Swimming)
        )
      ),
    )
  }

  @Test
  fun `rejects malformed or unsafe commands`() {
    assertNull(WearCompanionProtocol.decodeCommand("set_volume|101".encodeToByteArray()))
    assertNull(WearCompanionProtocol.decodeCommand("unknown".encodeToByteArray()))
  }
}
