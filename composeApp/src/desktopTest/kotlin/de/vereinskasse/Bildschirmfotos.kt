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
import de.vereinskasse.ui.BezahlenInhalt
import de.vereinskasse.ui.KassenZustand
import de.vereinskasse.ui.Seite
import de.vereinskasse.ui.VereinskasseTheme
import kotlinx.coroutines.CoroutineScope
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
