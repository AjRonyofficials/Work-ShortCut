package com.example.util

import kotlin.random.Random

data class CountryOption(
    val code: String,
    val name: String,
    val flag: String
)

enum class Gender {
    MALE, FEMALE, ANY
}

object NameGenerator {

    val commonCountries = listOf(
        CountryOption("BD", "Bangladesh", "🇧🇩"),
        CountryOption("US", "United States", "🇺🇸"),
        CountryOption("UK", "United Kingdom", "🇬🇧"),
        CountryOption("IN", "India", "🇮🇳"),
        CountryOption("PK", "Pakistan", "🇵🇰"),
        CountryOption("SA", "Saudi Arabia", "🇸🇦"),
        CountryOption("AE", "United Arab Emirates", "🇦🇪"),
        CountryOption("CA", "Canada", "🇨🇦"),
        CountryOption("AU", "Australia", "🇦🇺"),
        CountryOption("DE", "Germany", "🇩🇪"),
        CountryOption("FR", "France", "🇫🇷"),
        CountryOption("IT", "Italy", "🇮🇹"),
        CountryOption("ES", "Spain", "🇪🇸"),
        CountryOption("TR", "Turkey", "🇹🇷"),
        CountryOption("BR", "Brazil", "🇧🇷"),
        CountryOption("JP", "Japan", "🇯🇵")
    )

    val supportedCountries = commonCountries

    val allCountries = listOf(
        CountryOption("AF", "Afghanistan", "🇦🇫"),
        CountryOption("AL", "Albania", "🇦🇱"),
        CountryOption("DZ", "Algeria", "🇩🇿"),
        CountryOption("AR", "Argentina", "🇦🇷"),
        CountryOption("AM", "Armenia", "🇦🇲"),
        CountryOption("AU", "Australia", "🇦🇺"),
        CountryOption("AT", "Austria", "🇦🇹"),
        CountryOption("AZ", "Azerbaijan", "🇦🇿"),
        CountryOption("BH", "Bahrain", "🇧🇭"),
        CountryOption("BD", "Bangladesh", "🇧🇩"),
        CountryOption("BY", "Belarus", "🇧🇾"),
        CountryOption("BE", "Belgium", "🇧🇪"),
        CountryOption("BO", "Bolivia", "🇧🇴"),
        CountryOption("BA", "Bosnia", "🇧🇦"),
        CountryOption("BR", "Brazil", "🇧🇷"),
        CountryOption("BG", "Bulgaria", "🇧🇬"),
        CountryOption("KH", "Cambodia", "🇰🇭"),
        CountryOption("CM", "Cameroon", "🇨🇲"),
        CountryOption("CA", "Canada", "🇨🇦"),
        CountryOption("CL", "Chile", "🇨🇱"),
        CountryOption("CN", "China", "🇨🇳"),
        CountryOption("CO", "Colombia", "🇨🇴"),
        CountryOption("CR", "Costa Rica", "🇨🇷"),
        CountryOption("HR", "Croatia", "🇭🇷"),
        CountryOption("CY", "Cyprus", "🇨🇾"),
        CountryOption("CZ", "Czech Republic", "🇨🇿"),
        CountryOption("DK", "Denmark", "🇩🇰"),
        CountryOption("EG", "Egypt", "🇪🇬"),
        CountryOption("EE", "Estonia", "🇪🇪"),
        CountryOption("FI", "Finland", "🇫🇮"),
        CountryOption("FR", "France", "🇫🇷"),
        CountryOption("GE", "Georgia", "🇬🇪"),
        CountryOption("DE", "Germany", "🇩🇪"),
        CountryOption("GH", "Ghana", "🇬🇭"),
        CountryOption("GR", "Greece", "🇬🇷"),
        CountryOption("HK", "Hong Kong", "🇭🇰"),
        CountryOption("HU", "Hungary", "🇭🇺"),
        CountryOption("IS", "Iceland", "🇮🇸"),
        CountryOption("IN", "India", "🇮🇳"),
        CountryOption("ID", "Indonesia", "🇮🇩"),
        CountryOption("IR", "Iran", "🇮🇷"),
        CountryOption("IQ", "Iraq", "🇮🇶"),
        CountryOption("IE", "Ireland", "🇮🇪"),
        CountryOption("IL", "Israel", "🇮🇱"),
        CountryOption("IT", "Italy", "🇮🇹"),
        CountryOption("JM", "Jamaica", "🇯🇲"),
        CountryOption("JP", "Japan", "🇯🇵"),
        CountryOption("JO", "Jordan", "🇯🇴"),
        CountryOption("KZ", "Kazakhstan", "🇰🇿"),
        CountryOption("KE", "Kenya", "🇰🇪"),
        CountryOption("KW", "Kuwait", "🇰🇼"),
        CountryOption("LB", "Lebanon", "🇱🇧"),
        CountryOption("MY", "Malaysia", "🇲🇾"),
        CountryOption("MV", "Maldives", "🇲🇻"),
        CountryOption("MX", "Mexico", "🇲🇽"),
        CountryOption("MA", "Morocco", "🇲🇦"),
        CountryOption("NP", "Nepal", "🇳🇵"),
        CountryOption("NL", "Netherlands", "🇳🇱"),
        CountryOption("NZ", "New Zealand", "🇳🇿"),
        CountryOption("NG", "Nigeria", "🇳🇬"),
        CountryOption("NO", "Norway", "🇳🇴"),
        CountryOption("OM", "Oman", "🇴🇲"),
        CountryOption("PK", "Pakistan", "🇵🇰"),
        CountryOption("PA", "Panama", "🇵🇦"),
        CountryOption("PE", "Peru", "🇵🇪"),
        CountryOption("PH", "Philippines", "🇵🇭"),
        CountryOption("PL", "Poland", "🇵🇱"),
        CountryOption("PT", "Portugal", "🇵🇹"),
        CountryOption("QA", "Qatar", "🇶🇦"),
        CountryOption("RO", "Romania", "🇷🇴"),
        CountryOption("RU", "Russia", "🇷🇺"),
        CountryOption("SA", "Saudi Arabia", "🇸🇦"),
        CountryOption("RS", "Serbia", "🇷🇸"),
        CountryOption("SG", "Singapore", "🇸🇬"),
        CountryOption("ZA", "South Africa", "🇿🇦"),
        CountryOption("KR", "South Korea", "🇰🇷"),
        CountryOption("ES", "Spain", "🇪🇸"),
        CountryOption("LK", "Sri Lanka", "🇱🇰"),
        CountryOption("SE", "Sweden", "🇸🇪"),
        CountryOption("CH", "Switzerland", "🇨🇭"),
        CountryOption("TW", "Taiwan", "🇹🇼"),
        CountryOption("TH", "Thailand", "🇹🇭"),
        CountryOption("TR", "Turkey", "🇹🇷"),
        CountryOption("UA", "Ukraine", "🇺🇦"),
        CountryOption("AE", "United Arab Emirates", "🇦🇪"),
        CountryOption("UK", "United Kingdom", "🇬🇧"),
        CountryOption("US", "United States", "🇺🇸"),
        CountryOption("UZ", "Uzbekistan", "🇺🇿"),
        CountryOption("VN", "Vietnam", "🇻🇳"),
        CountryOption("YE", "Yemen", "🇾🇪"),
        CountryOption("ZW", "Zimbabwe", "🇿🇼")
    )

