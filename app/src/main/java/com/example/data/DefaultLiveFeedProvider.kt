package com.example.data

import com.example.data.model.BroadcastFeedItem
import com.example.service.PremiumV2ConsoleItem
import com.example.service.UnixSmsCdrRecord

/**
 * Provides 50 pre-seeded and guaranteed live broadcast OTP items
 * across diverse countries, active ranges, and carriers for all 4 panels:
 * 1. Kop Engine 1 (Live Console): Togo, Benin, Mali, Central African Rep, Guinea, Cameroon, Armenia, Madagascar, Myanmar, Mozambique, etc.
 * 2. Kop Engine 2 (Live Radar): Zambia, Zimbabwe, Mozambique, South Africa, Kenya, Nigeria, Ghana, Tanzania, Uganda, etc.
 * 3. Premium Number V2 (Live OTP): Italy, Ethiopia, France, Nigeria, Colombia, Kenya, South Africa, UK, US, Germany, Spain, Brazil, etc.
 * 4. Unix SMS (Live DLR Logs): US, UK, Canada, India, Nigeria, Cameroon, Kenya, Indonesia, Philippines, France, Germany, etc.
 */
object DefaultLiveFeedProvider {

    fun getGlobalKop1Broadcast50(): List<BroadcastFeedItem> {
        val now = System.currentTimeMillis()
        return listOf(
            BroadcastFeedItem(
                id = "kop1_01",
                number = "23674182901",
                range = "236741XXX",
                service = "OTHER",
                country = "Central African Republic",
                operator = "Special Services",
                otp = "Dear Lil, Your AlFursan mobile number is now updated to *********6698. If you didn't request this update, please contact the Guest Care Center.",
                time = now - 3_000L,
                isNew = true
            ),
            BroadcastFeedItem(
                id = "kop1_02",
                number = "22896019283",
                range = "228960XXX",
                service = "Facebook",
                country = "Togo",
                operator = "Togocel",
                otp = "Facebook: Your code is 315492",
                time = now - 7_000L,
                isNew = true
            ),
            BroadcastFeedItem(
                id = "kop1_03",
                number = "22961029182",
                range = "229610XXX",
                service = "Facebook",
                country = "Benin",
                operator = "MTN Benin",
                otp = "<#> 772548 is your Facebook code\nLaz+nxCarLW",
                time = now - 11_000L,
                isNew = true
            ),
            BroadcastFeedItem(
                id = "kop1_04",
                number = "22370019284",
                range = "223700XXX",
                service = "WhatsApp",
                country = "Mali",
                operator = "Orange Mali",
                otp = "Your WhatsApp code: 491-028. You can also tap on this link to verify your phone: v.whatsapp.com/491028",
                time = now - 15_000L,
                isNew = true
            ),
            BroadcastFeedItem(
                id = "kop1_05",
                number = "22462019382",
                range = "224620XXX",
                service = "Facebook",
                country = "Guinea",
                operator = "Orange Guinea",
                otp = "<#> 619283 is your Facebook code\nH29Q+Fsn4Sr",
                time = now - 22_000L,
                isNew = true
            ),
            BroadcastFeedItem(
                id = "kop1_06",
                number = "37477182910",
                range = "374771XXX",
                service = "Instagram",
                country = "Armenia",
                operator = "VivaCell-MTS",
                otp = "819204 is your Instagram code. Don't share it.",
                time = now - 28_000L,
                isNew = false
            ),
            BroadcastFeedItem(
                id = "kop1_07",
                number = "26134019283",
                range = "261340XXX",
                service = "Facebook",
                country = "Madagascar",
                operator = "Telma",
                otp = "<#> 918230 is your Facebook code\nLaz+nxCarLW",
                time = now - 35_000L,
                isNew = false
            ),
            BroadcastFeedItem(
                id = "kop1_08",
                number = "23762819201",
                range = "237628XXX",
                service = "Facebook",
                country = "Cameroon",
                operator = "MTN Cameroon",
                otp = "Facebook: Your code is 582910",
                time = now - 42_000L,
                isNew = false
            ),
            BroadcastFeedItem(
                id = "kop1_09",
                number = "95970019284",
                range = "959700XXX",
                service = "Telegram",
                country = "Myanmar",
                operator = "MPT",
                otp = "Telegram code: 48192. Do not give this code to anyone.",
                time = now - 50_000L,
                isNew = false
            ),
            BroadcastFeedItem(
                id = "kop1_10",
                number = "25884019283",
                range = "258840XXX",
                service = "Facebook",
                country = "Mozambique",
                operator = "Vodacom",
                otp = "<#> 391028 is your Facebook code\nH29Q+Fsn4Sr",
                time = now - 58_000L,
                isNew = false
            ),
            BroadcastFeedItem(
                id = "kop1_11",
                number = "22507019283",
                range = "225070XXX",
                service = "WhatsApp",
                country = "Ivory Coast",
                operator = "Orange CI",
                otp = "Your WhatsApp code: 729-103",
                time = now - 65_000L,
                isNew = false
            ),
            BroadcastFeedItem(
                id = "kop1_12",
                number = "26658019283",
                range = "266580XXX",
                service = "Facebook",
                country = "Lesotho",
                operator = "Vodacom Lesotho",
                otp = "<#> 482019 is your Facebook code\nLaz+nxCarLW",
                time = now - 72_000L,
                isNew = false
            ),
            BroadcastFeedItem(
                id = "kop1_13",
                number = "23675019283",
                range = "236750XXX",
                service = "Facebook",
                country = "Central African Republic",
                operator = "Telecel CAR",
                otp = "Facebook: Your code is 192834",
                time = now - 80_000L,
                isNew = false
            ),
            BroadcastFeedItem(
                id = "kop1_14",
                number = "22898102918",
                range = "228981XXX",
                service = "Facebook",
                country = "Togo",
                operator = "Moov Togo",
                otp = "<#> 849102 is your Facebook code\nH29Q+Fsn4Sr",
                time = now - 88_000L,
                isNew = false
            ),
            BroadcastFeedItem(
                id = "kop1_15",
                number = "22997102918",
                range = "229971XXX",
                service = "Instagram",
                country = "Benin",
                operator = "Moov Benin",
                otp = "392019 is your Instagram code. Don't share it.",
                time = now - 95_000L,
                isNew = false
            ),
            BroadcastFeedItem(
                id = "kop1_16",
                number = "22391019284",
                range = "223910XXX",
                service = "Facebook",
                country = "Mali",
                operator = "Malitel",
                otp = "<#> 501928 is your Facebook code\nLaz+nxCarLW",
                time = now - 105_000L,
                isNew = false
            ),
            BroadcastFeedItem(
                id = "kop1_17",
                number = "22466102918",
                range = "224661XXX",
                service = "WhatsApp",
                country = "Guinea",
                operator = "MTN Guinea",
                otp = "Your WhatsApp code: 391-820",
                time = now - 115_000L,
                isNew = false
            ),
            BroadcastFeedItem(
                id = "kop1_18",
                number = "37498019283",
                range = "374980XXX",
                service = "Facebook",
                country = "Armenia",
                operator = "Beeline AM",
                otp = "<#> 719203 is your Facebook code\nH29Q+Fsn4Sr",
                time = now - 125_000L,
                isNew = false
            ),
            BroadcastFeedItem(
                id = "kop1_19",
                number = "26132019283",
                range = "261320XXX",
                service = "Facebook",
                country = "Madagascar",
                operator = "Orange Madagascar",
                otp = "Facebook: Your code is 284019",
                time = now - 135_000L,
                isNew = false
            ),
            BroadcastFeedItem(
                id = "kop1_20",
                number = "23762610291",
                range = "237626XXX",
                service = "Facebook",
                country = "Cameroon",
                operator = "Orange Cameroun",
                otp = "<#> 918302 is your Facebook code\nLaz+nxCarLW",
                time = now - 145_000L,
                isNew = false
            ),
            BroadcastFeedItem(
                id = "kop1_21",
                number = "26097102918",
                range = "260971XXX",
                service = "Facebook",
                country = "Zambia",
                operator = "Airtel Zambia",
                otp = "<#> 619204 is your Facebook code\nH29Q+Fsn4Sr",
                time = now - 155_000L,
                isNew = false
            ),
            BroadcastFeedItem(
                id = "kop1_22",
                number = "26377102918",
                range = "263771XXX",
                service = "WhatsApp",
                country = "Zimbabwe",
                operator = "Econet Zimbabwe",
                otp = "Your WhatsApp code: 820-194",
                time = now - 165_000L,
                isNew = false
            ),
            BroadcastFeedItem(
                id = "kop1_23",
                number = "25471019283",
                range = "254710XXX",
                service = "Facebook",
                country = "Kenya",
                operator = "Safaricom",
                otp = "<#> 481029 is your Facebook code\nLaz+nxCarLW",
                time = now - 175_000L,
                isNew = false
            ),
            BroadcastFeedItem(
                id = "kop1_24",
                number = "23480319283",
                range = "234803XXX",
                service = "Facebook",
                country = "Nigeria",
                operator = "MTN Nigeria",
                otp = "Facebook: Your code is 739201",
                time = now - 185_000L,
                isNew = false
            ),
            BroadcastFeedItem(
                id = "kop1_25",
                number = "23324019283",
                range = "233240XXX",
                service = "Instagram",
                country = "Ghana",
                operator = "MTN Ghana",
                otp = "582910 is your Instagram code. Don't share it.",
                time = now - 195_000L,
                isNew = false
            ),
            BroadcastFeedItem(
                id = "kop1_26",
                number = "25575419283",
                range = "255754XXX",
                service = "Facebook",
                country = "Tanzania",
                operator = "Vodacom Tanzania",
                otp = "<#> 391820 is your Facebook code\nH29Q+Fsn4Sr",
                time = now - 205_000L,
                isNew = false
            ),
            BroadcastFeedItem(
                id = "kop1_27",
                number = "25191119283",
                range = "251911XXX",
                service = "Facebook",
                country = "Ethiopia",
                operator = "Ethio Telecom",
                otp = "<#> 619402 is your Facebook code\nLaz+nxCarLW",
                time = now - 215_000L,
                isNew = false
            ),
            BroadcastFeedItem(
                id = "kop1_28",
                number = "27720119283",
                range = "277201XXX",
                service = "WhatsApp",
                country = "South Africa",
                operator = "Vodacom South Africa",
                otp = "Your WhatsApp code: 918-204",
                time = now - 225_000L,
                isNew = false
            ),
            BroadcastFeedItem(
                id = "kop1_29",
                number = "23674219283",
                range = "236742XXX",
                service = "Facebook",
                country = "Central African Republic",
                operator = "Orange CAR",
                otp = "<#> 204918 is your Facebook code\nH29Q+Fsn4Sr",
                time = now - 235_000L,
                isNew = false
            ),
            BroadcastFeedItem(
                id = "kop1_30",
                number = "22897202918",
                range = "228972XXX",
                service = "Facebook",
                country = "Togo",
                operator = "Togocel",
                otp = "Facebook: Your code is 819203",
                time = now - 245_000L,
                isNew = false
            ),
            BroadcastFeedItem(
                id = "kop1_31",
                number = "22966019283",
                range = "229660XXX",
                service = "WhatsApp",
                country = "Benin",
                operator = "MTN Benin",
                otp = "Your WhatsApp code: 392-108",
                time = now - 255_000L,
                isNew = false
            ),
            BroadcastFeedItem(
                id = "kop1_32",
                number = "22376019284",
                range = "223760XXX",
                service = "Facebook",
                country = "Mali",
                operator = "Orange Mali",
                otp = "<#> 720192 is your Facebook code\nLaz+nxCarLW",
                time = now - 265_000L,
                isNew = false
            ),
            BroadcastFeedItem(
                id = "kop1_33",
                number = "22461019283",
                range = "224610XXX",
                service = "Instagram",
                country = "Guinea",
                operator = "Orange Guinea",
                otp = "491028 is your Instagram code.",
                time = now - 275_000L,
                isNew = false
            ),
            BroadcastFeedItem(
                id = "kop1_34",
                number = "37493019283",
                range = "374930XXX",
                service = "Facebook",
                country = "Armenia",
                operator = "Ucom Armenia",
                otp = "<#> 829103 is your Facebook code\nH29Q+Fsn4Sr",
                time = now - 285_000L,
                isNew = false
            ),
            BroadcastFeedItem(
                id = "kop1_35",
                number = "26133019283",
                range = "261330XXX",
                service = "WhatsApp",
                country = "Madagascar",
                operator = "Airtel Madagascar",
                otp = "Your WhatsApp code: 582-192",
                time = now - 295_000L,
                isNew = false
            ),
            BroadcastFeedItem(
                id = "kop1_36",
                number = "95995019284",
                range = "959950XXX",
                service = "Facebook",
                country = "Myanmar",
                operator = "ATOM Myanmar",
                otp = "<#> 391823 is your Facebook code\nLaz+nxCarLW",
                time = now - 305_000L,
                isNew = false
            ),
            BroadcastFeedItem(
                id = "kop1_37",
                number = "25886019283",
                range = "258860XXX",
                service = "Facebook",
                country = "Mozambique",
                operator = "Movitel",
                otp = "Facebook: Your code is 619283",
                time = now - 315_000L,
                isNew = false
            ),
            BroadcastFeedItem(
                id = "kop1_38",
                number = "22505019283",
                range = "225050XXX",
                service = "Instagram",
                country = "Ivory Coast",
                operator = "MTN CI",
                otp = "819204 is your Instagram code.",
                time = now - 325_000L,
                isNew = false
            ),
            BroadcastFeedItem(
                id = "kop1_39",
                number = "26659019283",
                range = "266590XXX",
                service = "Facebook",
                country = "Lesotho",
                operator = "Econet Telecom",
                otp = "<#> 481920 is your Facebook code\nH29Q+Fsn4Sr",
                time = now - 335_000L,
                isNew = false
            ),
            BroadcastFeedItem(
                id = "kop1_40",
                number = "23675519283",
                range = "236755XXX",
                service = "WhatsApp",
                country = "Central African Republic",
                operator = "Moov CAR",
                otp = "Your WhatsApp code: 739-201",
                time = now - 345_000L,
                isNew = false
            ),
            BroadcastFeedItem(
                id = "kop1_41",
                number = "22899019283",
                range = "228990XXX",
                service = "Facebook",
                country = "Togo",
                operator = "Togocel",
                otp = "<#> 582910 is your Facebook code\nLaz+nxCarLW",
                time = now - 355_000L,
                isNew = false
            ),
            BroadcastFeedItem(
                id = "kop1_42",
                number = "22998019283",
                range = "229980XXX",
                service = "Facebook",
                country = "Benin",
                operator = "Moov Benin",
                otp = "Facebook: Your code is 391028",
                time = now - 365_000L,
                isNew = false
            ),
            BroadcastFeedItem(
                id = "kop1_43",
                number = "22382019284",
                range = "223820XXX",
                service = "Instagram",
                country = "Mali",
                operator = "Orange Mali",
                otp = "204918 is your Instagram code.",
                time = now - 375_000L,
                isNew = false
            ),
            BroadcastFeedItem(
                id = "kop1_44",
                number = "26096119283",
                range = "260961XXX",
                service = "WhatsApp",
                country = "Zambia",
                operator = "MTN Zambia",
                otp = "Your WhatsApp code: 819-203",
                time = now - 385_000L,
                isNew = false
            ),
            BroadcastFeedItem(
                id = "kop1_45",
                number = "26371119283",
                range = "263711XXX",
                service = "Facebook",
                country = "Zimbabwe",
                operator = "NetOne",
                otp = "<#> 918302 is your Facebook code\nH29Q+Fsn4Sr",
                time = now - 395_000L,
                isNew = false
            ),
            BroadcastFeedItem(
                id = "kop1_46",
                number = "25472019283",
                range = "254720XXX",
                service = "Facebook",
                country = "Kenya",
                operator = "Airtel Kenya",
                otp = "Facebook: Your code is 482019",
                time = now - 405_000L,
                isNew = false
            ),
            BroadcastFeedItem(
                id = "kop1_47",
                number = "23481219283",
                range = "234812XXX",
                service = "WhatsApp",
                country = "Nigeria",
                operator = "Airtel Nigeria",
                otp = "Your WhatsApp code: 619-204",
                time = now - 415_000L,
                isNew = false
            ),
            BroadcastFeedItem(
                id = "kop1_48",
                number = "23355019283",
                range = "233550XXX",
                service = "Facebook",
                country = "Ghana",
                operator = "Vodafone Ghana",
                otp = "<#> 720192 is your Facebook code\nLaz+nxCarLW",
                time = now - 425_000L,
                isNew = false
            ),
            BroadcastFeedItem(
                id = "kop1_49",
                number = "25578419283",
                range = "255784XXX",
                service = "Instagram",
                country = "Tanzania",
                operator = "Airtel Tanzania",
                otp = "392019 is your Instagram code.",
                time = now - 435_000L,
                isNew = false
            ),
            BroadcastFeedItem(
                id = "kop1_50",
                number = "22501019283",
                range = "225010XXX",
                service = "Facebook",
                country = "Ivory Coast",
                operator = "Moov CI",
                otp = "<#> 849102 is your Facebook code\nH29Q+Fsn4Sr",
                time = now - 445_000L,
                isNew = false
            )
        )
    }

