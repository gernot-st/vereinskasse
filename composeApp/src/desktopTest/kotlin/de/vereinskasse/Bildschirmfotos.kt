package de.vereinskasse

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import de.vereinskasse.logik.KassenStore
import de.vereinskasse.logik.Plattform
import de.vereinskasse.logik.SpeicherImRam
import de.vereinskasse.logik.artikelHinzufuegen
import de.vereinskasse.logik.konfigExport
import de.vereinskasse.logik.pfandRueckgabeHinzufuegen
import de.vereinskasse.model.*
import de.vereinskasse.ui.App
import de.vereinskasse.ui.AuswertungSeite
import de.vereinskasse.ui.DiagrammArt
import de.vereinskasse.ui.VerlaufAbschnitt
import de.vereinskasse.logik.abschluss
import de.vereinskasse.logik.amTag
import de.vereinskasse.logik.tagText
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import de.vereinskasse.ui.BezahlenInhalt
import de.vereinskasse.ui.KassenZustand
import de.vereinskasse.ui.Seite
import de.vereinskasse.ui.VereinskasseTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.datetime.atStartOfDayIn
import kotlinx.coroutines.Dispatchers
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/**
 * Rendert Bildschirmfotos typischer Ansichten nach build/bildschirmfotos.
 * Dient zur Sichtkontrolle der Oberfläche ohne Handy/Emulator.
 */
class Bildschirmfotos {
    private val ordner = File("build/bildschirmfotos").apply { mkdirs() }

    private val plattform = object : Plattform {
        override fun teileText(text: String) {}
        override fun teileDatei(dateiname: String, inhalt: String) {}
        override fun teileBild(dateiname: String, bild: androidx.compose.ui.graphics.ImageBitmap) {}
        override fun dateiOeffnen() {}
    }

    private val getraenke = Gruppe("g1", "Getränke", 0xFF1E88E5)
    private val essen = Gruppe("g2", "Essen", 0xFFFB8C00)
    private val kuchen = Gruppe("g3", "Kuchen", 0xFFD81B60)
    private val artikel = listOf(
        Artikel("a1", "Bier 0,5", 350, "g1", pfandCent = 200),
        Artikel("a2", "Radler", 350, "g1", pfandCent = 200),
        Artikel("a3", "Cola", 250, "g1", pfandCent = 200),
        Artikel("a4", "Wasser", 200, "g1", pfandCent = 200),
        Artikel("a5", "Weinschorle", 400, "g1", pfandCent = 200),
        Artikel("a6", "Bratwurst", 400, "g2"),
        Artikel("a7", "Pommes", 300, "g2"),
        Artikel("a8", "Steak im Brötchen", 550, "g2"),
        Artikel("a9", "Käsekuchen", 250, "g3"),
        Artikel("a10", "Kaffee", 200, "g3"),
        Artikel("a11", "Schnaps", 200, "g1", sichtbar = false),
    )

    private fun beispielDaten(): AppDaten {
        val k1 = emptyList<Position>().artikelHinzufuegen(artikel[0], false, true).artikelHinzufuegen(artikel[0], false, true)
            .artikelHinzufuegen(artikel[5], false, true)
        val k2 = emptyList<Position>().artikelHinzufuegen(artikel[2], true, false).artikelHinzufuegen(artikel[6], false, true)
        val k3 = emptyList<Position>().pfandRueckgabeHinzufuegen(200).pfandRueckgabeHinzufuegen(200).artikelHinzufuegen(artikel[9], false, true)
        val bestellungen = (0 until 40).map { i ->
            Bestellung("b$i", 1_700_000_000_000 + i * 60_000L, listOf(k1, k2, k3)[i % 3], storniert = i == 7)
        }
        val v = Veranstaltung(
            "v1", "Sommerfest 2026", 1_700_000_000_000, anfangsbestandCent = 15000, istBestandCent = 47350,
            bestellungen = bestellungen,
            fremdAbschluesse = listOf(
                Abschluss(
                    "fremd", "Essens-Kasse", "TSV Musterdorf", "Sommerfest 2026", 1_700_000_100_000,
                    listOf(ArtikelSumme("Bratwurst", 55, 22000), ArtikelSumme("Pommes", 40, 12000, 2)),
                    70, 1, 400, 0, 0, 10000,
                )
            ),
        )
        val verein = Verein("ver1", "TSV Musterdorf", listOf(getraenke, essen, kuchen), artikel, listOf(v), "v1")
        return AppDaten(listOf(verein, Verein("ver2", "Feuerwehr Musterdorf")), "ver1", "Getränke-Kasse", "geraet1")
    }

