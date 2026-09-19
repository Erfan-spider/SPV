package com.spiderv2ray.spv.ui

object CountryFlags {
    private val map = listOf(
        "turkey" to "🇹🇷", "turkiye" to "🇹🇷", "tr" to "🇹🇷", "ترکیه" to "🇹🇷",
        "armenia" to "🇦🇲", "am" to "🇦🇲", "ارمنستان" to "🇦🇲",
        "uae" to "🇦🇪", "emirates" to "🇦🇪", "dubai" to "🇦🇪", "امارات" to "🇦🇪",
        "bulgaria" to "🇧🇬", "bg" to "🇧🇬", "بلغارستان" to "🇧🇬",
        "netherlands" to "🇳🇱", "holland" to "🇳🇱", "nl" to "🇳🇱", "هلند" to "🇳🇱",
        "germany" to "🇩🇪", "de" to "🇩🇪", "آلمان" to "🇩🇪",
        "france" to "🇫🇷", "fr" to "🇫🇷", "فرانسه" to "🇫🇷",
        "uk" to "🇬🇧", "england" to "🇬🇧", "britain" to "🇬🇧", "gb" to "🇬🇧", "انگلیس" to "🇬🇧",
        "usa" to "🇺🇸", "united states" to "🇺🇸", "america" to "🇺🇸", "us" to "🇺🇸", "آمریکا" to "🇺🇸",
        "canada" to "🇨🇦", "ca" to "🇨🇦", "کانادا" to "🇨🇦",
        "russia" to "🇷🇺", "ru" to "🇷🇺", "روسیه" to "🇷🇺",
        "iran" to "🇮🇷", "ir" to "🇮🇷", "ایران" to "🇮🇷",
        "japan" to "🇯🇵", "jp" to "🇯🇵", "ژاپن" to "🇯🇵",
        "korea" to "🇰🇷", "kr" to "🇰🇷", "کره" to "🇰🇷",
        "singapore" to "🇸🇬", "sg" to "🇸🇬", "سنگاپور" to "🇸🇬",
        "hong kong" to "🇭🇰", "hk" to "🇭🇰", "هنگ کنگ" to "🇭🇰",
        "india" to "🇮🇳", "in" to "🇮🇳", "هند" to "🇮🇳",
        "sweden" to "🇸🇪", "se" to "🇸🇪", "سوئد" to "🇸🇪",
        "finland" to "🇫🇮", "fi" to "🇫🇮", "فنلاند" to "🇫🇮",
        "poland" to "🇵🇱", "pl" to "🇵🇱", "لهستان" to "🇵🇱",
        "italy" to "🇮🇹", "it" to "🇮🇹", "ایتالیا" to "🇮🇹",
        "spain" to "🇪🇸", "es" to "🇪🇸", "اسپانیا" to "🇪🇸",
        "austria" to "🇦🇹", "at" to "🇦🇹", "اتریش" to "🇦🇹",
        "switzerland" to "🇨🇭", "ch" to "🇨🇭", "سوئیس" to "🇨🇭",
        "romania" to "🇷🇴", "ro" to "🇷🇴", "رومانی" to "🇷🇴",
        "ukraine" to "🇺🇦", "ua" to "🇺🇦", "اوکراین" to "🇺🇦",
        "iraq" to "🇮🇶", "iq" to "🇮🇶", "عراق" to "🇮🇶",
        "cyprus" to "🇨🇾", "cy" to "🇨🇾", "قبرس" to "🇨🇾",
        "georgia" to "🇬🇪", "ge" to "🇬🇪", "گرجستان" to "🇬🇪",
        "azerbaijan" to "🇦🇿", "az" to "🇦🇿", "آذربایجان" to "🇦🇿",
    )

    data class Info(val flag: String, val name: String)