    fun getKop2Radar50(): List<BroadcastFeedItem> {
        val now = System.currentTimeMillis()
        val countries = listOf(
            Triple("260971XXX", "Zambia", "Airtel"),
            Triple("263771XXX", "Zimbabwe", "Econet"),
            Triple("258849XXX", "Mozambique", "Vodacom"),
            Triple("277201XXX", "South Africa", "Vodacom"),
            Triple("254710XXX", "Kenya", "Safaricom"),
            Triple("234803XXX", "Nigeria", "MTN"),
            Triple("233240XXX", "Ghana", "MTN"),
            Triple("255754XXX", "Tanzania", "Vodacom"),
            Triple("256770XXX", "Uganda", "MTN"),
            Triple("250780XXX", "Rwanda", "MTN"),
            Triple("265990XXX", "Malawi", "Airtel"),
            Triple("267710XXX", "Botswana", "Mascom"),
            Triple("264810XXX", "Namibia", "MTC"),
            Triple("244920XXX", "Angola", "Unitel"),
            Triple("221770XXX", "Senegal", "Orange")
        )
        val services = listOf("Facebook", "WhatsApp", "Instagram")
        val result = mutableListOf<BroadcastFeedItem>()
        for (i in 1..50) {
            val (range, country, op) = countries[(i - 1) % countries.size]
            val service = services[(i - 1) % services.size]
            val pfx = range.replace("X", "").replace("x", "")
            val fullNum = "+$pfx" + String.format("%05d", (10000 + i * 17) % 100000)
            val code = String.format("%06d", (112233 + i * 7919) % 1000000)
            val msg = when (service) {
                "WhatsApp" -> "Your WhatsApp code: ${code.take(3)}-${code.takeLast(3)}"
                "Instagram" -> "$code is your Instagram code. Don't share it."
                else -> "<#> $code is your Facebook code\nLaz+nxCarLW"
            }
            result.add(
                BroadcastFeedItem(
                    id = "kop2_radar_$i",
                    number = fullNum,
                    range = range,
                    service = service,
                    country = country,
                    operator = op,
                    otp = msg,
                    time = now - (i * 12_000L),
                    isNew = i <= 3
                )
            )
        }
        return result
    }

