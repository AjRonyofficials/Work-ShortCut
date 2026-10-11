package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.WorkShortcutRepository
import com.example.data.local.model.ExcelRowEntity
import com.example.service.OverlayStateManager
import com.example.service.OverlayUiState
import kotlinx.coroutines.launch

@Composable
fun ExcelCollectorSection(
    state: OverlayUiState,
    savedRows: List<ExcelRowEntity>,
    repository: WorkShortcutRepository?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showPurgeConfirmDialog by remember { mutableStateOf(false) }
    var editingRow by remember { mutableStateOf<ExcelRowEntity?>(null) }
    var editColA by remember { mutableStateOf("") }
    var editColB by remember { mutableStateOf("") }
    var editColC by remember { mutableStateOf("") }
    var editColD by remember { mutableStateOf("") }
    var editColE by remember { mutableStateOf("") }
    var editColF by remember { mutableStateOf("") }

    val colCount = state.columnCount
    val sortedRows = savedRows.sortedBy { it.id }

    // Colors for columns
    val colorA = Color(0xFF4CAF50) // Green
    val colorB = Color(0xFF2196F3) // Blue
    val colorC = Color(0xFFFF9800) // Orange
    val colorD = Color(0xFF9C27B0) // Purple
    val colorE = Color(0xFF00BCD4) // Cyan
    val colorF = Color(0xFFE91E63) // Hot Pink

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("excel_collector_section"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Column Config & Row Pointers Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.TableChart,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Custom Columns & Active Rows",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Reset All Rows to 1 button
                        OutlinedButton(
                            onClick = {
                                OverlayStateManager.resetAllColumnRowsToOne()
                                Toast.makeText(context, "All column rows reset to 1", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.height(34.dp).testTag("reset_all_rows_button"),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reset to Row 1", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Column Count Selection Chips (2, 3, 4, 5, 6)
                    Text(
                        text = "Select Number of Columns for Overlay & Sheet (up to 6):",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(2, 3, 4, 5, 6).forEach { count ->
                            val label = when (count) {
                                2 -> "2 Cols (A, B)"
                                3 -> "3 Cols (A-C)"
                                4 -> "4 Cols (A-D)"
                                5 -> "5 Cols (A-E)"
                                else -> "6 Cols (A-F)"
                            }
                            FilterChip(
                                selected = colCount == count,
                                onClick = {
                                    OverlayStateManager.setColumnCount(count)
                                    Toast.makeText(context, "Set to $count columns", Toast.LENGTH_SHORT).show()
                                },
                                label = { Text(label, fontSize = 12.sp) },
                                leadingIcon = if (colCount == count) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Active Row Pointers Row
                    Text(
                        text = "Current Row on Overlay Buttons (Auto-advances on paste):",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val activeCols = when (colCount) {
                            2 -> listOf("A", "B")
                            3 -> listOf("A", "B", "C")
                            4 -> listOf("A", "B", "C", "D")
                            5 -> listOf("A", "B", "C", "D", "E")
                            else -> listOf("A", "B", "C", "D", "E", "F")
                        }

                        activeCols.forEach { colKey ->
                            val rowNum = state.columnRowMap[colKey] ?: 1
                            val badgeColor = when (colKey) {
                                "A" -> colorA
                                "B" -> colorB
                                "C" -> colorC
                                "D" -> colorD
                                "E" -> colorE
                                else -> colorF
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = badgeColor.copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor.copy(alpha = 0.5f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "Col $colKey",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = badgeColor
                                    )
                                    Text(
                                        text = "Row $rowNum",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Duplicate Alert Banner (if duplicate detected)
        item {
            AnimatedVisibility(visible = state.duplicateHighlightRow != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("duplicate_alert_banner"),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFFEBEE)
                    ),
                    border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFD32F2F))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFD32F2F),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "DUPLICATE TEXT DETECTED!",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 15.sp,
                                    color = Color(0xFFB71C1C)
                                )
                            }
                            IconButton(
                                onClick = { OverlayStateManager.clearDuplicateHighlight() },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = Color(0xFFD32F2F)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Same text already exists in Row #${state.duplicateHighlightRow} (Column ${state.duplicateHighlightCol ?: ""})",
                            fontSize = 13.sp,
                            color = Color(0xFFC62828),
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "\"${state.duplicateText ?: ""}\"",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFB71C1C),
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(
                                onClick = {
                                    val targetRow = sortedRows.find { it.id == state.duplicateHighlightRow }
                                    if (targetRow != null) {
                                        editingRow = targetRow
                                        editColA = targetRow.colA
                                        editColB = targetRow.colB
                                        editColC = targetRow.colC
                                        editColD = targetRow.colD
                                        editColE = targetRow.colE
                                        editColF = targetRow.colF
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(36.dp).testTag("edit_duplicate_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Edit Duplicate Row", color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // 3. Fast Copy Column Action Bar (Horizontal Scrollable)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "⚡ 1-Tap Copy Columns & Export",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Copy Column A
                        val countA = sortedRows.count { it.colA.isNotBlank() }
                        Button(
                            onClick = {
                                OverlayStateManager.copyColumnRecords(context, "A", sortedRows)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = colorA),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("copy_col_a_button")
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy Col A ($countA)", fontWeight = FontWeight.Bold)
                        }

                        // Copy Column B
                        val countB = sortedRows.count { it.colB.isNotBlank() }
                        Button(
                            onClick = {
                                OverlayStateManager.copyColumnRecords(context, "B", sortedRows)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = colorB),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("copy_col_b_button")
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy Col B ($countB)", fontWeight = FontWeight.Bold)
                        }

                        // Copy Column C (if 3+ columns)
                        if (colCount >= 3) {
                            val countC = sortedRows.count { it.colC.isNotBlank() }
                            Button(
                                onClick = {
                                    OverlayStateManager.copyColumnRecords(context, "C", sortedRows)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = colorC),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("copy_col_c_button")
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Copy Col C ($countC)", fontWeight = FontWeight.Bold)
                            }
                        }

                        // Copy Column D (if 4 columns)
                        if (colCount >= 4) {
                            val countD = sortedRows.count { it.colD.isNotBlank() }
                            Button(
                                onClick = {
                                    OverlayStateManager.copyColumnRecords(context, "D", sortedRows)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = colorD),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("copy_col_d_button")
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Copy Col D ($countD)", fontWeight = FontWeight.Bold)
                            }
                        }

                        // Copy Column E (if 5+ columns)
                        if (colCount >= 5) {
                            val countE = sortedRows.count { it.colE.isNotBlank() }
                            Button(
                                onClick = {
                                    OverlayStateManager.copyColumnRecords(context, "E", sortedRows)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = colorE),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("copy_col_e_button")
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Copy Col E ($countE)", fontWeight = FontWeight.Bold)
                            }
                        }

                        // Copy Column F (if 6 columns)
                        if (colCount >= 6) {
                            val countF = sortedRows.count { it.colF.isNotBlank() }
                            Button(
                                onClick = {
                                    OverlayStateManager.copyColumnRecords(context, "F", sortedRows)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = colorF),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("copy_col_f_button")
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Copy Col F ($countF)", fontWeight = FontWeight.Bold)
                            }
                        }

                        // Copy Entire Table (Excel TSV)
                        OutlinedButton(
                            onClick = {
                                OverlayStateManager.copyAllRowsAsCsv(context, sortedRows)
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("copy_all_tsv_button")
                        ) {
                            Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy All (Excel Table)", fontWeight = FontWeight.Bold)
                        }

                        // Purge/Clear All Data
                        OutlinedButton(
                            onClick = { showPurgeConfirmDialog = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("clear_all_rows_button")
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Clear All")
                        }
                    }
                }
            }
        }

        // 4. Stored Sheet Table Header & List
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Stored Rows in Serial (${sortedRows.size} total)",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )

                // Quick Add Row button
                TextButton(
                    onClick = {
                        val nextId = (sortedRows.maxOfOrNull { it.id } ?: 0L) + 1L
                        val newRow = ExcelRowEntity(id = nextId)
                        scope.launch { repository?.insertExcelRow(newRow) }
                        editingRow = newRow
                        editColA = ""
                        editColB = ""
                        editColC = ""
                        editColD = ""
                        editColE = ""
                        editColF = ""
                    }
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Row")
                }
            }
        }

        if (sortedRows.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.TableChart,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No data rows collected yet!",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Copy text on your phone and tap A1 or B1 on the Floating Overlay to auto-paste and collect serial data!",
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(sortedRows, key = { it.id }) { row ->
                val isHighlighted = row.id == state.duplicateHighlightRow || row.hasDuplicateWarning
                val borderColor = if (isHighlighted) Color(0xFFE53935) else MaterialTheme.colorScheme.outlineVariant
                val containerColor = if (isHighlighted) Color(0xFFFFEBEE) else MaterialTheme.colorScheme.surface

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = if (isHighlighted) 2.dp else 1.dp,
                            color = borderColor,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .testTag("sheet_row_${row.id}"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = containerColor)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isHighlighted) Color(0xFFD32F2F) else MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = "ROW #${row.id}",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 12.sp,
                                        color = if (isHighlighted) Color.White else MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }

                                if (isHighlighted) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFFFFCDD2)
                                    ) {
                                        Text(
                                            text = "⚠️ DUPLICATE",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = Color(0xFFB71C1C),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            Row {
                                // Edit Row Button
                                IconButton(
                                    onClick = {
                                        editingRow = row
                                        editColA = row.colA
                                        editColB = row.colB
                                        editColC = row.colC
                                        editColD = row.colD
                                        editColE = row.colE
                                        editColF = row.colF
                                    },
                                    modifier = Modifier.size(32.dp).testTag("edit_row_${row.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit Row",
                                        tint = if (isHighlighted) Color(0xFFD32F2F) else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // Delete Row Button
                                IconButton(
                                    onClick = {
                                        scope.launch { repository?.deleteExcelRow(row) }
                                        if (row.id == state.duplicateHighlightRow) {
                                            OverlayStateManager.clearDuplicateHighlight()
                                        }
                                        Toast.makeText(context, "Row #${row.id} deleted", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(32.dp).testTag("delete_row_${row.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Row",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Column Values Grid
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            // Col A
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "A: ",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = colorA,
                                    modifier = Modifier.width(24.dp)
                                )
                                Text(
                                    text = if (row.colA.isNotEmpty()) row.colA else "— empty —",
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (row.colA.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            if (row.colA.isNotEmpty()) {
                                                com.example.util.ClipboardHelper.copyToClipboard(context, row.colA, toastMessage = "Copied Col A: ${row.colA}")
                                            }
                                        }
                                )
                            }

                            // Col B
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "B: ",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = colorB,
                                    modifier = Modifier.width(24.dp)
                                )
                                Text(
                                    text = if (row.colB.isNotEmpty()) row.colB else "— empty —",
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (row.colB.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            if (row.colB.isNotEmpty()) {
                                                com.example.util.ClipboardHelper.copyToClipboard(context, row.colB, toastMessage = "Copied Col B: ${row.colB}")
                                            }
                                        }
                                )
                            }

                            // Col C
                            if (colCount >= 3) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "C: ",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = colorC,
                                        modifier = Modifier.width(24.dp)
                                    )
                                    Text(
                                        text = if (row.colC.isNotEmpty()) row.colC else "— empty —",
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (row.colC.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                if (row.colC.isNotEmpty()) {
                                                    com.example.util.ClipboardHelper.copyToClipboard(context, row.colC, toastMessage = "Copied Col C: ${row.colC}")
                                                }
                                            }
                                    )
                                }
                            }

                            // Col D
                            if (colCount >= 4) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "D: ",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = colorD,
                                        modifier = Modifier.width(24.dp)
                                    )
                                    Text(
                                        text = if (row.colD.isNotEmpty()) row.colD else "— empty —",
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (row.colD.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                if (row.colD.isNotEmpty()) {
                                                    com.example.util.ClipboardHelper.copyToClipboard(context, row.colD, toastMessage = "Copied Col D: ${row.colD}")
                                                }
                                            }
                                    )
                                }
                            }

                            // Col E
                            if (colCount >= 5) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "E: ",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = colorE,
                                        modifier = Modifier.width(24.dp)
                                    )
                                    Text(
                                        text = if (row.colE.isNotEmpty()) row.colE else "— empty —",
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (row.colE.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                if (row.colE.isNotEmpty()) {
                                                    com.example.util.ClipboardHelper.copyToClipboard(context, row.colE, toastMessage = "Copied Col E: ${row.colE}")
                                                }
                                            }
                                    )
                                }
                            }

                            // Col F
                            if (colCount >= 6) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "F: ",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = colorF,
                                        modifier = Modifier.width(24.dp)
                                    )
                                    Text(
                                        text = if (row.colF.isNotEmpty()) row.colF else "— empty —",
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (row.colF.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                if (row.colF.isNotEmpty()) {
                                                    com.example.util.ClipboardHelper.copyToClipboard(context, row.colF, toastMessage = "Copied Col F: ${row.colF}")
                                                }
                                            }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 5. Edit Row Dialog
    editingRow?.let { row ->
        AlertDialog(
            onDismissRequest = { editingRow = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Edit Row #${row.id}")
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = editColA,
                        onValueChange = { editColA = it },
                        label = { Text("Column A") },
                        modifier = Modifier.fillMaxWidth().testTag("edit_col_a_input"),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = editColB,
                        onValueChange = { editColB = it },
                        label = { Text("Column B") },
                        modifier = Modifier.fillMaxWidth().testTag("edit_col_b_input"),
                        singleLine = true
                    )
                    if (colCount >= 3) {
                        OutlinedTextField(
                            value = editColC,
                            onValueChange = { editColC = it },
                            label = { Text("Column C") },
                            modifier = Modifier.fillMaxWidth().testTag("edit_col_c_input"),
                            singleLine = true
                        )
                    }
                    if (colCount >= 4) {
                        OutlinedTextField(
                            value = editColD,
                            onValueChange = { editColD = it },
                            label = { Text("Column D") },
                            modifier = Modifier.fillMaxWidth().testTag("edit_col_d_input"),
                            singleLine = true
                        )
                    }
                    if (colCount >= 5) {
                        OutlinedTextField(
                            value = editColE,
                            onValueChange = { editColE = it },
                            label = { Text("Column E") },
                            modifier = Modifier.fillMaxWidth().testTag("edit_col_e_input"),
                            singleLine = true
                        )
                    }
                    if (colCount >= 6) {
                        OutlinedTextField(
                            value = editColF,
                            onValueChange = { editColF = it },
                            label = { Text("Column F") },
                            modifier = Modifier.fillMaxWidth().testTag("edit_col_f_input"),
                            singleLine = true
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val updated = row.copy(
                            colA = editColA.trim(),
                            colB = editColB.trim(),
                            colC = editColC.trim(),
                            colD = editColD.trim(),
                            colE = editColE.trim(),
                            colF = editColF.trim(),
                            hasDuplicateWarning = false,
                            duplicateDetails = ""
                        )
                        scope.launch {
                            repository?.updateExcelRow(updated)
                        }
                        if (row.id == state.duplicateHighlightRow) {
                            OverlayStateManager.clearDuplicateHighlight()
                        }
                        editingRow = null
                        Toast.makeText(context, "Row #${row.id} updated successfully!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.testTag("save_edit_row_button")
                ) {
                    Text("Save Changes")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingRow = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 6. Purge All Rows Confirmation Dialog
    if (showPurgeConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showPurgeConfirmDialog = false },
            icon = { Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Clear All Sheet Data?") },
            text = { Text("This will permanently delete all collected rows from the sheet database. Are you sure?") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch { repository?.clearAllExcelRows() }
                        OverlayStateManager.resetAllColumnRowsToOne()
                        OverlayStateManager.clearDuplicateHighlight()
                        showPurgeConfirmDialog = false
                        Toast.makeText(context, "All sheet rows cleared!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_clear_all_rows_button")
                ) {
                    Text("Clear Everything")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPurgeConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
