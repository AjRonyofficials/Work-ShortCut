package com.example.ui.screens

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.AuthManager
import com.example.service.AuthUser
import com.example.service.UnixSmsCdrRecord
import com.example.service.UnixSmsManager
import com.example.service.UploadedNumberItem
import com.example.util.ClipboardHelper
import com.example.util.VibrationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AdminCountryInfo(
    val code: String,
    val name: String,
    val flag: String,
    val dialCode: String
)

val ADMIN_PRESET_COUNTRIES = listOf(
    AdminCountryInfo("US", "United States", "🇺🇸", "+1"),
    AdminCountryInfo("BD", "Bangladesh", "🇧🇩", "+880"),
    AdminCountryInfo("GB", "United Kingdom", "🇬🇧", "+44"),
    AdminCountryInfo("CA", "Canada", "🇨🇦", "+1"),
    AdminCountryInfo("CM", "Cameroon", "🇨🇲", "+237"),
    AdminCountryInfo("BO", "Bolivia", "🇧🇴", "+591"),
    AdminCountryInfo("SD", "Sudan", "🇸🇩", "+249"),
    AdminCountryInfo("IN", "India", "🇮🇳", "+91"),
    AdminCountryInfo("PK", "Pakistan", "🇵🇰", "+92"),
    AdminCountryInfo("NG", "Nigeria", "🇳🇬", "+234"),
    AdminCountryInfo("PH", "Philippines", "🇵🇭", "+63"),
    AdminCountryInfo("ID", "Indonesia", "🇮🇩", "+62"),
    AdminCountryInfo("KE", "Kenya", "🇰🇪", "+254"),
    AdminCountryInfo("ZA", "South Africa", "🇿🇦", "+27"),
    AdminCountryInfo("BR", "Brazil", "🇧🇷", "+55"),
    AdminCountryInfo("MY", "Malaysia", "🇲🇾", "+60"),
    AdminCountryInfo("AE", "United Arab Emirates", "🇦🇪", "+971"),
    AdminCountryInfo("SA", "Saudi Arabia", "🇸🇦", "+966"),
    AdminCountryInfo("EG", "Egypt", "🇪🇬", "+20"),
    AdminCountryInfo("DE", "Germany", "🇩🇪", "+49"),
    AdminCountryInfo("FR", "France", "🇫🇷", "+33"),
    AdminCountryInfo("RU", "Russia", "🇷🇺", "+7")
)

val ADMIN_SERVICES = listOf("Facebook", "Instagram", "WhatsApp")

