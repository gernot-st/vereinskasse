package de.vereinskasse.logik

import de.vereinskasse.model.Abschluss
import de.vereinskasse.model.AppDaten
import de.vereinskasse.model.Artikel
import de.vereinskasse.model.ExportDatei
import de.vereinskasse.model.Gruppe
import de.vereinskasse.model.Verein
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
    ExportDatei(typ = ExportDatei.TYP_KONFIG, verein = VereinKonfig(id, name, gruppen, artikel)),
)

fun Abschluss.export(): String = JSON_SCHOEN.encodeToString(
    ExportDatei.serializer(),
    ExportDatei(typ = ExportDatei.TYP_ABSCHLUSS, abschluss = this),
)

fun dateiname(name: String, endung: String): String {
    val sauber = name.map { if (it.isLetterOrDigit()) it else '_' }.joinToString("").trim('_').ifEmpty { "Verein" }
    return "${sauber}_$endung.json"
}

// ---------- Import ----------

sealed interface ImportErgebnis {
    data class Konfig(val konfig: VereinKonfig) : ImportErgebnis
    data class AbschlussDatei(val abschluss: Abschluss) : ImportErgebnis
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
            )
            copy(vereine = vereine + neu, aktiverVereinId = neu.id)
        }
        art == ImportArt.ERSETZEN -> ersetzeVerein(vorhanden.copy(gruppen = konfig.gruppen, artikel = konfig.artikel))
        else -> ersetzeVerein(vorhanden.ergaenzen(konfig))
    }
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
