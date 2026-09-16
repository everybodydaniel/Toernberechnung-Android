package com.example.trnberechnung.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.example.trnberechnung.ui.components.TideNodeAppHeader
import com.example.trnberechnung.viewmodel.NorthSeaWarningsUiState
import com.example.trnberechnung.warnings.NorthSeaWarning
import com.example.trnberechnung.warnings.OfficialLinkKind
import com.example.trnberechnung.warnings.OfficialWarningDocument
import com.example.trnberechnung.warnings.WarningCategory
import com.example.trnberechnung.warnings.WarningCoordinate
import com.example.trnberechnung.warnings.WarningGeometry
import com.example.trnberechnung.warnings.WarningGeometryType
import com.example.trnberechnung.warnings.WarningLifecycle
import com.example.trnberechnung.warnings.WarningSourceId
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** UI coverage with local data only; no BSH or ELWIS request is made by these tests. */
class NorthSeaWarningsContentTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun headerBellShowsUnreadStateAndOpensWarningsDirectly() {
        var hasNewWarnings by mutableStateOf(true)
        var warningsOpened = false

        composeTestRule.setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                TideNodeAppHeader(
                    onWarnings = { warningsOpened = true },
                    onSettings = {},
                    hasNewWarnings = hasNewWarnings,
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription("Nordsee-Warnmeldungen, neue Meldungen verf\u00fcgbar")
            .assertIsDisplayed()
            .performClick()
        composeTestRule.onNodeWithTag("app_header_warnings_badge").assertIsDisplayed()
        composeTestRule.runOnIdle {
            assertTrue(warningsOpened)
            hasNewWarnings = false
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("app_header_warnings_badge").assertDoesNotExist()
        composeTestRule
            .onNodeWithContentDescription("Nordsee-Warnmeldungen")
            .assertIsDisplayed()
        val warningsButton =
            composeTestRule.onNodeWithTag("app_header_warnings").fetchSemanticsNode().boundsInRoot
        val settingsButton =
            composeTestRule.onNodeWithTag("app_header_settings").fetchSemanticsNode().boundsInRoot
        assertTrue(
            "The warning bell belongs directly left of settings",
            warningsButton.right <= settingsButton.left,
        )
    }

    @Test
    fun pdfBadgesAndMapActionsFollowOfficialMetadata() {
        val state = warningState()
        var openedDocumentId: String? = null
        var openedSourceId: String? = null
        var focusedWarningId: String? = null

        composeTestRule.setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    NorthSeaWarningsContent(
                        state = state,
                        onCategorySelected = {},
                        onRefresh = {},
                        onToggleExpanded = {},
                        onOpenDocument = { openedDocumentId = it.id },
                        onOpenSource = { openedSourceId = it.id },
                        onShowOnMap = { focusedWarningId = it.id },
                    )
                }
            }
        }

        composeTestRule
            .onNodeWithTag("north_sea_warnings_screen")
            .performScrollToNode(hasTestTag("official_document_bsh-north-sea-pdf"))
        composeTestRule
            .onNodeWithTag("pdf_badge_bsh-north-sea-pdf", useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("pdf_badge_elwis-web").assertDoesNotExist()
        composeTestRule.onNodeWithTag("official_document_bsh-north-sea-pdf").performClick()
        composeTestRule.runOnIdle {
            assertEquals("bsh-north-sea-pdf", openedDocumentId)
        }

        composeTestRule
            .onNodeWithTag("north_sea_warnings_screen")
            .performScrollToNode(hasTestTag("warning_card_elwis:with-position"))
        composeTestRule
            .onNodeWithTag("warning_map_elwis:with-position")
            .assertIsEnabled()
            .performClick()
        composeTestRule.onNodeWithTag("pdf_badge_elwis:with-position").assertDoesNotExist()
        composeTestRule.runOnIdle {
            assertEquals("elwis:with-position", focusedWarningId)
        }
        assertSingleLineActionLabel(
            label = "Auf der Seekarte anzeigen",
            buttonTag = "warning_map_elwis:with-position",
        )
        composeTestRule.onNodeWithTag("warning_source_elwis:with-position").performClick()
        composeTestRule.runOnIdle {
            assertEquals("elwis:with-position", openedSourceId)
        }
        composeTestRule
            .onNodeWithTag("warning_full_text_elwis:with-position")
            .performScrollTo()
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("north_sea_warnings_screen")
            .performScrollToNode(hasTestTag("warning_card_bsh:without-position"))
        composeTestRule.runOnIdle { focusedWarningId = null }
        composeTestRule
            .onNodeWithTag("warning_map_bsh:without-position")
            .assertIsNotEnabled()
            .performClick()
        composeTestRule.runOnIdle { assertEquals(null, focusedWarningId) }
        composeTestRule.onNodeWithTag("warning_source_bsh:without-position").assertIsEnabled().performClick()
        assertSingleLineActionLabel("Quelle", "warning_source_bsh:without-position")
        composeTestRule.runOnIdle {
            assertEquals("bsh:without-position", openedSourceId)
        }
        composeTestRule
            .onNodeWithTag("pdf_badge_bsh:without-position")
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Keine amtlichen Koordinaten verf\u00fcgbar.")
            .assertIsDisplayed()
    }

    @Test
    fun moreAndLessKeepTheirFullLabelsAndToggleTheOfficialText() {
        var state by mutableStateOf(warningState().copy(expandedIds = emptySet()))

        composeTestRule.setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    NorthSeaWarningsContent(
                        state = state,
                        onCategorySelected = {},
                        onRefresh = {},
                        onToggleExpanded = { warningId ->
                            state =
                                state.copy(
                                    expandedIds =
                                        if (warningId in state.expandedIds) {
                                            state.expandedIds - warningId
                                        } else {
                                            state.expandedIds + warningId
                                        },
                                )
                        },
                        onOpenDocument = {},
                        onOpenSource = {},
                        onShowOnMap = {},
                    )
                }
            }
        }

        composeTestRule
            .onNodeWithTag("north_sea_warnings_screen")
            .performScrollToNode(hasTestTag("warning_card_elwis:with-position"))
        assertSingleLineActionLabel("Mehr", "warning_expand_elwis:with-position")
        composeTestRule.onNodeWithTag("warning_expand_elwis:with-position").performClick()
        assertSingleLineActionLabel("Weniger", "warning_expand_elwis:with-position")
        composeTestRule
            .onNodeWithTag("warning_full_text_elwis:with-position")
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag("warning_expand_elwis:with-position").performClick()
        assertSingleLineActionLabel("Mehr", "warning_expand_elwis:with-position")
        composeTestRule.onNodeWithTag("warning_full_text_elwis:with-position").assertDoesNotExist()
    }

    @Test
    fun officialDocumentCardAdaptsFromPhoneToTabletWidth() {
        var configuration by
            mutableStateOf(
                configuration(widthDp = 393, heightDp = 873, smallestDp = 393),
            )

        composeTestRule.setContent {
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                MaterialTheme(colorScheme = lightColorScheme()) {
                    Surface(Modifier.fillMaxSize()) {
                        NorthSeaWarningsContent(
                            state = warningState(),
                            onCategorySelected = {},
                            onRefresh = {},
                            onToggleExpanded = {},
                            onOpenDocument = {},
                            onOpenSource = {},
                            onShowOnMap = {},
                        )
                    }
                }
            }
        }

        composeTestRule.onNodeWithTag("north_sea_warnings_screen").assertIsDisplayed()
        val phoneCardWidth =
            composeTestRule
                .onNodeWithTag("official_document_bsh-north-sea-pdf")
                .fetchSemanticsNode()
                .boundsInRoot
                .width

        composeTestRule.runOnIdle {
            configuration = configuration(widthDp = 800, heightDp = 1280, smallestDp = 800)
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("north_sea_warnings_screen").assertIsDisplayed()
        val tabletCardWidth =
            composeTestRule
                .onNodeWithTag("official_document_bsh-north-sea-pdf")
                .fetchSemanticsNode()
                .boundsInRoot
                .width

        assertTrue(
            "Tablet document cards should be wider than phone cards",
            tabletCardWidth > phoneCardWidth,
        )
    }

    @Test
    fun warningActionsFitPhonePortrait() {
        assertWarningActionsFit(
            viewport = DpSize(320.dp, 640.dp),
            cardContentWidth = 256.dp,
        )
    }

    @Test
    fun warningActionsFitPhoneLandscape() {
        assertWarningActionsFit(
            viewport = DpSize(640.dp, 320.dp),
            cardContentWidth = 576.dp,
        )
    }

    @Test
    fun warningActionsFitTabletPortrait() {
        assertWarningActionsFit(
            viewport = DpSize(800.dp, 1280.dp),
            cardContentWidth = 720.dp,
        )
    }

    @Test
    fun warningActionsFitTabletLandscape() {
        assertWarningActionsFit(
            viewport = DpSize(1280.dp, 800.dp),
            cardContentWidth = 1068.dp,
        )
    }

    private fun warningState(): NorthSeaWarningsUiState {
        val withPosition =
            warning(
                id = "elwis:with-position",
                linkKind = OfficialLinkKind.WEB,
                source = WarningSourceId.ELWIS,
                geometry =
                    WarningGeometry.validated(
                        WarningGeometryType.POINT,
                        listOf(WarningCoordinate(latitude = 53.706, longitude = 7.154)),
                    ),
            )
        val withoutPosition =
            warning(
                id = "bsh:without-position",
                linkKind = OfficialLinkKind.PDF,
                source = WarningSourceId.BSH,
                geometry = null,
            )
        return NorthSeaWarningsUiState(
            warnings = listOf(withPosition, withoutPosition),
            documents =
                listOf(
                    OfficialWarningDocument(
                        id = "bsh-north-sea-pdf",
                        title = "BSH Nautische Warnnachrichten Nordsee",
                        issuer = "Bundesamt f\u00fcr Seeschifffahrt und Hydrographie",
                        description = "Amtliches PDF-Dokument",
                        url = "https://www2.bsh.de/aktdat/nwn/nwn-nord.pdf",
                        linkKind = OfficialLinkKind.PDF,
                    ),
                    OfficialWarningDocument(
                        id = "elwis-web",
                        title = "Bekanntmachungen f\u00fcr Seefahrer",
                        issuer = "WSV / ELWIS",
                        description = "Amtliche Webseite",
                        url = "https://www.elwis.de/DE/dynamisch/Bfs/",
                        linkKind = OfficialLinkKind.WEB,
                    ),
                ),
            expandedIds = setOf(withPosition.id),
        )
    }

    private fun warning(
        id: String,
        linkKind: OfficialLinkKind,
        source: WarningSourceId,
        geometry: WarningGeometry?,
    ): NorthSeaWarning =
        NorthSeaWarning(
            id = id,
            officialId = id.substringAfter(':'),
            reference = "42/26",
            title = if (geometry == null) "Amtliches PDF" else "Verlegte Tonne",
            fullText = "Vollst\u00e4ndiger amtlicher Meldungstext mit allen ver\u00f6ffentlichten Angaben.",
            source = source,
            publisher = if (source == WarningSourceId.BSH) "BSH" else "WSV / ELWIS",
            category = WarningCategory.NOTICE,
            publishedDate = LocalDate.of(2026, 9, 16),
            validFrom = LocalDate.of(2026, 9, 16),
            validUntil = LocalDate.of(2026, 9, 30),
            area = "Deutsche Nordsee",
            geometry = geometry,
            sourceUrl =
                if (linkKind == OfficialLinkKind.PDF) {
                    "https://www2.bsh.de/aktdat/nwn/nwn-nord.pdf"
                } else {
                    "https://www.elwis.de/DE/dynamisch/Bfs/bfsMeldung:42:elwis_bfs_showBfs"
                },
            linkKind = linkKind,
            lastUpdatedAt = Instant.parse("2026-09-16T08:00:00Z"),
            lifecycle = WarningLifecycle.ACTIVE,
            isComplete = true,
            contentRevision = "revision-$id",
        )

    private fun configuration(
        widthDp: Int,
        heightDp: Int,
        smallestDp: Int,
    ): Configuration =
        Configuration().apply {
            screenWidthDp = widthDp
            screenHeightDp = heightDp
            smallestScreenWidthDp = smallestDp
            orientation = Configuration.ORIENTATION_PORTRAIT
        }

    private fun assertSingleLineActionLabel(
        label: String,
        buttonTag: String,
    ) {
        val textNode =
            composeTestRule
                .onNode(
                    hasText(label) and hasAnyAncestor(hasTestTag(buttonTag)),
                    useUnmergedTree = true,
                ).fetchSemanticsNode()
        val results = mutableListOf<TextLayoutResult>()
        val textLayoutAction = textNode.config[SemanticsActions.GetTextLayoutResult]
        val getTextLayoutResult = requireNotNull(textLayoutAction.action)

        assertTrue(getTextLayoutResult.invoke(results))
        val result = results.single()
        assertEquals(1, result.lineCount)
        assertFalse(
            "$label was ellipsized",
            result.isLineEllipsized(0),
        )
        assertTrue(
            "$label exceeds its text bounds: lineRight=${result.getLineRight(0)}, size=${result.size}",
            result.getLineRight(0) <= result.size.width + 1f,
        )
        assertFalse(result.didOverflowHeight)
    }

    private fun assertWarningActionsFit(
        viewport: DpSize,
        cardContentWidth: Dp,
    ) {
        composeTestRule.setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                Surface(Modifier.requiredSize(viewport.width, viewport.height)) {
                    WarningCardActions(
                        warningId = "layout",
                        expanded = false,
                        canShowOnMap = true,
                        onShowOnMap = {},
                        onOpenSource = {},
                        onToggleExpanded = {},
                        modifier = Modifier.width(cardContentWidth),
                    )
                }
            }
        }

        assertSingleLineActionLabel("Auf der Seekarte anzeigen", "warning_map_layout")
        assertSingleLineActionLabel("Quelle", "warning_source_layout")
        assertSingleLineActionLabel("Mehr", "warning_expand_layout")

        val actionBounds =
            composeTestRule.onNodeWithTag("warning_actions_layout").fetchSemanticsNode().boundsInRoot
        listOf("warning_map_layout", "warning_source_layout", "warning_expand_layout").forEach { tag ->
            val buttonBounds = composeTestRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            assertTrue(buttonBounds.left >= actionBounds.left)
            assertTrue(buttonBounds.right <= actionBounds.right)
            assertTrue(buttonBounds.top >= actionBounds.top)
            assertTrue(buttonBounds.bottom <= actionBounds.bottom)
        }
    }
}
