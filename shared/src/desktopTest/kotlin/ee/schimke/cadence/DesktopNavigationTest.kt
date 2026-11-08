package ee.schimke.cadence

import androidx.lifecycle.ViewModelStore
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopNavigationTest {
  @Test
  fun libraryDoesNotRestoreASectionOpenedFromHomeContent() = withNavigation {
    // Home's existing buttons navigate directly, without the desktop menu's popUpTo options.
    controller.navigate(Podcasts)
    assertEquals("Podcasts", screen())
    select(DesktopSection.Library)
    assertEquals("Home", screen())
    assertNull(controller.previousBackStackEntry)
  }

  @Test
  fun libraryDoesNotRestoreTheSectionBeingPopped() = withNavigation {
    for (section in DesktopSection.entries.filter { it != DesktopSection.Library }) {
      select(section)
      assertEquals(section.routeName, screen())
      select(DesktopSection.Library)
      assertEquals("Home", screen(), "Returning from $section must display Library")
      assertNull(controller.previousBackStackEntry)
    }
  }

  @Test
  fun returningToLibraryPreservesOtherSectionsSavedState() = withNavigation {
    select(DesktopSection.Episodes)
    controller.currentBackStackEntry!!.savedStateHandle["episode-scroll"] = 42
    select(DesktopSection.Library)
    assertEquals("Home", screen())
    select(DesktopSection.Sources)
    select(DesktopSection.Episodes)
    assertEquals("Podcasts", screen())
    assertEquals(42, controller.currentBackStackEntry!!.savedStateHandle["episode-scroll"])
  }

  private fun withNavigation(block: NavigationFixture.() -> Unit) {
    val store = ViewModelStore()
    try {
      NavigationFixture(store).block()
    } finally {
      store.clear()
    }
  }

  private class NavigationFixture(store: ViewModelStore) {
    private val navigator = ComposeNavigator()
    val controller =
      NavHostController().apply {
        setViewModelStore(store)
        navigatorProvider.addNavigator(navigator)
        graph =
          createGraph(startDestination = Home) {
            composable<Home> {}
            composable<Podcasts> {}
            composable<Devices> {}
            composable<ManageSync> {}
            composable<FileSync> {}
            composable<CloudSync> {}
          }
      }

    fun select(section: DesktopSection) {
      val before = navigator.backStack.value
      controller.navigateDesktopSection(section)
      (before + navigator.backStack.value).forEach(navigator::onTransitionComplete)
    }

    fun screen() = screenName(controller.currentDestination!!.route!!)
  }
}
