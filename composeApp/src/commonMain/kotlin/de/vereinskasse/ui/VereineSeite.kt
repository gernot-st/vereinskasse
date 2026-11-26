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
import de.vereinskasse.logik.alsCsv
import de.vereinskasse.logik.csvVorlage
import de.vereinskasse.logik.umsaetzeGeloescht
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import de.vereinskasse.logik.Plattform
import de.vereinskasse.logik.dateiname
import de.vereinskasse.logik.konfigExport
import de.vereinskasse.logik.neueId
import de.vereinskasse.logik.datumZeit
import de.vereinskasse.logik.jetzt
import de.vereinskasse.logik.sicherungExport
import de.vereinskasse.logik.sicherungsDateiname
import de.vereinskasse.model.Verein
import de.vereinskasse.logik.geld
import de.vereinskasse.logik.summeCent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.font.FontWeight

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
    var umsaetzeLoeschen by remember { mutableStateOf(false) }

    fun sicherungTeilen() {
        val zeit = jetzt()
        store.aendern { it.copy(letzteSicherung = zeit) }
        val logos = store.daten.vereine.mapNotNull { v ->
            v.logoId?.let { id -> store.logoBytes(id)?.let { id to it } }
        }.toMap()
        plattform.teileDatei(sicherungsDateiname(zeit), store.daten.sicherungExport(zeit, logos))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = kopfzeile(),
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
                    logo = store.logoBild(verein.logoId),
                    onTeilen = {
                        plattform.teileDatei(dateiname(verein.name, "Konfiguration"), verein.konfigExport(store.logoBytes(verein.logoId)))
                    },
                    onCsvExport = {
                        plattform.teileDatei(dateiname(verein.name, "Artikel", "csv"), verein.alsCsv(), "text/csv")
                    },
                    onCsvImport = {
                        // Ziel des CSV-Imports ist der aktive Verein
                        store.aendern { it.copy(aktiverVereinId = verein.id) }
                        plattform.dateiOeffnen()
                    },
                    onVorlage = { plattform.teileDatei("Vereinskasse_Vorlage.csv", csvVorlage(), "text/csv") },
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
                            (if (plattform.kannDateienDirektOeffnen) "• Importieren: Die Datei in WhatsApp antippen und mit „Vereinskasse“ öffnen – " +
                                "oder die Datei speichern und hier auf „Importieren“ tippen.\n"
                            else "• Importieren: Die Datei in WhatsApp antippen, über „Teilen“ in „Dateien“ sichern " +
                                "und dann hier auf „Importieren“ tippen.\n") +
                            "• Gibt es den Verein schon, fragt die App, ob ersetzt oder ergänzt werden soll.\n" +
                            "• Excel: Beim Verein unter „Teilen“ (oder in „Artikel & Co.“ im Menü oben rechts): „Als CSV exportieren“ bzw. „CSV importieren“. " +
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
                    plattform.speicherHinweis?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Text(
                        daten.letzteSicherung?.let { "Letzte Sicherung erstellt: ${datumZeit(it)}" } ?: "Noch keine Sicherung erstellt.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(onClick = { sicherungTeilen() }, modifier = Modifier.weight(1f)) {
                            Text("Sicherung erstellen")
                        }
                        OutlinedButton(onClick = { plattform.dateiOeffnen() }, modifier = Modifier.weight(1f)) {
                            Text("Wiederherstellen")
                        }
                    }
                }
            }
            item {
                Abschnitt("Alle Umsätze löschen") {
                    val verein = store.aktiverVerein
                    val veranstaltung = store.aktiveVeranstaltung
                    Text(
                        "Löscht Bestellungen, Stornos, importierte Abschlüsse und den gezählten Kassenstand der gerade " +
                            "gewählten Veranstaltung und setzt den Anfangsbestand auf 0. Vereine, Artikel und die " +
                            "Veranstaltung selbst bleiben erhalten – z. B. nach einem Probedurchlauf.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        if (verein != null && veranstaltung != null) "Gewählt: ${verein.name} – ${veranstaltung.name}"
                        else "Keine Veranstaltung gewählt.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    OutlinedButton(
                        onClick = { umsaetzeLoeschen = true },
                        enabled = veranstaltung != null,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) {
                        Icon(Icons.Default.Delete, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Alle Umsätze löschen")
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

    val zuLoeschenVerein = store.aktiverVerein
    val zuLoeschen = store.aktiveVeranstaltung
    if (umsaetzeLoeschen && zuLoeschenVerein != null && zuLoeschen != null) {
        UmsaetzeLoeschenDialog(
            verein = zuLoeschenVerein,
            veranstaltung = zuLoeschen,
            onSicherung = { sicherungTeilen() },
            onLoeschen = {
                store.veranstaltungAendern(zuLoeschenVerein.id, zuLoeschen.id) { it.umsaetzeGeloescht() }
                umsaetzeLoeschen = false
            },
            onAbbrechen = { umsaetzeLoeschen = false },
        )
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
                store.logosAufraeumen()
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
    logo: androidx.compose.ui.graphics.ImageBitmap?,
    onWaehlen: () -> Unit,
    onBearbeiten: () -> Unit,
    onTeilen: () -> Unit,
    onCsvExport: () -> Unit,
    onCsvImport: () -> Unit,
    onVorlage: () -> Unit,
    onUmbenennen: () -> Unit,
    onLoeschen: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onWaehlen)) {
        Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = aktiv, onClick = onWaehlen)
            LogoBild(logo, 44.dp, Modifier.padding(end = 10.dp))
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
            Box(Modifier.weight(1f)) {
                var menue by remember { mutableStateOf(false) }
                OutlinedButton(onClick = { menue = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Share, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Teilen")
                }
                DropdownMenu(expanded = menue, onDismissRequest = { menue = false }) {
                    DropdownMenuItem(text = { Text("Für andere Handys teilen") }, onClick = { menue = false; onTeilen() })
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("Als CSV exportieren (Excel)") }, onClick = { menue = false; onCsvExport() })
                    DropdownMenuItem(text = { Text("CSV importieren") }, onClick = { menue = false; onCsvImport() })
                    DropdownMenuItem(text = { Text("Leere CSV-Vorlage teilen") }, onClick = { menue = false; onVorlage() })
                }
            }
        }
        Spacer(Modifier.height(0.dp))
    }
}

