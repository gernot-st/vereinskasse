package de.vereinskasse.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.vereinskasse.logik.CsvErgebnis
import de.vereinskasse.logik.CsvFehler
import de.vereinskasse.logik.KassenStore
import de.vereinskasse.logik.csvLesen
import de.vereinskasse.logik.csvUebernehmen
import de.vereinskasse.logik.neueId
import de.vereinskasse.logik.vereinsnameAusDatei
import de.vereinskasse.model.Verein

private const val NEUER_VEREIN = "__neu__"

/** Vorschau einer CSV-Datei mit Auswahl des Vereins und Ergänzen/Ersetzen. */
@Composable
fun CsvImportDialog(
    store: KassenStore,
    text: String,
    dateiname: String?,
    zielVereinId: String?,
    fertig: () -> Unit,
    importiert: () -> Unit,
) {
    val ergebnis: Result<CsvErgebnis> = remember(text) {
        try {
            Result.success(csvLesen(text))
        } catch (e: CsvFehler) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(CsvFehler("Die Datei konnte nicht als CSV gelesen werden."))
        }
    }
    val csv = ergebnis.getOrElse {
        HinweisDialog("CSV-Import nicht möglich", it.message ?: "Unbekannter Fehler", fertig)
        return
    }

    val vereine = store.daten.vereine
    var ziel by remember { mutableStateOf(zielVereinId?.takeIf { id -> vereine.any { it.id == id } } ?: NEUER_VEREIN) }
    var neuerName by remember { mutableStateOf(vereinsnameAusDatei(dateiname)) }
    val zielVerein = vereine.firstOrNull { it.id == ziel }
    val zielHatInhalt = zielVerein != null && (zielVerein.artikel.isNotEmpty() || zielVerein.gruppen.isNotEmpty())

    fun uebernehmen(ersetzen: Boolean) {
        if (zielVerein == null) {
            val neu = Verein(neueId(), neuerName.trim()).csvUebernehmen(csv, ersetzen = true)
            store.aendern { it.copy(vereine = it.vereine + neu, aktiverVereinId = neu.id) }
        } else {
            store.vereinAendern(zielVerein.id) { it.csvUebernehmen(csv, ersetzen) }
        }
        fertig()
        importiert()
    }

    AlertDialog(
        onDismissRequest = fertig,
        title = { Text("Artikel aus CSV importieren") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${csv.gruppen.size} Gruppen und ${csv.artikel.size} Artikel gefunden.")
                Meldungen(
                    if (csv.fehler.size == 1) "1 Zeile wird übersprungen:" else "${csv.fehler.size} Zeilen werden übersprungen:",
                    csv.fehler,
                    MaterialTheme.colorScheme.error,
                )
                Meldungen("Hinweise:", csv.hinweise, MaterialTheme.colorScheme.onSurfaceVariant)

                Text("In welchen Verein?", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
                for (v in vereine) {
                    Auswahlzeile(v.name, ziel == v.id) { ziel = v.id }
                }
                Auswahlzeile("Neuen Verein anlegen", ziel == NEUER_VEREIN) { ziel = NEUER_VEREIN }
                if (ziel == NEUER_VEREIN) {
                    TextFeld("Name des Vereins", neuerName, { neuerName = it }, Modifier.fillMaxWidth())
                }

                if (zielHatInhalt) {
                    Button(onClick = { uebernehmen(ersetzen = false) }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        Text("Ergänzen")
                    }
                    Text(
                        "Neue Gruppen und Artikel werden hinzugefügt, gleichnamige bekommen Preis, Pfand und Gruppe aus der Datei.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(onClick = { uebernehmen(ersetzen = true) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Ersetzen")
                    }
                    Text(
                        "Gruppen und Artikel werden genau wie in der Datei übernommen, alles andere entfernt. " +
                            "Veranstaltungen und Umsätze bleiben erhalten.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            if (!zielHatInhalt) {
                TextButton(
                    onClick = { uebernehmen(ersetzen = true) },
                    enabled = ziel != NEUER_VEREIN || neuerName.isNotBlank(),
                ) { Text("Importieren") }
            }
        },
        dismissButton = { TextButton(onClick = fertig) { Text("Abbrechen") } },
    )
}

@Composable
private fun Meldungen(titel: String, meldungen: List<String>, farbe: androidx.compose.ui.graphics.Color) {
    if (meldungen.isEmpty()) return
    Column(Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState())) {
        Text(titel, style = MaterialTheme.typography.labelLarge, color = farbe)
        for (m in meldungen.take(20)) Text("• $m", style = MaterialTheme.typography.bodySmall, color = farbe)
        if (meldungen.size > 20) Text("… und ${meldungen.size - 20} weitere", style = MaterialTheme.typography.bodySmall, color = farbe)
    }
}

@Composable
private fun Auswahlzeile(text: String, gewaehlt: Boolean, onWahl: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onWahl), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = gewaehlt, onClick = onWahl)
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
