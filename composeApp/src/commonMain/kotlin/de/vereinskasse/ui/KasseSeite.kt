package de.vereinskasse.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.vereinskasse.logik.KassenStore
import de.vereinskasse.logik.anzahlArtikel
import de.vereinskasse.logik.artikelHinzufuegen
import de.vereinskasse.logik.artikelSortiert
import de.vereinskasse.logik.geld
import de.vereinskasse.logik.hinzufuegen
import de.vereinskasse.logik.jetzt
import de.vereinskasse.logik.letzteBestellung
import de.vereinskasse.logik.mengeAendern
import de.vereinskasse.logik.neueId
import de.vereinskasse.logik.pfandRueckgabeHinzufuegen
import de.vereinskasse.logik.pfandUmschalten
import de.vereinskasse.logik.summeCent
import de.vereinskasse.logik.uhrzeit
import de.vereinskasse.model.Artikel
import de.vereinskasse.model.Bestellung
import de.vereinskasse.model.Position
import de.vereinskasse.model.Veranstaltung
import de.vereinskasse.model.Verein
import kotlinx.coroutines.launch

/** Zustand der Kasse, der beim Wechsel zwischen Seiten erhalten bleibt. */
class KassenZustand {
    var korb by mutableStateOf(emptyList<Position>())
    var helfer by mutableStateOf(false)
    var mitPfand by mutableStateOf(true)
    var gruppenFilter by mutableStateOf<String?>(null)
    var korbAusgeklappt by mutableStateOf(false)
    var fuerVeranstaltung by mutableStateOf<String?>(null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KasseSeite(
    store: KassenStore,
    zustand: KassenZustand,
    oeffne: (Seite) -> Unit,
    bezahlenStartOffen: Boolean = false,
) {
    val verein = store.aktiverVerein
    val veranstaltung = store.aktiveVeranstaltung
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var menueOffen by remember { mutableStateOf(false) }
    var bezahlenOffen by remember { mutableStateOf(bezahlenStartOffen) }
    var stornoOffen by remember { mutableStateOf(false) }
    var neueVeranstaltungOffen by remember { mutableStateOf(false) }

    // Warenkorb leeren, wenn die Veranstaltung gewechselt wurde
    if (zustand.fuerVeranstaltung != veranstaltung?.id) {
        zustand.fuerVeranstaltung = veranstaltung?.id
        zustand.korb = emptyList()
        zustand.helfer = false
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                colors = kopfzeile(),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                    LogoBild(store.logoBild(verein?.logoId), 40.dp, Modifier.padding(end = 10.dp))
                    Column {
                        Text(verein?.name ?: "Vereinskasse", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (veranstaltung != null) {
                            Text(
                                veranstaltung.name,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    }
                },
                actions = {
                    if (veranstaltung != null) {
                        IconButton(onClick = { oeffne(Seite.Auswertung) }) {
                            Icon(Icons.Default.List, contentDescription = "Auswertung")
                        }
                    }
                    if (verein != null) {
                        IconButton(onClick = { oeffne(Seite.VereinBearbeiten(verein.id)) }) {
                            Icon(Icons.Default.Settings, contentDescription = "Artikel verwalten")
                        }
                    }
                    Box {
                        IconButton(onClick = { menueOffen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menü")
                        }
                        DropdownMenu(expanded = menueOffen, onDismissRequest = { menueOffen = false }) {
                            if (veranstaltung != null) {
                                DropdownMenuItem(
                                    text = { Text("Letzte Bestellung stornieren") },
                                    onClick = { menueOffen = false; stornoOffen = true },
                                )
                                DropdownMenuItem(
                                    text = { Text("Auswertung") },
                                    onClick = { menueOffen = false; oeffne(Seite.Auswertung) },
                                )
                            }
                            if (verein != null) {
                                DropdownMenuItem(
                                    text = { Text("Artikel & Veranstaltungen") },
                                    onClick = { menueOffen = false; oeffne(Seite.VereinBearbeiten(verein.id)) },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Vereine, Import & Export") },
                                onClick = { menueOffen = false; oeffne(Seite.Vereine) },
                            )
                        }
                    }
                },
            )
        },
    ) { innen ->
        Box(Modifier.padding(innen).fillMaxSize()) {
            when {
                verein == null -> LeerHinweis(
                    "Noch kein Verein ausgewählt.",
                    "Vereine verwalten",
                ) { oeffne(Seite.Vereine) }

                veranstaltung == null -> LeerHinweis(
                    "Für „${verein.name}“ ist keine Veranstaltung aktiv.\nLege eine Veranstaltung an, um zu kassieren.",
                    "Veranstaltung anlegen",
                ) { neueVeranstaltungOffen = true }

                verein.artikel.none { it.sichtbar } -> LeerHinweis(
                    "Es sind noch keine Artikel angelegt (oder alle ausgeblendet).",
                    "Artikel verwalten",
                ) { oeffne(Seite.VereinBearbeiten(verein.id)) }

                else -> KassenInhalt(
                    verein = verein,
                    veranstaltung = veranstaltung,
                    zustand = zustand,
                    onBezahlen = { bezahlenOffen = true },
                )
            }
        }
    }

    if (bezahlenOffen && verein != null && veranstaltung != null) {
        BezahlenDialog(
            summeCent = zustand.korb.summeCent(),
            onAbbrechen = { bezahlenOffen = false },
            onBuchen = { gegeben ->
                val summe = zustand.korb.summeCent()
                val bestellung = Bestellung(neueId(), jetzt(), zustand.korb, gegeben)
                store.veranstaltungAendern(verein.id, veranstaltung.id) {
                    it.copy(bestellungen = it.bestellungen + bestellung)
                }
                zustand.korb = emptyList()
                zustand.helfer = false
                zustand.korbAusgeklappt = false
                bezahlenOffen = false
                val text = buildString {
                    append(if (summe < 0) "Ausgezahlt: ${geld(-summe)}" else "Gebucht: ${geld(summe)}")
                    if (gegeben != null && gegeben > summe) append(" · Rückgeld ${geld(gegeben - summe)}")
                }
                scope.launch {
                    snackbar.currentSnackbarData?.dismiss()
                    val ergebnis = snackbar.showSnackbar(text, actionLabel = "Stornieren", duration = SnackbarDuration.Short)
                    if (ergebnis == SnackbarResult.ActionPerformed) stornoOffen = true
                }
            },
        )
    }

    if (stornoOffen && verein != null && veranstaltung != null) {
        StornoDialog(
            veranstaltung = veranstaltung,
            onStornieren = { bestellung ->
                store.veranstaltungAendern(verein.id, veranstaltung.id) { v ->
                    v.copy(bestellungen = v.bestellungen.map { if (it.id == bestellung.id) it.copy(storniert = true) else it })
                }
                stornoOffen = false
                scope.launch { snackbar.showSnackbar("Bestellung über ${geld(bestellung.positionen.summeCent())} storniert") }
            },
            onAbbrechen = { stornoOffen = false },
        )
    }

    if (neueVeranstaltungOffen && verein != null) {
        VeranstaltungDialog(
            vorhanden = null,
            onSpeichern = { name, anfang ->
                val neu = Veranstaltung(neueId(), name, jetzt(), anfangsbestandCent = anfang)
                store.vereinAendern(verein.id) {
                    it.copy(veranstaltungen = it.veranstaltungen + neu, aktiveVeranstaltungId = neu.id)
                }
                neueVeranstaltungOffen = false
            },
            onLoeschen = null,
            onAbbrechen = { neueVeranstaltungOffen = false },
        )
    }
}

@Composable
private fun LeerHinweis(text: String, knopf: String, onKlick: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onKlick) { Text(knopf) }
    }
}

@Composable
private fun KassenInhalt(
    verein: Verein,
    veranstaltung: Veranstaltung,
    zustand: KassenZustand,
    onBezahlen: () -> Unit,
) {
    val sichtbareArtikel = remember(verein) { verein.artikelSortiert().filter { it.sichtbar } }
    val gruppenMitArtikeln = remember(verein, sichtbareArtikel) {
        verein.gruppen.filter { g -> sichtbareArtikel.any { it.gruppeId == g.id } }
    }
    if (zustand.gruppenFilter != null && gruppenMitArtikeln.none { it.id == zustand.gruppenFilter }) {
        zustand.gruppenFilter = null
    }
    val angezeigt = if (zustand.gruppenFilter == null) sichtbareArtikel
    else sichtbareArtikel.filter { it.gruppeId == zustand.gruppenFilter }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val breit = maxWidth >= 700.dp
        if (breit) {
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    GruppenLeiste(verein, gruppenMitArtikeln, zustand)
                    KachelRaster(verein, angezeigt, zustand, breit = true, modifier = Modifier.weight(1f))
                }
                VerticalDivider()
                KorbSeitenleiste(verein, veranstaltung, zustand, onBezahlen, Modifier.width(380.dp).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                GruppenLeiste(verein, gruppenMitArtikeln, zustand)
                KachelRaster(verein, angezeigt, zustand, breit = false, modifier = Modifier.weight(1f))
                KorbUnten(verein, veranstaltung, zustand, onBezahlen)
            }
        }
    }
}

