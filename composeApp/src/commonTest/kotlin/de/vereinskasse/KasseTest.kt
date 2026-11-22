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

class VerlaufTest {
    private val zone = kotlinx.datetime.TimeZone.UTC
    // 13.06.2026 18:00 UTC
    private val basis = 1_781_373_600_000L
    private fun pos(name: String, menge: Int, helfer: Boolean = false) =
        Position(name = name, menge = menge, einzelpreisCent = 100, helfer = helfer)

    @Test
    fun festtagWechseltUmSechs() {
        val sa = festtag(basis, zone)
        assertEquals(kotlinx.datetime.LocalDate(2026, 6, 13), sa)
        // 1:30 Uhr nachts zählt noch zum Samstag
        assertEquals(sa, festtag(basis + (7 * 60 + 30) * 60_000L, zone))
        // 6:10 Uhr ist der nächste Tag
        assertEquals(kotlinx.datetime.LocalDate(2026, 6, 14), festtag(basis + (12 * 60 + 10) * 60_000L, zone))
        assertEquals("Sa 13.06.", tagText(sa))
    }

    @Test
    fun verlaufProViertelstunde() {
        val bestellungen = listOf(
            Bestellung("1", basis + 60_000, listOf(pos("Bier", 2), pos("Wurst", 1))),
            Bestellung("2", basis + 14 * 60_000, listOf(pos("Bier", 1, helfer = true))),
            Bestellung("3", basis + 31 * 60_000, listOf(pos("Bier", 3))),
            Bestellung("4", basis + 32 * 60_000, listOf(pos("Bier", 5)), storniert = true),
            // nächster Tag, große Lücke
            Bestellung("5", basis + 20 * 3_600_000L, listOf(pos("Wurst", 4))),
        )
        val v = bestellungen.verlauf(mitHelfer = true)
        // 18:00, 18:15 (leer), 18:30, Lücke, 14:00 am Folgetag
        assertEquals(5, v.anzahl)
        assertEquals(null, v.zeitpunkte[3])
        assertEquals(listOf(3, 0, 3, 0, 0), v.mengen["Bier"])
        assertEquals(listOf(3, 3, 6, 6, 6), v.kumuliert("Bier"))
        assertEquals(5, v.gesamt("Wurst"))

        val ohneHelfer = bestellungen.verlauf(mitHelfer = false)
        assertEquals(5, ohneHelfer.gesamt("Bier"))

        assertEquals(2, bestellungen.festtage(zone).size)
        assertEquals(4, bestellungen.amTag(kotlinx.datetime.LocalDate(2026, 6, 13), zone).size)
    }

    @Test
    fun abschlussMitBestellungenAmTag() {
        val bestellungen = listOf(
            Bestellung("1", basis, listOf(pos("Bier", 2))),
            Bestellung("2", basis + 20 * 3_600_000L, listOf(pos("Bier", 4))),
        )
        val a = bestellungen.abschluss("x", "Kasse 2", "TSV", "Fest", 0)
        val export = importLesen(a.export())
        assertIs<ImportErgebnis.AbschlussDatei>(export)
        assertEquals(2, export.abschluss.bestellungen.size)
        val tag = festtag(basis + 20 * 3_600_000L)
        assertEquals(4, export.abschluss.amTag(tag)!!.artikel.single().menge)
        // Alte Abschlüsse ohne Bestellungen können nicht nach Tag gefiltert werden
        assertNull(a.copy(enthaeltBestellungen = false).amTag(tag))
    }
}

class SicherungUndVeranstaltungTest {
    private val bestellung = Bestellung("b1", 1000, listOf(Position(name = "Bier", menge = 2, einzelpreisCent = 350)))
    private val quelle = Verein(
        "v1", "TSV",
        artikel = listOf(Artikel("a1", "Bier", 350)),
        veranstaltungen = listOf(
            Veranstaltung("e1", "Sommerfest 2026", 100, anfangsbestandCent = 15000, istBestandCent = 99, bestellungen = listOf(bestellung)),
            Veranstaltung("e2", "Kirchweih", 200, anfangsbestandCent = 5000),
        ),
        aktiveVeranstaltungId = "e1",
    )