@Composable
fun UmsaetzeLoeschenDialog(
    verein: Verein,
    veranstaltung: de.vereinskasse.model.Veranstaltung,
    onSicherung: () -> Unit,
    onLoeschen: () -> Unit,
    onAbbrechen: () -> Unit,
) {
    val gueltig = veranstaltung.bestellungen.filter { !it.storniert }
    val stornos = veranstaltung.bestellungen.size - gueltig.size
    val einnahmen = gueltig.sumOf { b -> b.positionen.summeCent() }
    AlertDialog(
        onDismissRequest = onAbbrechen,
        title = { Text("Alle Umsätze löschen?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Veranstaltung „${veranstaltung.name}“ (${verein.name}):", fontWeight = FontWeight.Bold)
                Text(
                    listOfNotNull(
                        "• ${anzahl(gueltig.size, "Bestellung", "Bestellungen")}, ${geld(einnahmen)}",
                        "• ${anzahl(stornos, "Storno", "Stornos")}",
                        "• ${anzahl(veranstaltung.fremdAbschluesse.size, "importierter Abschluss", "importierte Abschlüsse")} anderer Kassen",
                        veranstaltung.istBestandCent?.let { "• gezählter Kassenstand (${geld(it)})" },
                        "• Anfangsbestand ${geld(veranstaltung.anfangsbestandCent)} wird auf 0 gesetzt",
                    ).joinToString("\n"),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Willst du das wirklich? Die Umsätze können nicht wiederhergestellt werden.",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                )
                OutlinedButton(onClick = onSicherung, modifier = Modifier.fillMaxWidth()) {
                    Text("Vorher Sicherung erstellen")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onLoeschen) { Text("Umsätze löschen", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onAbbrechen) { Text("Abbrechen") } },
    )
}

private fun anzahl(n: Int, einzahl: String, mehrzahl: String) = "$n ${if (n == 1) einzahl else mehrzahl}"