@Composable
private fun GruppenLeiste(verein: Verein, gruppen: List<de.vereinskasse.model.Gruppe>, zustand: KassenZustand) {
    if (gruppen.size < 2) return
    LazyRow(
        contentPadding = PaddingValues(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(
                selected = zustand.gruppenFilter == null,
                onClick = { zustand.gruppenFilter = null },
                label = { Text("Alle") },
            )
        }
        items(gruppen, key = { it.id }) { g ->
            FilterChip(
                selected = zustand.gruppenFilter == g.id,
                onClick = { zustand.gruppenFilter = g.id },
                label = { Text(g.name) },
                leadingIcon = { FarbPunkt(g.farbe.alsFarbe()) },
            )
        }
    }
}

@Composable
private fun KachelRaster(
    verein: Verein,
    artikel: List<Artikel>,
    zustand: KassenZustand,
    breit: Boolean,
    modifier: Modifier,
) {
    val farben = remember(verein) { verein.gruppen.associate { it.id to it.farbe.alsFarbe() } }
    val mengen = zustand.korb.filter { it.artikelId != null }.groupBy { it.artikelId }
        .mapValues { (_, z) -> z.sumOf { it.menge } }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (breit) 150.dp else 104.dp),
        contentPadding = PaddingValues(start = 8.dp, end = 12.dp, top = 10.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        items(artikel, key = { it.id }) { a ->
            Kachel(
                artikel = a,
                farbe = farben[a.gruppeId] ?: MaterialTheme.colorScheme.secondary,
                menge = mengen[a.id] ?: 0,
                helfer = zustand.helfer,
                hoehe = if (breit) 110 else 100,
            ) {
                zustand.korb = zustand.korb.artikelHinzufuegen(a, zustand.helfer, zustand.mitPfand)
            }
        }
    }
}

