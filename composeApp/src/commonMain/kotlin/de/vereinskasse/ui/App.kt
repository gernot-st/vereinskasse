package de.vereinskasse.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.vereinskasse.logik.ImportArt
import de.vereinskasse.logik.ImportDatei
import de.vereinskasse.logik.ImportErgebnis
import de.vereinskasse.logik.textDekodieren
import de.vereinskasse.logik.neueVeranstaltungen
import de.vereinskasse.logik.umfangText
import de.vereinskasse.logik.datumZeit
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Checkbox
import androidx.compose.ui.Alignment
import de.vereinskasse.logik.KassenStore
import de.vereinskasse.logik.Plattform
import de.vereinskasse.logik.abschlussImportieren
import de.vereinskasse.logik.geld
import de.vereinskasse.logik.importLesen
import de.vereinskasse.logik.konfigImportieren
import de.vereinskasse.logik.passenderVerein
import de.vereinskasse.model.Abschluss
import de.vereinskasse.model.VereinKonfig

sealed interface Seite {
    data object Kasse : Seite
    data object Vereine : Seite
    data class VereinBearbeiten(val vereinId: String) : Seite
    data object Auswertung : Seite
}

/** Plattformabhängiger Zurück-Knopf (Android). */
typealias ZurueckHandler = @Composable (aktiv: Boolean, onZurueck: () -> Unit) -> Unit

@Composable
fun App(
    store: KassenStore,
    plattform: Plattform,
    importDatei: ImportDatei?,
    importErledigt: () -> Unit,
    zurueckHandler: ZurueckHandler = { _, _ -> },
    startSeiten: List<Seite>? = null,
    kassenZustand: KassenZustand = remember { KassenZustand() },
) {
    var stapel by remember {
        mutableStateOf(startSeiten ?: if (store.aktiverVerein == null) listOf(Seite.Vereine) else listOf(Seite.Kasse))
    }
    val oeffne: (Seite) -> Unit = { stapel = stapel + it }
    val zurueck: () -> Unit = { if (stapel.size > 1) stapel = stapel.dropLast(1) }
    val zurKasse: () -> Unit = { stapel = listOf(Seite.Kasse) }

    VereinskasseTheme {
        zurueckHandler(stapel.size > 1, zurueck)

        when (val seite = stapel.last()) {
            Seite.Kasse -> KasseSeite(store, kassenZustand, oeffne)
            Seite.Vereine -> VereineSeite(
                store, plattform, oeffne,
                zurueck = if (stapel.size > 1) zurueck else null,
                zurKasse = zurKasse,
            )
            is Seite.VereinBearbeiten -> VereinSeite(store, plattform, seite.vereinId, zurueck = {
                // Vom ersten Einrichten direkt zur Kasse, sonst eine Seite zurück
                if (stapel.size > 1) zurueck() else zurKasse()
            })
            Seite.Auswertung -> AuswertungSeite(store, plattform, zurueck)
        }

        if (importDatei != null) {
            val zielVerein = (stapel.last() as? Seite.VereinBearbeiten)?.vereinId ?: store.daten.aktiverVereinId
            ImportDialog(store, importDatei, zielVerein, importErledigt, nachKonfigImport = {
                if (stapel.last() == Seite.Vereine && stapel.size == 1) zurKasse()
            }, nachWiederherstellen = {
                stapel = if (store.aktiverVerein == null) listOf(Seite.Vereine) else listOf(Seite.Kasse)
            })
        }

        store.ladeFehler?.let { fehler ->
            HinweisDialog("Hinweis", fehler) { store.fehlerGelesen() }
        }
    }
}

@Composable
private fun ImportDialog(
    store: KassenStore,
    datei: ImportDatei,
    zielVereinId: String?,
    fertig: () -> Unit,
    nachKonfigImport: () -> Unit,
    nachWiederherstellen: () -> Unit,
) {
    val text = remember(datei) { textDekodieren(datei.bytes) }
    val istJson = text.trimStart().startsWith("{") || datei.name?.endsWith(".json", ignoreCase = true) == true
    if (!istJson) {
        CsvImportDialog(store, text, datei.name, zielVereinId, fertig, nachKonfigImport)
        return
    }
    val ergebnis = remember(text) { importLesen(text) }
    when (ergebnis) {
        is ImportErgebnis.Fehler -> HinweisDialog("Import nicht möglich", ergebnis.meldung, fertig)
        is ImportErgebnis.Konfig -> KonfigImportDialog(store, ergebnis.konfig, fertig = {
            fertig()
        }, importiert = nachKonfigImport)
        is ImportErgebnis.AbschlussDatei -> AbschlussImportDialog(store, ergebnis.abschluss, fertig)
        is ImportErgebnis.Sicherung -> WiederherstellenDialog(store, ergebnis, fertig, nachWiederherstellen)
    }
}

