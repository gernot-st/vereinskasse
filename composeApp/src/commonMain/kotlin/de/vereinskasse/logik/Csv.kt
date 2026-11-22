package de.vereinskasse.logik

import de.vereinskasse.model.Artikel
import de.vereinskasse.model.Gruppe
import de.vereinskasse.model.Verein

// ---------- Kodierung ----------

/** Sonderzeichen 0x80–0x9F in Windows-1252 (Excel unter Windows speichert "CSV" oft so). */
private val CP1252 = charArrayOf(
    '€', '�', '‚', 'ƒ', '„', '…', '†', '‡', 'ˆ', '‰', 'Š', '‹', 'Œ', '�', 'Ž', '�',
    '�', '‘', '’', '“', '”', '•', '–', '—', '˜', '™', 'š', '›', 'œ', '�', 'ž', 'Ÿ',
)

/** Liest Text als UTF-8 (mit oder ohne BOM); ist das ungültig, als Windows-1252. */
fun textDekodieren(bytes: ByteArray): String {
    val start = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) 3 else 0
    return try {
        bytes.decodeToString(start, bytes.size, throwOnInvalidSequence = true)
    } catch (e: CharacterCodingException) {
        buildString(bytes.size) {
            for (b in bytes) {
                val c = b.toInt() and 0xFF
                append(if (c in 0x80..0x9F) CP1252[c - 0x80] else c.toChar())
            }
        }
    }.removePrefix("﻿")
}

// ---------- Farben ----------

private val FARB_NAMEN: List<Pair<List<String>, Long>> = listOf(
    listOf("blau") to GRUPPEN_FARBEN[0],
    listOf("rot") to GRUPPEN_FARBEN[1],
    listOf("grün", "gruen") to GRUPPEN_FARBEN[2],
    listOf("orange") to GRUPPEN_FARBEN[3],
    listOf("lila", "violett") to GRUPPEN_FARBEN[4],
    listOf("türkis", "tuerkis") to GRUPPEN_FARBEN[5],
    listOf("gelb") to GRUPPEN_FARBEN[6],
    listOf("braun") to GRUPPEN_FARBEN[7],
    listOf("pink", "rosa") to GRUPPEN_FARBEN[8],
    listOf("grau", "blaugrau") to GRUPPEN_FARBEN[9],
    listOf("indigo", "dunkelblau") to GRUPPEN_FARBEN[10],
    listOf("hellgrün", "hellgruen") to GRUPPEN_FARBEN[11],
)

/** Farbname der Palette oder "#RRGGBB". */
fun farbeAlsText(farbe: Long): String =
    FARB_NAMEN.firstOrNull { it.second == farbe }?.first?.first()
        ?: "#" + (farbe and 0xFFFFFF).toString(16).uppercase().padStart(6, '0')

/** Liest "blau", "#1E88E5" oder "1E88E5". null = unbekannt. */
fun farbeLesen(text: String): Long? {
    val t = text.trim().lowercase()
    FARB_NAMEN.firstOrNull { t in it.first }?.let { return it.second }
    val hex = t.removePrefix("#")
    if (hex.length == 6 && hex.all { it.isDigit() || it in 'a'..'f' }) return 0xFF000000 or hex.toLong(16)
    return null
}

// ---------- Export ----------

private const val TRENNER = ';'

private fun feld(text: String): String =
    if (text.any { it == TRENNER || it == '"' || it == '\n' || it == '\r' } || text != text.trim()) {
        "\"" + text.replace("\"", "\"\"") + "\""
    } else text

private fun zeile(vararg felder: String) = felder.joinToString(TRENNER.toString()) { feld(it) } + "\r\n"

private val KOPF = arrayOf("Gruppe", "Artikel", "Preis", "Pfand", "Sichtbar", "Farbe")

/**
 * CSV für Excel (deutsch): Strichpunkt, Dezimalkomma, UTF-8 mit BOM, Windows-Zeilenenden.
 * Reihenfolge wie an der Kasse. Gruppen ohne Artikel erscheinen als Zeile ohne Artikelname.
 */
