package de.vereinskasse.logik

import de.vereinskasse.model.Abschluss
import de.vereinskasse.model.AppDaten
import de.vereinskasse.model.Artikel
import de.vereinskasse.model.ExportDatei
import de.vereinskasse.model.Gruppe
import de.vereinskasse.model.Verein
import de.vereinskasse.model.Veranstaltung
import de.vereinskasse.model.VeranstaltungKonfig
import de.vereinskasse.model.VereinKonfig
import kotlinx.serialization.json.Json

val JSON = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = false
}

private val JSON_SCHOEN = Json(JSON) { prettyPrint = true }

fun AppDaten.alsJson(): String = JSON.encodeToString(AppDaten.serializer(), this)

fun appDatenAusJson(text: String): AppDaten = JSON.decodeFromString(AppDaten.serializer(), text)

// ---------- Export ----------

fun Verein.konfigExport(): String = JSON_SCHOEN.encodeToString(
    ExportDatei.serializer(),
    ExportDatei(
        typ = ExportDatei.TYP_KONFIG,
        verein = VereinKonfig(
            id, name, gruppen, artikel,
            veranstaltungen = veranstaltungen.map { VeranstaltungKonfig(it.id, it.name, it.erstelltAm, it.anfangsbestandCent) },
            aktiveVeranstaltungId = aktiveVeranstaltungId,
        ),
    ),
)

/** Vollständige Datensicherung aller Daten dieses Geräts. */
fun AppDaten.sicherungExport(zeit: Long): String = JSON.encodeToString(
    ExportDatei.serializer(),
    ExportDatei(typ = ExportDatei.TYP_SICHERUNG, daten = this.copy(letzteSicherung = zeit), erstelltAm = zeit),
)

fun sicherungsDateiname(zeit: Long): String {
    val d = datum(zeit).split('.')
    return "Vereinskasse_Sicherung_${d[2]}-${d[1]}-${d[0]}.json"
}

/** Kennzahlen für die Rückfrage beim Wiederherstellen. */
fun AppDaten.umfangText(): String {
    val veranstaltungen = vereine.sumOf { it.veranstaltungen.size }
    val bestellungen = vereine.sumOf { v -> v.veranstaltungen.sumOf { it.bestellungen.size } }
    return "${vereine.size} ${if (vereine.size == 1) "Verein" else "Vereine"}, " +
        "$veranstaltungen ${if (veranstaltungen == 1) "Veranstaltung" else "Veranstaltungen"}, " +
        "$bestellungen ${if (bestellungen == 1) "Bestellung" else "Bestellungen"}"
}

fun Abschluss.export(): String = JSON_SCHOEN.encodeToString(
    ExportDatei.serializer(),
    ExportDatei(typ = ExportDatei.TYP_ABSCHLUSS, abschluss = this),
)

fun dateiname(name: String, zusatz: String, endung: String = "json"): String {
    val sauber = name.map { if (it.isLetterOrDigit()) it else '_' }.joinToString("").trim('_').ifEmpty { "Verein" }
    return "${sauber}_$zusatz.$endung"
}

// ---------- Import ----------

sealed interface ImportErgebnis {
    data class Konfig(val konfig: VereinKonfig) : ImportErgebnis
    data class AbschlussDatei(val abschluss: Abschluss) : ImportErgebnis
    data class Sicherung(val daten: AppDaten, val erstelltAm: Long?) : ImportErgebnis
    data class Fehler(val meldung: String) : ImportErgebnis
}

fun importLesen(text: String): ImportErgebnis {
    val datei = try {
        JSON.decodeFromString(ExportDatei.serializer(), text.trim())
    } catch (e: Exception) {
        return ImportErgebnis.Fehler("Die Datei ist keine gültige Vereinskasse-Datei.")
    }
    if (datei.format != ExportDatei.FORMAT) {
        return ImportErgebnis.Fehler("Die Datei ist keine Vereinskasse-Datei.")
    }
    return when {
        datei.typ == ExportDatei.TYP_KONFIG && datei.verein != null -> ImportErgebnis.Konfig(datei.verein)
        datei.typ == ExportDatei.TYP_ABSCHLUSS && datei.abschluss != null -> ImportErgebnis.AbschlussDatei(datei.abschluss)
        datei.typ == ExportDatei.TYP_SICHERUNG && datei.daten != null -> ImportErgebnis.Sicherung(datei.daten, datei.erstelltAm)
        else -> ImportErgebnis.Fehler("Unbekannter Dateiinhalt (Typ \"${datei.typ}\").")
    }
}

/** Sucht einen vorhandenen Verein, der zur importierten Konfiguration passt (gleiche ID oder gleicher Name). */
fun AppDaten.passenderVerein(konfig: VereinKonfig): Verein? =
    vereine.firstOrNull { it.id == konfig.id }
        ?: vereine.firstOrNull { it.name.trim().equals(konfig.name.trim(), ignoreCase = true) }

enum class ImportArt { ERSETZEN, ERGAENZEN, NEU }

