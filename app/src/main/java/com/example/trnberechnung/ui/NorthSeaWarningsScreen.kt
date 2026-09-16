package com.example.trnberechnung.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.trnberechnung.ui.components.TideNodeBlue
import com.example.trnberechnung.ui.components.TideNodeBlueLight
import com.example.trnberechnung.ui.components.TideNodeDanger
import com.example.trnberechnung.ui.components.TideNodeWarning
import com.example.trnberechnung.ui.components.tideNodeGlass
import com.example.trnberechnung.viewmodel.NorthSeaWarningsUiState
import com.example.trnberechnung.viewmodel.NorthSeaWarningsViewModel
import com.example.trnberechnung.warnings.NorthSeaWarning
import com.example.trnberechnung.warnings.OfficialLinkKind
import com.example.trnberechnung.warnings.OfficialLinkOpenResult
import com.example.trnberechnung.warnings.OfficialLinkOpener
import com.example.trnberechnung.warnings.OfficialWarningDocument
import com.example.trnberechnung.warnings.WarningCategory
import com.example.trnberechnung.warnings.WarningSourceId
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

@Composable
fun NorthSeaWarningsScreen(
    viewModel: NorthSeaWarningsViewModel,
    onShowOnMap: (NorthSeaWarning) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val openOfficialLink: (String) -> Unit = { url ->
        when (val result = OfficialLinkOpener.open(context, url)) {
            OfficialLinkOpenResult.Opened -> Unit
            is OfficialLinkOpenResult.Error -> {
                scope.launch { snackbarHostState.showSnackbar(result.userMessage) }
            }
        }
    }

    DisposableEffect(viewModel) {
        viewModel.onScreenOpened()
        onDispose(viewModel::onScreenClosed)
    }

    Box(modifier.fillMaxSize()) {
        NorthSeaWarningsContent(
            state = state,
            onCategorySelected = viewModel::selectCategory,
            onRefresh = viewModel::refresh,
            onToggleExpanded = viewModel::toggleExpanded,
            onOpenDocument = { document -> openOfficialLink(document.url) },
            onOpenSource = { warning -> openOfficialLink(warning.sourceUrl) },
            onShowOnMap = onShowOnMap,
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
        )
    }
}

