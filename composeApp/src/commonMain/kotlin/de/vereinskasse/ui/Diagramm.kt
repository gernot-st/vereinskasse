package de.vereinskasse.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.vereinskasse.logik.VIERTELSTUNDE_MS
import de.vereinskasse.logik.Verlauf
import de.vereinskasse.logik.uhrzeit
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * Kategorische Farben in fester Reihenfolge (validiert auf Farbenblindheit, je Hell/Dunkel).
 * Eine Farbe bleibt an einem Artikel, solange er ausgewählt ist.
 */
private val SERIEN_HELL = listOf(
    0xFF2A78D6, 0xFFEB6834, 0xFF1BAF7A, 0xFFEDA100, 0xFFE87BA4, 0xFF008300, 0xFF6250D6, 0xFFE34948,
)
private val SERIEN_DUNKEL = listOf(
    0xFF3987E5, 0xFFD95926, 0xFF199E70, 0xFFC98500, 0xFFD55181, 0xFF0CA30C, 0xFF9085E9, 0xFFE66767,
)
const val MAX_SERIEN = 8

@Composable
fun serienFarbe(platz: Int): Color {
    val dunkel = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return Color((if (dunkel) SERIEN_DUNKEL else SERIEN_HELL)[platz % MAX_SERIEN])
}

enum class DiagrammArt { HOCHLAUF, BALKEN }

/** Ein im Diagramm gezeigter Artikel mit seinem festen Farbplatz. */
data class Serie(val name: String, val platz: Int)

/** Runde Achsenschritte: 1, 2, 5, 10, 20, 50 … */
private fun schoenerSchritt(maximum: Int, ziel: Int = 4): Int {
    if (maximum <= ziel) return 1
    val roh = maximum.toDouble() / ziel
    val basis = 10.0.pow(floor(log10(roh)))
    val schritt = listOf(1.0, 2.0, 5.0, 10.0).first { it * basis >= roh } * basis
    return schritt.toInt().coerceAtLeast(1)
}

/** Text zur Grenze i (Beginn der Viertelstunde i bzw. Ende der letzten). */
private fun grenzZeit(verlauf: Verlauf, i: Int): Long? = when {
    i < verlauf.anzahl -> verlauf.zeitpunkte[i]
    else -> verlauf.zeitpunkte.lastOrNull()?.plus(VIERTELSTUNDE_MS)
}

fun viertelstundeText(verlauf: Verlauf, i: Int): String {
    val start = verlauf.zeitpunkte.getOrNull(i) ?: return "Pause"
    return "${uhrzeit(start)}–${uhrzeit(start + VIERTELSTUNDE_MS)} Uhr"
}

/**
 * Verlaufsdiagramm: Hochlauf (kumulierte Linien) oder gestapelte Balken pro Viertelstunde.
 * Antippen oder Wischen wählt eine Viertelstunde ([gewaehlt]).
 */
