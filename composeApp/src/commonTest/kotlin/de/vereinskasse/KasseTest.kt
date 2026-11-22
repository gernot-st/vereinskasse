package de.vereinskasse

import de.vereinskasse.logik.*
import de.vereinskasse.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KasseTest {
    private val bier = Artikel("a1", "Bier", 350, "g1", pfandCent = 200)
    private val wurst = Artikel("a2", "Bratwurst", 400, "g2")

    @Test
    fun geldFormatierenUndParsen() {
        assertEquals("12,50 €", geld(1250))
        assertEquals("-2,00 €", geld(-200))
        assertEquals("1.234,05 €", geld(123405))
        assertEquals(250L, parseGeld("2,5"))
        assertEquals(250L, parseGeld("2.50"))
        assertEquals(200L, parseGeld("2"))
        assertEquals(123450L, parseGeld("1.234,50"))
        assertEquals(5L, parseGeld(",05"))
        assertNull(parseGeld("abc"))
        assertNull(parseGeld("1,234"))
        assertNull(parseGeld(""))
        assertEquals("2,50", geldEingabe(250))
    }

    @Test
    fun warenkorbMitPfandUndHelfer() {
        var korb = emptyList<Position>()
        korb = korb.artikelHinzufuegen(bier, helfer = false, mitPfand = true)
        korb = korb.artikelHinzufuegen(bier, helfer = false, mitPfand = true)
        korb = korb.artikelHinzufuegen(wurst, helfer = false, mitPfand = true)
        korb = korb.artikelHinzufuegen(bier, helfer = true, mitPfand = false)
        assertEquals(3, korb.size)
        assertEquals(2, korb[0].menge)
        // 2 * (3,50 + 2,00) + 4,00 + 0
        assertEquals(1500L, korb.summeCent())
        assertEquals(4, korb.anzahlArtikel())

        korb = korb.pfandRueckgabeHinzufuegen(200)
        assertEquals(1300L, korb.summeCent())

        korb = korb.pfandUmschalten(0, bier)
        assertEquals(0L, korb[0].pfandCent)
        assertEquals(900L, korb.summeCent())

        korb = korb.mengeAendern(1, -1)
        assertEquals(500L, korb.summeCent())
    }

    @Test
    fun pfandUmschaltenFasstZusammen() {
        var korb = emptyList<Position>()
        korb = korb.artikelHinzufuegen(bier, false, mitPfand = true)
        korb = korb.artikelHinzufuegen(bier, false, mitPfand = false)
        assertEquals(2, korb.size)
        korb = korb.pfandUmschalten(1, bier)
        assertEquals(1, korb.size)
        assertEquals(2, korb[0].menge)
    }

    @Test
    fun abschlussUndZusammenfuehren() {
        val korb1 = emptyList<Position>()
            .artikelHinzufuegen(bier, false, true)
            .artikelHinzufuegen(wurst, false, true)
        val korb2 = emptyList<Position>()
            .artikelHinzufuegen(bier, true, false)
            .pfandRueckgabeHinzufuegen(200)
        val korb3 = emptyList<Position>().artikelHinzufuegen(wurst, false, true)
        val v = Veranstaltung(
            id = "v1", name = "Sommerfest", erstelltAm = 0, anfangsbestandCent = 10000, istBestandCent = 10550,
            bestellungen = listOf(
                Bestellung("b1", 1, korb1),
                Bestellung("b2", 2, korb2),
                Bestellung("b3", 3, korb3, storniert = true),
            )
        )
        val a = v.abschlussBerechnen("x", "Kasse 1", "TSV", 10)
        assertEquals(2, a.anzahlBestellungen)
        assertEquals(1, a.anzahlStornos)
        assertEquals(400L, a.stornoSummeCent)
        assertEquals(750L, a.artikelUmsatzCent)
        assertEquals(200L, a.pfandEinCent)
        assertEquals(200L, a.pfandAusCent)
        assertEquals(750L, a.kassenEinnahmeCent)
        assertEquals(10750L, a.sollBestandCent)
        val bierSumme = a.artikel.first { it.name == "Bier" }
        assertEquals(2, bierSumme.menge)
        assertEquals(1, bierSumme.helferMenge)

        val gesamt = listOf(a, a.copy(id = "y", istBestandCent = null)).zusammenfuehren("Alle", "TSV", "Sommerfest", 0)
        assertEquals(1500L, gesamt.artikelUmsatzCent)
        assertEquals(4, gesamt.artikel.first { it.name == "Bier" }.menge)
        assertNull(gesamt.istBestandCent)
        assertTrue(a.alsText("Abschluss", true, null).contains("Differenz: -2,00 €"))
    }

    @Test
    fun teilmengeVerschieben() {
        val liste = listOf("a1", "b1", "a2", "b2", "a3")
        val ergebnis = liste.teilmengeVerschieben({ it.startsWith("a") }, 2, 0)
        assertEquals(listOf("a3", "b1", "a1", "b2", "a2"), ergebnis)
    }

    @Test
    fun schnellBetraege() {
        assertEquals(listOf(1300L, 1500L, 2000L, 5000L, 10000L), schnellBetraege(1250))
        assertEquals(listOf(1000L, 2000L, 5000L, 10000L), schnellBetraege(500).take(4).let { schnellBetraege(500) }.filter { it >= 1000 })
    }

    @Test
    fun konfigExportImport() {
        val verein = Verein(
            "v1", "TSV", gruppen = listOf(Gruppe("g1", "Getränke", 1), Gruppe("g2", "Essen", 2)),
            artikel = listOf(bier, wurst),
        )
        val text = verein.konfigExport()
        val ergebnis = importLesen(text)
        assertIs<ImportErgebnis.Konfig>(ergebnis)

        // Neu importieren auf leerem Gerät
        val leer = AppDaten().konfigImportieren(ergebnis.konfig, ImportArt.ERGAENZEN)
        assertEquals(1, leer.vereine.size)
        assertEquals("v1", leer.aktiverVereinId)

        // Ergänzen: lokaler Verein hat anderen Bierpreis und einen eigenen Artikel
        val lokal = Verein(
            "anders", "tsv", gruppen = listOf(Gruppe("lg", "Getränke", 5)),
            artikel = listOf(Artikel("l1", "Bier", 300, "lg", sichtbar = false), Artikel("l2", "Cola", 250, "lg")),
        )
        val daten = AppDaten(vereine = listOf(lokal))
        val ergaenzt = daten.konfigImportieren(ergebnis.konfig, ImportArt.ERGAENZEN).vereine.single()
        assertEquals(listOf("Getränke", "Essen"), ergaenzt.gruppen.map { it.name })
        assertEquals(listOf("Bier", "Cola", "Bratwurst"), ergaenzt.artikel.map { it.name })
        assertEquals(350L, ergaenzt.artikel[0].preisCent)
        assertEquals(false, ergaenzt.artikel[0].sichtbar)
        assertEquals("lg", ergaenzt.artikel[0].gruppeId)
        assertEquals("g2", ergaenzt.artikel[2].gruppeId)

        val ersetzt = daten.konfigImportieren(ergebnis.konfig, ImportArt.ERSETZEN).vereine.single()
        assertEquals("anders", ersetzt.id)
        assertEquals(listOf("Bier", "Bratwurst"), ersetzt.artikel.map { it.name })

        val neu = daten.konfigImportieren(ergebnis.konfig, ImportArt.NEU)
        assertEquals(2, neu.vereine.size)
        assertEquals("TSV (Import)", neu.vereine[1].name)

        assertIs<ImportErgebnis.Fehler>(importLesen("hallo"))
        assertIs<ImportErgebnis.Fehler>(importLesen("{\"format\":\"x\",\"typ\":\"konfiguration\"}"))
    }

    @Test
    fun appDatenRundreise() {
        val d = AppDaten(vereine = listOf(Verein("v", "X", artikel = listOf(bier))), aktiverVereinId = "v", geraeteId = "g")
        assertEquals(d, appDatenAusJson(d.alsJson()))
        // Unbekannte Felder (neuere Version) werden ignoriert
        assertEquals(AppDaten(geraetename = "A"), appDatenAusJson("{\"geraetename\":\"A\",\"neu\":1}"))
    }
}