@Composable
internal fun NorthSeaWarningsContent(
    state: NorthSeaWarningsUiState,
    onCategorySelected: (WarningCategory?) -> Unit,
    onRefresh: () -> Unit,
    onToggleExpanded: (String) -> Unit,
    onOpenDocument: (OfficialWarningDocument) -> Unit,
    onOpenSource: (NorthSeaWarning) -> Unit,
    onShowOnMap: (NorthSeaWarning) -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout = currentAdaptiveLayout()
    val horizontalPadding = warningScreenHorizontalPadding(layout)
    val maxContentWidth = warningScreenMaxContentWidth(layout)
    val listState = rememberLazyListState()
    var handledRevealId by remember { mutableStateOf<String?>(null) }
    val warningItemsStartIndex =
        4 +
            (if (state.isStale) 1 else 0) +
            (if (state.isIncomplete && state.warnings.isNotEmpty()) 1 else 0) +
            (if (state.sourceError != null && state.warnings.isNotEmpty()) 1 else 0)
    LaunchedEffect(state.revealedWarningId, state.filteredWarnings, warningItemsStartIndex) {
        if (state.revealedWarningId == null || state.revealedWarningId == handledRevealId) {
            return@LaunchedEffect
        }
        val warningIndex =
            state.filteredWarnings.indexOfFirst { warning -> warning.id == state.revealedWarningId }
        if (warningIndex >= 0) {
            listState.scrollToItem(warningItemsStartIndex + warningIndex)
            handledRevealId = state.revealedWarningId
        }
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize().testTag("north_sea_warnings_screen"),
        contentPadding =
            androidx.compose.foundation.layout.PaddingValues(
                start = horizontalPadding,
                top = if (layout.isTablet) 24.dp else 16.dp,
                end = horizontalPadding,
                bottom = 40.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(if (layout.isTablet) 18.dp else 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Column(Modifier.fillMaxWidth().widthIn(max = maxContentWidth)) {
                Text(
                    text = "Nordsee-Warnmeldungen",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Amtliche Warnnachrichten und Bekanntmachungen für Seefahrer",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            Column(Modifier.fillMaxWidth().widthIn(max = maxContentWidth)) {
                SectionHeading("AMTLICHE BERICHTE & SEEFUNK", "BSH & WSV")
                Spacer(Modifier.height(10.dp))
                OfficialDocuments(
                    documents = state.documents,
                    isTablet = layout.isTablet,
                    onOpenDocument = onOpenDocument,
                )
            }
        }

        item {
            DisclaimerCard(Modifier.fillMaxWidth().widthIn(max = maxContentWidth))
        }

        item {
            Column(Modifier.fillMaxWidth().widthIn(max = maxContentWidth)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "AKTIVE MELDUNGEN (${state.warnings.size})",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = updateLabel(state.lastSuccessfulUpdate),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("warnings_last_update"),
                        )
                    }
                    IconButton(
                        onClick = onRefresh,
                        enabled = !state.isRefreshing,
                        modifier =
                            Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .testTag("warnings_refresh"),
                    ) {
                        if (state.isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Warnmeldungen aktualisieren")
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                WarningFilters(
                    selectedCategory = state.selectedCategory,
                    onCategorySelected = onCategorySelected,
                )
            }
        }

        if (state.isStale) {
            item {
                StatusBanner(
                    icon = Icons.Default.ReportProblem,
                    title = "Zwischengespeicherte Daten",
                    message =
                        "Die amtlichen Daten konnten nicht aktuell bestätigt werden. Die angezeigten Meldungen können veraltet sein.",
                    color = TideNodeWarning,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .widthIn(max = maxContentWidth)
                            .testTag("warnings_stale"),
                )
            }
        }
        if (state.isIncomplete && state.warnings.isNotEmpty()) {
            item {
                StatusBanner(
                    icon = Icons.Default.Info,
                    title = "Unvollständige Daten",
                    message =
                        "Einzelne amtliche Details werden erst beim Aufklappen geladen oder konnten nicht sicher gelesen werden.",
                    color = TideNodeBlue,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .widthIn(max = maxContentWidth)
                            .testTag("warnings_incomplete"),
                )
            }
        }
        state.sourceError?.takeIf { state.warnings.isNotEmpty() }?.let { error ->
            item {
                StatusBanner(
                    icon = Icons.Default.Error,
                    title =
                        if (state.isStale) {
                            "Fehler beim Aktualisieren einer Quelle"
                        } else {
                            "Fehler beim Verarbeiten einer Quelle"
                        },
                    message = error,
                    color = TideNodeDanger,
                    modifier = Modifier.fillMaxWidth().widthIn(max = maxContentWidth),
                )
            }
        }

        when {
            state.isInitialLoading -> {
                item {
                    LoadingState(Modifier.fillMaxWidth().widthIn(max = maxContentWidth))
                }
            }
            state.sourceError != null && state.warnings.isEmpty() -> {
                item {
                    ErrorState(
                        message = checkNotNull(state.sourceError),
                        onRefresh = onRefresh,
                        modifier = Modifier.fillMaxWidth().widthIn(max = maxContentWidth),
                    )
                }
            }
            state.filteredWarnings.isEmpty() -> {
                item {
                    EmptyState(
                        filtered = state.selectedCategory != null,
                        modifier = Modifier.fillMaxWidth().widthIn(max = maxContentWidth),
                    )
                }
            }
            else -> {
                items(state.filteredWarnings, key = NorthSeaWarning::id) { warning ->
                    WarningCard(
                        warning = warning,
                        expanded = warning.id in state.expandedIds,
                        loadingDetails = warning.id in state.loadingDetailIds,
                        detailError = state.detailErrors[warning.id],
                        onToggleExpanded = { onToggleExpanded(warning.id) },
                        onOpenSource = { onOpenSource(warning) },
                        onShowOnMap = { onShowOnMap(warning) },
                        modifier = Modifier.fillMaxWidth().widthIn(max = maxContentWidth),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeading(
    title: String,
    trailing: String,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(trailing, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TideNodeBlue)
    }
}

@Composable
private fun OfficialDocuments(
    documents: List<OfficialWarningDocument>,
    isTablet: Boolean,
    onOpenDocument: (OfficialWarningDocument) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        documents.forEach { document ->
            OfficialDocumentCard(
                document = document,
                onClick = { onOpenDocument(document) },
                modifier = Modifier.width(if (isTablet) 360.dp else 286.dp),
            )
        }
    }
}

@Composable
private fun OfficialDocumentCard(
    document: OfficialWarningDocument,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .height(184.dp)
                .tideNodeGlass(cornerRadius = 24.dp, elevation = 6.dp, alpha = 0.92f)
                .clickable(role = Role.Button, onClickLabel = "Amtliche Quelle öffnen", onClick = onClick)
                .padding(16.dp)
                .testTag("official_document_${document.id}"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector =
                    if (document.linkKind == OfficialLinkKind.PDF) {
                        Icons.Default.Description
                    } else {
                        Icons.Default.CellTower
                    },
                contentDescription = null,
                tint = TideNodeBlue,
            )
            Spacer(Modifier.weight(1f))
            if (showsPdfBadge(document.linkKind)) PdfBadge(document.id)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            document.title,
            fontSize = 16.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            document.description,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.weight(1f))
        Text(
            document.issuer,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PdfBadge(id: String) {
    Text(
        text = "PDF",
        color = Color.White,
        fontSize = 10.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier =
            Modifier
                .clip(RoundedCornerShape(7.dp))
                .background(Color(0xFFE52B3A))
                .padding(horizontal = 7.dp, vertical = 3.dp)
                .semantics { contentDescription = "PDF-Dokument" }
                .testTag("pdf_badge_$id"),
    )
}

internal fun showsPdfBadge(linkKind: OfficialLinkKind): Boolean = linkKind == OfficialLinkKind.PDF

@Composable
private fun WarningFilters(
    selectedCategory: WarningCategory?,
    onCategorySelected: (WarningCategory?) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        WarningFilterChip(
            label = "Alle",
            selected = selectedCategory == null,
            onClick = { onCategorySelected(null) },
            tag = "warnings_filter_all",
        )
        WarningCategory.entries.forEach { category ->
            WarningFilterChip(
                label = category.label,
                selected = selectedCategory == category,
                onClick = { onCategorySelected(category) },
                tag = "warnings_filter_${category.name.lowercase()}",
            )
        }
    }
}

@Composable
private fun WarningFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    tag: String,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium) },
        colors =
            FilterChipDefaults.filterChipColors(
                selectedContainerColor = TideNodeBlue.copy(alpha = 0.14f),
                selectedLabelColor = TideNodeBlue,
            ),
        modifier = Modifier.testTag(tag),
    )
}

@Composable
private fun WarningCard(
    warning: NorthSeaWarning,
    expanded: Boolean,
    loadingDetails: Boolean,
    detailError: String?,
    onToggleExpanded: () -> Unit,
    onOpenSource: () -> Unit,
    onShowOnMap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val categoryColor = warning.category.color
    Column(
        modifier =
            modifier
                .tideNodeGlass(cornerRadius = 24.dp, elevation = 5.dp, alpha = 0.94f)
                .padding(16.dp)
                .testTag("warning_card_${warning.id}"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).background(categoryColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    warning.category.icon,
                    contentDescription = null,
                    tint = categoryColor,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    warning.category.label.uppercase(),
                    color = categoryColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    listOfNotNull(warning.source.displayName, warning.reference, warning.publisher).joinToString(" · "),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (showsPdfBadge(warning.linkKind)) PdfBadge(warning.id)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            warning.title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 17.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        warning.area?.takeIf(String::isNotBlank)?.let { area ->
            Spacer(Modifier.height(5.dp))
            Text(
                area.removePrefix("Deutschland.Nordsee.").replace(".", " · "),
                color = TideNodeBlue,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(5.dp))
        Text(
            warningDateLabel(warning),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            warning.summary(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (expanded) {
            Spacer(Modifier.height(10.dp))
            if (loadingDetails) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (warning.isComplete) {
                            "Amtliche Details werden geprüft …"
                        } else {
                            "Amtliche Details werden geladen …"
                        },
                        fontSize = 13.sp,
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
            if (detailError != null) {
                Text(detailError, color = TideNodeDanger, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
            }
            when {
                warning.isComplete -> {
                    Text(
                        warning.fullText,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        modifier = Modifier.testTag("warning_full_text_${warning.id}"),
                    )
                }
                loadingDetails -> Unit
                detailError != null -> Unit
                else -> {
                    Text(
                        "Für diese Meldung liegen noch keine vollständig verarbeiteten Detaildaten vor.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        WarningCardActions(
            warningId = warning.id,
            expanded = expanded,
            canShowOnMap = warning.geometry?.isValid == true,
            onShowOnMap = onShowOnMap,
            onOpenSource = onOpenSource,
            onToggleExpanded = onToggleExpanded,
        )
        if (warning.geometry == null) {
            Text(
                if (warning.isComplete) {
                    "Keine amtlichen Koordinaten verfügbar."
                } else {
                    "Amtliche Koordinaten werden beim Öffnen der Details geprüft."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

@Composable
internal fun WarningCardActions(
    warningId: String,
    expanded: Boolean,
    canShowOnMap: Boolean,
    onShowOnMap: () -> Unit,
    onOpenSource: () -> Unit,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth().testTag("warning_actions_$warningId"),
    ) {
        val actionLayout = warningActionLayout(maxWidth, fontScale)
        if (actionLayout == WarningActionLayout.NARROW_STACKED) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                WarningActionButton(
                    label = "Auf der Seekarte anzeigen",
                    onClick = onShowOnMap,
                    enabled = canShowOnMap,
                    compact = true,
                    icon = null,
                    modifier = Modifier.fillMaxWidth().testTag("warning_map_$warningId"),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    WarningActionButton(
                        label = "Quelle",
                        onClick = onOpenSource,
                        compact = true,
                        icon = null,
                        modifier = Modifier.weight(1f).testTag("warning_source_$warningId"),
                    )
                    WarningActionButton(
                        label = if (expanded) "Weniger" else "Mehr",
                        onClick = onToggleExpanded,
                        compact = true,
                        icon = null,
                        modifier = Modifier.weight(1f).testTag("warning_expand_$warningId"),
                    )
                }
            }
        } else {
            val compact = actionLayout == WarningActionLayout.COMPACT_SINGLE_ROW
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(if (compact) 1.dp else 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WarningActionButton(
                    label = "Auf der Seekarte anzeigen",
                    onClick = onShowOnMap,
                    enabled = canShowOnMap,
                    compact = compact,
                    icon = if (compact) null else Icons.Default.Map,
                    modifier =
                        (if (compact) Modifier.weight(2.55f) else Modifier)
                            .testTag("warning_map_$warningId"),
                )
                WarningActionButton(
                    label = "Quelle",
                    onClick = onOpenSource,
                    compact = compact,
                    icon = if (compact) null else Icons.AutoMirrored.Filled.OpenInNew,
                    modifier =
                        (if (compact) Modifier.weight(1f) else Modifier)
                            .testTag("warning_source_$warningId"),
                )
                WarningActionButton(
                    label = if (expanded) "Weniger" else "Mehr",
                    onClick = onToggleExpanded,
                    compact = compact,
                    icon =
                        if (compact) {
                            null
                        } else if (expanded) {
                            Icons.Default.KeyboardArrowUp
                        } else {
                            Icons.Default.KeyboardArrowDown
                        },
                    modifier =
                        (if (compact) Modifier.weight(1.15f) else Modifier)
                            .testTag("warning_expand_$warningId"),
                )
                if (!compact) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun WarningActionButton(
    label: String,
    onClick: () -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.widthIn(min = 48.dp).heightIn(min = 48.dp),
        contentPadding =
            PaddingValues(
                horizontal = if (compact) 3.dp else 12.dp,
                vertical = 0.dp,
            ),
    ) {
        icon?.let {
            Icon(it, contentDescription = null, modifier = Modifier.size(if (compact) 15.dp else 17.dp))
            Spacer(Modifier.width(if (compact) 3.dp else 5.dp))
        }
        Text(
            text = label,
            fontSize =
                if (compact) {
                    warningCompactActionFontSizeSp(LocalDensity.current.fontScale).sp
                } else {
                    14.sp
                },
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun DisclaimerCard(modifier: Modifier = Modifier) {
    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(18.dp))
                .background(TideNodeBlue.copy(alpha = 0.09f))
                .padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Default.Info, contentDescription = null, tint = TideNodeBlue)
        Spacer(Modifier.width(10.dp))
        Text(
            "TideNode stellt amtliche Meldungen übersichtlich dar. Maßgeblich ist stets die verlinkte Originalquelle.",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun StatusBanner(
    icon: ImageVector,
    title: String,
    message: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(18.dp))
                .background(color.copy(alpha = 0.11f))
                .padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
            Text(
                message,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(vertical = 42.dp).testTag("warnings_loading"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text("Amtliche Meldungen werden geladen …")
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(20.dp))
                .background(TideNodeDanger.copy(alpha = 0.09f))
                .padding(20.dp)
                .testTag("warnings_network_error"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Default.Error, contentDescription = null, tint = TideNodeDanger)
        Spacer(Modifier.height(8.dp))
        Text("Warnmeldungen nicht verfügbar", fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(4.dp))
        Text(message, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        TextButton(onClick = onRefresh) { Text("Erneut versuchen") }
    }
}

@Composable
private fun EmptyState(
    filtered: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(vertical = 36.dp).testTag("warnings_empty"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Default.Info, contentDescription = null, tint = TideNodeBlue, modifier = Modifier.size(34.dp))
        Spacer(Modifier.height(9.dp))
        Text(
            if (filtered) "Keine Meldungen in dieser Kategorie" else "Keine aktiven Meldungen",
            fontWeight = FontWeight.ExtraBold,
        )
        Text(
            if (filtered) {
                "Wählen Sie „Alle“, um die übrigen amtlichen Meldungen zu sehen."
            } else {
                "Aktuell wurden keine aktiven Einzelmeldungen geladen. Prüfen Sie auch die amtlichen Dokumente oben."
            },
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val WarningCategory.label: String
    get() =
        when (this) {
            WarningCategory.DANGER -> "Gefahr"
            WarningCategory.WARNING -> "Warnung"
            WarningCategory.NOTICE -> "Hinweise"
        }

private val WarningSourceId.displayName: String
    get() =
        when (this) {
            WarningSourceId.BSH -> "BSH"
            WarningSourceId.ELWIS -> "WSV / ELWIS"
        }

private val WarningCategory.color: Color
    @Composable
    get() =
        when (this) {
            WarningCategory.DANGER -> TideNodeDanger
            WarningCategory.WARNING -> TideNodeWarning
            WarningCategory.NOTICE -> TideNodeBlueLight
        }

private val WarningCategory.icon: ImageVector
    get() =
        when (this) {
            WarningCategory.DANGER -> Icons.Default.Error
            WarningCategory.WARNING -> Icons.Default.ReportProblem
            WarningCategory.NOTICE -> Icons.Default.Info
        }

private fun warningDateLabel(warning: NorthSeaWarning): String {
    val published = warning.publishedDate?.format(DATE_FORMATTER)
    val validity =
        when {
            warning.validFrom != null && warning.validUntil != null ->
                "Gültig ${warning.validFrom.format(DATE_FORMATTER)}–${warning.validUntil.format(DATE_FORMATTER)}"
            warning.validFrom != null -> "Gültig ab ${warning.validFrom.format(DATE_FORMATTER)}"
            warning.validUntil != null -> "Gültig bis ${warning.validUntil.format(DATE_FORMATTER)}"
            else -> null
        }
    return listOfNotNull(published?.let { "Veröffentlicht $it" }, validity).joinToString(" · ")
        .ifBlank { "Kein amtliches Datum angegeben" }
}

private fun updateLabel(lastSuccessfulUpdate: Instant?): String =
    lastSuccessfulUpdate?.let { instant ->
        "Zuletzt erfolgreich: ${UPDATE_FORMATTER.format(instant.atZone(BERLIN_ZONE))} Uhr"
    } ?: "Noch nicht erfolgreich aktualisiert"

private val DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy")
private val UPDATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy · HH:mm")
private val BERLIN_ZONE = ZoneId.of("Europe/Berlin")
