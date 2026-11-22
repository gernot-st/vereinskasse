package de.vereinskasse.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.vereinskasse.logik.Plattform
import de.vereinskasse.logik.Verlauf
import de.vereinskasse.logik.artikelSortiert
import de.vereinskasse.logik.dateiname
import de.vereinskasse.logik.verlauf
import de.vereinskasse.model.Bestellung
import de.vereinskasse.model.Verein
import kotlinx.coroutines.launch

/** Nimmt die ersten freien Farbplätze für die Namen – bereits vergebene Farben bleiben erhalten. */
private fun Map<String, Int>.mit(namen: List<String>): Map<String, Int> {
    val ergebnis = LinkedHashMap(this)
    for (name in namen) {
        if (name in ergebnis) continue
        if (ergebnis.size >= MAX_SERIEN) break
        val frei = (0 until MAX_SERIEN).first { p -> p !in ergebnis.values }
        ergebnis[name] = frei
    }
    return ergebnis
}

private fun top5(verlauf: Verlauf, kandidaten: List<String>): Map<String, Int> {
    val beste = kandidaten.sortedByDescending { verlauf.gesamt(it) }.take(5).toSet()
    return emptyMap<String, Int>().mit(kandidaten.filter { it in beste })
}

/** Abschnitt "Verkaufsverlauf": Artikelauswahl, Umschalter, Diagramm, Teilen als Bild. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VerlaufAbschnitt(
    verein: Verein,
    titel: String,
    bestellungen: List<Bestellung>,
    mehrereGeraete: Boolean,
    fehlendeGeraete: List<String>,
    plattform: Plattform,
    logo: androidx.compose.ui.graphics.ImageBitmap? = null,
    startArt: DiagrammArt = DiagrammArt.HOCHLAUF,
    startGewaehlt: Int? = null,
) {
    var art by remember { mutableStateOf(startArt) }
    var mitHelfer by remember { mutableStateOf(true) }
    var auswahl by remember { mutableStateOf<Map<String, Int>?>(null) }
    var gewaehlt by remember { mutableStateOf(startGewaehlt) }
    val bild = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()

    val verlauf = remember(bestellungen, mitHelfer) { bestellungen.verlauf(mitHelfer) }
    if (gewaehlt != null && gewaehlt!! >= verlauf.anzahl) gewaehlt = null
    // Kandidaten in Kassen-Reihenfolge; unbekannte (gelöschte/fremde) Artikel ans Ende
    val reihenfolge = remember(verein) { verein.artikelSortiert().mapIndexed { i, a -> a.name to i }.toMap() }
    val kandidaten = verlauf.mengen.keys.sortedWith(compareBy({ reihenfolge[it] ?: Int.MAX_VALUE }, { it }))
    val aktuelleAuswahl = auswahl ?: top5(verlauf, kandidaten)
    val serien = kandidaten.filter { it in aktuelleAuswahl }.map { Serie(it, aktuelleAuswahl.getValue(it)) }

    Abschnitt("Verkaufsverlauf (Viertelstunden)") {
        if (verlauf.anzahl == 0) {
            Text("Noch keine Verkäufe.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Abschnitt
        }

        // Darstellung
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = art == DiagrammArt.HOCHLAUF, onClick = { art = DiagrammArt.HOCHLAUF }, label = { Text("Hochlauf") })
            FilterChip(selected = art == DiagrammArt.BALKEN, onClick = { art = DiagrammArt.BALKEN }, label = { Text("Pro Viertelstunde") })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Helferbuchungen mitzählen", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(checked = mitHelfer, onCheckedChange = { mitHelfer = it })
        }

        // Artikelauswahl
        Text(
            "Artikel (max. $MAX_SERIEN)",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 4.dp),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AssistChip(onClick = { auswahl = top5(verlauf, kandidaten) }, label = { Text("Top 5") })
            AssistChip(onClick = { auswahl = emptyMap<String, Int>().mit(kandidaten) }, label = {
                Text(if (kandidaten.size > MAX_SERIEN) "Erste $MAX_SERIEN" else "Alle")
            })
            AssistChip(onClick = { auswahl = emptyMap() }, label = { Text("Keine") })
            for (g in verein.gruppen) {
                val inGruppe = verein.artikel.filter { it.gruppeId == g.id }.map { it.name }.filter { it in kandidaten }
                if (inGruppe.isNotEmpty()) {
                    AssistChip(
                        onClick = { auswahl = emptyMap<String, Int>().mit(inGruppe) },
                        label = { Text(g.name) },
                        leadingIcon = { FarbPunkt(g.farbe.alsFarbe(), 10) },
                    )
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (name in kandidaten) {
                val platz = aktuelleAuswahl[name]
                val voll = aktuelleAuswahl.size >= MAX_SERIEN
                FilterChip(
                    selected = platz != null,
                    enabled = platz != null || !voll,
                    onClick = {
                        auswahl = if (platz != null) aktuelleAuswahl - name else aktuelleAuswahl.mit(listOf(name))
                    },
                    label = { Text("$name (${verlauf.gesamt(name)})") },
                    leadingIcon = if (platz != null) ({ FarbPunkt(serienFarbe(platz), 12) }) else null,
                )
            }
        }

        // Diagramm (dieser Teil wird als Bild geteilt)
        Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Column(
                Modifier
                    .drawWithContent {
                        bild.record { this@drawWithContent.drawContent() }
                        drawLayer(bild)
                    }
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LogoBild(logo, 44.dp, Modifier.padding(end = 10.dp))
                    Column {
                        Text("Verkaufsverlauf – $titel", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            (if (art == DiagrammArt.HOCHLAUF) "Verkaufte Stück, aufsummiert" else "Verkaufte Stück pro Viertelstunde") +
                                (if (mehrereGeraete) " · alle Geräte" else "") +
                                (if (!mitHelfer) " · ohne Helfer" else ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (serien.isEmpty()) {
                    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        Text("Bitte oben Artikel auswählen.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    VerlaufDiagramm(verlauf, serien, art, gewaehlt, { gewaehlt = it })
                    // Legende: Farbe + Name + Stück (Text in Textfarbe, Farbe nur an der Marke)
                    FlowRow(
                        Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        for (s in serien) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(width = 14.dp, height = if (art == DiagrammArt.HOCHLAUF) 3.dp else 10.dp)
                                    .background(serienFarbe(s.platz), RoundedCornerShape(2.dp)))
                                Spacer(Modifier.width(6.dp))
                                Text("${s.name} ${verlauf.gesamt(s.name)}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }

        // Details zur angetippten Viertelstunde
        val i = gewaehlt
        if (i != null && serien.isNotEmpty()) {
            Card(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(viertelstundeText(verlauf, i), style = MaterialTheme.typography.titleSmall)
                    for (s in serien) {
                        val inViertel = verlauf.mengen[s.name]?.getOrNull(i) ?: 0
                        val bisDahin = verlauf.kumuliert(s.name).getOrNull(i) ?: 0
                        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            FarbPunkt(serienFarbe(s.platz), 10)
                            Spacer(Modifier.width(8.dp))
                            Text(s.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text("+$inViertel", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(56.dp))
                            Text("bis dahin $bisDahin", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        } else if (serien.isNotEmpty()) {
            Text(
                "Tipp: Ins Diagramm tippen oder wischen, um die Werte einer Viertelstunde zu sehen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        if (fehlendeGeraete.isNotEmpty()) {
            Text(
                "Nicht enthalten (ältere App-Version ohne Uhrzeiten): ${fehlendeGeraete.joinToString()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        OutlinedButton(
            onClick = {
                scope.launch {
                    try {
                        plattform.teileBild(dateiname(titel, "Verlauf").removeSuffix(".json") + ".png", bild.toImageBitmap())
                    } catch (_: Exception) {
                    }
                }
            },
            enabled = serien.isNotEmpty(),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Icon(Icons.Default.Share, null)
            Spacer(Modifier.width(8.dp))
            Text("Grafik teilen")
        }
    }
}