@Composable
private fun WiederherstellenDialog(
    store: KassenStore,
    sicherung: ImportErgebnis.Sicherung,
    fertig: () -> Unit,
    wiederhergestellt: () -> Unit,
) {
    var bestaetigt by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = fertig,
        title = { Text("Datensicherung wiederherstellen?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Sicherung" + (sicherung.erstelltAm?.let { " vom ${datumZeit(it)}" } ?: "") + ":\n" +
                        sicherung.daten.umfangText() + "."
                )
                Text(
                    "Alle Daten auf diesem Gerät werden ersetzt:\n" + store.daten.umfangText() + " gehen dabei verloren.",
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    "Auch der Gerätename („${sicherung.daten.geraetename}“) wird übernommen. Die Sicherung ist für den Umzug " +
                        "auf ein neues Handy oder eine Neuinstallation gedacht – nicht, um ein zweites Kassen-Handy einzurichten " +
                        "(dafür „Für andere Handys teilen“).",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { bestaetigt = !bestaetigt }) {
                    Checkbox(checked = bestaetigt, onCheckedChange = { bestaetigt = it })
                    Text("Ja, alles ersetzen")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                store.aendern { sicherung.daten }
                fertig()
                wiederhergestellt()
            }, enabled = bestaetigt) { Text("Wiederherstellen") }
        },
        dismissButton = { TextButton(onClick = fertig) { Text("Abbrechen") } },
    )
}

@Composable
private fun KonfigImportDialog(store: KassenStore, konfig: VereinKonfig, fertig: () -> Unit, importiert: () -> Unit) {
    val vorhanden = store.daten.passenderVerein(konfig)
    val neueFeste = vorhanden.neueVeranstaltungen(konfig)
    val umfang = "${konfig.gruppen.size} Gruppen und ${konfig.artikel.size} Artikel" +
        if (neueFeste.isNotEmpty()) " sowie ${neueFeste.size} neue Veranstaltung(en) (${neueFeste.joinToString { "„${it.name}“" }})" else ""
    fun ausfuehren(art: ImportArt) {
        store.aendern { it.konfigImportieren(konfig, art) }
        fertig()
        importiert()
    }

    if (vorhanden == null) {
        AlertDialog(
            onDismissRequest = fertig,
            title = { Text("Verein importieren?") },
            text = { Text("„${konfig.name}“ mit $umfang wird als neuer Verein angelegt.") },
            confirmButton = { TextButton(onClick = { ausfuehren(ImportArt.NEU) }) { Text("Importieren") } },
            dismissButton = { TextButton(onClick = fertig) { Text("Abbrechen") } },
        )
    } else {
        AlertDialog(
            onDismissRequest = fertig,
            title = { Text("„${vorhanden.name}“ gibt es schon") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Die Datei enthält $umfang. Was soll passieren?")
                    Button(onClick = { ausfuehren(ImportArt.ERGAENZEN) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Ergänzen")
                    }
                    Text(
                        "Neue Gruppen und Artikel werden hinzugefügt, vorhandene (gleicher Name) bekommen Preis, Pfand und Gruppe aus der Datei.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(onClick = { ausfuehren(ImportArt.ERSETZEN) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Ersetzen")
                    }
                    Text(
                        "Alle Gruppen und Artikel werden durch die aus der Datei ersetzt. Vorhandene Veranstaltungen und Umsätze bleiben erhalten.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(onClick = { ausfuehren(ImportArt.NEU) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Als neuen Verein anlegen")
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = fertig) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun AbschlussImportDialog(store: KassenStore, abschluss: Abschluss, fertig: () -> Unit) {
    val verein = store.aktiverVerein
    val veranstaltung = store.aktiveVeranstaltung
    when {
        verein == null || veranstaltung == null -> HinweisDialog(
            "Abschluss importieren",
            "Bitte zuerst den Verein und die Veranstaltung auswählen, zu der der Abschluss gehört.",
            fertig,
        )
        abschluss.id == eigeneAbschlussId(veranstaltung, store.daten.geraeteId) -> HinweisDialog(
            "Abschluss importieren",
            "Das ist der Abschluss dieses Geräts – er ist schon enthalten.",
            fertig,
        )
        else -> {
            val bekannt = veranstaltung.fremdAbschluesse.any { it.id == abschluss.id }
            val andererName = !abschluss.vereinName.equals(verein.name, ignoreCase = true) ||
                !abschluss.veranstaltungName.equals(veranstaltung.name, ignoreCase = true)
            AlertDialog(
                onDismissRequest = fertig,
                title = { Text("Abschluss hinzufügen?") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Abschluss von „${abschluss.geraet}“ (${abschluss.anzahlBestellungen} Bestellungen, " +
                                "${geld(abschluss.kassenEinnahmeCent)}) zur Veranstaltung „${veranstaltung.name}“ hinzufügen?"
                        )
                        if (bekannt) Text("Ein älterer Stand dieses Geräts wird ersetzt.", style = MaterialTheme.typography.bodySmall)
                        if (andererName) {
                            Text(
                                "Achtung: Der Abschluss stammt von „${abschluss.vereinName} – ${abschluss.veranstaltungName}“.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        store.aendern { it.abschlussImportieren(verein.id, veranstaltung.id, abschluss) }
                        fertig()
                    }) { Text("Hinzufügen") }
                },
                dismissButton = { TextButton(onClick = fertig) { Text("Abbrechen") } },
            )
        }
    }
}
