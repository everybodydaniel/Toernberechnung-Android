package com.example.trnberechnung.ui

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.platform.app.InstrumentationRegistry
import com.example.trnberechnung.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * End-to-end smoke test for the four-tab shell and the map overlays. Network
 * data is deliberately not required; the UI must remain usable offline.
 */
class FullAppUiTest {
    @get:Rule(order = 0)
    val cleanAppStateRule =
        TestRule { base: Statement, _: Description ->
            object : Statement() {
                override fun evaluate() {
                    val instrumentation = InstrumentationRegistry.getInstrumentation()
                    val context = instrumentation.targetContext
                    context
                        .getSharedPreferences("onboarding_preferences", Context.MODE_PRIVATE)
                        .edit()
                        .clear()
                        .commit()
                    context
                        .getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean("is_skipped", true)
                        .commit()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        ParcelFileDescriptor.AutoCloseInputStream(
                            instrumentation.uiAutomation.executeShellCommand(
                                "pm grant ${context.packageName} " +
                                    Manifest.permission.POST_NOTIFICATIONS,
                            ),
                        ).use { it.readBytes() }
                    }
                    base.evaluate()
                }
            }
        }

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun fullMapShellPlannerNautiSettingsAndTabs() {
        completeOnboarding()

        composeTestRule.onNodeWithTag("nav_map_route").assertIsSelected()
        composeTestRule.onAllNodesWithTag("global_app_header").assertCountEquals(1)

        // The bell opens the warning overview directly and the secondary back
        // action returns to the previously selected main tab.
        composeTestRule.onNodeWithTag("app_header_warnings").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("north_sea_warnings_screen").assertIsDisplayed()
        composeTestRule.onNodeWithText("Nordsee-Warnmeldungen").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("global_app_header").assertCountEquals(0)
        composeTestRule.onNodeWithTag("warnings_back").performClick()
        composeTestRule.onNodeWithTag("nav_map_route").assertIsSelected()
        composeTestRule.onAllNodesWithTag("global_app_header").assertCountEquals(1)

        composeTestRule.onNodeWithTag("full_bleed_map_tab").assertIsDisplayed()
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            composeTestRule
                .onAllNodesWithTag("maplibre_surface_ready")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule.onNodeWithTag("maplibre_surface_ready").assertExists()
        composeTestRule.onNodeWithTag("route_planning_pill").assertIsDisplayed()
        composeTestRule.onNodeWithTag("NautiInlinePanel").assertIsDisplayed()
        composeTestRule.onNodeWithTag("NautiInlineChat").assertDoesNotExist()

        // The planner is a full-height sheet and can be dismissed without
        // clearing its long-lived ViewModel state.
        composeTestRule.onNodeWithTag("route_planning_pill").performClick()
        composeTestRule.onNodeWithTag("route_planner_sheet").assertIsDisplayed()
        composeTestRule.onNodeWithTag("route_start_selector").assertExists()
        composeTestRule.onNodeWithTag("route_destination_selector").assertExists()
        composeTestRule.onNodeWithTag("route_planner_close").performClick()
        composeTestRule.waitForIdle()

        // Nauti lives only on the map and exposes chat plus history.
        composeTestRule.onNodeWithTag("NautiInlinePanel").performClick()
        composeTestRule.onNodeWithTag("NautiInlineChat").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Verlauf").performClick()
        composeTestRule.onNodeWithTag("NautiInlineHistory").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Zurück zum Chat").performClick()
        composeTestRule.onNodeWithContentDescription("Chat einklappen").performClick()

        composeTestRule.onNodeWithTag("nav_revier").performClick()
        composeTestRule.onNodeWithTag("nav_revier").assertIsSelected()
        composeTestRule.onNodeWithTag("screen_weather").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("global_app_header").assertCountEquals(1)
        assertTabSurfaceFitsBetweenOverlays("screen_weather")

        composeTestRule.onNodeWithTag("nav_crew").performClick()
        composeTestRule.onNodeWithTag("nav_crew").assertIsSelected()
        composeTestRule.onNodeWithTag("screen_crewspace").assertIsDisplayed()
        composeTestRule.onNodeWithText("KI-Assistent").assertDoesNotExist()
        assertTabSurfaceFitsBetweenOverlays("screen_crewspace")

        composeTestRule.onNodeWithTag("nav_logbook").performClick()
        composeTestRule.onNodeWithTag("nav_logbook").assertIsSelected()
        composeTestRule.onNodeWithTag("screen_header_logbook").assertIsDisplayed()
        assertTabSurfaceFitsBetweenOverlays("screen_logbook")

        // Settings opens directly; there is no intermediate menu.
        composeTestRule.onNodeWithTag("app_header_settings").performClick()
        composeTestRule.onNodeWithTag("boat_name_input").assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Gezeiten, Hoch- und Niedrigwasser sowie nautische Warnnachrichten")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("WSV / ELWIS")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Bekanntmachungen für Seefahrer").assertIsDisplayed()
        val newBoatName = "Flying Dutchman"
        composeTestRule
            .onNodeWithTag("boat_name_input")
            .performScrollTo()
            .performTextReplacement(newBoatName)
        composeTestRule.onNodeWithTag("boat_name_headline").assertTextEquals(newBoatName)
        composeTestRule.onNodeWithTag("settings_back").performClick()
        composeTestRule.onNodeWithTag("nav_map_route").assertIsSelected()
        composeTestRule.onAllNodesWithTag("global_app_header").assertCountEquals(1)
    }

    private fun completeOnboarding() {
        repeat(2) {
            composeTestRule.onNodeWithTag("onboarding_continue")
                .assertIsEnabled()
                .performClick()
            composeTestRule.waitForIdle()
        }
        composeTestRule.onNodeWithTag("onboarding_warnings_illustration").assertIsDisplayed()
        composeTestRule.onNodeWithText("Gefahren und Sperrungen").assertIsDisplayed()
        composeTestRule
            .onNodeWithText("SEEFAHRER-MELDUNGEN")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Meldungen lesen.\nInformiert ablegen.")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(
                "Informieren Sie sich über Sperrungen, Gefahren und veränderte Seezeichen in " +
                    "der Nordsee. Öffnen Sie verortete Meldungen direkt auf der Karte.",
            )
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("onboarding_continue")
            .assertIsEnabled()
            .performClick()
        composeTestRule.waitForIdle()
        // The crew page no longer advertises a chat that is not coming: the tile reads "Crew" and
        // carries no release badge.
        composeTestRule.onNodeWithText("FULL RELEASE").assertDoesNotExist()
        composeTestRule.onNodeWithText("Crew").assertIsDisplayed()
        composeTestRule
            .onNodeWithText(
                "Organisiere Rollen und teile Termine mit deiner Crew – " +
                    "alle an Bord wissen, wann es losgeht und wer was übernimmt.",
            ).assertIsDisplayed()
        composeTestRule.onNodeWithTag("onboarding_continue").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("onboarding_disclaimer_checkbox")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeTestRule.onNodeWithTag("onboarding_continue")
            .assertIsEnabled()
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("onboarding_screen").assertDoesNotExist()
    }

    private fun assertTabSurfaceFitsBetweenOverlays(screenTag: String) {
        composeTestRule.waitForIdle()
        val headerBounds =
            composeTestRule
                .onNodeWithTag("global_app_header")
                .fetchSemanticsNode()
                .boundsInRoot
        val tabBounds =
            composeTestRule
                .onNodeWithTag(screenTag)
                .fetchSemanticsNode()
                .boundsInRoot
        val navigationBounds =
            composeTestRule
                .onNodeWithTag("global_bottom_navigation")
                .fetchSemanticsNode()
                .boundsInRoot

        assertTrue(
            "$screenTag must start below the shared header",
            tabBounds.top >= headerBounds.bottom - 1f,
        )
        assertTrue(
            "$screenTag must draw behind the floating bottom navigation",
            tabBounds.bottom >= navigationBounds.bottom,
        )
    }
}