fun Verein.alsCsv(): String = buildString {
    append('﻿')
    append(zeile(*KOPF))
    val gruppenNachId = gruppen.associateBy { it.id }
    for (a in artikelSortiert()) {
        val g = gruppenNachId[a.gruppeId]
        append(
            zeile(
                g?.name ?: "",
                a.name,
                geldEingabe(a.preisCent),
                geldEingabe(a.pfandCent, leerBeiNull = true),
                if (a.sichtbar) "ja" else "nein",
                g?.let { farbeAlsText(it.farbe) } ?: "",
            )
        )
    }
    for (g in gruppen.filter { g -> artikel.none { it.gruppeId == g.id } }) {
        append(zeile(g.name, "", "", "", "", farbeAlsText(g.farbe)))
    }
}

fun csvVorlage(): String = buildString {
    append('﻿')
    append(zeile(*KOPF))
    append(zeile("Getränke", "Bier 0,5", "3,50", "2,00", "ja", "blau"))
    append(zeile("Getränke", "Wasser", "2,00", "2,00", "ja", "blau"))
    append(zeile("Essen", "Bratwurst", "4,00", "", "ja", "orange"))
}

// ---------- Import ----------

/** Ergebnis des CSV-Lesens: Gruppen und Artikel (in Datei-Reihenfolge) plus Meldungen. */
data class CsvErgebnis(
    val gruppen: List<Gruppe>,
    val artikel: List<Artikel>,
    /** Zeilen, die übersprungen wurden. */
    val fehler: List<String>,
    /** Hinweise (Zeile wurde trotzdem übernommen). */
    val hinweise: List<String>,
    /** Namen der Gruppen, deren Farbe in der Datei angegeben war. */
    val gruppenMitFarbe: Set<String>,
    val hatSichtbarSpalte: Boolean,
)

class CsvFehler(meldung: String) : Exception(meldung)

/** Zerlegt CSV-Text in Zeilen und Felder (Anführungszeichen nach RFC 4180). */
fun csvZerlegen(text: String, trenner: Char): List<List<String>> {
    val zeilen = mutableListOf<List<String>>()
    var felder = mutableListOf<String>()
    val feld = StringBuilder()
    var inAnfuehrung = false
    var i = 0
    while (i < text.length) {
        val c = text[i]
        if (inAnfuehrung) {
            if (c == '"') {
                if (i + 1 < text.length && text[i + 1] == '"') {
                    feld.append('"'); i++
                } else inAnfuehrung = false
            } else feld.append(c)
        } else when (c) {
            '"' -> inAnfuehrung = true
            trenner -> { felder.add(feld.toString()); feld.clear() }
            '\r' -> {}
            '\n' -> { felder.add(feld.toString()); feld.clear(); zeilen.add(felder); felder = mutableListOf() }
            else -> feld.append(c)
        }
        i++
    }
    if (feld.isNotEmpty() || felder.isNotEmpty()) {
        felder.add(feld.toString()); zeilen.add(felder)
    }
    return zeilen
}

/** Trennzeichen anhand der Kopfzeile erkennen (Strichpunkt, Komma oder Tab). */
fun trennerErkennen(text: String): Char {
    val kopf = text.lineSequence().firstOrNull { it.isNotBlank() } ?: return TRENNER
    var inAnfuehrung = false
    val anzahl = mutableMapOf(';' to 0, ',' to 0, '\t' to 0)
    for (c in kopf) {
        if (c == '"') inAnfuehrung = !inAnfuehrung
        else if (!inAnfuehrung && c in anzahl) anzahl[c] = anzahl.getValue(c) + 1
    }
    return anzahl.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.key ?: TRENNER
}

private val SPALTEN = mapOf(
    "gruppe" to listOf("gruppe", "artikelgruppe", "kategorie", "warengruppe"),
    "artikel" to listOf("artikel", "name", "bezeichnung", "produkt", "artikelname"),
    "preis" to listOf("preis", "vk", "verkaufspreis", "preis (€)", "preis €", "preis in €", "preis eur"),
    "pfand" to listOf("pfand", "pfand (€)", "pfand €", "pfand in €"),
    "sichtbar" to listOf("sichtbar", "aktiv", "anzeigen"),
    "farbe" to listOf("farbe", "gruppenfarbe"),
)