    private val namesByCountry: Map<String, CountryNames> = mapOf(
        "BD" to CountryNames(
            malePrefixes = listOf("Md.", "Mohammad", "Kazi", "Syed", "Sheikh", "Al-Hajj", "Mir", "Dewan"),
            femalePrefixes = listOf("Mst.", "Syeda", "Kazi", "Begum", "Dr.", "Jannatul", "Farhana"),
            maleFirst = listOf(
                "Tanvir", "Rafiqul", "Sabbir", "Arif", "Mahmudul", "Hasan", "Shakil", "Rashed",
                "Tareq", "Kamrul", "Nazmul", "Ashik", "Mehedi", "Faisal", "Imran", "Farhan",
                "Zubair", "Rayhan", "Tamim", "Mustafiz", "Anik", "Nayeem", "Saiful", "Sharif",
                "Rakib", "Rony", "Biplob", "Nahid", "Sajid", "Alamin", "Shahadat", "Jubayer",
                "Sohag", "Jahid", "Shimul", "Sohel", "Mamun", "Monir", "Mizan", "Ripon",
                "Rubel", "Liton", "Riad", "Mahfuz", "Anwar", "Jamil", "Asif", "Enamul",
                "Habib", "Harun", "Jashim", "Khaled", "Lutfor", "Masud", "Nizam", "Parvez",
                "Quddus", "Ruhul", "Sattar", "Tariqul", "Wahid", "Zakir", "Afzal", "Badal",
                "Chanchal", "Delwar", "Ehsan", "Ferdous", "Golam", "Halim", "Iqbal", "Jalal",
                "Kamal", "Liaquat", "Motahar", "Nasir", "Osman", "Pappu", "Robiul", "Shaon",
                "Touhid", "Uzzal", "Zillur", "Ahsan", "Barkat", "Fahim", "Gias", "Hafiz",
                "Ismail", "Jannat", "Khorshed", "Latif", "Mokbul", "Nurul", "Rashedul", "Shihab",
                "Tajul", "Zahidul", "Sohan", "Shanto", "Taijul", "Taskin", "Soumya", "Nasum"
            ),
            femaleFirst = listOf(
                "Nusrat", "Farhana", "Sumaiya", "Tanjina", "Sharmin", "Sultana", "Sadia", "Rimi",
                "Jannatul", "Afsana", "Tasnim", "Mousumi", "Nabila", "Sabrina", "Fariha", "Anika",
                "Samia", "Priyanka", "Rumana", "Tania", "Mehnaz", "Nazia", "Mithila", "Lamia",
                "Ayesha", "Fatema", "Roksana", "Tahmina", "Naznin", "Shirin", "Shampa", "Munira",
                "Marufa", "Rubina", "Khadiza", "Bristy", "Nigar", "Salma", "Rehana", "Kohinur",
                "Bilkis", "Shahnaz", "Asma", "Monira", "Khatun", "Ferdousi", "Afroza", "Dilruba",
                "Hasina", "Mahbuba", "Rokeya", "Suraiya", "Tahsin", "Umaiza", "Wasima", "Yasmin",
                "Zinat", "Aditi", "Barsha", "Chaity", "Dipa", "Elora", "Farzana", "Gita",
                "Humaira", "Iffat", "Jarin", "Keya", "Lipika", "Moni", "Nahid", "Poly",
                "Ratna", "Shathi", "Tumpa", "Urbi", "Bithi", "Puja", "Shoma", "Champa",
                "Dalia", "Laboni", "Mohua", "Nipa", "Popy", "Rozina", "Shilpi", "Tarana"
            ),
            lastNames = listOf(
                "Ahmed", "Islam", "Hasan", "Rahman", "Hossain", "Chowdhury", "Khan", "Ali",
                "Akter", "Khatun", "Uddin", "Bhuiyan", "Sikder", "Miah", "Sarkar", "Kabir",
                "Talukder", "Munshi", "Hawlader", "Gazi", "Mallik", "Bepari", "Pramanik", "Khandakar",
                "Mazumder", "Mondal", "Biswas", "Barua", "Pal", "Dey", "Das", "Roy",
                "Bhowmik", "Ghosh", "Majumdar", "Chakraborty", "Samaddar", "Howlader", "Patwary", "Laskar",
                "Mia", "Sheikh", "Dewan", "Mulla", "Qazi", "Mirza", "Akond", "Bari",
                "Tarafdar", "Chakma", "Marma", "Gomes", "Rozario", "Costa", "Purkayastha", "Kazi"
            )
        ),
        "US" to CountryNames(
            malePrefixes = listOf("", "", "", "Jr.", "II", "III"),
            femalePrefixes = listOf("", "", "", "Dr.", "Prof."),
            maleFirst = listOf(
                "James", "Michael", "Ethan", "Alexander", "Daniel", "Matthew", "Lucas", "Noah",
                "Oliver", "William", "Benjamin", "Henry", "Jackson", "Mason", "Jack", "Samuel",
                "Liam", "Elijah", "Aiden", "Theodore", "Sebastian", "John", "David", "Wyatt",
                "Carter", "Julian", "Luke", "Grayson", "Isaac", "Jayden", "Gabriel", "Anthony",
                "Dylan", "Leo", "Lincoln", "Jaxon", "Asher", "Christopher", "Josiah", "Andrew",
                "Thomas", "Joshua", "Ezra", "Hudson", "Charles", "Caleb", "Isaiah", "Ryan",
                "Nathan", "Adrian", "Christian", "Maverick", "Colton", "Elias", "Aaron", "Eli",
                "Landon", "Jonathan", "Nolan", "Hunter", "Cameron", "Connor", "Santiago", "Jeremiah",
                "Ezekiel", "Angel", "Roman", "Easton", "Miles", "Robert", "Jameson", "Nicholas",
                "Greyson", "Cooper", "Ian", "Carson", "Axel", "Jaxson", "Dominic", "Leonardo"
            ),
            femaleFirst = listOf(
                "Emma", "Olivia", "Ava", "Sophia", "Isabella", "Mia", "Charlotte", "Amelia",
                "Harper", "Evelyn", "Abigail", "Emily", "Elizabeth", "Chloe", "Grace", "Zoey",
                "Mila", "Ella", "Avery", "Scarlett", "Eleanor", "Madison", "Layla", "Penelope",
                "Aria", "Riley", "Nora", "Lily", "Aubrey", "Hannah", "Lillian", "Addison",
                "Aubree", "Stella", "Natalie", "Zoe", "Leah", "Hazel", "Violet", "Aurora",
                "Savannah", "Audrey", "Brooklyn", "Bella", "Claire", "Skylar", "Lucy", "Paisley",
                "Everly", "Anna", "Caroline", "Nova", "Genesis", "Emilia", "Kennedy", "Samantha",
                "Maya", "Willow", "Kinsley", "Naomi", "Aaliyah", "Elena", "Sarah", "Ariana",
                "Allison", "Gabriella", "Alice", "Madelyn", "Cora", "Ruby", "Eva", "Serenity",
                "Autumn", "Adeline", "Hailey", "Gianna", "Valentina", "Isla", "Eliana", "Quinn"
            ),
            lastNames = listOf(
                "Smith", "Johnson", "Williams", "Brown", "Jones", "Miller", "Davis", "Wilson",
                "Anderson", "Taylor", "Thomas", "Moore", "Jackson", "Martin", "Lee", "White",
                "Harris", "Clark", "Lewis", "Robinson", "Walker", "Young", "Allen", "King",
                "Wright", "Scott", "Torres", "Nguyen", "Hill", "Flores", "Green", "Adams",
                "Nelson", "Baker", "Hall", "Rivera", "Campbell", "Mitchell", "Carter", "Roberts",
                "Gomez", "Phillips", "Evans", "Turner", "Diaz", "Parker", "Cruz", "Edwards",
                "Collins", "Reyes", "Stewart", "Morris", "Morales", "Murphy", "Cook", "Rogers",
                "Gutierrez", "Ortiz", "Morgan", "Cooper", "Peterson", "Bailey", "Reed", "Kelly",
                "Howard", "Ramos", "Kim", "Cox", "Ward", "Richardson", "Watson", "Brooks",
                "Chavez", "Wood", "James", "Bennett", "Gray", "Mendoza", "Ruiz", "Hughes"
            )
        ),
        "UK" to CountryNames(
            malePrefixes = listOf("", "", "", "Sir", "Lord"),
            femalePrefixes = listOf("", "", "", "Lady", "Dame"),
            maleFirst = listOf(
                "Oliver", "George", "Arthur", "Noah", "Muhammad", "Leo", "Oscar", "Harry",
                "Archie", "Jack", "Henry", "Charlie", "Freddie", "Theodore", "Thomas", "Finley",
                "Alfie", "Jacob", "William", "Isaac", "Tommy", "Joshua", "Alexander", "Edward",
                "Lucas", "James", "Max", "Albie", "Roman", "Teddy", "Logan", "Harrison",
                "Mason", "Daniel", "Ethan", "Sebastian", "Arlo", "Adam", "Reggie", "Caleb",
                "Jaxon", "Ronnie", "Dylan", "Hugo", "Zachary", "Albert", "Joseph", "Elijah",
                "Reuben", "David", "Jude", "Samuel", "Hunter", "Benjamin", "Luca", "Louis",
                "Stanley", "Sonny", "Frankie", "Gabriel", "Elliot", "Toby", "Jesse", "Chester",
                "Carter", "Harvey", "Harvey", "Harley", "Stanley", "Rowan", "Felix", "Brody"
            ),
            femaleFirst = listOf(
                "Olivia", "Amelia", "Isla", "Ava", "Mia", "Ivy", "Lily", "Isabella",
                "Rosie", "Sophia", "Grace", "Freya", "Willow", "Florence", "Emily", "Ella",
                "Poppy", "Evie", "Elsie", "Charlotte", "Sienna", "Daisy", "Phoebe", "Hallie",
                "Harper", "Alice", "Jessica", "Sophie", "Maya", "Ruby", "Matilda", "Evelyn",
                "Layla", "Maisie", "Millie", "Chloe", "Eleanor", "Mila", "Imogen", "Esme",
                "Violet", "Penelope", "Luna", "Harriet", "Lottie", "Thea", "Zara", "Ada",
                "Arabella", "Delilah", "Georgia", "Bonnie", "Darcey", "Robyn", "Nancy", "Amber",
                "Rose", "Emma", "Molly", "Orla", "Clara", "Erin", "Lola", "Annabelle"
            ),
            lastNames = listOf(
                "Smith", "Jones", "Taylor", "Brown", "Williams", "Wilson", "Johnson", "Davies",
                "Robinson", "Wright", "Thompson", "Evans", "Walker", "White", "Roberts", "Green",
                "Hall", "Thomas", "Clarke", "Jackson", "Wood", "Harris", "Edwards", "Turner",
                "Martin", "Cooper", "Hill", "Ward", "Hughes", "Moore", "Clark", "King",
                "Harrison", "Lewis", "Baker", "Patel", "Young", "Allen", "Anderson", "Phillips",
                "Lee", "Bell", "Parker", "Davis", "Bennett", "Miller", "Shaw", "Cook",
                "Richardson", "Marshall", "Collins", "Carter", "Bailey", "Cox", "Simpson", "Price"
            )
        ),
        "IN" to CountryNames(
            malePrefixes = listOf("", "", "", "Sri", "Pandit"),
            femalePrefixes = listOf("", "", "", "Smt.", "Kumari"),
            maleFirst = listOf(
                "Aarav", "Vihaan", "Aditya", "Arjun", "Reyansh", "Sai", "Aayush", "Rohan",
                "Ishaan", "Dhruv", "Kabir", "Aryan", "Aniket", "Dev", "Vikram", "Rahul",
                "Akash", "Kunal", "Sameer", "Gaurav", "Manish", "Deepak", "Sachin", "Naveen",
                "Amit", "Pranav", "Harsh", "Varun", "Siddharth", "Abhishek", "Suraj", "Nikhil",
                "Vivek", "Anand", "Ravi", "Ashish", "Suresh", "Dinesh", "Mohit", "Sunil",
                "Rajesh", "Pankaj", "Alok", "Shantanu", "Mayank", "Utkarsh", "Chirag", "Yash",
                "Tushar", "Hemant", "Neeraj", "Parth", "Tanmay", "Karan", "Kartik", "Girish",
                "Hitesh", "Jayant", "Lakshya", "Manoj", "Omkar", "Prashant", "Rakesh", "Sandip",
                "Tejas", "Uday", "Vikas", "Yogesh", "Bhavesh", "Chetan", "Darshan", "Eknath"
            ),
            femaleFirst = listOf(
                "Aadhya", "Diya", "Saanvi", "Ananya", "Pari", "Isha", "Navya", "Riya",
                "Myra", "Kavya", "Avani", "Tanvi", "Shreya", "Anushka", "Pooja", "Deepika",
                "Sneha", "Priyanka", "Neha", "Megha", "Swati", "Aditi", "Komal", "Sonal",
                "Ritu", "Pallavi", "Divya", "Nisha", "Kritika", "Simran", "Vanshika", "Bhavna",
                "Preeti", "Payal", "Rashmi", "Shruti", "Monika", "Jyoti", "Archana", "Sunita",
                "Anita", "Geeta", "Shilpa", "Kavita", "Sapna", "Rekha", "Sarita", "Mamta",
                "Aakanksha", "Bhumika", "Charu", "Drishti", "Garima", "Harshita", "Ishita", "Jhanvi",
                "Khushi", "Lavanya", "Muskan", "Nandini", "Prachi", "Radhika", "Sakshi", "Trisha"
            ),
            lastNames = listOf(
                "Sharma", "Verma", "Gupta", "Malhotra", "Bhatia", "Saxena", "Mehta", "Patel",
                "Chopra", "Reddy", "Nair", "Iyer", "Mukherjee", "Banerjee", "Singh", "Das",
                "Joshi", "Deshmukh", "Kulkarni", "Patil", "Shinde", "Pawar", "Bose", "Ghosh",
                "Chatterjee", "Dutta", "Sen", "Roy", "Mishra", "Pandey", "Tiwari", "Dubey",
                "Shukla", "Yadav", "Chauhan", "Rajput", "Thakur", "Maurya", "Agrawal", "Jain",
                "Bansal", "Goyal", "Mittal", "Singhal", "Garg", "Bhardwaj", "Choudhary", "Purohit",
                "Naidu", "Rao", "Pillai", "Menon", "Bhattacharya", "Majumdar", "Biswas", "Dhar"
            )
        ),
        "PK" to CountryNames(
            malePrefixes = listOf("Muhammad", "Syed", "Chaudhry", "Malik", "Mian", "Sardar"),
            femalePrefixes = listOf("Syeda", "Begum", "Dr.", "Bibi"),
            maleFirst = listOf(
                "Muhammad", "Ahmed", "Hamza", "Bilal", "Ali", "Usman", "Hassan", "Zain", "Farhan", "Shahid",
                "Babar", "Shaheen", "Rizwan", "Haris", "Naseem", "Shadab", "Imad", "Fakhar", "Asif", "Shoaib",
                "Wasim", "Waqar", "Javed", "Inzamam", "Saeed", "Younis", "Misbah", "Sarfaraz", "Kamran", "Umar",
                "Danish", "Yasir", "Junaid", "Sohail", "Aamer", "Wahab", "Faheem", "Khushdil", "Shan", "Abdullah"
            ),
            femaleFirst = listOf(
                "Ayesha", "Fatima", "Zainab", "Maryam", "Sana", "Hira", "Anum", "Laiba", "Khadija", "Nimra",
                "Mahira", "Mehwish", "Saba", "Sajal", "Yumna", "Ayeza", "Iqra", "Kinza", "Hania", "Minal",
                "Aiman", "Maya", "Hareem", "Sarah", "Alizeh", "Kubra", "Neelam", "Sanam", "Urwa", "Mawra",
                "Zarnish", "Saboor", "Ramsha", "Momina", "Aima", "Nida", "Shaista", "Samina", "Bushra", "Farah"
            ),
            lastNames = listOf(
                "Khan", "Malik", "Chaudhry", "Bhatti", "Butt", "Qureshi", "Siddiqui", "Abbasi", "Sheikh", "Raza",
                "Mirza", "Baig", "Mughal", "Ansari", "Farooqi", "Hashmi", "Gillani", "Bukhari", "Kazmi", "Naqvi",
                "Gondal", "Cheema", "Warraich", "Tarar", "Bajwa", "Wattoo", "Dogar", "Awan", "Kharal", "Janjua"
            )
        ),
        "SA" to CountryNames(
            malePrefixes = listOf("Sheikh", "Al-Amir", "Abu", "Dr."),
            femalePrefixes = listOf("Sheikha", "Um", "Dr."),
            maleFirst = listOf(
                "Mohammed", "Abdullah", "Abdulaziz", "Fahad", "Saud", "Omar", "Khaled", "Ali", "Sultan", "Nasser",
                "Turki", "Bandar", "Majed", "Faisal", "Nayef", "Bader", "Salman", "Waleed", "Mansour", "Talal"
            ),
            femaleFirst = listOf(
                "Fatima", "Sarah", "Reem", "Noura", "Lama", "Haya", "Maha", "Hanan", "Leen", "Danah",
                "Joud", "Shahad", "Raghad", "Tala", "Deema", "Lulwa", "Ghaida", "Rawan", "Alanoud", "Basma"
            ),
            lastNames = listOf(
                "Al-Ghamdi", "Al-Zahrani", "Al-Otaibi", "Al-Shehri", "Al-Harbi", "Al-Dossari", "Al-Qahtani", "Al-Mutairi",
                "Al-Subaie", "Al-Shahrani", "Al-Khaldi", "Al-Anazi", "Al-Rashidi", "Al-Shamrani", "Al-Ghamdi", "Al-Husseini"
            )
        ),
        "AE" to CountryNames(
            malePrefixes = listOf("Sheikh", "Al-Sayed"),
            femalePrefixes = listOf("Sheikha", "Al-Sayeda"),
            maleFirst = listOf(
                "Zayed", "Rashid", "Hamdan", "Saeed", "Mansoor", "Majid", "Khalifa", "Sultan", "Hazza", "Maktoum",
                "Ahmed", "Mohammed", "Salem", "Obaid", "Suhail", "Matar", "Khalfan", "Thani", "Butti", "Tahnoon"
            ),
            femaleFirst = listOf(
                "Mariam", "Fatima", "Shamsa", "Meera", "Latifa", "Salama", "Mouza", "Maitha", "Afra", "Hind",
                "Sheikha", "Roudha", "Alia", "Asma", "Kholoud", "Manal", "Amna", "Hessa", "Rawdha", "Moza"
            ),
            lastNames = listOf(
                "Al-Falasi", "Al-Maktoum", "Al-Nuaimi", "Al-Suwaidi", "Al-Zaabi", "Al-Mazrouei", "Al-Ketbi", "Al-Ameri",
                "Al-Shamsi", "Al-Romaithi", "Al-Dhaheri", "Al-Mansoori", "Al-Qassimi", "Al-Sharqi", "Al-Mualla", "Al-Hameli"
            )
        ),
        "CA" to CountryNames(
            malePrefixes = listOf("", ""),
            femalePrefixes = listOf("", ""),
            maleFirst = listOf(
                "Liam", "Noah", "William", "Lucas", "Leo", "Benjamin", "Oliver", "Jack", "Nathan", "Felix",
                "Samuel", "Logan", "Jacob", "Thomas", "Ethan", "Alexander", "Gabriel", "Theo", "Owen", "Adam"
            ),
            femaleFirst = listOf(
                "Olivia", "Emma", "Charlotte", "Amelia", "Sophia", "Chloe", "Mia", "Alice", "Florence", "Lea",
                "Zoe", "Hannah", "Clara", "Beatrice", "Rosalie", "Juliette", "Mila", "Maya", "Eva", "Victoria"
            ),
            lastNames = listOf(
                "Tremblay", "Roy", "Gagnon", "Cote", "Bouchard", "Smith", "Brown", "Campbell", "MacDonald", "Stewart",
                "Morin", "Fortin", "Gauthier", "Pelletier", "Belanger", "Levesque", "Wilson", "Anderson", "Taylor", "Johnson"
            )
        ),
        "AU" to CountryNames(
            malePrefixes = listOf("", ""),
            femalePrefixes = listOf("", ""),
            maleFirst = listOf(
                "Oliver", "Noah", "William", "Jack", "Leo", "Henry", "Charlie", "Thomas", "Lucas", "Hudson",
                "Liam", "Alexander", "Harrison", "Lachlan", "James", "Max", "Oscar", "Mason", "Cooper", "Archie"
            ),
            femaleFirst = listOf(
                "Charlotte", "Olivia", "Amelia", "Isla", "Mia", "Ava", "Grace", "Willow", "Harper", "Chloe",
                "Ruby", "Sophie", "Ivy", "Zoe", "Matilda", "Ella", "Evie", "Georgia", "Audrey", "Lily"
            ),
            lastNames = listOf(
                "Smith", "Jones", "Williams", "Brown", "Wilson", "Taylor", "Johnson", "White", "Martin", "Anderson",
                "Thompson", "Nguyen", "Thomas", "Walker", "Harris", "Lee", "Ryan", "Robinson", "Kelly", "King"
            )
        ),
        "DE" to CountryNames(
            malePrefixes = listOf("", ""),
            femalePrefixes = listOf("", ""),
            maleFirst = listOf(
                "Lukas", "Leon", "Finn", "Paul", "Jonas", "Felix", "Maximilian", "Tim", "Niklas", "Jan",
                "Ben", "Elias", "Noah", "Luis", "Luca", "Moritz", "Julian", "David", "Philipp", "Alexander"
            ),
            femaleFirst = listOf(
                "Emma", "Mia", "Hannah", "Emilia", "Sofia", "Lina", "Marie", "Mila", "Ella", "Clara",
                "Lea", "Anna", "Lena", "Laura", "Lara", "Johanna", "Sarah", "Leonie", "Emily", "Sophie"
            ),
            lastNames = listOf(
                "Müller", "Schmidt", "Schneider", "Fischer", "Weber", "Meyer", "Wagner", "Becker", "Schulz", "Hoffmann",
                "Schäfer", "Koch", "Bauer", "Richter", "Klein", "Wolf", "Schröder", "Neumann", "Schwarz", "Zimmermann"
            )
        ),
        "FR" to CountryNames(
            malePrefixes = listOf("", ""),
            femalePrefixes = listOf("", ""),
            maleFirst = listOf(
                "Gabriel", "Leo", "Raphael", "Arthur", "Louis", "Lucas", "Adam", "Jules", "Hugo", "Mael",
                "Liam", "Noah", "Paul", "Ethan", "Sacha", "Tom", "Gaspard", "Alexandre", "Nolan", "Enzo"
            ),
            femaleFirst = listOf(
                "Jade", "Louise", "Emma", "Alice", "Ambre", "Lina", "Rose", "Chloe", "Mia", "Lea",
                "Anna", "Mila", "Julia", "Romy", "Ines", "Lena", "Agathe", "Iris", "Elena", "Zoe"
            ),
            lastNames = listOf(
                "Martin", "Bernard", "Thomas", "Petit", "Robert", "Richard", "Durand", "Dubois", "Moreau", "Laurent",
                "Simon", "Michel", "Lefebvre", "Leroy", "Roux", "David", "Bertrand", "Morel", "Fournier", "Girard"
            )
        ),
        "IT" to CountryNames(
            malePrefixes = listOf("", ""),
            femalePrefixes = listOf("", ""),
            maleFirst = listOf(
                "Leonardo", "Francesco", "Alessandro", "Lorenzo", "Mattia", "Andrea", "Gabriele", "Riccardo", "Tommaso", "Edoardo",
                "Federico", "Giuseppe", "Antonio", "Marco", "Diego", "Davide", "Christian", "Giovanni", "Pietro", "Matteo"
            ),
            femaleFirst = listOf(
                "Sofia", "Giulia", "Aurora", "Ginevra", "Alice", "Beatrice", "Emma", "Giorgia", "Vittoria", "Matilde",
                "Ludovica", "Chiara", "Anna", "Camilla", "Greta", "Bianca", "Sara", "Gaia", "Noemi", "Francesca"
            ),
            lastNames = listOf(
                "Rossi", "Russo", "Ferrari", "Esposito", "Bianchi", "Romano", "Colombo", "Ricci", "Marino", "Greco",
                "Bruno", "Gallo", "Conti", "De Luca", "Mancini", "Costa", "Giordano", "Rizzo", "Lombardi", "Moretti"
            )
        ),
        "ES" to CountryNames(
            malePrefixes = listOf("", ""),
            femalePrefixes = listOf("", ""),
            maleFirst = listOf(
                "Hugo", "Mateo", "Martin", "Lucas", "Leo", "Daniel", "Alejandro", "Manuel", "Pablo", "Alvaro",
                "Adrian", "David", "Mario", "Enzo", "Diego", "Marcos", "Izan", "Javier", "Alex", "Bruno"
            ),
            femaleFirst = listOf(
                "Lucia", "Sofia", "Martina", "Maria", "Julia", "Paula", "Valeria", "Emma", "Alba", "Sara",
                "Carla", "Carmen", "Noa", "Claudia", "Vega", "Alma", "Lara", "Daniela", "Abril", "Mia"
            ),
            lastNames = listOf(
                "Garcia", "Rodriguez", "Gonzalez", "Fernandez", "Lopez", "Martinez", "Sanchez", "Perez", "Gomez", "Martin",
                "Jimenez", "Ruiz", "Hernandez", "Diaz", "Moreno", "Muñoz", "Alvarez", "Romero", "Alonso", "Gutierrez"
            )
        ),
        "TR" to CountryNames(
            malePrefixes = listOf("", ""),
            femalePrefixes = listOf("", ""),
            maleFirst = listOf(
                "Yusuf", "Eymen", "Ömer", "Miraç", "Kerem", "Alparslan", "Mustafa", "Ali", "Ahmet", "Emir",
                "Mehmet", "Can", "Burak", "Emre", "Barış", "Kaan", "Murat", "Deniz", "Oğuz", "Serkan"
            ),
            femaleFirst = listOf(
                "Zeynep", "Elif", "Defne", "Asel", "Azra", "Eylül", "Nehir", "Ecrin", "Meryem", "Zehra",
                "Ceren", "Dilek", "Büşra", "Seda", "Gül", "Gamze", "Damla", "İrem", "Hazal", "Esra"
            ),
            lastNames = listOf(
                "Yılmaz", "Kaya", "Demir", "Çelik", "Şahin", "Yıldız", "Yıldırım", "Öztürk", "Aydın", "Özdemir",
                "Arslan", "Doğan", "Kılıç", "Aslan", "Çetin", "Kara", "Koç", "Kurt", "Özkan", "Şimşek"
            )
        ),
        "BR" to CountryNames(
            malePrefixes = listOf("", ""),
            femalePrefixes = listOf("", ""),
            maleFirst = listOf(
                "Miguel", "Arthur", "Heitor", "Bernardo", "Davi", "Gabriel", "Pedro", "Lorenzo", "Lucas", "Matheus",
                "Gustavo", "Guilherme", "Rafael", "Felipe", "Enzo", "Nicolas", "Murilo", "Samuel", "Lucca", "Theo"
            ),
            femaleFirst = listOf(
                "Helena", "Alice", "Laura", "Manuela", "Sophia", "Isabella", "Luiza", "Valentina", "Giovanna", "Maria",
                "Livia", "Cecilia", "Eloa", "Lara", "Antonella", "Isadora", "Mariana", "Beatriz", "Lorena", "Melissa"
            ),
            lastNames = listOf(
                "Silva", "Santos", "Oliveira", "Souza", "Rodrigues", "Ferreira", "Alves", "Pereira", "Lima", "Gomes",
                "Costa", "Ribeiro", "Martins", "Carvalho", "Almeida", "Lopes", "Soares", "Fernandes", "Vieira", "Barbosa"
            )
        ),
        "JP" to CountryNames(
            malePrefixes = listOf("", ""),
            femalePrefixes = listOf("", ""),
            maleFirst = listOf(
                "Ren", "Haruto", "Souta", "Yuto", "Riku", "Kaito", "Hinata", "Minato", "Asahi", "Takumi",
                "Daiki", "Kenji", "Hiroshi", "Sho", "Ryota", "Tsubasa", "Kazuki", "Sora", "Hayato", "Kota"
            ),
            femaleFirst = listOf(
                "Himari", "Hina", "Yua", "Sakura", "Ichika", "Akari", "Sara", "Yui", "Mei", "Rio",
                "Aoi", "Rin", "Koharu", "Kanna", "Ema", "Mio", "Tsumugi", "Nanami", "Ayaka", "Misaki"
            ),
            lastNames = listOf(
                "Sato", "Suzuki", "Takahashi", "Tanaka", "Watanabe", "Ito", "Yamamoto", "Nakamura", "Kobayashi", "Kato",
                "Yoshida", "Yamada", "Sasaki", "Yamaguchi", "Saito", "Matsumoto", "Inoue", "Kimura", "Hayashi", "Shimizu"
            )
        )
    )

