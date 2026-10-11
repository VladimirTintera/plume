package eu.tintera.plume.features.conversation

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Letiště, které umíme v trase letu poznat: [city] je anglický název (tak ho najdou mapy
 * i kalendář), [zone] časové pásmo IANA — odlet i přílet ve zprávě jsou MÍSTNÍ časy letišť.
 */
data class Airport(val code: String, val city: String, val latitude: Double, val longitude: Double, val zone: String) {
    /** Jak se letiště píše do místa události: „Prague (PRG)". */
    val label: String get() = "$city ($code)"
}

/** Vzdálenost po povrchu Země v kilometrech (haversine). */
internal fun Airport.distanceKm(other: Airport): Double {
    fun radians(degrees: Double) = degrees * kotlin.math.PI / 180
    val dLat = radians(other.latitude - latitude)
    val dLon = radians(other.longitude - longitude)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(radians(latitude)) * cos(radians(other.latitude)) * sin(dLon / 2) * sin(dLon / 2)
    return 2 * EARTH_RADIUS_KM * asin(sqrt(a))
}

/**
 * Odhad délky letu v minutách: cestovní rychlost a půl hodiny na vzlet, přistání a pojíždění,
 * zaokrouhleno nahoru na pět minut. Praha–Londýn (1 047 km) tak vyjde 2 h (jízdní řád 1 h 55 min
 * až 2 h 05 min). Jen odhad —
 * sheet to říká a kalendář ho nechá upravit.
 */
internal fun estimatedFlightMinutes(from: Airport, to: Airport): Int {
    val minutes = from.distanceKm(to) / CRUISE_KMH * 60 + GROUND_MINUTES
    return kotlin.math.ceil(minutes / 5).toInt() * 5
}

private const val EARTH_RADIUS_KM = 6371.0
private const val CRUISE_KMH = 780.0
private const val GROUND_MINUTES = 35.0

/**
 * Nejběžnější letiště Evropy a největší světová. Chybějící letiště jen znamená, že se trasa
 * a odhad délky nenabídnou. Souřadnice stačí přibližné (odhad), pásma musí sedět.
 */
