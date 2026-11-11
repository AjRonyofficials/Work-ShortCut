package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Female
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Male
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Wc
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
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.OverlayStateManager
import com.example.service.OverlayUiState
import com.example.ui.theme.BrandBlue
import com.example.ui.theme.BrandGreen
import com.example.util.ClipboardHelper
import com.example.util.Gender
import com.example.util.NameGenerator
import com.example.util.VibrationHelper

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NameGeneratorSection(
    state: OverlayUiState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val recentGeneratedNames = remember { mutableStateListOf<String>() }
    var showSearchDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var sampleRefreshKey by remember { mutableIntStateOf(0) }
    var customPassDraft by remember(state.savedPasswordText) { mutableStateOf(state.savedPasswordText) }
    val activeCountry = NameGenerator.getCountryOption(state.selectedCountry)

    val cardBgColor = Color(0xFF0D1726)
    val innerBoxBgColor = Color(0xFF08101C)
    val cardBorderColor = Color(0xFF1C2E4A)
    val cyanAccent = Color(0xFF00E5FF)
    val neonGreen = Color(0xFF00E676)
    val purpleAccent = Color(0xFFA855F7)

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color(0xFFE2E8F0),
        focusedContainerColor = innerBoxBgColor,
        unfocusedContainerColor = innerBoxBgColor,
        focusedBorderColor = cyanAccent,
        unfocusedBorderColor = Color(0xFF233856),
        focusedLabelColor = cyanAccent,
        unfocusedLabelColor = Color(0xFF94A3B8),
        cursorColor = cyanAccent
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("name_generator_section"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Section Header Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = cardBgColor),
            border = BorderStroke(1.2.dp, cardBorderColor)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(BrandBlue, cyanAccent)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Name & Password Generator",
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "First Name • Last Name • Full Name • Strong Password",
                            fontSize = 11.5.sp,
                            color = cyanAccent,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    color = neonGreen.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, neonGreen.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "${activeCountry.flag} ${activeCountry.code}",
                        color = neonGreen,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // 2. Active Country & Quick Selector Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("active_country_card"),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = cardBgColor),
            border = BorderStroke(1.2.dp, cyanAccent.copy(alpha = 0.45f))
        ) {
            Column(modifier = Modifier.padding(15.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = activeCountry.flag,
                            fontSize = 34.sp,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = activeCountry.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = cyanAccent.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(0.8.dp, cyanAccent)
                                ) {
                                    Text(
                                        text = activeCountry.code,
                                        color = cyanAccent,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "✓ Floating Overlay ও অ্যাপে সক্রিয় দেশ",
                                fontSize = 11.sp,
                                color = neonGreen,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            searchQuery = ""
                            showSearchDialog = true
                        },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, cyanAccent.copy(alpha = 0.6f)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("search_country_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = cyanAccent,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Search A-Z (${NameGenerator.allCountries.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = cyanAccent)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Quick common countries list
                val displayedQuickCountries = remember(state.selectedCountry) {
                    val list = NameGenerator.commonCountries.toMutableList()
                    if (list.none { it.code.equals(state.selectedCountry, ignoreCase = true) }) {
                        list.add(0, activeCountry)
                    }
                    list
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    displayedQuickCountries.forEach { country ->
                        val isSelected = state.selectedCountry.equals(country.code, ignoreCase = true)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                OverlayStateManager.setSelectedCountry(country.code)
                            },
                            label = {
                                Text(
                                    text = "${country.flag} ${country.code}",
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    color = if (isSelected) Color.White else Color(0xFFCBD5E1)
                                )
                            },
                            leadingIcon = if (isSelected) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = cyanAccent,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = innerBoxBgColor,
                                selectedContainerColor = BrandBlue.copy(alpha = 0.35f)
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = Color(0xFF1E3A56),
                                selectedBorderColor = cyanAccent
                            ),
                            modifier = Modifier.testTag("country_chip_${country.code}")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Gender Selection Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val genders = listOf(
                        Triple(Gender.MALE, "Male (ছেলে)", Icons.Default.Male),
                        Triple(Gender.FEMALE, "Female (মেয়ে)", Icons.Default.Female),
                        Triple(Gender.ANY, "Any (উভয়)", Icons.Default.Wc)
                    )
                    genders.forEach { (gender, title, icon) ->
                        val isSelected = state.selectedGender == gender
                        Surface(
                            onClick = { OverlayStateManager.setSelectedGender(gender) },
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .testTag("gender_button_${gender.name}"),
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) cyanAccent.copy(alpha = 0.18f) else innerBoxBgColor,
                            border = BorderStroke(
                                1.2.dp,
                                if (isSelected) cyanAccent else Color(0xFF1E3A56)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 9.dp, horizontal = 6.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = title,
                                    tint = if (isSelected) cyanAccent else Color(0xFF94A3B8),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = title,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. First Name, Last Name & Full Name Studio Card
        val currentFullName = state.lastGeneratedName.ifEmpty {
            NameGenerator.generateName(state.selectedCountry, state.selectedGender)
        }
        val nameParts = currentFullName.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
        val firstName = nameParts.firstOrNull() ?: currentFullName
        val lastName = if (nameParts.size > 1) nameParts.drop(1).joinToString(" ") else firstName

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = cardBgColor),
            border = BorderStroke(1.2.dp, neonGreen.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "✨ First Name & Last Name Studio (${state.selectedCountry})",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Surface(
                        color = neonGreen.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "1-Tap Copy",
                            color = neonGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Main Generate & Copy Full Name Button
                Button(
                    onClick = {
                        val generated = OverlayStateManager.generateAndCopyName(context)
                        if (recentGeneratedNames.none { it == generated }) {
                            recentGeneratedNames.add(0, generated)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("generate_name_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(19.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Generate & Copy Full Name (${state.selectedCountry})",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.5.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Full Name Display Box
                Surface(
                    onClick = {
                        ClipboardHelper.copyToClipboard(
                            context,
                            currentFullName,
                            "Full Name",
                            "📋 Copied Full Name: $currentFullName"
                        )
                        VibrationHelper.vibrateSuccess(context)
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = innerBoxBgColor,
                    border = BorderStroke(1.dp, neonGreen.copy(alpha = 0.55f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "FULL NAME (পুরো নাম - ট্যাপ করে কপি করুন)",
                                fontSize = 10.sp,
                                color = neonGreen,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = currentFullName,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Full Name",
                            tint = neonGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Dedicated First Name & Last Name Cards side-by-side
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // FIRST NAME CARD
                    Surface(
                        onClick = {
                            ClipboardHelper.copyToClipboard(
                                context,
                                firstName,
                                "First Name",
                                "📋 First Name কপি হয়েছে: $firstName"
                            )
                            VibrationHelper.vibrateSuccess(context)
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = innerBoxBgColor,
                        border = BorderStroke(1.2.dp, cyanAccent.copy(alpha = 0.6f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "FIRST NAME",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = cyanAccent
                                )
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy First Name",
                                    tint = cyanAccent,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = firstName,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                onClick = {
                                    val generated = OverlayStateManager.generateAndCopyName(context)
                                    val newFirst = generated.trim().split("\\s+".toRegex()).firstOrNull() ?: generated
                                    ClipboardHelper.copyToClipboard(
                                        context,
                                        newFirst,
                                        "First Name",
                                        "⚡ নতুন First Name কপি হয়েছে: $newFirst"
                                    )
                                },
                                shape = RoundedCornerShape(6.dp),
                                color = cyanAccent.copy(alpha = 0.16f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "🔄 New First Copy",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = cyanAccent,
                                    modifier = Modifier.padding(vertical = 4.dp, horizontal = 6.dp)
                                )
                            }
                        }
                    }

                    // LAST NAME CARD
                    Surface(
                        onClick = {
                            ClipboardHelper.copyToClipboard(
                                context,
                                lastName,
                                "Last Name",
                                "📋 Last Name কপি হয়েছে: $lastName"
                            )
                            VibrationHelper.vibrateSuccess(context)
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = innerBoxBgColor,
                        border = BorderStroke(1.2.dp, BrandBlue.copy(alpha = 0.7f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "LAST NAME",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF60A5FA)
                                )
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy Last Name",
                                    tint = Color(0xFF60A5FA),
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = lastName,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                onClick = {
                                    val generated = OverlayStateManager.generateAndCopyName(context)
                                    val parts = generated.trim().split("\\s+".toRegex())
                                    val newLast = if (parts.size > 1) parts.drop(1).joinToString(" ") else generated
                                    ClipboardHelper.copyToClipboard(
                                        context,
                                        newLast,
                                        "Last Name",
                                        "⚡ নতুন Last Name কপি হয়েছে: $newLast"
                                    )
                                },
                                shape = RoundedCornerShape(6.dp),
                                color = BrandBlue.copy(alpha = 0.22f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "🔄 New Last Copy",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF93C5FD),
                                    modifier = Modifier.padding(vertical = 4.dp, horizontal = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Built-in Strong Password & Custom Password Generator Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = cardBgColor),
            border = BorderStroke(1.2.dp, purpleAccent.copy(alpha = 0.55f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = purpleAccent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Password Generator & Overlay Pass",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Text(
                                text = if (!state.isRandomPasswordMode && state.savedPasswordText.isNotBlank())
                                    "Mode: Custom Fixed Password (${state.savedPasswordText})"
                                else
                                    "Mode: Auto Random Strong Password",
                                fontSize = 11.sp,
                                color = Color(0xFFD8B4FE)
                            )
                        }
                    }

                    Switch(
                        checked = state.showOverlayPwCopy,
                        onCheckedChange = { enabled ->
                            OverlayStateManager.setOverlaySectionVisibility("PW", enabled)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = purpleAccent
                        )
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Mode Selector: Auto Random vs Custom Fixed Password
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        onClick = {
                            OverlayStateManager.setRandomPasswordMode(true)
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = if (state.isRandomPasswordMode) purpleAccent.copy(alpha = 0.25f) else innerBoxBgColor,
                        border = BorderStroke(1.dp, if (state.isRandomPasswordMode) purpleAccent else Color(0xFF1E3A56)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 9.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = if (state.isRandomPasswordMode) Color.White else Color(0xFF94A3B8), modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Auto Random Pass",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (state.isRandomPasswordMode) Color.White else Color(0xFF94A3B8)
                            )
                        }
                    }

                    Surface(
                        onClick = {
                            OverlayStateManager.setRandomPasswordMode(false)
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = if (!state.isRandomPasswordMode) purpleAccent.copy(alpha = 0.25f) else innerBoxBgColor,
                        border = BorderStroke(1.dp, if (!state.isRandomPasswordMode) purpleAccent else Color(0xFF1E3A56)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 9.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = if (!state.isRandomPasswordMode) Color.White else Color(0xFF94A3B8), modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Custom Fixed Pass",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (!state.isRandomPasswordMode) Color.White else Color(0xFF94A3B8)
                            )
                        }
                    }
                }

                if (!state.isRandomPasswordMode) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = customPassDraft,
                            onValueChange = { newText: String -> customPassDraft = newText },
                            placeholder = { Text("Enter custom password (e.g. Rony@2026#)", fontSize = 12.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            colors = textFieldColors,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                OverlayStateManager.setSavedPasswordText(context, customPassDraft.trim())
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = purpleAccent),
                            modifier = Modifier.height(50.dp)
                        ) {
                            Text("Save", fontWeight = FontWeight.ExtraBold, color = Color.White)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 1-Tap Generate & Copy Password Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            OverlayStateManager.copySavedPasswordToClipboard(context)
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = purpleAccent),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                    ) {
                        Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(17.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (!state.isRandomPasswordMode && state.savedPasswordText.isNotBlank())
                                "Copy Custom Pass (${state.savedPasswordText})"
                            else
                                "Generate & Copy Strong Password",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.5.sp,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                val activeDisplayPassword = if (state.isRandomPasswordMode) state.currentRandomPassword else state.savedPasswordText
                if (activeDisplayPassword.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        onClick = {
                            ClipboardHelper.copyToClipboard(
                                context,
                                activeDisplayPassword,
                                "Password",
                                "🔑 Password কপি হয়েছে: $activeDisplayPassword"
                            )
                            VibrationHelper.vibrateSuccess(context)
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = innerBoxBgColor,
                        border = BorderStroke(1.dp, purpleAccent.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Active Pass: $activeDisplayPassword",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.5.sp,
                                color = Color(0xFFE9D5FF)
                            )
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy Password",
                                tint = Color(0xFFD8B4FE),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // 5. Quick Instant Name Recommendations (12 Names with First / Last / Full Copy)
        val quickSampleNames = remember(state.selectedCountry, state.selectedGender, sampleRefreshKey) {
            NameGenerator.generateNameList(state.selectedCountry, state.selectedGender, 12)
        }

        if (quickSampleNames.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                border = BorderStroke(1.2.dp, cardBorderColor)
            ) {
                Column(modifier = Modifier.padding(15.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Quick Names List (${activeCountry.flag} ${state.selectedCountry})",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Text(
                                text = "যেকোনো নামের First, Last বা Full Name এক ক্লিকে কপি করুন",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }

                        Surface(
                            onClick = { sampleRefreshKey++ },
                            shape = RoundedCornerShape(8.dp),
                            color = cyanAccent.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, cyanAccent.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = cyanAccent, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("আরো নাম", color = cyanAccent, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        quickSampleNames.forEach { name ->
                            val parts = name.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
                            val fName = parts.firstOrNull() ?: name
                            val lName = if (parts.size > 1) parts.drop(1).joinToString(" ") else fName

                            Surface(
                                onClick = {
                                    ClipboardHelper.copyToClipboard(
                                        context,
                                        name,
                                        "Name",
                                        "✓ Full Name কপি হয়েছে: $name"
                                    )
                                    VibrationHelper.vibrateSuccess(context)
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = innerBoxBgColor,
                                border = BorderStroke(0.9.dp, Color(0xFF1A2D47)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = name,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            onClick = {
                                                ClipboardHelper.copyToClipboard(
                                                    context,
                                                    fName,
                                                    "First Name",
                                                    "✓ First Name কপি হয়েছে: $fName"
                                                )
                                                VibrationHelper.vibrateSuccess(context)
                                            },
                                            shape = RoundedCornerShape(6.dp),
                                            color = cyanAccent.copy(alpha = 0.14f),
                                            border = BorderStroke(0.7.dp, cyanAccent.copy(alpha = 0.45f))
                                        ) {
                                            Text(
                                                text = "First",
                                                color = cyanAccent,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                            )
                                        }

                                        Surface(
                                            onClick = {
                                                ClipboardHelper.copyToClipboard(
                                                    context,
                                                    lName,
                                                    "Last Name",
                                                    "✓ Last Name কপি হয়েছে: $lName"
                                                )
                                                VibrationHelper.vibrateSuccess(context)
                                            },
                                            shape = RoundedCornerShape(6.dp),
                                            color = BrandBlue.copy(alpha = 0.2f),
                                            border = BorderStroke(0.7.dp, BrandBlue.copy(alpha = 0.5f))
                                        ) {
                                            Text(
                                                text = "Last",
                                                color = Color(0xFF93C5FD),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                            )
                                        }

                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy Full",
                                            tint = neonGreen,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Searchable All Countries A-Z Dialog
    if (showSearchDialog) {
        val filteredCountries = remember(searchQuery) {
            if (searchQuery.isBlank()) {
                NameGenerator.allCountries
            } else {
                NameGenerator.allCountries.filter {
                    it.name.contains(searchQuery, ignoreCase = true) ||
                        it.code.contains(searchQuery, ignoreCase = true)
                }
            }
        }

        AlertDialog(
            onDismissRequest = { showSearchDialog = false },
            containerColor = cardBgColor,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = null,
                        tint = cyanAccent,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Select Country (A to Z)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search country or code (e.g. BD, US, UK)...", fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = cyanAccent)
                        },
                        trailingIcon = if (searchQuery.isNotEmpty()) {
                            {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color(0xFF94A3B8))
                                }
                            }
                        } else null,
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = textFieldColors,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("country_search_input")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "${filteredCountries.size} countries available",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        items(filteredCountries, key = { it.code }) { country ->
                            val isSelected = state.selectedCountry.equals(country.code, ignoreCase = true)
                            Surface(
                                onClick = {
                                    OverlayStateManager.setSelectedCountry(country.code)
                                    showSearchDialog = false
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) BrandBlue.copy(alpha = 0.28f) else innerBoxBgColor,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) cyanAccent else Color(0xFF1E3A56)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("country_item_${country.code}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = country.flag, fontSize = 20.sp)
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = country.name,
                                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                            fontSize = 13.5.sp,
                                            color = Color.White
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "(${country.code})",
                                            fontSize = 11.5.sp,
                                            color = cyanAccent
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = cyanAccent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSearchDialog = false }) {
                    Text("Close", color = cyanAccent, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}
