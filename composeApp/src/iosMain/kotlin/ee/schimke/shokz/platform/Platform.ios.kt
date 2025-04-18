package ee.schimke.shokz.platform

import dev.zacsweers.metro.Inject
import platform.UIKit.UIDevice

@Inject
class IOSPlatform: Platform {
    override val name: String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
}