internal val AIRPORTS: Map<String, Airport> = listOf(
    Airport("PRG", "Prague", 50.1008, 14.2600, "Europe/Prague"),
    Airport("BRQ", "Brno", 49.1513, 16.6944, "Europe/Prague"),
    Airport("OSR", "Ostrava", 49.6963, 18.1111, "Europe/Prague"),
    Airport("VIE", "Vienna", 48.1103, 16.5697, "Europe/Vienna"),
    Airport("BTS", "Bratislava", 48.1702, 17.2127, "Europe/Bratislava"),
    Airport("KSC", "Kosice", 48.6631, 21.2411, "Europe/Bratislava"),
    Airport("BUD", "Budapest", 47.4369, 19.2556, "Europe/Budapest"),
    Airport("WAW", "Warsaw", 52.1657, 20.9671, "Europe/Warsaw"),
    Airport("KRK", "Krakow", 50.0777, 19.7848, "Europe/Warsaw"),
    Airport("BER", "Berlin", 52.3667, 13.5033, "Europe/Berlin"),
    Airport("MUC", "Munich", 48.3538, 11.7861, "Europe/Berlin"),
    Airport("FRA", "Frankfurt", 50.0379, 8.5622, "Europe/Berlin"),
    Airport("DUS", "Dusseldorf", 51.2895, 6.7668, "Europe/Berlin"),
    Airport("HAM", "Hamburg", 53.6304, 9.9882, "Europe/Berlin"),
    Airport("STR", "Stuttgart", 48.6899, 9.2219, "Europe/Berlin"),
    Airport("CGN", "Cologne", 50.8659, 7.1427, "Europe/Berlin"),
    Airport("ZRH", "Zurich", 47.4582, 8.5555, "Europe/Zurich"),
    Airport("GVA", "Geneva", 46.2381, 6.1089, "Europe/Zurich"),
    Airport("BSL", "Basel", 47.5900, 7.5291, "Europe/Zurich"),
    Airport("AMS", "Amsterdam", 52.3105, 4.7683, "Europe/Amsterdam"),
    Airport("BRU", "Brussels", 50.9010, 4.4844, "Europe/Brussels"),
    Airport("CRL", "Charleroi", 50.4592, 4.4538, "Europe/Brussels"),
    Airport("LHR", "London Heathrow", 51.4700, -0.4543, "Europe/London"),
    Airport("LGW", "London Gatwick", 51.1537, -0.1821, "Europe/London"),
    Airport("STN", "London Stansted", 51.8850, 0.2350, "Europe/London"),
    Airport("LTN", "London Luton", 51.8747, -0.3683, "Europe/London"),
    Airport("MAN", "Manchester", 53.3650, -2.2728, "Europe/London"),
    Airport("EDI", "Edinburgh", 55.9500, -3.3725, "Europe/London"),
    Airport("DUB", "Dublin", 53.4213, -6.2701, "Europe/Dublin"),
    Airport("CDG", "Paris Charles de Gaulle", 49.0097, 2.5479, "Europe/Paris"),
    Airport("ORY", "Paris Orly", 48.7262, 2.3652, "Europe/Paris"),
    Airport("NCE", "Nice", 43.6584, 7.2159, "Europe/Paris"),
    Airport("LYS", "Lyon", 45.7256, 5.0811, "Europe/Paris"),
    Airport("MRS", "Marseille", 43.4393, 5.2214, "Europe/Paris"),
    Airport("BCN", "Barcelona", 41.2974, 2.0833, "Europe/Madrid"),
    Airport("MAD", "Madrid", 40.4983, -3.5676, "Europe/Madrid"),
    Airport("AGP", "Malaga", 36.6749, -4.4991, "Europe/Madrid"),
    Airport("PMI", "Palma de Mallorca", 39.5517, 2.7388, "Europe/Madrid"),
    Airport("ALC", "Alicante", 38.2822, -0.5582, "Europe/Madrid"),
    Airport("TFS", "Tenerife South", 28.0445, -16.5725, "Atlantic/Canary"),
    Airport("LPA", "Gran Canaria", 27.9319, -15.3866, "Atlantic/Canary"),
    Airport("LIS", "Lisbon", 38.7742, -9.1342, "Europe/Lisbon"),
    Airport("OPO", "Porto", 41.2481, -8.6814, "Europe/Lisbon"),
    Airport("FAO", "Faro", 37.0144, -7.9659, "Europe/Lisbon"),
    Airport("FCO", "Rome Fiumicino", 41.8003, 12.2389, "Europe/Rome"),
    Airport("MXP", "Milan Malpensa", 45.6306, 8.7281, "Europe/Rome"),
    Airport("LIN", "Milan Linate", 45.4451, 9.2767, "Europe/Rome"),
    Airport("BGY", "Bergamo", 45.6739, 9.7042, "Europe/Rome"),
    Airport("VCE", "Venice", 45.5053, 12.3519, "Europe/Rome"),
    Airport("NAP", "Naples", 40.8860, 14.2908, "Europe/Rome"),
    Airport("CTA", "Catania", 37.4668, 15.0664, "Europe/Rome"),
    Airport("ATH", "Athens", 37.9364, 23.9445, "Europe/Athens"),
    Airport("HER", "Heraklion", 35.3397, 25.1803, "Europe/Athens"),
    Airport("RHO", "Rhodes", 36.4054, 28.0862, "Europe/Athens"),
    Airport("CFU", "Corfu", 39.6019, 19.9117, "Europe/Athens"),
    Airport("SKG", "Thessaloniki", 40.5197, 22.9709, "Europe/Athens"),
    Airport("IST", "Istanbul", 41.2753, 28.7519, "Europe/Istanbul"),
    Airport("SAW", "Istanbul Sabiha Gokcen", 40.8986, 29.3092, "Europe/Istanbul"),
    Airport("AYT", "Antalya", 36.8987, 30.8005, "Europe/Istanbul"),
    Airport("CPH", "Copenhagen", 55.6180, 12.6560, "Europe/Copenhagen"),
    Airport("ARN", "Stockholm Arlanda", 59.6519, 17.9186, "Europe/Stockholm"),
    Airport("OSL", "Oslo", 60.1939, 11.1004, "Europe/Oslo"),
    Airport("HEL", "Helsinki", 60.3172, 24.9633, "Europe/Helsinki"),
    Airport("KEF", "Reykjavik Keflavik", 63.9850, -22.6056, "Atlantic/Reykjavik"),
    Airport("RIX", "Riga", 56.9236, 23.9711, "Europe/Riga"),
    Airport("VNO", "Vilnius", 54.6341, 25.2858, "Europe/Vilnius"),
    Airport("TLL", "Tallinn", 59.4133, 24.8328, "Europe/Tallinn"),
    Airport("OTP", "Bucharest", 44.5711, 26.0850, "Europe/Bucharest"),
    Airport("SOF", "Sofia", 42.6952, 23.4062, "Europe/Sofia"),
    Airport("BEG", "Belgrade", 44.8184, 20.3091, "Europe/Belgrade"),
    Airport("ZAG", "Zagreb", 45.7429, 16.0688, "Europe/Zagreb"),
    Airport("SPU", "Split", 43.5389, 16.2980, "Europe/Zagreb"),
    Airport("DBV", "Dubrovnik", 42.5614, 18.2682, "Europe/Zagreb"),
    Airport("LJU", "Ljubljana", 46.2237, 14.4576, "Europe/Ljubljana"),
    Airport("MLA", "Malta", 35.8575, 14.4775, "Europe/Malta"),
    Airport("LCA", "Larnaca", 34.8751, 33.6249, "Asia/Nicosia"),
    Airport("TLV", "Tel Aviv", 32.0055, 34.8854, "Asia/Jerusalem"),
    Airport("DXB", "Dubai", 25.2532, 55.3657, "Asia/Dubai"),
    Airport("AUH", "Abu Dhabi", 24.4330, 54.6511, "Asia/Dubai"),
    Airport("DOH", "Doha", 25.2731, 51.6081, "Asia/Qatar"),
    Airport("CAI", "Cairo", 30.1219, 31.4056, "Africa/Cairo"),
    Airport("HRG", "Hurghada", 27.1783, 33.7994, "Africa/Cairo"),
    Airport("SSH", "Sharm el-Sheikh", 27.9773, 34.3950, "Africa/Cairo"),
    Airport("RAK", "Marrakesh", 31.6069, -8.0363, "Africa/Casablanca"),
    Airport("JFK", "New York JFK", 40.6413, -73.7781, "America/New_York"),
    Airport("EWR", "Newark", 40.6895, -74.1745, "America/New_York"),
    Airport("BOS", "Boston", 42.3656, -71.0096, "America/New_York"),
    Airport("IAD", "Washington Dulles", 38.9531, -77.4565, "America/New_York"),
    Airport("MIA", "Miami", 25.7959, -80.2870, "America/New_York"),
    Airport("ATL", "Atlanta", 33.6407, -84.4277, "America/New_York"),
    Airport("ORD", "Chicago O'Hare", 41.9742, -87.9073, "America/Chicago"),
    Airport("DFW", "Dallas Fort Worth", 32.8998, -97.0403, "America/Chicago"),
    Airport("DEN", "Denver", 39.8561, -104.6737, "America/Denver"),
    Airport("LAX", "Los Angeles", 33.9416, -118.4085, "America/Los_Angeles"),
    Airport("SFO", "San Francisco", 37.6213, -122.3790, "America/Los_Angeles"),
    Airport("SEA", "Seattle", 47.4502, -122.3088, "America/Los_Angeles"),
    Airport("YYZ", "Toronto", 43.6777, -79.6248, "America/Toronto"),
    Airport("YUL", "Montreal", 45.4706, -73.7408, "America/Toronto"),
    Airport("YVR", "Vancouver", 49.1967, -123.1815, "America/Vancouver"),
    Airport("MEX", "Mexico City", 19.4361, -99.0719, "America/Mexico_City"),
    Airport("CUN", "Cancun", 21.0365, -86.8770, "America/Cancun"),
    Airport("GRU", "Sao Paulo", -23.4356, -46.4731, "America/Sao_Paulo"),
    Airport("HND", "Tokyo Haneda", 35.5494, 139.7798, "Asia/Tokyo"),
    Airport("NRT", "Tokyo Narita", 35.7720, 140.3929, "Asia/Tokyo"),
    Airport("ICN", "Seoul Incheon", 37.4602, 126.4407, "Asia/Seoul"),
    Airport("PEK", "Beijing", 40.0799, 116.6031, "Asia/Shanghai"),
    Airport("PVG", "Shanghai Pudong", 31.1443, 121.8083, "Asia/Shanghai"),
    Airport("HKG", "Hong Kong", 22.3080, 113.9185, "Asia/Hong_Kong"),
    Airport("SIN", "Singapore", 1.3644, 103.9915, "Asia/Singapore"),
    Airport("BKK", "Bangkok", 13.6900, 100.7501, "Asia/Bangkok"),
    Airport("KUL", "Kuala Lumpur", 2.7456, 101.7072, "Asia/Kuala_Lumpur"),
    Airport("DPS", "Bali Denpasar", -8.7482, 115.1675, "Asia/Makassar"),
    Airport("DEL", "Delhi", 28.5562, 77.1000, "Asia/Kolkata"),
    Airport("BOM", "Mumbai", 19.0896, 72.8656, "Asia/Kolkata"),
    Airport("SYD", "Sydney", -33.9399, 151.1753, "Australia/Sydney"),
    Airport("MEL", "Melbourne", -37.6690, 144.8410, "Australia/Melbourne"),
).associateBy { it.code }

