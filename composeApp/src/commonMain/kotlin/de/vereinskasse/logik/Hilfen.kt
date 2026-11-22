package de.vereinskasse.logik

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.random.Random

/** Formatiert Cent-Beträge als "12,50 €". */
fun geld(cent: Long, mitEuro: Boolean = true): String {
    val vorzeichen = if (cent < 0) "-" else ""
    val betrag = abs(cent)
    val euro = betrag / 100
    val rest = (betrag % 100).toString().padStart(2, '0')
    val euroText = euro.toString().reversed().chunked(3).joinToString(".").reversed()
    return "$vorzeichen$euroText,$rest" + if (mitEuro) " €" else ""
}

/**
 * Wandelt eine Eingabe wie "2,50", "2.5", "2" oder "1.234,50" in Cent um.
 * Liefert null bei ungültiger Eingabe.
 */
fun parseGeld(eingabe: String): Long? {
    var text = eingabe.trim().replace("€", "").replace(" ", "")
    if (text.isEmpty()) return null
    val negativ = text.startsWith("-")
    if (negativ) text = text.substring(1)
    // Tausenderpunkte entfernen, wenn zusätzlich ein Komma vorkommt
    if (text.contains(',')) text = text.replace(".", "").replace(',', '.')
    if (text.count { it == '.' } > 1) return null
    if (!text.all { it.isDigit() || it == '.' } || text == ".") return null
    val teile = text.split('.')
    val euro = teile[0].ifEmpty { "0" }.toLongOrNull() ?: return null
    val centText = teile.getOrNull(1) ?: ""
    if (centText.length > 2) return null
    val cent = centText.padEnd(2, '0').ifEmpty { "00" }.toLongOrNull() ?: return null
    val ergebnis = euro * 100 + cent
    return if (negativ) -ergebnis else ergebnis
}

/** Betrag für Eingabefelder, z. B. "2,50" (leer bei 0, wenn gewünscht). */
fun geldEingabe(cent: Long, leerBeiNull: Boolean = false): String =
    if (leerBeiNull && cent == 0L) "" else geld(cent, mitEuro = false).replace(".", "")

fun jetzt(): Long = Clock.System.now().toEpochMilliseconds()

fun neueId(): String =
    jetzt().toString(36) + Random.nextLong(0, Long.MAX_VALUE).toString(36).take(8)

private fun Int.zweistellig() = toString().padStart(2, '0')

fun datumZeit(millis: Long): String {
    val t = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault())
    return "${t.dayOfMonth.zweistellig()}.${t.monthNumber.zweistellig()}.${t.year} " +
        "${t.hour.zweistellig()}:${t.minute.zweistellig()}"
}

fun datum(millis: Long): String = datumZeit(millis).substringBefore(' ')

fun uhrzeit(millis: Long): String = datumZeit(millis).substringAfter(' ')

/** Vorschläge für den gegebenen Betrag: passende Scheine/Beträge oberhalb der Summe. */
fun schnellBetraege(summeCent: Long): List<Long> {
    if (summeCent <= 0) return emptyList()
    val kandidaten = mutableSetOf<Long>()
    // Nächster voller Euro, nächste 5 €, nächste 10 €
    for (schritt in listOf(100L, 500L, 1000L)) {
        val aufgerundet = ((summeCent + schritt - 1) / schritt) * schritt
        if (aufgerundet > summeCent) kandidaten += aufgerundet
    }
    for (schein in listOf(500L, 1000L, 2000L, 5000L, 10000L)) {
        if (schein > summeCent) kandidaten += schein
    }
    return kandidaten.sorted().take(6)
}

/** Farbpalette für Artikelgruppen (ARGB). */
val GRUPPEN_FARBEN: List<Long> = listOf(
    0xFF1E88E5, // Blau
    0xFFE53935, // Rot
    0xFF43A047, // Grün
    0xFFFB8C00, // Orange
    0xFF8E24AA, // Lila
    0xFF00897B, // Türkis
    0xFFF4B400, // Gelb
    0xFF6D4C41, // Braun
    0xFFD81B60, // Pink
    0xFF546E7A, // Blaugrau
    0xFF3949AB, // Indigo
    0xFF7CB342, // Hellgrün
)
