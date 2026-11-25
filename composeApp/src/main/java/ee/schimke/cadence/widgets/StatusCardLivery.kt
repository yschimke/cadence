package ee.schimke.cadence.widgets

import android.util.Log
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import ee.schimke.cadence.platform.CardDesign
import ee.schimke.cadence.platform.CardDesigns
import ee.schimke.cadence.podcasts.StatusCardRenderer
import ee.schimke.cadence.podcasts.StatusCardTemplate
import ee.schimke.cadence.podcasts.StatusCardValues
import ee.schimke.livery.client.BindResult
import ee.schimke.livery.client.ContractBinder
import ee.schimke.livery.client.TemplateQuery
import ee.schimke.livery.instantiate.InstantiateResult
import ee.schimke.livery.instantiate.TemplateInstantiator
import ee.schimke.livery.protocol.PlayerCapabilities
import ee.schimke.livery.protocol.Surfaces
import kotlin.coroutines.cancellation.CancellationException

/**
 * Home's status card from the Livery catalog (contract [StatusCardTemplate.CONTRACT]): the designs
 * Settings offers, and the chosen design's template with the card's values bound in. Templates come
 * from [WidgetLivery]'s repository, so a design published to the `catalog` release reaches Home
 * with the next refresh, and the catalog built into the app draws it until then (or whenever a
 * download is missing or invalid).
 */
@SingleIn(AppScope::class)
@Inject
class StatusCardLivery(private val livery: WidgetLivery) : StatusCardRenderer {
  private val instantiator = TemplateInstantiator { _, _, _ -> null }

  val designs = CardDesigns {
    try {
      livery.repository
        .designs(StatusCardTemplate.CONTRACT, emptyMap())
        .filter { !it.design.revoked }
        .map { CardDesign(it.design.id, it.design.title) }
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (e: Exception) {
      Log.w(TAG, "No status card designs", e)
      emptyList()
    }
  }

  override suspend fun render(
    designId: String,
    values: StatusCardValues,
    widthDp: Int,
    heightDp: Int,
  ): ByteArray? =
    try {
      val loaded =
        livery.repository.resolve(
          TemplateQuery(
            contract = StatusCardTemplate.CONTRACT,
            caps = CAPABILITIES,
            widthDp = widthDp,
            heightDp = heightDp,
            designId = designId,
            minor = StatusCardTemplate.CONTRACT_MINOR,
          ),
        ) ?: return null.also { Log.w(TAG, "No $designId template for ${widthDp}x$heightDp") }
      val contract = loaded.contract ?: return null.also { Log.w(TAG, "$designId has no contract") }
      when (val bound = ContractBinder.bind(contract, values.data())) {
        is BindResult.Ok ->
          when (
            val out =
              instantiator.instantiate(
                loaded.bytes,
                bound.bindings,
                widthDp,
                heightDp,
                maxBytes = MAX_BYTES,
                numericActions = false,
              )
          ) {
            is InstantiateResult.Ok -> out.bytes.toByteArray()
            else -> null.also { Log.w(TAG, "$designId didn't instantiate: $out") }
          }
        else -> null.also { Log.w(TAG, "$designId didn't bind: $bound") }
      }
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (e: Exception) {
      Log.w(TAG, "No $designId template", e)
      null
    }

  companion object {
    private const val TAG = "StatusCardLivery"

    /** A design's whole card, with every row's branch: well over any one state's size. */
    const val MAX_BYTES = 200_000L

    /** Home's own player: the launcher widgets' document profile, inside the app. */
    val CAPABILITIES =
      PlayerCapabilities(
        Surfaces.IN_APP,
        maxRcApiLevel = 7,
        profiles = setOf("widgets"),
        livePush = false,
        maxBytes = MAX_BYTES,
      )
  }
}
