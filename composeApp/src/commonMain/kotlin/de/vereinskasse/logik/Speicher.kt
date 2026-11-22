package de.vereinskasse.logik

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.vereinskasse.model.AppDaten
import de.vereinskasse.model.Veranstaltung
import de.vereinskasse.model.Verein
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/** Plattformabhängiges Lesen/Schreiben der Datendatei. */
interface Speicher {
    fun laden(): String?
    fun speichern(inhalt: String)
}

/** Plattformabhängige Funktionen (Teilen, Datei öffnen). */
interface Plattform {
    /** Text teilen (z. B. per WhatsApp). */
    fun teileText(text: String)

    /** Datei mit Inhalt teilen (z. B. per WhatsApp). */
    fun teileDatei(dateiname: String, inhalt: String)

    /** Bild (z. B. Diagramm) als PNG teilen. */
    fun teileBild(dateiname: String, bild: androidx.compose.ui.graphics.ImageBitmap)

    /** Dateiauswahl öffnen; der Inhalt kommt über den Import-Weg der App zurück. */
    fun dateiOeffnen()
}

/** Hält den App-Zustand und speichert jede Änderung im Hintergrund. */
class KassenStore(private val speicher: Speicher, scope: CoroutineScope) {

    private val speicherKanal = Channel<AppDaten>(Channel.CONFLATED)

    var ladeFehler: String? by mutableStateOf(null)
        private set

    var daten: AppDaten by mutableStateOf(laden())
        private set

    init {
        if (daten.geraeteId.isEmpty()) {
            daten = daten.copy(geraeteId = neueId())
            speicherKanal.trySend(daten)
        }
        scope.launch(Dispatchers.Default) {
            for (stand in speicherKanal) {
                try {
                    speicher.speichern(stand.alsJson())
                } catch (e: Exception) {
                    ladeFehler = "Speichern fehlgeschlagen: ${e.message}"
                }
            }
        }
    }

    private fun laden(): AppDaten {
        val text = try {
            speicher.laden()
        } catch (e: Exception) {
            null
        } ?: return AppDaten()
        return try {
            appDatenAusJson(text)
        } catch (e: Exception) {
            ladeFehler = "Gespeicherte Daten konnten nicht gelesen werden."
            AppDaten()
        }
    }

    fun aendern(aenderung: (AppDaten) -> AppDaten) {
        val neu = aenderung(daten)
        if (neu != daten) {
            daten = neu
            speicherKanal.trySend(neu)
        }
    }

    /** Sofort speichern (z. B. wenn die App in den Hintergrund geht). */
    fun sofortSpeichern() {
        try {
            speicher.speichern(daten.alsJson())
        } catch (_: Exception) {
        }
    }

    fun fehlerGelesen() {
        ladeFehler = null
    }

    // ---------- Bequeme Zugriffe ----------

    val aktiverVerein: Verein?
        get() = daten.vereine.firstOrNull { it.id == daten.aktiverVereinId }

    val aktiveVeranstaltung: Veranstaltung?
        get() = aktiverVerein?.let { v -> v.veranstaltungen.firstOrNull { it.id == v.aktiveVeranstaltungId } }

    fun verein(id: String): Verein? = daten.vereine.firstOrNull { it.id == id }

    fun vereinAendern(id: String, aenderung: (Verein) -> Verein) = aendern { d ->
        d.copy(vereine = d.vereine.map { if (it.id == id) aenderung(it) else it })
    }

    fun veranstaltungAendern(vereinId: String, veranstaltungId: String, aenderung: (Veranstaltung) -> Veranstaltung) =
        vereinAendern(vereinId) { v ->
            v.copy(veranstaltungen = v.veranstaltungen.map { if (it.id == veranstaltungId) aenderung(it) else it })
        }
}

/** Speicher im Arbeitsspeicher (für Tests und Vorschauen). */
class SpeicherImRam(private var inhalt: String? = null) : Speicher {
    override fun laden() = inhalt
    override fun speichern(inhalt: String) {
        this.inhalt = inhalt
    }
}