    private fun store(daten: AppDaten = beispielDaten()) =
        KassenStore(SpeicherImRam(de.vereinskasse.logik.JSON.encodeToString(AppDaten.serializer(), daten)), CoroutineScope(Dispatchers.Unconfined))

    private fun foto(name: String, breiteDp: Int, hoeheDp: Int, dunkel: Boolean = false, inhalt: @Composable () -> Unit) {
        ImageComposeScene(breiteDp * 2, hoeheDp * 2, Density(2f)) {
            inhalt()
        }.use { scene ->
            scene.render(0)
            scene.render(500_000_000)
            val bild = scene.render(1_000_000_000)
            File(ordner, "$name.png").writeBytes(bild.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        }
    }

    private fun gefuellterKorb() = KassenZustand().apply {
        fuerVeranstaltung = "v1"
        korb = emptyList<Position>()
            .artikelHinzufuegen(artikel[0], false, true)
            .artikelHinzufuegen(artikel[0], false, true)
            .artikelHinzufuegen(artikel[5], false, true)
            .artikelHinzufuegen(artikel[2], true, false)
            .pfandRueckgabeHinzufuegen(200)
    }

    @Test
    fun kasseHandy() {
        val s = store()
        foto("01_kasse_handy", 412, 892) { App(s, plattform, null, {}, kassenZustand = gefuellterKorb()) }
        val ausgeklappt = gefuellterKorb().apply { korbAusgeklappt = true }
        foto("02_kasse_handy_bestellung", 412, 892) { App(s, plattform, null, {}, kassenZustand = ausgeklappt) }
    }

    @Test
    fun kasseTablet() {
        val s = store()
        foto("03_kasse_tablet", 1280, 800) { App(s, plattform, null, {}, kassenZustand = gefuellterKorb()) }
    }

    @Test
    fun bezahlen() {
        foto("04_bezahlen", 412, 700) {
            VereinskasseTheme { Surface { BezahlenInhalt(1250, {}, {}, startGegeben = 2000) } }
        }
    }

    @Test
    fun auswertung() {
        foto("05_auswertung", 412, 2000) { App(store(), plattform, null, {}, startSeiten = listOf(Seite.Kasse, Seite.Auswertung)) }
    }

    @Test
    fun verwaltung() {
        foto("06_artikel", 412, 892) {
            App(store(), plattform, null, {}, startSeiten = listOf(Seite.Kasse, Seite.VereinBearbeiten("ver1")))
        }
        foto("07_vereine", 412, 892) { App(store(), plattform, null, {}, startSeiten = listOf(Seite.Kasse, Seite.Vereine)) }
    }

    /** Zwei Festtage: Sa 14:00–01:30, So 11:00–18:00, mit Spitzen am Abend bzw. Mittag. */
    private fun festBestellungen(seed: Int, nurEssen: Boolean = false): List<Bestellung> {
        val zufall = kotlin.random.Random(seed)
        val zone = kotlinx.datetime.TimeZone.currentSystemDefault()
        fun zeit(tag: Int, stunde: Double): Long =
            kotlinx.datetime.LocalDate(2026, 6, tag).atStartOfDayIn(zone).toEpochMilliseconds() + (stunde * 3_600_000).toLong()
        val ergebnis = mutableListOf<Bestellung>()
        var id = 0
        fun tagErzeugen(tag: Int, von: Double, bis: Double, spitze: Double, anzahl: Int) {
            repeat(anzahl) {
                // Dreiecksverteilung um die Spitze
                val u = zufall.nextDouble()
                val c = (spitze - von) / (bis - von)
                val x = if (u < c) von + kotlin.math.sqrt(u * (bis - von) * (spitze - von))
                else bis - kotlin.math.sqrt((1 - u) * (bis - von) * (bis - spitze))
                var korb = emptyList<Position>()
                val auswahl = if (nurEssen) artikel.filter { it.gruppeId == "g2" } else artikel.filter { it.sichtbar && it.gruppeId != "g2" }
                repeat(1 + zufall.nextInt(3)) {
                    val a = auswahl[minOf(zufall.nextInt(auswahl.size), zufall.nextInt(auswahl.size))]
                    val nachmittag = x < 17.5 && a.gruppeId == "g3"
                    if (a.gruppeId != "g3" || nachmittag || zufall.nextInt(5) == 0) {
                        korb = korb.artikelHinzufuegen(a, helfer = zufall.nextInt(15) == 0, mitPfand = true)
                    }
                }
                if (korb.isNotEmpty()) ergebnis += Bestellung("${seed}_${id++}", zeit(tag, x), korb, storniert = zufall.nextInt(60) == 0)
            }
        }
        tagErzeugen(13, 14.0, 25.5, 21.0, 260)
        tagErzeugen(14, 11.0, 18.0, 13.0, 140)
        return ergebnis.sortedBy { it.zeit }
    }

    private fun festDaten(): AppDaten {
        val essen = festBestellungen(2, nurEssen = true)
        val v = Veranstaltung(
            "v1", "Sommerfest 2026", 0, anfangsbestandCent = 15000,
            bestellungen = festBestellungen(1),
            fremdAbschluesse = listOf(essen.abschluss("fremd", "Essens-Kasse", "TSV Musterdorf", "Sommerfest 2026", 0)),
        )
        val verein = Verein("ver1", "TSV Musterdorf", listOf(getraenke, this.essen, kuchen), artikel, listOf(v), "v1")
        return AppDaten(listOf(verein), "ver1", "Getränke-Kasse", "geraet1")
    }

    @Test
    fun verlauf() {
        val s = store(festDaten())
        val v = s.aktiveVeranstaltung!!
        val samstag = kotlinx.datetime.LocalDate(2026, 6, 13)
        val alle = (v.bestellungen + v.fremdAbschluesse.flatMap { it.bestellungen })
        fun abschnitt(name: String, tag: kotlinx.datetime.LocalDate?, art: DiagrammArt, gewaehlt: Int?, breite: Int = 412) =
            foto(name, breite, 1000) {
                VereinskasseTheme {
                    androidx.compose.material3.Surface {
                        androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(16.dp)) {
                            VerlaufAbschnitt(
                                s.aktiverVerein!!, "Sommerfest 2026" + (tag?.let { " · " + tagText(it) } ?: ""),
                                alle.amTag(tag), true, emptyList(), plattform, art, gewaehlt,
                            )
                        }
                    }
                }
            }
        abschnitt("11_verlauf_hochlauf_samstag", samstag, DiagrammArt.HOCHLAUF, null)
        abschnitt("12_verlauf_balken_samstag", samstag, DiagrammArt.BALKEN, 28)
        abschnitt("13_verlauf_hochlauf_alle_tage", null, DiagrammArt.HOCHLAUF, 20)
        abschnitt("14_verlauf_tablet", samstag, DiagrammArt.HOCHLAUF, null, breite = 900)
        foto("15_auswertung_tag", 412, 1400) {
            VereinskasseTheme { AuswertungSeite(s, plattform, {}, startTag = samstag) }
        }
    }

    @Test
    fun importFrage() {
        val s = store()
        val export = s.aktiverVerein!!.konfigExport()
        foto("08_import", 412, 892) { App(s, plattform, export, {}) }
    }

    @Test
    fun ersterStart() {
        foto("09_erster_start", 412, 892) { App(store(AppDaten()), plattform, null, {}) }
    }
}
