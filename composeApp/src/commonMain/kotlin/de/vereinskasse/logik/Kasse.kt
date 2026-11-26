package de.vereinskasse.logik

import de.vereinskasse.model.Abschluss
import de.vereinskasse.model.Artikel
import de.vereinskasse.model.ArtikelSumme
import de.vereinskasse.model.Bestellung
import de.vereinskasse.model.Position
import de.vereinskasse.model.Veranstaltung
import de.vereinskasse.model.Verein
import kotlinx.datetime.LocalDate

// ---------- Warenkorb ----------

fun List<Position>.hinzufuegen(neu: Position): List<Position> {
    val index = indexOfFirst { it.gleicheArt(neu) }
    return if (index >= 0) {
        toMutableList().also { it[index] = it[index].copy(menge = it[index].menge + neu.menge) }
    } else {
        this + neu
    }
}

fun List<Position>.artikelHinzufuegen(artikel: Artikel, helfer: Boolean, mitPfand: Boolean): List<Position> =
    hinzufuegen(
        Position(
            artikelId = artikel.id,
            name = artikel.name,
            menge = 1,
            einzelpreisCent = if (helfer) 0 else artikel.preisCent,
            pfandCent = if (mitPfand) artikel.pfandCent else 0,
            helfer = helfer,
        )
    )

fun List<Position>.pfandRueckgabeHinzufuegen(betragCent: Long): List<Position> =
    hinzufuegen(
        Position(
            name = "Pfand zurück",
            menge = 1,
            einzelpreisCent = 0,
            pfandCent = -betragCent,
            pfandRueckgabe = true,
        )
    )

/** Ändert die Menge einer Zeile; bei 0 wird sie entfernt. */
fun List<Position>.mengeAendern(index: Int, delta: Int): List<Position> {
    if (index !in indices) return this
    val neueMenge = this[index].menge + delta
    return if (neueMenge <= 0) filterIndexed { i, _ -> i != index }
    else toMutableList().also { it[index] = it[index].copy(menge = neueMenge) }
}

/** Schaltet das Pfand einer Zeile ein/aus und fasst ggf. gleiche Zeilen zusammen. */
fun List<Position>.pfandUmschalten(index: Int, artikel: Artikel?): List<Position> {
    if (index !in indices || artikel == null || artikel.pfandCent == 0L) return this
    val zeile = this[index]
    val umgeschaltet = zeile.copy(pfandCent = if (zeile.pfandCent == 0L) artikel.pfandCent else 0)
    val ohne = filterIndexed { i, _ -> i != index }
    val ziel = ohne.indexOfFirst { it.gleicheArt(umgeschaltet) }
    return if (ziel >= 0) {
        ohne.toMutableList().also { it[ziel] = it[ziel].copy(menge = it[ziel].menge + umgeschaltet.menge) }
    } else {
        ohne.toMutableList().also { it.add(index, umgeschaltet) }
    }
}

fun List<Position>.summeCent(): Long = sumOf { it.summeCent }

fun List<Position>.anzahlArtikel(): Int = filter { !it.pfandRueckgabe }.sumOf { it.menge }

// ---------- Artikel-Reihenfolge ----------

/** Artikel in Kassen-Reihenfolge: nach Gruppen-Reihenfolge, dann Listen-Reihenfolge. Ohne Gruppe zuletzt. */
fun Verein.artikelSortiert(): List<Artikel> {
    val gruppenIndex = gruppen.mapIndexed { i, g -> g.id to i }.toMap()
    return artikel.withIndex()
        .sortedWith(compareBy({ gruppenIndex[it.value.gruppeId] ?: Int.MAX_VALUE }, { it.index }))
        .map { it.value }
}

/**
 * Verschiebt innerhalb einer Teilmenge (z. B. Artikel einer Gruppe) ein Element.
 * Die übrigen Elemente der Gesamtliste behalten ihre Plätze.
 */
fun <T> List<T>.teilmengeVerschieben(gehoertDazu: (T) -> Boolean, von: Int, nach: Int): List<T> {
    val plaetze = indices.filter { gehoertDazu(this[it]) }
    if (von !in plaetze.indices || nach !in plaetze.indices) return this
    val teil = plaetze.map { this[it] }.toMutableList()
    teil.add(nach, teil.removeAt(von))
    val ergebnis = toMutableList()
    plaetze.forEachIndexed { i, platz -> ergebnis[platz] = teil[i] }
    return ergebnis
}

// ---------- Auswertung ----------

/** Entfernt alle Umsätze einer Veranstaltung; die Veranstaltung selbst bleibt erhalten. */
fun Veranstaltung.umsaetzeGeloescht(): Veranstaltung =
    copy(bestellungen = emptyList(), fremdAbschluesse = emptyList(), istBestandCent = null, anfangsbestandCent = 0)

fun Veranstaltung.letzteBestellung(): Bestellung? = bestellungen.maxByOrNull { it.zeit }

fun Veranstaltung.abschlussBerechnen(abschlussId: String, geraet: String, vereinName: String, zeit: Long): Abschluss =
    bestellungen.abschluss(abschlussId, geraet, vereinName, name, zeit, anfangsbestandCent, istBestandCent)