    fun generateName(countryCode: String, gender: Gender = Gender.ANY): String {
        val code = countryCode.uppercase()
        val data = namesByCountry[code] ?: when (code) {
            "SA", "AE", "EG", "QA", "KW", "BH", "OM", "JO", "LB", "IQ", "DZ", "MA", "YE" -> namesByCountry["SA"]!!
            "ES", "MX", "AR", "CO", "PE", "CL", "CR", "PA", "UY", "BO" -> namesByCountry["ES"]!!
            "PT", "BR" -> namesByCountry["BR"]!!
            "IN", "NP", "LK" -> namesByCountry["IN"]!!
            "PK", "AF" -> namesByCountry["PK"]!!
            "BD" -> namesByCountry["BD"]!!
            "FR", "BE", "LU" -> namesByCountry["FR"]!!
            "DE", "AT", "CH", "NL" -> namesByCountry["DE"]!!
            "IT" -> namesByCountry["IT"]!!
            "TR", "AZ" -> namesByCountry["TR"]!!
            "JP" -> namesByCountry["JP"]!!
            "UK", "IE" -> namesByCountry["UK"]!!
            "AU", "NZ" -> namesByCountry["AU"]!!
            "CA" -> namesByCountry["CA"]!!
            else -> namesByCountry["US"]!!
        }
        val isMale = when (gender) {
            Gender.MALE -> true
            Gender.FEMALE -> false
            Gender.ANY -> Random.nextBoolean()
        }
        val pool = if (isMale) data.maleFirst else data.femaleFirst
        val prefixes = if (isMale) data.malePrefixes else data.femalePrefixes

        val firstName = pool[Random.nextInt(pool.size)]
        val lastName = data.lastNames[Random.nextInt(data.lastNames.size)]

        val prefix = if (prefixes.isNotEmpty() && Random.nextInt(100) < 40) {
            val p = prefixes[Random.nextInt(prefixes.size)].trim()
            if (p.isNotEmpty()) "$p " else ""
        } else ""

        val fullName = "$prefix$firstName $lastName".trim()
        return fullName
    }