private val JA = setOf("ja", "j", "yes", "y", "1", "true", "wahr", "x")
private val NEIN = setOf("nein", "n", "no", "0", "false", "falsch", "")

fun csvLesen(text: String): CsvErgebnis {
    val trenner = trennerErkennen(text)
    val zeilen = csvZerlegen(text, trenner)
    val kopfIndex = zeilen.indexOfFirst { z -> z.any { it.isNotBlank() } }
    if (kopfIndex < 0) throw CsvFehler("Die Datei ist leer.")
    val kopf = zeilen[kopfIndex].map { it.trim().removePrefix("﻿").lowercase() }
    val spalte = SPALTEN.mapValues { (_, namen) -> kopf.indexOfFirst { it in namen } }.filterValues { it >= 0 }
    if ("artikel" !in spalte || "preis" !in spalte) {
        throw CsvFehler(
            "In der ersten Zeile fehlen die Spaltenüberschriften „Artikel“ und „Preis“. " +
                "Erwartet wird z. B.: Gruppe;Artikel;Preis;Pfand;Sichtbar;Farbe"
        )
    }

    val gruppen = mutableListOf<Gruppe>()
    val gruppenMitFarbe = mutableSetOf<String>()
    val artikel = mutableListOf<Artikel>()
    val fehler = mutableListOf<String>()
    val hinweise = mutableListOf<String>()

    fun gruppeFuer(name: String, farbText: String, nr: Int): Gruppe {
        val farbe = if (farbText.isBlank()) null else farbeLesen(farbText).also {
            if (it == null) hinweise += "Zeile $nr: Farbe „$farbText“ unbekannt – es wird eine automatische Farbe verwendet."
        }
        val vorhanden = gruppen.indexOfFirst { it.name.equals(name, ignoreCase = true) }
        if (vorhanden >= 0) {
            if (farbe != null && name.lowercase() !in gruppenMitFarbe) {
                gruppen[vorhanden] = gruppen[vorhanden].copy(farbe = farbe)
                gruppenMitFarbe += name.lowercase()
            }
            return gruppen[vorhanden]
        }
        val auto = GRUPPEN_FARBEN.firstOrNull { f -> gruppen.none { it.farbe == f } } ?: GRUPPEN_FARBEN[gruppen.size % GRUPPEN_FARBEN.size]
        if (farbe != null) gruppenMitFarbe += name.lowercase()
        return Gruppe(neueId(), name, farbe ?: auto).also { gruppen += it }
    }

    for (z in (kopfIndex + 1) until zeilen.size) {
        val werte = zeilen[z]
        val nr = z + 1
        fun wert(name: String) = spalte[name]?.let { werte.getOrNull(it) }?.trim() ?: ""
        if (werte.all { it.isBlank() }) continue

        val gruppenName = wert("gruppe")
        val name = wert("artikel")
        val preisText = wert("preis")

        if (name.isEmpty()) {
            if (gruppenName.isNotEmpty() && preisText.isEmpty()) {
                gruppeFuer(gruppenName, wert("farbe"), nr) // Gruppe ohne Artikel
            } else {
                fehler += "Zeile $nr: Artikelname fehlt."
            }
            continue
        }
        val preis = parseGeld(preisText)
        if (preisText.isEmpty() || preis == null || preis < 0) {
            fehler += "Zeile $nr ($name): Preis „$preisText“ ist ungültig."
            continue
        }
        val pfandText = wert("pfand")
        val pfand = if (pfandText.isEmpty()) 0 else parseGeld(pfandText)
        if (pfand == null || pfand < 0) {
            fehler += "Zeile $nr ($name): Pfand „$pfandText“ ist ungültig."
            continue
        }
        val sichtbarText = wert("sichtbar").lowercase()
        val sichtbar = when (sichtbarText) {
            in JA -> true
            in NEIN -> sichtbarText.isEmpty() // leer = sichtbar
            else -> {
                hinweise += "Zeile $nr ($name): Sichtbar „$sichtbarText“ nicht erkannt (ja/nein) – Artikel wird angezeigt."
                true
            }
        }
        if (artikel.any { it.name.equals(name, ignoreCase = true) }) {
            fehler += "Zeile $nr: Artikel „$name“ kommt doppelt vor – übersprungen."
            continue
        }
        val gruppe = if (gruppenName.isEmpty()) null else gruppeFuer(gruppenName, wert("farbe"), nr)
        artikel += Artikel(neueId(), name, preis, gruppe?.id, pfand, sichtbar)
    }

    if (artikel.isEmpty() && gruppen.isEmpty()) {
        throw CsvFehler(if (fehler.isEmpty()) "Die Datei enthält keine Artikel." else "Keine gültigen Artikel gefunden.\n" + fehler.take(5).joinToString("\n"))
    }
    return CsvErgebnis(gruppen, artikel, fehler, hinweise, gruppenMitFarbe, "sichtbar" in spalte)
}