/** Berechnet einen Abschluss aus einer Liste von Bestellungen (die Bestellungen werden mitgeliefert). */
fun List<Bestellung>.abschluss(
    abschlussId: String,
    geraet: String,
    vereinName: String,
    veranstaltungName: String,
    zeit: Long,
    anfangsbestandCent: Long = 0,
    istBestandCent: Long? = null,
): Abschluss {
    val gueltig = filter { !it.storniert }
    val storniert = filter { it.storniert }
    val positionen = gueltig.flatMap { it.positionen }

    val artikel = positionen.filter { !it.pfandRueckgabe }
        .groupBy { it.name }
        .map { (name, zeilen) ->
            ArtikelSumme(
                name = name,
                menge = zeilen.sumOf { it.menge },
                umsatzCent = zeilen.sumOf { it.menge * it.einzelpreisCent },
                helferMenge = zeilen.filter { it.helfer }.sumOf { it.menge },
            )
        }

    return Abschluss(
        id = abschlussId,
        geraet = geraet,
        vereinName = vereinName,
        veranstaltungName = veranstaltungName,
        erstelltAm = zeit,
        artikel = artikel,
        anzahlBestellungen = gueltig.size,
        anzahlStornos = storniert.size,
        stornoSummeCent = storniert.sumOf { b -> b.positionen.summeCent() },
        pfandEinCent = positionen.filter { it.pfandCent > 0 }.sumOf { it.menge * it.pfandCent },
        pfandAusCent = -positionen.filter { it.pfandCent < 0 }.sumOf { it.menge * it.pfandCent },
        anfangsbestandCent = anfangsbestandCent,
        istBestandCent = istBestandCent,
        enthaeltBestellungen = true,
        bestellungen = this,
    )
}

/** Abschluss eines anderen Geräts auf einen Festtag einschränken (null = geht nicht, alte Version ohne Uhrzeiten). */
fun Abschluss.amTag(tag: LocalDate?): Abschluss? = when {
    tag == null -> this
    !enthaeltBestellungen -> null
    else -> bestellungen.amTag(tag).abschluss(id, geraet, vereinName, veranstaltungName, erstelltAm)
}

/** Fasst Abschlüsse mehrerer Geräte zu einem Gesamtergebnis zusammen. */
fun List<Abschluss>.zusammenfuehren(geraet: String, vereinName: String, veranstaltungName: String, zeit: Long): Abschluss {
    val artikelReihenfolge = LinkedHashMap<String, ArtikelSumme>()
    for (abschluss in this) {
        for (a in abschluss.artikel) {
            val bisher = artikelReihenfolge[a.name]
            artikelReihenfolge[a.name] = if (bisher == null) a else bisher.copy(
                menge = bisher.menge + a.menge,
                umsatzCent = bisher.umsatzCent + a.umsatzCent,
                helferMenge = bisher.helferMenge + a.helferMenge,
            )
        }
    }
    val alleGezaehlt = all { it.istBestandCent != null }
    return Abschluss(
        id = "gesamt",
        geraet = geraet,
        vereinName = vereinName,
        veranstaltungName = veranstaltungName,
        erstelltAm = zeit,
        artikel = artikelReihenfolge.values.toList(),
        anzahlBestellungen = sumOf { it.anzahlBestellungen },
        anzahlStornos = sumOf { it.anzahlStornos },
        stornoSummeCent = sumOf { it.stornoSummeCent },
        pfandEinCent = sumOf { it.pfandEinCent },
        pfandAusCent = sumOf { it.pfandAusCent },
        anfangsbestandCent = sumOf { it.anfangsbestandCent },
        istBestandCent = if (alleGezaehlt && isNotEmpty()) sumOf { it.istBestandCent ?: 0 } else null,
    )
}

/** Artikel nach Reihenfolge des Vereins sortieren; unbekannte (gelöschte/fremde) ans Ende. */
fun List<ArtikelSumme>.sortiertNach(verein: Verein?): List<ArtikelSumme> {
    if (verein == null) return this
    val reihenfolge = verein.artikelSortiert().mapIndexed { i, a -> a.name to i }.toMap()
    return withIndex().sortedWith(compareBy({ reihenfolge[it.value.name] ?: Int.MAX_VALUE }, { it.index }))
        .map { it.value }
}

/** Lesbarer Text für WhatsApp. */
fun Abschluss.alsText(titel: String, mitKassenstand: Boolean, verein: Verein?): String = buildString {
    appendLine("*$titel*")
    appendLine("$vereinName – $veranstaltungName")
    appendLine("Stand: ${datumZeit(erstelltAm)}")
    appendLine()
    appendLine("*Artikel*")
    for (a in artikel.sortiertNach(verein)) {
        val helfer = if (a.helferMenge > 0) " (davon ${a.helferMenge} Helfer)" else ""
        appendLine("${a.menge}× ${a.name}$helfer: ${geld(a.umsatzCent)}")
    }
    appendLine()
    appendLine("Umsatz Artikel: ${geld(artikelUmsatzCent)}")
    if (pfandEinCent != 0L || pfandAusCent != 0L) {
        appendLine("Pfand eingenommen: ${geld(pfandEinCent)}")
        appendLine("Pfand ausgezahlt: ${geld(pfandAusCent)}")
    }
    appendLine("*Einnahmen gesamt: ${geld(kassenEinnahmeCent)}*")
    appendLine("Bestellungen: $anzahlBestellungen")
    if (anzahlStornos > 0) appendLine("Stornos: $anzahlStornos (${geld(stornoSummeCent)})")
    val helferGesamt = artikel.sumOf { it.helferMenge }
    if (helferGesamt > 0) appendLine("Helferbuchungen: $helferGesamt")
    if (mitKassenstand) {
        appendLine()
        appendLine("*Kassenstand*")
        appendLine("Anfangsbestand: ${geld(anfangsbestandCent)}")
        appendLine("Soll: ${geld(sollBestandCent)}")
        istBestandCent?.let {
            appendLine("Ist (gezählt): ${geld(it)}")
            appendLine("Differenz: ${geld(it - sollBestandCent)}")
        }
    }
}.trimEnd()