fun AppDaten.konfigImportieren(konfig: VereinKonfig, art: ImportArt): AppDaten {
    val vorhanden = passenderVerein(konfig)
    return when {
        art == ImportArt.NEU || vorhanden == null -> {
            val idVergeben = vereine.any { it.id == konfig.id }
            val name = if (vereine.any { it.name.equals(konfig.name, ignoreCase = true) }) "${konfig.name} (Import)" else konfig.name
            val neu = Verein(
                id = if (idVergeben) neueId() else konfig.id,
                name = name,
                gruppen = konfig.gruppen,
                artikel = konfig.artikel,
            ).veranstaltungenUebernehmen(konfig)
            copy(vereine = vereine + neu, aktiverVereinId = neu.id)
        }
        art == ImportArt.ERSETZEN -> ersetzeVerein(
            vorhanden.copy(gruppen = konfig.gruppen, artikel = konfig.artikel).veranstaltungenUebernehmen(konfig)
        )
        else -> ersetzeVerein(vorhanden.ergaenzen(konfig).veranstaltungenUebernehmen(konfig))
    }
}

/**
 * Übernimmt Veranstaltungen aus der Konfiguration: vorhandene (gleiche ID oder gleicher Name)
 * bleiben unangetastet, neue werden ohne Bestellungen angelegt. Die in der Datei aktive
 * Veranstaltung wird auch hier aktiv.
 */
fun Verein.veranstaltungenUebernehmen(konfig: VereinKonfig): Verein {
    if (konfig.veranstaltungen.isEmpty()) return this
    val liste = veranstaltungen.toMutableList()
    val zuordnung = mutableMapOf<String, String>()
    for (k in konfig.veranstaltungen) {
        val vorhanden = liste.firstOrNull { it.id == k.id }
            ?: liste.firstOrNull { it.name.trim().equals(k.name.trim(), ignoreCase = true) }
        if (vorhanden != null) {
            zuordnung[k.id] = vorhanden.id
        } else {
            val id = if (liste.any { it.id == k.id }) neueId() else k.id
            liste += Veranstaltung(id, k.name, k.erstelltAm, anfangsbestandCent = k.anfangsbestandCent)
            zuordnung[k.id] = id
        }
    }
    val aktiv = konfig.aktiveVeranstaltungId?.let { zuordnung[it] } ?: aktiveVeranstaltungId
    return copy(veranstaltungen = liste, aktiveVeranstaltungId = aktiv)
}

/** Neue Veranstaltungen, die ein Import anlegen würde. */
fun Verein?.neueVeranstaltungen(konfig: VereinKonfig): List<VeranstaltungKonfig> =
    konfig.veranstaltungen.filter { k ->
        this == null || veranstaltungen.none { it.id == k.id || it.name.trim().equals(k.name.trim(), ignoreCase = true) }
    }

fun AppDaten.ersetzeVerein(verein: Verein): AppDaten =
    copy(vereine = vereine.map { if (it.id == verein.id) verein else it })

/** Ergänzt Gruppen und Artikel nach Namen: vorhandene werden aktualisiert, neue angehängt. */
fun Verein.ergaenzen(konfig: VereinKonfig): Verein {
    val gruppen = gruppen.toMutableList()
    val gruppenZuordnung = mutableMapOf<String, String>() // Import-ID -> lokale ID
    for (g in konfig.gruppen) {
        val index = gruppen.indexOfFirst { it.name.trim().equals(g.name.trim(), ignoreCase = true) }
        if (index >= 0) {
            gruppen[index] = gruppen[index].copy(farbe = g.farbe)
            gruppenZuordnung[g.id] = gruppen[index].id
        } else {
            val id = if (gruppen.any { it.id == g.id }) neueId() else g.id
            gruppen += Gruppe(id, g.name, g.farbe)
            gruppenZuordnung[g.id] = id
        }
    }
    val artikel = artikel.toMutableList()
    for (a in konfig.artikel) {
        val gruppeId = a.gruppeId?.let { gruppenZuordnung[it] }
        val index = artikel.indexOfFirst { it.name.trim().equals(a.name.trim(), ignoreCase = true) }
        if (index >= 0) {
            artikel[index] = artikel[index].copy(preisCent = a.preisCent, pfandCent = a.pfandCent, gruppeId = gruppeId)
        } else {
            val id = if (artikel.any { it.id == a.id }) neueId() else a.id
            artikel += Artikel(id, a.name, a.preisCent, gruppeId, a.pfandCent, a.sichtbar)
        }
    }
    return copy(gruppen = gruppen, artikel = artikel)
}

/** Fügt einen fremden Abschluss der Veranstaltung hinzu (gleiche ID wird ersetzt). */
fun AppDaten.abschlussImportieren(vereinId: String, veranstaltungId: String, abschluss: Abschluss): AppDaten {
    val verein = vereine.firstOrNull { it.id == vereinId } ?: return this
    return ersetzeVerein(
        verein.copy(veranstaltungen = verein.veranstaltungen.map { v ->
            if (v.id != veranstaltungId) v
            else v.copy(fremdAbschluesse = v.fremdAbschluesse.filter { it.id != abschluss.id } + abschluss)
        })
    )
}