    @Test
    fun konfigEnthaeltVeranstaltungenOhneUmsaetze() {
        val text = quelle.konfigExport()
        assertTrue("bestellungen" !in text && "istBestand" !in text)
        val konfig = (importLesen(text) as ImportErgebnis.Konfig).konfig
        assertEquals(2, konfig.veranstaltungen.size)

        // Neues Gerät: Veranstaltungen leer angelegt, gleiche IDs, aktiv gesetzt
        val neu = AppDaten().konfigImportieren(konfig, ImportArt.ERGAENZEN).vereine.single()
        assertEquals(listOf("e1", "e2"), neu.veranstaltungen.map { it.id })
        assertEquals(15000L, neu.veranstaltungen[0].anfangsbestandCent)
        assertTrue(neu.veranstaltungen.all { it.bestellungen.isEmpty() })
        assertEquals("e1", neu.aktiveVeranstaltungId)
    }

    @Test
    fun vorhandeneVeranstaltungenBleibenUnangetastet() {
        val lokal = Verein(
            "v1", "TSV",
            veranstaltungen = listOf(
                Veranstaltung("x9", "sommerfest 2026", 5, anfangsbestandCent = 1, bestellungen = listOf(bestellung)),
                Veranstaltung("x8", "Altes Fest", 1),
            ),
            aktiveVeranstaltungId = "x8",
        )
        val konfig = (importLesen(quelle.konfigExport()) as ImportErgebnis.Konfig).konfig
        assertEquals(listOf("Kirchweih"), lokal.neueVeranstaltungen(konfig).map { it.name })
        for (art in listOf(ImportArt.ERGAENZEN, ImportArt.ERSETZEN)) {
            val v = AppDaten(vereine = listOf(lokal)).konfigImportieren(konfig, art).vereine.single()
            assertEquals(listOf("sommerfest 2026", "Altes Fest", "Kirchweih"), v.veranstaltungen.map { it.name })
            assertEquals(1L, v.veranstaltungen[0].anfangsbestandCent)
            assertEquals(1, v.veranstaltungen[0].bestellungen.size)
            // Aktive Veranstaltung der Datei (Sommerfest) wird auf die lokale gleichnamige gemappt
            assertEquals("x9", v.aktiveVeranstaltungId)
        }
    }

    @Test
    fun alteKonfigOhneVeranstaltungen() {
        val alt = """{"format":"vereinskasse","version":1,"typ":"konfiguration","verein":{"id":"v1","name":"TSV","gruppen":[],"artikel":[]}}"""
        val konfig = (importLesen(alt) as ImportErgebnis.Konfig).konfig
        val lokal = quelle.copy(aktiveVeranstaltungId = "e2")
        val v = AppDaten(vereine = listOf(lokal)).konfigImportieren(konfig, ImportArt.ERGAENZEN).vereine.single()
        assertEquals(2, v.veranstaltungen.size)
        assertEquals("e2", v.aktiveVeranstaltungId)
    }

    @Test
    fun sicherungRundreise() {
        val daten = AppDaten(listOf(quelle), "v1", "Getränke-Kasse", "geraet1")
        val text = daten.sicherungExport(1_781_373_600_000)
        val ergebnis = importLesen(text) as ImportErgebnis.Sicherung
        assertEquals(daten.copy(letzteSicherung = 1_781_373_600_000), ergebnis.daten)
        assertEquals(1_781_373_600_000, ergebnis.erstelltAm)
        assertEquals("1 Verein, 2 Veranstaltungen, 1 Bestellung", ergebnis.daten.umfangText())
        assertTrue(sicherungsDateiname(1_781_373_600_000).startsWith("Vereinskasse_Sicherung_2026-06-1"))
    }
}