@Composable
private fun Kachel(artikel: Artikel, farbe: Color, menge: Int, helfer: Boolean, hoehe: Int, onKlick: () -> Unit) {
    val textFarbe = farbe.textFarbe()
    // Lange Wörter (z. B. "Käsekuchen") verkleinern, damit sie nicht mitten im Wort umbrechen
    val laengstesWort = artikel.name.split(' ').maxOf { it.length }
    val schrift = when {
        laengstesWort > 12 -> 12.sp
        laengstesWort > 9 -> 14.sp
        else -> 17.sp
    }
    Box {
        Surface(
            color = farbe,
            shape = RoundedCornerShape(14.dp),
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth().height(hoehe.dp).clickable(onClick = onKlick),
        ) {
            Column(
                Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    artikel.name,
                    color = textFarbe,
                    fontWeight = FontWeight.Bold,
                    fontSize = schrift,
                    lineHeight = schrift * 1.15f,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (helfer) "Helfer" else geld(artikel.preisCent),
                    color = textFarbe,
                    fontSize = 15.sp,
                    maxLines = 1,
                )
                if (artikel.pfandCent > 0) {
                    Text(
                        "+ ${geld(artikel.pfandCent)} Pfand",
                        color = textFarbe.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        maxLines = 1,
                    )
                }
            }
        }
        if (menge > 0) {
            Box(
                Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-6).dp).size(26.dp)
                    .background(MaterialTheme.colorScheme.surface, CircleShape)
                    .border(2.dp, farbe, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(menge.toString(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

/** Optionen: Pfand an/aus, Helfer, Pfand-Rückgabe, letzte Bestellung wiederholen. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KassenOptionen(
    verein: Verein,
    veranstaltung: Veranstaltung,
    zustand: KassenZustand,
    modifier: Modifier = Modifier,
    einzeilig: Boolean = false,
) {
    val pfandBetraege = remember(verein) {
        verein.artikel.filter { it.sichtbar && it.pfandCent > 0 }.map { it.pfandCent }.distinct().sorted()
    }
    val letzte = veranstaltung.bestellungen.filter { !it.storniert }.maxByOrNull { it.zeit }
    val inhalt: @Composable () -> Unit = {
        if (pfandBetraege.isNotEmpty()) {
            FilterChip(
                selected = zustand.mitPfand,
                onClick = { zustand.mitPfand = !zustand.mitPfand },
                label = { Text(if (zustand.mitPfand) "Pfand: an" else "Pfand: aus") },
            )
        }
        FilterChip(
            selected = zustand.helfer,
            onClick = { zustand.helfer = !zustand.helfer },
            label = { Text("Helfer") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ),
        )
        for (betrag in pfandBetraege) {
            AssistChip(
                onClick = { zustand.korb = zustand.korb.pfandRueckgabeHinzufuegen(betrag) },
                label = { Text(if (pfandBetraege.size == 1) "Pfand zurück" else "Pfand zurück ${geld(betrag)}") },
            )
        }
        if (letzte != null) {
            AssistChip(
                onClick = { zustand.korb = letzte.positionen.fold(zustand.korb) { korb, p -> korb.hinzufuegen(p) } },
                label = { Text("Wiederholen") },
                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null, Modifier.size(18.dp)) },
            )
        }
    }
    if (einzeilig) {
        Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { inhalt() }
    } else {
        FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) { inhalt() }
    }
}

@Composable
private fun KorbListe(verein: Verein, zustand: KassenZustand, modifier: Modifier = Modifier) {
    val artikelNachId = remember(verein) { verein.artikel.associateBy { it.id } }
    LazyColumn(modifier) {
        itemsIndexed(zustand.korb) { index, p ->
            val artikel = p.artikelId?.let { artikelNachId[it] }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (p.helfer) {
                            Text(
                                "Helfer",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp),
                            )
                        }
                        if (artikel != null && artikel.pfandCent > 0) {
                            Text(
                                if (p.pfandCent > 0) "inkl. Pfand" else "ohne Pfand",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                textDecoration = TextDecoration.Underline,
                                modifier = Modifier.clickable { zustand.korb = zustand.korb.pfandUmschalten(index, artikel) }
                                    .padding(vertical = 2.dp),
                            )
                        } else if (!p.pfandRueckgabe) {
                            Text(geld(p.einzelpreisCent), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                IconButton(onClick = { zustand.korb = zustand.korb.mengeAendern(index, -1) }, Modifier.size(40.dp)) {
                    Icon(
                        if (p.menge == 1) Icons.Default.Delete else Icons.Default.KeyboardArrowDown,
                        contentDescription = "weniger",
                    )
                }
                Text(
                    "${p.menge}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.width(28.dp),
                    textAlign = TextAlign.Center,
                )
                IconButton(onClick = { zustand.korb = zustand.korb.mengeAendern(index, 1) }, Modifier.size(40.dp)) {
                    Icon(Icons.Default.Add, contentDescription = "mehr")
                }
                Text(
                    geld(p.summeCent),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(82.dp),
                )
            }
        }
    }
}

@Composable
private fun SummeUndBezahlen(zustand: KassenZustand, onBezahlen: () -> Unit, modifier: Modifier = Modifier) {
    val summe = zustand.korb.summeCent()
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                "${zustand.korb.anzahlArtikel()} Artikel",
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                geld(summe),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        if (zustand.korb.isNotEmpty()) {
            IconButton(onClick = { zustand.korb = emptyList() }) {
                Icon(Icons.Default.Delete, contentDescription = "Bestellung leeren")
            }
        }
        Button(
            onClick = onBezahlen,
            enabled = zustand.korb.isNotEmpty(),
            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp),
            colors = if (zustand.helfer) ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiary,
            ) else ButtonDefaults.buttonColors(),
        ) {
            Text(if (summe < 0) "Auszahlen" else "Kassieren", fontSize = 18.sp)
        }
    }
}

@Composable
private fun KorbUnten(verein: Verein, veranstaltung: Veranstaltung, zustand: KassenZustand, onBezahlen: () -> Unit) {
    Surface(tonalElevation = 3.dp, shadowElevation = 6.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            KassenOptionen(verein, veranstaltung, zustand, einzeilig = true)
            if (zustand.korb.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().clickable { zustand.korbAusgeklappt = !zustand.korbAusgeklappt }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (zustand.korbAusgeklappt) "Bestellung ausblenden" else "Bestellung anzeigen (${zustand.korb.size} Zeilen)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        if (zustand.korbAusgeklappt) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                if (zustand.korbAusgeklappt) {
                    KorbListe(verein, zustand, Modifier.heightIn(max = 260.dp))
                    HorizontalDivider()
                }
            }
            SummeUndBezahlen(zustand, onBezahlen, Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun KorbSeitenleiste(
    verein: Verein,
    veranstaltung: Veranstaltung,
    zustand: KassenZustand,
    onBezahlen: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier.padding(12.dp)) {
        Text("Bestellung", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        KassenOptionen(verein, veranstaltung, zustand)
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        if (zustand.korb.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "Artikel antippen, um sie hinzuzufügen",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            KorbListe(verein, zustand, Modifier.weight(1f))
        }
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        SummeUndBezahlen(zustand, onBezahlen)
    }
}

@Composable
private fun StornoDialog(veranstaltung: Veranstaltung, onStornieren: (Bestellung) -> Unit, onAbbrechen: () -> Unit) {
    val letzte = veranstaltung.letzteBestellung()
    when {
        letzte == null -> HinweisDialog("Stornieren", "Es gibt noch keine Bestellung.", onAbbrechen)
        letzte.storniert -> HinweisDialog(
            "Stornieren",
            "Die letzte Bestellung (${uhrzeit(letzte.zeit)} Uhr, ${geld(letzte.positionen.summeCent())}) ist bereits storniert.",
            onAbbrechen,
        )
        else -> androidx.compose.material3.AlertDialog(
            onDismissRequest = onAbbrechen,
            title = { Text("Letzte Bestellung stornieren?") },
            text = {
                Column {
                    Text("${uhrzeit(letzte.zeit)} Uhr", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    for (p in letzte.positionen) {
                        WertZeile("${p.menge}× ${p.name}${if (p.helfer) " (Helfer)" else ""}", geld(p.summeCent))
                    }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    WertZeile("Summe", geld(letzte.positionen.summeCent()), hervorheben = true)
                    if (letzte.positionen.summeCent() > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text("Betrag an den Kunden zurückgeben.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { onStornieren(letzte) }) { Text("Stornieren") }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = onAbbrechen) { Text("Abbrechen") } },
        )
    }
}
