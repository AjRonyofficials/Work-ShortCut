package com.example.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ActiveRangeItem
import com.example.util.AllCountriesProvider
import com.example.util.ClipboardHelper

data class PresetRangeEntry(
    val countryName: String,
    val countryCode: String,
    val flag: String,
    val dialCode: String,
    val range: String,
    val tag: String
)

val KOP_PRESET_RANGES: List<PresetRangeEntry> = listOf(
    PresetRangeEntry("Cameroon", "CM", "🇨🇲", "+237", "237620XXX", "MTN Cameroon"),
    PresetRangeEntry("Cameroon", "CM", "🇨🇲", "+237", "237626XXX", "Orange Cameroon"),
    PresetRangeEntry("Cameroon", "CM", "🇨🇲", "+237", "237650XXX", "CamTel Mobile"),
    PresetRangeEntry("Cameroon", "CM", "🇨🇲", "+237", "237670XXX", "Nexttel Cameroon"),
    PresetRangeEntry("Nigeria", "NG", "🇳🇬", "+234", "234803XXX", "MTN Nigeria"),
    PresetRangeEntry("Nigeria", "NG", "🇳🇬", "+234", "234901XXX", "Airtel Nigeria"),
    PresetRangeEntry("Zambia", "ZM", "🇿🇲", "+260", "260971XXX", "Airtel Zambia"),
    PresetRangeEntry("Zambia", "ZM", "🇿🇲", "+260", "260762XXX", "MTN Zambia"),
    PresetRangeEntry("Bolivia", "BO", "🇧🇴", "+591", "591700XXX", "Entel Bolivia"),
    PresetRangeEntry("Sudan", "SD", "🇸🇩", "+249", "249912XXX", "Zain Sudan"),
    PresetRangeEntry("Zimbabwe", "ZW", "🇿🇼", "+263", "263771XXX", "Econet Wireless"),
    PresetRangeEntry("Kenya", "KE", "🇰🇪", "+254", "254712XXX", "Safaricom"),
    PresetRangeEntry("South Africa", "ZA", "🇿🇦", "+27", "278212XXX", "Vodacom"),
    PresetRangeEntry("Bangladesh", "BD", "🇧🇩", "+880", "8801700XXX", "Grameenphone"),
    PresetRangeEntry("United States", "US", "🇺🇸", "+1", "1202555XXX", "US Mobile"),
    PresetRangeEntry("United Kingdom", "GB", "🇬🇧", "+44", "447911XXX", "Vodafone UK")
)