@Composable
fun VerlaufDiagramm(
    verlauf: Verlauf,
    serien: List<Serie>,
    art: DiagrammArt,
    gewaehlt: Int?,
    onWahl: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    hoehe: Dp = 260.dp,
) {
    val textMesser = rememberTextMeasurer()
    val farben = serien.map { serienFarbe(it.platz) }
    val flaeche = MaterialTheme.colorScheme.surface
    val achsenText = TextStyle(fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val labelText = TextStyle(fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
    val gitter = MaterialTheme.colorScheme.outlineVariant
    val achse = MaterialTheme.colorScheme.outline
    val markierung = MaterialTheme.colorScheme.onSurface
    val dichte = LocalDensity.current

    val n = verlauf.anzahl.coerceAtLeast(1)
    val reihen = serien.map { s -> verlauf.mengen[s.name] ?: List(verlauf.anzahl) { 0 } }
    val kumuliert = serien.map { verlauf.kumuliert(it.name).ifEmpty { List(verlauf.anzahl) { 0 } } }
    val maximum = when (art) {
        DiagrammArt.HOCHLAUF -> kumuliert.maxOfOrNull { it.lastOrNull() ?: 0 } ?: 0
        DiagrammArt.BALKEN -> (0 until verlauf.anzahl).maxOfOrNull { i -> reihen.sumOf { it[i] } } ?: 0
    }.coerceAtLeast(1)
    val schritt = schoenerSchritt(maximum)
    val yMax = (ceil(maximum.toDouble() / schritt) * schritt).toInt()

    // Direkte Beschriftung am Linienende nur bei wenigen Linien
    val direktBeschriften = art == DiagrammArt.HOCHLAUF && serien.size in 1..4

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val breitePx = with(dichte) { maxWidth.toPx() }
        val yLabelBreite = textMesser.measure(yMax.toString(), achsenText).size.width
        val links = yLabelBreite + with(dichte) { 8.dp.toPx() }
        val rechtsRand = if (direktBeschriften) {
            val laengster = serien.maxOf { textMesser.measure(it.name, labelText).size.width }
            (laengster + with(dichte) { 16.dp.toPx() }).coerceAtMost(breitePx * 0.32f)
        } else with(dichte) { 8.dp.toPx() }
        val plotBreite = (breitePx - links - rechtsRand).coerceAtLeast(1f)
        val slotBreite = plotBreite / n

        val aktuelleWahl = rememberUpdatedState(gewaehlt)
        val wahlAendern = rememberUpdatedState(onWahl)
        fun index(x: Float): Int? =
            if (verlauf.anzahl == 0) null else ((x - links) / slotBreite).toInt().coerceIn(0, verlauf.anzahl - 1)

        Canvas(
            Modifier
                .fillMaxWidth()
                .height(hoehe)
                .pointerInput(verlauf, links, slotBreite) {
                    detectTapGestures { pos ->
                        val i = index(pos.x)
                        wahlAendern.value(if (i == aktuelleWahl.value) null else i)
                    }
                }
                .pointerInput(verlauf, links, slotBreite) {
                    detectHorizontalDragGestures { change, _ -> wahlAendern.value(index(change.position.x)) }
                },
        ) {
            val oben = 8.dp.toPx()
            val unten = size.height - 22.dp.toPx()
            val plotHoehe = unten - oben
            fun y(wert: Int) = unten - plotHoehe * wert / yMax
            fun x(grenze: Int) = links + grenze * slotBreite

            // Gitter und y-Achse
            var w = 0
            while (w <= yMax) {
                val yy = y(w)
                drawLine(if (w == 0) achse else gitter.copy(alpha = 0.6f), Offset(links, yy), Offset(links + plotBreite, yy), 1.dp.toPx())
                val t = textMesser.measure(w.toString(), achsenText)
                drawText(t, topLeft = Offset(links - t.size.width - 6.dp.toPx(), yy - t.size.height / 2f))
                w += schritt
            }

            // Pausen (zusammengefasste Zeiträume ohne Verkauf)
            verlauf.zeitpunkte.forEachIndexed { i, zeit ->
                if (zeit == null) {
                    val mitte = x(i) + slotBreite / 2
                    drawLine(
                        gitter, Offset(mitte, oben), Offset(mitte, unten), 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
                    )
                }
            }

            xAchse(verlauf, textMesser, achsenText, links, slotBreite, unten)

            // Gewählte Viertelstunde hinterlegen
            gewaehlt?.takeIf { it < verlauf.anzahl }?.let { i ->
                drawRect(gitter.copy(alpha = 0.35f), Offset(x(i), oben), Size(slotBreite, plotHoehe))
            }

            when (art) {
                DiagrammArt.BALKEN -> {
                    val luecke = if (slotBreite > 6.dp.toPx()) 2.dp.toPx() else 0f
                    for (i in 0 until verlauf.anzahl) {
                        var basis = 0
                        val oberste = reihen.indexOfLast { it[i] > 0 }
                        reihen.forEachIndexed { s, reihe ->
                            val wert = reihe[i]
                            if (wert > 0) {
                                val yUnten = y(basis) - if (basis > 0) luecke else 0f
                                val yOben = y(basis + wert)
                                if (yUnten - yOben > 0.5f) {
                                    val radius = if (s == oberste) minOf(4.dp.toPx(), (slotBreite - luecke) / 2) else 0f
                                    val pfad = Path().apply {
                                        addRoundRect(
                                            RoundRect(
                                                left = x(i) + luecke / 2, top = yOben,
                                                right = x(i + 1) - luecke / 2, bottom = yUnten,
                                                topLeftCornerRadius = CornerRadius(radius),
                                                topRightCornerRadius = CornerRadius(radius),
                                            )
                                        )
                                    }
                                    drawPath(pfad, farben[s])
                                }
                                basis += wert
                            }
                        }
                    }
                }

                DiagrammArt.HOCHLAUF -> {
                    kumuliert.forEachIndexed { s, werte ->
                        if (werte.isEmpty()) return@forEachIndexed
                        val pfad = Path().apply {
                            moveTo(x(0), y(0))
                            werte.forEachIndexed { i, wert -> lineTo(x(i + 1), y(wert)) }
                        }
                        drawPath(pfad, farben[s], style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                    gewaehlt?.takeIf { it < verlauf.anzahl }?.let { i ->
                        val xx = x(i + 1)
                        drawLine(markierung.copy(alpha = 0.5f), Offset(xx, oben), Offset(xx, unten), 1.dp.toPx())
                        kumuliert.forEachIndexed { s, werte ->
                            val p = Offset(xx, y(werte.getOrElse(i) { 0 }))
                            drawCircle(flaeche, 6.dp.toPx(), p)
                            drawCircle(farben[s], 4.dp.toPx(), p)
                        }
                    }
                    if (direktBeschriften) {
                        endBeschriftungen(serien, kumuliert, farben, textMesser, labelText, x(verlauf.anzahl), ::y)
                    }
                }
            }
        }
    }
}

private fun DrawScope.xAchse(
    verlauf: Verlauf,
    textMesser: TextMeasurer,
    stil: TextStyle,
    links: Float,
    slotBreite: Float,
    unten: Float,
) {
    if (verlauf.anzahl == 0) return
    val labelBreite = textMesser.measure("00:00", stil).size.width + 10.dp.toPx()
    // Stundenabstand so wählen, dass die Beschriftungen nicht kollidieren
    val stundenBreite = slotBreite * 4
    val jedeStunden = listOf(1, 2, 3, 4, 6, 12).firstOrNull { it * stundenBreite >= labelBreite } ?: 24
    // Pausen zuerst beschriften und ihren Platz freihalten
    val belegt = mutableListOf<ClosedFloatingPointRange<Float>>()
    verlauf.zeitpunkte.forEachIndexed { i, zeit ->
        if (zeit == null) {
            val t = textMesser.measure("Pause", stil)
            val start = links + (i + 0.5f) * slotBreite - t.size.width / 2f
            drawText(t, topLeft = Offset(start, unten + 5.dp.toPx()))
            belegt += (start - 4.dp.toPx())..(start + t.size.width + 4.dp.toPx())
        }
    }
    for (i in 0..verlauf.anzahl) {
        val zeit = grenzZeit(verlauf, i) ?: continue
        val text = uhrzeit(zeit)
        val minute = text.substringAfter(':')
        val stunde = text.substringBefore(':').toInt()
        val passend = when {
            jedeStunden == 1 && stundenBreite > labelBreite * 2 -> minute == "00" || minute == "30"
            else -> minute == "00" && stunde % jedeStunden == 0
        }
        if (!passend) continue
        val xx = links + i * slotBreite
        val t = textMesser.measure(text, stil)
        val start = (xx - t.size.width / 2f).coerceAtLeast(0f)
        val ende = start + t.size.width
        if (belegt.any { start < it.endInclusive && ende > it.start }) continue
        drawLine(stil.color.copy(alpha = 0.5f), Offset(xx, unten), Offset(xx, unten + 3.dp.toPx()), 1.dp.toPx())
        drawText(t, topLeft = Offset(start, unten + 5.dp.toPx()))
        belegt += start..(ende + 6.dp.toPx())
    }
}

/** Namen am Linienende, mit Farbmarke daneben; übereinanderliegende werden auseinandergeschoben. */
private fun DrawScope.endBeschriftungen(
    serien: List<Serie>,
    kumuliert: List<List<Int>>,
    farben: List<Color>,
    textMesser: TextMeasurer,
    stil: TextStyle,
    xEnde: Float,
    y: (Int) -> Float,
) {
    val texte = serien.map { textMesser.measure(it.name, stil, maxLines = 1) }
    val hoehe = texte.maxOf { it.size.height }.toFloat()
    val positionen = serien.indices.map { s -> s to y(kumuliert[s].lastOrNull() ?: 0) }.sortedBy { it.second }.toMutableList()
    for (k in 1 until positionen.size) {
        val (s, yy) = positionen[k]
        val minimal = positionen[k - 1].second + hoehe
        if (yy < minimal) positionen[k] = s to minimal
    }
    val maxText = (size.width - xEnde - 14.dp.toPx()).toInt().coerceAtLeast(10)
    for ((s, yy) in positionen) {
        drawCircle(farben[s], 3.dp.toPx(), Offset(xEnde + 7.dp.toPx(), yy))
        val t = textMesser.measure(serien[s].name, stil, maxLines = 1, softWrap = false,
            constraints = androidx.compose.ui.unit.Constraints(maxWidth = maxText),
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        drawText(t, topLeft = Offset(xEnde + 13.dp.toPx(), yy - t.size.height / 2f))
    }
}
