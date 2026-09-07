package com.example.trnberechnung.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sailing
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.Water
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.trnberechnung.R
import com.example.trnberechnung.model.AppPreferences
import com.example.trnberechnung.model.BoatProfileRepository
import com.example.trnberechnung.ui.components.TideNodeBlue
import com.example.trnberechnung.ui.components.tideNodeGlass

import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

import androidx.compose.ui.graphics.luminance

private val SettingsCardBg: Color
    @Composable get() = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFF1E293B) else Color.White

private val SettingsSectionTitle: Color
    @Composable get() = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFF93C5FD) else Color(0xFF1E3A8A)

private val SettingsSubtitle: Color
    @Composable get() = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFF94A3B8) else Color(0xFF64748B)

private val SettingsInputBg: Color
    @Composable get() = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFF0F172A) else Color(0xFFF8FAFC)

private val SettingsInputBorder: Color
    @Composable get() = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFF334155) else Color(0xFFE2E8F0)

private val SettingsBadgeBg: Color
    @Composable get() = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFF1E3A8A).copy(alpha = 0.4f) else Color(0xFFEFF6FF)

private val SettingsPrimaryBlue: Color
    @Composable get() = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFF60A5FA) else Color(0xFF2563EB)

private val SettingsTextColor: Color
    @Composable get() = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFFF8FAFC) else Color(0xFF0F172A)