    // Converts a real ISO 3166-1 alpha-2 country code (e.g. "TR", from an IP
    // geolocation lookup) into its flag emoji, algorithmically - this covers
    // every country, unlike the tag-name guess list above which only knows
    // the handful of names it was given.
    fun flagFromCountryCode(code: String?): String {
        if (code.isNullOrBlank() || code.length != 2) return "🌐"
        val upper = code.uppercase()
        if (!upper[0].isLetter() || !upper[1].isLetter()) return "🌐"
        val base = 0x1F1E6 // regional indicator symbol letter A
        val first = base + (upper[0] - 'A')
        val second = base + (upper[1] - 'A')
        return String(Character.toChars(first)) + String(Character.toChars(second))
    }

    fun fromTag(tag: String?): Info {
        if (tag.isNullOrBlank()) return Info("🌐", "Unknown")
        val lower = tag.lowercase()
        for ((key, flag) in map) {
            if (lower.contains(key)) {
                val name = key.replaceFirstChar { it.uppercase() }
                    .replace("Turkiye", "Turkey")
                    .replace("Uae", "UAE")
                    .replace("Usa", "USA")
                    .replace("Uk", "UK")
                    .replace("Hong kong", "Hong Kong")
                return Info(flag, prettyName(key))
            }
        }
        // emoji already in tag?
        val emoji = tag.takeWhile { !it.isLetter() && !it.isDigit() }.trim()
        if (emoji.isNotEmpty() && emoji.any { Character.UnicodeBlock.of(it) == Character.UnicodeBlock.MISCELLANEOUS_SYMBOLS_AND_PICTOGRAPHS || Character.UnicodeBlock.of(it)?.toString()?.contains("EMOJI") == true || it.code > 0x1F1E0 }) {
            return Info(emoji.take(4), tag.filter { it.isLetter() || it.isWhitespace() }.trim().ifEmpty { "Server" })
        }
        return Info("🌐", tag.take(24))
    }

    private fun prettyName(key: String): String = when (key) {
        "turkey", "turkiye", "tr", "ترکیه" -> "Turkey"
        "armenia", "am", "ارمنستان" -> "Armenia"
        "uae", "emirates", "dubai", "امارات" -> "UAE"
        "bulgaria", "bg", "بلغارستان" -> "Bulgaria"
        "netherlands", "holland", "nl", "هلند" -> "Netherlands"
        "germany", "de", "آلمان" -> "Germany"
        "france", "fr", "فرانسه" -> "France"
        "uk", "england", "britain", "gb", "انگلیس" -> "United Kingdom"
        "usa", "united states", "america", "us", "آمریکا" -> "United States"
        "canada", "ca", "کانادا" -> "Canada"
        "russia", "ru", "روسیه" -> "Russia"
        "iran", "ir", "ایران" -> "Iran"
        "japan", "jp", "ژاپن" -> "Japan"
        "korea", "kr", "کره" -> "South Korea"
        "singapore", "sg", "سنگاپور" -> "Singapore"
        "hong kong", "hk", "هنگ کنگ" -> "Hong Kong"
        "india", "in", "هند" -> "India"
        "sweden", "se", "سوئد" -> "Sweden"
        "finland", "fi", "فنلاند" -> "Finland"
        "poland", "pl", "لهستان" -> "Poland"
        "italy", "it", "ایتالیا" -> "Italy"
        "spain", "es", "اسپانیا" -> "Spain"
        "austria", "at", "اتریش" -> "Austria"
        "switzerland", "ch", "سوئیس" -> "Switzerland"
        "romania", "ro", "رومانی" -> "Romania"
        "ukraine", "ua", "اوکراین" -> "Ukraine"
        "iraq", "iq", "عراق" -> "Iraq"
        "cyprus", "cy", "قبرس" -> "Cyprus"
        "georgia", "ge", "گرجستان" -> "Georgia"
        "azerbaijan", "az", "آذربایجان" -> "Azerbaijan"
        else -> key.replaceFirstChar { it.uppercase() }
    }
}
