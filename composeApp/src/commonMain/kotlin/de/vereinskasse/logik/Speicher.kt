package de.vereinskasse.logik

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.ImageBitmap
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

/** Eine zu importierende Datei (aus WhatsApp, Dateiauswahl …). */
class ImportDatei(val name: String?, val bytes: ByteArray)

/** Plattformabhängige Funktionen (Teilen, Datei öffnen). */
interface Plattform {
    /** Hinweis zur Datensicherheit, falls die Plattform Daten verlieren kann (z. B. Browser). */
    val speicherHinweis: String? get() = null

    /** Können Dateien (z. B. aus WhatsApp) direkt mit der App geöffnet werden? */
    val kannDateienDirektOeffnen: Boolean get() = true

    /** Text teilen (z. B. per WhatsApp). */
    fun teileText(text: String)

    /** Datei mit Inhalt teilen (z. B. per WhatsApp). */
    fun teileDatei(dateiname: String, inhalt: String, mimeType: String = "application/json")

    /** Bild (z. B. Diagramm) als PNG teilen. */
    fun teileBild(dateiname: String, bild: androidx.compose.ui.graphics.ImageBitmap)

    /** Dateiauswahl öffnen; der Inhalt kommt über den Import-Weg der App zurück. */
    fun dateiOeffnen()

    /** Bild aus der Galerie wählen; liefert es verkleinert (max. 512 px, PNG/JPEG) oder null. */
    fun bildAuswaehlen(ergebnis: (ByteArray?) -> Unit)
}

/** Hält den App-Zustand und speichert jede Änderung im Hintergrund. */
class KassenStore(
    private val speicher: Speicher,
    scope: CoroutineScope,
    val logos: LogoSpeicher = LogoSpeicherImRam(),
) {

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

    // ---------- Logos ----------

    private val logoBilder = mutableStateMapOf<String, ImageBitmap?>()

    /** Logo als Bild (zwischengespeichert). */
    fun logoBild(id: String?): ImageBitmap? {
        if (id == null) return null
        if (id !in logoBilder) {
            logoBilder[id] = try {
                logos.laden(id)?.let { logos.dekodieren(it) }
            } catch (_: Exception) {
                null
            }
        }
        return logoBilder[id]
    }

    fun logoBytes(id: String?): ByteArray? = id?.let {
        try {
            logos.laden(it)
        } catch (_: Exception) {
            null
        }
    }

    /** Speichert ein Logo und gibt seine Kennung zurück (null, wenn es kein lesbares Bild ist). */
    fun logoSpeichern(bytes: ByteArray, id: String = neueId()): String? {
        val bild = try {
            logos.dekodieren(bytes)
        } catch (_: Exception) {
            null
        } ?: return null
        return try {
            logos.speichern(id, bytes)
            logoBilder[id] = bild
            id
        } catch (_: Exception) {
            null
        }
    }

    /** Löscht Logo-Dateien, die kein Verein mehr verwendet. */
    fun logosAufraeumen() {
        val benutzt = daten.vereine.mapNotNull { it.logoId }.toSet()
        try {
            for (id in logos.alle()) if (id !in benutzt) {
                logos.loeschen(id)
                logoBilder.remove(id)
            }
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
