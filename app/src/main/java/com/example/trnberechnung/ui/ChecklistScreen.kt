package com.example.trnberechnung.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.trnberechnung.model.ChecklistItem
import com.example.trnberechnung.model.ChecklistItemType
import com.example.trnberechnung.ui.theme.*
import com.example.trnberechnung.viewmodel.TideViewModel

// ══════════════════════════════════════════════════════
// Colors matching LogbookScreen
// ══════════════════════════════════════════════════════
private val CheckCardBg = Color(0xFF1B2838)
private val CheckFieldBg = Color(0xFF162030)
private val CheckFieldBorder = Color(0xFF2A3A4E)
private val CheckAccent = Color(0xFF00BFA6)

@Composable
fun ChecklistContent(
    tripId: Int,
    viewModel: TideViewModel
) {
    // Cache the flow reference so it survives recomposition
    val flow = remember(tripId) { viewModel.getChecklistForTrip(tripId) }
    val items by flow.collectAsState()
    var initialized by remember(tripId) { mutableStateOf(false) }

    LaunchedEffect(tripId) {
        viewModel.initializeChecklist(tripId)
        initialized = true
    }

    if (!initialized || items.isEmpty()) {
        if (!initialized) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = NauticalPrimary, strokeWidth = 2.dp)
            }
        }
        return
    }

    val grouped = remember(items) { items.groupBy { it.category } }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        grouped.forEach { (category, categoryItems) ->
            ChecklistCategoryCard(
                category = category,
                items = categoryItems,
                onItemUpdated = { viewModel.updateChecklistItem(it) }
            )
        }
    }
}

@Composable
private fun ChecklistCategoryCard(
    category: String,
    items: List<ChecklistItem>,
    onItemUpdated: (ChecklistItem) -> Unit
) {
    val checkedCount = items.count {
        it.type == ChecklistItemType.CHECK.name && it.isChecked
    }
    val checkTotal = items.count { it.type == ChecklistItemType.CHECK.name }
    val progress = if (checkTotal > 0) checkedCount.toFloat() / checkTotal else 1f

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CheckCardBg),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Category header with progress
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val icon = when {
                    category.contains("Crew") -> "👥"
                    category.contains("Technik") -> "🔧"
                    category.contains("Navigation") -> "🧭"
                    category.contains("Wetter") -> "⛅"
                    category.contains("Boot") -> "⛵"
                    else -> "📋"
                }
                Text(icon, fontSize = 18.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    category.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = NauticalTextSecondary,
                    letterSpacing = 1.sp,
                    modifier = Modifier.weight(1f)
                )
                if (checkTotal > 0) {
                    Text(
                        "$checkedCount/$checkTotal",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (checkedCount == checkTotal) NauticalGo else NauticalTextSecondary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (checkTotal > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                val progressColor by animateColorAsState(
                    targetValue = if (checkedCount == checkTotal) NauticalGo else CheckAccent,
                    label = "progress"
                )
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = progressColor,
                    trackColor = CheckFieldBorder
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            items.forEach { item ->
                if (item.type == ChecklistItemType.CHECK.name) {
                    CheckItemRow(item = item, onToggle = {
                        onItemUpdated(item.copy(isChecked = !item.isChecked))
                    })
                } else {
                    // Hide "Aufbauhöhe (m)" text field unless its checkbox is checked
                    val isAufbauhoehe = item.label.contains("Aufbauhöhe", ignoreCase = true)
                    val parentChecked = if (isAufbauhoehe) {
                        items.any { it.type == ChecklistItemType.CHECK.name
                            && it.label.contains("Aufbauhöhe", ignoreCase = true)
                            && it.isChecked }
                    } else true

                    if (parentChecked) {
                        TextItemRow(item = item, onTextChanged = { newText ->
                            onItemUpdated(item.copy(textValue = newText))
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckItemRow(item: ChecklistItem, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = item.isChecked,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = CheckAccent,
                uncheckedColor = NauticalTextSecondary,
                checkmarkColor = NauticalTextOnPrimary
            )
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            item.label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (item.isChecked) NauticalTextSecondary else NauticalTextPrimary,
            fontWeight = if (item.isChecked) FontWeight.Normal else FontWeight.Medium
        )
    }
}

@Composable
private fun TextItemRow(item: ChecklistItem, onTextChanged: (String) -> Unit) {
    var text by remember(item.id, item.textValue) { mutableStateOf(item.textValue) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { newVal ->
                text = newVal
                onTextChanged(newVal)
            },
            label = { Text(item.label, color = NauticalTextSecondary, fontSize = 12.sp) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = false,
            maxLines = 3,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CheckAccent,
                unfocusedBorderColor = CheckFieldBorder,
                focusedLabelColor = CheckAccent,
                unfocusedLabelColor = NauticalTextSecondary,
                cursorColor = CheckAccent,
                focusedTextColor = NauticalTextPrimary,
                unfocusedTextColor = NauticalTextPrimary
            )
        )
    }
}
