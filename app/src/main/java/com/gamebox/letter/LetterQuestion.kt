package com.gamebox.letter

data class LetterQuestion(
    val picture: String,
    val englishWord: String,
    val hebrewWord: String,
    val englishLetter: String,
    val hebrewLetter: String
) {
    fun word(language: LetterLanguage) =
        if (language == LetterLanguage.ENGLISH) englishWord else hebrewWord

    fun letter(language: LetterLanguage) =
        if (language == LetterLanguage.ENGLISH) englishLetter else hebrewLetter
}

enum class LetterLanguage { ENGLISH, HEBREW }

object LetterQuestionBank {
    val questions = listOf(
        LetterQuestion("🍎", "apple", "תפוח", "A", "ת"),
        LetterQuestion("🐜", "ant", "נמלה", "A", "נ"),
        LetterQuestion("⚽", "ball", "כדור", "B", "כ"),
        LetterQuestion("🐻", "bear", "דוב", "B", "ד"),
        LetterQuestion("🐱", "cat", "חתול", "C", "ח"),
        LetterQuestion("🚗", "car", "מכונית", "C", "מ"),
        LetterQuestion("🐶", "dog", "כלב", "D", "כ"),
        LetterQuestion("🦆", "duck", "ברווז", "D", "ב"),
        LetterQuestion("🥚", "egg", "ביצה", "E", "ב"),
        LetterQuestion("🐘", "elephant", "פיל", "E", "פ"),
        LetterQuestion("🐟", "fish", "דג", "F", "ד"),
        LetterQuestion("🌸", "flower", "פרח", "F", "פ"),
        LetterQuestion("🐐", "goat", "עז", "G", "ע"),
        LetterQuestion("🍇", "grapes", "ענבים", "G", "ע"),
        LetterQuestion("🎸", "guitar", "גיטרה", "G", "ג"),
        LetterQuestion("🐪", "camel", "גמל", "C", "ג"),
        LetterQuestion("🏠", "house", "בית", "H", "ב"),
        LetterQuestion("🐴", "horse", "סוס", "H", "ס"),
        LetterQuestion("🦛", "hippo", "היפופוטם", "H", "ה"),
        LetterQuestion("🍦", "ice cream", "גלידה", "I", "ג"),
        LetterQuestion("🧊", "ice", "קרח", "I", "ק"),
        LetterQuestion("🧃", "juice", "מיץ", "J", "מ"),
        LetterQuestion("🧥", "jacket", "מעיל", "J", "מ"),
        LetterQuestion("🔑", "key", "מפתח", "K", "מ"),
        LetterQuestion("🪁", "kite", "עפיפון", "K", "ע"),
        LetterQuestion("🦁", "lion", "אריה", "L", "א"),
        LetterQuestion("🍋", "lemon", "לימון", "L", "ל"),
        LetterQuestion("🌙", "moon", "ירח", "M", "י"),
        LetterQuestion("🐭", "mouse", "עכבר", "M", "ע"),
        LetterQuestion("🪺", "nest", "קן", "N", "ק"),
        LetterQuestion("👃", "nose", "אף", "N", "א"),
        LetterQuestion("🐙", "octopus", "תמנון", "O", "ת"),
        LetterQuestion("🍊", "orange", "תפוז", "O", "ת"),
        LetterQuestion("🐼", "panda", "פנדה", "P", "פ"),
        LetterQuestion("🍕", "pizza", "פיצה", "P", "פ"),
        LetterQuestion("👑", "queen", "מלכה", "Q", "מ"),
        LetterQuestion("🧀", "quiche", "קיש", "Q", "ק"),
        LetterQuestion("🐇", "rabbit", "ארנב", "R", "א"),
        LetterQuestion("🌈", "rainbow", "קשת", "R", "ק"),
        LetterQuestion("☀️", "sun", "שמש", "S", "ש"),
        LetterQuestion("🐍", "snake", "נחש", "S", "נ"),
        LetterQuestion("🐢", "turtle", "צב", "T", "צ"),
        LetterQuestion("🍅", "tomato", "עגבנייה", "T", "ע"),
        LetterQuestion("☂️", "umbrella", "מטרייה", "U", "מ"),
        LetterQuestion("🦄", "unicorn", "חד קרן", "U", "ח"),
        LetterQuestion("📺", "television", "טלוויזיה", "T", "ט"),
        LetterQuestion("🍞", "toast", "טוסט", "T", "ט"),
        LetterQuestion("🎻", "violin", "כינור", "V", "כ"),
        LetterQuestion("🌋", "volcano", "הר געש", "V", "ה"),
        LetterQuestion("🐳", "whale", "לווייתן", "W", "ל"),
        LetterQuestion("🍉", "watermelon", "אבטיח", "W", "א"),
        LetterQuestion("🪟", "window", "חלון", "W", "ח"),
        LetterQuestion("🪟", "curtain", "וילון", "C", "ו"),
        LetterQuestion("🌹", "rose", "ורד", "R", "ו"),
        LetterQuestion("🎄", "xmas tree", "עץ אשוח", "X", "ע"),
        LetterQuestion("🩻", "x-ray", "צילום רנטגן", "X", "צ"),
        LetterQuestion("🧶", "yarn", "חוט צמר", "Y", "ח"),
        LetterQuestion("🪀", "yo-yo", "יו-יו", "Y", "י"),
        LetterQuestion("🦓", "zebra", "זברה", "Z", "ז"),
        LetterQuestion("🤐", "zipper", "רוכסן", "Z", "ר"),
        LetterQuestion("🫒", "olive", "זית", "O", "ז"),
        LetterQuestion("📻", "radio", "רדיו", "R", "ר"),
        LetterQuestion("🧴", "shampoo", "שמפו", "S", "ש"),
        LetterQuestion("🍫", "chocolate", "שוקולד", "C", "ש"),
        LetterQuestion("🥪", "sandwich", "סנדוויץ׳", "S", "ס")
    )

    fun alphabet(language: LetterLanguage): List<String> =
        if (language == LetterLanguage.ENGLISH) {
            ('A'..'Z').map(Char::toString)
        } else {
            listOf("א", "ב", "ג", "ד", "ה", "ו", "ז", "ח", "ט", "י", "כ", "ל", "מ", "נ", "ס", "ע", "פ", "צ", "ק", "ר", "ש", "ת")
        }
}
