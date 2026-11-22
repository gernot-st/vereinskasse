package de.vereinskasse.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import de.vereinskasse.logik.KassenStore
import de.vereinskasse.logik.Plattform
import de.vereinskasse.logik.abschlussBerechnen
import de.vereinskasse.logik.alsText
import de.vereinskasse.logik.dateiname
import de.vereinskasse.logik.datumZeit
import de.vereinskasse.logik.export
import de.vereinskasse.logik.geld
import de.vereinskasse.logik.jetzt
import de.vereinskasse.logik.sortiertNach
import de.vereinskasse.logik.summeCent
import de.vereinskasse.logik.uhrzeit
import de.vereinskasse.logik.zusammenfuehren
import de.vereinskasse.model.Abschluss
import de.vereinskasse.model.Veranstaltung
import de.vereinskasse.model.Verein

/** Kennung des eigenen Abschlusses: pro Veranstaltung und Gerät gleich, damit ein erneuter Import ersetzt. */
fun eigeneAbschlussId(veranstaltung: Veranstaltung, geraeteId: String) = "${veranstaltung.id}_$geraeteId"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuswertungSeite(store: KassenStore, plattform: Plattform, zurueck: () -> Unit) {
    val verein = store.aktiverVerein
    var gewaehlteId by remember { mutableStateOf(verein?.aktiveVeranstaltungId) }
    val veranstaltung = verein?.veranstaltungen?.firstOrNull { it.id == gewaehlteId }
        ?: verein?.veranstaltungen?.maxByOrNull { it.erstelltAm }
    var teilenMenue by remember { mutableStateOf(false) }
    var fremdLoeschen by remember { mutableStateOf<Abschluss?>(null) }

    val eigener = if (verein != null && veranstaltung != null) {
        veranstaltung.abschlussBerechnen(
            eigeneAbschlussId(veranstaltung, store.daten.geraeteId),
            store.daten.geraetename, verein.name, jetzt(),
        )
    } else null
    val gesamt = if (eigener != null && veranstaltung != null && veranstaltung.fremdAbschluesse.isNotEmpty()) {
        (listOf(eigener) + veranstaltung.fremdAbschluesse).zusammenfuehren("Alle Geräte", eigener.vereinName, eigener.veranstaltungName, jetzt())
    } else null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Auswertung") },
                navigationIcon = { IconButton(onClick = zurueck) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } },
                actions = {
                    if (eigener != null) {
                        Box {
                            IconButton(onClick = { teilenMenue = true }) { Icon(Icons.Default.Share, "Teilen") }
                            DropdownMenu(expanded = teilenMenue, onDismissRequest = { teilenMenue = false }) {
                                DropdownMenuItem(
                                    text = { Text("Als Text teilen (WhatsApp)") },
                                    onClick = {
                                        teilenMenue = false
                                        plattform.teileText(auswertungsText(eigener, gesamt, verein))
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Abschluss-Datei teilen (zum Zusammenführen)") },
                                    onClick = {
                                        teilenMenue = false
                                        plattform.teileDatei(
                                            dateiname("${eigener.veranstaltungName}_${eigener.geraet}", "Abschluss"),
                                            eigener.export(),
                                        )
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { innen ->
        if (verein == null || veranstaltung == null || eigener == null) {
            Box(Modifier.padding(innen).fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("Noch keine Veranstaltung vorhanden.", textAlign = TextAlign.Center)
            }
            return@Scaffold
        }
        LazyColumn(
            Modifier.padding(innen).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (verein.veranstaltungen.size > 1) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        items(verein.veranstaltungen.sortedByDescending { it.erstelltAm }, key = { it.id }) { v ->
                            FilterChip(selected = v.id == veranstaltung.id, onClick = { gewaehlteId = v.id }, label = { Text(v.name) })
                        }
                    }
                }
            }
            item {
                Column(Modifier.widthIn(max = 640.dp)) {
                    Text(
                        "${veranstaltung.name} · ${store.daten.geraetename}",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Ueberblick(eigener)
                    Kassenstand(store, verein, veranstaltung, eigener)
                    ArtikelTabelle("Verkaufte Artikel", eigener, verein)
                }
            }
            item {
                Column(Modifier.widthIn(max = 640.dp)) {
                    Abschnitt("Mehrere Kassen zusammenführen") {
                        Text(
                            "Auf den anderen Geräten unter Auswertung → Teilen → „Abschluss-Datei teilen“ wählen " +
                                "und hierher schicken. Dann die Datei öffnen oder hier importieren.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        for (f in veranstaltung.fremdAbschluesse) {
                            Card(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                                Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                                        Text(f.geraet, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            "Stand ${datumZeit(f.erstelltAm)} · ${f.anzahlBestellungen} Bestellungen",
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                    Text(geld(f.kassenEinnahmeCent), style = MaterialTheme.typography.titleSmall)
                                    IconButton(onClick = { fremdLoeschen = f }) { Icon(Icons.Default.Delete, "Entfernen") }
                                }
                            }
                        }
                        OutlinedButton(onClick = { plattform.dateiOeffnen() }, modifier = Modifier.padding(top = 8.dp)) {
                            Text("Abschluss-Datei importieren")
                        }
                    }
                    if (gesamt != null) {
                        Abschnitt("Gesamt aller Geräte (${veranstaltung.fremdAbschluesse.size + 1})") {
                            WertZeile("Einnahmen gesamt", geld(gesamt.kassenEinnahmeCent), hervorheben = true)
                            WertZeile("davon Umsatz Artikel", geld(gesamt.artikelUmsatzCent))
                            if (gesamt.pfandEinCent != 0L || gesamt.pfandAusCent != 0L) {
                                WertZeile("Pfand eingenommen / ausgezahlt", "${geld(gesamt.pfandEinCent)} / ${geld(gesamt.pfandAusCent)}")
                            }
                            WertZeile("Bestellungen", gesamt.anzahlBestellungen.toString())
                        }
                        ArtikelTabelle("Artikel aller Geräte", gesamt, verein)
                    }
                    Bestellliste(veranstaltung)
                    Button(
                        onClick = { plattform.teileText(auswertungsText(eigener, gesamt, verein)) },
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    ) {
                        Icon(Icons.Default.Share, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Auswertung per WhatsApp teilen")
                    }
                }
            }
        }
    }

    fremdLoeschen?.let { f ->
        BestaetigenDialog(
            titel = "Abschluss entfernen?",
            text = "Der Abschluss von „${f.geraet}“ wird aus dieser Auswertung entfernt.",
            bestaetigen = "Entfernen",
            onBestaetigen = {
                if (verein != null && veranstaltung != null) {
                    store.veranstaltungAendern(verein.id, veranstaltung.id) { v ->
                        v.copy(fremdAbschluesse = v.fremdAbschluesse.filter { it.id != f.id })
                    }
                }
                fremdLoeschen = null
            },
            onAbbrechen = { fremdLoeschen = null },
        )
    }
}

private fun auswertungsText(eigener: Abschluss, gesamt: Abschluss?, verein: Verein?): String =
    if (gesamt == null) eigener.alsText("Abschluss ${eigener.geraet}", mitKassenstand = true, verein = verein)
    else gesamt.alsText("Abschluss aller Geräte", mitKassenstand = false, verein = verein) + "\n\n" +
        eigener.alsText("Davon ${eigener.geraet}", mitKassenstand = true, verein = verein)

@Composable
private fun Ueberblick(a: Abschluss) {
    Abschnitt("Überblick") {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Einnahmen", style = MaterialTheme.typography.labelLarge)
                Text(geld(a.kassenEinnahmeCent), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                WertZeile("Umsatz Artikel", geld(a.artikelUmsatzCent))
                if (a.pfandEinCent != 0L || a.pfandAusCent != 0L) {
                    WertZeile("Pfand eingenommen", geld(a.pfandEinCent))
                    WertZeile("Pfand ausgezahlt", geld(-a.pfandAusCent))
                }
                WertZeile("Bestellungen", a.anzahlBestellungen.toString())
                val helfer = a.artikel.sumOf { it.helferMenge }
                if (helfer > 0) WertZeile("Helferbuchungen (0 €)", helfer.toString())
                if (a.anzahlStornos > 0) WertZeile("Stornos", "${a.anzahlStornos} (${geld(a.stornoSummeCent)})")
            }
        }
    }
}

@Composable
private fun Kassenstand(store: KassenStore, verein: Verein, veranstaltung: Veranstaltung, a: Abschluss) {
    Abschnitt("Kassenstand") {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                key(veranstaltung.id) {
                    BetragFeld(
                        "Anfangsbestand (Wechselgeld)",
                        veranstaltung.anfangsbestandCent,
                        { wert ->
                            if (wert != null) store.veranstaltungAendern(verein.id, veranstaltung.id) { it.copy(anfangsbestandCent = wert) }
                        },
                        Modifier.fillMaxWidth(),
                    )
                }
                WertZeile("Soll in der Kasse", geld(a.sollBestandCent), Modifier.padding(top = 8.dp), hervorheben = true)
                key(veranstaltung.id) {
                    BetragFeld(
                        "Ist – gezählter Kassenstand",
                        veranstaltung.istBestandCent,
                        { wert -> store.veranstaltungAendern(verein.id, veranstaltung.id) { it.copy(istBestandCent = wert) } },
                        Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                }
                veranstaltung.istBestandCent?.let { ist ->
                    val differenz = ist - a.sollBestandCent
                    WertZeile(
                        when {
                            differenz == 0L -> "Differenz – stimmt genau"
                            differenz > 0 -> "Differenz (zu viel)"
                            else -> "Differenz (zu wenig)"
                        },
                        geld(differenz),
                        Modifier.padding(top = 8.dp),
                        hervorheben = true,
                        wertFarbe = when {
                            differenz == 0L -> Gruen
                            differenz < 0 -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.tertiary
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtikelTabelle(titel: String, a: Abschluss, verein: Verein) {
    Abschnitt(titel) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                if (a.artikel.isEmpty()) {
                    Text("Noch nichts verkauft.", modifier = Modifier.padding(vertical = 8.dp))
                }
                for ((i, s) in a.artikel.sortiertNach(verein).withIndex()) {
                    if (i > 0) HorizontalDivider()
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${s.menge}×", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(52.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (s.helferMenge > 0) {
                                Text("davon ${s.helferMenge} Helfer", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                            }
                        }
                        Text(geld(s.umsatzCent), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun Bestellliste(veranstaltung: Veranstaltung) {
    var offen by remember { mutableStateOf(false) }
    val liste = veranstaltung.bestellungen.sortedByDescending { it.zeit }
    if (liste.isEmpty()) return
    Abschnitt("Bestellungen (${liste.size})") {
        TextButton(onClick = { offen = !offen }) { Text(if (offen) "Ausblenden" else "Alle Bestellungen anzeigen") }
        if (offen) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    for ((i, b) in liste.withIndex()) {
                        if (i > 0) HorizontalDivider()
                        val deko = if (b.storniert) TextDecoration.LineThrough else TextDecoration.None
                        val farbe = if (b.storniert) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Text(uhrzeit(b.zeit), modifier = Modifier.width(56.dp), color = farbe, textDecoration = deko)
                            Text(
                                b.positionen.joinToString(", ") { "${it.menge}× ${it.name}" } + if (b.storniert) " (storniert)" else "",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                color = farbe,
                                textDecoration = deko,
                            )
                            Text(geld(b.positionen.summeCent()), color = farbe, textDecoration = deko)
                        }
                    }
                }
            }
        }
    }
}