@Composable
fun AdminPanelSection(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isAdmin = AuthManager.isAdmin()
    val unixState by UnixSmsManager.state.collectAsState()
    val managedUsers by AuthManager.managedUsers.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Unix SMS, 1: Users, 2: Live CDR

    LaunchedEffect(Unit) {
        UnixSmsManager.init(context)
        AuthManager.init(context)
    }

    if (!isAdmin) {
        // Non-Admin Locked Screen
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFF0A0F18))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131C28)),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, Color(0xFFE53935).copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth(0.92f)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Lock",
                        tint = Color(0xFFE53935),
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "🔒 এডমিন এক্সেস সংরক্ষিত",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "এই সেকশনটি শুধুমাত্র মাস্টার এডমিনের জন্য নির্ধারিত (${AuthManager.ADMIN_EMAIL})। আপনি সাধারণ ইউজার হিসেবে লগইন আছেন।",
                        fontSize = 12.sp,
                        color = Color(0xFF90A4AE),
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = { AuthManager.logout() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("লগআউট করে এডমিন লগইন করুন", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        return
    }

    // Admin Dashboard
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF090E17))
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(6.dp))

            // Admin Header Bar
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF101926)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF1E334D)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFF3B82F6)))
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AdminPanelSettings,
                                contentDescription = "Admin",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "MASTER ADMIN PANEL",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF818CF8),
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = Color(0xFF00E676).copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "LIVE",
                                        color = Color(0xFF00E676),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Text(
                                text = AuthManager.ADMIN_EMAIL,
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            AuthManager.logout()
                            Toast.makeText(context, "লগআউট করা হয়েছে", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444))
                    ) {
                        Icon(Icons.Default.Logout, contentDescription = "Logout", modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Logout", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            // Modern Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color(0xFF0D1522),
                contentColor = Color(0xFF818CF8),
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = Color(0xFF818CF8),
                        height = 3.dp
                    )
                },
                divider = { HorizontalDivider(color = Color(0xFF1E2D42)) }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Unix SMS (${unixState.uploadedNumbers.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Users (${managedUsers.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Live CDR (${unixState.liveCdrRecords.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }
        }

        when (selectedTab) {
            0 -> {
                // TAB 0: UNIX SMS NUMBER MANAGEMENT
                item {
                    AdminUnixSmsCard(
                        context = context,
                        unixState = unixState
                    )
                }
            }
            1 -> {
                // TAB 1: USER ACCOUNT MANAGEMENT
                item {
                    AdminUserManagerCard(
                        context = context,
                        managedUsers = managedUsers
                    )
                }
            }
            2 -> {
                // TAB 2: LIVE CDR TRAFFIC
                item {
                    AdminLiveCdrCard(
                        unixState = unixState
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

/**
 * Tab 0: Unix SMS Management Card (Upload by text / File, Delete by Country, Token config)
 */
@Composable
fun AdminUnixSmsCard(
    context: Context,
    unixState: com.example.service.UnixSmsState
) {
    var selectedCountry by remember { mutableStateOf(ADMIN_PRESET_COUNTRIES.first()) }
    var selectedService by remember { mutableStateOf(ADMIN_SERVICES.first()) }
    var numbersInput by remember { mutableStateOf("") }
    var countryDropdownExpanded by remember { mutableStateOf(false) }
    var isUploading by remember { mutableStateOf(false) }

    // Clear by country
    var deleteCountryDropdownExpanded by remember { mutableStateOf(false) }
    var deleteTargetCountry by remember { mutableStateOf("ALL") }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // Search filter for uploaded list
    var filterQuery by remember { mutableStateOf("") }

    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val content = inputStream?.bufferedReader()?.use { it.readText() } ?: ""
                if (content.isNotEmpty()) {
                    numbersInput = if (numbersInput.isBlank()) content else numbersInput + "\n" + content
                    Toast.makeText(context, "✓ ফাইল সফলভাবে লোড হয়েছে!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "ফাইলটি খালি ছিল", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "ফাইল পড়তে সমস্যা: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // 1. Stats Counter
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val total = unixState.uploadedNumbers.size
            val available = unixState.uploadedNumbers.count { !it.isUsed }
            val used = unixState.uploadedNumbers.count { it.isUsed }

            AdminStatPill(title = "TOTAL", count = total.toString(), color = Color(0xFF60A5FA), modifier = Modifier.weight(1f))
            AdminStatPill(title = "AVAILABLE", count = available.toString(), color = Color(0xFF34D399), modifier = Modifier.weight(1f))
            AdminStatPill(title = "USED", count = used.toString(), color = Color(0xFFFBBF24), modifier = Modifier.weight(1f))
            AdminStatPill(title = "TODAY OTP", count = unixState.todayOtpCount.toString(), color = Color(0xFFF472B6), modifier = Modifier.weight(1f))
        }

        // 1.5 USER OTP RATE CONTROL CARD (Increase / Decrease rate)
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF101C2B)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Bolt, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "USER OTP RATE CONTROL (ওটিপি রেট নিয়ন্ত্রণ)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Surface(
                        color = Color(0xFF0284C7).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "$${String.format(java.util.Locale.US, "%.3f", unixState.otpRatePerSms)} / OTP",
                            color = Color(0xFF38BDF8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "ইউজারদের ওটিপি রেট বাড়াতে (+) বা কমাতে (-) নিচের বোতাম ব্যবহার করুন:",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Decrease buttons
                    Button(
                        onClick = {
                            UnixSmsManager.adjustOtpRate(-0.005)
                            Toast.makeText(context, "রেট কমানো হয়েছে: $${String.format(java.util.Locale.US, "%.3f", unixState.otpRatePerSms - 0.005)}", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("-0.005", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF87171))
                    }

                    Button(
                        onClick = {
                            UnixSmsManager.adjustOtpRate(-0.001)
                            Toast.makeText(context, "রেট কমানো হয়েছে: $${String.format(java.util.Locale.US, "%.3f", unixState.otpRatePerSms - 0.001)}", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("-0.001", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF87171))
                    }

                    // Current Rate Box
                    Surface(
                        color = Color(0xFF090E17),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFF0284C7)),
                        modifier = Modifier.weight(1.3f)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                            Text(
                                text = "$${String.format(java.util.Locale.US, "%.3f", unixState.otpRatePerSms)}",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // Increase buttons
                    Button(
                        onClick = {
                            UnixSmsManager.adjustOtpRate(+0.001)
                            Toast.makeText(context, "রেট বাড়ানো হয়েছে: $${String.format(java.util.Locale.US, "%.3f", unixState.otpRatePerSms + 0.001)}", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("+0.001", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF34D399))
                    }

                    Button(
                        onClick = {
                            UnixSmsManager.adjustOtpRate(+0.005)
                            Toast.makeText(context, "রেট বাড়ানো হয়েছে: $${String.format(java.util.Locale.US, "%.3f", unixState.otpRatePerSms + 0.005)}", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("+0.005", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF34D399))
                    }
                }
            }
        }

        // 2. Upload Numbers Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111B29)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF1E324A)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Phone, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "UPLOAD UNIX SMS NUMBERS",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Country Selector Dropdown
                Text(text = "1. দেশ নির্বাচন করুন (Select Country):", fontSize = 11.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))

                Box {
                    Surface(
                        color = Color(0xFF090E17),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF243B55)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { countryDropdownExpanded = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = selectedCountry.flag, fontSize = 20.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${selectedCountry.name} (${selectedCountry.dialCode})",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(text = "▼", color = Color(0xFF64748B), fontSize = 11.sp)
                        }
                    }

                    DropdownMenu(
                        expanded = countryDropdownExpanded,
                        onDismissRequest = { countryDropdownExpanded = false },
                        modifier = Modifier.background(Color(0xFF0F1A28))
                    ) {
                        ADMIN_PRESET_COUNTRIES.forEach { country ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = country.flag, fontSize = 16.sp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "${country.name} (${country.dialCode})",
                                            color = Color.White,
                                            fontSize = 12.sp
                                        )
                                    }
                                },
                                onClick = {
                                    selectedCountry = country
                                    countryDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Service Selector: Facebook, Instagram, WhatsApp
                Text(text = "2. সার্ভিস নির্বাচন করুন (Service):", fontSize = 11.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ADMIN_SERVICES.forEach { serviceName ->
                        val isSelected = selectedService == serviceName
                        val serviceColor = when (serviceName) {
                            "Facebook" -> Color(0xFF1877F2)
                            "Instagram" -> Color(0xFFE1306C)
                            else -> Color(0xFF25D366) // WhatsApp
                        }

                        Surface(
                            color = if (isSelected) serviceColor.copy(alpha = 0.25f) else Color(0xFF090E17),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, if (isSelected) serviceColor else Color(0xFF243B55)),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedService = serviceName }
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = serviceName,
                                    color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Number Input + File Upload
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "3. নাম্বার লিখুন বা ফাইল আপলোড করুন:", fontSize = 11.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.SemiBold)

                    // File upload button
                    OutlinedButton(
                        onClick = {
                            filePickerLauncher.launch("text/*")
                        },
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8))
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Upload .txt/.csv", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                OutlinedTextField(
                    value = numbersInput,
                    onValueChange = { numbersInput = it },
                    placeholder = {
                        Text(
                            text = "প্রতি লাইনে একটি নাম্বার দিন বা কমা দিয়ে পেস্ট করুন:\n+12025550199\n+12025550188\nবা 12025550177, 12025550166...",
                            fontSize = 11.sp,
                            color = Color(0xFF475569)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF243B55),
                        focusedContainerColor = Color(0xFF090E17),
                        unfocusedContainerColor = Color(0xFF090E17)
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Upload Button
                Button(
                    onClick = {
                        if (numbersInput.isBlank()) {
                            Toast.makeText(context, "নাম্বার দিন অথবা ফাইল আপলোড করুন", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isUploading = true
                        val addedCount = UnixSmsManager.addNumbersBatch(
                            rawContent = numbersInput,
                            countryCode = selectedCountry.code,
                            countryName = selectedCountry.name,
                            flag = selectedCountry.flag,
                            service = selectedService
                        )
                        isUploading = false
                        if (addedCount > 0) {
                            Toast.makeText(context, "✓ $addedCount টি নতুন নাম্বার সফলভাবে প্যানেলে যোগ হয়েছে!", Toast.LENGTH_LONG).show()
                            numbersInput = ""
                            VibrationHelper.vibrateSuccess(context)
                        } else {
                            Toast.makeText(context, "কোনো নতুন বৈধ নাম্বার পাওয়া যায়নি বা ইতিমধ্যে আছে", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    enabled = !isUploading
                ) {
                    if (isUploading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "UPLOAD NUMBERS TO UNIX SMS",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // 3. Clear / Delete Numbers Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131722)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF332029)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = Color(0xFFF43F5E), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "CLEAR / DELETE NUMBERS",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "কোন দেশের নাম্বার ডিলিট করবেন নির্বাচন করুন:",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8)
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Target Country Selector
                Box {
                    Surface(
                        color = Color(0xFF090E17),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF4C1D2F)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { deleteCountryDropdownExpanded = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val displayName = if (deleteTargetCountry == "ALL") "সব দেশের নাম্বার (ALL COUNTRIES)" else {
                                val c = ADMIN_PRESET_COUNTRIES.find { it.code == deleteTargetCountry }
                                "${c?.flag ?: "🌐"} ${c?.name ?: deleteTargetCountry}"
                            }
                            Text(text = displayName, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(text = "▼", color = Color(0xFF64748B), fontSize = 10.sp)
                        }
                    }

                    DropdownMenu(
                        expanded = deleteCountryDropdownExpanded,
                        onDismissRequest = { deleteCountryDropdownExpanded = false },
                        modifier = Modifier.background(Color(0xFF0F1A28))
                    ) {
                        DropdownMenuItem(
                            text = { Text("🌐 সব দেশের নাম্বার (ALL COUNTRIES)", color = Color(0xFFF43F5E), fontWeight = FontWeight.Bold) },
                            onClick = {
                                deleteTargetCountry = "ALL"
                                deleteCountryDropdownExpanded = false
                            }
                        )

                        val activeCountryCodes = unixState.uploadedNumbers.map { it.countryCode }.distinct()
                        activeCountryCodes.forEach { code ->
                            val c = ADMIN_PRESET_COUNTRIES.find { it.code == code }
                            DropdownMenuItem(
                                text = { Text("${c?.flag ?: "🌐"} ${c?.name ?: code} (${unixState.uploadedNumbers.count { it.countryCode == code }} nos)", color = Color.White) },
                                onClick = {
                                    deleteTargetCountry = code
                                    deleteCountryDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = { showDeleteConfirmDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (deleteTargetCountry == "ALL") "CLEAR ALL NUMBERS" else "CLEAR $deleteTargetCountry NUMBERS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Delete Confirmation Dialog
        if (showDeleteConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmDialog = false },
                containerColor = Color(0xFF141D2B),
                title = {
                    Text(text = "⚠️ নাম্বার ডিলিট নিশ্চিতকরণ", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                },
                text = {
                    Text(
                        text = if (deleteTargetCountry == "ALL") {
                            "আপনি কি নিশ্চিত যে আপনি সমস্ত আপলোড করা নাম্বার সম্পূর্ণ মুছে ফেলতে চান?"
                        } else {
                            "আপনি কি নিশ্চিত যে $deleteTargetCountry এর সকল নাম্বার মুছে ফেলতে চান?"
                        },
                        color = Color(0xFFCBD5E1),
                        fontSize = 12.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            UnixSmsManager.deleteNumbersByCountry(deleteTargetCountry)
                            showDeleteConfirmDialog = false
                            Toast.makeText(context, "✓ নাম্বার সফলভাবে মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48))
                    ) {
                        Text("হ্যাঁ, ডিলিট করুন")
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showDeleteConfirmDialog = false }) {
                        Text("বাতিল", color = Color.White)
                    }
                }
            )
        }

        // 4. Uploaded Numbers List Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF101824)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF1E2D40)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "UPLOADED NUMBERS (${unixState.uploadedNumbers.size})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    OutlinedTextField(
                        value = filterQuery,
                        onValueChange = { filterQuery = it },
                        placeholder = { Text("খুঁজুন...", fontSize = 10.sp, color = Color(0xFF64748B)) },
                        modifier = Modifier
                            .width(130.dp)
                            .height(40.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF38BDF8),
                            unfocusedBorderColor = Color(0xFF243B55),
                            focusedContainerColor = Color(0xFF090E17),
                            unfocusedContainerColor = Color(0xFF090E17)
                        ),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                val filtered = unixState.uploadedNumbers.filter {
                    filterQuery.isBlank() ||
                            it.number.contains(filterQuery) ||
                            it.countryName.contains(filterQuery, ignoreCase = true) ||
                            it.service.contains(filterQuery, ignoreCase = true)
                }

                if (filtered.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "কোনো নাম্বার পাওয়া যায়নি। উপরে নাম্বার আপলোড করুন।",
                            color = Color(0xFF64748B),
                            fontSize = 11.sp
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        filtered.take(50).forEach { item ->
                            UploadedNumberRowItem(
                                item = item,
                                onDelete = { UnixSmsManager.deleteSingleNumber(item.id) },
                                onCopy = {
                                    ClipboardHelper.copyToClipboard(context, item.number, "Phone Number")
                                    Toast.makeText(context, "কপি করা হয়েছে: ${item.number}", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }

                        if (filtered.size > 50) {
                            Text(
                                text = "... আরও ${filtered.size - 50} টি নাম্বার রয়েছে",
                                color = Color(0xFF64748B),
                                fontSize = 10.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UploadedNumberRowItem(
    item: UploadedNumberItem,
    onDelete: () -> Unit,
    onCopy: () -> Unit
) {
    Surface(
        color = Color(0xFF090E17),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color(0xFF1E2D40)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = item.flag, fontSize = 16.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = item.number,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${item.service} • ${item.countryCode}",
                            color = Color(0xFF94A3B8),
                            fontSize = 9.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        if (item.isUsed) {
                            Surface(
                                color = Color(0xFFF59E0B).copy(alpha = 0.2f),
                                shape = RoundedCornerShape(3.dp)
                            ) {
                                Text(
                                    text = "USED",
                                    color = Color(0xFFFBBF24),
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                                )
                            }
                        } else {
                            Surface(
                                color = Color(0xFF10B981).copy(alpha = 0.2f),
                                shape = RoundedCornerShape(3.dp)
                            ) {
                                Text(
                                    text = "AVAILABLE",
                                    color = Color(0xFF34D399),
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCopy, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color(0xFF38BDF8), modifier = Modifier.size(14.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFF43F5E), modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

/**
 * Tab 1: User Accounts Manager Card
 */
@Composable
fun AdminUserManagerCard(
    context: Context,
    managedUsers: List<AuthUser>
) {
    var newUserEmail by remember { mutableStateOf("") }
    var newUserPass by remember { mutableStateOf("") }
    var newUserName by remember { mutableStateOf("") }
    var createdSuccessCredentials by remember { mutableStateOf<Pair<String, String>?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Create User Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111B29)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF1E324A)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, tint = Color(0xFF818CF8), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "CREATE USER LOGIN CREDENTIALS",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "ইউজাররা নিজে রেজিস্টার করতে পারবে না। আপনি এখান থেকে ইমেইল ও পাসওয়ার্ড তৈরি করে তাদের দিলে তারা লগইন করতে পারবে।",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8),
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = newUserName,
                    onValueChange = { newUserName = it },
                    label = { Text("ইউজারের নাম (User Name)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF818CF8),
                        unfocusedBorderColor = Color(0xFF243B55),
                        focusedContainerColor = Color(0xFF090E17),
                        unfocusedContainerColor = Color(0xFF090E17)
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = newUserEmail,
                    onValueChange = { newUserEmail = it },
                    label = { Text("ইমেইল (Email)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF818CF8),
                        unfocusedBorderColor = Color(0xFF243B55),
                        focusedContainerColor = Color(0xFF090E17),
                        unfocusedContainerColor = Color(0xFF090E17)
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = newUserPass,
                    onValueChange = { newUserPass = it },
                    label = { Text("পাসওয়ার্ড (Password)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF818CF8),
                        unfocusedBorderColor = Color(0xFF243B55),
                        focusedContainerColor = Color(0xFF090E17),
                        unfocusedContainerColor = Color(0xFF090E17)
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        val (success, msg) = AuthManager.createManagedUser(
                            emailInput = newUserEmail,
                            passInput = newUserPass,
                            nameInput = newUserName
                        )
                        if (success) {
                            createdSuccessCredentials = Pair(newUserEmail.trim().lowercase(), newUserPass.trim())
                            Toast.makeText(context, "✓ $msg", Toast.LENGTH_SHORT).show()
                            newUserEmail = ""
                            newUserPass = ""
                            newUserName = ""
                            VibrationHelper.vibrateSuccess(context)
                        } else {
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("GENERATE & CREATE USER ACCOUNT", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                // Show Success Copy Box
                createdSuccessCredentials?.let { (em, pw) ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = Color(0xFF064E3B).copy(alpha = 0.4f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF059669))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "✓ নতুন অ্যাকাউন্ট তৈরি সফল!", color = Color(0xFF34D399), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                OutlinedButton(
                                    onClick = {
                                        val text = "Work ShortCut Login Credentials:\nEmail: $em\nPassword: $pw"
                                        ClipboardHelper.copyToClipboard(context, text, "Login Credentials")
                                        Toast.makeText(context, "লগইন তথ্য কপি করা হয়েছে!", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, Color(0xFF34D399))
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color(0xFF34D399))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Copy Info", fontSize = 10.sp, color = Color(0xFF34D399))
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "Email: $em\nPassword: $pw", color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }

        // List of Created Users
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF101824)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF1E2D40)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "MANAGED USER ACCOUNTS (${managedUsers.size})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(10.dp))

                if (managedUsers.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "কোনো ইউজার অ্যাকাউন্ট তৈরি করা হয়নি। উপরে ইউজার তৈরি করুন।",
                            color = Color(0xFF64748B),
                            fontSize = 11.sp
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        managedUsers.forEach { user ->
                            AdminUserItemRow(
                                user = user,
                                onToggleStatus = { AuthManager.toggleUserStatus(user.email) },
                                onToggleBan = { AuthManager.toggleUserBan(user.email) },
                                onResetLogins = { AuthManager.resetUserLogins(user.email) },
                                onDelete = { AuthManager.deleteManagedUser(user.email) },
                                onCopy = {
                                    val text = "Email: ${user.email}\nPassword: ${user.passwordHash}"
                                    ClipboardHelper.copyToClipboard(context, text, "User Credentials")
                                    Toast.makeText(context, "কপি করা হয়েছে: ${user.email}", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminUserItemRow(
    user: AuthUser,
    onToggleStatus: () -> Unit,
    onToggleBan: () -> Unit,
    onResetLogins: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit
) {
    var passwordVisible by remember { mutableStateOf(false) }

    Surface(
        color = Color(0xFF090E17),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            1.dp,
            if (user.isBanned) Color(0xFFEF4444) else if (user.isActive) Color(0xFF1E2D40) else Color(0xFFF59E0B)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = user.name,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    if (user.isBanned) {
                        Surface(
                            color = Color(0xFFEF4444).copy(alpha = 0.25f),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, Color(0xFFEF4444))
                        ) {
                            Text(
                                text = "🚫 BANNED",
                                color = Color(0xFFF87171),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    } else if (user.isActive) {
                        Surface(
                            color = Color(0xFF10B981).copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "ACTIVE",
                                color = Color(0xFF34D399),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    } else {
                        Surface(
                            color = Color(0xFFF59E0B).copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "INACTIVE",
                                color = Color(0xFFFBBF24),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onCopy, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color(0xFF38BDF8), modifier = Modifier.size(14.dp))
                    }
                    IconButton(onClick = onToggleBan, modifier = Modifier.size(28.dp)) {
                        Text(
                            text = if (user.isBanned) "Unban" else "Ban",
                            color = if (user.isBanned) Color(0xFF34D399) else Color(0xFFF87171),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onToggleStatus, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = if (user.isActive) Icons.Default.Close else Icons.Default.Check,
                            contentDescription = "Toggle status",
                            tint = if (user.isActive) Color(0xFFF59E0B) else Color(0xFF10B981),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(14.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = user.email,
                color = Color(0xFF94A3B8),
                fontSize = 11.sp
            )

            Spacer(modifier = Modifier.height(3.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (passwordVisible) "Pass: ${user.passwordHash}" else "Pass: ••••••••",
                        color = Color(0xFF38BDF8),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    IconButton(onClick = { passwordVisible = !passwordVisible }, modifier = Modifier.size(20.dp)) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                // Logins count and active devices badge
                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Logins: ${user.loginCount}",
                            color = Color(0xFF60A5FA),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "• Active: ${user.activeDevicesCount}",
                            color = if (user.activeDevicesCount > 0) Color(0xFF34D399) else Color(0xFF94A3B8),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (user.lastLoginAt > 0L) {
                Spacer(modifier = Modifier.height(2.dp))
                val timeDiff = (System.currentTimeMillis() - user.lastLoginAt) / 1000
                val timeStr = when {
                    timeDiff < 60 -> "Just now"
                    timeDiff < 3600 -> "${timeDiff / 60}m ago"
                    timeDiff < 86400 -> "${timeDiff / 3600}h ago"
                    else -> "${timeDiff / 86400}d ago"
                }
                Text(
                    text = "Last login: $timeStr ${if (user.lastDeviceName.isNotEmpty()) "• ${user.lastDeviceName}" else ""}",
                    color = Color(0xFF64748B),
                    fontSize = 9.sp
                )
            }
        }
    }
}

/**
 * Tab 2: Live CDR Traffic Stream (Admin View)
 */
@Composable
fun AdminLiveCdrCard(
    unixState: com.example.service.UnixSmsState
) {
    var searchFilter by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111B29)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF1E324A)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00E676))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "LIVE UNIX SMS STREAM (1s FAST POLL)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00E676)
                        )
                    }

                    Surface(
                        color = Color(0xFF38BDF8).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "${unixState.liveCdrRecords.size} Records",
                            color = Color(0xFF38BDF8),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = searchFilter,
                    onValueChange = { searchFilter = it },
                    placeholder = { Text("মেসেজ, নাম্বার বা সেন্ডার দিয়ে ফিল্টার করুন...", fontSize = 11.sp, color = Color(0xFF64748B)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF243B55),
                        focusedContainerColor = Color(0xFF090E17),
                        unfocusedContainerColor = Color(0xFF090E17)
                    ),
                    singleLine = true
                )
            }
        }

        val filteredRecords = unixState.liveCdrRecords.filter {
            searchFilter.isBlank() ||
                    it.num.contains(searchFilter) ||
                    it.cli.contains(searchFilter, ignoreCase = true) ||
                    it.message.contains(searchFilter, ignoreCase = true)
        }

        if (filteredRecords.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF101824)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "কোনো নতুন SMS রেকর্ড নেই বা লোড হচ্ছে...",
                        color = Color(0xFF64748B),
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                filteredRecords.forEach { cdr ->
                    AdminCdrItemRow(cdr = cdr)
                }
            }
        }
    }
}

@Composable
fun AdminCdrItemRow(cdr: UnixSmsCdrRecord) {
    Surface(
        color = Color(0xFF101824),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFF1E2D40)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = Color(0xFF38BDF8).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = cdr.cli.ifEmpty { "SMS" },
                            color = Color(0xFF38BDF8),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = cdr.num,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Text(
                    text = cdr.dt,
                    color = Color(0xFF64748B),
                    fontSize = 9.sp
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = cdr.message,
                color = Color(0xFFE2E8F0),
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
fun AdminStatPill(
    title: String,
    count: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                color = color.copy(alpha = 0.9f),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = count,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}