    fun generateNameList(countryCode: String, gender: Gender, count: Int = 12): List<String> {
        val list = mutableListOf<String>()
        val maxAttempts = count * 3
        var attempts = 0
        while (list.size < count && attempts < maxAttempts) {
            attempts++
            val name = generateName(countryCode, gender)
            if (name.isNotBlank() && !list.contains(name)) {
                list.add(name)
            }
        }
        return list
    }

    fun countryCodeToEmojiFlag(countryCode: String): String {
        val upper = countryCode.trim().uppercase()
        if (upper.length == 2 && upper[0] in 'A'..'Z' && upper[1] in 'A'..'Z') {
            val first = Character.codePointAt(upper, 0) - 0x41 + 0x1F1E6
            val second = Character.codePointAt(upper, 1) - 0x41 + 0x1F1E6
            return String(Character.toChars(first)) + String(Character.toChars(second))
        }
        return "🌐"
    }

    fun getCountryOption(countryCode: String): CountryOption {
        val upper = countryCode.trim().uppercase()
        if (upper.isEmpty()) return CountryOption("US", "United States", "🇺🇸")

        val match = allCountries.firstOrNull { it.code.equals(upper, ignoreCase = true) }
            ?: commonCountries.firstOrNull { it.code.equals(upper, ignoreCase = true) }
        if (match != null) return match

        if (upper.length == 2 && upper[0] in 'A'..'Z' && upper[1] in 'A'..'Z') {
            val flag = countryCodeToEmojiFlag(upper)
            val name = try {
                java.util.Locale("", upper).displayCountry.ifBlank { upper }
            } catch (_: Exception) {
                upper
            }
            return CountryOption(upper, name, flag)
        }

        return CountryOption(upper, upper, "🌐")
    }

    private data class CountryNames(
        val maleFirst: List<String>,
        val femaleFirst: List<String>,
        val lastNames: List<String>,
        val malePrefixes: List<String> = emptyList(),
        val femalePrefixes: List<String> = emptyList()
    )
}
