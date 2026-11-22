package de.vereinskasse.logik

import de.vereinskasse.model.Bestellung
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Ein Festtag beginnt um 6:00 Uhr – Bestellungen nach Mitternacht zählen noch zum Vortag. */
const val TAGESWECHSEL_STUNDE = 6

const val VIERTELSTUNDE_MS = 15 * 60 * 1000L

fun festtag(millis: Long, zone: TimeZone = TimeZone.currentSystemDefault()): LocalDate =
    Instant.fromEpochMilliseconds(millis - TAGESWECHSEL_STUNDE * 3_600_000L).toLocalDateTime(zone).date

fun List<Bestellung>.amTag(tag: LocalDate?, zone: TimeZone = TimeZone.currentSystemDefault()): List<Bestellung> =
    if (tag == null) this else filter { festtag(it.zeit, zone) == tag }

fun List<Bestellung>.festtage(zone: TimeZone = TimeZone.currentSystemDefault()): List<LocalDate> =
    map { festtag(it.zeit, zone) }.distinct().sorted()

private val WOCHENTAGE = mapOf(
    DayOfWeek.MONDAY to "Mo", DayOfWeek.TUESDAY to "Di", DayOfWeek.WEDNESDAY to "Mi", DayOfWeek.THURSDAY to "Do",
    DayOfWeek.FRIDAY to "Fr", DayOfWeek.SATURDAY to "Sa", DayOfWeek.SUNDAY to "So",
)

/** z. B. "Sa 13.06." */
fun tagText(tag: LocalDate): String =
    "${WOCHENTAGE[tag.dayOfWeek]} ${tag.dayOfMonth.toString().padStart(2, '0')}.${tag.monthNumber.toString().padStart(2, '0')}."

/**
 * Verkaufte Stück pro Viertelstunde.
 * [zeitpunkte]: Beginn jeder Viertelstunde; null = zusammengefasste Pause (z. B. Nacht).
 * [mengen]: je Artikelname die Stückzahl pro Viertelstunde (gleiche Länge wie zeitpunkte).
 */
data class Verlauf(
    val zeitpunkte: List<Long?>,
    val mengen: Map<String, List<Int>>,
) {
    val anzahl: Int get() = zeitpunkte.size

    fun kumuliert(name: String): List<Int> {
        var summe = 0
        return (mengen[name] ?: emptyList()).map { summe += it; summe }
    }

    fun gesamt(name: String): Int = mengen[name]?.sum() ?: 0

    companion object {
        val LEER = Verlauf(emptyList(), emptyMap())
    }
}

/**
 * Berechnet den Verlauf aus Bestellungen (Stornos und Pfand-Rückgaben zählen nicht).
 * Pausen ohne Verkauf, die länger als [maxLuecke] Viertelstunden sind, werden zu einem Eintrag zusammengefasst.
 */
fun List<Bestellung>.verlauf(mitHelfer: Boolean, maxLuecke: Int = 8): Verlauf {
    val eintraege = filter { !it.storniert }.flatMap { b ->
        val slot = b.zeit.floorDiv(VIERTELSTUNDE_MS)
        b.positionen.filter { !it.pfandRueckgabe && (mitHelfer || !it.helfer) }.map { Triple(slot, it.name, it.menge) }
    }.filter { it.third != 0 }
    if (eintraege.isEmpty()) return Verlauf.LEER

    val proSlot = eintraege.groupBy { it.first }
    val erster = proSlot.keys.min()
    val letzter = proSlot.keys.max()
    val namen = eintraege.map { it.second }.distinct()

    val zeitpunkte = mutableListOf<Long?>()
    val mengen = namen.associateWith { mutableListOf<Int>() }
    var leer = 0L
    fun leereAnhaengen(von: Long, anzahl: Long) {
        if (anzahl <= 0) return
        if (anzahl > maxLuecke) {
            zeitpunkte += null
            mengen.values.forEach { it += 0 }
        } else {
            for (i in 0 until anzahl) {
                zeitpunkte += (von + i) * VIERTELSTUNDE_MS
                mengen.values.forEach { it += 0 }
            }
        }
    }
    for (slot in erster..letzter) {
        val inSlot = proSlot[slot]
        if (inSlot == null) {
            leer++
            continue
        }
        leereAnhaengen(slot - leer, leer)
        leer = 0
        zeitpunkte += slot * VIERTELSTUNDE_MS
        val summen = inSlot.groupBy { it.second }.mapValues { (_, l) -> l.sumOf { it.third } }
        for (name in namen) mengen.getValue(name) += summen[name] ?: 0
    }
    return Verlauf(zeitpunkte, mengen)
}