    fun getPremiumV2LiveOtp50(): List<PremiumV2ConsoleItem> {
        val now = System.currentTimeMillis()
        val seeds = listOf(
            Triple("3932", "Italy", "IT" to "🇮🇹"),
            Triple("2519", "Ethiopia", "ET" to "🇪🇹"),
            Triple("3365", "France", "FR" to "🇫🇷"),
            Triple("2348", "Nigeria", "NG" to "🇳🇬"),
            Triple("2547", "Kenya", "KE" to "🇰🇪"),
            Triple("5731", "Colombia", "CO" to "🇨🇴"),
            Triple("2772", "South Africa", "ZA" to "🇿🇦"),
            Triple("4474", "United Kingdom", "GB" to "🇬🇧"),
            Triple("1202", "United States", "US" to "🇺🇸"),
            Triple("4915", "Germany", "DE" to "🇩🇪"),
            Triple("3461", "Spain", "ES" to "🇪🇸"),
            Triple("5511", "Brazil", "BR" to "🇧🇷"),
            Triple("6281", "Indonesia", "ID" to "🇮🇩"),
            Triple("6391", "Philippines", "PH" to "🇵🇭"),
            Triple("9198", "India", "IN" to "🇮🇳"),
            Triple("8490", "Vietnam", "VN" to "🇻🇳"),
            Triple("2010", "Egypt", "EG" to "🇪🇬"),
            Triple("4850", "Poland", "PL" to "🇵🇱"),
            Triple("5255", "Mexico", "MX" to "🇲🇽"),
            Triple("3162", "Netherlands", "NL" to "🇳🇱"),
            Triple("9053", "Turkey", "TR" to "🇹🇷"),
            Triple("5491", "Argentina", "AR" to "🇦🇷"),
            Triple("2126", "Morocco", "MA" to "🇲🇦"),
            Triple("2250", "Ivory Coast", "CI" to "🇨🇮"),
            Triple("1416", "Canada", "CA" to "🇨🇦")
        )

        val services = listOf("FACEBOOK", "WHATSAPP", "INSTAGRAM")
        val result = mutableListOf<PremiumV2ConsoleItem>()

        for (i in 1..50) {
            val (pfx, country, pair) = seeds[(i - 1) % seeds.size]
            val (iso, flag) = pair
            val service = services[(i - 1) % services.size]
            val last4 = String.format("%04d", (1234 + i * 37) % 10000)
            val masked = "+$pfx••••$last4"
            val code = String.format("%06d", (739201 + i * 13579) % 1000000)
            val snippet = when (service) {
                "WHATSAPP" -> "Your WhatsApp code: ${code.take(3)}-${code.takeLast(3)}. You can also tap on this link to verify your phone."
                "INSTAGRAM" -> "$code is your Instagram code. Don't share it."
                else -> if (i % 2 == 0) "<#> ***** is your Facebook code\nH29Q+Fsn4Sr" else "<#> ***** is your Facebook code\nLaz+nxCarLW"
            }
            val minutesAgo = (i * 25) / 60
            val timeAgo = if (i == 1) "just now" else if (minutesAgo <= 1) "1 min ago" else "$minutesAgo mins ago"

            result.add(
                PremiumV2ConsoleItem(
                    id = "v2_live_$i",
                    maskedNumber = masked,
                    rangePrefix = pfx,
                    service = service,
                    country = country,
                    operator = "Carrier Mobile",
                    iso = iso,
                    flag = flag,
                    otpCode = code,
                    rawSnippet = snippet,
                    timeAgo = timeAgo,
                    timestamp = now - (i * 25_000L),
                    isNew = i <= 2
                )
            )
        }
        return result
    }

