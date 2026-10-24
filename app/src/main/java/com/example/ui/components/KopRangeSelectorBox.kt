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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.ActiveRangeItem
import com.example.service.UnixSmsManager
import com.example.util.AllCountriesProvider
import com.example.util.ClipboardHelper

data class PresetRangeEntry(
    val id: String = java.util.UUID.randomUUID().toString(),
    val countryName: String,
    val countryCode: String,
    val flag: String,
    val dialCode: String,
    val range: String,
    val tag: String,
    val hits: Int = 0,
    val isLive: Boolean = true
)

val KOP_PRESET_RANGES: List<PresetRangeEntry> = listOf(
    // Guinea
    PresetRangeEntry(countryName = "Guinea", countryCode = "GN", flag = "🇬🇳", dialCode = "+224", range = "224651XXXX", tag = "FB Clone ⚡", hits = 88),
    PresetRangeEntry(countryName = "Guinea", countryCode = "GN", flag = "🇬🇳", dialCode = "+224", range = "224620XXXX", tag = "New Fresh 🟢", hits = 62),
    PresetRangeEntry(countryName = "Guinea", countryCode = "GN", flag = "🇬🇳", dialCode = "+224", range = "224664XXXX", tag = "PC Clone 🖥️", hits = 45),

    // Sudan
    PresetRangeEntry(countryName = "Sudan", countryCode = "SD", flag = "🇸🇩", dialCode = "+249", range = "249912XXXX", tag = "FB Clone ⚡", hits = 112),
    PresetRangeEntry(countryName = "Sudan", countryCode = "SD", flag = "🇸🇩", dialCode = "+249", range = "249123XXXX", tag = "PC Clone 🖥️", hits = 94),
    PresetRangeEntry(countryName = "Sudan", countryCode = "SD", flag = "🇸🇩", dialCode = "+249", range = "249900XXXX", tag = "New Fresh 🟢", hits = 56),

    // Cameroon
    PresetRangeEntry(countryName = "Cameroon", countryCode = "CM", flag = "🇨🇲", dialCode = "+237", range = "237620XXXX", tag = "FB Clone ⚡", hits = 98),
    PresetRangeEntry(countryName = "Cameroon", countryCode = "CM", flag = "🇨🇲", dialCode = "+237", range = "237626XXXX", tag = "New Fresh 🟢", hits = 73),
    PresetRangeEntry(countryName = "Cameroon", countryCode = "CM", flag = "🇨🇲", dialCode = "+237", range = "237650XXXX", tag = "PC Clone 🖥️", hits = 65),
    PresetRangeEntry(countryName = "Cameroon", countryCode = "CM", flag = "🇨🇲", dialCode = "+237", range = "237670XXXX", tag = "WhatsApp 💬", hits = 81),

    // Nigeria
    PresetRangeEntry(countryName = "Nigeria", countryCode = "NG", flag = "🇳🇬", dialCode = "+234", range = "234803XXXX", tag = "FB Clone ⚡", hits = 120),
    PresetRangeEntry(countryName = "Nigeria", countryCode = "NG", flag = "🇳🇬", dialCode = "+234", range = "234901XXXX", tag = "New Fresh 🟢", hits = 89),
    PresetRangeEntry(countryName = "Nigeria", countryCode = "NG", flag = "🇳🇬", dialCode = "+234", range = "234706XXXX", tag = "PC Clone 🖥️", hits = 58),

    // Zambia
    PresetRangeEntry(countryName = "Zambia", countryCode = "ZM", flag = "🇿🇲", dialCode = "+260", range = "260971XXXX", tag = "FB Clone ⚡", hits = 77),
    PresetRangeEntry(countryName = "Zambia", countryCode = "ZM", flag = "🇿🇲", dialCode = "+260", range = "260762XXXX", tag = "OTP Work 🔥", hits = 64),

    // Bolivia
    PresetRangeEntry(countryName = "Bolivia", countryCode = "BO", flag = "🇧🇴", dialCode = "+591", range = "591700XXXX", tag = "FB Clone ⚡", hits = 83),
    PresetRangeEntry(countryName = "Bolivia", countryCode = "BO", flag = "🇧🇴", dialCode = "+591", range = "591715XXXX", tag = "New Fresh 🟢", hits = 51),

    // Zimbabwe
    PresetRangeEntry(countryName = "Zimbabwe", countryCode = "ZW", flag = "🇿🇼", dialCode = "+263", range = "263771XXXX", tag = "New Fresh 🟢", hits = 42),

    // Kenya
    PresetRangeEntry(countryName = "Kenya", countryCode = "KE", flag = "🇰🇪", dialCode = "+254", range = "254712XXXX", tag = "WhatsApp 💬", hits = 68),
    PresetRangeEntry(countryName = "Kenya", countryCode = "KE", flag = "🇰🇪", dialCode = "+254", range = "254708XXXX", tag = "FB Clone ⚡", hits = 59),

    // South Africa
    PresetRangeEntry(countryName = "South Africa", countryCode = "ZA", flag = "🇿🇦", dialCode = "+27", range = "278212XXXX", tag = "OTP Work 🔥", hits = 71),

    // Bangladesh
    PresetRangeEntry(countryName = "Bangladesh", countryCode = "BD", flag = "🇧🇩", dialCode = "+880", range = "8801700XXXX", tag = "New Fresh 🟢", hits = 92),
    PresetRangeEntry(countryName = "Bangladesh", countryCode = "BD", flag = "🇧🇩", dialCode = "+880", range = "8801819XXXX", tag = "FB Clone ⚡", hits = 85),

    // India
    PresetRangeEntry(countryName = "India", countryCode = "IN", flag = "🇮🇳", dialCode = "+91", range = "919810XXXX", tag = "FB Clone ⚡", hits = 105),

    // Indonesia & Philippines
    PresetRangeEntry(countryName = "Indonesia", countryCode = "ID", flag = "🇮🇩", dialCode = "+62", range = "628123XXXX", tag = "FB Clone ⚡", hits = 79),
    PresetRangeEntry(countryName = "Philippines", countryCode = "PH", flag = "🇵🇭", dialCode = "+63", range = "639171XXXX", tag = "FB Clone ⚡", hits = 74),

    // Egypt
    PresetRangeEntry(countryName = "Egypt", countryCode = "EG", flag = "🇪🇬", dialCode = "+20", range = "201001XXXX", tag = "New Fresh 🟢", hits = 53),

    // Global
    PresetRangeEntry(countryName = "United States", countryCode = "US", flag = "🇺🇸", dialCode = "+1", range = "1202555XXXX", tag = "Global Live 🌐", hits = 60),
    PresetRangeEntry(countryName = "United Kingdom", countryCode = "GB", flag = "🇬🇧", dialCode = "+44", range = "447911XXXX", tag = "Global Live 🌐", hits = 67)
)

