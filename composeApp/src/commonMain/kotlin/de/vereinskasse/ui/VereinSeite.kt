package de.vereinskasse.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.vereinskasse.logik.GRUPPEN_FARBEN
import de.vereinskasse.logik.KassenStore
import de.vereinskasse.logik.Plattform
import de.vereinskasse.logik.dateiname
import de.vereinskasse.logik.datum
import de.vereinskasse.logik.geld
import de.vereinskasse.logik.jetzt
import de.vereinskasse.logik.konfigExport
import de.vereinskasse.logik.neueId
import de.vereinskasse.logik.summeCent
import de.vereinskasse.logik.teilmengeVerschieben
import de.vereinskasse.model.Artikel
import de.vereinskasse.model.Gruppe
import de.vereinskasse.model.Veranstaltung
import de.vereinskasse.model.Verein
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private enum class Reiter(val titel: String) { ARTIKEL("Artikel"), GRUPPEN("Gruppen"), VERANSTALTUNGEN("Veranstaltungen") }

/** Markiert im Artikel-Filter "ohne Gruppe". */
private const val OHNE_GRUPPE = "__ohne__"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VereinSeite(
    store: KassenStore,
    plattform: Plattform,
    vereinId: String,
    zurueck: () -> Unit,
    startReiter: Int = 0,
) {
    val verein = store.verein(vereinId)
    if (verein == null) {
        zurueck()
        return
    }
    var reiter by remember { mutableStateOf(Reiter.entries[startReiter]) }
    var umbenennen by remember { mutableStateOf(false) }
    // Dialog-Zustände: null = zu, Objekt mit leerer id = neu
    var artikelDialog by remember { mutableStateOf<Artikel?>(null) }
    var gruppeDialog by remember { mutableStateOf<Gruppe?>(null) }
    var veranstaltungDialog by remember { mutableStateOf<Veranstaltung?>(null) }
    var neueVeranstaltung by remember { mutableStateOf(false) }
    var artikelFilter by remember { mutableStateOf(verein.gruppen.firstOrNull()?.id ?: OHNE_GRUPPE) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(verein.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = zurueck) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } },
                actions = {
                    IconButton(onClick = { umbenennen = true }) { Icon(Icons.Default.Edit, "Umbenennen") }
                    IconButton(onClick = {
                        plattform.teileDatei(dateiname(verein.name, "Konfiguration"), verein.konfigExport())
                    }) { Icon(Icons.Default.Share, "Konfiguration teilen") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    when (reiter) {
                        Reiter.ARTIKEL -> artikelDialog = Artikel(
                            id = "", name = "", preisCent = 0,
                            gruppeId = artikelFilter.takeIf { it != OHNE_GRUPPE },
                        )
                        Reiter.GRUPPEN -> gruppeDialog = Gruppe(
                            "", "", GRUPPEN_FARBEN[verein.gruppen.size % GRUPPEN_FARBEN.size],
                        )
                        Reiter.VERANSTALTUNGEN -> neueVeranstaltung = true
                    }
                },
                icon = { Icon(Icons.Default.Add, null) },
                text = {
                    Text(
                        when (reiter) {
                            Reiter.ARTIKEL -> "Artikel"
                            Reiter.GRUPPEN -> "Gruppe"
                            Reiter.VERANSTALTUNGEN -> "Veranstaltung"
                        }
                    )
                },
            )
        },
    ) { innen ->
        Column(Modifier.padding(innen).fillMaxSize()) {
            PrimaryTabRow(selectedTabIndex = reiter.ordinal) {
                for (r in Reiter.entries) {
                    Tab(selected = reiter == r, onClick = { reiter = r }, text = { Text(r.titel, maxLines = 1, softWrap = false, fontSize = 13.sp) })
                }
            }
            when (reiter) {
                Reiter.ARTIKEL -> ArtikelReiter(
                    store, verein, artikelFilter,
                    onFilter = { artikelFilter = it },
                    onBearbeiten = { artikelDialog = it },
                )
                Reiter.GRUPPEN -> GruppenReiter(store, verein, onBearbeiten = { gruppeDialog = it })
                Reiter.VERANSTALTUNGEN -> VeranstaltungenReiter(store, verein, onBearbeiten = { veranstaltungDialog = it })
            }
        }
    }

    if (umbenennen) {
        NameDialog("Verein umbenennen", "Name des Vereins", verein.name, onSpeichern = { name ->
            store.vereinAendern(verein.id) { it.copy(name = name) }
            umbenennen = false
        }, onAbbrechen = { umbenennen = false })
    }

    artikelDialog?.let { artikel ->
        ArtikelDialog(
            artikel = artikel,
            gruppen = verein.gruppen,
            onSpeichern = { neu, weiterer ->
                store.vereinAendern(verein.id) { v ->
                    if (artikel.id.isEmpty()) v.copy(artikel = v.artikel + neu.copy(id = neueId()))
                    else v.copy(artikel = v.artikel.map { if (it.id == artikel.id) neu else it })
                }
                artikelFilter = neu.gruppeId ?: OHNE_GRUPPE
                artikelDialog = if (weiterer) Artikel("", "", 0, neu.gruppeId, neu.pfandCent) else null
            },
            onLoeschen = if (artikel.id.isEmpty()) null else ({
                store.vereinAendern(verein.id) { v -> v.copy(artikel = v.artikel.filter { it.id != artikel.id }) }
                artikelDialog = null
            }),
            onAbbrechen = { artikelDialog = null },
        )
    }

    gruppeDialog?.let { gruppe ->
        GruppeDialog(
            gruppe = gruppe,
            anzahlArtikel = verein.artikel.count { it.gruppeId == gruppe.id && gruppe.id.isNotEmpty() },
            onSpeichern = { neu ->
                store.vereinAendern(verein.id) { v ->
                    if (gruppe.id.isEmpty()) v.copy(gruppen = v.gruppen + neu.copy(id = neueId()))
                    else v.copy(gruppen = v.gruppen.map { if (it.id == gruppe.id) neu else it })
                }
                gruppeDialog = null
            },
            onLoeschen = if (gruppe.id.isEmpty()) null else ({
                store.vereinAendern(verein.id) { v ->
                    v.copy(
                        gruppen = v.gruppen.filter { it.id != gruppe.id },
                        artikel = v.artikel.map { if (it.gruppeId == gruppe.id) it.copy(gruppeId = null) else it },
                    )
                }
                if (artikelFilter == gruppe.id) artikelFilter = OHNE_GRUPPE
                gruppeDialog = null
            }),
            onAbbrechen = { gruppeDialog = null },
        )
    }

    if (neueVeranstaltung) {
        VeranstaltungDialog(
            vorhanden = null,
            onSpeichern = { name, anfang ->
                val neu = Veranstaltung(neueId(), name, jetzt(), anfangsbestandCent = anfang)
                store.vereinAendern(verein.id) {
                    it.copy(veranstaltungen = it.veranstaltungen + neu, aktiveVeranstaltungId = neu.id)
                }
                neueVeranstaltung = false
            },
            onLoeschen = null,
            onAbbrechen = { neueVeranstaltung = false },
        )
    }

    veranstaltungDialog?.let { v ->
        VeranstaltungDialog(
            vorhanden = v,
            onSpeichern = { name, anfang ->
                store.veranstaltungAendern(verein.id, v.id) { it.copy(name = name, anfangsbestandCent = anfang) }
                veranstaltungDialog = null
            },
            onLoeschen = {
                store.vereinAendern(verein.id) { ver ->
                    ver.copy(
                        veranstaltungen = ver.veranstaltungen.filter { it.id != v.id },
                        aktiveVeranstaltungId = if (ver.aktiveVeranstaltungId == v.id) null else ver.aktiveVeranstaltungId,
                    )
                }
                veranstaltungDialog = null
            },
            onAbbrechen = { veranstaltungDialog = null },
        )
    }
}