/**
 * Města a letiště psaná česky — bez diakritiky, malými, jako ZAČÁTEK slova kvůli skloňování
 * („prah" = Praha, Prahy, Praze; „londyn" = Londýn, Londýna). Uznají se jen za českou předložkou
 * (z, ze, do, na) ve zprávě o letu. Město s víc letišti dostane hlavní (Londýn → Heathrow);
 * kód letiště ve zprávě má vždycky přednost.
 *
 * Vynechaná jsou jména, která jsou zároveň běžným slovem nebo jménem (Nice, Sofie, Porto,
 * Palma), a Kolín nad Rýnem — „z Kolína" je spíš Kolín u Prahy.
 */
internal val CZECH_CITY_STEMS: Map<String, String> = linkedMapOf(
    "ruzyn" to "PRG", "prah" to "PRG", "brn" to "BRQ", "ostrav" to "OSR", "viden" to "VIE", "vidn" to "VIE",
    "bratislav" to "BTS", "kosic" to "KSC", "budapest" to "BUD", "varsav" to "WAW", "krakov" to "KRK",
    "berlin" to "BER", "mnichov" to "MUC", "frankfurt" to "FRA", "dusseldorf" to "DUS", "hambur" to "HAM",
    "stuttgart" to "STR", "curych" to "ZRH", "zenev" to "GVA", "basilej" to "BSL", "amsterdam" to "AMS",
    "brusel" to "BRU", "londyn" to "LHR", "manchester" to "MAN", "edinburgh" to "EDI", "dublin" to "DUB",
    "pariz" to "CDG", "lyon" to "LYS", "marseill" to "MRS", "barcelon" to "BCN", "madrid" to "MAD",
    "malag" to "AGP", "mallorc" to "PMI", "alicant" to "ALC", "tenerif" to "TFS", "gran canari" to "LPA",
    "lisabon" to "LIS", "faro" to "FAO", "milan" to "MXP", "benat" to "VCE", "neapol" to "NAP",
    "katani" to "CTA", "aten" to "ATH", "kret" to "HER", "rhod" to "RHO", "korfu" to "CFU", "solun" to "SKG",
    "istanbul" to "IST", "antaly" to "AYT", "kodan" to "CPH", "stockholm" to "ARN", "helsink" to "HEL",
    "reykjavik" to "KEF", "vilni" to "VNO", "tallin" to "TLL", "bukurest" to "OTP", "belehrad" to "BEG",
    "zahreb" to "ZAG", "split" to "SPU", "dubrovni" to "DBV", "lublan" to "LJU", "malt" to "MLA",
    "larnak" to "LCA", "dubaj" to "DXB", "abu dhab" to "AUH", "dauh" to "DOH", "kahir" to "CAI",
    "hurghad" to "HRG", "sarm" to "SSH", "marrakes" to "RAK", "new york" to "JFK", "newyork" to "JFK",
    "boston" to "BOS", "washington" to "IAD", "miami" to "MIA", "majami" to "MIA", "atlant" to "ATL",
    "chicag" to "ORD", "dallas" to "DFW", "denver" to "DEN", "los angeles" to "LAX", "san francisc" to "SFO",
    "seattl" to "SEA", "toront" to "YYZ", "montreal" to "YUL", "vancouver" to "YVR", "mexik" to "MEX",
    "cancun" to "CUN", "sao paul" to "GRU", "toki" to "HND", "soul" to "ICN", "peking" to "PEK",
    "sanghaj" to "PVG", "hongkong" to "HKG", "hong kong" to "HKG", "singapur" to "SIN", "bangkok" to "BKK",
    "kuala lumpur" to "KUL", "bali" to "DPS", "dill" to "DEL", "bombaj" to "BOM", "mumbaj" to "BOM",
    "sydney" to "SYD", "melbourn" to "MEL",
)