@Composable
fun DashboardScreen(
    appPreferences: AppPreferences? = null,
    onToggleDarkMode: (Boolean) -> Unit = {},
    onReplayOnboarding: () -> Unit = {},
) {
    val context = LocalContext.current
    val repo = remember { BoatProfileRepository(context) }
    val scrollState = rememberScrollState()
    val adaptiveLayout = currentAdaptiveLayout()

    var boatName by remember { mutableStateOf(repo.boatName) }
    var boatType by remember { mutableStateOf(repo.boatType.ifBlank { "Segelyacht" }) }
    var callSign by remember { mutableStateOf(repo.callSign) }
    var draft by remember { mutableStateOf(repo.draft.toString()) }
    var length by remember { mutableStateOf(if (repo.length > 0) repo.length.toString() else "") }
    var safetyMargin by remember { mutableStateOf(repo.safetyMargin.toString()) }
    var waterLevelCorrection by remember { mutableStateOf(repo.waterLevelCorrection.toString()) }

    var isDark by remember { mutableStateOf(appPreferences?.isDarkMode ?: false) }
    var boatTypeExpanded by remember { mutableStateOf(false) }
    var showOnboardingDialog by remember { mutableStateOf(false) }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(scrollState)
                .padding(
                    horizontal = if (adaptiveLayout.isTablet) adaptiveLayout.horizontalScreenPadding else 16.dp,
                    vertical = if (adaptiveLayout.isTablet) 16.dp else 10.dp,
                ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement =
            Arrangement.spacedBy(if (adaptiveLayout.isTablet) TabletLayoutTokens.SectionSpacing else 14.dp),
    ) {
        // Drag Handle & Header Title
        Box(
            modifier =
                Modifier
                    .size(width = 38.dp, height = 5.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFCBD5E1)),
        )

        Text(
            text = "Einstellungen",
            fontSize = if (adaptiveLayout.isTablet) 24.sp else 19.sp,
            fontWeight = FontWeight.ExtraBold,
            color = SettingsTextColor,
            modifier = Modifier.semantics { heading() }
        )

        // Hidden headline testTag for automated UI tests
        Box(modifier = Modifier.size(0.dp).testTag("boat_name_headline")) {
            Text(boatName)
        }

        // ══════════════════════════════════════════════════
        // Card 1: Top Brand Pill (TideNode)
        // ══════════════════════════════════════════════════
        SettingsCard {
            Row(
                modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(R.drawable.tidenode_mark),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .size(if (adaptiveLayout.isTablet) 64.dp else 52.dp)
                            .clip(RoundedCornerShape(if (adaptiveLayout.isTablet) 18.dp else 14.dp)),
                )
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        text = "TideNode",
                        fontSize = if (adaptiveLayout.isTablet) 27.sp else 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = SettingsSectionTitle,
                        letterSpacing = (-0.5).sp,
                    )
                    Text(
                        text = "Profile, Darstellung und Datenquellen",
                        fontSize = if (adaptiveLayout.isTablet) 16.sp else 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = SettingsSubtitle,
                    )
                }
            }
        }

        // ══════════════════════════════════════════════════
        // Card 2: Bootsprofil
        // ══════════════════════════════════════════════════
        SettingsCard {
            SettingsCardHeader(
                icon = Icons.Default.Sailing,
                title = "Bootsprofil",
            )
            Spacer(Modifier.height(14.dp))

            // Bootsname Field
    SettingsInputField(
        value = boatName,
        onValueChange = {
            boatName = it
            repo.boatName = it
        },
        label = "Bootsname",
        placeholder = "z.B. Morning Star",
        leadingIcon = Icons.Default.Label,
        modifier = Modifier.testTag("boat_name_input"),
    )

            Spacer(Modifier.height(12.dp))

            // Bootstyp Selector
            Text(
                "Bootstyp",
                fontSize = if (adaptiveLayout.isTablet) 14.sp else 12.sp,
                fontWeight = FontWeight.Bold,
                color = SettingsSubtitle,
            )
            Spacer(Modifier.height(4.dp))
            Box {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(if (adaptiveLayout.isTablet) 64.dp else 52.dp)
                            .clip(RoundedCornerShape(if (adaptiveLayout.isTablet) 20.dp else 16.dp))
                            .background(SettingsInputBg)
                            .border(
                                1.dp,
                                SettingsInputBorder,
                                RoundedCornerShape(if (adaptiveLayout.isTablet) 20.dp else 16.dp),
                            )
                            .clickable(
                                onClick = { boatTypeExpanded = true },
                                role = Role.Button,
                                onClickLabel = "Bootstyp auswählen"
                            )
                            .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBadge(Icons.Default.DirectionsBoat)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = boatType,
                        fontSize = if (adaptiveLayout.isTablet) 18.sp else 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        Icons.Default.UnfoldMore,
                        null,
                        tint = SettingsPrimaryBlue,
                        modifier = Modifier.size(if (adaptiveLayout.isTablet) 24.dp else 20.dp),
                    )
                }

                DropdownMenu(
                    expanded = boatTypeExpanded,
                    onDismissRequest = { boatTypeExpanded = false },
                ) {
                    listOf("Segelyacht", "Motoryacht", "Katamaran", "Gleiter", "Motorboot", "Schwertboot").forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option, fontWeight = FontWeight.Bold) },
                            onClick = {
                                boatType = option
                                repo.boatType = option
                                boatTypeExpanded = false
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Rufzeichen Field
            SettingsInputField(
                value = callSign,
                onValueChange = {
                    callSign = it
                    repo.callSign = it
                },
                label = "Rufzeichen",
                placeholder = "z.B. DA1234",
                leadingIcon = Icons.Default.CellTower,
            )

            Spacer(Modifier.height(12.dp))

            // 3 Numeric Fields Row (Tiefgang, Länge, UKC)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SettingsNumberBox(
                    value = draft,
                    onValueChange = {
                        draft = it
                        it.replace(',', '.').toFloatOrNull()?.let { v -> repo.draft = v }
                    },
                    icon = Icons.Default.ArrowDownward,
                    label = "Tiefgang",
                    modifier = Modifier.weight(1f),
                )
                SettingsNumberBox(
                    value = length,
                    onValueChange = {
                        length = it
                        it.replace(',', '.').toFloatOrNull()?.let { v -> repo.length = v }
                    },
                    icon = Icons.Default.Straighten,
                    label = "Länge",
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SettingsNumberBox(
                    value = safetyMargin,
                    onValueChange = {
                        safetyMargin = it
                        it.replace(',', '.').toFloatOrNull()?.let { v -> repo.safetyMargin = v }
                    },
                    icon = Icons.Default.Shield,
                    label = "UKC-M",
                    modifier = Modifier.weight(1f),
                )
                SettingsNumberBox(
                    value = waterLevelCorrection,
                    onValueChange = {
                        waterLevelCorrection = it
                        it.replace(',', '.').toFloatOrNull()?.let { v -> repo.waterLevelCorrection = v }
                    },
                    icon = Icons.Default.Water,
                    label = "Pegelkorrektur (m)",
                    compactLabel = "Pegel ± (m)",
                    accessibilityLabel = "Pegelkorrektur in Metern",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // ══════════════════════════════════════════════════
        // Card 3: Darstellung (Dark / White Mode Slider)
        // ══════════════════════════════════════════════════
        SettingsCard {
            SettingsCardHeader(
                icon = Icons.Default.Palette,
                title = "Darstellung",
            )
            Spacer(Modifier.height(14.dp))

            // Segmented Theme Toggle Bar
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(if (adaptiveLayout.isTablet) 68.dp else 56.dp)
                        .clip(RoundedCornerShape(if (adaptiveLayout.isTablet) 34.dp else 28.dp))
                        .background(SettingsInputBg)
                        .border(
                            1.dp,
                            SettingsInputBorder,
                            RoundedCornerShape(if (adaptiveLayout.isTablet) 34.dp else 28.dp),
                        )
                        .padding(4.dp),
            ) {
                // Light Mode Button
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clip(RoundedCornerShape(if (adaptiveLayout.isTablet) 30.dp else 24.dp))
                            .background(if (!isDark) SettingsPrimaryBlue else Color.Transparent)
                            .clickable {
                                isDark = false
                                appPreferences?.isDarkMode = false
                                onToggleDarkMode(false)
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(contentAlignment = Alignment.TopEnd) {
                        Icon(
                            Icons.Default.WbSunny,
                            contentDescription = "Light Mode",
                            tint = if (!isDark) Color.White else SettingsSubtitle,
                            modifier = Modifier.size(if (adaptiveLayout.isTablet) 30.dp else 24.dp),
                        )
                        if (!isDark) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(Color.White),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Default.Check, null, tint = SettingsPrimaryBlue, modifier = Modifier.size(9.dp))
                            }
                        }
                    }
                }

                // Dark Mode Button
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clip(RoundedCornerShape(if (adaptiveLayout.isTablet) 30.dp else 24.dp))
                            .background(if (isDark) SettingsPrimaryBlue else Color.Transparent)
                            .clickable {
                                isDark = true
                                appPreferences?.isDarkMode = true
                                onToggleDarkMode(true)
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(contentAlignment = Alignment.TopEnd) {
                        Icon(
                            Icons.Default.NightsStay,
                            contentDescription = "Dark Mode",
                            tint = if (isDark) Color.White else SettingsSubtitle,
                            modifier = Modifier.size(if (adaptiveLayout.isTablet) 30.dp else 24.dp),
                        )
                        if (isDark) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(Color.White),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Default.Check, null, tint = SettingsPrimaryBlue, modifier = Modifier.size(9.dp))
                            }
                        }
                    }
                }
            }
        }

        // ══════════════════════════════════════════════════
        // Card 4: Einführung
        // ══════════════════════════════════════════════════
        SettingsCard {
            SettingsCardHeader(
                icon = Icons.Default.AutoAwesome,
                title = "Einführung",
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(if (adaptiveLayout.isTablet) 20.dp else 16.dp))
                        .background(SettingsInputBg)
                        .border(
                            1.dp,
                            SettingsInputBorder,
                            RoundedCornerShape(if (adaptiveLayout.isTablet) 20.dp else 16.dp),
                        )
                        .clickable(
                            onClick = {
                                onReplayOnboarding()
                                showOnboardingDialog = true
                            },
                            role = Role.Button,
                            onClickLabel = "Einführung starten"
                        )
                        .padding(if (adaptiveLayout.isTablet) 16.dp else 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(if (adaptiveLayout.isTablet) 48.dp else 38.dp)
                            .clip(CircleShape)
                            .background(SettingsBadgeBg),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        null,
                        tint = SettingsPrimaryBlue,
                        modifier = Modifier.size(if (adaptiveLayout.isTablet) 27.dp else 22.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Einführung erneut ansehen",
                        fontWeight = FontWeight.Bold,
                        fontSize = if (adaptiveLayout.isTablet) 18.sp else 15.sp,
                        color =
                            if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
                                Color(0xFFF8FAFC)
                            } else {
                                Color(0xFF0F172A)
                            },
                    )
                    Text(
                        "Törnplanung, Wetter und Crew",
                        fontSize = if (adaptiveLayout.isTablet) 15.sp else 12.sp,
                        color = SettingsSubtitle,
                    )
                }
                Icon(
                    Icons.Default.ChevronRight,
                    null,
                    tint = SettingsSubtitle,
                    modifier = Modifier.size(if (adaptiveLayout.isTablet) 24.dp else 20.dp),
                )
            }
        }

        // ══════════════════════════════════════════════════
        // Card 5: Datenquellen (Android Native Data Sources)
        // ══════════════════════════════════════════════════
        SettingsCard {
            SettingsCardHeader(
                icon = Icons.Default.Language,
                title = "Datenquellen",
            )
            Spacer(Modifier.height(12.dp))

            DataSourceRow(
                icon = Icons.Default.Water,
                title = "BSH",
                subtitle = "Gezeiten, Hoch- und Niedrigwasser",
            )
            Spacer(Modifier.height(8.dp))
            DataSourceRow(
                icon = Icons.Default.Cloud,
                title = "Open-Meteo & DWD",
                subtitle = "Wetter-Prognosen, Wind und Böen",
            )
        }

        // ══════════════════════════════════════════════════
        // Card 6: Copyright & Disclaimer
        // ══════════════════════════════════════════════════
        SettingsCard {
            Text(
                text = "© 2026 TideNode",
                fontSize = if (adaptiveLayout.isTablet) 20.sp else 17.sp,
                fontWeight = FontWeight.ExtraBold,
                color = SettingsSectionTitle,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text =
                    "TideNode ersetzt keine Seeordnung, amtlichen Bekanntmachungen, Revierinformationen " +
                        "oder die nautische Verantwortung der Schiffsführung.",
                fontSize = if (adaptiveLayout.isTablet) 15.sp else 13.sp,
                color = SettingsSubtitle,
                lineHeight = 18.sp,
            )
        }

        Spacer(Modifier.height(if (adaptiveLayout.isTablet) 32.dp else 20.dp))
    }

    if (showOnboardingDialog) {
        Dialog(
            onDismissRequest = { showOnboardingDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            OnboardingScreen(
                onCompleted = { showOnboardingDialog = false },
            )
        }
    }
}

