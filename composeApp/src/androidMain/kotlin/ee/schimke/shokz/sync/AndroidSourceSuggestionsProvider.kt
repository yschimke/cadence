package ee.schimke.shokz.sync

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding

@ContributesBinding(AppScope::class, binding = binding<SourceSuggestionsProvider>())
@Inject
class AndroidSourceSuggestionsProvider(
    private val context: Context,
) : SourceSuggestionsProvider {

    override suspend fun list(): List<SourceSuggestion> =
        SourceSuggestionsCatalogue.all.map { it.copy(installed = isInstalled(it.packageName)) }

    override fun open(suggestion: SourceSuggestion) {
        val pm = context.packageManager
        val launch = pm.getLaunchIntentForPackage(suggestion.packageName)
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(launch) }
            return
        }
        val market = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=${suggestion.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(market) }.isSuccess) return

        val web = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=${suggestion.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(web) }
    }

    private fun isInstalled(packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}