@Composable
fun KopRangeSelectorBox(
    title: String = "TARGET RANGE / COUNTRY",
    currentRange: String,
    onRangeSelected: (String) -> Unit,
    activeRanges: List<ActiveRangeItem> = emptyList(),
    accentColor: Color = Color(0xFF00B0FF),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showRangeDropdownModal by remember { mutableStateOf(false) }
    var searchFilter by remember { mutableStateOf("") }

    val detectedCountry = remember(currentRange) {
        AllCountriesProvider.detectCountry(currentRange)
    }

    val flag = detectedCountry?.flag ?: "🌐"
    val countryName = detectedCountry?.name ?: "Global"
    val isoShort = (detectedCountry?.code ?: "UN").uppercase()
    val dialCode = detectedCountry?.dialCode ?: ""

    Column(modifier = modifier.fillMaxWidth()) {
        // 1. Header with Title & Auto-Detected Country Pill
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = Color(0xFF90A4AE),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )

            // Auto-Detected Country Badge
            Surface(
                color = accentColor.copy(alpha = 0.18f),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.55f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = flag, fontSize = 11.5.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$countryName [$isoShort] $dialCode",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 2. High-End Range Dropdown Selector Box (Interactive Card)
        Surface(
            color = Color(0xFF0B1726),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.2.dp, accentColor.copy(alpha = 0.7f)),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showRangeDropdownModal = true }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Surface(
                        color = accentColor.copy(alpha = 0.2f),
                        shape = CircleShape,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(text = flag, fontSize = 16.sp)
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "$countryName [$isoShort]",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            if (dialCode.isNotBlank()) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "($dialCode)",
                                    color = accentColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Text(
                            text = if (currentRange.isNotBlank()) currentRange else "Select a Range...",
                            color = accentColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Surface(
                    color = accentColor.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(0.8.dp, accentColor)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "রেঞ্জ লিস্ট ▾",
                            color = Color.White,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 3. Direct Editable TextField for manual custom range entry
        OutlinedTextField(
            value = currentRange,
            onValueChange = onRangeSelected,
            placeholder = { Text("অথবা সরাসরি টাইপ করুন (e.g. 237620XXX)...", color = Color(0xFF546E7A), fontSize = 11.5.sp) },
            leadingIcon = {
                Icon(Icons.Default.Public, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = accentColor,
                unfocusedBorderColor = Color(0xFF1E3A56),
                focusedContainerColor = Color(0xFF09121D),
                unfocusedContainerColor = Color(0xFF09121D)
            ),
            shape = RoundedCornerShape(10.dp),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }

    // 4. Beautiful Range Selection Modal Dialog
    if (showRangeDropdownModal) {
        val allEntries = remember(activeRanges) {
            val list = mutableListOf<PresetRangeEntry>()
            // 1. Add server dynamic active ranges first with detected country
            activeRanges.forEach { act ->
                val c = AllCountriesProvider.detectCountry(act.range)
                val cFlag = c?.flag ?: "⚡"
                val cName = c?.name ?: "Signal"
                val cCode = (c?.code ?: "UN").uppercase()
                val cDial = c?.dialCode ?: ""
                list.add(
                    PresetRangeEntry(
                        countryName = cName,
                        countryCode = cCode,
                        flag = cFlag,
                        dialCode = cDial,
                        range = act.range,
                        tag = if (act.hits > 0) "${act.service} • ${act.hits} Hits" else act.service
                    )
                )
            }
            // 2. Add standard curated preset ranges if not already present
            KOP_PRESET_RANGES.forEach { p ->
                if (list.none { it.range.equals(p.range, ignoreCase = true) }) {
                    list.add(p)
                }
            }
            list
        }

        val filteredEntries = remember(allEntries, searchFilter) {
            if (searchFilter.isBlank()) allEntries else {
                val q = searchFilter.trim().lowercase()
                allEntries.filter {
                    it.countryName.lowercase().contains(q) ||
                    it.countryCode.lowercase().contains(q) ||
                    it.range.lowercase().contains(q) ||
                    it.dialCode.replace("+", "").contains(q) ||
                    it.tag.lowercase().contains(q)
                }
            }
        }

        AlertDialog(
            onDismissRequest = { showRangeDropdownModal = false },
            containerColor = Color(0xFF0C1624),
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "🌍 কান্ট্রি ও রেঞ্জ লিস্ট নির্বাচন করুন",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "যে রেঞ্জে কাজ করবেন তা এক ট্যাপে সিলেক্ট করুন:",
                            color = Color(0xFF90A4AE),
                            fontSize = 10.sp
                        )
                    }
                    IconButton(
                        onClick = { showRangeDropdownModal = false },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF90A4AE))
                    }
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Search in dialog
                    OutlinedTextField(
                        value = searchFilter,
                        onValueChange = { searchFilter = it },
                        placeholder = { Text("দেশ বা রেঞ্জ খুঁজুন (e.g. Cameroon, 237, Nigeria)...", fontSize = 11.sp, color = Color(0xFF64748B)) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = Color(0xFF1E3A56),
                            focusedContainerColor = Color(0xFF080F18),
                            unfocusedContainerColor = Color(0xFF080F18)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(340.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredEntries, key = { it.countryCode + "_" + it.range }) { entry ->
                            val isSelected = entry.range.equals(currentRange, ignoreCase = true)
                            Surface(
                                color = if (isSelected) accentColor.copy(alpha = 0.22f) else Color(0xFF0E1A29),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) accentColor else Color(0xFF1E3A56)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onRangeSelected(entry.range)
                                        ClipboardHelper.copyToClipboard(context, entry.range, "Range")
                                        Toast.makeText(context, "✓ সিলেক্ট হয়েছে: ${entry.countryName} (${entry.range})", Toast.LENGTH_SHORT).show()
                                        showRangeDropdownModal = false
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 9.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Text(text = entry.flag, fontSize = 18.sp)
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "${entry.countryName} [${entry.countryCode}]",
                                                    color = Color.White,
                                                    fontSize = 12.5.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = entry.dialCode,
                                                    color = accentColor,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = entry.range,
                                                    color = if (isSelected) accentColor else Color(0xFF81D4FA),
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                                if (entry.tag.isNotBlank()) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "• ${entry.tag}",
                                                        color = Color(0xFF90A4AE),
                                                        fontSize = 10.sp
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (isSelected) {
                                        Surface(
                                            color = accentColor,
                                            shape = CircleShape,
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(13.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }
}