@Composable
private fun SettingsCard(
    content: @Composable ColumnScope.() -> Unit,
) {
    val adaptiveLayout = currentAdaptiveLayout()
    Column(
        modifier =
            (if (adaptiveLayout.isTablet) {
                Modifier.widthIn(max = adaptiveLayout.compactContentMaxWidth).fillMaxWidth()
            } else {
                Modifier.fillMaxWidth()
            }).clip(
                RoundedCornerShape(if (adaptiveLayout.isTablet) TabletLayoutTokens.CardCornerRadius else 24.dp),
            )
                .background(SettingsCardBg)
                .border(
                    1.dp,
                    SettingsInputBorder,
                    RoundedCornerShape(
                        if (adaptiveLayout.isTablet) TabletLayoutTokens.CardCornerRadius else 24.dp,
                    ),
                ).padding(if (adaptiveLayout.isTablet) TabletLayoutTokens.CardPadding else 18.dp),
        content = content,
    )
}

@Composable
private fun SettingsCardHeader(
    icon: ImageVector,
    title: String,
) {
    val adaptiveLayout = currentAdaptiveLayout()
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon)
        Spacer(Modifier.width(10.dp))
        Text(
            text = title,
            fontSize = if (adaptiveLayout.isTablet) 22.sp else 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = SettingsSectionTitle,
        )
    }
}

