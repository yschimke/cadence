package ee.schimke.cadence.widgets

import android.content.Context
import android.os.Build
import ee.schimke.cadence.analytics.CadenceFirebase
import ee.schimke.livery.client.AnalyticsEventSink
import ee.schimke.livery.client.AndroidBundled
import ee.schimke.livery.client.Clock
import ee.schimke.livery.client.DecisionContext
import ee.schimke.livery.client.Experiments
import ee.schimke.livery.client.FileBlobStore
import ee.schimke.livery.client.FileKeyValueStore
import ee.schimke.livery.client.GitHubReleaseSource
import ee.schimke.livery.client.ImageResolver
import ee.schimke.livery.client.JavaCrypto
import ee.schimke.livery.client.KeyValueStore
import ee.schimke.livery.client.KtorFetcher
import ee.schimke.livery.client.PlanResult
import ee.schimke.livery.client.SlotPlanner
import ee.schimke.livery.client.TemplateRepository
import ee.schimke.livery.client.TrustPolicy
import ee.schimke.livery.client.TrustedKey
import ee.schimke.livery.protocol.PlayerCapabilities
import ee.schimke.livery.protocol.Surfaces
import ee.schimke.livery.widgets.launcher.LiveryLauncherWidgetProvider
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.io.encoding.Base64

/**
 * Cadence's widget templates. The APK ships a catalog (`composeApp/livery`, built into the assets
 * by the Livery Gradle plugin), and merging a change to that tree on `main` publishes it, signed,
 * to this repository's rolling `catalog` release (`.github/workflows/livery-catalog.yml`).
 * [CatalogRefreshWorker] downloads it in the background; rendering only reads what is on disk, and
 * falls back to the bundled catalog until a newer signed one is there.
 *
 * Seasons (`composeApp/livery/seasons/`) are written into the manifest as experiments that every
 * user is in, so the planner's [Experiments] is what makes the widget draw a season's design.
 */
internal class WidgetLivery(
  context: Context,
  private val clock: Clock = Clock { System.currentTimeMillis() },
) {
  private val dir = File(context.noBackupFilesDir, "livery")
  private val kv: KeyValueStore = FileKeyValueStore(File(dir, "kv"))

  val repository =
    TemplateRepository(
      source = GitHubReleaseSource(owner = "yschimke", repo = "cadence", tag = CATALOG_TAG),
      fetcher = KtorFetcher(),
      crypto = JavaCrypto,
      // Only catalogs signed with Cadence's key are taken; the bundled catalog is the APK's own.
      trust = TrustPolicy.Signed(trustedKeys),
      blobs = FileBlobStore(File(dir, "blobs")),
      kv = kv,
      clock = clock,
      targets =
        listOf(
          PlayerCapabilities(
            Surfaces.LAUNCHER_WIDGET,
            maxRcApiLevel = 7,
            profiles = setOf("widgets"),
            livePush = false,
            maxBytes = LiveryLauncherWidgetProvider.DEFAULT_TEMPLATE_MAX_BYTES,
          )
        ),
      bundled = AndroidBundled.fromAssets(context),
    )

  private val appVersion: Long = runCatching {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode
    else @Suppress("DEPRECATION") info.versionCode.toLong()
  }
    .getOrDefault(0)

  private val experiments =
    Experiments(
      manifest = { repository.current() },
      context = {
        DecisionContext(
          nowEpochMs = clock.nowEpochMs(),
          platform = "android",
          locale = Locale.getDefault().toLanguageTag(),
          appVersion = appVersion,
          bucketingId = bucketingId(),
        )
      },
      // Livery's events in Firebase (livery_*), with one livery_exposure per season the widget
      // draws, so analytics can split by it.
      events = AnalyticsEventSink(CadenceFirebase.liverySinks()),
    )

  val planner = SlotPlanner(repository, experiments.sink, clock, experiments)

  /**
   * Reports the season (or experiment arm) [result] draws, once per arm. A launcher widget never
   * reports impressions itself, so this is what records that its look was shown.
   */
  fun drawn(result: PlanResult) {
    val plan = (result as? PlanResult.Ready)?.plan ?: return
    for ((experiment, arm) in plan.experiments) experiments.exposed(
      experiment,
      arm,
      clock.nowEpochMs(),
      designId = plan.template.design?.id,
    )
  }

  /**
   * The look the season running now gives the headphone widget, for Cadence's widgets that aren't
   * Livery templates (the podcast sync card's pool): the design a season picks for the headphone
   * contract, else [HeadphoneLook.Default]. Only seasons are read: a partial experiment on the
   * contract isn't a season.
   */
  suspend fun seasonLook(): HeadphoneLook {
    val arm =
      experiments.designFor(HeadphoneControlsTemplate.CONTRACT) ?: return HeadphoneLook.Default
    val season = repository.current()?.experiments?.firstOrNull { it.id == arm.experiment }
    return if (season?.allocationBasisPoints == 10_000 && season.arms.size == 1)
      HeadphoneLook.ofDesign(arm.designId)
    else HeadphoneLook.Default
  }

  /** The headphone templates draw no images; the resolver is here for the provider's contract. */
  val images by lazy { ImageResolver(repository, KtorFetcher(), JavaCrypto) }

  /**
   * A random id kept on this device, for experiment buckets. A season enrols everyone, so it only
   * matters if the catalog ever runs a partial experiment.
   */
  private suspend fun bucketingId(): String =
    kv.get(BUCKETING_KEY) ?: UUID.randomUUID().toString().also { kv.put(BUCKETING_KEY, it) }

  companion object {
    /** The rolling release the catalog workflow publishes to. */
    const val CATALOG_TAG = "catalog"

    /** The key id the catalog workflow signs with (`key-id` in `livery-catalog.yml`). */
    const val CATALOG_KEY_ID = "cadence-1"

    /**
     * `cadence.pub` from `livery keygen cadence`: the raw Ed25519 public key in Base64. The private
     * half is the `LIVERY_SIGNING_KEY` repository secret (and in 1Password). Rotating it means a
     * new key id here and in `livery-catalog.yml`, in an app release before the catalog switches.
     */
    const val CATALOG_PUBLIC_KEY = "4cr3vfcTkeAsjKwPcILKtp8MttydyDiyWAlwoFupJyk="

    private const val BUCKETING_KEY = "cadence.bucketing-id"

    val trustedKeys: List<TrustedKey> =
      CATALOG_PUBLIC_KEY.takeIf { it.isNotBlank() }
        ?.let { listOf(TrustedKey(CATALOG_KEY_ID, Base64.decode(it))) }
        .orEmpty()

    @Volatile private var instance: WidgetLivery? = null

    fun get(context: Context): WidgetLivery =
      instance
        ?: synchronized(this) {
          instance ?: WidgetLivery(context.applicationContext).also { instance = it }
        }
  }
}
