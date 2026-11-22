package de.vereinskasse.model

import kotlinx.serialization.Serializable

/** Alle Daten der App. Wird als JSON lokal gespeichert. */
@Serializable
data class AppDaten(
    val vereine: List<Verein> = emptyList(),
    val aktiverVereinId: String? = null,
    /** Name dieses Geräts, erscheint in Abschlüssen (z. B. "Getränke-Kasse"). */
    val geraetename: String = "Kasse 1",
    /** Eindeutige Kennung dieses Geräts (für Abschlüsse beim Zusammenführen). */
    val geraeteId: String = "",
)

@Serializable
data class Verein(
    val id: String,
    val name: String,
    val gruppen: List<Gruppe> = emptyList(),
    /** Reihenfolge der Liste = Reihenfolge der Kacheln innerhalb einer Gruppe. */
    val artikel: List<Artikel> = emptyList(),
    val veranstaltungen: List<Veranstaltung> = emptyList(),
    val aktiveVeranstaltungId: String? = null,
)

@Serializable
data class Gruppe(
    val id: String,
    val name: String,
    /** Farbe als ARGB, z. B. 0xFF1E88E5. */
    val farbe: Long,
)

@Serializable
data class Artikel(
    val id: String,
    val name: String,
    val preisCent: Long,
    val gruppeId: String? = null,
    /** Pfandbetrag, der optional mitgebucht wird (0 = kein Pfand). */
    val pfandCent: Long = 0,
    /** Ausgeblendete Artikel (z. B. ausverkauft) erscheinen nicht an der Kasse. */
    val sichtbar: Boolean = true,
)

@Serializable
data class Veranstaltung(
    val id: String,
    val name: String,
    val erstelltAm: Long,
    /** Wechselgeld in der Kasse zu Beginn. */
    val anfangsbestandCent: Long = 0,
    /** Gezählter Kassenstand am Ende (null = noch nicht gezählt). */
    val istBestandCent: Long? = null,
    val bestellungen: List<Bestellung> = emptyList(),
    /** Importierte Abschlüsse anderer Geräte zum Zusammenführen. */
    val fremdAbschluesse: List<Abschluss> = emptyList(),
)

@Serializable
data class Bestellung(
    val id: String,
    val zeit: Long,
    val positionen: List<Position>,
    /** Vom Kunden gegebener Betrag (null = passend / nicht erfasst). */
    val gegebenCent: Long? = null,
    val storniert: Boolean = false,
)

/**
 * Eine Zeile einer Bestellung (bzw. des Warenkorbs).
 * Betrag der Zeile = menge * (einzelpreisCent + pfandCent).
 * Bei Pfand-Rückgabe ist pfandCent negativ und einzelpreisCent 0.
 */
@Serializable
data class Position(
    val artikelId: String? = null,
    val name: String,
    val menge: Int,
    val einzelpreisCent: Long,
    val pfandCent: Long = 0,
    val helfer: Boolean = false,
    val pfandRueckgabe: Boolean = false,
) {
    val summeCent: Long get() = menge * (einzelpreisCent + pfandCent)

    /** Zwei Zeilen mit gleichem Schlüssel werden im Warenkorb zusammengefasst. */
    fun gleicheArt(andere: Position): Boolean =
        artikelId == andere.artikelId && name == andere.name &&
            einzelpreisCent == andere.einzelpreisCent && pfandCent == andere.pfandCent &&
            helfer == andere.helfer && pfandRueckgabe == andere.pfandRueckgabe
}

/** Zusammenfassung einer Veranstaltung auf einem Gerät – wird zum Zusammenführen geteilt. */
@Serializable
data class Abschluss(
    val id: String,
    val geraet: String,
    val vereinName: String,
    val veranstaltungName: String,
    val erstelltAm: Long,
    val artikel: List<ArtikelSumme>,
    val anzahlBestellungen: Int,
    val anzahlStornos: Int,
    val stornoSummeCent: Long,
    val pfandEinCent: Long,
    /** Ausgezahltes Pfand als positiver Betrag. */
    val pfandAusCent: Long,
    val anfangsbestandCent: Long,
    val istBestandCent: Long? = null,
    /** Ab Version 1.1: alle Bestellungen mit Uhrzeit (für Tagesauswahl und Verlauf). */
    val enthaeltBestellungen: Boolean = false,
    val bestellungen: List<Bestellung> = emptyList(),
) {
    val artikelUmsatzCent: Long get() = artikel.sumOf { it.umsatzCent }
    val kassenEinnahmeCent: Long get() = artikelUmsatzCent + pfandEinCent - pfandAusCent
    val sollBestandCent: Long get() = anfangsbestandCent + kassenEinnahmeCent
}

@Serializable
data class ArtikelSumme(
    val name: String,
    /** Gesamtmenge inkl. Helferbuchungen. */
    val menge: Int,
    val umsatzCent: Long,
    val helferMenge: Int = 0,
)

/** Inhalt einer geteilten Datei (Konfiguration oder Abschluss). */
@Serializable
data class ExportDatei(
    val format: String = FORMAT,
    val version: Int = 1,
    val typ: String,
    val verein: VereinKonfig? = null,
    val abschluss: Abschluss? = null,
) {
    companion object {
        const val FORMAT = "vereinskasse"
        const val TYP_KONFIG = "konfiguration"
        const val TYP_ABSCHLUSS = "abschluss"
    }
}

/** Konfiguration eines Vereins ohne Bestellungen. */
@Serializable
data class VereinKonfig(
    val id: String,
    val name: String,
    val gruppen: List<Gruppe>,
    val artikel: List<Artikel>,
)