@Composable
private fun Hinweis(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun ArtikelReiter(
    store: KassenStore,
    verein: Verein,
    filter: String,
    onFilter: (String) -> Unit,
    onBearbeiten: (Artikel) -> Unit,
) {
    val gehoertDazu: (Artikel) -> Boolean = { a -> (a.gruppeId ?: OHNE_GRUPPE) == filter || (filter == OHNE_GRUPPE && verein.gruppen.none { it.id == a.gruppeId }) }
    val liste = verein.artikel.filter(gehoertDazu)
    val hatOhneGruppe = verein.artikel.any { a -> a.gruppeId == null || verein.gruppen.none { it.id == a.gruppeId } }
    val farbe = verein.gruppen.firstOrNull { it.id == filter }?.farbe?.alsFarbe() ?: MaterialTheme.colorScheme.secondary

    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { von, nach ->
        val vonIndex = liste.indexOfFirst { it.id == von.key }
        val nachIndex = liste.indexOfFirst { it.id == nach.key }
        if (vonIndex >= 0 && nachIndex >= 0) {
            store.vereinAendern(verein.id) { v -> v.copy(artikel = v.artikel.teilmengeVerschieben(gehoertDazu, vonIndex, nachIndex)) }
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (verein.gruppen.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(verein.gruppen, key = { it.id }) { g ->
                    FilterChip(
                        selected = filter == g.id,
                        onClick = { onFilter(g.id) },
                        label = { Text(g.name) },
                        leadingIcon = { FarbPunkt(g.farbe.alsFarbe()) },
                    )
                }
                if (hatOhneGruppe) {
                    item {
                        FilterChip(selected = filter == OHNE_GRUPPE, onClick = { onFilter(OHNE_GRUPPE) }, label = { Text("Ohne Gruppe") })
                    }
                }
            }
        } else {
            Hinweis("Tipp: Lege unter „Gruppen“ z. B. Getränke und Essen an – dann werden die Kacheln farbig.")
        }
        if (liste.isEmpty()) {
            Hinweis("Noch keine Artikel in dieser Gruppe. Tippe unten auf „+ Artikel“.")
        } else {
            Hinweis("Zum Sortieren am ☰-Symbol ziehen. Schalter = an der Kasse sichtbar.")
        }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(liste, key = { it.id }) { artikel ->
                ReorderableItem(reorderState, key = artikel.id) { zieht ->
                    Card(
                        elevation = CardDefaults.cardElevation(defaultElevation = if (zieht) 8.dp else 1.dp),
                        modifier = Modifier.fillMaxWidth().clickable { onBearbeiten(artikel) },
                    ) {
                        Row(Modifier.padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {}, modifier = Modifier.draggableHandle()) {
                                Icon(Icons.Default.Menu, contentDescription = "Verschieben")
                            }
                            FarbPunkt(farbe)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f).padding(vertical = 10.dp).alpha(if (artikel.sichtbar) 1f else 0.5f)) {
                                Text(artikel.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    geld(artikel.preisCent) +
                                        (if (artikel.pfandCent > 0) " + ${geld(artikel.pfandCent)} Pfand" else "") +
                                        (if (!artikel.sichtbar) " · ausgeblendet" else ""),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = artikel.sichtbar,
                                onCheckedChange = { an ->
                                    store.vereinAendern(verein.id) { v ->
                                        v.copy(artikel = v.artikel.map { if (it.id == artikel.id) it.copy(sichtbar = an) else it })
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GruppenReiter(store: KassenStore, verein: Verein, onBearbeiten: (Gruppe) -> Unit) {
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { von, nach ->
        val vonIndex = verein.gruppen.indexOfFirst { it.id == von.key }
        val nachIndex = verein.gruppen.indexOfFirst { it.id == nach.key }
        if (vonIndex >= 0 && nachIndex >= 0) {
            store.vereinAendern(verein.id) { v ->
                v.copy(gruppen = v.gruppen.toMutableList().apply { add(nachIndex, removeAt(vonIndex)) })
            }
        }
    }
    Column(Modifier.fillMaxSize()) {
        Hinweis(
            if (verein.gruppen.isEmpty()) "Noch keine Gruppen. Gruppen fassen Artikel zusammen (z. B. Getränke, Essen, Kuchen) und geben den Kacheln ihre Farbe."
            else "Die Reihenfolge hier bestimmt die Reihenfolge an der Kasse. Zum Sortieren am ☰-Symbol ziehen."
        )
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(verein.gruppen, key = { it.id }) { gruppe ->
                ReorderableItem(reorderState, key = gruppe.id) { zieht ->
                    Card(
                        elevation = CardDefaults.cardElevation(defaultElevation = if (zieht) 8.dp else 1.dp),
                        modifier = Modifier.fillMaxWidth().clickable { onBearbeiten(gruppe) },
                    ) {
                        Row(Modifier.padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {}, modifier = Modifier.draggableHandle()) {
                                Icon(Icons.Default.Menu, contentDescription = "Verschieben")
                            }
                            FarbPunkt(gruppe.farbe.alsFarbe(), 22)
                            Spacer(Modifier.width(12.dp))
                            Text(gruppe.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(vertical = 14.dp))
                            Text(
                                "${verein.artikel.count { it.gruppeId == gruppe.id }} Artikel",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VeranstaltungenReiter(store: KassenStore, verein: Verein, onBearbeiten: (Veranstaltung) -> Unit) {
    val liste = verein.veranstaltungen.sortedByDescending { it.erstelltAm }
    LazyColumn(
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Text(
                if (liste.isEmpty()) "Noch keine Veranstaltung. Jede Veranstaltung (z. B. Sommerfest 2026) hat eigene Bestellungen und eine eigene Auswertung."
                else "Die markierte Veranstaltung ist an der Kasse aktiv. Antippen zum Bearbeiten.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(4.dp),
            )
        }
        items(liste, key = { it.id }) { v ->
            val gueltig = v.bestellungen.filter { !it.storniert }
            Card(Modifier.fillMaxWidth().clickable { onBearbeiten(v) }) {
                Row(Modifier.padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = verein.aktiveVeranstaltungId == v.id,
                        onClick = { store.vereinAendern(verein.id) { it.copy(aktiveVeranstaltungId = v.id) } },
                    )
                    Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                        Text(v.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${datum(v.erstelltAm)} · ${gueltig.size} Bestellungen · ${geld(gueltig.sumOf { it.positionen.summeCent() })}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ArtikelDialog(
    artikel: Artikel,
    gruppen: List<Gruppe>,
    onSpeichern: (Artikel, weiterer: Boolean) -> Unit,
    onLoeschen: (() -> Unit)?,
    onAbbrechen: () -> Unit,
) {
    // key: Felder neu initialisieren, wenn nach "Speichern + weiterer" ein neuer Artikel kommt
    androidx.compose.runtime.key(artikel) {
        var name by remember { mutableStateOf(artikel.name) }
        var preis by remember { mutableStateOf<Long?>(if (artikel.id.isEmpty()) null else artikel.preisCent) }
        var pfand by remember { mutableStateOf<Long?>(artikel.pfandCent) }
        var gruppeId by remember { mutableStateOf(artikel.gruppeId) }
        var sichtbar by remember { mutableStateOf(artikel.sichtbar) }
        var loeschenFragen by remember { mutableStateOf(false) }
        val neu = artikel.id.isEmpty()
        val gueltig = name.isNotBlank() && preis != null && (preis ?: 0) >= 0 && (pfand ?: 0) >= 0
        fun ergebnis() = artikel.copy(name = name.trim(), preisCent = preis ?: 0, pfandCent = pfand ?: 0, gruppeId = gruppeId, sichtbar = sichtbar)

        AlertDialog(
            onDismissRequest = onAbbrechen,
            title = { Text(if (neu) "Neuer Artikel" else "Artikel bearbeiten") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextFeld("Name", name, { name = it }, Modifier.fillMaxWidth(), autoFokus = neu)
                    BetragFeld("Preis", if (neu) null else artikel.preisCent, { preis = it }, Modifier.fillMaxWidth(), leerErlaubt = false)
                    BetragFeld("Pfand (optional)", artikel.pfandCent.takeIf { it != 0L }, { pfand = it ?: 0 }, Modifier.fillMaxWidth())
                    if (gruppen.isNotEmpty()) {
                        Text("Gruppe", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (g in gruppen) {
                                FilterChip(
                                    selected = gruppeId == g.id,
                                    onClick = { gruppeId = g.id },
                                    label = { Text(g.name) },
                                    leadingIcon = { FarbPunkt(g.farbe.alsFarbe()) },
                                )
                            }
                            FilterChip(selected = gruppeId == null, onClick = { gruppeId = null }, label = { Text("Keine") })
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("An der Kasse sichtbar", modifier = Modifier.weight(1f))
                        Switch(checked = sichtbar, onCheckedChange = { sichtbar = it })
                    }
                    if (neu) {
                        TextButton(onClick = { onSpeichern(ergebnis(), true) }, enabled = gueltig) {
                            Text("Speichern und weiteren Artikel anlegen")
                        }
                    }
                    if (onLoeschen != null) {
                        TextButton(onClick = { loeschenFragen = true }) {
                            Text("Artikel löschen", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { onSpeichern(ergebnis(), false) }, enabled = gueltig) { Text("Speichern") } },
            dismissButton = { TextButton(onClick = onAbbrechen) { Text("Abbrechen") } },
        )

        if (loeschenFragen && onLoeschen != null) {
            BestaetigenDialog(
                titel = "Artikel löschen?",
                text = "„${artikel.name}“ wird gelöscht. Bisherige Bestellungen bleiben in der Auswertung erhalten.\n\nTipp: Zum vorübergehenden Entfernen (z. B. ausverkauft) besser ausblenden.",
                bestaetigen = "Löschen",
                onBestaetigen = { loeschenFragen = false; onLoeschen() },
                onAbbrechen = { loeschenFragen = false },
            )
        }
    }
}

@Composable
private fun GruppeDialog(
    gruppe: Gruppe,
    anzahlArtikel: Int,
    onSpeichern: (Gruppe) -> Unit,
    onLoeschen: (() -> Unit)?,
    onAbbrechen: () -> Unit,
) {
    var name by remember { mutableStateOf(gruppe.name) }
    var farbe by remember { mutableStateOf(gruppe.farbe) }
    var loeschenFragen by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onAbbrechen,
        title = { Text(if (gruppe.id.isEmpty()) "Neue Gruppe" else "Gruppe bearbeiten") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextFeld("Name", name, { name = it }, Modifier.fillMaxWidth(), autoFokus = gruppe.id.isEmpty())
                Text("Farbe", style = MaterialTheme.typography.labelLarge)
                FarbAuswahl(farbe) { farbe = it }
                if (onLoeschen != null) {
                    TextButton(onClick = { loeschenFragen = true }) {
                        Text("Gruppe löschen", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSpeichern(gruppe.copy(name = name.trim(), farbe = farbe)) }, enabled = name.isNotBlank()) {
                Text("Speichern")
            }
        },
        dismissButton = { TextButton(onClick = onAbbrechen) { Text("Abbrechen") } },
    )
    if (loeschenFragen && onLoeschen != null) {
        BestaetigenDialog(
            titel = "Gruppe löschen?",
            text = "„${gruppe.name}“ wird gelöscht." +
                if (anzahlArtikel > 0) " Die $anzahlArtikel Artikel bleiben erhalten und sind danach ohne Gruppe." else "",
            bestaetigen = "Löschen",
            onBestaetigen = { loeschenFragen = false; onLoeschen() },
            onAbbrechen = { loeschenFragen = false },
        )
    }
}