@Composable
private fun IconBadge(icon: ImageVector, contentDescription: String? = null) {
    val adaptiveLayout = currentAdaptiveLayout()
    Box(
        modifier =
            Modifier
                .size(if (adaptiveLayout.isTablet) 44.dp else 36.dp)
                .clip(CircleShape)
                .background(SettingsBadgeBg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = SettingsPrimaryBlue,
            modifier = Modifier.size(if (adaptiveLayout.isTablet) 24.dp else 20.dp),
        )
    }
}

@Composable
private fun SettingsInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    leadingIcon: ImageVector,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    val adaptiveLayout = currentAdaptiveLayout()
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = {
            Text(
                placeholder,
                color = SettingsSubtitle,
                fontSize = if (adaptiveLayout.isTablet) 17.sp else 14.sp,
            )
        },
        leadingIcon = {
            Box(
                modifier =
                    Modifier
                        // Keep the same 14 dp badge inset and effective 10 dp text gap as Bootstyp.
                        .width(if (adaptiveLayout.isTablet) 64.dp else 56.dp)
                        .padding(start = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                IconBadge(leadingIcon, contentDescription = null)
            }
        },
        trailingIcon = trailingIcon,
        modifier =
            modifier
                .fillMaxWidth()
                .height(if (adaptiveLayout.isTablet) 72.dp else 64.dp),
        shape = RoundedCornerShape(if (adaptiveLayout.isTablet) 20.dp else 16.dp),
        textStyle =
            LocalTextStyle.current.copy(
                fontSize = if (adaptiveLayout.isTablet) 18.sp else LocalTextStyle.current.fontSize,
            ),
        singleLine = true,
        visualTransformation = visualTransformation,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedContainerColor = SettingsInputBg,
                unfocusedContainerColor = SettingsInputBg,
                focusedBorderColor = SettingsPrimaryBlue,
                unfocusedBorderColor = SettingsInputBorder,
                focusedTextColor = SettingsTextColor,
                unfocusedTextColor = SettingsTextColor,
            ),
    )
}