/** Řím má krátký kořen („rim" je i ráfek), proto jen celé tvary. Stejně Oslo a Riga. */
internal val CZECH_CITY_WORDS: Map<String, String> = mapOf(
    "rim" to "FCO", "rima" to "FCO", "rime" to "FCO", "rimu" to "FCO",
    "oslo" to "OSL", "osla" to "OSL", "rigy" to "RIX", "riga" to "RIX", "rige" to "RIX", "rigu" to "RIX",
)

/**
 * Anglické názvy měst (malými), jen za „from" / „to". Bez slov, která jsou zároveň běžná
 * („Split", „Nice", „Malta" ne — „to split", „to nice").
 */
internal val ENGLISH_CITIES: Map<String, String> = linkedMapOf(
    "prague" to "PRG", "vienna" to "VIE", "bratislava" to "BTS", "budapest" to "BUD", "warsaw" to "WAW",
    "krakow" to "KRK", "berlin" to "BER", "munich" to "MUC", "frankfurt" to "FRA", "zurich" to "ZRH",
    "geneva" to "GVA", "amsterdam" to "AMS", "brussels" to "BRU", "london" to "LHR", "manchester" to "MAN",
    "edinburgh" to "EDI", "dublin" to "DUB", "paris" to "CDG", "barcelona" to "BCN", "madrid" to "MAD",
    "lisbon" to "LIS", "rome" to "FCO", "milan" to "MXP", "venice" to "VCE", "naples" to "NAP",
    "athens" to "ATH", "istanbul" to "IST", "copenhagen" to "CPH", "stockholm" to "ARN", "oslo" to "OSL",
    "helsinki" to "HEL", "reykjavik" to "KEF", "dubai" to "DXB", "doha" to "DOH", "cairo" to "CAI",
    "new york" to "JFK", "boston" to "BOS", "washington" to "IAD", "miami" to "MIA", "chicago" to "ORD",
    "los angeles" to "LAX", "san francisco" to "SFO", "seattle" to "SEA", "toronto" to "YYZ",
    "vancouver" to "YVR", "mexico city" to "MEX", "tokyo" to "HND", "seoul" to "ICN", "beijing" to "PEK",
    "shanghai" to "PVG", "hong kong" to "HKG", "singapore" to "SIN", "bangkok" to "BKK", "bali" to "DPS",
    "delhi" to "DEL", "mumbai" to "BOM", "sydney" to "SYD", "melbourne" to "MEL",
)