@Composable
fun KopRangeSelectorBox(
    title: String = "TARGET RANGE / PRESET COUNTRY",
    currentRange: String,
    onRangeSelected: (String) -> Unit,
    activeRanges: List<ActiveRangeItem> = emptyList(),
    accentColor: Color = Color(0xFF00B0FF),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val unixState by UnixSmsManager.state.collectAsState()
    var showRangeDropdownModal by remember { mutableStateOf(false) }
    var searchFilter by remember { mutableStateOf("") }
    var selectedTagFilter by remember { mutableStateOf("ALL") }

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
                            text = if (currentRange.isNotBlank()) currentRange else "রেঞ্জ সিলেক্ট করুন বা পেস্ট করুন...",
                            color = if (currentRange.isNotBlank()) accentColor else Color(0xFF64748B),
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
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "রেঞ্জ লিস্ট ▾",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 3. Direct Editable TextField for manual custom range entry with real-time auto detection
        OutlinedTextField(
            value = currentRange,
            onValueChange = { input ->
                onRangeSelected(input)
            },
            placeholder = { Text("অথবা যেকোনো দেশের রেঞ্জ/নাম্বার পেস্ট করুন (e.g. 224651XXXX)...", color = Color(0xFF546E7A), fontSize = 11.sp) },
            leadingIcon = {
                Icon(Icons.Default.Public, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
            },
            trailingIcon = {
                if (currentRange.isNotBlank()) {
                    IconButton(
                        onClick = { onRangeSelected("") },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color(0xFF78909C), modifier = Modifier.size(16.dp))
                    }
                }
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

    // 4. Crash-Proof Full-Featured Range Selection Modal Dialog
    if (showRangeDropdownModal) {
        val allEntries = remember(activeRanges, unixState.uploadedNumbers) {
            val list = mutableListOf<PresetRangeEntry>()
            val seenRanges = mutableSetOf<String>()

            // 1. Add active live pool ranges from server (Zenex / Kop Engine live pool)
            activeRanges.forEach { act ->
                val cleanR = act.range.trim()
                if (cleanR.isNotBlank() && !seenRanges.contains(cleanR.uppercase())) {
                    seenRanges.add(cleanR.uppercase())
                    val c = AllCountriesProvider.detectCountry(cleanR)
                    val cFlag = c?.flag ?: "⚡"
                    val cName = c?.name ?: "Signal"
                    val cCode = (c?.code ?: "UN").uppercase()
                    val cDial = c?.dialCode ?: ""

                    val tagLabel = when {
                        act.service.contains("FB", ignoreCase = true) || act.service.contains("Face", ignoreCase = true) -> "FB Clone ⚡"
                        act.tag.contains("Fresh", ignoreCase = true) || act.tag.contains("New", ignoreCase = true) -> "New Fresh 🟢"
                        act.tag.contains("PC", ignoreCase = true) || act.tag.contains("Clone", ignoreCase = true) -> "PC Clone 🖥️"
                        act.service.contains("WhatsApp", ignoreCase = true) -> "WhatsApp 💬"
                        act.service.contains("OTP", ignoreCase = true) -> "OTP Work 🔥"
                        else -> if (act.tag.isNotBlank()) "${act.service} • ${act.tag}" else act.service
                    }

                    list.add(
                        PresetRangeEntry(
                            countryName = cName,
                            countryCode = cCode,
                            flag = cFlag,
                            dialCode = cDial,
                            range = cleanR,
                            tag = tagLabel,
                            hits = if (act.hits > 0) act.hits else 48
                        )
                    )
                }
            }

            // 2. Add ranges from Admin Uploaded Numbers pool (Unix SMS)
            unixState.uploadedNumbers.forEach { item ->
                val digits = item.number.replace("[^0-9]".toRegex(), "")
                if (digits.length >= 6) {
                    val prefix = digits.take(6) + "XXXX"
                    if (!seenRanges.contains(prefix.uppercase())) {
                        seenRanges.add(prefix.uppercase())
                        val c = AllCountriesProvider.detectCountry(digits)
                        val cFlag = c?.flag ?: item.flag
                        val cName = c?.name ?: item.countryName
                        val cCode = c?.code ?: item.countryCode
                        val cDial = c?.dialCode ?: "+${digits.take(3)}"

                        val isPc = item.numberType.equals("PC Clone", ignoreCase = true)
                        val tagLabel = when {
                            item.service.contains("Facebook", ignoreCase = true) && isPc -> "FB PC Clone 🖥️"
                            item.service.contains("Facebook", ignoreCase = true) -> "FB Clone ⚡"
                            item.service.contains("OTP", ignoreCase = true) -> "OTP Work 🔥"
                            item.service.contains("WhatsApp", ignoreCase = true) -> "WhatsApp 💬"
                            isPc -> "PC Clone 🖥️"
                            else -> "New Fresh 🟢"
                        }

                        list.add(
                            PresetRangeEntry(
                                countryName = cName,
                                countryCode = cCode,
                                flag = cFlag,
                                dialCode = cDial,
                                range = prefix,
                                tag = tagLabel,
                                hits = 75
                            )
                        )
                    }
                }
            }

            // 3. Add Curated Live Preset Ranges (with FB Clone, New Fresh, PC Clone, etc.)
            KOP_PRESET_RANGES.forEach { p ->
                val cleanR = p.range.trim().uppercase()
                if (!seenRanges.contains(cleanR)) {
                    seenRanges.add(cleanR)
                    list.add(p)
                }
            }

            list
        }

        val filteredEntries = remember(allEntries, searchFilter, selectedTagFilter) {
            var result: List<PresetRangeEntry> = allEntries
            if (selectedTagFilter != "ALL") {
                result = when (selectedTagFilter) {
                    "FB_CLONE" -> result.filter { it.tag.contains("FB", ignoreCase = true) || it.tag.contains("Clone", ignoreCase = true) }
                    "NEW_FRESH" -> result.filter { it.tag.contains("New", ignoreCase = true) || it.tag.contains("Fresh", ignoreCase = true) }
                    "PC_CLONE" -> result.filter { it.tag.contains("PC", ignoreCase = true) }
                    "WHATSAPP" -> result.filter { it.tag.contains("WhatsApp", ignoreCase = true) }
                    "OTP_WORK" -> result.filter { it.tag.contains("OTP", ignoreCase = true) }
                    else -> result
                }
            }

            if (searchFilter.isNotBlank()) {
                val q = searchFilter.trim().lowercase()
                result = result.filter {
                    it.countryName.lowercase().contains(q) ||
                    it.countryCode.lowercase().contains(q) ||
                    it.range.lowercase().contains(q) ||
                    it.dialCode.replace("+", "").contains(q) ||
                    it.tag.lowercase().contains(q)
                }
            }

            result
        }

        Dialog(
            onDismissRequest = { showRangeDropdownModal = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .heightIn(max = 600.dp)
                    .padding(vertical = 12.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF09131F)),
                border = BorderStroke(1.2.dp, accentColor.copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ElectricBolt, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = "লাইভ রেঞ্জ ও কান্ট্রি পুল (LIVE POOL)",
                                    color = Color.White,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "যেকোনো রেঞ্জে ১-ট্যাপে সিলেক্ট বা অটো কপি করুন",
                                    color = Color(0xFF90A4AE),
                                    fontSize = 10.sp
                                )
                            }
                        }

                        IconButton(
                            onClick = { showRangeDropdownModal = false },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF90A4AE), modifier = Modifier.size(18.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Quick Filter Chips (All, FB Clone, New Fresh, PC Clone, WhatsApp, OTP Work)
                    val filterChips = listOf(
                        "ALL" to "🌐 All (${allEntries.size})",
                        "FB_CLONE" to "⚡ FB Clone",
                        "NEW_FRESH" to "🟢 New Fresh",
                        "PC_CLONE" to "🖥️ PC Clone",
                        "WHATSAPP" to "💬 WhatsApp",
                        "OTP_WORK" to "🔥 OTP Work"
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(filterChips.size) { idx ->
                            val chip = filterChips[idx]
                            val isChipSelected = selectedTagFilter == chip.first
                            Surface(
                                color = if (isChipSelected) accentColor.copy(alpha = 0.25f) else Color(0xFF131F2E),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, if (isChipSelected) accentColor else Color(0xFF1F354D)),
                                modifier = Modifier.clickable { selectedTagFilter = chip.first }
                            ) {
                                Text(
                                    text = chip.second,
                                    color = if (isChipSelected) Color.White else Color(0xFF94A3B8),
                                    fontSize = 10.5.sp,
                                    fontWeight = if (isChipSelected) FontWeight.Bold else FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Search Field
                    OutlinedTextField(
                        value = searchFilter,
                        onValueChange = { searchFilter = it },
                        placeholder = { Text("দেশ বা রেঞ্জ খুঁজুন (e.g. Guinea, 224, Sudan, Cameroon)...", fontSize = 11.sp, color = Color(0xFF64748B)) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                        },
                        trailingIcon = {
                            if (searchFilter.isNotBlank()) {
                                IconButton(onClick = { searchFilter = "" }, modifier = Modifier.size(20.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color(0xFF78909C), modifier = Modifier.size(14.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = Color(0xFF1E3A56),
                            focusedContainerColor = Color(0xFF070E18),
                            unfocusedContainerColor = Color(0xFF070E18)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "রেজাল্ট: ${filteredEntries.size} টি লাইভ পুল রেঞ্জ পাওয়া গেছে",
                        color = Color(0xFF64748B),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Safe LazyColumn with 100% collision-free indexed keys
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        itemsIndexed(
                            items = filteredEntries,
                            key = { index, item -> "${item.range}_${item.tag}_$index" }
                        ) { _, entry ->
                            val isSelected = entry.range.equals(currentRange.trim(), ignoreCase = true)

                            // Tag Color Logic
                            val tagBg = when {
                                entry.tag.contains("FB", ignoreCase = true) -> Color(0xFF1877F2).copy(alpha = 0.2f)
                                entry.tag.contains("Fresh", ignoreCase = true) || entry.tag.contains("New", ignoreCase = true) -> Color(0xFF00E676).copy(alpha = 0.2f)
                                entry.tag.contains("PC", ignoreCase = true) -> Color(0xFFFF9100).copy(alpha = 0.2f)
                                entry.tag.contains("WhatsApp", ignoreCase = true) -> Color(0xFF25D366).copy(alpha = 0.2f)
                                entry.tag.contains("OTP", ignoreCase = true) -> Color(0xFFFF3D00).copy(alpha = 0.2f)
                                else -> Color(0xFF00E5FF).copy(alpha = 0.15f)
                            }

                            val tagFg = when {
                                entry.tag.contains("FB", ignoreCase = true) -> Color(0xFF60A5FA)
                                entry.tag.contains("Fresh", ignoreCase = true) || entry.tag.contains("New", ignoreCase = true) -> Color(0xFF00E676)
                                entry.tag.contains("PC", ignoreCase = true) -> Color(0xFFFFB74D)
                                entry.tag.contains("WhatsApp", ignoreCase = true) -> Color(0xFF4ADE80)
                                entry.tag.contains("OTP", ignoreCase = true) -> Color(0xFFFF6E40)
                                else -> Color(0xFF38BDF8)
                            }

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
                                        Text(text = entry.flag, fontSize = 20.sp)
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
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(top = 2.dp)
                                            ) {
                                                Text(
                                                    text = entry.range,
                                                    color = if (isSelected) accentColor else Color(0xFF81D4FA),
                                                    fontSize = 12.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = FontFamily.Monospace
                                                )

                                                Spacer(modifier = Modifier.width(6.dp))

                                                // Distinctive Tag Badge
                                                Surface(
                                                    color = tagBg,
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = entry.tag,
                                                        color = tagFg,
                                                        fontSize = 8.5.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (entry.hits > 0) {
                                            Surface(
                                                color = Color(0xFF0284C7).copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "${entry.hits} Hits",
                                                    color = Color(0xFF38BDF8),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                        }

                                        IconButton(
                                            onClick = {
                                                ClipboardHelper.copyToClipboard(context, entry.range, "Range")
                                                Toast.makeText(context, "কপি হয়েছে: ${entry.range}", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.ContentCopy,
                                                contentDescription = "Copy",
                                                tint = Color(0xFF90A4AE),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }

                                        if (isSelected) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Surface(
                                                color = accentColor,
                                                shape = CircleShape,
                                                modifier = Modifier.size(18.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(12.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