@Composable
private fun SettingsNumberBox(
    value: String,
    onValueChange: (String) -> Unit,
    icon: ImageVector,
    label: String,
    compactLabel: String? = null,
    accessibilityLabel: String = label,
    modifier: Modifier = Modifier,
) {
    val adaptiveLayout = currentAdaptiveLayout()
    val density = LocalDensity.current
    val labelLineCount =
        if (
            (!adaptiveLayout.isTablet && density.fontScale > 1f) ||
            (adaptiveLayout.isTablet && density.fontScale > 1.6f)
        ) {
            2
        } else {
            1
        }
    val baseFieldHeight = if (adaptiveLayout.isTablet) 68.dp else 56.dp
    val textGroupHeight =
        with(density) {
            (if (adaptiveLayout.isTablet) 18.sp else 14.sp).toDp() * labelLineCount.toFloat() +
                (if (adaptiveLayout.isTablet) 22.sp else 20.sp).toDp()
        } + 10.dp
    val fieldHeight = maxOf(baseFieldHeight, textGroupHeight)
    Row(
        modifier =
            modifier
                .height(fieldHeight)
                .clip(RoundedCornerShape(if (adaptiveLayout.isTablet) 20.dp else 16.dp))
                .background(SettingsInputBg)
                .border(
                    1.dp,
                    SettingsInputBorder,
                    RoundedCornerShape(if (adaptiveLayout.isTablet) 20.dp else 16.dp),
                ).padding(horizontal = if (adaptiveLayout.isTablet) 14.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .semantics(mergeDescendants = true) {
                        contentDescription = accessibilityLabel
                    },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            textStyle =
                LocalTextStyle.current.copy(
                    color = SettingsTextColor,
                    fontSize = if (adaptiveLayout.isTablet) 18.sp else 16.sp,
                    lineHeight = if (adaptiveLayout.isTablet) 22.sp else 20.sp,
                ),
            cursorBrush = SolidColor(SettingsPrimaryBlue),
            decorationBox = { innerTextField ->
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val useCompactLabel =
                        compactLabel != null &&
                            !adaptiveLayout.isTablet &&
                            maxWidth < 120.dp * LocalDensity.current.fontScale
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = if (useCompactLabel) compactLabel.orEmpty() else label,
                            color = SettingsSubtitle,
                            fontSize = if (adaptiveLayout.isTablet) 15.sp else 11.sp,
                            lineHeight = if (adaptiveLayout.isTablet) 18.sp else 14.sp,
                            maxLines = labelLineCount,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(2.dp))
                        innerTextField()
                    }
                }
            },
        )
    }
}

@Composable
private fun DataSourceRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
) {
    val adaptiveLayout = currentAdaptiveLayout()
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(if (adaptiveLayout.isTablet) 20.dp else 16.dp))
                .background(SettingsInputBg)
                .border(
                    1.dp,
                    SettingsInputBorder,
                    RoundedCornerShape(if (adaptiveLayout.isTablet) 20.dp else 16.dp),
                ).padding(if (adaptiveLayout.isTablet) 16.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                title,
                fontWeight = FontWeight.ExtraBold,
                fontSize = if (adaptiveLayout.isTablet) 18.sp else 15.sp,
                color = SettingsSectionTitle,
            )
            Text(
                subtitle,
                fontSize = if (adaptiveLayout.isTablet) 15.sp else 12.sp,
                color = SettingsSubtitle,
            )
        }
    }
}