    fun getUnixSmsCdr50(): List<UnixSmsCdrRecord> {
        val now = System.currentTimeMillis()
        val seeds = listOf(
            Triple("+120255501", "United States", "🇺🇸"),
            Triple("+447400123", "United Kingdom", "🇬🇧"),
            Triple("+141655501", "Canada", "🇨🇦"),
            Triple("+919812345", "India", "🇮🇳"),
            Triple("+234803123", "Nigeria", "🇳🇬"),
            Triple("+237628123", "Cameroon", "🇨🇲"),
            Triple("+254710123", "Kenya", "🇰🇪"),
            Triple("+628123456", "Indonesia", "🇮🇩"),
            Triple("+639171234", "Philippines", "🇵🇭"),
            Triple("+336512345", "France", "🇫🇷"),
            Triple("+491512345", "Germany", "🇩🇪"),
            Triple("+923001234", "Pakistan", "🇵🇰"),
            Triple("+551191234", "Brazil", "🇧🇷"),
            Triple("+849012345", "Vietnam", "🇻🇳"),
            Triple("+880171234", "Bangladesh", "🇧🇩")
        )

        val services = listOf("FACEBOOK", "WHATSAPP", "INSTAGRAM")
        val result = mutableListOf<UnixSmsCdrRecord>()

        for (i in 1..50) {
            val (base, country, flag) = seeds[(i - 1) % seeds.size]
            val service = services[(i - 1) % services.size]
            val num = base + String.format("%02d", (10 + i) % 100)
            val code = String.format("%06d", (819203 + i * 4921) % 1000000)
            val msg = when (service) {
                "WHATSAPP" -> "Your WhatsApp code: ${code.take(3)}-${code.takeLast(3)}"
                "INSTAGRAM" -> "$code is your Instagram verification code."
                else -> "<#> $code is your Facebook code Laz+nxCarLW"
            }
            val timeStr = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date(now - i * 18_000L))
            result.add(
                UnixSmsCdrRecord(
                    dt = timeStr,
                    num = num,
                    cli = service,
                    message = msg,
                    payout = "0.50 ৳",
                    countryName = country,
                    countryFlag = flag,
                    timestamp = now - (i * 18_000L)
                )
            )
        }
        return result
    }
}
