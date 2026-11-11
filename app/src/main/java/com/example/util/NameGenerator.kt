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
        CountryOption("JP", "Japan", "🇯🇵"),
        CountryOption("NG", "Nigeria", "🇳🇬"),
        CountryOption("ID", "Indonesia", "🇮🇩"),
        CountryOption("PH", "Philippines", "🇵🇭"),
        CountryOption("MY", "Malaysia", "🇲🇾")
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
            maleFirst = listOf(
                "Tanvir", "Rafiqul", "Sabbir", "Arif", "Mahmudul", "Rakibul", "Shakil", "Rashed",
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
                "Tajul", "Zahidul", "Sohan", "Shanto", "Taijul", "Taskin", "Soumya", "Nasum",
                "Arafat", "Arman", "Adnan", "Abrar", "Ahnaf", "Ayman", "Azizul", "Bappy",
                "Didar", "Emon", "Fahad", "Fardeen", "Fuad", "Galib", "Hasib", "Hridoy",
                "Iftekhar", "Irfan", "Istiak", "Jewel", "Jibon", "Kawsar", "Limon", "Maruf",
                "Mashrafe", "Mominul", "Mushfiq", "Naimul", "Noman", "Ovi", "Pavel", "Polash",
                "Protik", "Rafi", "Rahat", "Raju", "Rana", "Redwan", "Rezaul", "Rifat",
                "Rokon", "Sadek", "Sadman", "Sagor", "Sakib", "Salman", "Samiul", "Sanwar",
                "Sarwar", "Shafiq", "Shahin", "Shamim", "Shanto", "Shuvo", "Siam", "Sifat",
                "Sumon", "Tarek", "Tawsif", "Tuhin", "Yasin", "Yeasin", "Zahin", "Ziaul"
            ),
            femaleFirst = listOf(
                "Nusrat", "Farhana", "Sumaiya", "Tanjina", "Sharmin", "Sultana", "Sadia", "Rimi",
                "Jannatul", "Afsana", "Tasnim", "Mousumi", "Nabila", "Sabrina", "Fariha", "Anika",
                "Samia", "Priyanka", "Rumana", "Tania", "Mehnaz", "Nazia", "Mithila", "Lamia",
                "Ayesha", "Fatema", "Roksana", "Tahmina", "Naznin", "Shirin", "Shampa", "Munira",
                "Marufa", "Rubina", "Khadiza", "Bristy", "Nigar", "Salma", "Rehana", "Kohinur",
                "Bilkis", "Shahnaz", "Asma", "Monira", "Ferdousi", "Afroza", "Dilruba", "Hasina",
                "Mahbuba", "Rokeya", "Suraiya", "Tahsin", "Umaiza", "Wasima", "Yasmin", "Zinat",
                "Aditi", "Barsha", "Chaity", "Dipa", "Elora", "Farzana", "Gita", "Humaira",
                "Iffat", "Jarin", "Keya", "Lipika", "Moni", "Poly", "Ratna", "Shathi",
                "Tumpa", "Urbi", "Bithi", "Puja", "Shoma", "Champa", "Dalia", "Laboni",
                "Mohua", "Nipa", "Popy", "Rozina", "Shilpi", "Tarana", "Afia", "Anjum",
                "Arpita", "Bushra", "Esha", "Eva", "Fahmida", "Faria", "Hafsa", "Habiba",
                "Ishrat", "Jhumur", "Kaniz", "Liza", "Lubna", "Mahfuza", "Mahima", "Maisha",
                "Maliha", "Maria", "Mim", "Mitu", "Mou", "Mukta", "Nadia", "Nafisa",
                "Naila", "Naima", "Nandini", "Nasrin", "Nishi", "Nowshin", "Nupur", "Orpita",
                "Pinky", "Proma", "Raisa", "Razia", "Ritu", "Riya", "Ruma", "Sadia",
                "Saima", "Sanjida", "Sanju", "Shaila", "Shamima", "Shanta", "Shefali", "Simi",
                "Sonia", "Suborna", "Suchona", "Sumi", "Swarna", "Tabassum", "Tamanna", "Tisha"
            ),
            lastNames = listOf(
                "Ahmed", "Islam", "Hasan", "Rahman", "Hossain", "Chowdhury", "Khan", "Ali",
                "Akter", "Khatun", "Uddin", "Bhuiyan", "Sikder", "Miah", "Sarkar", "Kabir",
                "Talukder", "Munshi", "Hawlader", "Gazi", "Mallik", "Bepari", "Pramanik", "Khandakar",
                "Mazumder", "Mondal", "Biswas", "Barua", "Pal", "Dey", "Das", "Roy",
                "Bhowmik", "Ghosh", "Majumdar", "Chakraborty", "Samaddar", "Howlader", "Patwary", "Laskar",
                "Mia", "Sheikh", "Dewan", "Mulla", "Qazi", "Mirza", "Akond", "Bari",
                "Tarafdar", "Chakma", "Marma", "Gomes", "Rozario", "Costa", "Purkayastha", "Kazi",
                "Siddique", "Faruque", "Karim", "Latif", "Mahmud", "Matin", "Mannan", "Haque",
                "Hoque", "Zaman", "Alam", "Jahan", "Sultana", "Begum", "Nahar", "Parvin"
            )
        ),
        "US" to CountryNames(
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
                "Greyson", "Cooper", "Ian", "Carson", "Axel", "Jaxson", "Dominic", "Leonardo",
                "Austin", "Brandon", "Bryson", "Cole", "Declan", "Evan", "Gavin", "Harrison",
                "Jason", "Jordan", "Kayden", "Kevin", "Kyle", "Levi", "Logan", "Marcus",
                "Micah", "Owen", "Parker", "Rowan", "Sawyer", "Silas", "Tyler", "Vincent",
                "Wesley", "Weston", "Xavier", "Zachary", "Zane", "Zion", "Blake", "Brady"
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
                "Autumn", "Adeline", "Hailey", "Gianna", "Valentina", "Isla", "Eliana", "Quinn",
                "Alexa", "Alyssa", "Amber", "Ashley", "Brianna", "Brooke", "Camila", "Clara",
                "daisy", "Delilah", "Eden", "Iris", "Jade", "Jasmine", "Julia", "Kylie",
                "Lauren", "Lydia", "Mackenzie", "Maria", "Melanie", "Morgan", "Natalia", "Paige"
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
                "Chavez", "Wood", "James", "Bennett", "Gray", "Mendoza", "Ruiz", "Hughes",
                "Price", "Alvarez", "Castillo", "Sanders", "Patel", "Myers", "Long", "Ross",
                "Foster", "Jimenez", "Powell", "Jenkins", "Perry", "Russell", "Sullivan", "Bell"
            )
        ),
        "UK" to CountryNames(
            maleFirst = listOf(
                "Oliver", "George", "Arthur", "Noah", "Muhammad", "Leo", "Oscar", "Harry",
                "Archie", "Jack", "Henry", "Charlie", "Freddie", "Theodore", "Thomas", "Finley",
                "Alfie", "Jacob", "William", "Isaac", "Tommy", "Joshua", "Alexander", "Edward",
                "Lucas", "James", "Max", "Albie", "Roman", "Teddy", "Logan", "Harrison",
                "Mason", "Daniel", "Ethan", "Sebastian", "Arlo", "Adam", "Reggie", "Caleb",
                "Jaxon", "Ronnie", "Dylan", "Hugo", "Zachary", "Albert", "Joseph", "Elijah",
                "Reuben", "David", "Jude", "Samuel", "Hunter", "Benjamin", "Luca", "Louis",
                "Stanley", "Sonny", "Frankie", "Gabriel", "Elliot", "Toby", "Jesse", "Chester",
                "Carter", "Harvey", "Harley", "Rowan", "Felix", "Brody", "Callum", "Liam",
                "Rory", "Jamie", "Connor", "Kieran", "Declan", "Aidan", "Owen", "Reece"
            ),
            femaleFirst = listOf(
                "Olivia", "Amelia", "Isla", "Ava", "Mia", "Ivy", "Lily", "Isabella",
                "Rosie", "Sophia", "Grace", "Freya", "Willow", "Florence", "Emily", "Ella",
                "Poppy", "Evie", "Elsie", "Charlotte", "Sienna", "Daisy", "Phoebe", "Hallie",
                "Harper", "Alice", "Jessica", "Sophie", "Maya", "Ruby", "Matilda", "Evelyn",
                "Layla", "Maisie", "Millie", "Chloe", "Eleanor", "Mila", "Imogen", "Esme",
                "Violet", "Penelope", "Luna", "Harriet", "Lottie", "Thea", "Zara", "Ada",
                "Arabella", "Delilah", "Georgia", "Bonnie", "Darcey", "Robyn", "Nancy", "Amber",
                "Rose", "Emma", "Molly", "Orla", "Clara", "Erin", "Lola", "Annabelle",
                "Abigail", "Hollie", "scarlett", "Bella", "Summer", "Heidi", "Niamh", "Skye"
            ),
            lastNames = listOf(
                "Smith", "Jones", "Taylor", "Brown", "Williams", "Wilson", "Johnson", "Davies",
                "Robinson", "Wright", "Thompson", "Evans", "Walker", "White", "Roberts", "Green",
                "Hall", "Thomas", "Clarke", "Jackson", "Wood", "Harris", "Edwards", "Turner",
                "Martin", "Cooper", "Hill", "Ward", "Hughes", "Moore", "Clark", "King",
                "Harrison", "Lewis", "Baker", "Patel", "Young", "Allen", "Anderson", "Phillips",
                "Lee", "Bell", "Parker", "Davis", "Bennett", "Miller", "Shaw", "Cook",
                "Richardson", "Marshall", "Collins", "Carter", "Bailey", "Cox", "Simpson", "Price",
                "Mackenzie", "Campbell", "Stewart", "Murray", "Reid", "Kelly", "Murphy", "Walsh"
            )
        ),
        "IN" to CountryNames(
            maleFirst = listOf(
                "Aarav", "Vihaan", "Aditya", "Arjun", "Reyansh", "Sai", "Aayush", "Rohan",
                "Ishaan", "Dhruv", "Kabir", "Aryan", "Aniket", "Dev", "Vikram", "Rahul",
                "Akash", "Kunal", "Sameer", "Gaurav", "Manish", "Deepak", "Sachin", "Naveen",
                "Amit", "Pranav", "Harsh", "Varun", "Siddharth", "Abhishek", "Suraj", "Nikhil",
                "Vivek", "Anand", "Ravi", "Ashish", "Suresh", "Dinesh", "Mohit", "Sunil",
                "Rajesh", "Pankaj", "Alok", "Shantanu", "Mayank", "Utkarsh", "Chirag", "Yash",
                "Tushar", "Hemant", "Neeraj", "Parth", "Tanmay", "Karan", "Kartik", "Girish",
                "Hitesh", "Jayant", "Lakshya", "Manoj", "Omkar", "Prashant", "Rakesh", "Sandip",
                "Tejas", "Uday", "Vikas", "Yogesh", "Bhavesh", "Chetan", "Darshan", "Eknath",
                "Rishabh", "Shubham", "Saurabh", "Anurag", "Vipul", "Piyush", "nitin", "Mukesh"
            ),
            femaleFirst = listOf(
                "Aadhya", "Diya", "Saanvi", "Ananya", "Pari", "Isha", "Navya", "Riya",
                "Myra", "Kavya", "Avani", "Tanvi", "Shreya", "Anushka", "Pooja", "Deepika",
                "Sneha", "Priyanka", "Neha", "Megha", "Swati", "Aditi", "Komal", "Sonal",
                "Ritu", "Pallavi", "Divya", "Nisha", "Kritika", "Simran", "Vanshika", "Bhavna",
                "Preeti", "Payal", "Rashmi", "Shruti", "Monika", "Jyoti", "Archana", "Sunita",
                "Anita", "Geeta", "Shilpa", "Kavita", "Sapna", "Rekha", "Sarita", "Mamta",
                "Aakanksha", "Bhumika", "Charu", "Drishti", "Garima", "Harshita", "Ishita", "Jhanvi",
                "Khushi", "Lavanya", "Muskan", "Nandini", "Prachi", "Radhika", "Sakshi", "Trisha",
                "Kiara", "Alia", "Tara", "sonakshi", "Kriti", "disha", "Shraddha", "Bhumi"
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
            maleFirst = listOf(
                "Muhammad", "Ahmed", "Hamza", "Bilal", "Ali", "Usman", "Hassan", "Zain", "Farhan", "Shahid",
                "Babar", "Shaheen", "Rizwan", "Haris", "Naseem", "Shadab", "Imad", "Fakhar", "Asif", "Shoaib",
                "Wasim", "Waqar", "Javed", "Inzamam", "Saeed", "Younis", "Misbah", "Sarfaraz", "Kamran", "Umar",
                "Danish", "Yasir", "Junaid", "Sohail", "Aamer", "Wahab", "Faheem", "Khushdil", "Shan", "Abdullah",
                "Saad", "Talha", "Fahad", "Salman", "Arslan", "Zeeshan", "Kashif", "Noman", "Adnan", "Tariq"
            ),
            femaleFirst = listOf(
                "Ayesha", "Fatima", "Zainab", "Maryam", "Sana", "Hira", "Anum", "Laiba", "Khadija", "Nimra",
                "Mahira", "Mehwish", "Saba", "Sajal", "Yumna", "Ayeza", "Iqra", "Kinza", "Hania", "Minal",
                "Aiman", "Maya", "Hareem", "Sarah", "Alizeh", "Kubra", "Neelam", "Sanam", "Urwa", "Mawra",
                "Zarnish", "Saboor", "Ramsha", "Momina", "Aima", "Nida", "Shaista", "Samina", "Bushra", "Farah",
                "Sidra", "Rabia", "Aqsa", "Hafsa", "Mahnoor", "Areeba", "komal", "Uzma", "Nadia", "Kiran"
            ),
            lastNames = listOf(
                "Khan", "Malik", "Chaudhry", "Bhatti", "Butt", "Qureshi", "Siddiqui", "Abbasi", "Sheikh", "Raza",
                "Mirza", "Baig", "Mughal", "Ansari", "Farooqi", "Hashmi", "Gillani", "Bukhari", "Kazmi", "Naqvi",
                "Gondal", "Cheema", "Warraich", "Tarar", "Bajwa", "Wattoo", "Dogar", "Awan", "Kharal", "Janjua",
                "Niazi", "Afridi", "Yousafzai", "Khattak", "Mengal", "Bugti", "Leghari", "Bhutto", "Soomro", "Memon"
            )
        ),
        "SA" to CountryNames(
            maleFirst = listOf(
                "Mohammed", "Abdullah", "Abdulaziz", "Fahad", "Saud", "Omar", "Khaled", "Ali", "Sultan", "Nasser",
                "Turki", "Bandar", "Majed", "Faisal", "Nayef", "Bader", "Salman", "Waleed", "Mansour", "Talal",
                "Yasser", "Sami", "Tariq", "Adel", "Hamad", "Ibrahim", "Saleh", "Ziad", "Rayan", "Nawaf"
            ),
            femaleFirst = listOf(
                "Fatima", "Sarah", "Reem", "Noura", "Lama", "Haya", "Maha", "Hanan", "Leen", "Danah",
                "Joud", "Shahad", "Raghad", "Tala", "Deema", "Lulwa", "Ghaida", "Rawan", "Alanoud", "Basma",
                "Abeer", "Amal", "Dalal", "Hend", "Jawaher", "Latifa", "Mona", "Nada", "Salwa", "Yasmin"
            ),
            lastNames = listOf(
                "Al-Ghamdi", "Al-Zahrani", "Al-Otaibi", "Al-Shehri", "Al-Harbi", "Al-Dossari", "Al-Qahtani", "Al-Mutairi",
                "Al-Subaie", "Al-Shahrani", "Al-Khaldi", "Al-Anazi", "Al-Rashidi", "Al-Shamrani", "Al-Husseini", "Al-Tamimi",
                "Al-malki", "Al-Yami", "Al-Bishi", "Al-Juhani"
            )
        ),
        "AE" to CountryNames(
            maleFirst = listOf(
                "Zayed", "Rashid", "Hamdan", "Saeed", "Mansoor", "Majid", "Khalifa", "Sultan", "Hazza", "Maktoum",
                "Ahmed", "Mohammed", "Salem", "Obaid", "Suhail", "Matar", "Khalfan", "Thani", "Butti", "Tahnoon",
                "Tariq", "Saif", "Marwan", "Nasser", "Juma", "Humaid", "Abdullah", "Eisa", "Ammar", "Yousef"
            ),
            femaleFirst = listOf(
                "Mariam", "Fatima", "Shamsa", "Meera", "Latifa", "Salama", "Mouza", "Maitha", "Afra", "Hind",
                "Sheikha", "Roudha", "Alia", "Asma", "Kholoud", "Manal", "Amna", "Hessa", "Rawdha", "Moza",
                "Noora", "Shamma", "Aisha", "Reem", "Sara", "Dana", "Mahra", "budoor", "Hamda", "khadija"
            ),
            lastNames = listOf(
                "Al-Falasi", "Al-Maktoum", "Al-Nuaimi", "Al-Suwaidi", "Al-Zaabi", "Al-Mazrouei", "Al-Ketbi", "Al-Ameri",
                "Al-Shamsi", "Al-Romaithi", "Al-Dhaheri", "Al-Mansoori", "Al-Qassimi", "Al-Sharqi", "Al-Mualla", "Al-Hameli",
                "Al-Marri", "Al-Kaabi", "Al-Ahbabi", "Al-Balooshi"
            )
        ),
        "CA" to CountryNames(
            maleFirst = listOf(
                "Liam", "Noah", "William", "Lucas", "Leo", "Benjamin", "Oliver", "Jack", "Nathan", "Felix",
                "Samuel", "Logan", "Jacob", "Thomas", "Ethan", "Alexander", "Gabriel", "Theo", "Owen", "Adam",
                "Caleb", "Ryan", "Connor", "Dylan", "Carter", "Evan", "Luke", "Zachary", "Aiden", "Matthew"
            ),
            femaleFirst = listOf(
                "Olivia", "Emma", "Charlotte", "Amelia", "Sophia", "Chloe", "Mia", "Alice", "Florence", "Lea",
                "Zoe", "Hannah", "Clara", "Beatrice", "Rosalie", "Juliette", "Mila", "Maya", "Eva", "Victoria",
                "Isla", "Grace", "Lily", "Avery", "Evelyn", "Abigail", "Nora", "Scarlett", "Ella", "Sarah"
            ),
            lastNames = listOf(
                "Tremblay", "Roy", "Gagnon", "Cote", "Bouchard", "Smith", "Brown", "Campbell", "MacDonald", "Stewart",
                "Morin", "Fortin", "Gauthier", "Pelletier", "Belanger", "Levesque", "Wilson", "Anderson", "Taylor", "Johnson",
                "Martin", "Lee", "Thompson", "White", "Scott", "Young", "Fraser", "Reid", "Ross", "Clark"
            )
        ),
        "AU" to CountryNames(
            maleFirst = listOf(
                "Oliver", "Noah", "William", "Jack", "Leo", "Henry", "Charlie", "Thomas", "Lucas", "Hudson",
                "Liam", "Alexander", "Harrison", "Lachlan", "James", "Max", "Oscar", "Mason", "Cooper", "Archie",
                "Hunter", "Ethan", "Levi", "Flynn", "Harvey", "Angus", "Hamish", "Darcy", "riley", "Xavier"
            ),
            femaleFirst = listOf(
                "Charlotte", "Olivia", "Amelia", "Isla", "Mia", "Ava", "Grace", "Willow", "Harper", "Chloe",
                "Ruby", "Sophie", "Ivy", "Zoe", "Matilda", "Ella", "Evie", "Georgia", "Audrey", "Lily",
                "Sienna", "Zara", "poppy", "Scarlett", "Phoebe", "Evelyn", "Billie", "Frankie", "Mackenzie", "Maddison"
            ),
            lastNames = listOf(
                "Smith", "Jones", "Williams", "Brown", "Wilson", "Taylor", "Johnson", "White", "Martin", "Anderson",
                "Thompson", "Nguyen", "Thomas", "Walker", "Harris", "Lee", "Ryan", "Robinson", "Kelly", "King",
                "Davis", "Wright", "Evans", "Roberts", "Green", "Hall", "Wood", "Jackson", "Clarke", "Campbell"
            )
        ),
        "DE" to CountryNames(
            maleFirst = listOf(
                "Lukas", "Leon", "Finn", "Paul", "Jonas", "Felix", "Maximilian", "Tim", "Niklas", "Jan",
                "Ben", "Elias", "Noah", "Luis", "Luca", "Moritz", "Julian", "David", "Philipp", "Alexander",
                "Matteo", "Emil", "Theo", "Anton", "Jakob", "Oskar", "Liam", "Henry", "Milan", "Fabian"
            ),
            femaleFirst = listOf(
                "Emma", "Mia", "Hannah", "Emilia", "Sofia", "Lina", "Marie", "Mila", "Ella", "Clara",
                "Lea", "Anna", "Lena", "Laura", "Lara", "Johanna", "Sarah", "Leonie", "Emily", "Sophie",
                "Mathilda", "Ida", "Frieda", "Lia", "Greta", "Charlotte", "Nele", "Leni", "Maja", "Nora"
            ),
            lastNames = listOf(
                "Müller", "Schmidt", "Schneider", "Fischer", "Weber", "Meyer", "Wagner", "Becker", "Schulz", "Hoffmann",
                "Schäfer", "Koch", "Bauer", "Richter", "Klein", "Wolf", "Schröder", "Neumann", "Schwarz", "Zimmermann",
                "Braun", "Krüger", "Hofmann", "Hartmann", "Lange", "Schmitt", "Werner", "Schmitz", "Krause", "Meier"
            )
        ),
        "FR" to CountryNames(
            maleFirst = listOf(
                "Gabriel", "Leo", "Raphael", "Arthur", "Louis", "Lucas", "Adam", "Jules", "Hugo", "Mael",
                "Liam", "Noah", "Paul", "Ethan", "Sacha", "Tom", "Gaspard", "Alexandre", "Nolan", "Enzo",
                "Theo", "Mathis", "Timothe", "Antoine", "Maxime", "Baptiste", "Clement", "Romain", "Valentin", "Yanis"
            ),
            femaleFirst = listOf(
                "Jade", "Louise", "Emma", "Alice", "Ambre", "Lina", "Rose", "Chloe", "Mia", "Lea",
                "Anna", "Mila", "Julia", "Romy", "Ines", "Lena", "Agathe", "Iris", "Elena", "Zoe",
                "Camille", "Manon", "Sarah", "Clara", "Eva", "Juliette", "Lucie", "Margaux", "Clemence", "Adele"
            ),
            lastNames = listOf(
                "Martin", "Bernard", "Thomas", "Petit", "Robert", "Richard", "Durand", "Dubois", "Moreau", "Laurent",
                "Simon", "Michel", "Lefebvre", "Leroy", "Roux", "David", "Bertrand", "Morel", "Fournier", "Girard",
                "Bonnet", "Dupont", "Lambert", "Fontaine", "Rousseau", "Vincent", "Muller", "Lefevre", "Faure", "Andre"
            )
        ),
        "IT" to CountryNames(
            maleFirst = listOf(
                "Leonardo", "Francesco", "Alessandro", "Lorenzo", "Mattia", "Andrea", "Gabriele", "Riccardo", "Tommaso", "Edoardo",
                "Federico", "Giuseppe", "Antonio", "Marco", "Diego", "Davide", "Christian", "Giovanni", "Pietro", "Matteo",
                "Nicolo", "Filippo", "Samuele", "Luca", "Simone", "Stefano", "Roberto", "Fabio", "Paolo", "Daniele"
            ),
            femaleFirst = listOf(
                "Sofia", "Giulia", "Aurora", "Ginevra", "Alice", "Beatrice", "Emma", "Giorgia", "Vittoria", "Matilde",
                "Ludovica", "Chiara", "Anna", "Camilla", "Greta", "Bianca", "Sara", "Gaia", "Noemi", "Francesca",
                "Martina", "Alessia", "Elena", "Arianna", "Elisa", "Rebecca", "Viola", "Adele", "Nicole", "Valentina"
            ),
            lastNames = listOf(
                "Rossi", "Russo", "Ferrari", "Esposito", "Bianchi", "Romano", "Colombo", "Ricci", "Marino", "Greco",
                "Bruno", "Gallo", "Conti", "DeLuca", "Mancini", "Costa", "Giordano", "Rizzo", "Lombardi", "Moretti",
                "Barbieri", "Fontana", "Santoro", "Mariani", "Rinaldi", "Caruso", "Ferrara", "Galli", "Martini", "Leone"
            )
        ),
        "ES" to CountryNames(
            maleFirst = listOf(
                "Hugo", "Mateo", "Martin", "Lucas", "Leo", "Daniel", "Alejandro", "Manuel", "Pablo", "Alvaro",
                "Adrian", "David", "Mario", "Enzo", "Diego", "Marcos", "Izan", "Javier", "Alex", "Bruno",
                "Oliver", "Dylan", "Thiago", "Antonio", "Marco", "Carlos", "Gonzalo", "Miguel", "Jorge", "Sergio"
            ),
            femaleFirst = listOf(
                "Lucia", "Sofia", "Martina", "Maria", "Julia", "Paula", "Valeria", "Emma", "Alba", "Sara",
                "Carla", "Carmen", "Noa", "Claudia", "Vega", "Alma", "Lara", "Daniela", "Abril", "Mia",
                "Lola", "Olivia", "Elena", "Aitana", "Laia", "Irene", "Adriana", "Laura", "Marta", "Chloe"
            ),
            lastNames = listOf(
                "Garcia", "Rodriguez", "Gonzalez", "Fernandez", "Lopez", "Martinez", "Sanchez", "Perez", "Gomez", "Martin",
                "Jimenez", "Ruiz", "Hernandez", "Diaz", "Moreno", "Muñoz", "Alvarez", "Romero", "Alonso", "Gutierrez",
                "Navarro", "Torres", "Dominguez", "Vazquez", "Ramos", "Gil", "Ramirez", "Serrano", "Blanco", "Molina"
            )
        ),
        "TR" to CountryNames(
            maleFirst = listOf(
                "Yusuf", "Eymen", "Ömer", "Miraç", "Kerem", "Alparslan", "Mustafa", "Ali", "Ahmet", "Emir",
                "Mehmet", "Can", "Burak", "Emre", "Barış", "Kaan", "Murat", "Deniz", "Oğuz", "Serkan",
                "Hakan", "Gökhan", "Volkan", "Tolga", "Onur", "Uğur", "Fatih", "İbrahim", "Hasan", "Hüseyin"
            ),
            femaleFirst = listOf(
                "Zeynep", "Elif", "Defne", "Asel", "Azra", "Eylül", "Nehir", "Ecrin", "Meryem", "Zehra",
                "Ceren", "Dilek", "Büşra", "Seda", "Gül", "Gamze", "Damla", "İrem", "Hazal", "Esra",
                "Merve", "Kübra", "Ayşe", "Fatma", "Hatice", "Ebru", "Özge", "Pınar", "Sinem", "Tuğba"
            ),
            lastNames = listOf(
                "Yılmaz", "Kaya", "Demir", "Çelik", "Şahin", "Yıldız", "Yıldırım", "Öztürk", "Aydın", "Özdemir",
                "Arslan", "Doğan", "Kılıç", "Aslan", "Çetin", "Kara", "Koç", "Kurt", "Özkan", "Şimşek",
                "Polat", "Özcan", "Korkmaz", "Çakır", "Erdoğan", "Yavuz", "Can", "Acar", "Şen", "Aksoy"
            )
        ),
        "BR" to CountryNames(
            maleFirst = listOf(
                "Miguel", "Arthur", "Heitor", "Bernardo", "Davi", "Gabriel", "Pedro", "Lorenzo", "Lucas", "Matheus",
                "Gustavo", "Guilherme", "Rafael", "Felipe", "Enzo", "Nicolas", "Murilo", "Samuel", "Lucca", "Theo",
                "Joao", "Bruno", "Caio", "Diego", "Eduardo", "Leonardo", "Marcelo", "Rodrigo", "Thiago", "Vitor"
            ),
            femaleFirst = listOf(
                "Helena", "Alice", "Laura", "Manuela", "Sophia", "Isabella", "Luiza", "Valentina", "Giovanna", "Maria",
                "Livia", "Cecilia", "Eloa", "Lara", "Antonella", "Isadora", "Mariana", "Beatriz", "Lorena", "Melissa",
                "Ana", "Bruna", "Camila", "Fernanda", "Gabriela", "Juliana", "Leticia", "Amanda", "Carolina", "Natália"
            ),
            lastNames = listOf(
                "Silva", "Santos", "Oliveira", "Souza", "Rodrigues", "Ferreira", "Alves", "Pereira", "Lima", "Gomes",
                "Costa", "Ribeiro", "Martins", "Carvalho", "Almeida", "Lopes", "Soares", "Fernandes", "Vieira", "Barbosa",
                "Rocha", "Dias", "Nascimento", "Andrade", "Moreira", "Nunes", "Marques", "Machado", "Mendes", "Freitas"
            )
        ),
        "JP" to CountryNames(
            maleFirst = listOf(
                "Ren", "Haruto", "Souta", "Yuto", "Riku", "Kaito", "Hinata", "Minato", "Asahi", "Takumi",
                "Daiki", "Kenji", "Hiroshi", "Sho", "Ryota", "Tsubasa", "Kazuki", "Sora", "Hayato", "Kota",
                "Yuki", "Akira", "Naoki", "Shinji", "Taro", "Keisuke", "Daisuke", "Satoshi", "Makoto", "Takeshi"
            ),
            femaleFirst = listOf(
                "Himari", "Hina", "Yua", "Sakura", "Ichika", "Akari", "Sara", "Yui", "Mei", "Rio",
                "Aoi", "Rin", "Koharu", "Kanna", "Ema", "Mio", "Tsumugi", "Nanami", "Ayaka", "Misaki",
                "Haruka", "Yuka", "Natsuki", "Mai", "Rina", "Miku", "Noa", "Saki", "Hana", "Momoka"
            ),
            lastNames = listOf(
                "Sato", "Suzuki", "Takahashi", "Tanaka", "Watanabe", "Ito", "Yamamoto", "Nakamura", "Kobayashi", "Kato",
                "Yoshida", "Yamada", "Sasaki", "Yamaguchi", "Saito", "Matsumoto", "Inoue", "Kimura", "Hayashi", "Shimizu",
                "Yamazaki", "Mori", "Abe", "Ikeda", "Hashimoto", "Yamashita", "Ishikawa", "Nakajima", "Maeda", "Fujita"
            )
        ),
        "RU" to CountryNames(
            maleFirst = listOf(
                "Ivan", "Dmitry", "Alexander", "Sergey", "Mikhail", "Maxim", "Andrey", "Artem", "Nikita", "Ilya",
                "Kirill", "Egor", "Matvey", "Timofey", "Roman", "Vladimir", "Yaroslav", "Fedor", "Gleb", "Konstantin",
                "Denis", "Pavel", "Aleksey", "Nikolay", "Anton", "Oleg", "Igor", "Viktor", "Yuri", "Ruslan"
            ),
            femaleFirst = listOf(
                "Anastasia", "Elena", "Olga", "Anna", "Maria", "Daria", "Polina", "Alisa", "Victoria", "Ekaterina",
                "Ksenia", "Arina", "Valeria", "Veronika", "Vasilisa", "Margarita", "Svetlana", "Yulia", "Tatiana", "Natalia",
                "Irina", "Marina", "Oksana", "Diana", "Alina", "Kristina", "Evgenia", "Elizaveta", "Sofia", "Ulyana"
            ),
            lastNames = listOf(
                "Ivanov", "Smirnov", "Kuznetsov", "Popov", "Sokolov", "Lebedev", "Kozlov", "Novikov", "Morozov", "Petrov",
                "Volkov", "Solovyov", "Vasilyev", "Zaytsev", "Pavlov", "Semyonov", "Golubev", "Vinogradov", "Bogdanov", "Vorobyov",
                "Fedorov", "Mikhailov", "Belyaev", "Tarasov", "Belov", "Komarov", "Orlov", "Kiselyov", "Makarov", "Andreev"
            )
        ),
        "KR" to CountryNames(
            maleFirst = listOf(
                "Minjun", "Seojun", "Doyun", "Yejun", "Siwoo", "Hajoon", "Jiho", "Juwon", "Junwoo", "Minjae",
                "Hyunwoo", "Junseo", "Gunwoo", "Woojin", "Eunwoo", "Sunwoo", "Yujun", "Jinwoo", "Seungwoo", "Jihoon",
                "Donghyun", "Sungmin", "Taehyun", "Jaehyun", "Minho", "Jimin", "Taehyung", "Jungkook", "Seokjin", "Yoongi"
            ),
            femaleFirst = listOf(
                "Seoyeon", "Seoyun", "Jiwoo", "Haeun", "Harin", "Seoah", "Jia", "Sieun", "Ahrin", "Chaewon",
                "Sooah", "Jiyoo", "Daeun", "Yuna", "Eunseo", "Yeeun", "Yoonseo", "Minseo", "Chaeeun", "Soyoon",
                "Jisoo", "Jennie", "Subin", "Yerin", "Nayeon", "Sana", "Mina", "Dahyun", "Tzuyu", "Irene"
            ),
            lastNames = listOf(
                "Kim", "Lee", "Park", "Choi", "Jung", "Kang", "Cho", "Yoon", "Jang", "Lim",
                "Han", "Oh", "Seo", "Shin", "Kwon", "Hwang", "Ahn", "Song", "Yoo", "Hong",
                "Jeon", "Ko", "Mun", "Yang", "Son", "Bae", "Jo", "Baek", "Heo", "Nam"
            )
        ),
        "CN" to CountryNames(
            maleFirst = listOf(
                "Wei", "Jun", "Lei", "Yong", "Jie", "Tao", "Ming", "Bo", "Hao", "Yi",
                "Feng", "Peng", "Qiang", "Chao", "Bin", "Yu", "Xin", "Kai", "Rui", "Zhe",
                "Zihan", "Yuxuan", "Haoran", "Zixuan", "Yuchen", "Mingze", "Jiahao", "Wenbo", "JunJie", "Zhiyuan"
            ),
            femaleFirst = listOf(
                "Jing", "Li", "Yan", "Na", "Fang", "Ling", "Min", "Xiu", "Dan", "Ping",
                "Juan", "Lan", "Ting", "Hui", "Qian", "Ying", "Mei", "Xue", "Lu", "Yue",
                "Yinuo", "Yihan", "Xinyi", "Zihan", "Yutong", "Muhan", "Kexin", "Yuxuan", "Shihan", "Jiaqi"
            ),
            lastNames = listOf(
                "Wang", "Li", "Zhang", "Liu", "Chen", "Yang", "Huang", "Zhao", "Wu", "Zhou",
                "Xu", "Sun", "Ma", "Zhu", "Hu", "Guo", "He", "Gao", "Lin", "Luo",
                "Zheng", "Liang", "Xie", "Song", "Tang", "Xu", "Han", "Feng", "Deng", "Cao"
            )
        ),
        "NG" to CountryNames(
            maleFirst = listOf(
                "Chukwudi", "Emeka", "Babatunde", "Oluwaseun", "Adebayo", "Chinedu", "Olumide", "Ifeanyi", "Chidi", "Obinna",
                "Dapo", "Femi", "Kayode", "Tunde", "Segun", "Kunle", "Uche", "Kelechi", "Nnamdi", "Tochukwu",
                "Ayodeji", "Olusegun", "Temitope", "Damilola", "Ibrahim", "Musa", "Sani", "Usman", "Yusuf", "Victor"
            ),
            femaleFirst = listOf(
                "Ngozi", "Chioma", "Ifeoma", "Funmilayo", "Folake", "Amaka", "Zainab", "Aisha", "Yetunde", "Bolanle",
                "Chiamaka", "Chinelo", "Adaeze", "Olamide", "Simisola", "Eniola", "Bukola", "Ronke", "Tolani", "Halima",
                "Titilayo", "Omotola", "Yemi", "Tiwa", "Nneka", "Kemi", "Bisi", "Sade", "Amina", "Fatima"
            ),
            lastNames = listOf(
                "Adeyemi", "Okafor", "Balogun", "Eze", "Obi", "Adeleke", "Bello", "Danjuma", "Alabi", "Oladipo",
                "Nwosu", "Okonkwo", "Chukwu", "Ibrahim", "Abubakar", "Lawal", "Musa", "Ojo", "Ogunleye", "Fashola",
                "Nwachukwu", "Okeke", "Bakare", "Ajayi", "Odili", "Adenuga", "Dangote", "Otedola", "Sanusi", "Garba"
            )
        ),
        "PH" to CountryNames(
            maleFirst = listOf(
                "Joshua", "Christian", "Angelo", "John", "Gabriel", "Mark", "Daniel", "Justin", "Nathaniel", "Carl",
                "Kenneth", "Alexander", "Ethan", "Jerome", "Francis", "Miguel", "Rafael", "Vincent", "Paolo", "Renz",
                "Carlo", "Jomar", "Bryan", "Kevin", "Ryan", "Adrian", "Alvin", "Marvin", "Jayson", "Ronald"
            ),
            femaleFirst = listOf(
                "Angel", "Princess", "Nicole", "Christine", "Grace", "Althea", "Jasmine", "Bea", "Patricia", "Samantha",
                "Alyssa", "Camille", "Ella", "Kaye", "Bernadette", "Chloe", "Sofia", "Andrea", "Clarisse", "Geline",
                "Katrina", "Bianca", "Mica", "Trisha", "Shiela", "Jessa", "Mariel", "Joyce", "Rica", "Charlene"
            ),
            lastNames = listOf(
                "Santos", "Reyes", "Cruz", "Bautista", "Ocampo", "Garcia", "Mendoza", "Ramos", "Flores", "Gonzales",
                "Villanueva", "Castillo", "Rivera", "Aquino", "Castro", "Delacruz", "Tolentino", "Salazar", "Mercado", "Perez",
                "Soriano", "Pascual", "DeGuzman", "Diaz", "Morales", "Fernandez", "Navarro", "Gutierrez", "Santiago", "Domingo"
            )
        ),
        "ID" to CountryNames(
            maleFirst = listOf(
                "Budi", "Agus", "Eko", "Rizky", "Dimas", "Bayu", "Hendra", "Aditya", "Fajar", "Bambang",
                "Wahyu", "Surya", "Ilham", "Gilang", "Arif", "Dedi", "Tri", "Doni", "Reza", "Faisal",
                "Bagus", "Andi", "Putra", "Satria", "Nanda", "Rama", "Yoga", "Iqbal", "Rian", "Taufik"
            ),
            femaleFirst = listOf(
                "Siti", "Nur", "Dewi", "Putri", "Rini", "Tri", "Ayu", "Sri", "Mega", "Dian",
                "Wulan", "Intan", "Nia", "Fitri", "Lestari", "Ratna", "Tari", "Maya", "Indah", "Rani",
                "Annisa", "Nabila", "Aulia", "Salsabila", "Dinda", "Nada", "Citra", "Kartika", "Bunga", "Tiara"
            ),
            lastNames = listOf(
                "Saputra", "Wijaya", "Setiawan", "Hidayat", "Pratama", "Nugroho", "Kusuma", "Wibowo", "Santoso", "Suryono",
                "Permana", "Kurniawan", "Gunawan", "Susanto", "Firmansyah", "Siregar", "Nasution", "Lubis", "Harahap", "Sitorus",
                "Maulana", "Ramadhan", "Hakim", "Utama", "Prabowo", "Laksana", "Sanjaya", "budiman", "Hartono", "Salim"
            )
        ),
        "MY" to CountryNames(
            maleFirst = listOf(
                "Aiman", "Haziq", "Irfan", "Syafiq", "Faris", "Danish", "Hakim", "Amirul", "Azlan", "Firdaus",
                "Faiz", "Hafiz", "Zulkifli", "Khairul", "Ridzuan", "Shahrul", "Nizam", "Razak", "Ismail", "harith"
            ),
            femaleFirst = listOf(
                "Nurul", "Siti", "Aisyah", "Farah", "Nadia", "Syafiqah", "Amirah", "Izzati", "Atikah", "Alya",
                "Sofia", "Hannah", "Zara", "Qistina", "Balqis", "Maisarah", "Natasha", "Yasmin", "Liyana", "Nabila"
            ),
            lastNames = listOf(
                "Abdullah", "Ibrahim", "Rahman", "Hassan", "Ahmad", "Ismail", "Othman", "Yusof", "Aziz", "Razak",
                "Bakar", "Ali", "Omar", "Zakaria", "kassim", "Hamid", "Harun", "Hashim", "Karim", "Latif"
            )
        ),
        "VN" to CountryNames(
            maleFirst = listOf(
                "Minh", "Duc", "Tuan", "Nam", "Huy", "Hoang", "Long", "Phong", "Hai", "Quang",
                "Dung", "Thanh", "Bao", "Khang", "Phuc", "Thien", "Hieu", "Khoa", "Nhat", "Dat"
            ),
            femaleFirst = listOf(
                "Linh", "Trang", "Huong", "Mai", "Hoa", "Lan", "Anh", "Ngoc", "Thao", "Phuong",
                "Vy", "Ha", "Huyen", "Nhi", "Quynh", "Chi", "My", "Tuyet", "Duyen", "Yen"
            ),
            lastNames = listOf(
                "Nguyen", "Tran", "Le", "Pham", "Hoang", "Phan", "Vu", "Dang", "Bui", "Do",
                "Ho", "Ngo", "Duong", "Ly", "Luong", "Dinh", "Dao", "Doan", "Ta", "Trinh"
            )
        ),
        "NL" to CountryNames(
            maleFirst = listOf(
                "Daan", "Sem", "Lucas", "Milan", "Levi", "Finn", "Noah", "Luuk", "Jesse", "Bram",
                "Lars", "Thijs", "Ruben", "Liam", "Mees", "Stijn", "Sven", "Tim", "Julian", "Thomas"
            ),
            femaleFirst = listOf(
                "Emma", "Sophie", "Julia", "Tess", "Anna", "Sara", "Eva", "Lieke", "Fleur", "Lotte",
                "Sanne", "Noa", "Roos", "Fenan", "Iris", "Maud", "Lynn", "Yara", "Evi", "Amber"
            ),
            lastNames = listOf(
                "DeJong", "Jansen", "DeVries", "VanDeBerg", "VanDijk", "Bakker", "Janssen", "Visser", "Smit", "Meijer",
                "DeBoer", "Mulder", "DeGroot", "Bos", "Vos", "Peters", "Hendriks", "VanLeeuwen", "Dekker", "Brouwer"
            )
        ),
        "SE" to CountryNames(
            maleFirst = listOf(
                "Lars", "Johan", "Erik", "Karl", "Anders", "Mikael", "Per", "Magnus", "Fredrik", "Daniel",
                "Oskar", "William", "Lucas", "Liam", "Elias", "Hugo", "Oliver", "Alexander", "Filip", "Leo"
            ),
            femaleFirst = listOf(
                "Astrid", "Elsa", "Freja", "Maja", "Karin", "Sara", "Emma", "Maria", "Alice", "Alma",
                "Ebba", "Ella", "Wilma", "Klara", "Agnes", "Saga", "Signe", "Stella", "Linnea", "Ida"
            ),
            lastNames = listOf(
                "Andersson", "Johansson", "Karlsson", "Nilsson", "Eriksson", "Larsson", "Olsson", "Persson", "Svensson", "Gustafsson",
                "Pettersson", "Jonsson", "Jansson", "Hansson", "Bengtsson", "Lindberg", "Jakobsson", "Magnusson", "Olofsson", "Lindqvist"
            )
        ),
        "PL" to CountryNames(
            maleFirst = listOf(
                "Jakub", "Kacper", "Szymon", "Jan", "Mateusz", "Filip", "Wojciech", "Mikolaj", "Aleksander", "Piotr",
                "Michal", "Stanislaw", "Bartosz", "Dawid", "Tomasz", "Pawel", "Krzysztof", "Adam", "Marcin", "Lukasz"
            ),
            femaleFirst = listOf(
                "Julia", "Zuzanna", "Maja", "Lena", "Wiktoria", "Oliwia", "Natalia", "Aleksandra", "Hanna", "Amelia",
                "Zofia", "Alicja", "Emilia", "Maria", "Antonina", "Laura", "Pola", "Iga", "Kornelia", "Nadia"
            ),
            lastNames = listOf(
                "Nowak", "Kowalski", "Wisniewski", "Wojcik", "Kowalczyk", "Kaminski", "Lewandowski", "Zielinski", "Szymanski", "Wozniak",
                "Kozlowski", "Jankowski", "Mazur", "Kwiatkowski", "Wojciechowski", "Krawczyk", "Kaczmarek", "Piotrowski", "Grabowski", "Zajac"
            )
        ),
        "UA" to CountryNames(
            maleFirst = listOf(
                "Oleksandr", "Dmytro", "Maksym", "Serhiy", "Vladyslav", "Artem", "Andriy", "Ivan", "Mykhailo", "Yaroslav",
                "Bohdan", "Nazar", "Taras", "Roman", "Vitaliy", "Denys", "Ihor", "Yevhen", "Pavlo", "Oleg"
            ),
            femaleFirst = listOf(
                "Anastasiya", "Anna", "Daryna", "Sofiya", "Kateryna", "Maryna", "Viktoriya", "Yuliya", "Olha", "Alina",
                "Diana", "Polina", "Valeriya", "Iryna", "Nataliya", "Oksana", "Tetyana", "Nadiya", "Mariya", "Svitlana"
            ),
            lastNames = listOf(
                "Shevchenko", "Boyko", "Kravchenko", "Bondarenko", "Tkachenko", "Kovalenko", "Melnyk", "Moroz", "Marchenko", "Lysenko",
                "Rudenko", "Kovalchuk", "Ponomarenko", "Savchenko", "Hrytsenko", "Kuzmenko", "Lytvynenko", "Kostenko", "Pavlenko", "Shapoval"
            )
        ),
        "MX" to CountryNames(
            maleFirst = listOf(
                "Carlos", "Jose", "Luis", "Juan", "Alejandro", "Miguel", "Diego", "Fernando", "Jorge", "Ricardo",
                "Eduardo", "Javier", "Daniel", "Mateo", "Santiago", "Emiliano", "Sebastian", "Leonardo", "Gael", "Mauricio"
            ),
            femaleFirst = listOf(
                "Maria", "Guadalupe", "Sofia", "Camila", "Valentina", "Ximena", "Mariana", "Fernanda", "Daniela", "Valeria",
                "Regina", "Renata", "Andrea", "Natalia", "Alejandra", "Gabriela", "Paola", "Romina", "Paulina", "Jimena"
            ),
            lastNames = listOf(
                "Hernandez", "Garcia", "Martinez", "Lopez", "Gonzalez", "Rodriguez", "Perez", "Sanchez", "Ramirez", "Cruz",
                "Flores", "Gomez", "Morales", "Vazquez", "Reyes", "Jimenez", "Torres", "Diaz", "Gutierrez", "Mendoza"
            )
        ),
        "CO" to CountryNames(
            maleFirst = listOf(
                "Santiago", "Mateo", "Sebastian", "David", "Nicolas", "Samuel", "Alejandro", "Daniel", "Jeronimo", "Emiliano",
                "Juan", "Andres", "Felipe", "Camilo", "Esteban", "Julian", "Gabriel", "Tomas", "Martin", "Lucas"
            ),
            femaleFirst = listOf(
                "Isabella", "Mariana", "Valeria", "Sofia", "Salome", "Gabriela", "Luciana", "Sara", "Valentina", "Camila",
                "Antonella", "Juliana", "Daniela", "Laura", "Catalina", "Paula", "Manuela", "Alejandra", "Maria", "Victoria"
            ),
            lastNames = listOf(
                "Rodriguez", "Gomez", "Lopez", "Gonzalez", "Garcia", "Martinez", "Ramirez", "Sanchez", "Diaz", "Perez",
                "Castro", "Vargas", "Rios", "Torres", "Morales", "Suarez", "Rojas", "Jimenez", "Muñoz", "Castillo"
            )
        ),
        "AR" to CountryNames(
            maleFirst = listOf(
                "Joaquin", "Bautista", "Benjamin", "Tomas", "Facundo", "Agustin", "Mateo", "Franco", "Thiago", "Santino",
                "Lautaro", "Ignacio", "Nicolas", "Lucas", "Valentin", "Felipe", "Bruno", "Manuel", "Lorenzo", "Martin"
            ),
            femaleFirst = listOf(
                "Martina", "Lucia", "Catalina", "Mia", "Delfina", "Emilia", "Valentina", "Camila", "Julieta", "Zoe",
                "Sofia", "Pilar", "Victoria", "Morena", "Abril", "Guadalupe", "Malena", "Juana", "Renata", "Lola"
            ),
            lastNames = listOf(
                "Gonzalez", "Rodriguez", "Lopez", "Fernandez", "Garcia", "Perez", "Martinez", "Gomez", "Diaz", "Alvarez",
                "Romero", "Sosa", "Torres", "Ruiz", "Ramirez", "Flores", "Acosta", "Benitez", "Medina", "Herrera"
            )
        ),
        "EG" to CountryNames(
            maleFirst = listOf(
                "Mohamed", "Ahmed", "Mahmoud", "Mostafa", "Youssef", "Omar", "Amr", "Khaled", "Ali", "Tarek",
                "Karim", "Hassan", "Hussein", "Ibrahim", "Sherif", "Sameh", "Hazem", "Ziad", "Yasser", "Ramy"
            ),
            femaleFirst = listOf(
                "Fatima", "Mariam", "Aya", "Nour", "Sarah", "Yasmin", "Menna", "Salma", "Heba", "Rania",
                "Dina", "Noha", "Reem", "Mona", "Nada", "Rawan", "Esraa", "Nadine", "Shahd", "Habiba"
            ),
            lastNames = listOf(
                "Hassan", "Ibrahim", "Ali", "Khalil", "Mostafa", "ElSayed", "AbdelRahman", "Mansour", "Osman", "Salem",
                "Mahmoud", "Farouk", "Shaker", "Soliman", "Fouad", "Fahmy", "Kamel", "Badawi", "Shawky", "Radwan"
            )
        ),
        "GR" to CountryNames(
            maleFirst = listOf(
                "Georgios", "Dimitrios", "Konstantinos", "Ioannis", "Nikolaos", "Panagiotis", "Vasileios", "Christos", "Athanasios", "Michail",
                "Spyridon", "Antonios", "Alexandros", "Evangelos", "Stefanos", "Marios", "Ilias", "Stavros", "Petros", "Theodoros"
            ),
            femaleFirst = listOf(
                "Maria", "Eleni", "Aikaterini", "Vasiliki", "Sophia", "Angeliki", "Georgia", "Dimitra", "Konstantina", "Ioanna",
                "Paraskevi", "Christina", "Eirini", "Evangelia", "Styliani", "Anna", "Despoina", "Kalliopi", "Theodora", "Fotini"
            ),
            lastNames = listOf(
                "Papadopoulos", "Pappas", "Oikonomou", "Georgiou", "Nikolaou", "Dimitriou", "Vlachos", "Angelopoulos", "Konstantinou", "Karras",
                "Giannakopoulos", "Petridis", "Vassiliou", "Athanasiou", "Alexiou", "Ioannidis", "Stavropoulos", "Christodoulou", "Antoniou", "Sidiropoulos"
            )
        ),
        "TH" to CountryNames(
            maleFirst = listOf(
                "Somchai", "Natthaphon", "Kittisak", "Thanawat", "Chaiwat", "Krit", "Thanakorn", "Piyawat", "Anan", "Sarayut",
                "Teerapat", "Worawut", "Wichai", "Chatchai", "Prasert", "Somsak", "Boonmee", "Santi", "Prawit", "Wirote"
            ),
            femaleFirst = listOf(
                "Supaporn", "Kanya", "Rattana", "Wanida", "Ploy", "Siriporn", "Nattaya", "Chutima", "Sunisa", "Jiraporn",
                "Kanokwan", "Sasithorn", "Pornthip", "Supatra", "Malai", "Boonreung", "Apinya", "Uraiwan", "Phannee", "Kamonwan"
            ),
            lastNames = listOf(
                "Saelim", "Thongkham", "Saetan", "Wongsuwan", "Chaiprasert", "Ratanakul", "Suwanarat", "Phasuk", "Srisuk", "Wongchai",
                "Kittisuk", "Bunnak", "Chaisri", "Saetang", "Saelee", "Saeheng", "Saechua", "Charoenwong", "Petchrat", "Thongsuk"
            )
        )
    )

    data class PersonDetails(
        val fullName: String,
        val firstName: String,
        val lastName: String,
        val prefix: String = "",
        val gender: Gender,
        val countryCode: String,
        val countryName: String,
        val flag: String
    )

    fun generatePersonDetails(countryCode: String, gender: Gender = Gender.ANY): PersonDetails {
        val code = countryCode.uppercase()
        val data = getCountryData(code)
        val isMale = when (gender) {
            Gender.MALE -> true
            Gender.FEMALE -> false
            Gender.ANY -> Random.nextBoolean()
        }
        val pool = if (isMale) data.maleFirst else data.femaleFirst

        val rawFirst = pool[Random.nextInt(pool.size)].trim()
        val rawLast = data.lastNames[Random.nextInt(data.lastNames.size)].trim()

        val firstName = rawFirst.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        val lastName = rawLast.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

        // Always produce a clean "FirstName LastName" so First Name and Last Name split accurately
        val fullName = "$firstName $lastName".trim()
        val countryOpt = getCountryOption(code)

        return PersonDetails(
            fullName = fullName,
            firstName = firstName,
            lastName = lastName,
            prefix = "",
            gender = if (isMale) Gender.MALE else Gender.FEMALE,
            countryCode = countryOpt.code,
            countryName = countryOpt.name,
            flag = countryOpt.flag
        )
    }

    fun generateStrongPassword(length: Int = 10, includeSymbols: Boolean = true): String {
        val safeLen = length.coerceIn(6, 24)
        val upper = "ABCDEFGHJKLMNPQRSTUVWXYZ"
        val lower = "abcdefghijkmnopqrstuvwxyz"
        val digits = "23456789"
        val symbols = "@#$%&*!?"
        val allChars = if (includeSymbols) upper + lower + digits + symbols else upper + lower + digits

        val chars = mutableListOf<Char>()
        chars.add(upper[Random.nextInt(upper.length)])
        chars.add(lower[Random.nextInt(lower.length)])
        chars.add(digits[Random.nextInt(digits.length)])
        if (includeSymbols) {
            chars.add(symbols[Random.nextInt(symbols.length)])
        }
        while (chars.size < safeLen) {
            chars.add(allChars[Random.nextInt(allChars.length)])
        }
        chars.shuffle()
        return chars.joinToString("")
    }

    private fun getCountryData(code: String): CountryNames {
        return namesByCountry[code] ?: when (code) {
            "SA", "AE", "EG", "QA", "KW", "BH", "OM", "JO", "LB", "IQ", "DZ", "MA", "YE" -> namesByCountry[code] ?: namesByCountry["SA"]!!
            "ES", "MX", "AR", "CO", "PE", "CL", "CR", "PA", "UY", "BO" -> namesByCountry[code] ?: namesByCountry["ES"]!!
            "PT", "BR" -> namesByCountry["BR"]!!
            "IN", "NP", "LK" -> namesByCountry["IN"]!!
            "PK", "AF" -> namesByCountry["PK"]!!
            "BD" -> namesByCountry["BD"]!!
            "FR", "BE", "LU" -> namesByCountry["FR"]!!
            "DE", "AT", "CH" -> namesByCountry["DE"]!!
            "NL" -> namesByCountry["NL"] ?: namesByCountry["DE"]!!
            "IT" -> namesByCountry["IT"]!!
            "TR", "AZ" -> namesByCountry["TR"]!!
            "JP" -> namesByCountry["JP"]!!
            "KR" -> namesByCountry["KR"] ?: namesByCountry["JP"]!!
            "CN", "TW", "HK" -> namesByCountry["CN"] ?: namesByCountry["JP"]!!
            "RU", "BY", "KZ", "UZ" -> namesByCountry["RU"] ?: namesByCountry["US"]!!
            "UA" -> namesByCountry["UA"] ?: namesByCountry["RU"] ?: namesByCountry["US"]!!
            "PL", "CZ", "SK" -> namesByCountry["PL"] ?: namesByCountry["DE"]!!
            "SE", "NO", "DK", "FI", "IS" -> namesByCountry["SE"] ?: namesByCountry["DE"]!!
            "PH" -> namesByCountry["PH"] ?: namesByCountry["US"]!!
            "ID" -> namesByCountry["ID"] ?: namesByCountry["BD"]!!
            "MY", "SG" -> namesByCountry["MY"] ?: namesByCountry["ID"]!!
            "VN" -> namesByCountry["VN"] ?: namesByCountry["CN"] ?: namesByCountry["US"]!!
            "TH" -> namesByCountry["TH"] ?: namesByCountry["IN"]!!
            "NG", "GH", "KE", "ZA", "CM", "ZW" -> namesByCountry["NG"] ?: namesByCountry["US"]!!
            "GR", "CY" -> namesByCountry["GR"] ?: namesByCountry["IT"]!!
            "UK", "IE" -> namesByCountry["UK"]!!
            "AU", "NZ" -> namesByCountry["AU"]!!
            "CA" -> namesByCountry["CA"]!!
            else -> namesByCountry["US"]!!
        }
    }

    fun generateName(countryCode: String, gender: Gender = Gender.ANY): String {
        return generatePersonDetails(countryCode, gender).fullName
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
