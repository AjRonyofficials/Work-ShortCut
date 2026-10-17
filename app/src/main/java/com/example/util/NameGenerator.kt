package com.example.util

data class CountryOption(
    val code: String,
    val name: String,
    val flag: String,
    val phoneCode: String = ""
)

enum class Gender {
    MALE, FEMALE, RANDOM, ANY
}

object NameGenerator {

    val commonCountries = listOf(
        CountryOption("US", "United States", "🇺🇸", "+1"),
        CountryOption("BD", "Bangladesh", "🇧🇩", "+880"),
        CountryOption("GB", "United Kingdom", "🇬🇧", "+44"),
        CountryOption("CA", "Canada", "🇨🇦", "+1"),
        CountryOption("IN", "India", "🇮🇳", "+91"),
        CountryOption("PK", "Pakistan", "🇵🇰", "+92"),
        CountryOption("DE", "Germany", "🇩🇪", "+49"),
        CountryOption("FR", "France", "🇫🇷", "+33"),
        CountryOption("AU", "Australia", "🇦🇺", "+61")
    )

    val allCountries: List<CountryOption> = AllCountriesProvider.allCountries.map {
        CountryOption(it.code, it.name, it.flag, it.dialCode)
    }

    private val namesByCountry = mapOf(
        "US" to Pair(
            listOf("James", "John", "Robert", "Michael", "William", "David", "Richard", "Joseph", "Thomas", "Charles", "Daniel", "Matthew", "Anthony", "Donald", "Mark"),
            listOf("Mary", "Patricia", "Jennifer", "Linda", "Elizabeth", "Barbara", "Susan", "Jessica", "Sarah", "Karen", "Nancy", "Lisa", "Betty", "Margaret", "Sandra")
        ),
        "BD" to Pair(
            listOf("Arif", "Rahim", "Tanvir", "Siam", "Shakil", "Mahmud", "Sabbir", "Mehedi", "Fahim", "Hasan", "Rakib", "Alamin", "Imran", "Nayeem", "Saiful"),
            listOf("Nusrat", "Sadia", "Tahmina", "Ayesha", "Zara", "Maya", "Farhana", "Mousumi", "Sharmin", "Jannat", "Tasnim", "Sultana", "Rima", "Pooja", "Rumana")
        ),
        "GB" to Pair(
            listOf("Oliver", "George", "Harry", "Noah", "Jack", "Leo", "Arthur", "Muhammad", "Oscar", "Charlie", "Jacob", "Thomas", "Henry", "William", "Edward"),
            listOf("Olivia", "Amelia", "Isla", "Ava", "Mia", "Grace", "Freya", "Lily", "Sophia", "Ivy", "Florence", "Willow", "Evie", "Phoebe", "Ella")
        ),
        "IN" to Pair(
            listOf("Aarav", "Vihaan", "Aditya", "Sai", "Reyansh", "Arjun", "Krishna", "Ishaan", "Shaurya", "Atharva", "Aayush", "Dhruv", "Kabir", "Rudra", "Rohan"),
            listOf("Aanya", "Diya", "Saanvi", "Ananya", "Aadhya", "Pari", "Isha", "Navya", "Riya", "Myra", "Avani", "Kiara", "Kavya", "Anika", "Tara")
        )
    )

    private val lastNames = listOf(
        "Smith", "Johnson", "Williams", "Brown", "Jones", "Garcia", "Miller", "Davis",
        "Rodriguez", "Martinez", "Hernandez", "Lopez", "Gonzalez", "Wilson", "Anderson",
        "Rahman", "Ahmed", "Chowdhury", "Khan", "Hossain", "Ali", "Islam", "Hasan"
    )

    fun getCountryOption(code: String): CountryOption {
        return allCountries.firstOrNull { it.code.equals(code, ignoreCase = true) }
            ?: commonCountries.firstOrNull { it.code.equals(code, ignoreCase = true) }
            ?: CountryOption(code, code, countryCodeToEmojiFlag(code))
    }

    fun countryCodeToEmojiFlag(countryCode: String): String {
        if (countryCode.length != 2) return "🌐"
        val firstChar = Character.codePointAt(countryCode.uppercase(), 0) - 0x41 + 0x1F1E6
        val secondChar = Character.codePointAt(countryCode.uppercase(), 1) - 0x41 + 0x1F1E6
        return String(Character.toChars(firstChar)) + String(Character.toChars(secondChar))
    }

    fun generateName(countryCode: String, gender: Gender = Gender.RANDOM): String = generateRealtimeName(countryCode, gender)

    fun generateRealtimeName(countryCode: String, gender: Gender = Gender.RANDOM): String {
        val (males, females) = namesByCountry[countryCode.uppercase()] ?: namesByCountry["US"]!!
        val isMale = when (gender) {
            Gender.MALE -> true
            Gender.FEMALE -> false
            Gender.RANDOM, Gender.ANY -> (0..1).random() == 0
        }
        val first = if (isMale) males.random() else females.random()
        val last = lastNames.random()
        return "$first $last"
    }

    fun generateNameList(countryCode: String, gender: Gender, count: Int): List<String> {
        val list = mutableListOf<String>()
        val safeCount = count.coerceIn(1, 20)
        repeat(safeCount) {
            list.add(generateRealtimeName(countryCode, gender))
        }
        return list.distinct()
    }
}
