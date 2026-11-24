package ee.schimke.cadence.metro

import android.app.Application
import android.content.Context
import androidx.work.ListenableWorker
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metrox.android.MetroAppComponentProviders
import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import ee.schimke.cadence.auth.AndroidCredentialVault
import ee.schimke.cadence.auth.CredentialManagerRestoreKeyEscrow
import ee.schimke.cadence.auth.CredentialVault
import ee.schimke.cadence.bluetooth.BluetoothController
import ee.schimke.cadence.data.AndroidFileSystem
import ee.schimke.cadence.podcasts.AndroidPodcastPlatform
import ee.schimke.cadence.sync.RefreshOrchestrator
import ee.schimke.cadence.sync.RefreshScheduler
import ee.schimke.cadence.sync.SyncOrchestrator
import ee.schimke.cadence.sync.SyncRepo
import kotlin.reflect.KClass
import okio.FileSystem

/**
 * Contract surface exposed by the Android app graph. The concrete `@DependencyGraph` lives in
 * `:composeApp` so Metro can aggregate component contributions (e.g. `MainActivity`,
 * `PhoneWearCommandService`, workers) from the application module.
 */
interface AndroidAppGraph : AppGraph, ViewModelGraph, MetroAppComponentProviders {
  @Multibinds(allowEmpty = true)
  val workerFactories: Map<KClass<out ListenableWorker>, () -> AssistedWorkerFactory>

  val workerFactory: MetroWorkerFactory

  val syncOrchestrator: SyncOrchestrator

  /** The phone-owned controller is the only bridge allowed to reach OpenSwim transport. */
  val bluetoothController: BluetoothController

  val syncRepo: SyncRepo

  val refreshOrchestrator: RefreshOrchestrator

  val refreshScheduler: RefreshScheduler

  val podcastPlatform: AndroidPodcastPlatform

  @Provides
  @SingleIn(AppScope::class)
  fun provideCredentialVault(context: Context): CredentialVault =
    AndroidCredentialVault.create(context, CredentialManagerRestoreKeyEscrow(context))

  @Provides fun provideFileSystem(context: Context): FileSystem = AndroidFileSystem(context)

  @Provides fun provideOptionalFileSystem(fileSystem: FileSystem): FileSystem? = fileSystem

  @Binds val Application.bindContext: Context
}