/**
 * Übernimmt eine CSV in einen Verein.
 * Ersetzen: Gruppen und Artikel werden ersetzt (gleichnamige behalten ihre interne ID).
 * Ergänzen: gleichnamige werden aktualisiert, neue angehängt; Farben/Sichtbarkeit nur, wenn in der Datei angegeben.
 */
fun Verein.csvUebernehmen(csv: CsvErgebnis, ersetzen: Boolean): Verein {
    if (ersetzen) {
        val gruppenIds = csv.gruppen.associate { neu ->
            neu.id to (gruppen.firstOrNull { it.name.equals(neu.name, ignoreCase = true) }?.id ?: neu.id)
        }
        return copy(
            gruppen = csv.gruppen.map { it.copy(id = gruppenIds.getValue(it.id)) },
            artikel = csv.artikel.map { neu ->
                neu.copy(
                    id = artikel.firstOrNull { it.name.equals(neu.name, ignoreCase = true) }?.id ?: neu.id,
                    gruppeId = neu.gruppeId?.let { gruppenIds[it] },
                )
            },
        )
    }
    val gruppenNeu = gruppen.toMutableList()
    val zuordnung = mutableMapOf<String, String>()
    for (g in csv.gruppen) {
        val i = gruppenNeu.indexOfFirst { it.name.equals(g.name, ignoreCase = true) }
        if (i >= 0) {
            if (g.name.lowercase() in csv.gruppenMitFarbe) gruppenNeu[i] = gruppenNeu[i].copy(farbe = g.farbe)
            zuordnung[g.id] = gruppenNeu[i].id
        } else {
            val farbe = if (g.name.lowercase() in csv.gruppenMitFarbe) g.farbe
            else GRUPPEN_FARBEN.firstOrNull { f -> gruppenNeu.none { it.farbe == f } } ?: g.farbe
            gruppenNeu += g.copy(farbe = farbe)
            zuordnung[g.id] = g.id
        }
    }
    val artikelNeu = artikel.toMutableList()
    for (a in csv.artikel) {
        val gruppeId = a.gruppeId?.let { zuordnung[it] }
        val i = artikelNeu.indexOfFirst { it.name.equals(a.name, ignoreCase = true) }
        if (i >= 0) {
            artikelNeu[i] = artikelNeu[i].copy(
                preisCent = a.preisCent,
                pfandCent = a.pfandCent,
                gruppeId = gruppeId,
                sichtbar = if (csv.hatSichtbarSpalte) a.sichtbar else artikelNeu[i].sichtbar,
            )
        } else {
            artikelNeu += a.copy(gruppeId = gruppeId)
        }
    }
    return copy(gruppen = gruppenNeu, artikel = artikelNeu)
}

/** Vereinsname aus einem Dateinamen wie "TSV_Musterdorf_Artikel.csv". */
fun vereinsnameAusDatei(dateiname: String?): String =
    (dateiname ?: "").substringAfterLast('/').substringBeforeLast('.')
        .removeSuffix("_Artikel").removeSuffix("_Konfiguration")
        .replace('_', ' ').trim().ifEmpty { "Neuer Verein" }
