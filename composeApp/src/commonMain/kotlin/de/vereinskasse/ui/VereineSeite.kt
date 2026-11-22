package de.vereinskasse.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.vereinskasse.logik.KassenStore
import de.vereinskasse.logik.Plattform
import de.vereinskasse.logik.dateiname
import de.vereinskasse.logik.konfigExport
import de.vereinskasse.logik.neueId
import de.vereinskasse.logik.datumZeit
import de.vereinskasse.logik.jetzt
import de.vereinskasse.logik.sicherungExport
import de.vereinskasse.logik.sicherungsDateiname
import de.vereinskasse.model.Verein

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VereineSeite(
    store: KassenStore,
    plattform: Plattform,
    oeffne: (Seite) -> Unit,
    zurueck: (() -> Unit)?,
    zurKasse: () -> Unit,
) {
    val daten = store.daten
    var neuOffen by remember { mutableStateOf(false) }
    var umbenennen by remember { mutableStateOf<Verein?>(null) }
    var loeschen by remember { mutableStateOf<Verein?>(null) }
    var geraetename by remember { mutableStateOf(daten.geraetename) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vereine") },
                navigationIcon = {
                    if (zurueck != null) {
                        IconButton(onClick = zurueck) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") }
                    }
                },
            )
        },
    ) { innen ->
        LazyColumn(
            Modifier.padding(innen).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (daten.vereine.isEmpty()) {
                item {
                    Text(
                        "Willkommen! Lege zuerst einen Verein an – oder importiere die Konfiguration, " +
                            "die dir jemand per WhatsApp geschickt hat.",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }
            items(daten.vereine, key = { it.id }) { verein ->
                VereinKarte(
                    verein = verein,
                    aktiv = verein.id == daten.aktiverVereinId,
                    onWaehlen = {
                        store.aendern { it.copy(aktiverVereinId = verein.id) }
                        zurKasse()
                    },
                    onBearbeiten = {
                        store.aendern { it.copy(aktiverVereinId = verein.id) }
                        oeffne(Seite.VereinBearbeiten(verein.id))
                    },
                    onTeilen = { plattform.teileDatei(dateiname(verein.name, "Konfiguration"), verein.konfigExport()) },
                    onUmbenennen = { umbenennen = verein },
                    onLoeschen = { loeschen = verein },
                )
            }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(onClick = { neuOffen = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Neuer Verein")
                    }
                    OutlinedButton(onClick = { plattform.dateiOeffnen() }, modifier = Modifier.weight(1f)) {
                        Text("Importieren")
                    }
                }
            }
            item {
                Abschnitt("So funktioniert Teilen per WhatsApp") {
                    Text(
                        "• Exportieren: Beim Verein auf „Teilen“ tippen und WhatsApp wählen. " +
                            "Es wird eine Datei mit Gruppen, Artikeln und Veranstaltungen (ohne Umsätze) verschickt – " +
                            "so haben alle Kassen dieselbe Veranstaltung.\n" +
                            "• Importieren: Die Datei in WhatsApp antippen und mit „Vereinskasse“ öffnen – " +
                            "oder die Datei speichern und hier auf „Importieren“ tippen.\n" +
                            "• Gibt es den Verein schon, fragt die App, ob ersetzt oder ergänzt werden soll.\n" +
                            "• Excel: Im Verein über ⋮ → „Als CSV exportieren“ bzw. „CSV importieren“. " +
                            "Spalten: Gruppe; Artikel; Preis; Pfand; Sichtbar; Farbe. „Importieren“ hier erkennt CSV-Dateien ebenfalls.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                Abschnitt("Datensicherung") {
                    Text(
                        "Sichert alles – Vereine, Artikel, Veranstaltungen, Bestellungen und Abschlüsse – in eine Datei. " +
                            "Zum Wiederherstellen nach Handywechsel oder Neuinstallation die Datei mit der App öffnen " +
                            "oder hier „Wiederherstellen“ wählen.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        daten.letzteSicherung?.let { "Letzte Sicherung erstellt: ${datumZeit(it)}" } ?: "Noch keine Sicherung erstellt.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(onClick = {
                            val zeit = jetzt()
                            store.aendern { it.copy(letzteSicherung = zeit) }
                            plattform.teileDatei(sicherungsDateiname(zeit), store.daten.sicherungExport(zeit))
                        }, modifier = Modifier.weight(1f)) {
                            Text("Sicherung erstellen")
                        }
                        OutlinedButton(onClick = { plattform.dateiOeffnen() }, modifier = Modifier.weight(1f)) {
                            Text("Wiederherstellen")
                        }
                    }
                }
            }
            item {
                Abschnitt("Dieses Gerät") {
                    OutlinedTextField(
                        value = geraetename,
                        onValueChange = { text ->
                            geraetename = text
                            if (text.isNotBlank()) store.aendern { it.copy(geraetename = text.trim()) }
                        },
                        label = { Text("Gerätename (z. B. Getränke-Kasse)") },
                        supportingText = { Text("Erscheint in Abschlüssen, wenn mehrere Kassen zusammengeführt werden.") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    if (neuOffen) {
        NameDialog("Neuer Verein", "Name des Vereins", "", onSpeichern = { name ->
            val verein = Verein(neueId(), name)
            store.aendern { it.copy(vereine = it.vereine + verein, aktiverVereinId = verein.id) }
            neuOffen = false
            oeffne(Seite.VereinBearbeiten(verein.id))
        }, onAbbrechen = { neuOffen = false })
    }

    umbenennen?.let { verein ->
        NameDialog("Verein umbenennen", "Name des Vereins", verein.name, onSpeichern = { name ->
            store.vereinAendern(verein.id) { it.copy(name = name) }
            umbenennen = null
        }, onAbbrechen = { umbenennen = null })
    }

    loeschen?.let { verein ->
        BestaetigenDialog(
            titel = "Verein löschen?",
            text = "„${verein.name}“ mit ${verein.artikel.size} Artikeln und ${verein.veranstaltungen.size} " +
                "Veranstaltungen (inkl. aller Bestellungen) wird endgültig gelöscht.",
            bestaetigen = "Löschen",
            onBestaetigen = {
                store.aendern { d ->
                    d.copy(
                        vereine = d.vereine.filter { it.id != verein.id },
                        aktiverVereinId = if (d.aktiverVereinId == verein.id) null else d.aktiverVereinId,
                    )
                }
                loeschen = null
            },
            onAbbrechen = { loeschen = null },
        )
    }
}

@Composable
private fun VereinKarte(
    verein: Verein,
    aktiv: Boolean,
    onWaehlen: () -> Unit,
    onBearbeiten: () -> Unit,
    onTeilen: () -> Unit,
    onUmbenennen: () -> Unit,
    onLoeschen: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onWaehlen)) {
        Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = aktiv, onClick = onWaehlen)
            Column(Modifier.weight(1f)) {
                Text(verein.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${verein.gruppen.size} Gruppen · ${verein.artikel.size} Artikel · ${verein.veranstaltungen.size} Veranstaltungen",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onUmbenennen) { Icon(Icons.Default.Edit, "Umbenennen") }
            IconButton(onClick = onLoeschen) { Icon(Icons.Default.Delete, "Löschen") }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = onBearbeiten, modifier = Modifier.weight(1f)) { Text("Artikel & Co.") }
            OutlinedButton(onClick = onTeilen, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Share, null)
                Spacer(Modifier.width(6.dp))
                Text("Teilen")
            }
        }
        Spacer(Modifier.height(0.dp))
    }
}
